package com.nashaai.assistant.domain.actions

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests ActionEngine's policy gate without touching the Android platform. */
class ActionEngineTest {

    private class RecordingExecutor(
        private val resultFor: (ActionRequest) -> ActionResult
    ) : ActionExecutor {
        val invocations = mutableListOf<ActionRequest>()
        override suspend fun execute(request: ActionRequest): ActionResult {
            invocations.add(request); return resultFor(request)
        }
    }

    private fun okExecutor(): RecordingExecutor =
        RecordingExecutor { req ->
            ActionResult(req.type, req.risk, ActionExecutionStatus.SUCCESS, "ok")
        }

    private fun throwingExecutor(): ActionExecutor = object : ActionExecutor {
        override suspend fun execute(request: ActionRequest): ActionResult {
            throw RuntimeException("boom")
        }
    }

    // ---- Preview ------------------------------------------------------

    @Test fun preview_lowRisk_showsSummaryAndNoWarning() {
        val engine = ActionEngine(okExecutor())
        val req = ActionRequest(ActionType.OPEN_SETTINGS, ActionParams.NoParams)
        val preview = engine.preview(req)
        assertEquals(req, preview.request)
        assertEquals(ActionRisk.LOW, preview.risk)
        assertFalse(preview.requiresConfirmation)
        assertTrue(preview.humanSummary.isNotBlank())
    }

    @Test fun preview_prepareMessage_includesRecipientWarning() {
        val engine = ActionEngine(okExecutor())
        val req = ActionRequest(ActionType.PREPARE_MESSAGE, ActionParams.PrepareMessage("hello"))
        val preview = engine.preview(req)
        assertNotNull(preview.warning)
        assertTrue(preview.warning!!.contains("Recipient", ignoreCase = true))
    }

    // ---- LOW ----------------------------------------------------------

    @Test fun execute_lowRisk_withoutConfirmation_goesToExecutor() = runTest {
        val exec = okExecutor(); val engine = ActionEngine(exec)
        val req = ActionRequest(ActionType.OPEN_SETTINGS, ActionParams.NoParams)
        assertEquals(ActionExecutionStatus.SUCCESS, engine.execute(req).status)
        assertEquals(1, exec.invocations.size)
    }

    // ---- MEDIUM / HIGH -----------------------------------------------

    @Test fun execute_mediumRisk_withoutConfirmation_returnsAwaiting() = runTest {
        val exec = okExecutor(); val engine = ActionEngine(exec)
        val req = ActionRequest(ActionType.COPY_TEXT, ActionParams.CopyText("x"))
        assertEquals(ActionExecutionStatus.AWAITING_CONFIRMATION, engine.execute(req).status)
        assertTrue(exec.invocations.isEmpty())
    }

    @Test fun execute_mediumRisk_withConfirmation_goesToExecutor() = runTest {
        val exec = okExecutor(); val engine = ActionEngine(exec)
        val req = ActionRequest(ActionType.COPY_TEXT, ActionParams.CopyText("x"))
        assertEquals(ActionExecutionStatus.SUCCESS, engine.execute(req, confirmed = true).status)
        assertEquals(1, exec.invocations.size)
    }

    @Test fun execute_highRisk_withoutConfirmation_returnsAwaiting() = runTest {
        val exec = okExecutor(); val engine = ActionEngine(exec)
        val req = ActionRequest(ActionType.PREPARE_MESSAGE, ActionParams.PrepareMessage("hi"))
        assertEquals(ActionExecutionStatus.AWAITING_CONFIRMATION, engine.execute(req).status)
        assertTrue(exec.invocations.isEmpty())
    }

    @Test fun execute_highRisk_withConfirmation_goesToExecutor() = runTest {
        val exec = okExecutor(); val engine = ActionEngine(exec)
        val req = ActionRequest(ActionType.PREPARE_MESSAGE, ActionParams.PrepareMessage("hi"))
        assertEquals(ActionExecutionStatus.SUCCESS, engine.execute(req, confirmed = true).status)
        assertEquals(1, exec.invocations.size)
    }

    // ---- BLOCKED / UNKNOWN -------------------------------------------

    @Test fun execute_unknown_returnsBlocked_andNeverCallsExecutor() = runTest {
        val exec = okExecutor(); val engine = ActionEngine(exec)
        val req = ActionRequest(ActionType.UNKNOWN, ActionParams.NoParams)
        val result = engine.execute(req, confirmed = true)
        assertEquals(ActionExecutionStatus.BLOCKED, result.status)
        assertEquals("blocked_by_policy", result.detail)
        assertTrue(exec.invocations.isEmpty())
    }

    // ---- Exception safety --------------------------------------------

    @Test fun execute_executorThrows_returnsFailureNotCrash() = runTest {
        val engine = ActionEngine(throwingExecutor())
        val req = ActionRequest(ActionType.OPEN_SETTINGS, ActionParams.NoParams)
        val result = engine.execute(req)
        assertEquals(ActionExecutionStatus.FAILURE, result.status)
        assertEquals("executor_threw", result.detail)
    }
}
