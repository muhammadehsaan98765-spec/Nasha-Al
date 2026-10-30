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

class OpenAiCompatibleProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var provider: OpenAiCompatibleProvider

    @Before fun setUp() {
        server = MockWebServer(); server.start(); provider = OpenAiCompatibleProvider()
    }

    @After fun tearDown() {
        try { server.shutdown() } catch (_: Exception) { }
    }

    private fun config(timeoutSeconds: Long = 5): ProviderConfig = ProviderConfig(
        baseUrl = server.url("/").toString().trimEnd('/'),
        model = "gpt-4o-mini", apiKey = "test-bearer-123", timeoutSeconds = timeoutSeconds
    )

    private fun okBody(content: String = "Hi back!"): String =
        """{"choices":[{"message":{"role":"assistant","content":"$content"}}]}"""

    @Test fun successfulResponse_returnsReplyText() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(okBody("OK")))
        val result = provider.chat(listOf(ChatTurn.user("Hello")), config())
        assertTrue(result.isSuccess); assertEquals("OK", result.getOrNull())
    }

    @Test fun bearerTokenInAuthorizationHeader() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(okBody()))
        provider.chat(listOf(ChatTurn.user("hi")), config())
        val recorded = server.takeRequest()
        assertEquals("Bearer test-bearer-123", recorded.getHeader("Authorization"))
        val url = recorded.requestUrl?.toString().orEmpty()
        assertFalse("bearer leaked to URL", url.contains("test-bearer-123"))
    }

    @Test fun requestPathHitsChatCompletions() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(okBody()))
        provider.chat(listOf(ChatTurn.user("hi")), config())
        val path = server.takeRequest().path.orEmpty()
        assertTrue("unexpected path: $path", path.contains("/chat/completions"))
    }

    @Test fun emptyChoices_returnsParseFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"choices":[]}"""))
        val result = provider.chat(listOf(ChatTurn.user("hi")), config())
        assertTrue(result.isFailure); assertTrue(result.exceptionOrNull() is ProviderException.Parse)
    }

    @Test fun malformedJson_returnsParseFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("nope"))
        val result = provider.chat(listOf(ChatTurn.user("hi")), config())
        assertTrue(result.isFailure); assertTrue(result.exceptionOrNull() is ProviderException.Parse)
    }

    @Test fun unauthorized_mapsToUnauthorized() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        val result = provider.chat(listOf(ChatTurn.user("hi")), config())
        assertTrue(result.exceptionOrNull() is ProviderException.Unauthorized)
    }

    @Test fun rateLimited_mapsToRateLimited() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("{}"))
        val result = provider.chat(listOf(ChatTurn.user("hi")), config())
        assertTrue(result.exceptionOrNull() is ProviderException.RateLimited)
    }

    @Test fun slowResponse_timeoutOrNetworkFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(okBody()).setBodyDelay(5, TimeUnit.SECONDS))
        val result = provider.chat(listOf(ChatTurn.user("hi")), config(timeoutSeconds = 1))
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue("expected Timeout or Network, got ${ex?.javaClass?.simpleName}",
            ex is ProviderException.Timeout || ex is ProviderException.Network)
    }

    @Test fun cleartextNonLocalhost_isRejectedByValidatorBeforeNetwork() = runTest {
        val config = ProviderConfig(
            baseUrl = "http://example.com/v1", model = "gpt-4o-mini",
            apiKey = "test-bearer-123", timeoutSeconds = 5
        )
        val result = provider.chat(listOf(ChatTurn.user("hi")), config)
        assertTrue(result.isFailure); assertTrue(result.exceptionOrNull() is ProviderException.InvalidConfig)
    }
}
