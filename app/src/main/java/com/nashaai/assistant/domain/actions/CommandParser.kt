package com.nashaai.assistant.domain.actions

import java.net.URI

/**
 * Deterministic natural-language → ActionRequest parser.
 *
 * Design rules (enforced by unit tests in Block 12):
 *   - Never executes anything, never calls the network, never touches
 *     Android APIs. Pure JVM.
 *   - Returns UNKNOWN for anything it cannot confidently map to a
 *     supported action. In particular, arbitrary AI output is never
 *     treated as a command unless it matches an explicit pattern below.
 *   - URL validation requires both a well-formed scheme AND a non-empty
 *     host. Only http and https are allowed — javascript:, file:,
 *     intent:, data:, vbscript:, about:, chrome: are rejected.
 *   - Package names must match a strict dotted-lowercase pattern.
 *
 * Supported inputs (each can be prefixed with `/` or written as natural
 * language in English / Roman Urdu / Urdu):
 *
 *   /open <url|package>      — http(s) URL opens in browser; package opens app
 *   /url <url>               — same as above, always URL
 *   /search <query>          — web search
 *   /copy <text>             — copy text to clipboard
 *   /note <title>|<body>     — create a note
 *   /message <text>          — prepare message (opens share sheet)
 *   /share <text>            — share text via system sheet
 *   /read <text>             — read text aloud
 *   /settings                — open Android settings
 *   /notifications           — open notification settings
 *   /home /back /recents     — accessibility navigation
 *   /save-code <lang>|<code> — save to coding workspace
 *
 *   "open <url|package>", "search for <q>", "google <q>",
 *   "copy <text>", "note <title>: <body>",
 *   "kholo <x>", "<x> kholo", "dhoondo <q>", "search karo <q>",
 *   "copy karo <t>", "تلاش کرو <q>", "کھولو <x>", "کاپی کرو <t>"
 */
class CommandParser {

    companion object {
        /** Only these URL schemes are ever accepted by the parser. */
        private val ALLOWED_URL_SCHEMES = setOf("http", "https")

        /** Dot-separated identifier, e.g. com.example.app. */
        private val PACKAGE_NAME_REGEX =
            Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

        /**
         * Validates [raw] as an http(s) URL.
         * Returns the trimmed URL on success, null on any failure.
         * Rejects embedded userinfo (user:pass@host) — that would smuggle
         * credentials through the URL. Public so Block 6's Android executor
         * can reuse the exact same policy.
         */
        fun validateUrlOrNull(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            return try {
                val uri = URI(trimmed)
                val scheme = uri.scheme?.lowercase() ?: return null
                if (scheme !in ALLOWED_URL_SCHEMES) return null
                val host = uri.host ?: return null
                if (host.isBlank()) return null
                if (uri.userInfo != null) return null
                trimmed
            } catch (_: Exception) {
                null
            }
        }

        /** True iff [name] looks like an Android package name. */
        fun isValidPackageName(name: String): Boolean =
            PACKAGE_NAME_REGEX.matches(name.trim())
    }

    /** Parse [rawInput] into a typed request. Never throws. */
    fun parse(rawInput: String): ActionRequest {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return unknown(rawInput)

        if (trimmed.startsWith("/")) return parseSlashCommand(trimmed, rawInput)

        return parseNaturalLanguage(trimmed, rawInput) ?: unknown(rawInput)
    }

    // ---- Slash commands -------------------------------------------------

