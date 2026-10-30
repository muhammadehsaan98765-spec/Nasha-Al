package com.nashaai.assistant.data.provider

/**
 * One turn in a chat conversation.
 *
 * Canonical roles used by the app:
 *   - "user"      — the human
 *   - "assistant" — the model
 *
 * Providers translate these to their own wire format internally
 * (Gemini uses "user"/"model"; OpenAI uses "user"/"assistant").
 */
data class ChatTurn(
    val role: String,
    val text: String
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"

        fun user(text: String): ChatTurn = ChatTurn(ROLE_USER, text)
        fun assistant(text: String): ChatTurn = ChatTurn(ROLE_ASSISTANT, text)
    }
}

/**
 * Provider configuration passed to [AiProvider.chat] / [AiProvider.testConnection].
 *
 * The [apiKey] is loaded from [com.nashaai.assistant.data.settings.SecureKeyStore]
 * by the caller; this class does NOT persist anything and MUST NOT be logged.
 */
data class ProviderConfig(
    val baseUrl: String,
    val model: String,
    val apiKey: String,
    val timeoutSeconds: Long = 30L
)

/**
 * Abstract AI provider.
 *
 * Implementations must:
 *   - Fail fast (return [Result.failure]) when config is invalid.
 *   - Never log or expose [ProviderConfig.apiKey].
 *   - Map HTTP/network failures to typed [ProviderException] subtypes.
 */
interface AiProvider {
    /**
     * Sends [history] to the provider and returns the assistant reply text.
     * Never returns blank text on success.
     */
    suspend fun chat(history: List<ChatTurn>, config: ProviderConfig): Result<String>

    /**
     * Minimal liveness check. Sends a tiny prompt and returns the reply.
     * Same error semantics as [chat].
     */
    suspend fun testConnection(config: ProviderConfig): Result<String>
}
