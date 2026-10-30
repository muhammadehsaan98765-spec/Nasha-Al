package com.nashaai.assistant.util

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Thin, safe wrapper around [TextToSpeech].
 *
 * Design rules:
 *   - Single engine instance per process, created once in AppContainer on
 *     the main thread (Application.onCreate runs on Main; TextToSpeech
 *     requires a Looper thread for construction).
 *   - speak() / stop() / shutdown() are safe to call from any thread after
 *     initialization; the underlying TextToSpeech marshals internally.
 *   - If the device has no TTS engine, or no voice for the requested
 *     locale, [speak] returns false and the caller is informed honestly.
 *   - We do NOT claim offline synthesis. Whether speech is synthesised
 *     locally or streamed from the engine's network is the engine's
 *     decision; we simply invoke it.
 *   - Urdu TTS depends entirely on the installed engine. If the engine has
 *     no Urdu voice, we fall back to English and the caller still gets
 *     true from speak(); a future UI enhancement can surface the fallback.
 */
class TtsManager(context: Context) {

    companion object {
        private const val UTTERANCE_ID = "nasha-tts"
    }

    private val appContext: Context = context.applicationContext

    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var ready: Boolean = false
    @Volatile private var initFailed: Boolean = false
    @Volatile private var lastInitError: String? = null

    // Languages reported available by the engine at init time.
    @Volatile private var availableLanguageTags: Set<String> = emptySet()

    init {
        try {
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ready = true
                    try {
                        availableLanguageTags = tts
                            ?.availableLanguages
                            ?.mapNotNull { it?.toLanguageTag()?.lowercase() }
                            ?.toSet()
                            ?: emptySet()
                        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) { /* no-op */ }
                            override fun onDone(utteranceId: String?) { /* no-op */ }
                            @Deprecated("Deprecated in API 21+")
                            override fun onError(utteranceId: String?) { /* no-op */ }
                            override fun onError(utteranceId: String?, errorCode: Int) {
                                // Silent — no user-visible action.
                            }
                        })
                    } catch (_: Exception) {
                        // Some engines throw on availableLanguages; treat as
                        // empty and rely on per-call setLanguage().
                    }
                } else {
                    initFailed = true
                    lastInitError = "TextToSpeech init failed with status $status"
                    ready = false
                }
            }
        } catch (e: Exception) {
            // No TTS engine installed, or constructor threw (rare on Main).
            initFailed = true
            lastInitError = e.message
            tts = null
        }
    }

    // ---- Status ---------------------------------------------------------

    /** True once the engine reported a successful init. */
    fun isReady(): Boolean = ready

    /** True if init explicitly failed (engine missing or init error). */
    fun hasFailed(): Boolean = initFailed

    /** Human-readable reason init failed, if any. Not shown in UI by default. */
    fun lastError(): String? = lastInitError

    /**
     * True if the engine reports it can speak [languageTag]. Falls back to
     * optimistic true when availableLanguages is empty (some engines do not
     * populate it); speak() will attempt setLanguage() and fall back if
     * needed.
     */
    fun isLanguageSupported(languageTag: String): Boolean {
        if (!ready) return false
        val canonical = canonicalTag(languageTag)
        if (availableLanguageTags.isEmpty()) return true
        return availableLanguageTags.any { tag ->
            tag == canonical || tag.startsWith("$canonical-")
        }
    }

    // ---- Speak / stop / shutdown ---------------------------------------

    /**
     * Queues [text] for speech, replacing any in-progress utterance.
     *
     * @param text               Non-blank text to speak.
     * @param preferredLanguage  BCP-47 tag from the user's settings
     *                           ("en" | "ur" | "ur-Latn").
     * @return true if the utterance was queued; false if TTS is unavailable
     *         or the engine threw.
     */
    fun speak(text: String, preferredLanguage: String = "en"): Boolean {
        if (!ready) return false
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false

        val engine = tts ?: return false
        val resolvedTag = resolveLanguageTag(trimmed, preferredLanguage)

        return try {
            val locale = Locale.forLanguageTag(resolvedTag)
            val result = engine.setLanguage(locale)

            when (result) {
                TextToSpeech.LANG_MISSING_DATA,
                TextToSpeech.LANG_NOT_SUPPORTED -> {
                    // Fall back to English rather than refusing to speak.
                    try { engine.setLanguage(Locale.US) } catch (_: Exception) { }
                }
                else -> Unit
            }

            val speakResult = engine.speak(
                trimmed,
                TextToSpeech.QUEUE_FLUSH,
                Bundle(),
                UTTERANCE_ID
            )
            speakResult == TextToSpeech.SUCCESS
        } catch (_: Exception) {
            false
        }
    }

    /** Stop any in-progress speech. Safe to call at any time. */
    fun stop() {
        try { tts?.stop() } catch (_: Exception) { /* no-op */ }
    }

    /**
     * Release the engine. After this call the instance is unusable.
     * AppContainer does not call shutdown() in production (process teardown
     * releases the engine); this exists for tests and future foreground
     * service use.
     */
    fun shutdown() {
        try { tts?.stop() } catch (_: Exception) { /* no-op */ }
        try { tts?.shutdown() } catch (_: Exception) { /* no-op */ }
        tts = null
        ready = false
        availableLanguageTags = emptySet()
    }

    // ---- Language resolution -------------------------------------------

    /**
     * Decides which BCP-47 tag to request for [text]:
     *   1. If text contains Arabic/Urdu script (U+0600–U+06FF), use "ur".
     *   2. Otherwise map the user's preference:
     *        "ur"      → "ur"
     *        "ur-Latn" → "en"  (Roman Urdu; no dedicated TTS voice)
     *        anything else → "en"
     */
    private fun resolveLanguageTag(text: String, preferred: String): String {
        if (text.any { it in '\u0600'..'\u06FF' }) return "ur"
        return when (preferred) {
            "ur" -> "ur"
            "ur-Latn" -> "en"
            else -> "en"
        }
    }

    private fun canonicalTag(tag: String): String {
        val parts = tag.split('-')
        return parts.firstOrNull()?.lowercase().orEmpty()
    }
}
