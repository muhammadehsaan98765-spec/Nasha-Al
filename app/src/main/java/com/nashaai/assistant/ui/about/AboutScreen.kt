package com.nashaai.assistant.ui.about

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nashaai.assistant.AppContainer

/**
 * About Nasha AI.
 *
 * Answers the questions a user is entitled to before trusting the app:
 *   - What does Nasha AI do?
 *   - What does it NOT do?
 *   - Where does my data go?
 *   - What permissions does it use, and why?
 *   - What works offline vs online?
 *
 * The [DiagnosticsSection] at the bottom surfaces real, non-sensitive
 * status (network, provider, accessibility, config validation). No API
 * keys, chat messages, or device identifiers appear there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appVersion = remember { readAppVersion(context) }
    val androidInfo = remember {
        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("About Nasha AI") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ---- Header ------------------------------------------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Nasha AI",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "A multilingual voice-first assistant for Android.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Version: $appVersion",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = androidInfo,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // ---- Capabilities -----------------------------------------
            InfoCard(title = "What Nasha AI can do") {
                Bullets(
                    listOf(
                        "Chat with an AI provider you configure (Gemini or OpenAI-compatible).",
                        "Speak or type in English, Urdu, and Roman Urdu.",
                        "Open installed apps, Android Settings, and notification settings.",
                        "Search the web through your chosen engine.",
                        "Copy text, save notes, draft code, and share text.",
                        "Read responses aloud (in a future update).",
                        "Perform Home / Back / Recents when accessibility is enabled."
                    )
                )
            }

            // ---- Limitations ------------------------------------------
            InfoCard(title = "What Nasha AI does NOT do") {
                Bullets(
                    listOf(
                        "It does not bypass the lock screen, PINs, passwords, or biometrics.",
                        "It does not read screen content, messages, or passwords from other apps.",
                        "It does not send messages on your behalf — the share sheet opens.",
                        "It does not collect contacts, SMS, call logs, or device files.",
                        "It does not execute raw AI output as commands.",
                        "It cannot control every Android app or setting — the OS restrictions apply.",
                        "It does not run in the background or listen with the screen off."
                    )
                )
            }

            // ---- Privacy ----------------------------------------------
            InfoCard(title = "Privacy & data handling") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "• API keys are encrypted on this device with Android Keystore.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "• Chat text is sent only to the AI provider you configure.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "• Chat history is stored locally (DataStore). You can disable " +
                            "it in Settings or clear it at any time.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "• Diagnostics never include API keys, chat content, or " +
                            "device identifiers.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "• Speech recognition may use network services. Offline " +
                            "recognition is not guaranteed for any language.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // ---- Provider + network -----------------------------------
            InfoCard(title = "AI provider & network") {
                Text(
                    "AI chat requires an internet connection and a configured " +
                        "provider key. Provider pricing, rate limits, and free-tier " +
                        "availability are set by the provider, not by Nasha AI. " +
                        "Device actions (open app, search URL, copy text, save note) " +
                        "work offline; AI replies do not.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // ---- Accessibility ----------------------------------------
            InfoCard(title = "Accessibility service") {
                Text(
                    "If you choose to enable it, Nasha AI's accessibility service " +
                        "performs ONLY Home, Back, and Recents global actions. The " +
                        "service has `canRetrieveWindowContent=false` in its config, " +
                        "so it cannot read text fields, messages, or screen content. " +
                        "You can enable or disable it at any time in Android Settings.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // ---- Offline vs online ------------------------------------
            InfoCard(title = "Offline vs online") {
                Text(
                    "Offline: chat history, saved notes, coding workspace, settings, " +
                        "device actions, and accessibility navigation.\n\n" +
                        "Online: AI chat responses, Brave Search API, and any action " +
                        "that requires network (e.g. opening an http(s) URL will " +
                        "require a browser with connectivity).",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // ---- Diagnostics ------------------------------------------
            DiagnosticsSection(container = container)

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun Bullets(items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            Text(
                text = "• $item",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun readAppVersion(context: android.content.Context): String = try {
    val info = if (Build.VERSION.SDK_INT >= 33) {
        context.packageManager.getPackageInfo(
            context.packageName,
            android.content.pm.PackageManager.PackageInfoFlags.of(0)
        )
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    info.versionName ?: "unknown"
} catch (_: Exception) {
    "unknown"
}