    private fun parseSlashCommand(trimmed: String, rawInput: String): ActionRequest {
        val spaceIdx = trimmed.indexOf(' ')
        val command = if (spaceIdx == -1) {
            trimmed.substring(1).lowercase()
        } else {
            trimmed.substring(1, spaceIdx).lowercase()
        }
        val args = if (spaceIdx == -1) "" else trimmed.substring(spaceIdx + 1).trim()

        return when (command) {
            "open" -> parseOpenTarget(args, rawInput)
            "url" -> validateUrlOrNull(args)?.let {
                ActionRequest(ActionType.OPEN_URL, ActionParams.OpenUrl(it), rawInput)
            } ?: unknown(rawInput)

            "search" -> if (args.isBlank()) unknown(rawInput) else
                ActionRequest(ActionType.WEB_SEARCH, ActionParams.WebSearch(args), rawInput)

            "copy" -> if (args.isBlank()) unknown(rawInput) else
                ActionRequest(ActionType.COPY_TEXT, ActionParams.CopyText(args), rawInput)

            "note" -> splitNote(args)?.let { (title, body) ->
                ActionRequest(ActionType.CREATE_NOTE, ActionParams.CreateNote(title, body), rawInput)
            } ?: unknown(rawInput)

            "message", "msg" -> if (args.isBlank()) unknown(rawInput) else
                ActionRequest(ActionType.PREPARE_MESSAGE, ActionParams.PrepareMessage(args), rawInput)

            "share" -> if (args.isBlank()) unknown(rawInput) else
                ActionRequest(ActionType.SHARE_TEXT, ActionParams.ShareText(args), rawInput)

            "read" -> if (args.isBlank()) unknown(rawInput) else
                ActionRequest(ActionType.READ_ALOUD, ActionParams.ReadAloud(args), rawInput)

            "settings" -> ActionRequest(ActionType.OPEN_SETTINGS, ActionParams.NoParams, rawInput)
            "notifications", "notif" -> ActionRequest(
                ActionType.OPEN_NOTIFICATION_SETTINGS, ActionParams.NoParams, rawInput
            )

            "home" -> ActionRequest(ActionType.ACCESSIBILITY_HOME, ActionParams.NoParams, rawInput)
            "back" -> ActionRequest(ActionType.ACCESSIBILITY_BACK, ActionParams.NoParams, rawInput)
            "recents" -> ActionRequest(ActionType.ACCESSIBILITY_RECENTS, ActionParams.NoParams, rawInput)

            "save-code" -> splitSaveCode(args)?.let { (lang, code) ->
                ActionRequest(
                    ActionType.SAVE_CODE,
                    ActionParams.SaveCode(language = lang, code = code, suggestedName = null),
                    rawInput
                )
            } ?: unknown(rawInput)

            else -> unknown(rawInput)
        }
    }

    private fun parseOpenTarget(args: String, rawInput: String): ActionRequest {
        if (args.isBlank()) return unknown(rawInput)
        validateUrlOrNull(args)?.let {
            return ActionRequest(ActionType.OPEN_URL, ActionParams.OpenUrl(it), rawInput)
        }
        if (isValidPackageName(args)) {
            return ActionRequest(ActionType.OPEN_APP, ActionParams.OpenApp(args.trim()), rawInput)
        }
        return unknown(rawInput)
    }

    // ---- Natural language ----------------------------------------------

