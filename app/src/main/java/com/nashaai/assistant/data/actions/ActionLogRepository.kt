package com.nashaai.assistant.data.actions

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.actionLogDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "nasha_action_log")

/**
 * One redacted entry in the action log.
 *
 * SECURITY: never put message bodies, search query strings, screen text,
 * file paths, or any user-provided content in [detail]. Only short status
 * codes and non-sensitive labels are allowed (e.g. "accessibility_disabled",
 * "share_sheet_opened"). Message content is explicitly forbidden by the
 * project's privacy invariants.
 */
@Serializable
data class ActionLogEntry(
    val timestamp: Long,
    val type: String,
    val risk: String,
    val success: Boolean,
    val detail: String = ""
)

/**
 * Rolling on-device action log.
 *
 * Persisted as a single JSON blob under one DataStore key. Capped at
 * [MAX_ENTRIES]; when full, the oldest entries are dropped first.
 *
 * This repository is intentionally decoupled from the ActionEngine's own
 * model classes (which live in Block 5). Actions pass their type/risk as
 * plain strings; the engine will serialize [com.nashaai.assistant.domain.action.ActionType]
 * via `.name` and [com.nashaai.assistant.domain.action.ActionRisk] via `.name`.
 */
class ActionLogRepository(private val context: Context) {

    private companion object {
        const val MAX_ENTRIES = 200
        val LOG_JSON = stringPreferencesKey("action_log_json")

        val json: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }
    }

    @Serializable
    private data class LogWrapper(
        val entries: List<ActionLogEntry> = emptyList()
    )

    /** Reactive stream of the current log (newest-first). */
    val log: Flow<List<ActionLogEntry>> = context.actionLogDataStore.data.map { prefs ->
        decode(prefs[LOG_JSON]).sortedByDescending { it.timestamp }
    }

    /** Appends an entry, rotating out the oldest when over the cap. */
    suspend fun append(entry: ActionLogEntry): Unit = withContext(Dispatchers.IO) {
        val current = decode(
            context.actionLogDataStore.data.first()[LOG_JSON]
        )
        val combined = (current + entry).sortedByDescending { it.timestamp }
        val trimmed = combined.take(MAX_ENTRIES)
        write(trimmed)
    }

    suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        write(emptyList())
    }

    suspend fun snapshot(): List<ActionLogEntry> =
        log.first()

    // ---- Internals ----------------------------------------------------

    private fun decode(raw: String?): List<ActionLogEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(LogWrapper.serializer(), raw).entries
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun write(list: List<ActionLogEntry>) {
        val payload = json.encodeToString(
            LogWrapper.serializer(),
            LogWrapper(list)
        )
        context.actionLogDataStore.edit { prefs ->
            prefs[LOG_JSON] = payload
        }
    }
}
