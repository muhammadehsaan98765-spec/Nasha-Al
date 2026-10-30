package com.nashaai.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.nashaai.assistant.domain.actions.ActionExecutionStatus
import com.nashaai.assistant.domain.actions.ActionExecutor
import com.nashaai.assistant.domain.actions.ActionRequest
import com.nashaai.assistant.domain.actions.ActionResult
import com.nashaai.assistant.domain.actions.ActionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Nasha AI accessibility service.
 *
 * Scope is deliberately tiny — only the three global actions the OS
 * sanctions through AccessibilityService.performGlobalAction:
 *
 *   - GLOBAL_ACTION_HOME
 *   - GLOBAL_ACTION_BACK
 *   - GLOBAL_ACTION_RECENTS
 *
 * Nothing else. In particular:
 *   - It never reads screen content.
 *   - It never inspects window nodes / text fields.
 *   - It never types, taps programmatically, sends messages, or taps
 *     buttons on the user's behalf.
 *   - It never bypasses the lock screen or captures credentials.
 *
 * The accessibility_service_config.xml in Block 1 declares
 * `canRetrieveWindowContent="false"` to enforce this at the OS level.
 *
 * Implements [ActionExecutor] for Block 5's ActionEngine. Only the three
 * accessibility action types are handled here; every other type returns
 * NOT_IMPLEMENTED so a composite executor (see AppContainer) can route it
 * to the appropriate Android Intent path.
 */
class NashaAccessibilityService : AccessibilityService(), ActionExecutor {

    companion object {
        private val _isConnected = MutableStateFlow(false)

        /** Reactive runtime connectivity — driven by onServiceConnected/onDestroy. */
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        /**
         * Live singleton reference. Set in onServiceConnected, cleared in
         * onDestroy. ActionExecutor consumers must null-check before use.
         */
        @Volatile
        var instance: NashaAccessibilityService? = null
            private set

        private fun bind(svc: NashaAccessibilityService) {
            instance = svc
            _isConnected.value = true
        }

        private fun unbind(svc: NashaAccessibilityService) {
            if (instance === svc) instance = null
            _isConnected.value = false
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        bind(this)
    }

    /**
     * No-op. This service intentionally does not react to window-state
     * changes. Declaring a non-empty event type in the XML config is
     * required for the service to be usable, but we do not inspect events.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty — see class doc.
    }

    override fun onInterrupt() {
        // Intentionally empty.
    }

    override fun onDestroy() {
        unbind(this)
        super.onDestroy()
    }

    // ---- ActionExecutor -------------------------------------------------

    override suspend fun execute(request: ActionRequest): ActionResult {
        return when (request.type) {
            ActionType.ACCESSIBILITY_HOME ->
                performNav(GLOBAL_ACTION_HOME, request)

            ActionType.ACCESSIBILITY_BACK ->
                performNav(GLOBAL_ACTION_BACK, request)

            ActionType.ACCESSIBILITY_RECENTS ->
                performNav(GLOBAL_ACTION_RECENTS, request)

            ActionType.UNKNOWN ->
                ActionResult(
                    type = request.type,
                    risk = request.risk,
                    status = ActionExecutionStatus.BLOCKED,
                    detail = "unknown_action"
                )

            else ->
                // Valid action, but not one this service is allowed to run.
                ActionResult(
                    type = request.type,
                    risk = request.risk,
                    status = ActionExecutionStatus.NOT_IMPLEMENTED,
                    detail = "not_accessibility_action"
                )
        }
    }

    private fun performNav(
        globalAction: Int,
        request: ActionRequest
    ): ActionResult {
        return try {
            val ok = performGlobalAction(globalAction)
            ActionResult(
                type = request.type,
                risk = request.risk,
                status = if (ok) {
                    ActionExecutionStatus.SUCCESS
                } else {
                    ActionExecutionStatus.FAILURE
                },
                detail = if (ok) "global_action_performed" else "global_action_failed"
            )
        } catch (_: Exception) {
            ActionResult(
                type = request.type,
                risk = request.risk,
                status = ActionExecutionStatus.FAILURE,
                detail = "global_action_threw"
            )
        }
    }
}
