package com.nashaai.assistant.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nashaai.assistant.data.chat.ChatRepository
import com.nashaai.assistant.data.chat.ChatSession
import com.nashaai.assistant.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** UI state for the History screen. */
data class HistoryUiState(
    val sessions: List<ChatSession> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val saveHistoryEnabled: Boolean = true,
    val selectedSession: ChatSession? = null
)

/**
 * Chat history controller.
 *
 * Consumes ChatRepository's suspend listSessions / deleteSession /
 * clearAll / exportSessionAsText APIs. Respects the user's
 * settings.saveHistory preference by surfacing it in the UI; the
 * repository still retains saved sessions because the setting only
 * affects whether NEW sessions are saved (per Block 8's ChatViewModel).
 */
class HistoryViewModel(
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val saveEnabled = settingsRepository.settings.first().saveHistory
                val sessions = chatRepository.listSessions()
                _state.value = _state.value.copy(
                    sessions = sessions,
                    saveHistoryEnabled = saveEnabled,
                    isLoading = false
                )
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Couldn't load history"
                )
            }
        }
    }

    fun openSession(id: String) {
        val session = _state.value.sessions.firstOrNull { it.id == id } ?: return
        _state.value = _state.value.copy(selectedSession = session)
    }

    fun closeSession() {
        _state.value = _state.value.copy(selectedSession = null)
    }

    fun deleteSession(id: String) {
        viewModelScope.launch {
            try {
                chatRepository.deleteSession(id)
                refresh()
            } catch (_: Exception) {
                _state.value = _state.value.copy(error = "Couldn't delete session")
            }
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            try {
                chatRepository.clearAll()
                refresh()
            } catch (_: Exception) {
                _state.value = _state.value.copy(error = "Couldn't clear history")
            }
        }
    }

    /**
     * Renders [id] as plain text (via ChatRepository.exportSessionAsText)
     * and hands it to [onReady]. Called before SAF launch; [onReady] may
     * receive null if the session is gone.
     */
    fun exportSession(id: String, onReady: (String?) -> Unit) {
        viewModelScope.launch {
            val text = try {
                chatRepository.exportSessionAsText(id)
            } catch (_: Exception) {
                null
            }
            onReady(text)
        }
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }
}
