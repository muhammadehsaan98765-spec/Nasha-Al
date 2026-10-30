package com.nashaai.assistant.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nashaai.assistant.ui.theme.NashaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation test for RTL layout direction driven by NashaTheme.
 *
 * Uses the real NashaTheme and LocalLayoutDirection rather than a parallel
 * implementation.
 */
@RunWith(AndroidJUnit4::class)
class UrduRtlTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun urduTheme_setsRtlLayoutDirection() {
        var captured: LayoutDirection? = null

        composeRule.setContent {
            NashaTheme(language = "ur") {
                captured = LocalLayoutDirection.current
            }
        }
        composeRule.waitForIdle()

        assertEquals(LayoutDirection.Rtl, captured)
    }

    @Test
    fun englishTheme_setsLtrLayoutDirection() {
        var captured: LayoutDirection? = null

        composeRule.setContent {
            NashaTheme(language = "en") {
                captured = LocalLayoutDirection.current
            }
        }
        composeRule.waitForIdle()

        assertEquals(LayoutDirection.Ltr, captured)
    }

    @Test
    fun romanUrduTheme_setsLtrLayoutDirection() {
        var captured: LayoutDirection? = null

        composeRule.setContent {
            NashaTheme(language = "ur-Latn") {
                captured = LocalLayoutDirection.current
            }
        }
        composeRule.waitForIdle()

        assertEquals(LayoutDirection.Ltr, captured)
    }
}
