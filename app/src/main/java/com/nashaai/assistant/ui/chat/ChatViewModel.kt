package com.nashaai.assistant.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nashaai.assistant.data.chat.ChatRepository
import com.nashaai.assistant.data.chat.ChatSession
import com.nashaai.assistant.data.chat.SessionTurn
import com.nashaai.assistant.data.provider.ChatTurn
import com.nashaai.assistant.data.provider.ProviderException
import com.nashaai.assistant.data.settings.SettingsRepository
import com.nashaai.assistant.domain.actions.ActionEngine
import com.nashaai.assistant.domain.actions.ActionExecutionStatus
import com.nashaai.assistant.domain.actions.ActionParams
import com.nashaai.assistant.domain.actions.ActionPreview
import com.nashaai.assistant.domain.actions.ActionRequest
import com.nashaai.assistant.domain.actions.ActionResult
import com.nashaai.assistant.domain.actions.ActionType
import com.nashaai.assistant.domain.actions.CommandParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Chat UI state.
 *
 * Block 14 additions:
 *   - [canRetry] tells ChatScreen whether the "Retry" button is meaningful
 *     for the current error. AI-suggestion rejection is NOT retryable
 *     (retrying would re-send the same turn — the rejection is a parse
 *     failure of the SECOND suggestion request, which will fail again).
 *     Network/provider failures ARE retryable.
 */
data class ChatUiState(
    val session: ChatSession = ChatSession.new(),
    val isSending: Boolean = false,
    val error: String? = null,
    val canRetry: Boolean = true,
    val pendingAction: ActionRequest? = null,
    val actionPreview: ActionPreview? = null
) {
    val turns: List<SessionTurn> get() = session.turns
    val hasMessages: Boolean get() = session.turns.isNotEmpty()
    val hasPendingAction: Boolean get() = pendingAction != null
}

/**
 * Chat orchestration with optional AI action suggestions (Block 13/14).
 *
 * AI suggestion flow (unchanged from Block 13, with the Block 14 retry fix):
 *   - Second request for a strict JSON suggestion.
 *   - Validated against allowlist + args schema.
 *   - Non-conforming → "AI suggestion rejected" with canRetry=false.
 *   - Suggestion becomes pendingAction + actionPreview — user must confirm.
 */
