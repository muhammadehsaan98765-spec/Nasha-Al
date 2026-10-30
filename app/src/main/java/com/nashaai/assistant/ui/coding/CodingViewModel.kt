package com.nashaai.assistant.ui.coding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nashaai.assistant.data.chat.ChatRepository
import com.nashaai.assistant.data.provider.ChatTurn
import com.nashaai.assistant.data.provider.ProviderException
import com.nashaai.assistant.data.workspace.CodeEntry
import com.nashaai.assistant.data.workspace.WorkspaceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the Coding workspace.
 *
 * [editorContent] is what the user sees in the monospace editor. After a
 * successful generate() call it is populated from the code fence extracted
 * from [rawResponse]; the user can edit it freely before saving.
 */
data class CodingUiState(
    val prompt: String = "",
    val language: String = DEFAULT_LANGUAGE,
    val filename: String = DEFAULT_FILENAME,
    val rawResponse: String? = null,
    val editorContent: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val savedFiles: List<CodeEntry> = emptyList(),
    val selectedEntryId: String? = null,
    val message: String? = null
) {
    companion object {
        const val DEFAULT_LANGUAGE = "kotlin"
        const val DEFAULT_FILENAME = "snippet.kt"
    }
}

/**
 * Coding workspace controller.
 *
 * Data flow:
 *
 *   prompt  ──sendMessage──►  ChatRepository  ──►  AI provider
 *                                    │
 *                                    ▼  raw reply
 *                          extractCodeFromReply()
 *                                    │
 *                                    ▼
 *                              editorContent
 *                                    │
 *                          user taps Save
 *                                    │
 *                                    ▼
 *                         WorkspaceRepository.save()
 *
 * Safety:
 *   - Generated code is NEVER compiled, executed, or auto-imported into
 *     any IDE.
 *   - API keys never appear in the prompt, editor, response, or saved
 *     workspace files.
 *   - No "APK was built" style claims — the UI carries a warning.
 */
