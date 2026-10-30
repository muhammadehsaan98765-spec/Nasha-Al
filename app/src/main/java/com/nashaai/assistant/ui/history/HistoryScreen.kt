package com.nashaai.assistant.ui.history

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nashaai.assistant.AppContainer
import com.nashaai.assistant.data.chat.ChatSession
import com.nashaai.assistant.data.chat.SessionTurn
import com.nashaai.assistant.data.provider.ChatTurn
import com.nashaai.assistant.util.SafHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun HistoryRoute(container: AppContainer, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val factory = remember(container) {
        viewModelFactory {
            initializer {
                HistoryViewModel(
                    chatRepository = container.chatRepository,
                    settingsRepository = container.settingsRepository
                )
            }
        }
    }
    val viewModel: HistoryViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()

    var pendingExportId by remember { mutableStateOf<String?>(null) }

    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val id = pendingExportId
        if (uri != null && id != null) {
            viewModel.exportSession(id) { content ->
                if (content != null) {
                    scope.launch {
                        SafHelper.writeTextToUri(context, uri, content).fold(
                            onSuccess = {
                                Toast.makeText(context, "Session exported",
                                    Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { e ->
                                val msg = SafHelper.userMessageFor(e)
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                } else {
                    Toast.makeText(context, "Session not found",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
        pendingExportId = null
    }

    HistoryScreen(
        state = state,
        onOpen = viewModel::openSession,
        onClose = viewModel::closeSession,
        onDelete = viewModel::deleteSession,
        onClearAll = viewModel::clearAll,
        onExport = { id ->
            pendingExportId = id
            val title = state.sessions.firstOrNull { it.id == id }?.title ?: "session"
            createDocLauncher.launch(SafHelper.suggestedFileName(title, "txt"))
        },
        onDismissError = viewModel::dismissError,
        modifier = modifier
    )
}

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onOpen: (String) -> Unit,
    onClose: () -> Unit,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit,
    onExport: (String) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDeleteId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Chat History", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (state.sessions.isNotEmpty()) {
                OutlinedButton(onClick = { confirmClear = true }) { Text("Clear all") }
            }
        }

        if (!state.saveHistoryEnabled && state.sessions.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(8.dp)) {
                Text("Save history is disabled in Settings. Existing sessions " +
                    "are still shown; new chats won't be saved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(10.dp))
            }
        }

        state.error?.let { err ->
            Surface(color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp)) {
                Row(modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(err, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismissError) { Text("Dismiss") }
                }
            }
        }

        when {
            state.isLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.height(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Loading…", style = MaterialTheme.typography.bodySmall)
            }
            state.sessions.isEmpty() -> EmptyHistory()
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(state.sessions, key = { it.id }) { session ->
                    HistoryRow(
                        session = session,
                        onClick = { onOpen(session.id) },
                        onExport = { onExport(session.id) },
                        onDelete = { confirmDeleteId = session.id }
                    )
                }
            }
        }
    }

    state.selectedSession?.let { session ->
        SessionDetailDialog(session = session, onDismiss = onClose)
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all history?") },
            text = { Text("This permanently deletes all saved sessions.") },
            confirmButton = {
                TextButton(onClick = { onClearAll(); confirmClear = false }) {
                    Text("Delete all")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        )
    }

    confirmDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDeleteId = null },
            title = { Text("Delete this session?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { onDelete(id); confirmDeleteId = null }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteId = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun EmptyHistory() {
    Card(colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp)) {
            Text("No saved sessions", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("Start a chat to create your first session. Sessions are saved " +
                "when 'Save chat history' is enabled in Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HistoryRow(
    session: ChatSession,
    onClick: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(session.title.ifBlank { "Untitled" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium)
            Text("${session.turns.size} messages • ${formatTimestamp(session.updatedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onExport) {
            Icon(Icons.Filled.Share, contentDescription = "Export session")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete session")
        }
    }
}

@Composable
private fun SessionDetailDialog(session: ChatSession, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(session.title.ifBlank { "Untitled" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(modifier = Modifier.height(400.dp)
                    .verticalScroll(rememberScrollState())) {
                Text(
                    "Created: ${formatFullTimestamp(session.createdAt)}\n" +
                        "Updated: ${formatFullTimestamp(session.updatedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                session.turns.forEach { turn ->
                    TurnBlock(turn)
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun TurnBlock(turn: SessionTurn) {
    val isUser = turn.role == ChatTurn.ROLE_USER
    Column {
        Text(if (isUser) "You" else "AI",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (isUser) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(2.dp))
        Text(turn.text, style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Default)
    }
}

private fun formatTimestamp(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000L -> "just now"
        diff < 3_600_000L -> "${diff / 60_000L}m ago"
        diff < 86_400_000L -> "${diff / 3_600_000L}h ago"
        diff < 7 * 86_400_000L -> "${diff / 86_400_000L}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts))
    }
}

private fun formatFullTimestamp(ts: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
