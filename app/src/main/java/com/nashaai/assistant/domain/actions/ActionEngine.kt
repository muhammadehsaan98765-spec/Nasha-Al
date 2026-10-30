package com.nashaai.assistant.domain.actions

import com.nashaai.assistant.data.actions.ActionLogEntry
import com.nashaai.assistant.data.actions.ActionLogRepository

/**
 * Platform-specific executor abstraction.
 *
 * Block 6 will implement this with an Android Context and (for the three
 * accessibility actions only) NashaAccessibilityService. By depending on
 * this interface rather than on Android types directly, ActionEngine stays
 * pure-JVM testable (Block 12) and the platform layer can be swapped with
 * a fake in unit tests.
 *
 * Contract:
 *   - The executor MUST re-validate [ActionRequest.params] before acting.
 *     It MUST NOT call back into ActionEngine.
 *   - The executor MUST return a typed ActionResult; thrown exceptions are
 *     caught by ActionEngine and become FAILURE with a short code.
 *   - The executor MUST never surface exception messages containing URLs,
 *     tokens, or message text back through [ActionResult.detail].
 */
interface ActionExecutor {
    suspend fun execute(request: ActionRequest): ActionResult
}

/**
 * Central domain coordinator for the action subsystem.
 *
 *   parse (CommandParser)  ──►  ActionRequest
 *                                     │
 *              ActionEngine.preview ──┼──►  ActionPreview (UI)
 *                                     │
 *              ActionEngine.execute ──┴──►  ActionResult (after confirmation)
 *
 * Safety invariants:
 *   - UNKNOWN / BLOCKED are never handed to the executor.
 *   - MEDIUM / HIGH require `confirmed = true`; without it they return
 *     AWAITING_CONFIRMATION and do nothing.
 *   - Executor exceptions are swallowed and converted into a generic
 *     FAILURE — never propagated, never logged with the raw message.
 *   - Logging is optional (logRepository may be null for pure unit tests).
 *     Only short status codes are stored; user content is never logged.
 */
class ActionEngine(
    private val executor: ActionExecutor,
    private val logRepository: ActionLogRepository? = null,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    /** Builds the UI-facing preview for [request]. Never executes anything. */
    fun preview(request: ActionRequest): ActionPreview = ActionPreview(
        request = request,
        humanSummary = buildHumanSummary(request),
        warning = buildWarning(request)
    )

    /**
     * Executes [request] subject to confirmation and policy.
     *
     * @param confirmed Set to true only after the user has explicitly
     *                  tapped Confirm in the UI. Ignored for LOW risk.
     */
    suspend fun execute(request: ActionRequest, confirmed: Boolean = false): ActionResult {
        // 1) Policy gate — fail closed.
        if (!request.executable) {
            val blocked = ActionResult(
                type = request.type,
                risk = request.risk,
                status = ActionExecutionStatus.BLOCKED,
                detail = "blocked_by_policy"
            )
            logSafely(blocked)
            return blocked
        }

        // 2) Confirmation gate — MEDIUM / HIGH.
        if (request.requiresConfirmation && !confirmed) {
            // Not an execution attempt; do not log.
            return ActionResult(
                type = request.type,
                risk = request.risk,
                status = ActionExecutionStatus.AWAITING_CONFIRMATION,
                detail = "confirmation_required"
            )
        }

        // 3) Delegate to executor. Any exception becomes a generic failure.
        return try {
            val result = executor.execute(request)
            logSafely(result)
            result
        } catch (_: Exception) {
            val failure = ActionResult(
                type = request.type,
                risk = request.risk,
                status = ActionExecutionStatus.FAILURE,
                detail = "executor_threw"
            )
            logSafely(failure)
            failure
        }
    }

    // ---- Preview rendering ---------------------------------------------

    private fun buildHumanSummary(request: ActionRequest): String = when (val p = request.params) {
        is ActionParams.OpenUrl ->
            "Open ${p.url} in browser"

        is ActionParams.WebSearch ->
            "Search the web for: \"${shorten(p.query, 80)}\""

        is ActionParams.OpenApp ->
            "Open app: ${p.packageName}"

        is ActionParams.CopyText ->
            "Copy to clipboard: \"${shorten(p.text, 60)}\""

        is ActionParams.CreateNote ->
            "Create note \"${shorten(p.title, 40)}\" (${p.body.length} characters)"

        is ActionParams.ReadAloud ->
            "Read aloud: \"${shorten(p.text, 60)}\""

        is ActionParams.ShareText ->
            "Share ${p.text.length} characters via system share sheet"

        is ActionParams.SaveCode ->
            "Save ${p.language} code (${p.code.length} characters) to workspace"

        is ActionParams.PrepareMessage ->
            "Prepare a message (${p.text.length} characters) and open the share sheet"

        ActionParams.NoParams ->
            humanSummaryForNoParams(request.type)
    }

    private fun humanSummaryForNoParams(type: ActionType): String = when (type) {
        ActionType.OPEN_SETTINGS -> "Open Android Settings"
        ActionType.OPEN_NOTIFICATION_SETTINGS -> "Open Notification settings"
        ActionType.ACCESSIBILITY_HOME -> "Go to the Home screen (requires accessibility)"
        ActionType.ACCESSIBILITY_BACK -> "Go Back (requires accessibility)"
        ActionType.ACCESSIBILITY_RECENTS -> "Show Recents (requires accessibility)"
        ActionType.UNKNOWN -> "Unrecognized command"
        else -> "Perform ${type.name}"
    }

    private fun buildWarning(request: ActionRequest): String? = when (request.type) {
        ActionType.PREPARE_MESSAGE ->
            "Recipient is not auto-selected. You must choose a contact in the target app and press Send yourself."

        ActionType.SHARE_TEXT ->
            "The system share sheet will open. You choose the target app."

        ActionType.ACCESSIBILITY_HOME,
        ActionType.ACCESSIBILITY_BACK,
        ActionType.ACCESSIBILITY_RECENTS ->
            "This requires the Nasha AI accessibility service to be enabled. The service cannot read screen content."

        ActionType.UNKNOWN ->
            "Nasha AI did not recognize this as a supported action."

        else -> null
    }

    // ---- Logging --------------------------------------------------------

    /**
     * Appends a redacted log entry when [logRepository] is wired in.
     * Only [type], [risk], [success], and a short [detail] code are stored.
     * Logging failures are swallowed — never break a user action because of
     * a diagnostics write.
     */
    private suspend fun logSafely(result: ActionResult) {
        val log = logRepository ?: return
        try {
            log.append(
                ActionLogEntry(
                    timestamp = clock(),
                    type = result.type.name,
                    risk = result.risk.name,
                    success = result.success,
                    detail = result.detail.take(80)
                )
            )
        } catch (_: Exception) {
            // Swallow. Diagnostics must never affect user flows.
        }
    }

    private fun shorten(s: String, max: Int): String {
        val clean = s.replace('\n', ' ').trim()
        return if (clean.length <= max) clean else clean.take(max - 1) + "…"
    }
}
