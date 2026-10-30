package com.nashaai.assistant.data.chat

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nashaai.assistant.data.provider.AiProvider
import com.nashaai.assistant.data.provider.ChatTurn
import com.nashaai.assistant.data.provider.GeminiProvider
import com.nashaai.assistant.data.provider.OpenAiCompatibleProvider
import com.nashaai.assistant.data.provider.ProviderConfig
import com.nashaai.assistant.data.provider.ProviderException
import com.nashaai.assistant.data.settings.AiProviderType
import com.nashaai.assistant.data.settings.SecureKeyStore
import com.nashaai.assistant.data.settings.SettingsRepository
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// A dedicated DataStore file for chat history. Kept separate from
// "nasha_settings" so chat persistence can be cleared independently.
private val Context.chatDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "nasha_chats")

/**
 * One persisted turn inside a chat session.
 * Kept separate from [ChatTurn] because that lives in the provider layer
 * and is intentionally NOT annotated @Serializable.
 */
@Serializable
data class SessionTurn(val role: String, val text: String) {
    fun toChatTurn(): ChatTurn = ChatTurn(role, text)

    companion object {
        fun from(t: ChatTurn): SessionTurn = SessionTurn(t.role, t.text)
    }
}

/**
 * A saved conversation. Immutable; the store replaces whole sessions on
 * updates so concurrent readers never see partial state.
 */
@Serializable
data class ChatSession(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val turns: List<SessionTurn>
) {
    companion object {
        fun new(title: String = "New chat"): ChatSession {
            val now = System.currentTimeMillis()
            return ChatSession(
                id = UUID.randomUUID().toString(),
                title = title,
                createdAt = now,
                updatedAt = now,
                turns = emptyList()
            )
        }

        /** Derives a short title from the first user message, if any. */
        fun deriveTitle(turns: List<SessionTurn>): String {
            val firstUser = turns.firstOrNull { it.role == ChatTurn.ROLE_USER } ?: return "New chat"
            val trimmed = firstUser.text.trim().replace('\n', ' ')
            return if (trimmed.length <= 60) trimmed else trimmed.take(57) + "…"
        }
    }
}

/**
 * Chat history + AI send orchestration.
 *
 * Responsibilities:
 *   1. Resolve the current [com.nashaai.assistant.data.settings.AppSettings]
 *      and the encrypted API key.
 *   2. Build a [ProviderConfig] and dispatch to the selected [AiProvider].
 *   3. Persist sessions to DataStore (JSON blob, single key).
 *
 * The repository does NOT decide whether to save; the caller (ChatViewModel)
 * checks `settings.saveHistory` before calling [saveSession]. This keeps the
 * repository's API side-effect-free enough to unit test.
 */
class ChatRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val secureKeyStore: SecureKeyStore,
    private val providerFactory: (AiProviderType) -> AiProvider = { type ->
        when (type) {
            AiProviderType.GEMINI -> GeminiProvider()
            AiProviderType.OPENAI_COMPATIBLE -> OpenAiCompatibleProvider()
        }
    }
) {

    private companion object {
        val SESSIONS_JSON = stringPreferencesKey("chat_sessions_json")

        val json: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }
    }

    @Serializable
    private data class SessionsWrapper(
        val sessions: List<ChatSession> = emptyList()
    )

    // ---- Send ---------------------------------------------------------

    /**
     * Sends [history] to the configured AI provider.
     *
     * @param history Ordered oldest-to-newest turns. The last turn is
     *                typically the new user message.
     * @param configOverride Optional explicit config (used by tests and by
     *                       the Settings "Test connection" button when it
     *                       tests an unsaved key). When null, config is
     *                       resolved from Settings + SecureKeyStore.
     */
    suspend fun sendMessage(
        history: List<ChatTurn>,
        configOverride: ProviderConfig? = null
    ): Result<String> {
        if (history.isEmpty()) {
            return Result.failure(
                ProviderException.InvalidConfig("Cannot send an empty conversation.")
            )
        }

        val config = configOverride ?: resolveConfig()
            ?: return Result.failure(
                ProviderException.InvalidConfig(
                    "No API key. Open Settings and add your provider key."
                )
            )

        val provider = providerFactory(resolveProviderType(configOverride))
        return provider.chat(history, config)
    }

    /**
     * Sends a single "ping" to the provider with the current settings.
     * Used by Settings → "Test connection" and Diagnostics → "Provider test".
     */
    suspend fun testConnection(
        configOverride: ProviderConfig? = null
    ): Result<String> {
        val config = configOverride ?: resolveConfig()
            ?: return Result.failure(
                ProviderException.InvalidConfig(
                    "No API key. Open Settings and add your provider key."
                )
            )
        val provider = providerFactory(resolveProviderType(configOverride))
        return provider.testConnection(config)
    }

    /**
     * Builds a [ProviderConfig] from current settings + encrypted key,
     * or null if the API key is not set.
     */
    suspend fun resolveConfig(): ProviderConfig? {
        val settings = settingsRepository.settings.first()
        val apiKey = secureKeyStore.load(SecureKeyStore.ALIAS_AI_API_KEY)
        if (apiKey.isNullOrBlank()) return null
        return ProviderConfig(
            baseUrl = settings.baseUrl,
            model = settings.model,
            apiKey = apiKey,
            timeoutSeconds = settings.requestTimeoutSeconds
        )
    }

    private suspend fun resolveProviderType(
        override: ProviderConfig?
    ): AiProviderType {
        // If an override was supplied, keep the currently selected type; the
        // override is just different URL/model/key for the same wire protocol.
        return settingsRepository.settings.first().providerType
    }

    // ---- Persistence --------------------------------------------------

    val sessions: Flow<List<ChatSession>> =
        context.chatDataStore.data.map { prefs ->
            decodeSessions(prefs[SESSIONS_JSON])
        }

    suspend fun listSessions(): List<ChatSession> = sessions.first()

    suspend fun loadSession(id: String): ChatSession? =
        listSessions().firstOrNull { it.id == id }

    /** Inserts or replaces a session by id, then returns the new session list. */
    suspend fun saveSession(session: ChatSession): Result<List<ChatSession>> =
        withContext(Dispatchers.IO) {
            try {
                val current = listSessions()
                val updated = current.toMutableList()
                val idx = updated.indexOfFirst { it.id == session.id }
                val toSave = session.copy(updatedAt = System.currentTimeMillis())
                if (idx >= 0) updated[idx] = toSave else updated.add(toSave)
                // Newest-updated first for the History list.
                val sorted = updated.sortedByDescending { it.updatedAt }
                writeSessions(sorted)
                Result.success(sorted)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun deleteSession(id: String): Result<List<ChatSession>> =
        withContext(Dispatchers.IO) {
            try {
                val remaining = listSessions().filterNot { it.id == id }
                writeSessions(remaining)
                Result.success(remaining)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun clearAll(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            writeSessions(emptyList())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Renders [session] as plain text suitable for SAF export.
     * Returns null if the id is unknown.
     */
    suspend fun exportSessionAsText(id: String): String? {
        val session = loadSession(id) ?: return null
        val sb = StringBuilder()
        sb.append("# ").append(session.title).append('\n')
        sb.append("Created: ")
            .append(java.time.Instant.ofEpochMilli(session.createdAt).toString())
            .append('\n')
        sb.append("Updated: ")
            .append(java.time.Instant.ofEpochMilli(session.updatedAt).toString())
            .append("\n\n")
        session.turns.forEach { turn ->
            sb.append("## ").append(turn.role).append('\n')
            sb.append(turn.text).append("\n\n")
        }
        return sb.toString()
    }

    // ---- Internals ----------------------------------------------------

    private fun decodeSessions(raw: String?): List<ChatSession> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(SessionsWrapper.serializer(), raw).sessions
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun writeSessions(list: List<ChatSession>) {
        val payload = json.encodeToString(
            SessionsWrapper.serializer(),
            SessionsWrapper(list)
        )
        context.chatDataStore.edit { prefs ->
            prefs[SESSIONS_JSON] = payload
        }
    }
}
