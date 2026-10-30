package com.nashaai.assistant.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nashaai.assistant.AppContainer
import com.nashaai.assistant.R
import com.nashaai.assistant.data.chat.SessionTurn
import com.nashaai.assistant.data.provider.ChatTurn
import com.nashaai.assistant.domain.actions.ActionPreview
import com.nashaai.assistant.domain.actions.ActionRisk

@Composable
fun ChatRoute(
    container: AppContainer,
    prefillText: String? = null,
    onPrefillConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val factory = remember(container) {
        viewModelFactory {
            initializer {
                ChatViewModel(
                    chatRepository = container.chatRepository,
                    settingsRepository = container.settingsRepository,
                    commandParser = container.commandParser,
                    actionEngine = container.actionEngine
                )
            }
        }
    }
    val viewModel: ChatViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()

    ChatScreen(
        state = state,
        onSend = viewModel::send,
        onRetry = viewModel::retry,
        onDismissError = viewModel::dismissError,
        onNewConversation = viewModel::newConversation,
        onClearChat = viewModel::clearChat,
        onConfirmAction = viewModel::confirmAction,
        onCancelAction = viewModel::cancelAction,
        prefillText = prefillText,
        onPrefillConsumed = onPrefillConsumed,
        modifier = modifier
    )
}

@Composable
fun ChatScreen(
    state: ChatUiState,
    onSend: (String) -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    onNewConversation: () -> Unit,
    onClearChat: () -> Unit,
    onConfirmAction: () -> Unit,
    onCancelAction: () -> Unit,
    prefillText: String?,
    onPrefillConsumed: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(prefillText) {
        if (!prefillText.isNullOrBlank()) {
            inputText = prefillText
            onPrefillConsumed()
        }
    }

    LaunchedEffect(state.turns.size, state.isSending) {
        if (state.turns.isNotEmpty()) {
            listState.animateScrollToItem(state.turns.size + (if (state.isSending) 1 else 0))
        }
    }

    Column(modifier = modifier.fillMaxSize()) {

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onNewConversation) {
                Text(stringResource(R.string.next), fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClearChat) {
                Text(stringResource(R.string.chat_clear))
            }
        }

        if (state.error != null) {
            ErrorBanner(
                message = state.error,
                onDismiss = onDismissError,
                onRetry = onRetry,
                canRetry = state.canRetry
            )
        }

        val preview = state.actionPreview
        if (preview != null) {
            ActionPreviewCard(
                preview = preview,
                onConfirm = onConfirmAction,
                onCancel = onCancelAction
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            if (!state.hasMessages && !state.isSending) {
                EmptyState()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.turns) { turn -> MessageBubble(turn) }
                    if (state.isSending) item { TypingBubble() }
                }
            }
        }

        Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp, max = 160.dp),
                    placeholder = { Text(stringResource(R.string.chat_input_hint)) },
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (inputText.isNotBlank() && !state.isSending) {
                            onSend(inputText); inputText = ""; focusManager.clearFocus()
                        }
                    })
                )
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank() && !state.isSending) {
                            onSend(inputText); inputText = ""; focusManager.clearFocus()
                        }
                    },
                    enabled = inputText.isNotBlank() && !state.isSending,
                    modifier = Modifier.size(48.dp)
                        .semantics { contentDescription = "Send message" }
                ) {
                    Icon(Icons.Filled.Send, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    canRetry: Boolean
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            // BLOCK 14: only show Retry when it is actually useful.
            if (canRetry) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.chat_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "Ask anything. Type /open, /search, /copy, /note, " +
                    "/message, or /read to trigger an action preview.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun MessageBubble(turn: SessionTurn) {
    val isUser = turn.role == ChatTurn.ROLE_USER
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant
    val alignment = if (isUser) Arrangement.End else Arrangement.Start
    val clipboard = LocalClipboardManager.current

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = alignment) {
        Column(
            modifier = Modifier.widthIn(max = 320.dp)
                .background(bubbleColor, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            MessageBody(text = turn.text, textColor = textColor)
            if (!isUser) {
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { clipboard.setText(AnnotatedString(turn.text)) },
                    modifier = Modifier.height(28.dp)
                        .semantics { contentDescription = "Copy message" }
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null,
                        modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.copy), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun MessageBody(text: String, textColor: androidx.compose.ui.graphics.Color) {
    val segments = remember(text) { splitByCodeFences(text) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        segments.forEach { seg ->
            if (seg.isCode) {
                CodeBlock(code = seg.text)
            } else {
                Text(text = seg.text, color = textColor,
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun CodeBlock(code: String) {
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = { clipboard.setText(AnnotatedString(code)) },
                modifier = Modifier.height(28.dp)
                    .semantics { contentDescription = "Copy code block" }
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null,
                    modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.copy), fontSize = 12.sp)
            }
        }
        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Text(text = code, fontFamily = FontFamily.Monospace,
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun TypingBubble() {
    Row(modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.loading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActionPreviewCard(
    preview: ActionPreview,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val riskLabel = when (preview.risk) {
        ActionRisk.LOW -> stringResource(R.string.action_risk_low)
        ActionRisk.MEDIUM -> stringResource(R.string.action_risk_medium)
        ActionRisk.HIGH -> stringResource(R.string.action_risk_high)
        ActionRisk.BLOCKED -> "Blocked"
    }
    val riskColor = when (preview.risk) {
        ActionRisk.LOW -> MaterialTheme.colorScheme.primaryContainer
        ActionRisk.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer
        ActionRisk.HIGH -> MaterialTheme.colorScheme.errorContainer
        ActionRisk.BLOCKED -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = riskColor)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.chat_action_preview),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text("• $riskLabel", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(6.dp))
            Text(preview.humanSummary, style = MaterialTheme.typography.bodyMedium)
            if (preview.warning != null) {
                Spacer(Modifier.height(6.dp))
                Text(preview.warning, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                Spacer(Modifier.width(6.dp))
                Button(onClick = onConfirm, enabled = preview.executable) {
                    Text(stringResource(R.string.chat_confirm_action))
                }
            }
        }
    }
}

private data class MessageSegment(val text: String, val isCode: Boolean)

private fun splitByCodeFences(raw: String): List<MessageSegment> {
    val segments = mutableListOf<MessageSegment>()
    var i = 0
    val len = raw.length
    while (i < len) {
        val fenceStart = raw.indexOf("```", i)
        if (fenceStart == -1) {
            val text = raw.substring(i)
            if (text.isNotBlank()) segments.add(MessageSegment(text, false))
            break
        }
        if (fenceStart > i) {
            val before = raw.substring(i, fenceStart)
            if (before.isNotBlank()) segments.add(MessageSegment(before, false))
        }
        val fenceEnd = raw.indexOf("```", fenceStart + 3)
        if (fenceEnd == -1) {
            val rest = raw.substring(fenceStart + 3).trimStart('\n')
            if (rest.isNotBlank()) segments.add(MessageSegment(rest, true))
            break
        }
        var code = raw.substring(fenceStart + 3, fenceEnd).trimStart('\n')
        val firstNewline = code.indexOf('\n')
        if (firstNewline in 1..30) {
            val firstLine = code.substring(0, firstNewline)
            val looksLikeLang = firstLine.isNotBlank() &&
                firstLine.all { it.isLetterOrDigit() || it in "+#-_" }
            if (looksLikeLang) code = code.substring(firstNewline + 1)
        }
        if (code.isNotBlank()) segments.add(MessageSegment(code.trimEnd(), true))
        i = fenceEnd + 3
    }
    return segments
}
