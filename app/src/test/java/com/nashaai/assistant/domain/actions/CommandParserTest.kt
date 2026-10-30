package com.nashaai.assistant.domain.actions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for the deterministic CommandParser (Block 5).
 * Only asserts on behaviour the parser actually implements.
 */
class CommandParserTest {

    private lateinit var parser: CommandParser

    @Before
    fun setUp() { parser = CommandParser() }

    // ---- Slash commands -----------------------------------------------

    @Test fun slashOpenUrl_returnsOpenUrl() {
        val r = parser.parse("/open https://example.com")
        assertEquals(ActionType.OPEN_URL, r.type)
        assertEquals("https://example.com", (r.params as ActionParams.OpenUrl).url)
    }

    @Test fun slashOpenPackage_returnsOpenApp() {
        val r = parser.parse("/open com.whatsapp")
        assertEquals(ActionType.OPEN_APP, r.type)
        assertEquals("com.whatsapp", (r.params as ActionParams.OpenApp).packageName)
    }

    @Test fun slashUrlWithHttpScheme_returnsOpenUrl() {
        assertEquals(ActionType.OPEN_URL, parser.parse("/url http://example.com").type)
    }

    @Test fun slashUrlWithJavascriptScheme_returnsUnknown() {
        assertEquals(ActionType.UNKNOWN, parser.parse("/url javascript:alert(1)").type)
    }

    @Test fun slashUrlWithFileScheme_returnsUnknown() {
        assertEquals(ActionType.UNKNOWN, parser.parse("/url file:///etc/passwd").type)
    }

    @Test fun slashSearch_returnsWebSearch() {
        val r = parser.parse("/search cute cats")
        assertEquals(ActionType.WEB_SEARCH, r.type)
        assertEquals("cute cats", (r.params as ActionParams.WebSearch).query)
    }

    @Test fun slashSearchEmpty_returnsUnknown() {
        assertEquals(ActionType.UNKNOWN, parser.parse("/search ").type)
    }

    @Test fun slashCopy_returnsCopyText() {
        val r = parser.parse("/copy hello world")
        assertEquals(ActionType.COPY_TEXT, r.type)
        assertEquals("hello world", (r.params as ActionParams.CopyText).text)
    }

    @Test fun slashNoteWithPipe_parsesTitleAndBody() {
        val r = parser.parse("/note My Title | Body text")
        assertEquals(ActionType.CREATE_NOTE, r.type)
        val p = r.params as ActionParams.CreateNote
        assertEquals("My Title", p.title); assertEquals("Body text", p.body)
    }

    @Test fun slashNoteWithColon_parsesTitleAndBody() {
        val r = parser.parse("/note My Title: Body text")
        assertEquals(ActionType.CREATE_NOTE, r.type)
        val p = r.params as ActionParams.CreateNote
        assertEquals("My Title", p.title); assertEquals("Body text", p.body)
    }

    @Test fun slashMessage_returnsPrepareMessage() {
        assertEquals(ActionType.PREPARE_MESSAGE, parser.parse("/message hello there").type)
    }

    @Test fun slashShare_returnsShareText() {
        assertEquals(ActionType.SHARE_TEXT, parser.parse("/share hello").type)
    }

    @Test fun slashRead_returnsReadAloud() {
        assertEquals(ActionType.READ_ALOUD, parser.parse("/read hello").type)
    }

    @Test fun slashSettings_returnsOpenSettings() {
        assertEquals(ActionType.OPEN_SETTINGS, parser.parse("/settings").type)
    }

    @Test fun slashNotifications_returnsNotificationSettings() {
        assertEquals(ActionType.OPEN_NOTIFICATION_SETTINGS, parser.parse("/notifications").type)
    }

    @Test fun slashHome_returnsAccessibilityHome() {
        assertEquals(ActionType.ACCESSIBILITY_HOME, parser.parse("/home").type)
    }

    @Test fun slashBack_returnsAccessibilityBack() {
        assertEquals(ActionType.ACCESSIBILITY_BACK, parser.parse("/back").type)
    }

    @Test fun slashRecents_returnsAccessibilityRecents() {
        assertEquals(ActionType.ACCESSIBILITY_RECENTS, parser.parse("/recents").type)
    }

    @Test fun slashUnknownCommand_returnsUnknown() {
        assertEquals(ActionType.UNKNOWN, parser.parse("/foobar xyz").type)
    }

    // ---- English ------------------------------------------------------

    @Test fun englishOpenUrl_returnsOpenUrl() {
        assertEquals(ActionType.OPEN_URL, parser.parse("open https://nasha.ai").type)
    }

