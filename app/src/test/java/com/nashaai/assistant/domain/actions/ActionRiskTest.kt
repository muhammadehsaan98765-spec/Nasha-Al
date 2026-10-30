package com.nashaai.assistant.domain.actions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies ActionRisk flags and their propagation through ActionRequest. */
class ActionRiskTest {

    @Test fun low_isExecutableWithoutConfirmation() {
        assertFalse(ActionRisk.LOW.requiresConfirmation)
        assertTrue(ActionRisk.LOW.executable)
    }

    @Test fun medium_requiresConfirmation() {
        assertTrue(ActionRisk.MEDIUM.requiresConfirmation)
        assertTrue(ActionRisk.MEDIUM.executable)
    }

    @Test fun high_requiresConfirmation() {
        assertTrue(ActionRisk.HIGH.requiresConfirmation)
        assertTrue(ActionRisk.HIGH.executable)
    }

    @Test fun blocked_isNeverExecutable() {
        assertFalse(ActionRisk.BLOCKED.executable)
    }

    @Test fun unknownAction_hasBlockedRisk() {
        val req = ActionRequest(ActionType.UNKNOWN, ActionParams.NoParams)
        assertEquals(ActionRisk.BLOCKED, req.risk)
        assertFalse(req.executable)
    }

    @Test fun accessibilityHome_isMediumRisk() {
        val req = ActionRequest(ActionType.ACCESSIBILITY_HOME, ActionParams.NoParams)
        assertEquals(ActionRisk.MEDIUM, req.risk)
        assertTrue(req.requiresConfirmation); assertTrue(req.executable)
    }

    @Test fun prepareMessage_isHighRisk() {
        val req = ActionRequest(ActionType.PREPARE_MESSAGE, ActionParams.PrepareMessage("hello"))
        assertEquals(ActionRisk.HIGH, req.risk)
        assertTrue(req.requiresConfirmation); assertTrue(req.executable)
    }

    @Test fun readAloud_isLowRisk() {
        val req = ActionRequest(ActionType.READ_ALOUD, ActionParams.ReadAloud("hi"))
        assertEquals(ActionRisk.LOW, req.risk)
        assertFalse(req.requiresConfirmation); assertTrue(req.executable)
    }

    @Test fun openSettings_isLowRisk() {
        val req = ActionRequest(ActionType.OPEN_SETTINGS, ActionParams.NoParams)
        assertEquals(ActionRisk.LOW, req.risk)
        assertFalse(req.requiresConfirmation); assertTrue(req.executable)
    }

    @Test fun shareText_isMediumRisk() {
        val req = ActionRequest(ActionType.SHARE_TEXT, ActionParams.ShareText("hi"))
        assertEquals(ActionRisk.MEDIUM, req.risk)
        assertTrue(req.requiresConfirmation)
    }

    @Test fun saveCode_isMediumRisk() {
        val req = ActionRequest(ActionType.SAVE_CODE, ActionParams.SaveCode("kotlin", "fun main(){}"))
        assertEquals(ActionRisk.MEDIUM, req.risk)
        assertTrue(req.requiresConfirmation)
    }
}
