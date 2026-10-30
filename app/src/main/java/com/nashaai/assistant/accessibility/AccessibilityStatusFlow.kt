package com.nashaai.assistant.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Reactive accessibility status.
 *
 * Two orthogonal facts are exposed:
 *
 *   [isEnabled]   — what the *user* last toggled in Android Settings
 *                   (Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).
 *                   Persists across process restarts.
 *
 *   [isConnected] — whether a live [NashaAccessibilityService] instance
 *                   exists in this process. Flips to true only after
 *                   onServiceConnected; flips to false in onDestroy.
 *
 *   [isReady]     — isEnabled && isConnected. Use this before handing an
 *                   accessibility action to the executor.
 *
 * The isEnabled flow is driven by a ContentObserver on the
 * ENABLED_ACCESSIBILITY_SERVICES settings URI, so it emits on both
 * app-visible changes and OS-driven ones. The observer is unregistered
 * automatically when the flow collector cancels (via awaitClose).
 */
class AccessibilityStatusFlow(private val context: Context) {

    /**
     * True iff the *user* has the Nasha AI accessibility service enabled
     * in Android Settings. Cold flow; each collector gets its own
     * observer registration.
     */
    val isEnabled: Flow<Boolean> = callbackFlow {
        val uri = Settings.Secure.getUriFor(
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(isEnabledNow())
            }
        }
        try {
            context.contentResolver.registerContentObserver(uri, false, observer)
        } catch (_: Exception) {
            // If registration fails, emit false and stay idle; caller can
            // still call isEnabledNow() manually as a last resort.
            trySend(false)
        }
        trySend(isEnabledNow())
        awaitClose {
            try {
                context.contentResolver.unregisterContentObserver(observer)
            } catch (_: Exception) {
                // Ignore — may not have registered.
            }
        }
    }.distinctUntilChanged()

    /**
     * True iff a live NashaAccessibilityService instance exists in this
     * process. Exposed from the service's companion StateFlow.
     */
    val isConnected: Flow<Boolean> = NashaAccessibilityService.isConnected

    /** True iff user enabled the service AND a live instance is connected. */
    val isReady: Flow<Boolean> =
        combine(isEnabled, isConnected) { enabled, connected ->
            enabled && connected
        }.distinctUntilChanged()

    /**
     * One-shot synchronous check of the user-facing enabled state.
     * Safe to call from any thread.
     */
    fun isEnabledNow(): Boolean = try {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
            as? AccessibilityManager ?: return false
        val infoList: List<AccessibilityServiceInfo> =
            am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )
        infoList.any { info ->
            info.resolveInfo?.serviceInfo?.packageName == context.packageName
        }
    } catch (_: Exception) {
        false
    }
}
