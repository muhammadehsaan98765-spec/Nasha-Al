package com.nashaai.assistant.data.provider

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GeminiProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var provider: GeminiProvider

    @Before fun setUp() {
        server = MockWebServer(); server.start(); provider = GeminiProvider()
    }

    @After fun tearDown() {
        try { server.shutdown() } catch (_: Exception) { }
    }

    private fun config(timeoutSeconds: Long = 5): ProviderConfig = ProviderConfig(
        baseUrl = server.url("/").toString().trimEnd('/'),
        model = "gemini-2.0-flash", apiKey = "test-api-key", timeoutSeconds = timeoutSeconds
    )

    private fun okBody(text: String = "Hello!"): String =
        """{"candidates":[{"content":{"role":"model","parts":[{"text":"$text"}]}}]}"""

    @Test fun successfulResponse_returnsReplyText() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(okBody("Hi there")))
        val result = provider.chat(listOf(ChatTurn.user("Hello")), config())
        assertTrue(result.isSuccess); assertEquals("Hi there", result.getOrNull())
    }

    @Test fun apiKeyIsSentInHeader_notInUrl() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(okBody()))
        provider.chat(listOf(ChatTurn.user("hi")), config())
        val recorded = server.takeRequest()
        assertEquals("test-api-key", recorded.getHeader("x-goog-api-key"))
        val url = recorded.requestUrl?.toString().orEmpty()
        assertFalse("key leaked to URL: $url", url.contains("test-api-key"))
        assertFalse("key= query present", url.contains("key="))
    }

    @Test fun requestPathHitsGenerateContentEndpoint() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(okBody()))
        provider.chat(listOf(ChatTurn.user("hi")), config())
        val path = server.takeRequest().path.orEmpty()
        assertTrue("unexpected path: $path", path.contains("/v1beta/models/gemini-2.0-flash:generateContent"))
    }

    @Test fun emptyCandidates_returnsParseFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"candidates":[]}"""))
        val result = provider.chat(listOf(ChatTurn.user("hi")), config())
        assertTrue(result.isFailure); assertTrue(result.exceptionOrNull() is ProviderException.Parse)
    }

    @Test fun malformedJson_returnsParseFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not-json"))
        val result = provider.chat(listOf(ChatTurn.user("hi")), config())
        assertTrue(result.isFailure); assertTrue(result.exceptionOrNull() is ProviderException.Parse)
    }

    @Test fun unauthorized_mapsToUnauthorized() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        val result = provider.chat(listOf(ChatTurn.user("hi")), config())
        assertTrue(result.exceptionOrNull() is ProviderException.Unauthorized)
    }

    @Test fun forbidden_mapsToForbidden() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("{}"))
        val result = provider.chat(listOf(ChatTurn.user("hi")), config())
        assertTrue(result.exceptionOrNull() is ProviderException.Forbidden)
    }

    @Test fun rateLimited_mapsToRateLimited() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("{}"))
        val result = provider.chat(listOf(ChatTurn.user("hi")), config())
        assertTrue(result.exceptionOrNull() is ProviderException.RateLimited)
    }

    @Test fun serverError_mapsToServer() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        val result = provider.chat(listOf(ChatTurn.user("hi")), config())
        val ex = result.exceptionOrNull()
        assertTrue(ex is ProviderException.Server); assertEquals(500, (ex as ProviderException.Server).code)
    }

    @Test fun slowResponse_timeoutOrNetworkFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(okBody()).setBodyDelay(5, TimeUnit.SECONDS))
        val result = provider.chat(listOf(ChatTurn.user("hi")), config(timeoutSeconds = 1))
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue("expected Timeout or Network, got ${ex?.javaClass?.simpleName}",
            ex is ProviderException.Timeout || ex is ProviderException.Network)
    }

    @Test fun blankApiKey_returnsInvalidConfig() = runTest {
        val bad = ProviderConfig(
            baseUrl = server.url("/").toString().trimEnd('/'),
            model = "gemini-2.0-flash", apiKey = "", timeoutSeconds = 5
        )
        val result = provider.chat(listOf(ChatTurn.user("hi")), bad)
        assertTrue(result.isFailure); assertTrue(result.exceptionOrNull() is ProviderException.InvalidConfig)
    }

    @Test fun emptyHistory_returnsInvalidConfig() = runTest {
        val result = provider.chat(emptyList(), config())
        assertTrue(result.isFailure); assertTrue(result.exceptionOrNull() is ProviderException.InvalidConfig)
    }
}
