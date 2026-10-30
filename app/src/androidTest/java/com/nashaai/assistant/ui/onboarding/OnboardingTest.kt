package com.nashaai.assistant.ui.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nashaai.assistant.ui.onboarding.OnboardingScreen
import com.nashaai.assistant.ui.onboarding.OnboardingState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation test for the stateless OnboardingScreen.
 *
 * Renders with controlled state and verifies that Next/Skip/Finish buttons
 * invoke the correct callbacks. Does not touch the ViewModel or DataStore.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun welcomeStep_nextButton_invokesOnNext() {
        var nextCalled = false

        composeRule.setContent {
            OnboardingScreen(
                state = OnboardingState(step = 0),
                onNext = { nextCalled = true },
                onBack = {},
                onSkip = {},
                onFinish = {}
            )
        }

        composeRule.onNodeWithText("Next").performClick()
        composeRule.waitForIdle()

        assertTrue(nextCalled)
    }

    @Test
    fun welcomeStep_skipButton_invokesOnSkip() {
        var skipCalled = false

        composeRule.setContent {
            OnboardingScreen(
                state = OnboardingState(step = 0),
                onNext = {},
                onBack = {},
                onSkip = { skipCalled = true },
                onFinish = {}
            )
        }

        composeRule.onNodeWithText("Skip").performClick()
        composeRule.waitForIdle()

        assertTrue(skipCalled)
    }

    @Test
    fun middleStep_backButton_invokesOnBack() {
        var backCalled = false

        composeRule.setContent {
            OnboardingScreen(
                state = OnboardingState(step = 1),
                onNext = {},
                onBack = { backCalled = true },
                onSkip = {},
                onFinish = {}
            )
        }

        composeRule.onNodeWithText("Back").performClick()
        composeRule.waitForIdle()

        assertTrue(backCalled)
    }

    @Test
    fun lastStep_doneButton_invokesOnFinish() {
        var finishCalled = false

        composeRule.setContent {
            OnboardingScreen(
                state = OnboardingState(step = OnboardingState.LAST_STEP),
                onNext = {},
                onBack = {},
                onSkip = {},
                onFinish = { finishCalled = true }
            )
        }

        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitForIdle()

        assertTrue(finishCalled)
    }
}
