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
 * Google Gemini generative language provider.
 *
 * Endpoint:   POST {baseUrl}/v1beta/models/{model}:generateContent
 * Auth:       x-goog-api-key header (NEVER in URL query — avoids leaking the
 *             key into intermediary logs).
 * Request:    { "contents": [ { "role": "user"|"model", "parts": [ { "text": ... } ] } ] }
 * Response:   candidates[0].content.parts[0].text
 */
class GeminiProvider(
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
        val body = buildRequestBody(history)
        return execute(url, config, body)
    }

    override suspend fun testConnection(config: ProviderConfig): Result<String> =
        chat(listOf(ChatTurn.user("ping")), config)

    // ---- internals ----------------------------------------------------

    private fun buildUrl(config: ProviderConfig): String {
        val base = config.baseUrl.trim().trimEnd('/')
        val model = config.model.trim()
        return "$base/v1beta/models/$model:generateContent"
    }

    private fun buildRequestBody(history: List<ChatTurn>): String {
        val contents = history.map { turn ->
            GeminiContentDto(
                role = if (turn.role == ChatTurn.ROLE_ASSISTANT) "model" else "user",
                parts = listOf(GeminiPartDto(turn.text))
            )
        }
        return json.encodeToString(
            GeminiRequestDto.serializer(),
            GeminiRequestDto(contents = contents)
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
                .addHeader("x-goog-api-key", config.apiKey)
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

                val parsed: GeminiResponseDto = try {
                    json.decodeFromString(
                        GeminiResponseDto.serializer(),
                        responseBody
                    )
                } catch (e: Exception) {
                    return@withContext Result.failure(ProviderException.Parse(e))
                }

                val text = parsed.candidates
                    ?.firstOrNull()
                    ?.content
                    ?.parts
                    ?.firstOrNull()
                    ?.text

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
                    lower.contains("api_key") || lower.contains("api key") ||
                        lower.contains("api-key") ->
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

// ---- Wire DTOs (Gemini generateContent) ------------------------------

@Serializable
private data class GeminiPartDto(val text: String)

@Serializable
private data class GeminiContentDto(
    val role: String,
    val parts: List<GeminiPartDto>
)

@Serializable
private data class GeminiRequestDto(val contents: List<GeminiContentDto>)

@Serializable
private data class GeminiCandidateDto(
    val content: GeminiContentDto? = null,
    val finishReason: String? = null
)

@Serializable
private data class GeminiErrorDto(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)

@Serializable
private data class GeminiResponseDto(
    val candidates: List<GeminiCandidateDto>? = null,
    val error: GeminiErrorDto? = null
)
