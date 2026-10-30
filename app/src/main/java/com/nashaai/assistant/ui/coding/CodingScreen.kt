package com.nashaai.assistant.ui.coding

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nashaai.assistant.AppContainer
import com.nashaai.assistant.data.workspace.CodeEntry
import com.nashaai.assistant.util.SafHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------
// Route
// ---------------------------------------------------------------------

@Composable
fun CodingRoute(container: AppContainer, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val factory = remember(container) {
        viewModelFactory {
            initializer {
                CodingViewModel(
                    chatRepository = container.chatRepository,
                    workspaceRepository = container.workspaceRepository
                )
            }
        }
    }
    val viewModel: CodingViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.message) {
        if (state.message != null) { delay(3000); viewModel.clearMessage() }
    }

    // SAF exporter — writes the current editor content to a user-chosen URI.
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                SafHelper.writeTextToUri(context, uri, state.editorContent).fold(
                    onSuccess = {
                        Toast.makeText(context, "Exported", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { e ->
                        val msg = SafHelper.userMessageFor(e)
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    CodingScreen(
        state = state,
        onPromptChange = viewModel::setPrompt,
        onLanguageChange = viewModel::setLanguage,
        onFilenameChange = viewModel::setFilename,
        onEditorChange = viewModel::setEditorContent,
        onGenerate = viewModel::generate,
        onCancel = viewModel::cancel,
        onRetry = viewModel::retry,
        onClearResponse = viewModel::clearResponse,
        onSave = viewModel::saveToWorkspace,
        onLoad = viewModel::loadEntry,
        onDelete = viewModel::deleteEntry,
        onRename = viewModel::renameEntry,
        onExport = {
            val name = SafHelper.suggestedFileName(
                state.filename.ifBlank { "snippet" }, "txt"
            )
            exportLauncher.launch(name)
        },
        onDismissError = viewModel::dismissError,
        modifier = modifier
    )
}

// ---------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------

@Composable
fun CodingScreen(
    state: CodingUiState,
    onPromptChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onFilenameChange: (String) -> Unit,
    onEditorChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onClearResponse: () -> Unit,
    onSave: () -> Unit,
    onLoad: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onExport: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    var renameDialog by remember { mutableStateOf<CodeEntry?>(null) }

    Column(
        modifier = modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Coding Workspace", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold)
        Text("Generate code with AI, edit it, and save it locally. Code is " +
            "not compiled or executed — you run it in your own environment.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        // ---- Prompt ------------------------------------------
        OutlinedTextField(
            value = state.prompt, onValueChange = onPromptChange,
            label = { Text("Prompt") },
            placeholder = { Text("Describe the code you want…") },
            maxLines = 5, modifier = Modifier.fillMaxWidth()
        )

        // ---- Language + filename -----------------------------
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.language, onValueChange = onLanguageChange,
                label = { Text("Language") }, singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = state.filename, onValueChange = onFilenameChange,
                label = { Text("Filename") }, singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        // ---- Actions -----------------------------------------
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onGenerate,
                enabled = state.prompt.isNotBlank() && !state.isLoading) {
                Text("Generate")
            }
            if (state.isLoading) {
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
            } else {
                OutlinedButton(onClick = onRetry) { Text("Retry") }
                OutlinedButton(onClick = onClearResponse) { Text("Clear") }
            }
        }

        if (state.isLoading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.height(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Generating…", style = MaterialTheme.typography.bodySmall)
            }
        }

        // ---- Error -------------------------------------------
        state.error?.let { err ->
            Surface(color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(err, color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismissError) { Text("Dismiss") }
                }
            }
        }

        // ---- Raw response (if different from editor) ---------
        val raw = state.rawResponse
        if (raw != null && raw.trim() != state.editorContent.trim()) {
            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(8.dp)) {
                    Text("Raw AI reply",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    SelectionContainer {
                        Text(raw, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // ---- Editor ------------------------------------------
        Text("Code editor", style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = state.editorContent, onValueChange = onEditorChange,
            textStyle = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = 160.dp, max = 480.dp)
        )

        // ---- Editor actions ----------------------------------
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString(state.editorContent)) },
                enabled = state.editorContent.isNotBlank()
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null,
                    modifier = Modifier.height(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy")
            }
            Button(onClick = onSave,
                enabled = state.editorContent.isNotBlank()) {
                Text(if (state.selectedEntryId != null) "Update" else "Save")
            }
            OutlinedButton(
                onClick = onExport,
                enabled = state.editorContent.isNotBlank()
            ) {
                Icon(Icons.Filled.Upload, contentDescription = null,
                    modifier = Modifier.height(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Export")
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 6.dp))

        // ---- Saved files -------------------------------------
        Text("Saved in workspace", style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold)
        if (state.savedFiles.isEmpty()) {
            Text("No saved files yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            state.savedFiles.forEach { entry ->
                SavedFileRow(
                    entry = entry,
                    isSelected = entry.id == state.selectedEntryId,
                    onLoad = { onLoad(entry.id) },
                    onRename = { renameDialog = entry },
                    onDelete = { onDelete(entry.id) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("⚠ Generated code is not compiled or executed by Nasha AI. " +
            "Review it before using it anywhere.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
    }

    renameDialog?.let { entry ->
        var nameDraft by remember(entry.id) { mutableStateOf(entry.name) }
        AlertDialog(
            onDismissRequest = { renameDialog = null },
            title = { Text("Rename file") },
            text = {
                OutlinedTextField(
                    value = nameDraft, onValueChange = { nameDraft = it },
                    label = { Text("New name") }, singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = { onRename(entry.id, nameDraft); renameDialog = null }) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameDialog = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SavedFileRow(
    entry: CodeEntry,
    isSelected: Boolean,
    onLoad: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface
    Row(modifier = Modifier.fillMaxWidth()
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium)
            Text("${entry.language} • ${formatTimestamp(entry.updatedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onLoad, modifier = Modifier.semantics {
            contentDescription = "Load ${entry.name}"
        }) { Icon(Icons.Filled.Edit, contentDescription = null) }
        IconButton(onClick = onRename, modifier = Modifier.semantics {
            contentDescription = "Rename ${entry.name}"
        }) { Icon(Icons.Filled.ContentCopy, contentDescription = null) }
        IconButton(onClick = onDelete, modifier = Modifier.semantics {
            contentDescription = "Delete ${entry.name}"
        }) { Icon(Icons.Filled.Delete, contentDescription = null) }
    }
}

private fun formatTimestamp(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000L -> "just now"
        diff < 3_600_000L -> "${diff / 60_000L}m ago"
        diff < 86_400_000L -> "${diff / 3_600_000L}h ago"
        else -> java.text.SimpleDateFormat(
            "MMM d", java.util.Locale.getDefault()
        ).format(java.util.Date(ts))
    }
}