class CodingViewModel(
    private val chatRepository: ChatRepository,
    private val workspaceRepository: WorkspaceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CodingUiState())
    val state: StateFlow<CodingUiState> = _state.asStateFlow()

    private var currentJob: Job? = null

    init {
        refreshSavedFiles()
    }

    // ---- Form fields ---------------------------------------------------

    fun setPrompt(text: String) {
        _state.update { it.copy(prompt = text) }
    }

    fun setLanguage(lang: String) {
        _state.update {
            val newDefault = defaultFilenameFor(lang)
            it.copy(
                language = lang,
                filename = if (it.filename == defaultFilenameFor(it.language)) {
                    newDefault
                } else {
                    it.filename
                }
            )
        }
    }

    fun setFilename(name: String) {
        _state.update { it.copy(filename = name) }
    }

    fun setEditorContent(content: String) {
        _state.update { it.copy(editorContent = content) }
    }

    // ---- Generate ------------------------------------------------------

    fun generate() {
        val prompt = _state.value.prompt.trim()
        if (prompt.isEmpty()) {
            _state.update { it.copy(error = "Write a prompt first") }
            return
        }
        if (_state.value.isLoading) return

        currentJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null, message = null) }

        currentJob = viewModelScope.launch {
            val language = _state.value.language
            val history = listOf(
                ChatTurn.user(buildCodingPrompt(prompt, language))
            )
            val result = chatRepository.sendMessage(history)
            result.fold(
                onSuccess = { reply ->
                    val extracted = extractCodeFromReply(reply)
                    val editor = extracted ?: reply.trim()
                    _state.update {
                        it.copy(
                            isLoading = false,
                            rawResponse = reply,
                            editorContent = editor
                        )
                    }
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(isLoading = false, error = safeError(err))
                    }
                }
            )
        }
    }

    fun cancel() {
        currentJob?.cancel()
        currentJob = null
        _state.update { it.copy(isLoading = false) }
    }

    fun retry() {
        if (_state.value.prompt.isNotBlank()) generate()
    }

    fun clearResponse() {
        _state.update {
            it.copy(
                rawResponse = null,
                editorContent = "",
                error = null,
                selectedEntryId = null
            )
        }
    }

    // ---- Workspace -----------------------------------------------------

    fun refreshSavedFiles() {
        viewModelScope.launch {
            try {
                val files = workspaceRepository.list()
                _state.update { it.copy(savedFiles = files) }
            } catch (_: Exception) {
                // list() itself swallows and returns empty on error.
                _state.update { it.copy(savedFiles = emptyList()) }
            }
        }
    }

    fun saveToWorkspace() {
        val s = _state.value
        if (s.editorContent.isBlank()) {
            _state.update { it.copy(error = "Nothing to save") }
            return
        }
        val name = s.filename.ifBlank { defaultFilenameFor(s.language) }
        viewModelScope.launch {
            val result = workspaceRepository.save(
                name = name,
                language = s.language,
                code = s.editorContent,
                existingId = s.selectedEntryId
            )
            result.fold(
                onSuccess = { entry ->
                    _state.update {
                        it.copy(
                            selectedEntryId = entry.id,
                            message = "Saved as ${entry.name}",
                            error = null
                        )
                    }
                    refreshSavedFiles()
                },
                onFailure = {
                    _state.update { it.copy(error = "Failed to save to workspace") }
                }
            )
        }
    }

    fun loadEntry(id: String) {
        viewModelScope.launch {
            val entry = workspaceRepository.loadEntry(id) ?: run {
                _state.update { it.copy(error = "Couldn't load file") }
                return@launch
            }
            val content = workspaceRepository.load(id) ?: run {
                _state.update { it.copy(error = "Couldn't read file") }
                return@launch
            }
            _state.update {
                it.copy(
                    selectedEntryId = entry.id,
                    filename = entry.name,
                    language = entry.language,
                    editorContent = content,
                    rawResponse = null,
                    error = null,
                    message = "Loaded ${entry.name}"
                )
            }
        }
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch {
            val ok = workspaceRepository.delete(id)
            if (ok) {
                _state.update {
                    it.copy(
                        message = "Deleted",
                        selectedEntryId = if (it.selectedEntryId == id) null else it.selectedEntryId
                    )
                }
                refreshSavedFiles()
            } else {
                _state.update { it.copy(error = "Failed to delete") }
            }
        }
    }

    /**
     * Renames a saved file via WorkspaceRepository.save() with the existing
     * id — the repository's save method updates in place when existingId is
     * provided, which is the only rename path available in Block 4.
     */
    fun renameEntry(id: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val entry = workspaceRepository.loadEntry(id) ?: return@launch
            val code = workspaceRepository.load(id) ?: return@launch
            val result = workspaceRepository.save(
                name = trimmed,
                language = entry.language,
                code = code,
                existingId = id
            )
            result.fold(
                onSuccess = {
                    _state.update { s ->
                        s.copy(
                            message = "Renamed to $trimmed",
                            filename = if (s.selectedEntryId == id) trimmed else s.filename
                        )
                    }
                    refreshSavedFiles()
                },
                onFailure = {
                    _state.update { it.copy(error = "Failed to rename") }
                }
            )
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    // ---- Helpers -------------------------------------------------------

    private fun buildCodingPrompt(userPrompt: String, language: String): String =
        "Write ${language.uppercase()} code for the following request. " +
            "Return ONLY the code in a single ```$language fenced code block. " +
            "No explanation, no surrounding prose. If the request is ambiguous, " +
            "return the simplest reasonable solution.\n\n" +
            "Request: $userPrompt"

    private fun extractCodeFromReply(raw: String): String? {
        val start = raw.indexOf("```")
        if (start == -1) return null
        val firstNewline = raw.indexOf('\n', start)
        if (firstNewline == -1) return null
        val end = raw.indexOf("```", firstNewline + 1)
        if (end == -1) return null
        val body = raw.substring(firstNewline + 1, end).trimEnd()
        return body.ifBlank { null }
    }

    private fun defaultFilenameFor(lang: String): String = when (lang.lowercase()) {
        "kotlin" -> "snippet.kt"
        "java" -> "Snippet.java"
        "python" -> "snippet.py"
        "javascript", "js" -> "snippet.js"
        "html" -> "snippet.html"
        "css" -> "snippet.css"
        "markdown", "md" -> "snippet.md"
        else -> "snippet.txt"
    }

    private fun safeError(e: Throwable): String = when (e) {
        is ProviderException -> e.message ?: "Provider error"
        else -> "Something went wrong while generating code"
    }
}
