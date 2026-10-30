package com.nashaai.assistant.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nashaai.assistant.data.chat.ChatRepository
import com.nashaai.assistant.data.network.NetworkMonitor
import com.nashaai.assistant.data.provider.ProviderConfig
import com.nashaai.assistant.data.provider.ProviderConfigValidator
import com.nashaai.assistant.data.provider.ProviderException
import com.nashaai.assistant.data.provider.ProviderValidationResult
import com.nashaai.assistant.data.settings.AiProviderType
import com.nashaai.assistant.data.settings.AppSettings
import com.nashaai.assistant.data.settings.SearchEngine
import com.nashaai.assistant.data.settings.SecureKeyStore
import com.nashaai.assistant.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Short-lived UI message (success / error) shown at the top of Settings
 * and auto-dismissed after a few seconds by the UI.
 */
data class SettingsUiMessage(
    val text: String,
    val isError: Boolean = false
)

/**
 * Connection-test result. The [Success] preview is truncated and is used
 * ONLY to prove the provider returned text. Raw provider output is not
 * surfaced verbatim in the Settings UI to avoid showing anything that
 * might contain echoed prompt text.
 */
sealed class ConnectionTestResult {
    object Idle : ConnectionTestResult()
    object Loading : ConnectionTestResult()
    data class Success(val preview: String) : ConnectionTestResult()
    data class Failure(val message: String) : ConnectionTestResult()
}

