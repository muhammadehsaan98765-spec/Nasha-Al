package com.nashaai.assistant.ui.chat

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nashaai.assistant.ui.chat.ChatScreen
import com.nashaai.assistant.ui.chat.ChatUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation test for the stateless ChatScreen.
 *
 * Renders the real composable with controlled state and callbacks. No live
 * network, no ViewModel, no API key. Verifies that typing + sending calls
 * onSend with the typed text.
 */
@RunWith(AndroidJUnit4::class)
class ChatScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun typingAndSend_invokesOnSendWithText() {
        var capturedText: String? = null

        composeRule.setContent {
            ChatScreen(
                state = ChatUiState(),
                onSend = { capturedText = it },
                onRetry = {},
                onDismissError = {},
                onNewConversation = {},
                onClearChat = {},
                onConfirmAction = {},
                onCancelAction = {},
                prefillText = null,
                onPrefillConsumed = {}
            )
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("hello")
        composeRule.onNodeWithContentDescription("Send message").performClick()
        composeRule.waitForIdle()

        assertEquals("hello", capturedText)
    }

    @Test
    fun emptyInput_doesNotInvokeOnSend() {
        var sendInvoked = false

        composeRule.setContent {
            ChatScreen(
                state = ChatUiState(),
                onSend = { sendInvoked = true },
                onRetry = {},
                onDismissError = {},
                onNewConversation = {},
                onClearChat = {},
                onConfirmAction = {},
                onCancelAction = {},
                prefillText = null,
                onPrefillConsumed = {}
            )
        }

        // Send button disabled when input is blank; click does nothing.
        composeRule.onNodeWithContentDescription("Send message").performClick()
        composeRule.waitForIdle()

        assertEquals(false, sendInvoked)
    }
}
