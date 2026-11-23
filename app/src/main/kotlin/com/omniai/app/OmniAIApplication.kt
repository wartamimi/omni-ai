package com.omniai.app

import android.app.Application
import com.omniai.core.data.AppInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class OmniAIApplication : Application() {
    @Inject lateinit var initializer: AppInitializer

    override fun onCreate() {
        super.onCreate()
        initializer.initialize()
    }
}
