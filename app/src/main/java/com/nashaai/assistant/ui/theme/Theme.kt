package com.nashaai.assistant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp

// Brand colors (aligned with res/values/colors.xml shipped in Block 1).
private val NashaPrimary = Color(0xFF6750A4)
private val NashaSecondary = Color(0xFF625B71)
private val NashaTertiary = Color(0xFF7D5260)
private val NashaError = Color(0xFFB3261E)

private val NashaLightColors = lightColorScheme(
    primary = NashaPrimary,
    secondary = NashaSecondary,
    tertiary = NashaTertiary,
    error = NashaError
)

private val NashaDarkColors = darkColorScheme(
    // Material 3 dark tonal palette keeps ≥4.5:1 contrast on surface.
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0)
)

private val NashaTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 16.sp,
        lineHeight = 24.sp
    )
)

/**
 * True if the given BCP-47 language tag is normally written right-to-left.
 * Handles both bare-language tags ("ur") and region-suffixed tags
 * ("ar-EG"). "ur-Latn" (Roman Urdu) is LTR by design and correctly
 * returns false because `substringBefore('-')` yields "ur", BUT — we
 * explicitly special-case any tag whose script subtag is "Latn" as LTR.
 */
fun isRtlLanguage(tag: String): Boolean {
    val parts = tag.split('-')
    val primary = parts.firstOrNull()?.lowercase().orEmpty()
    val script = parts.getOrNull(1)?.lowercase().orEmpty()
    if (script == "latn") return false
    return primary in setOf("ur", "ar", "fa", "ps", "sd", "he", "yi")
}

/**
 * Material 3 app theme.
 *
 * Parameters:
 *   - darkTheme: pass `isSystemInDarkTheme()` for system mode, `true` or
 *     `false` for forced light/dark. Blocks 1-6 do not persist a theme
 *     preference yet; MainActivity uses system mode. A future Settings
 *     block can read a stored `theme` preference and pass it here.
 *   - language: current interface language ("en" | "ur" | "ur-Latn" | …).
 *     Used solely to pick [LayoutDirection]. RTL is provided via
 *     LocalLayoutDirection so every descendant (including dialogs and
 *     popups) inherits it.
 */
@Composable
fun NashaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    language: String = "en",
    content: @Composable () -> Unit
) {
    val direction = if (isRtlLanguage(language)) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        MaterialTheme(
            colorScheme = if (darkTheme) NashaDarkColors else NashaLightColors,
            typography = NashaTypography,
            content = content
        )
    }
}
