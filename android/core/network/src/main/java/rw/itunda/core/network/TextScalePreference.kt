package rw.itunda.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Real Uber "Simple Mode" equivalent (2026-08-15) -- see docs/DESIGN_REFERENCES.md
// Section 69 for the full sourced account (Uber's real Senior Accounts + Simple Mode,
// launched 2025-06-04, expanding internationally through 2026): a real, standing
// accessibility text-scale toggle, reachable without any family-link setup. The
// larger "request a ride on someone else's behalf" half of that same research was
// deliberately scoped and NOT built (needs real design decisions on rider-of-record/
// payer-wallet routing) -- this is the separable, architecturally simpler half.
enum class TextScaleOption(val multiplier: Float, val label: String) {
    DEFAULT(1.0f, "Default"),
    LARGE(1.15f, "Large"),
    EXTRA_LARGE(1.3f, "Extra large"),
}

/**
 * Live app-wide text-scale override, mirroring ThemePreference/AppLocalePreference's
 * identical StateFlow + TokenStore-backed pattern -- so toggling the setting takes
 * effect immediately across the whole composition tree without an app restart.
 */
object TextScalePreference {
    private val _option = MutableStateFlow(TextScaleOption.DEFAULT)
    val option: StateFlow<TextScaleOption> = _option

    fun restore() {
        _option.value = NetworkClient.currentTokenStore().getTextScaleOption()
    }

    fun set(option: TextScaleOption) {
        NetworkClient.currentTokenStore().setTextScaleOption(option)
        _option.value = option
    }
}