    private fun parseNaturalLanguage(input: String, rawInput: String): ActionRequest? {

        // --- English -----------------------------------------------------
        Regex("^open\\s+(.+)$", RegexOption.IGNORE_CASE).find(input)?.let { m ->
            val rest = m.groupValues[1].trim()
            validateUrlOrNull(rest)?.let {
                return ActionRequest(ActionType.OPEN_URL, ActionParams.OpenUrl(it), rawInput)
            }
            if (isValidPackageName(rest)) {
                return ActionRequest(ActionType.OPEN_APP, ActionParams.OpenApp(rest), rawInput)
            }
        }

        Regex("^(?:search(?:\\s+for)?|google)\\s+(.+)$", RegexOption.IGNORE_CASE)
            .find(input)?.let { m ->
                val q = m.groupValues[1].trim()
                if (q.isNotBlank()) {
                    return ActionRequest(ActionType.WEB_SEARCH, ActionParams.WebSearch(q), rawInput)
                }
            }

        Regex("^copy\\s+(.+)$", RegexOption.IGNORE_CASE).find(input)?.let { m ->
            val t = m.groupValues[1].trim()
            if (t.isNotBlank()) {
                return ActionRequest(ActionType.COPY_TEXT, ActionParams.CopyText(t), rawInput)
            }
        }

        Regex("^(?:create\\s+)?note\\s+(.+)$", RegexOption.IGNORE_CASE).find(input)?.let { m ->
            splitNote(m.groupValues[1])?.let { (title, body) ->
                return ActionRequest(
                    ActionType.CREATE_NOTE,
                    ActionParams.CreateNote(title, body),
                    rawInput
                )
            }
        }

        // --- Roman Urdu --------------------------------------------------
        Regex("^kholo\\s+(.+)$", RegexOption.IGNORE_CASE).find(input)?.let { m ->
            val rest = m.groupValues[1].trim()
            validateUrlOrNull(rest)?.let {
                return ActionRequest(ActionType.OPEN_URL, ActionParams.OpenUrl(it), rawInput)
            }
            if (isValidPackageName(rest)) {
                return ActionRequest(ActionType.OPEN_APP, ActionParams.OpenApp(rest), rawInput)
            }
        }
        Regex("^(.+)\\s+kholo$", RegexOption.IGNORE_CASE).find(input)?.let { m ->
            val rest = m.groupValues[1].trim()
            validateUrlOrNull(rest)?.let {
                return ActionRequest(ActionType.OPEN_URL, ActionParams.OpenUrl(it), rawInput)
            }
            if (isValidPackageName(rest)) {
                return ActionRequest(ActionType.OPEN_APP, ActionParams.OpenApp(rest), rawInput)
            }
        }

        Regex(
            "^(?:dhoondo|dhoond(?:o|\\s+karo)|search\\s+karo|talash\\s+karo)\\s+(.+)$",
            RegexOption.IGNORE_CASE
        ).find(input)?.let { m ->
            val q = m.groupValues[1].trim()
            if (q.isNotBlank()) {
                return ActionRequest(ActionType.WEB_SEARCH, ActionParams.WebSearch(q), rawInput)
            }
        }

        Regex("^copy\\s+karo\\s+(.+)$", RegexOption.IGNORE_CASE).find(input)?.let { m ->
            val t = m.groupValues[1].trim()
            if (t.isNotBlank()) {
                return ActionRequest(ActionType.COPY_TEXT, ActionParams.CopyText(t), rawInput)
            }
        }

        // --- Urdu --------------------------------------------------------
        Regex("^(?:تلاش\\s+(?:کرو|کریں))\\s+(.+)$").find(input)?.let { m ->
            val q = m.groupValues[1].trim()
            if (q.isNotBlank()) {
                return ActionRequest(ActionType.WEB_SEARCH, ActionParams.WebSearch(q), rawInput)
            }
        }
        Regex("^(.+)\\s+تلاش\\s+(?:کرو|کریں)$").find(input)?.let { m ->
            val q = m.groupValues[1].trim()
            if (q.isNotBlank()) {
                return ActionRequest(ActionType.WEB_SEARCH, ActionParams.WebSearch(q), rawInput)
            }
        }

        Regex("^کھولو\\s+(.+)$").find(input)?.let { m ->
            val rest = m.groupValues[1].trim()
            validateUrlOrNull(rest)?.let {
                return ActionRequest(ActionType.OPEN_URL, ActionParams.OpenUrl(it), rawInput)
            }
            if (isValidPackageName(rest)) {
                return ActionRequest(ActionType.OPEN_APP, ActionParams.OpenApp(rest), rawInput)
            }
        }

        Regex("^کاپی\\s+کرو\\s+(.+)$").find(input)?.let { m ->
            val t = m.groupValues[1].trim()
            if (t.isNotBlank()) {
                return ActionRequest(ActionType.COPY_TEXT, ActionParams.CopyText(t), rawInput)
            }
        }

        return null
    }

    // ---- Helpers --------------------------------------------------------

    /**
     * Splits a note payload of the form "<title> | <body>" or
     * "<title>: <body>". Returns null if either side is blank.
     */
    private fun splitNote(raw: String): Pair<String, String>? {
        val pipe = raw.indexOf('|')
        if (pipe > 0) {
            val title = raw.substring(0, pipe).trim()
            val body = raw.substring(pipe + 1).trim()
            if (title.isNotBlank() && body.isNotBlank()) return title to body
        }
        val colon = raw.indexOf(':')
        if (colon > 0) {
            val title = raw.substring(0, colon).trim()
            val body = raw.substring(colon + 1).trim()
            if (title.isNotBlank() && body.isNotBlank()) return title to body
        }
        return null
    }

    /** Splits "<lang> | <code>" for SAVE_CODE. */
    private fun splitSaveCode(raw: String): Pair<String, String>? {
        val pipe = raw.indexOf('|')
        if (pipe <= 0) return null
        val lang = raw.substring(0, pipe).trim()
        val code = raw.substring(pipe + 1).trim()
        return if (lang.isNotBlank() && code.isNotBlank()) lang to code else null
    }

    private fun unknown(rawInput: String): ActionRequest =
        ActionRequest(ActionType.UNKNOWN, ActionParams.NoParams, rawInput)
}
