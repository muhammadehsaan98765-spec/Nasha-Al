package com.nashaai.assistant.accessibility

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests for the accessibility-disabled state.
 *
 * BLOCK 15 FIX:
 *   Previous version used `androidx.test.core.app.ApplicationProvider`,
 *   which is not on the unit-test classpath. Replaced with
 *   `RuntimeEnvironment.getApplication()`.
 *
 * Scope:
 *   - Companion defaults: instance == null, isConnected StateFlow false.
 *   - AccessibilityUtils.isServiceConnected() returns false by default.
 *   - AccessibilityUtils.isServiceEnabled(context) returns false.
 *   - AccessibilityStatusFlow.isEnabledNow() returns false.
 *
 * Never calls performGlobalAction or modifies user Settings.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AccessibilityDisabledTest {

    @Test
    fun serviceInstance_isNullByDefault() {
        assertNull(NashaAccessibilityService.instance)
    }

    @Test
    fun isConnected_isFalseByDefault() {
        assertFalse(NashaAccessibilityService.isConnected.value)
    }

    @Test
    fun accessibilityUtils_isServiceConnected_returnsFalse() {
        assertFalse(AccessibilityUtils.isServiceConnected())
    }

    @Test
    fun accessibilityUtils_isServiceEnabled_returnsFalse_whenNotEnabled() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        assertFalse(AccessibilityUtils.isServiceEnabled(ctx))
    }

    @Test
    fun accessibilityStatusFlow_isEnabledNow_returnsFalse() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        assertFalse(AccessibilityStatusFlow(ctx).isEnabledNow())
    }
}