/**
 * Settings screen controller.
 *
 * Design notes:
 *   - Draft values for base URL / model / custom search URL live in the
 *     composable (not here) so typing does not trigger a DataStore write
 *     per keystroke. The ViewModel exposes "save…" methods that persist
 *     only when the user explicitly presses Save.
 *   - The API key, once saved, is never read back into UI state; only
 *     "configured" / "not configured" booleans are exposed.
 *   - Connection test uses the DRAFT URL/model (via method parameters)
 *     so the user can validate before saving.
 *   - Language changes go through both [SettingsRepository.updateLanguage]
 *     and [AppCompatDelegate.setApplicationLocales] so RTL and localized
 *     string resources switch together.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val secureKeyStore: SecureKeyStore,
    private val chatRepository: ChatRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppSettings()
        )

    val networkOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = networkMonitor.isCurrentlyOnline()
        )

    private val _hasAiKey = MutableStateFlow(false)
    val hasAiKey: StateFlow<Boolean> = _hasAiKey.asStateFlow()

    private val _hasBraveKey = MutableStateFlow(false)
    val hasBraveKey: StateFlow<Boolean> = _hasBraveKey.asStateFlow()

    private val _connectionTest =
        MutableStateFlow<ConnectionTestResult>(ConnectionTestResult.Idle)
    val connectionTest: StateFlow<ConnectionTestResult> = _connectionTest.asStateFlow()

    private val _message = MutableStateFlow<SettingsUiMessage?>(null)
    val message: StateFlow<SettingsUiMessage?> = _message.asStateFlow()

    init {
        refreshKeyStatus()
    }

    private fun refreshKeyStatus() {
        _hasAiKey.value = secureKeyStore.hasKey(SecureKeyStore.ALIAS_AI_API_KEY)
        _hasBraveKey.value = secureKeyStore.hasKey(SecureKeyStore.ALIAS_BRAVE_SEARCH_KEY)
    }

    // ---- Provider -----------------------------------------------------

    fun setProvider(type: AiProviderType) {
        viewModelScope.launch {
            runCatching { settingsRepository.updateProvider(type) }
        }
    }

    fun saveProviderConfig(baseUrl: String, model: String) {
        val url = baseUrl.trim()
        val mdl = model.trim()
        if (url.isEmpty()) {
            _message.value = SettingsUiMessage("Base URL cannot be empty", isError = true)
            return
        }
        if (mdl.isEmpty()) {
            _message.value = SettingsUiMessage("Model name cannot be empty", isError = true)
            return
        }
        viewModelScope.launch {
            try {
                settingsRepository.updateBaseUrl(url)
                settingsRepository.updateModel(mdl)
                _message.value = SettingsUiMessage("Provider config saved")
            } catch (_: Exception) {
                _message.value = SettingsUiMessage("Failed to save provider config", isError = true)
            }
        }
    }

    fun testConnection(baseUrl: String, model: String) {
        viewModelScope.launch {
            val key = withContext(Dispatchers.IO) {
                runCatching { secureKeyStore.load(SecureKeyStore.ALIAS_AI_API_KEY) }
                    .getOrNull()
            }
            if (key.isNullOrBlank()) {
                _connectionTest.value = ConnectionTestResult.Failure(
                    "No API key saved. Add one first."
                )
                return@launch
            }
            val config = ProviderConfig(
                baseUrl = baseUrl.trim(),
                model = model.trim(),
                apiKey = key,
                timeoutSeconds = settings.value.requestTimeoutSeconds
            )
            when (val v = ProviderConfigValidator.validate(config)) {
                is ProviderValidationResult.Invalid -> {
                    _connectionTest.value = ConnectionTestResult.Failure(v.reason)
                    return@launch
                }
                ProviderValidationResult.Valid -> Unit
            }
            _connectionTest.value = ConnectionTestResult.Loading
            val result = chatRepository.testConnection(config)
            result.fold(
                onSuccess = { reply ->
                    val preview = if (reply.length > 60) reply.take(57) + "…" else reply
                    _connectionTest.value =
                        ConnectionTestResult.Success(preview.ifBlank { "(empty reply)" })
                },
                onFailure = { err ->
                    _connectionTest.value =
                        ConnectionTestResult.Failure(safeErrorMessage(err))
                }
            )
        }
    }

    // ---- AI API key ---------------------------------------------------

    fun saveApiKey(plaintext: String) {
        val trimmed = plaintext.trim()
        if (trimmed.isEmpty()) {
            _message.value = SettingsUiMessage("API key cannot be empty", isError = true)
            return
        }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    secureKeyStore.save(SecureKeyStore.ALIAS_AI_API_KEY, trimmed)
                }.getOrDefault(false)
            }
            if (ok) {
                refreshKeyStatus()
                _message.value = SettingsUiMessage("API key saved")
            } else {
                _message.value = SettingsUiMessage("Failed to save API key", isError = true)
            }
        }
    }

    fun deleteApiKey() {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    secureKeyStore.delete(SecureKeyStore.ALIAS_AI_API_KEY)
                }.getOrDefault(false)
            }
            if (ok) {
                refreshKeyStatus()
                _connectionTest.value = ConnectionTestResult.Idle
                _message.value = SettingsUiMessage("API key deleted")
            } else {
                _message.value = SettingsUiMessage("Failed to delete API key", isError = true)
            }
        }
    }

    // ---- Brave Search key ---------------------------------------------

    fun saveBraveKey(plaintext: String) {
        val trimmed = plaintext.trim()
        if (trimmed.isEmpty()) {
            _message.value = SettingsUiMessage("Brave key cannot be empty", isError = true)
            return
        }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    secureKeyStore.save(SecureKeyStore.ALIAS_BRAVE_SEARCH_KEY, trimmed)
                }.getOrDefault(false)
            }
            if (ok) {
                refreshKeyStatus()
                _message.value = SettingsUiMessage("Brave Search key saved")
            } else {
                _message.value = SettingsUiMessage("Failed to save Brave key", isError = true)
            }
        }
    }

    fun deleteBraveKey() {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    secureKeyStore.delete(SecureKeyStore.ALIAS_BRAVE_SEARCH_KEY)
                }.getOrDefault(false)
            }
            if (ok) {
                refreshKeyStatus()
                _message.value = SettingsUiMessage("Brave Search key deleted")
            } else {
                _message.value = SettingsUiMessage("Failed to delete Brave key", isError = true)
            }
        }
    }

    // ---- Language -----------------------------------------------------

    fun setLanguage(tag: String) {
        val normalized = when (tag) {
            "ur" -> "ur"
            "ur-Latn" -> "ur-Latn"
            else -> "en"
        }
        viewModelScope.launch {
            runCatching { settingsRepository.updateLanguage(normalized) }
            try {
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(normalized)
                )
            } catch (_: Exception) {
                // Locale application is best-effort; the repository value
                // already drives NashaTheme's RTL direction.
            }
        }
    }

    // ---- Search -------------------------------------------------------

    fun setSearchEngine(engine: SearchEngine) {
        viewModelScope.launch { runCatching { settingsRepository.updateSearchEngine(engine) } }
    }

    fun saveCustomSearchUrl(url: String) {
        if (url.isBlank()) {
            _message.value = SettingsUiMessage("Custom URL cannot be empty", isError = true)
            return
        }
        viewModelScope.launch {
            runCatching { settingsRepository.updateCustomSearchUrl(url.trim()) }
            _message.value = SettingsUiMessage("Custom search URL saved")
        }
    }

    fun setResultLimit(limit: Int) {
        viewModelScope.launch { runCatching { settingsRepository.updateResultLimit(limit) } }
    }

    fun setSafeSearch(enabled: Boolean) {
        viewModelScope.launch { runCatching { settingsRepository.updateSafeSearch(enabled) } }
    }

    fun setTimeoutSeconds(seconds: Long) {
        viewModelScope.launch { runCatching { settingsRepository.updateTimeoutSeconds(seconds) } }
    }

    // ---- History + AI action suggestions ------------------------------

    fun setSaveHistory(enabled: Boolean) {
        viewModelScope.launch { runCatching { settingsRepository.updateSaveHistory(enabled) } }
    }

    fun clearHistory() {
        viewModelScope.launch {
            val ok = runCatching { chatRepository.clearAll() }.isSuccess
            if (ok) {
                _message.value = SettingsUiMessage("Chat history cleared")
            } else {
                _message.value = SettingsUiMessage("Failed to clear chat history", isError = true)
            }
        }
    }

    fun setAiActionSuggestions(enabled: Boolean) {
        viewModelScope.launch { runCatching { settingsRepository.setAiActionSuggestions(enabled) } }
    }

    // ---- Misc ---------------------------------------------------------

    fun clearMessage() {
        _message.value = null
    }

    fun clearConnectionResult() {
        _connectionTest.value = ConnectionTestResult.Idle
    }

    private fun safeErrorMessage(e: Throwable): String = when (e) {
        is ProviderException -> e.message ?: "Provider error"
        else -> "Connection failed. Check your network and try again."
    }
}
