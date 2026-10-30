package com.nashaai.assistant.data.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * OpenAI-compatible chat-completions provider.
 *
 * Works with any service exposing POST {baseUrl}/chat/completions with a
 * Bearer token — e.g. OpenAI, OpenRouter, Groq, Together, or a local
 * Ollama instance at http://10.0.2.2:11434/v1.
 *
 * Endpoint:   POST {baseUrl}/chat/completions
 * Auth:       Authorization: Bearer {apiKey}
 * Request:    { "model": ..., "messages": [ { "role": "system|user|assistant", "content": ... } ] }
 * Response:   choices[0].message.content
 *
 * Cleartext HTTP is blocked for non-loopback hosts by [ProviderConfigValidator]
 * BEFORE any request is built, so keys are never sent over plain http to the
 * public internet.
 */
class OpenAiCompatibleProvider(
    private val clientFactory: () -> OkHttpClient = ::defaultClient
) : AiProvider {

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        private val json: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }

        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
    }

    override suspend fun chat(
        history: List<ChatTurn>,
        config: ProviderConfig
    ): Result<String> {
        when (val v = ProviderConfigValidator.validate(config)) {
            is ProviderValidationResult.Invalid ->
                return Result.failure(ProviderException.InvalidConfig(v.reason))
            ProviderValidationResult.Valid -> Unit
        }
        if (history.isEmpty()) {
            return Result.failure(ProviderException.InvalidConfig("History is empty"))
        }

        val url = buildUrl(config)
        val body = buildRequestBody(history, config)
        return execute(url, config, body)
    }

    override suspend fun testConnection(config: ProviderConfig): Result<String> =
        chat(listOf(ChatTurn.user("ping")), config)

    // ---- internals ----------------------------------------------------

    private fun buildUrl(config: ProviderConfig): String {
        val base = config.baseUrl.trim().trimEnd('/')
        return "$base/chat/completions"
    }

    private fun buildRequestBody(history: List<ChatTurn>, config: ProviderConfig): String {
        val messages = history.map { turn ->
            OpenAiMessageDto(
                role = when (turn.role) {
                    ChatTurn.ROLE_ASSISTANT -> "assistant"
                    "system" -> "system"
                    else -> "user"
                },
                content = turn.text
            )
        }
        return json.encodeToString(
            OpenAiRequestDto.serializer(),
            OpenAiRequestDto(model = config.model.trim(), messages = messages)
        )
    }

    private suspend fun execute(
        url: String,
        config: ProviderConfig,
        body: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val client = clientFactory().newBuilder()
            .callTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
            .build()

        val request = try {
            Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(JSON_MEDIA))
                .build()
        } catch (e: IllegalArgumentException) {
            return@withContext Result.failure(
                ProviderException.InvalidConfig("URL is invalid", e)
            )
        }

        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        mapHttpError(response.code, responseBody)
                    )
                }

                val parsed: OpenAiResponseDto = try {
                    json.decodeFromString(
                        OpenAiResponseDto.serializer(),
                        responseBody
                    )
                } catch (e: Exception) {
                    return@withContext Result.failure(ProviderException.Parse(e))
                }

                val text = parsed.choices
                    ?.firstOrNull()
                    ?.message
                    ?.content

                if (text.isNullOrBlank()) {
                    val detail = parsed.error?.message ?: "empty response"
                    return@withContext Result.failure(
                        ProviderException.Parse(IllegalStateException(detail))
                    )
                }

                Result.success(text)
            }
        } catch (e: SocketTimeoutException) {
            Result.failure(ProviderException.Timeout(e))
        } catch (e: IOException) {
            Result.failure(ProviderException.Network(e))
        } catch (e: Exception) {
            Result.failure(ProviderException.Network(e))
        }
    }

    private fun mapHttpError(code: Int, body: String): ProviderException {
        val trimmed = body.take(500)
        return when (code) {
            401 -> ProviderException.Unauthorized()
            403 -> ProviderException.Forbidden()
            429 -> ProviderException.RateLimited()
            in 500..599 -> ProviderException.Server(code, trimmed)
            in 400..499 -> {
                val lower = trimmed.lowercase()
                when {
                    lower.contains("api key") || lower.contains("api_key") ||
                        lower.contains("invalid_api_key") ->
                        ProviderException.Unauthorized()
                    else -> ProviderException.Parse(
                        IllegalStateException("HTTP $code: ${trimmed.take(300)}")
                    )
                }
            }
            else -> ProviderException.Server(code, trimmed)
        }
    }
}

// ---- Wire DTOs (OpenAI chat-completions) -----------------------------

@Serializable
private data class OpenAiMessageDto(val role: String, val content: String)

@Serializable
private data class OpenAiRequestDto(
    val model: String,
    val messages: List<OpenAiMessageDto>
)

@Serializable
private data class OpenAiChoiceDto(
    val message: OpenAiMessageDto? = null,
    val finishReason: String? = null
)

@Serializable
private data class OpenAiErrorDto(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)

@Serializable
private data class OpenAiResponseDto(
    val choices: List<OpenAiChoiceDto>? = null,
    val error: OpenAiErrorDto? = null
)
