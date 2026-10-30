package com.nashaai.assistant.domain.actions

/**
 * Risk classification for every action type.
 *
 * Invariants:
 *   - MEDIUM and HIGH require explicit user confirmation before execution.
 *   - BLOCKED is never executable under any circumstance.
 *   - LOW runs without confirmation but still via ActionEngine so it is
 *     logged and previewable.
 */
enum class ActionRisk(
    val requiresConfirmation: Boolean,
    val executable: Boolean
) {
    LOW(requiresConfirmation = false, executable = true),
    MEDIUM(requiresConfirmation = true, executable = true),
    HIGH(requiresConfirmation = true, executable = true),
    BLOCKED(requiresConfirmation = false, executable = false)
}

/**
 * Every action the app knows how to reason about.
 *
 * Block 5 requires at minimum OPEN_URL, WEB_SEARCH, OPEN_APP, COPY_TEXT,
 * CREATE_NOTE, UNKNOWN. The remaining entries come from the master plan
 * (§6.1) and are consumed by Blocks 6-10 for accessibility navigation,
 * sharing, code saving, and message preparation.
 */
enum class ActionType(val defaultRisk: ActionRisk) {

    // --- Block 5 required minimum ---------------------------------------
    OPEN_URL(ActionRisk.LOW),
    WEB_SEARCH(ActionRisk.LOW),
    OPEN_APP(ActionRisk.LOW),
    COPY_TEXT(ActionRisk.MEDIUM),
    CREATE_NOTE(ActionRisk.MEDIUM),

    // --- Master plan §6.1 entries (needed by future blocks) -------------
    OPEN_SETTINGS(ActionRisk.LOW),
    OPEN_NOTIFICATION_SETTINGS(ActionRisk.LOW),
    READ_ALOUD(ActionRisk.LOW),
    SHARE_TEXT(ActionRisk.MEDIUM),
    SAVE_CODE(ActionRisk.MEDIUM),
    ACCESSIBILITY_HOME(ActionRisk.MEDIUM),
    ACCESSIBILITY_BACK(ActionRisk.MEDIUM),
    ACCESSIBILITY_RECENTS(ActionRisk.MEDIUM),
    PREPARE_MESSAGE(ActionRisk.HIGH),

    // --- Fallback -------------------------------------------------------
    UNKNOWN(ActionRisk.BLOCKED)
}

/**
 * Typed parameters for each action.
 *
 * Using a sealed class instead of a generic Map makes the executor branch
 * exhaustive at compile time and prevents accidentally dropping a field.
 */
sealed class ActionParams {
    data class OpenUrl(val url: String) : ActionParams()
    data class WebSearch(val query: String) : ActionParams()
    data class OpenApp(val packageName: String) : ActionParams()
    data class CopyText(val text: String) : ActionParams()
    data class CreateNote(val title: String, val body: String) : ActionParams()
    data class ReadAloud(val text: String) : ActionParams()
    data class ShareText(val text: String) : ActionParams()
    data class SaveCode(
        val language: String,
        val code: String,
        val suggestedName: String? = null
    ) : ActionParams()
    data class PrepareMessage(val text: String) : ActionParams()

    /** Used by action types that carry no parameters. */
    object NoParams : ActionParams()
}

/**
 * A parsed, typed request ready for preview / execution.
 *
 * `rawUserText` is kept for display and debugging but is NEVER logged
 * automatically — it can contain user content.
 */
data class ActionRequest(
    val type: ActionType,
    val params: ActionParams,
    val rawUserText: String? = null
) {
    val risk: ActionRisk get() = type.defaultRisk
    val requiresConfirmation: Boolean get() = risk.requiresConfirmation
    val executable: Boolean get() = risk.executable
}

/**
 * UI-facing preview. The UI shows [humanSummary] and (if present)
 * [warning], then asks for confirmation when [requiresConfirmation] is
 * true. Full typed params travel in [request] for the executor.
 */
data class ActionPreview(
    val request: ActionRequest,
    val humanSummary: String,
    val warning: String? = null
) {
    val type: ActionType get() = request.type
    val risk: ActionRisk get() = request.risk
    val requiresConfirmation: Boolean get() = request.requiresConfirmation
    val executable: Boolean get() = request.executable
}

/**
 * Result state machine.
 *
 *   SUCCESS              — action completed
 *   FAILURE              — executor ran but failed
 *   CANCELLED            — user abandoned a flow (e.g. closed share sheet)
 *   BLOCKED              — refused by policy (unknown / invalid params)
 *   NOT_IMPLEMENTED      — action type recognized, executor not wired yet
 *   AWAITING_CONFIRMATION — needs user confirmation; not an execution attempt
 */
enum class ActionExecutionStatus {
    SUCCESS,
    FAILURE,
    CANCELLED,
    BLOCKED,
    NOT_IMPLEMENTED,
    AWAITING_CONFIRMATION
}

/**
 * Result of an execution attempt.
 *
 * SECURITY: [detail] must contain a short status code (e.g.
 * "blocked_by_policy", "executor_threw") — never user content, API keys,
 * full URLs, or exception messages.
 */
data class ActionResult(
    val type: ActionType,
    val risk: ActionRisk,
    val status: ActionExecutionStatus,
    val detail: String = ""
) {
    val success: Boolean get() = status == ActionExecutionStatus.SUCCESS
}
