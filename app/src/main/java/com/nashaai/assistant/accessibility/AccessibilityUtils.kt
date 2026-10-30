package com.nashaai.assistant.accessibility

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Stateless helpers for checking accessibility state and opening the
 * Android accessibility settings screen.
 *
 * None of these methods throw; every failure is reported via return value
 * so UI callers can surface a clear message instead of crashing.
 */
object AccessibilityUtils {

    /**
     * True iff the user has the Nasha AI accessibility service enabled in
     * Android Settings. Uses AccessibilityManager as the authoritative
     * source — do not rely on Settings.Secure.getString parsing alone,
     * which is easy to get wrong on vendor ROMs.
     */
    fun isServiceEnabled(context: Context): Boolean =
        AccessibilityStatusFlow(context).isEnabledNow()

    /**
     * True iff a live NashaAccessibilityService instance is currently
     * connected in this process. False if the user enabled the service
     * but the OS has not yet bound it, or if it was just disabled.
     */
    fun isServiceConnected(): Boolean =
        NashaAccessibilityService.instance != null

    /**
     * Opens the system Accessibility settings screen.
     *
     * @return true if the settings activity was launched; false if no
     *         activity could handle ACTION_ACCESSIBILITY_SETTINGS (rare;
     *         some locked-down devices) or another exception occurred.
     */
    fun openAccessibilitySettings(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}
