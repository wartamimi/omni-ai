package com.omniai.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.omniai.core.designsystem.theme.OmniTheme
import com.omniai.core.designsystem.theme.GlassBackground
import com.omniai.core.designsystem.theme.LocalGlass
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.omniai.core.domain.repository.SettingsRepository
import com.omniai.core.model.AppSettings
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

/**
 * Single activity. Extends FragmentActivity because BiometricPrompt requires it.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository

    /** Text shared into the app from another app (ACTION_SEND), consumed once by the UI. */
    private val sharedText = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        var ready = false
        splash.setKeepOnScreenCondition { !ready }
        handleIntent(intent)

        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            val s: AppSettings? = settings
            if (s != null) {
                ready = true
                OmniTheme(themeMode = s.themeMode, accent = s.accent, dynamicColor = s.dynamicColor, textScale = s.textScale) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackground(LocalGlass.current)
                        OmniApp(sharedText = sharedText, onSharedTextConsumed = { sharedText.value = null })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { sharedText.value = it.take(100_000) }
        }
    }
}
