package com.nashaai.assistant.ui.about

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nashaai.assistant.AppContainer
import com.nashaai.assistant.accessibility.AccessibilityStatusFlow
import com.nashaai.assistant.data.provider.ProviderConfig
import com.nashaai.assistant.data.provider.ProviderConfigValidator
import com.nashaai.assistant.data.provider.ProviderValidationResult
import com.nashaai.assistant.data.settings.AppSettings
import com.nashaai.assistant.data.settings.SecureKeyStore

/**
 * Privacy-safe diagnostics card.
 *
 * Contains ONLY non-sensitive status. Never includes:
 *   - API keys or passwords
 *   - Chat messages or sessions
 *   - File contents or paths
 *   - Device identifiers (IMEI, Android ID, advertising ID)
 *   - Full URLs with credentials or sensitive query parameters
 *   - Raw exception messages
 *
 * The "Copy diagnostics" button copies the SAME sanitized report to the
 * clipboard. Nothing is copied automatically.
 */
@Composable
fun DiagnosticsSection(
    container: AppContainer,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val online by container.networkMonitor.isOnline.collectAsStateWithLifecycle(
        initialValue = container.networkMonitor.isCurrentlyOnline()
    )
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = AppSettings()
    )

    val accessibilityFlow = remember(context) {
        AccessibilityStatusFlow(context.applicationContext)
    }
    val accessibilityEnabled by accessibilityFlow.isEnabled
        .collectAsStateWithLifecycle(initialValue = false)
    val accessibilityConnected by accessibilityFlow.isConnected
        .collectAsStateWithLifecycle(initialValue = false)

    val appVersion = remember { readAppVersion(context) }
    val androidInfo = remember {
        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }

    // Key-presence flags are cheap synchronous reads; re-evaluated on each
    // recomposition of this screen so they always reflect current state.
    val hasAiKey = container.secureKeyStore.hasKey(SecureKeyStore.ALIAS_AI_API_KEY)
    val hasBraveKey = container.secureKeyStore.hasKey(SecureKeyStore.ALIAS_BRAVE_SEARCH_KEY)

    // Config validation runs against a dummy non-blank key so that "key
    // present" is decoupled from "URL/model/timeout valid". The real key is
    // never loaded here.
    val providerValid = remember(settings) {
        val dummy = ProviderConfig(
            baseUrl = settings.baseUrl,
            model = settings.model,
            apiKey = "placeholder",
            timeoutSeconds = settings.requestTimeoutSeconds
        )
        ProviderConfigValidator.validate(dummy) is ProviderValidationResult.Valid
    }

    val report = remember(
        appVersion, androidInfo, online,
        settings.providerType, settings.model,
        hasAiKey, hasBraveKey,
        accessibilityEnabled, accessibilityConnected,
        providerValid
    ) {
        buildString {
            appendLine("Nasha AI — Diagnostics")
            appendLine("App version: $appVersion")
            appendLine("Platform: $androidInfo")
            appendLine("Network: ${if (online) "Online" else "Offline"}")
            appendLine("AI provider: ${settings.providerType.name}")
            appendLine("AI model: ${settings.model}")
            appendLine("AI API key configured: $hasAiKey")
            appendLine("Brave Search key configured: $hasBraveKey")
            appendLine("Accessibility user-enabled: $accessibilityEnabled")
            appendLine("Accessibility service connected: $accessibilityConnected")
            appendLine("Provider config valid: $providerValid")
        }.trimEnd()
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Diagnostics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = report,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "This report never includes API keys, chat messages, or " +
                    "device identifiers.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Button(onClick = { clipboard.setText(AnnotatedString(report)) }) {
                Text("Copy diagnostics")
            }
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