    @Test fun englishOpenPackage_returnsOpenApp() {
        assertEquals(ActionType.OPEN_APP, parser.parse("open com.instagram.android").type)
    }

    @Test fun englishSearch_returnsWebSearch() {
        assertEquals(ActionType.WEB_SEARCH, parser.parse("search for kotlin coroutines").type)
    }

    @Test fun englishGoogle_returnsWebSearch() {
        assertEquals(ActionType.WEB_SEARCH, parser.parse("google nasha ai").type)
    }

    @Test fun englishCopy_returnsCopyText() {
        assertEquals(ActionType.COPY_TEXT, parser.parse("copy test").type)
    }

    // ---- Roman Urdu ---------------------------------------------------

    @Test fun romanUrduKholoPrefix_returnsOpenUrl() {
        assertEquals(ActionType.OPEN_URL, parser.parse("kholo https://example.com").type)
    }

    @Test fun romanUrduKholoSuffix_returnsOpenApp() {
        assertEquals(ActionType.OPEN_APP, parser.parse("com.whatsapp kholo").type)
    }

    @Test fun romanUrduDhoondo_returnsWebSearch() {
        assertEquals(ActionType.WEB_SEARCH, parser.parse("dhoondo cats").type)
    }

    @Test fun romanUrduSearchKaro_returnsWebSearch() {
        assertEquals(ActionType.WEB_SEARCH, parser.parse("search karo pizza").type)
    }

    @Test fun romanUrduCopyKaro_returnsCopyText() {
        assertEquals(ActionType.COPY_TEXT, parser.parse("copy karo hello").type)
    }

    // ---- Urdu script --------------------------------------------------

    @Test fun urduTalash_returnsWebSearch() {
        assertEquals(ActionType.WEB_SEARCH, parser.parse("تلاش کرو cats").type)
    }

    @Test fun urduKholo_returnsOpenUrl() {
        assertEquals(ActionType.OPEN_URL, parser.parse("کھولو https://example.com").type)
    }

    @Test fun urduCopyKaro_returnsCopyText() {
        assertEquals(ActionType.COPY_TEXT, parser.parse("کاپی کرو hello").type)
    }

    // ---- Edge cases ---------------------------------------------------

    @Test fun emptyInput_returnsUnknown() {
        assertEquals(ActionType.UNKNOWN, parser.parse("").type)
        assertEquals(ActionType.UNKNOWN, parser.parse("   ").type)
    }

    @Test fun plainConversationalText_returnsUnknown() {
        assertEquals(ActionType.UNKNOWN, parser.parse("how are you today?").type)
        assertEquals(ActionType.UNKNOWN, parser.parse("tell me a joke").type)
    }

    @Test fun malformedPackageName_returnsUnknown() {
        assertEquals(ActionType.UNKNOWN, parser.parse("/open whatsapp").type)
        assertEquals(ActionType.UNKNOWN, parser.parse("/open com.").type)
        assertEquals(ActionType.UNKNOWN, parser.parse("/open .com.foo").type)
    }

    // ---- URL validation -----------------------------------------------

    @Test fun validateUrlOrNull_acceptsHttps() {
        assertNotNull(CommandParser.validateUrlOrNull("https://example.com"))
    }

    @Test fun validateUrlOrNull_rejectsJavascript() {
        assertNull(CommandParser.validateUrlOrNull("javascript:void(0)"))
    }

    @Test fun validateUrlOrNull_rejectsFileScheme() {
        assertNull(CommandParser.validateUrlOrNull("file:///etc/passwd"))
    }

    @Test fun validateUrlOrNull_rejectsIntentScheme() {
        assertNull(CommandParser.validateUrlOrNull("intent://evil#Intent"))
    }

    @Test fun validateUrlOrNull_rejectsUserInfo() {
        assertNull(CommandParser.validateUrlOrNull("https://user:pass@example.com"))
    }

    @Test fun validateUrlOrNull_rejectsBlankHost() {
        assertNull(CommandParser.validateUrlOrNull("https://"))
    }

    @Test fun isValidPackageName_acceptsDottedName() {
        assertTrue(CommandParser.isValidPackageName("com.whatsapp"))
        assertTrue(CommandParser.isValidPackageName("org.example.app_name"))
    }

    @Test fun isValidPackageName_rejectsSingleSegment() {
        assertFalse(CommandParser.isValidPackageName("whatsapp"))
    }

    @Test fun isValidPackageName_rejectsSpecialChars() {
        assertFalse(CommandParser.isValidPackageName("com.whats-app"))
        assertFalse(CommandParser.isValidPackageName("com.whats app"))
    }
}
