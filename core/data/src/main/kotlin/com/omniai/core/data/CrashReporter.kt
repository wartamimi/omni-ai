package com.omniai.core.data

import android.content.Context
import android.os.Build
import com.omniai.core.common.Redactor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local-only crash capture. Writes the last uncaught exception (redacted) to app-private storage so
 * the user can view and share it from Settings. Nothing is uploaded anywhere.
 */
@Singleton
class CrashReporter @Inject constructor(@ApplicationContext private val context: Context) {
    private val file get() = File(context.filesDir, "crash/last_crash.txt")

    fun install(versionName: String) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                val report = buildString {
                    appendLine("OmniAI $versionName · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("Time: ${java.util.Date()}")
                    appendLine("Thread: ${thread.name}")
                    appendLine()
                    append(Redactor.redact(trace.take(60_000)))
                }
                file.parentFile?.mkdirs()
                file.writeText(report)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun lastReport(): String? = runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear() {
        runCatching { file.delete() }
    }
}
