package com.nashaai.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nashaai.assistant.data.settings.AppSettings
import com.nashaai.assistant.ui.NashaApp
import com.nashaai.assistant.ui.theme.NashaTheme

/**
 * Sole Activity in the app.
 *
 * Responsibilities:
 *   - Enable edge-to-edge rendering.
 *   - Read the current [AppSettings] (this is a cheap Flow read — DataStore
 *     caches the file).
 *   - Apply [NashaTheme] with the correct color scheme and layout
 *     direction, then render [NashaApp].
 *
 * Deliberately does NOT:
 *   - Request any permission at launch.
 *   - Start microphone capture.
 *   - Execute actions or network calls on lifecycle transitions.
 *   - Reference any screen that has not been provided yet — NashaApp owns
 *     the tab shell, and its placeholder screens are internal to it.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as NashaApplication).container

        setContent {
            val settings by container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())

            NashaTheme(language = settings.language) {
                NashaApp(container = container, settings = settings)
            }
        }
    }
}
