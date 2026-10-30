package com.nashaai.assistant.data.search

import com.nashaai.assistant.data.provider.ProviderException
import com.nashaai.assistant.data.settings.SearchEngine
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** A single search result row, normalized across engines. */
data class SearchResult(
    val title: String,
    val url: String,
    val snippet: String
)

/**
 * Search layer:
 *   - [buildBrowserUrl]: pure function producing a URL that opens in any
 *     browser (no API key, no network call here).
 *   - [braveSearch]: real HTTP call to the Brave Search API using the
 *     caller-supplied API key. Key is NEVER placed in the URL query and
 *     NEVER logged.
 *
 * The Brave API key is loaded by the caller from
 * [com.nashaai.assistant.data.settings.SecureKeyStore.ALIAS_BRAVE_SEARCH_KEY]
 * and passed in explicitly so this class has no dependency on Android
 * context and remains unit-testable.
 */
class SearchProvider(
    private val clientFactory: () -> OkHttpClient = ::defaultClient
) {

    companion object {
        private const val BRAVE_ENDPOINT = "https://api.search.brave.com/res/v1/web/search"

        private val json: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }

        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()

        /**
         * Builds a browser-openable URL for [query] given the configured
         * [engine]. For [SearchEngine.CUSTOM], uses [customTemplate] verbatim
         * (the template must end with `=` or `?q=` style prefix).
         *
         * Pure function — no Android framework calls, safe for JVM tests.
         */
        fun buildBrowserUrl(
            engine: SearchEngine,
            query: String,
            customTemplate: String = ""
        ): String {
            val encoded = encodeQuery(query)
            val base = when (engine) {
                SearchEngine.GOOGLE ->
                    "https://www.google.com/search?q="
                SearchEngine.BING ->
                    "https://www.bing.com/search?q="
                SearchEngine.DUCKDUCKGO ->
                    "https://duckduckgo.com/?q="
                SearchEngine.BRAVE ->
                    "https://search.brave.com/search?q="
                SearchEngine.CUSTOM ->
                    customTemplate.ifBlank { "https://www.google.com/search?q=" }
            }
            return if (base.endsWith("=") || base.endsWith("?") || base.endsWith("&")) {
                base + encoded
            } else if (base.contains("?")) {
                "$base&q=$encoded"
            } else {
                "$base?q=$encoded"
            }
        }

        /**
         * Percent-encodes [raw] for use in a URL query value.
         * Uses %20 for spaces (not '+'), matching what most search engines
         * expect in path/query and preserving exact characters. This helper
         * is intentionally NOT android.net.Uri.encode so tests can run on
         * a plain JVM.
         */
        fun encodeQuery(raw: String): String = try {
            URLEncoder.encode(raw, StandardCharsets.UTF_8.name())
                .replace("+", "%20")
        } catch (_: Exception) {
            raw
        }
    }

    /**
     * Queries the Brave Search API.
     *
     * @param query            User query (encoded internally).
     * @param apiKey           Brave Search API key (from SecureKeyStore).
     * @param limit            1..20 results (clamped).
     * @param safeSearch       true → safesearch=strict; false → safesearch=off.
     * @param timeoutSeconds   Per-request timeout.
     */
    suspend fun braveSearch(
        query: String,
        apiKey: String,
        limit: Int = 5,
        safeSearch: Boolean = true,
        timeoutSeconds: Long = 20L
    ): Result<List<SearchResult>> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            return@withContext Result.failure(
                ProviderException.InvalidConfig("Search query is empty.")
            )
        }
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                ProviderException.InvalidConfig(
                    "Brave Search API key is missing. Add it in Settings."
                )
            )
        }

        val clamped = limit.coerceIn(1, 20)
        val safeParam = if (safeSearch) "strict" else "off"
        val url = buildString {
            append(BRAVE_ENDPOINT)
            append("?q=").append(encodeQuery(query))
            append("&count=").append(clamped)
            append("&safesearch=").append(safeParam)
        }

        val client = clientFactory().newBuilder()
            .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build()

        val request = try {
            Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .addHeader("X-Subscription-Token", apiKey)
                .get()
                .build()
        } catch (e: IllegalArgumentException) {
            return@withContext Result.failure(
                ProviderException.InvalidConfig("Search URL is invalid.", e)
            )
        }

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        mapHttpError(response.code, body)
                    )
                }
                val parsed = try {
                    json.decodeFromString(BraveResponse.serializer(), body)
                } catch (e: Exception) {
                    return@withContext Result.failure(ProviderException.Parse(e))
                }
                val results = parsed.web?.results
                    ?.map {
                        SearchResult(
                            title = it.title.orEmpty(),
                            url = it.url.orEmpty(),
                            snippet = it.description.orEmpty()
                        )
                    }
                    ?.filter { it.url.isNotBlank() }
                    ?: emptyList()
                Result.success(results)
            }
        } catch (e: SocketTimeoutException) {
            Result.failure(ProviderException.Timeout(e))
        } catch (e: IOException) {
            Result.failure(ProviderException.Network(e))
        } catch (e: Exception) {
            Result.failure(ProviderException.Network(e))
        }
    }

    // ---- internals ----------------------------------------------------

    private fun mapHttpError(code: Int, body: String): ProviderException {
        val trimmed = body.take(500)
        return when (code) {
            401 -> ProviderException.Unauthorized()
            403 -> ProviderException.Forbidden()
            429 -> ProviderException.RateLimited()
            in 500..599 -> ProviderException.Server(code, trimmed)
            in 400..499 -> ProviderException.Parse(
                IllegalStateException("HTTP $code: ${trimmed.take(300)}")
            )
            else -> ProviderException.Server(code, trimmed)
        }
    }
}

// ---- Wire DTOs (Brave web search response) ---------------------------

@Serializable
private data class BraveResult(
    val title: String? = null,
    val url: String? = null,
    val description: String? = null
)

@Serializable
private data class BraveWeb(
    val results: List<BraveResult>? = null
)

@Serializable
private data class BraveResponse(
    val web: BraveWeb? = null
)
