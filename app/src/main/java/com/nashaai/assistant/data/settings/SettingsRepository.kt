package com.nashaai.assistant.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Provider selection enum. Values are persisted by name. */
enum class AiProviderType {
    GEMINI,
    OPENAI_COMPATIBLE
}

/** Search engine selection enum. Values are persisted by name. */
enum class SearchEngine {
    GOOGLE,
    BING,
    DUCKDUCKGO,
    BRAVE,
    CUSTOM
}

/**
 * Aggregated app settings snapshot.
 *
 * This is a plain data class; the repository exposes it as a hot Flow so
 * ViewModels can `collectAsStateWithLifecycle()` and Compose recomposes
 * automatically when any individual field changes.
 */
data class AppSettings(
    val providerType: AiProviderType = AiProviderType.GEMINI,
    val baseUrl: String = DEFAULT_GEMINI_BASE_URL,
    val model: String = DEFAULT_GEMINI_MODEL,
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val customSearchUrl: String = DEFAULT_GOOGLE_SEARCH_URL,
    val resultLimit: Int = 5,
    val safeSearch: Boolean = true,
    val requestTimeoutSeconds: Long = 30L,
    val language: String = "en",
    val saveHistory: Boolean = true,
    val onboardingComplete: Boolean = false,
    val aiActionSuggestionsEnabled: Boolean = false
) {
    companion object {
        const val DEFAULT_GEMINI_BASE_URL = "https://generativelanguage.googleapis.com"
        const val DEFAULT_GEMINI_MODEL = "gemini-2.0-flash"
        const val DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_OPENAI_MODEL = "gpt-4o-mini"
        const val DEFAULT_GOOGLE_SEARCH_URL = "https://www.google.com/search?q="
        const val DEFAULT_BING_SEARCH_URL = "https://www.bing.com/search?q="
        const val DEFAULT_DDG_SEARCH_URL = "https://duckduckgo.com/?q="
    }
}

// File-level DataStore delegate; one instance per process.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nasha_settings")

/**
 * Persistent settings store backed by Jetpack DataStore (Preferences).
 *
 * All writes are suspend; reads are a single hot Flow. DataStore handles
 * atomic writes and cross-process consistency (though Nasha AI is single-
 * process, the discipline is good hygiene).
 *
 * Note: this is NOT where API keys live. API keys are stored in
 * [SecureKeyStore] because they must be encrypted with Android Keystore,
 * not stored in the plaintext DataStore file.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val PROVIDER_TYPE = stringPreferencesKey("provider_type")
        val BASE_URL = stringPreferencesKey("base_url")
        val MODEL = stringPreferencesKey("model")
        val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val CUSTOM_SEARCH_URL = stringPreferencesKey("custom_search_url")
        val RESULT_LIMIT = intPreferencesKey("result_limit")
        val SAFE_SEARCH = booleanPreferencesKey("safe_search")
        val TIMEOUT_SECONDS = longPreferencesKey("timeout_seconds")
        val LANGUAGE = stringPreferencesKey("language")
        val SAVE_HISTORY = booleanPreferencesKey("save_history")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val AI_ACTION_SUGGESTIONS = booleanPreferencesKey("ai_action_suggestions_enabled")
    }

    /** Reactive snapshot of all app settings. */
    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            providerType = safeEnum(
                p[Keys.PROVIDER_TYPE],
                AiProviderType.GEMINI,
                AiProviderType::valueOf
            ),
            baseUrl = p[Keys.BASE_URL] ?: AppSettings.DEFAULT_GEMINI_BASE_URL,
            model = p[Keys.MODEL] ?: AppSettings.DEFAULT_GEMINI_MODEL,
            searchEngine = safeEnum(
                p[Keys.SEARCH_ENGINE],
                SearchEngine.GOOGLE,
                SearchEngine::valueOf
            ),
            customSearchUrl = p[Keys.CUSTOM_SEARCH_URL] ?: AppSettings.DEFAULT_GOOGLE_SEARCH_URL,
            resultLimit = (p[Keys.RESULT_LIMIT] ?: 5).coerceIn(1, 20),
            safeSearch = p[Keys.SAFE_SEARCH] ?: true,
            requestTimeoutSeconds = (p[Keys.TIMEOUT_SECONDS] ?: 30L).coerceIn(5L, 300L),
            language = normalizeLanguageTag(p[Keys.LANGUAGE] ?: "en"),
            saveHistory = p[Keys.SAVE_HISTORY] ?: true,
            onboardingComplete = p[Keys.ONBOARDING_COMPLETE] ?: false,
            aiActionSuggestionsEnabled = p[Keys.AI_ACTION_SUGGESTIONS] ?: false
        )
    }

    // ---- Mutators -----------------------------------------------------

    suspend fun updateProvider(type: AiProviderType) {
        context.dataStore.edit { it[Keys.PROVIDER_TYPE] = type.name }
    }

    suspend fun updateBaseUrl(url: String) {
        context.dataStore.edit { it[Keys.BASE_URL] = url.trim() }
    }

    suspend fun updateModel(model: String) {
        context.dataStore.edit { it[Keys.MODEL] = model.trim() }
    }

    suspend fun updateSearchEngine(engine: SearchEngine) {
        context.dataStore.edit { it[Keys.SEARCH_ENGINE] = engine.name }
    }

    suspend fun updateCustomSearchUrl(url: String) {
        context.dataStore.edit { it[Keys.CUSTOM_SEARCH_URL] = url.trim() }
    }

    suspend fun updateResultLimit(limit: Int) {
        context.dataStore.edit { it[Keys.RESULT_LIMIT] = limit.coerceIn(1, 20) }
    }

    suspend fun updateSafeSearch(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SAFE_SEARCH] = enabled }
    }

    suspend fun updateTimeoutSeconds(seconds: Long) {
        context.dataStore.edit { it[Keys.TIMEOUT_SECONDS] = seconds.coerceIn(5L, 300L) }
    }

    suspend fun updateLanguage(tag: String) {
        context.dataStore.edit { it[Keys.LANGUAGE] = normalizeLanguageTag(tag) }
    }

    suspend fun updateSaveHistory(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SAVE_HISTORY] = enabled }
    }

    suspend fun markOnboardingComplete() {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = true }
    }

    suspend fun setAiActionSuggestions(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AI_ACTION_SUGGESTIONS] = enabled }
    }

    /** Wipes every DataStore preference. Does NOT touch SecureKeyStore. */
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    // ---- Helpers ------------------------------------------------------

    private fun normalizeLanguageTag(raw: String): String = when (raw) {
        "en", "ur", "ur-Latn" -> raw
        "ur_PK", "ur-PK" -> "ur"
        "roman", "roman-urdu", "ur_Latn" -> "ur-Latn"
        else -> "en"
    }

    private inline fun <reified E : Enum<E>> safeEnum(
        raw: String?,
        default: E,
        parser: (String) -> E
    ): E = try {
        if (raw.isNullOrBlank()) default else parser(raw)
    } catch (_: IllegalArgumentException) {
        default
    }
}
