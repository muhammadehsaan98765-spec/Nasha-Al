package com.nashaai.assistant.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nashaai.assistant.R

/**
 * Modern three-step onboarding UI.
 *
 * Layout rules:
 *   - Every Column and Row uses start/end alignment (never left/right),
 *     so RTL languages (Urdu) render correctly without extra branching.
 *   - Titles and short descriptions come from `R.string.*` (already
 *     translated to Urdu and Roman Urdu in Block 1). Extended body copy is
 *     English for now; it lives in this file so a future localization
 *     pass can move it into values-ur / values-b+ur+Latn without touching
 *     any other file. No such extended strings exist in Block 1's
 *     strings.xml, so adding them here keeps Blocks 1-6 unchanged.
 *
 * Accessibility:
 *   - Buttons use plain text labels — no icon-only actions.
 *   - Progress dots have a content description so TalkBack announces them.
 *   - Getting Started is not gated on API-key entry: onboarding only
 *     explains where setup will happen.
 */
@Composable
fun OnboardingScreen(
    state: OnboardingState,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Top Skip row (Skip only shown on steps 0 and 1)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.weight(1f))
                if (state.step < OnboardingState.LAST_STEP) {
                    TextButton(onClick = onSkip) {
                        Text(stringResource(R.string.skip))
                    }
                }
            }

            // Scrollable content area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    when (state.step) {
                        0 -> WelcomeStep()
                        1 -> ProviderStep()
                        2 -> PrivacyStep()
                    }
                }
            }

            // Progress dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .semantics {
                        contentDescription =
                            "Step ${state.step + 1} of ${OnboardingState.LAST_STEP + 1}"
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(OnboardingState.LAST_STEP + 1) { i ->
                    val active = i == state.step
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (active) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .let { m ->
                                if (active) {
                                    m.then(
                                        Modifier.background(MaterialTheme.colorScheme.primary)
                                    )
                                } else {
                                    m.then(
                                        Modifier.background(
                                            MaterialTheme.colorScheme.outlineVariant
                                        )
                                    )
                                }
                            }
                    )
                }
            }

            // Bottom action row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.step > 0) {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.back))
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        if (state.step < OnboardingState.LAST_STEP) onNext() else onFinish()
                    }
                ) {
                    Text(
                        if (state.step < OnboardingState.LAST_STEP) {
                            stringResource(R.string.next)
                        } else {
                            stringResource(R.string.done)
                        }
                    )
                }
            }
        }
    }
}

// ---- Individual steps -------------------------------------------------

@Composable
private fun WelcomeStep() {
    Text(
        text = stringResource(R.string.onboarding_welcome),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.onboarding_welcome_desc),
        style = MaterialTheme.typography.bodyLarge
    )
    Spacer(Modifier.height(20.dp))
    // Extended copy kept inline so Block 1 strings.xml stays untouched.
    InfoCard {
        Text(
            text = "Nasha AI is a multilingual assistant. It can chat, read responses aloud, " +
                "open apps and settings, and help you draft messages and code. Device " +
                "actions like opening apps and searching the web work offline; AI " +
                "responses require an internet connection and a configured provider.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
    Spacer(Modifier.height(16.dp))
    Text(
        text = "You can configure everything later in Settings. Onboarding is optional — " +
            "tap Skip to jump straight in.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ProviderStep() {
    Text(
        text = stringResource(R.string.onboarding_step_provider),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.onboarding_provider_desc),
        style = MaterialTheme.typography.bodyLarge
    )
    Spacer(Modifier.height(20.dp))
    InfoCard {
        Text(
            text = "Supported providers:\n" +
                "• Gemini (Google AI Studio key)\n" +
                "• Any OpenAI-compatible endpoint (OpenAI, OpenRouter, Groq, " +
                "or a local Ollama server)\n\n" +
                "You do not have to add a key now. If you skip this, chat will " +
                "show a clear 'No provider configured' message until you add " +
                "one in Settings.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
    Spacer(Modifier.height(16.dp))
    Text(
        text = "Your API key will be encrypted on this device using Android " +
            "Keystore. It will never appear in chat logs, diagnostics exports, " +
            "or ordinary preferences.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PrivacyStep() {
    Text(
        text = stringResource(R.string.onboarding_step_accessibility),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.onboarding_accessibility_desc),
        style = MaterialTheme.typography.bodyLarge
    )
    Spacer(Modifier.height(20.dp))
    InfoCard {
        Text(
            text = "What accessibility does:\n" +
                "• Performs only Home, Back, and Recents navigation.\n" +
                "• Cannot read screen content, messages, or passwords.\n" +
                "• Is never enabled automatically — you must enable it in " +
                "Android Settings > Accessibility.\n\n" +
                "What Nasha AI does NOT do:\n" +
                "• It does not bypass the lock screen or capture PINs, " +
                "passwords, or biometrics.\n" +
                "• It does not send messages on your behalf — the share " +
                "sheet opens and you choose the recipient.\n" +
                "• It does not record the microphone in the background.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
    Spacer(Modifier.height(16.dp))
    Text(
        text = "You can enable or disable accessibility at any time in Android " +
            "Settings. Leaving it off does not affect chat, web search, coding " +
            "workspace, or settings.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// ---- Helpers ----------------------------------------------------------

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

// Re-export a tiny convenience for the progress-dot background, so the
// outer file does not need to import androidx.compose.foundation.background
// in two places.
private fun Modifier.background(color: androidx.compose.ui.graphics.Color): Modifier =
    this.then(androidx.compose.foundation.background(color))
