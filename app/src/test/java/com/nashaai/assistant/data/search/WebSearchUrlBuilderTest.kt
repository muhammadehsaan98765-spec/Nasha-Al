package com.nashaai.assistant.data.search

import com.nashaai.assistant.data.settings.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for SearchProvider.buildBrowserUrl / encodeQuery.
 * No network — only URL construction.
 */
class WebSearchUrlBuilderTest {

    // ---- Preset engines -----------------------------------------------

    @Test fun google_preset() {
        val url = SearchProvider.buildBrowserUrl(SearchEngine.GOOGLE, "hello")
        assertEquals("https://www.google.com/search?q=hello", url)
    }

    @Test fun bing_preset() {
        val url = SearchProvider.buildBrowserUrl(SearchEngine.BING, "hello")
        assertEquals("https://www.bing.com/search?q=hello", url)
    }

    @Test fun duckduckgo_preset() {
        val url = SearchProvider.buildBrowserUrl(SearchEngine.DUCKDUCKGO, "hello")
        assertTrue(url.startsWith("https://duckduckgo.com/?q="))
        assertTrue(url.endsWith("hello"))
    }

    @Test fun brave_preset() {
        val url = SearchProvider.buildBrowserUrl(SearchEngine.BRAVE, "hello")
        assertTrue(url.startsWith("https://search.brave.com/search?q="))
    }

    @Test fun custom_usesTemplate() {
        val url = SearchProvider.buildBrowserUrl(
            SearchEngine.CUSTOM, "hello",
            customTemplate = "https://my.search.example/?q="
        )
        assertEquals("https://my.search.example/?q=hello", url)
    }

    @Test fun custom_blankTemplate_fallsBackToGoogle() {
        val url = SearchProvider.buildBrowserUrl(
            SearchEngine.CUSTOM, "hello", customTemplate = ""
        )
        assertEquals("https://www.google.com/search?q=hello", url)
    }

    // ---- Encoding: spaces --------------------------------------------

    @Test fun spacesUsePercent20_notPlus() {
        val url = SearchProvider.buildBrowserUrl(SearchEngine.GOOGLE, "hello world")
        assertTrue("expected %20 in URL: $url", url.contains("hello%20world"))
        assertFalse("'+' must not appear: $url", url.contains("hello+world"))
    }

    // ---- Encoding: special characters --------------------------------

    @Test fun ampersand_isEncoded() {
        val encoded = SearchProvider.encodeQuery("cats & dogs")
        assertTrue(encoded.contains("%26"))
        assertFalse(encoded.contains("&"))
    }

    @Test fun equals_isEncoded() {
        val encoded = SearchProvider.encodeQuery("a=b")
        assertTrue(encoded.contains("%3D"))
    }

    @Test fun questionMark_isEncoded() {
        val encoded = SearchProvider.encodeQuery("what?")
        assertTrue(encoded.contains("%3F"))
    }

    // ---- Encoding: Unicode / Urdu ------------------------------------

    @Test fun urduQuery_isPercentEncoded() {
        // کیٹس = U+06A9 U+06CC U+0679 U+0633
        val encoded = SearchProvider.encodeQuery("کیٹس")
        assertTrue("Urdu must be percent-encoded: $encoded", encoded.contains("%"))
        assertFalse(encoded.contains("کیٹس"))
    }

    @Test fun urduQuery_urlStillValid() {
        val url = SearchProvider.buildBrowserUrl(SearchEngine.GOOGLE, "کیٹس")
        assertTrue(url.startsWith("https://www.google.com/search?q="))
        assertTrue(url.contains("%"))
    }

    // ---- Encoding: empty / whitespace --------------------------------

    @Test fun emptyQuery_producesBaseUrl() {
        val url = SearchProvider.buildBrowserUrl(SearchEngine.GOOGLE, "")
        assertEquals("https://www.google.com/search?q=", url)
    }

    @Test fun whitespaceQuery_isEncodedAsSpaces() {
        val url = SearchProvider.buildBrowserUrl(SearchEngine.GOOGLE, "a b")
        assertTrue(url.contains("a%20b"))
    }
}
