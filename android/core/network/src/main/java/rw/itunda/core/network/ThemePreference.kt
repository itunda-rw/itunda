package rw.itunda.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Live app-wide theme override, backing SettingsScreen's Display row. A StateFlow
 * (not just a TokenStore getter read once in MainActivity) so toggling the setting
 * takes effect immediately across the whole composition tree without an app
 * restart -- same reasoning as SessionManager.sessionState.
 */
object ThemePreference {
    private val _mode = MutableStateFlow(ThemeMode.SYSTEM)
    val mode: StateFlow<ThemeMode> = _mode

    fun restore() {
        _mode.value = NetworkClient.currentTokenStore().getThemeMode()
    }

    fun set(mode: ThemeMode) {
        NetworkClient.currentTokenStore().setThemeMode(mode)
        _mode.value = mode
    }
}
