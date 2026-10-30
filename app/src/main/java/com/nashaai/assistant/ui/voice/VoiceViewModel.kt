package com.nashaai.assistant.ui.voice

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nashaai.assistant.data.settings.AppSettings
import com.nashaai.assistant.data.settings.SettingsRepository
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Voice interaction states.
 *
 * Idle       — nothing captured; mic button shown
 * Listening  — recognizer active; partialText streams as the user speaks
 * Processing — recognizer emitted endOfSpeech but has not yet finalized
 * Result     — final transcript ready; user can edit or send to Chat
 * Error      — something failed; `needsMicPermission` distinguishes a
 *              permission problem (offer to request) from other errors
 */
sealed class VoiceState {
    object Idle : VoiceState()
    data class Listening(val partialText: String = "") : VoiceState()
    data class Processing(val partialText: String = "") : VoiceState()
    data class Result(val transcript: String) : VoiceState()
    data class Error(
        val message: String,
        val needsMicPermission: Boolean = false
    ) : VoiceState()
}

/**
 * Speech-to-text controller.
 *
 * Safety rules:
 *   - Never starts listening without explicit user action (mic tap).
 *   - Never auto-executes the transcript — the caller sends it to Chat,
 *     where [com.nashaai.assistant.domain.actions.CommandParser] may turn
 *     it into an action PREVIEW (still not executed).
 *   - Never records in the background: recognizer is stopped on stop,
 *     cancel, and onCleared.
 *   - No TTS in this block; a future TtsManager (Block 11) will be wired
 *     through a separate interface. Not referenced here.
 *
 * SpeechRecognizer lifecycle:
 *   - createSpeechRecognizer must run on the main thread. All recognizer
 *     work is dispatched to Dispatchers.Main.
 *   - destroy() is called in onCleared.
 */
class VoiceViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null

    // ---- Public API ---------------------------------------------------

    /**
     * Start one recognition pass. Requires RECORD_AUDIO; if not granted,
     * emits an Error with needsMicPermission=true so the UI can show the
     * system permission flow.
     */
    fun startListening() {
        if (_state.value is VoiceState.Listening) return
        viewModelScope.launch(Dispatchers.Main) {
            val lang = try {
                settingsRepository.settings.first().language
            } catch (_: Exception) {
                "en"
            }
            doStart(lang)
        }
    }

    /** Stop capture early; onResults or onError will follow. */
    fun stopListening() {
        viewModelScope.launch(Dispatchers.Main) {
            try { recognizer?.stopListening() } catch (_: Exception) { }
        }
    }

    /** Fully reset — cancel any pending recognition and go Idle. */
    fun reset() {
        viewModelScope.launch(Dispatchers.Main) {
            try { recognizer?.cancel() } catch (_: Exception) { }
            _state.value = VoiceState.Idle
        }
    }

    /**
     * Call after the system permission dialog resolves.
     * If granted, immediately starts listening (convenience).
     */
    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            startListening()
        } else {
            _state.value = VoiceState.Error(
                message = "Microphone permission denied. You can still type in Chat.",
                needsMicPermission = true
            )
        }
    }

    // ---- Internals ----------------------------------------------------

    private fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        getApplication(),
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    private fun doStart(language: String) {
        if (!hasPermission()) {
            _state.value = VoiceState.Error(
                message = "Microphone permission is needed for voice input.",
                needsMicPermission = true
            )
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(getApplication())) {
            _state.value = VoiceState.Error(
                message = "Speech recognition is not available on this device. " +
                    "You can still type in Chat."
            )
            return
        }

        ensureRecognizer()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeFor(language).toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Do NOT prefer offline — per master plan, we make no offline claims.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        }
        try {
            recognizer?.startListening(intent)
            _state.value = VoiceState.Listening("")
        } catch (_: Exception) {
            _state.value = VoiceState.Error(
                message = "Could not start speech recognition. Try again."
            )
        }
    }

    private fun ensureRecognizer() {
        if (recognizer != null) return
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(getApplication())
                .also { it.setRecognitionListener(listener) }
        } catch (_: Exception) {
            recognizer = null
        }
    }

    private fun localeFor(language: String): Locale = when (language) {
        "ur" -> Locale.forLanguageTag("ur-PK")
        // Documented fallback (Block 3.2 of master plan): Roman Urdu speech
        // uses the en-US recognizer since no dedicated ur-Latn model exists.
        "ur-Latn" -> Locale.forLanguageTag("en-US")
        else -> Locale.forLanguageTag("en-US")
    }

    private val listener: RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { /* no-op */ }
        override fun onBeginningOfSpeech() { /* no-op */ }
        override fun onRmsChanged(rmsdB: Float) { /* no-op */ }
        override fun onBufferReceived(buffer: ByteArray?) { /* no-op */ }
        override fun onEndOfSpeech() {
            val partial = (_state.value as? VoiceState.Listening)?.partialText.orEmpty()
            _state.value = VoiceState.Processing(partial)
        }
        override fun onError(error: Int) {
            _state.value = VoiceState.Error(
                message = errorMessage(error),
                needsMicPermission = error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
            )
        }
        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
                .trim()
            if (text.isEmpty()) {
                _state.value = VoiceState.Error("Didn't catch that. Please try again.")
            } else {
                _state.value = VoiceState.Result(text)
            }
        }
        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotEmpty()) {
                _state.value = VoiceState.Listening(text)
            }
        }
        override fun onEvent(eventType: Int, params: Bundle?) { /* no-op */ }
    }

    private fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
        SpeechRecognizer.ERROR_CLIENT -> "Speech recognition error. Try again."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "Microphone permission is required."
        SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition."
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout."
        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Please try again."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
            "Speech recognizer is busy. Try again in a moment."
        SpeechRecognizer.ERROR_SERVER -> "Speech server error. Try again."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Try again."
        else -> "Speech recognition failed. Try again."
    }

    override fun onCleared() {
        super.onCleared()
        try { recognizer?.cancel() } catch (_: Exception) { }
        try { recognizer?.destroy() } catch (_: Exception) { }
        recognizer = null
    }
}
