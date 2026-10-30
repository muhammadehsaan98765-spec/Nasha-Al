package com.nashaai.assistant.ui.voice

import android.Manifest
import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nashaai.assistant.AppContainer
import com.nashaai.assistant.R

// ---------------------------------------------------------------------
// Route
// ---------------------------------------------------------------------

/**
 * Voice tab entry point.
 *
 * @param onSendToChat Called when the user taps "Send to Chat" on a final
 *                     transcript. NashaApp should switch to the Chat tab
 *                     and feed the text into the input field.
 */
@Composable
fun VoiceRoute(
    container: AppContainer,
    onSendToChat: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application

    val factory = remember(container) {
        viewModelFactory {
            initializer {
                VoiceViewModel(application = app, settingsRepository = container.settingsRepository)
            }
        }
    }
    val viewModel: VoiceViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onPermissionResult(granted) }

    VoiceScreen(
        state = state,
        onStartListening = {
            // Permission request must come from an Activity context; the
            // launcher handles the system dialog and routes the result
            // back into the ViewModel.
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        },
        onStopListening = viewModel::stopListening,
        onReset = viewModel::reset,
        onSendToChat = onSendToChat,
        modifier = modifier
    )
}

// ---------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------

@Composable
fun VoiceScreen(
    state: VoiceState,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onReset: () -> Unit,
    onSendToChat: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var editedTranscript by rememberSaveable { mutableStateOf("") }

    // Sync editedTranscript when state supplies a transcript.
    LaunchedEffect(state) {
        when (state) {
            is VoiceState.Result -> editedTranscript = state.transcript
            is VoiceState.Processing -> editedTranscript = state.partialText
            else -> Unit
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.voice_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.voice_disclosure),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        when (state) {
            is VoiceState.Idle -> IdleContent(onMicTap = onStartListening)
            is VoiceState.Listening -> ListeningContent(state, onStop = onStopListening)
            is VoiceState.Processing -> ProcessingContent(state)
            is VoiceState.Result -> ResultContent(
                editedTranscript = editedTranscript,
                onEdit = { editedTranscript = it },
                onSendToChat = {
                    if (editedTranscript.isNotBlank()) onSendToChat(editedTranscript.trim())
                },
                onStartOver = onReset
            )
            is VoiceState.Error -> ErrorContent(state, onRetry = onStartListening, onReset = onReset)
        }
    }
}

// ---- Sub-composables ------------------------------------------------

@Composable
private fun IdleContent(onMicTap: () -> Unit) {
    Spacer(Modifier.height(40.dp))
    FloatingActionButton(
        onClick = onMicTap,
        modifier = Modifier
            .size(96.dp)
            .semantics { contentDescription = "Start listening" }
    ) {
        Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(40.dp))
    }
    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.voice_start),
        style = MaterialTheme.typography.bodyLarge
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Tap the mic to speak in English, Urdu, or Roman Urdu. You can also " +
            "type your message in Chat.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun ListeningContent(state: VoiceState.Listening, onStop: () -> Unit) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Mic,
            contentDescription = "Listening",
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onErrorContainer
        )
    }
    Spacer(Modifier.height(16.dp))
    Text(
        text = if (state.partialText.isBlank()) "Listening…" else state.partialText,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(16.dp))
    Button(onClick = onStop) {
        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.voice_stop))
    }
}

@Composable
private fun ProcessingContent(state: VoiceState.Processing) {
    CircularProgressIndicator(modifier = Modifier.size(48.dp))
    Spacer(Modifier.height(16.dp))
    Text(
        text = if (state.partialText.isBlank()) "Processing…" else state.partialText,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun ResultContent(
    editedTranscript: String,
    onEdit: (String) -> Unit,
    onSendToChat: () -> Unit,
    onStartOver: () -> Unit
) {
    Text(
        text = "Transcript (editable)",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
        value = editedTranscript,
        onValueChange = onEdit,
        modifier = Modifier.fillMaxWidth(),
        maxLines = 6,
        placeholder = { Text("Your transcript…") }
    )
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onStartOver) {
            Text("Start over")
        }
        Button(onClick = onSendToChat, enabled = editedTranscript.isNotBlank()) {
            Text(stringResource(R.string.voice_use_in_chat))
        }
    }
    Spacer(Modifier.height(16.dp))
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(
            text = "Tip: if your transcript is a recognised command " +
                "(e.g. \"open youtube.com\" or \"search cats\"), " +
                "Chat will show a preview and ask you to confirm before " +
                "doing anything.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun ErrorContent(
    state: VoiceState.Error,
    onRetry: () -> Unit,
    onReset: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            if (state.needsMicPermission) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.voice_mic_permission_rationale),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onReset) { Text(stringResource(R.string.dismiss)) }
        Button(onClick = onRetry) {
            if (state.needsMicPermission) Text("Try again") else Text(stringResource(R.string.retry))
        }
    }
    Spacer(Modifier.height(24.dp))
    Text(
        text = "You can always use the Chat tab to type your message instead.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}
