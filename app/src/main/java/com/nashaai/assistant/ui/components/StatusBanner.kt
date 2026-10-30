package com.nashaai.assistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Visual state for a banner chip. */
enum class BannerState { Success, Warning, Error, Loading, Unknown }

private data class ChipColors(
    val container: Color,
    val onContainer: Color
)

@Composable
private fun colorsFor(state: BannerState): ChipColors {
    val cs = MaterialTheme.colorScheme
    return when (state) {
        BannerState.Success -> ChipColors(cs.primaryContainer, cs.onPrimaryContainer)
        BannerState.Warning -> ChipColors(cs.tertiaryContainer, cs.onTertiaryContainer)
        BannerState.Error -> ChipColors(cs.errorContainer, cs.onErrorContainer)
        BannerState.Loading -> ChipColors(cs.surfaceVariant, cs.onSurfaceVariant)
        BannerState.Unknown -> ChipColors(cs.surfaceVariant, cs.onSurfaceVariant)
    }
}

/**
 * Persistent, accessible status banner.
 *
 * All text is short, unambiguous, and localized via the caller-supplied
 * strings. Never exposes API keys, tokens, or full exception messages —
 * callers are responsible for passing a safe `error` string (e.g.
 * "Request timed out" rather than an exception's toString()).
 *
 * Chips are clickable so navigation to the relevant Settings section is a
 * single tap — the caller decides where each chip leads.
 */
@Composable
fun StatusBanner(
    networkOnline: Boolean?,
    providerConfigured: Boolean,
    accessibilityEnabled: Boolean,
    voiceAvailable: Boolean,
    error: String?,
    onErrorDismiss: () -> Unit,
    onNetworkClick: () -> Unit = {},
    onProviderClick: () -> Unit = {},
    onAccessibilityClick: () -> Unit = {},
    onVoiceClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        androidx.compose.foundation.layout.Column {
            if (error != null) {
                ErrorRow(message = error, onDismiss = onErrorDismiss)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ---- Network ----------------------------------------
                val netState = when (networkOnline) {
                    true -> BannerState.Success
                    false -> BannerState.Warning
                    null -> BannerState.Loading
                }
                val netLabel = when (networkOnline) {
                    true -> "Online"
                    false -> "Offline"
                    null -> "…"
                }
                StatusChip(
                    label = netLabel,
                    state = netState,
                    contentDescription = "Network status: $netLabel",
                    onClick = onNetworkClick
                )

                // ---- Provider ---------------------------------------
                StatusChip(
                    label = if (providerConfigured) "Provider ✓" else "No provider",
                    state = if (providerConfigured) BannerState.Success else BannerState.Warning,
                    contentDescription = if (providerConfigured) {
                        "AI provider configured"
                    } else {
                        "AI provider not configured. Tap to open Settings."
                    },
                    onClick = onProviderClick
                )

                // ---- Accessibility ----------------------------------
                StatusChip(
                    label = if (accessibilityEnabled) "Accessibility ✓" else "Accessibility off",
                    state = if (accessibilityEnabled) BannerState.Success else BannerState.Unknown,
                    contentDescription = if (accessibilityEnabled) {
                        "Accessibility service enabled"
                    } else {
                        "Accessibility service disabled. Tap to open Settings."
                    },
                    onClick = onAccessibilityClick
                )

                // ---- Voice ------------------------------------------
                StatusChip(
                    label = if (voiceAvailable) "Voice ✓" else "Voice not ready",
                    state = if (voiceAvailable) BannerState.Success else BannerState.Unknown,
                    contentDescription = if (voiceAvailable) {
                        "Voice assistant available"
                    } else {
                        "Voice assistant not yet available in this build."
                    },
                    onClick = onVoiceClick
                )
            }
        }
    }
}

@Composable
private fun ErrorRow(message: String, onDismiss: () -> Unit) {
    val colors = colorsFor(BannerState.Error)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.container)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            color = colors.onContainer,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "Error: $message" }
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Dismiss error",
                tint = colors.onContainer
            )
        }
    }
}

@Composable
private fun StatusChip(
    label: String,
    state: BannerState,
    contentDescription: String,
    onClick: () -> Unit
) {
    val colors = colorsFor(state)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(colors.container)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            }
            .then(
                if (state == BannerState.Loading) Modifier
                else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Using a Box with an onClick so we avoid the ripple overhead per
        // chip while keeping the banner snappy; the semantics block above
        // exposes it as a clickable button to TalkBack.
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .androidClickable(onClick = onClick)
        ) {
            Text(
                text = label,
                color = colors.onContainer,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// Small shim so we don't depend on the foundation.clickable overload
// changing between Compose versions. Always-available on Compose BOM
// 2024.10.01.
@Suppress("NOTHING_TO_INLINE")
private inline fun Modifier.androidClickable(
    crossinline onClick: () -> Unit
): Modifier = this.then(
    androidx.compose.foundation.clickable(onClick = onClick)
)
