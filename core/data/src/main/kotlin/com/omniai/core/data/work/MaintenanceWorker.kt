package com.omniai.core.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.omniai.core.data.attachments.AttachmentProcessor
import com.omniai.core.database.OmniDatabase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * Local housekeeping only (no network): prunes provider health history older than 90 days and
 * deletes attachment files that no message references anymore.
 */
class MaintenanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun database(): OmniDatabase
        fun attachments(): AttachmentProcessor
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        val db = deps.database()
        db.providerDao().pruneHealth(System.currentTimeMillis() - 90L * 24 * 3600 * 1000)
        val referenced = db.messageDao().allAttachments().map { it.localPath }.toSet()
        deps.attachments().cleanOrphans(referenced)
        return Result.success()
    }

    companion object {
        private const val NAME = "omniai-maintenance"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
