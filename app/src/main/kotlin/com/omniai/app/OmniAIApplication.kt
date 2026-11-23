package com.omniai.app

import android.app.Application
import com.omniai.core.data.AppInitializer
import com.omniai.core.data.CrashReporter
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class OmniAIApplication : Application() {
    @Inject lateinit var initializer: AppInitializer
    @Inject lateinit var crashReporter: CrashReporter

    override fun onCreate() {
        super.onCreate()
        crashReporter.install(BuildConfig.VERSION_NAME)
        initializer.initialize()
    }
}
