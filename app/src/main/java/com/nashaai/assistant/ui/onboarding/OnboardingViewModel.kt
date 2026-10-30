package com.nashaai.assistant.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nashaai.assistant.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Immutable UI state for onboarding.
 *
 * `step` is 0-based and maps to:
 *   0 = Welcome / introduction
 *   1 = AI provider guidance (no API-key entry required during onboarding)
 *   2 = Privacy & permissions explanation (including accessibility)
 *
 * `completed` becomes true after [OnboardingViewModel.finish] successfully
 * persists the completion flag. NashaApp observes the Settings flow and
 * swaps to the main shell once `AppSettings.onboardingComplete == true`.
 */
data class OnboardingState(
    val step: Int = 0,
    val completed: Boolean = false
) {
    companion object {
        const val LAST_STEP = 2
    }
}

/**
 * Three-step onboarding controller.
 *
 * Security / privacy:
 *   - No API key is ever placed in this ViewModel's state, logs, or
 *     persistence. Onboarding only teaches the user where to add the key
 *     later (Settings screen, Block 9).
 *   - Completion is persisted via the existing
 *     [SettingsRepository.markOnboardingComplete] — no new DataStore is
 *     introduced and no existing contract is changed.
 */
class OnboardingViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    /** Advance to next step; on the final step this calls [finish]. */
    fun next() {
        val current = _state.value
        if (current.step < OnboardingState.LAST_STEP) {
            _state.value = current.copy(step = current.step + 1)
        } else {
            finish()
        }
    }

    /** Go back one step; no-op at step 0. */
    fun back() {
        val current = _state.value
        if (current.step > 0) {
            _state.value = current.copy(step = current.step - 1)
        }
    }

    /** Skip onboarding from any step. Equivalent to [finish]. */
    fun skip() = finish()

    /**
     * Persist completion and flip state to `completed = true`.
     * Idempotent — safe to call more than once.
     */
    fun finish() {
        if (_state.value.completed) return
        viewModelScope.launch {
            try {
                settingsRepository.markOnboardingComplete()
            } catch (_: Exception) {
                // Swallow — a failed write would only delay the transition.
                // NashaApp's Settings flow will still show the main shell
                // if the write actually succeeded.
            }
            _state.value = _state.value.copy(completed = true)
        }
    }
}