class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val commandParser: CommandParser,
    private val actionEngine: ActionEngine
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.isSending) return

        val parsed = commandParser.parse(trimmed)
        if (parsed.type != ActionType.UNKNOWN) {
            val preview = actionEngine.preview(parsed)
            _state.update {
                it.copy(pendingAction = parsed, actionPreview = preview, error = null)
            }
            return
        }

        val pendingTurns = _state.value.session.turns.map { it.toChatTurn() } +
            ChatTurn.user(trimmed)
        _state.update { st ->
            st.copy(
                session = st.session.copy(turns = pendingTurns.map { SessionTurn.from(it) }),
                isSending = true,
                error = null,
                canRetry = true
            )
        }

        viewModelScope.launch {
            chatRepository.sendMessage(pendingTurns).fold(
                onSuccess = { reply ->
                    val finalTurns = pendingTurns + ChatTurn.assistant(reply)
                    val sessionTurns = finalTurns.map { SessionTurn.from(it) }
                    _state.update { st ->
                        st.copy(
                            session = st.session.copy(
                                title = ChatSession.deriveTitle(sessionTurns),
                                updatedAt = System.currentTimeMillis(),
                                turns = sessionTurns
                            ),
                            isSending = false,
                            canRetry = true
                        )
                    }
                    persistIfEnabled(finalTurns)
                    maybeRequestAiSuggestion(reply)
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            isSending = false,
                            error = safeMessage(err),
                            canRetry = true
                        )
                    }
                }
            )
        }
    }

    fun retry() {
        val turns = _state.value.session.turns.map { it.toChatTurn() }
        if (turns.isEmpty() || turns.last().role != ChatTurn.ROLE_USER) return
        if (_state.value.isSending) return
        _state.update { it.copy(isSending = true, error = null, canRetry = true) }
        viewModelScope.launch {
            chatRepository.sendMessage(turns).fold(
                onSuccess = { reply ->
                    val finalTurns = turns + ChatTurn.assistant(reply)
                    val sessionTurns = finalTurns.map { SessionTurn.from(it) }
                    _state.update { st ->
                        st.copy(
                            session = st.session.copy(
                                title = ChatSession.deriveTitle(sessionTurns),
                                updatedAt = System.currentTimeMillis(),
                                turns = sessionTurns
                            ),
                            isSending = false,
                            canRetry = true
                        )
                    }
                    persistIfEnabled(finalTurns)
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            isSending = false,
                            error = safeMessage(err),
                            canRetry = true
                        )
                    }
                }
            )
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    fun newConversation() {
        val previous = _state.value.session
        _state.value = ChatUiState(session = ChatSession.new())
        if (previous.turns.isNotEmpty()) {
            viewModelScope.launch {
                val saveHistory = runCatching {
                    settingsRepository.settings.first().saveHistory
                }.getOrDefault(true)
                if (saveHistory) runCatching { chatRepository.saveSession(previous) }
            }
        }
    }

    fun clearChat() {
        val current = _state.value.session
        _state.value = ChatUiState(session = ChatSession.new())
        if (current.turns.isNotEmpty()) {
            viewModelScope.launch {
                runCatching { chatRepository.deleteSession(current.id) }
            }
        }
    }

    fun confirmAction() {
        val request = _state.value.pendingAction ?: return
        _state.update { it.copy(pendingAction = null, actionPreview = null) }
        viewModelScope.launch {
            val result = actionEngine.execute(request, confirmed = true)
            val message = humanStatus(result)
            val turns = _state.value.session.turns.map { it.toChatTurn() } +
                ChatTurn.assistant(message)
            val sessionTurns = turns.map { SessionTurn.from(it) }
            _state.update { st ->
                st.copy(
                    session = st.session.copy(
                        title = ChatSession.deriveTitle(sessionTurns),
                        updatedAt = System.currentTimeMillis(),
                        turns = sessionTurns
                    )
                )
            }
            persistIfEnabled(turns)
        }
    }

    fun cancelAction() {
        _state.update { it.copy(pendingAction = null, actionPreview = null) }
    }

    // ---- AI action suggestions (Block 13, fixed in Block 14) ----------

    private fun maybeRequestAiSuggestion(reply: String) {
        viewModelScope.launch {
            val enabled = runCatching {
                settingsRepository.settings.first().aiActionSuggestionsEnabled
            }.getOrDefault(false)
            if (!enabled) return@launch

            val prompt = buildSuggestionPrompt(reply)
            val result = chatRepository.sendMessage(listOf(ChatTurn.user(prompt)))
            result.onSuccess { json -> parseAndOfferSuggestion(json) }
            // Silent on failure — suggestions are best-effort.
        }
    }

    private fun buildSuggestionPrompt(reply: String): String = buildString {
        appendLine("You are an action suggester for an Android assistant.")
        appendLine("Based on the AI reply below, suggest AT MOST ONE action the user might want.")
        appendLine("Reply with ONLY a JSON object, no markdown, no prose:")
        appendLine("""{"type":"<ACTION_TYPE>","args":{...}}""")
        appendLine("If no action is appropriate, reply: ")
        appendLine("""{"type":"NONE"}""")
        appendLine()
        appendLine("Allowed types and args:")
        appendLine("""- "WEB_SEARCH": {"query": "<text>"}""")
        appendLine("""- "OPEN_URL": {"url": "<http/https URL>"}""")
        appendLine("""- "OPEN_APP": {"packageName": "<com.example.app>"}""")
        appendLine("""- "COPY_TEXT": {"text": "<text>"}""")
        appendLine("""- "CREATE_NOTE": {"title": "<text>", "body": "<text>"}""")
        appendLine("""- "READ_ALOUD": {"text": "<text>"}""")
        appendLine("""- "SHARE_TEXT": {"text": "<text>"}""")
        appendLine()
        appendLine("AI reply:")
        append("\"").append(reply.take(500)).append("\"")
    }

    private fun parseAndOfferSuggestion(raw: String) {
        try {
            val cleaned = raw.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            val obj: JsonObject = Json.parseToJsonElement(cleaned).jsonObject
            val typeStr = (obj["type"] as? JsonPrimitive)?.content
            if (typeStr.isNullOrBlank() || typeStr == "NONE") return

            val type = try { ActionType.valueOf(typeStr) } catch (_: Exception) {
                rejectSuggestion(); return
            }
            val allowed = setOf(
                ActionType.WEB_SEARCH, ActionType.OPEN_URL, ActionType.OPEN_APP,
                ActionType.COPY_TEXT, ActionType.CREATE_NOTE, ActionType.READ_ALOUD,
                ActionType.SHARE_TEXT
            )
            if (type !in allowed) { rejectSuggestion(); return }

            val args = obj["args"]?.jsonObject ?: run { rejectSuggestion(); return }

            fun arg(k: String): String? =
                (args[k] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

            val params: ActionParams = when (type) {
                ActionType.WEB_SEARCH ->
                    ActionParams.WebSearch(arg("query") ?: return rejectSuggestion())
                ActionType.OPEN_URL -> {
                    val u = arg("url") ?: return rejectSuggestion()
                    val safe = CommandParser.validateUrlOrNull(u) ?: return rejectSuggestion()
                    ActionParams.OpenUrl(safe)
                }
                ActionType.OPEN_APP -> {
                    val p = arg("packageName") ?: return rejectSuggestion()
                    if (!CommandParser.isValidPackageName(p)) return rejectSuggestion()
                    ActionParams.OpenApp(p)
                }
                ActionType.COPY_TEXT ->
                    ActionParams.CopyText(arg("text") ?: return rejectSuggestion())
                ActionType.CREATE_NOTE -> ActionParams.CreateNote(
                    arg("title") ?: return rejectSuggestion(),
                    arg("body") ?: return rejectSuggestion()
                )
                ActionType.READ_ALOUD ->
                    ActionParams.ReadAloud(arg("text") ?: return rejectSuggestion())
                ActionType.SHARE_TEXT ->
                    ActionParams.ShareText(arg("text") ?: return rejectSuggestion())
                else -> return rejectSuggestion()
            }

            val request = ActionRequest(type, params)
            val preview = actionEngine.preview(request)
            _state.update { it.copy(pendingAction = request, actionPreview = preview) }
        } catch (_: Exception) {
            rejectSuggestion()
        }
    }

    /**
     * BLOCK 14 FIX: rejection sets canRetry=false so the ErrorBanner
     * hides the Retry button (retrying would re-send the same turn, and
     * the suggestion JSON would be rejected again — no benefit).
     * Dismiss still works and clears the error.
     */
    private fun rejectSuggestion() {
        _state.update {
            it.copy(error = "AI suggestion rejected", canRetry = false)
        }
    }

    private suspend fun persistIfEnabled(turns: List<ChatTurn>) {
        val saveHistory = runCatching {
            settingsRepository.settings.first().saveHistory
        }.getOrDefault(true)
        if (!saveHistory) return
        try {
            val sessionTurns = turns.map { SessionTurn.from(it) }
            val toSave = _state.value.session.copy(
                title = ChatSession.deriveTitle(sessionTurns),
                updatedAt = System.currentTimeMillis(),
                turns = sessionTurns
            )
            chatRepository.saveSession(toSave)
        } catch (_: Exception) { }
    }

    private fun safeMessage(e: Throwable): String = when (e) {
        is ProviderException -> e.message ?: "Provider error. Try again."
        else -> "Something went wrong. Check your connection."
    }

    private fun humanStatus(result: ActionResult): String {
        val icon = when (result.status) {
            ActionExecutionStatus.SUCCESS -> "✅"
            ActionExecutionStatus.FAILURE -> "⚠️"
            ActionExecutionStatus.BLOCKED -> "⛔"
            ActionExecutionStatus.NOT_IMPLEMENTED -> "ℹ️"
            ActionExecutionStatus.CANCELLED -> "⏹"
            ActionExecutionStatus.AWAITING_CONFIRMATION -> "⏳"
        }
        val body = when (result.status) {
            ActionExecutionStatus.SUCCESS -> when (result.type) {
                ActionType.OPEN_URL, ActionType.WEB_SEARCH -> "Opened in your browser."
                ActionType.OPEN_APP -> "App opened."
                ActionType.COPY_TEXT -> "Copied to clipboard."
                ActionType.CREATE_NOTE -> "Note saved."
                ActionType.OPEN_SETTINGS -> "Settings opened."
                ActionType.OPEN_NOTIFICATION_SETTINGS -> "Notification settings opened."
                ActionType.ACCESSIBILITY_HOME -> "Went to Home."
                ActionType.ACCESSIBILITY_BACK -> "Went Back."
                ActionType.ACCESSIBILITY_RECENTS -> "Opened Recents."
                ActionType.SHARE_TEXT, ActionType.PREPARE_MESSAGE -> "Share sheet opened."
                ActionType.SAVE_CODE -> "Code saved to workspace."
                ActionType.READ_ALOUD -> when (result.detail) {
                    "tts_queued" -> "Reading aloud…"
                    else -> "Reading aloud."
                }
                else -> "Done."
            }
            ActionExecutionStatus.FAILURE -> when (result.type) {
                ActionType.READ_ALOUD -> when (result.detail) {
                    "tts_unavailable" ->
                        "Text-to-speech is not available on this device."
                    "tts_failed" -> "Couldn't start text-to-speech."
                    else -> "Text-to-speech failed."
                }
                else -> failureText(result)
            }
            ActionExecutionStatus.BLOCKED -> "Blocked by safety policy."
            ActionExecutionStatus.NOT_IMPLEMENTED -> "This action isn't available yet."
            ActionExecutionStatus.CANCELLED -> "Cancelled."
            ActionExecutionStatus.AWAITING_CONFIRMATION -> "Waiting for confirmation."
        }
        return "$icon $body"
    }

    private fun failureText(result: ActionResult): String = when (result.detail) {
        "accessibility_disabled" ->
            "Accessibility service is not enabled. Open Settings to enable it."
        "app_not_installed" -> "That app is not installed on this device."
        "no_handler" -> "No app can handle this on this device."
        "clipboard_failed" -> "Couldn't access the clipboard."
        "note_save_failed" -> "Couldn't save the note."
        "code_save_failed" -> "Couldn't save the code."
        "invalid_url", "missing_url" -> "That URL isn't valid."
        "missing_query" -> "Search query was empty."
        "missing_text" -> "Nothing to copy or share."
        "missing_package" -> "No package specified."
        "missing_code" -> "No code to save."
        "missing_note" -> "Note is empty."
        "executor_threw", "intent_failed", "global_action_threw" ->
            "Something went wrong while performing the action."
        else -> "Action failed."
    }
}
