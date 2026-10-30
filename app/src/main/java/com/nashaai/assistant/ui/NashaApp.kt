package com.nashaai.assistant.ui

import android.speech.SpeechRecognizer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nashaai.assistant.AppContainer
import com.nashaai.assistant.R
import com.nashaai.assistant.accessibility.AccessibilityStatusFlow
import com.nashaai.assistant.data.settings.AppSettings
import com.nashaai.assistant.data.settings.SecureKeyStore
import com.nashaai.assistant.ui.actions.ActionsRoute
import com.nashaai.assistant.ui.chat.ChatRoute
import com.nashaai.assistant.ui.coding.CodingRoute
import com.nashaai.assistant.ui.components.StatusBanner
import com.nashaai.assistant.ui.history.HistoryRoute
import com.nashaai.assistant.ui.onboarding.OnboardingScreen
import com.nashaai.assistant.ui.onboarding.OnboardingViewModel
import com.nashaai.assistant.ui.settings.SettingsRoute
import com.nashaai.assistant.ui.voice.VoiceRoute

/**
 * Tabs shown in the bottom navigation bar.
 *
 * Block 10 adds [History] as a 6th tab because no existing tab could host
 * the History screen without modifying Block 8/9 files (out of Block 10
 * scope). All prior tabs retain their original routes.
 */
private enum class NashaTab { Chat, Voice, Actions, Coding, History, Settings }

/**
 * Single Compose shell for the whole app (Block 10 revision).
 *
 * Wiring changes vs Block 9:
 *   - Actions tab → [ActionsRoute]
 *   - Coding tab → [CodingRoute]
 *   - History tab → [HistoryRoute]  (new tab, added in Block 10)
 *   - All other routes (Chat, Voice, Settings) unchanged.
 */
@Composable
fun NashaApp(
    container: AppContainer,
    settings: AppSettings,
    modifier: Modifier = Modifier
) {
    if (!settings.onboardingComplete) {
        OnboardingRoute(container = container, modifier = modifier)
    } else {
        MainShell(container = container, settings = settings, modifier = modifier)
    }
}

// ---- Onboarding wiring ------------------------------------------------

@Composable
private fun OnboardingRoute(container: AppContainer, modifier: Modifier = Modifier) {
    val factory = remember {
        viewModelFactory {
            initializer { OnboardingViewModel(container.settingsRepository) }
        }
    }
    val viewModel: OnboardingViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()

    OnboardingScreen(
        state = state,
        onNext = viewModel::next,
        onBack = viewModel::back,
        onSkip = viewModel::skip,
        onFinish = viewModel::finish,
        modifier = modifier
    )
}

// ---- Main shell -------------------------------------------------------

@Composable
private fun MainShell(
    container: AppContainer,
    settings: AppSettings,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val online by container.networkMonitor.isOnline
        .collectAsStateWithLifecycle(
            initialValue = container.networkMonitor.isCurrentlyOnline()
        )

    val accessibilityFlow = remember(context) {
        AccessibilityStatusFlow(context.applicationContext)
    }
    val accessibilityEnabled by accessibilityFlow.isEnabled
        .collectAsStateWithLifecycle(initialValue = false)

    var providerConfigured by remember {
        mutableStateOf(container.secureKeyStore.hasKey(SecureKeyStore.ALIAS_AI_API_KEY))
    }

    val voiceAvailable = remember(context) {
        try {
            SpeechRecognizer.isRecognitionAvailable(context.applicationContext)
        } catch (_: Exception) {
            false
        }
    }

    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = NashaTab.entries
    val selected = tabs[selectedIndex]

    var pendingChatInput by remember { mutableStateOf<String?>(null) }
    var bannerError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedIndex, settings) {
        providerConfigured = container.secureKeyStore.hasKey(SecureKeyStore.ALIAS_AI_API_KEY)
    }

    val navigateTo: (NashaTab) -> Unit = { target ->
        val idx = tabs.indexOf(target)
        if (idx >= 0) selectedIndex = idx
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding(),
        topBar = {
            StatusBanner(
                networkOnline = online,
                providerConfigured = providerConfigured,
                accessibilityEnabled = accessibilityEnabled,
                voiceAvailable = voiceAvailable,
                error = bannerError,
                onErrorDismiss = { bannerError = null },
                onNetworkClick = { /* future: network help */ },
                onProviderClick = { navigateTo(NashaTab.Settings) },
                onAccessibilityClick = { navigateTo(NashaTab.Settings) },
                onVoiceClick = { navigateTo(NashaTab.Voice) }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedIndex == index,
                        onClick = { selectedIndex = index },
                        icon = {
                            Icon(
                                imageVector = iconFor(tab),
                                contentDescription = stringResource(labelFor(tab))
                            )
                        },
                        label = { Text(stringResource(labelFor(tab))) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selected) {
                NashaTab.Chat -> ChatRoute(
                    container = container,
                    prefillText = pendingChatInput,
                    onPrefillConsumed = { pendingChatInput = null }
                )
                NashaTab.Voice -> VoiceRoute(
                    container = container,
                    onSendToChat = { text ->
                        pendingChatInput = text
                        navigateTo(NashaTab.Chat)
                    }
                )
                NashaTab.Actions -> ActionsRoute(container = container)
                NashaTab.Coding -> CodingRoute(container = container)
                NashaTab.History -> HistoryRoute(container = container)
                NashaTab.Settings -> SettingsRoute(container = container)
            }
        }
    }
}

private fun labelFor(tab: NashaTab): Int = when (tab) {
    NashaTab.Chat -> R.string.nav_chat
    NashaTab.Voice -> R.string.nav_voice
    NashaTab.Actions -> R.string.nav_actions
    NashaTab.Coding -> R.string.nav_coding
    NashaTab.History -> R.string.nav_history
    NashaTab.Settings -> R.string.nav_settings
}

private fun iconFor(tab: NashaTab): androidx.compose.ui.graphics.vector.ImageVector =
    when (tab) {
        NashaTab.Chat -> Icons.Filled.Chat
        NashaTab.Voice -> Icons.Filled.Mic
        NashaTab.Actions -> Icons.Filled.Bolt
        NashaTab.Coding -> Icons.Filled.Code
        NashaTab.History -> Icons.Filled.History
        NashaTab.Settings -> Icons.Filled.Settings
    }

@Composable
private fun PlaceholderScreen(title: String, subtitle: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
