package rw.itunda.core.network

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val LOCALE_PREFS_NAME = "itunda_locale_prefs"
private const val LOCALE_PREFS_KEY = "app_locale"

/**
 * Live app-wide language override (2026-08-08 fix) -- promoted here from
 * LoginScreen.kt's own file-private readStoredLocale/storeLocale after finding a
 * real, serious propagation bug: LoginScreen.kt wrapped only ITS OWN subtree in the
 * Configuration-overridden CompositionLocalProvider, and MainActivity.kt's setContent
 * never wrapped ItundaAppScreen (everything a user sees after logging in) in
 * anything equivalent. The in-app switcher therefore only ever affected the login
 * screen itself -- every stringResource() call in OverviewScreen.kt/TransferFlow.kt/
 * SettingsScreen.kt and everywhere else was silently reading the device's raw OS
 * locale the whole time, never the stored in-app choice, unless the phone's own
 * system language happened to already be Kinyarwanda. A StateFlow (mirroring
 * ThemePreference's own established pattern in this same file's directory) fixes
 * this the same way: read once at Application startup, held live in memory, wrapped
 * once around the whole app root in MainActivity.kt so every screen in every module
 * sees the same Configuration-overridden LocalContext regardless of which Compose
 * subtree it's mounted under -- CompositionLocal propagation crosses Gradle-module
 * boundaries at runtime even though the modules can't see each other at compile time.
 */
object AppLocalePreference {
    private val _locale = MutableStateFlow("en")
    val locale: StateFlow<String> = _locale

    fun restore(context: Context) {
        val stored = context.getSharedPreferences(LOCALE_PREFS_NAME, Context.MODE_PRIVATE).getString(LOCALE_PREFS_KEY, null)
        // French (values-fr/) added 2026-08-15 as itunda's third real locale -- a
        // device already set to French used to silently fall through to English here,
        // the same class of gap as the LanguageSwitcher toggle that couldn't reach it
        // either (see LoginScreen.kt's own doc comment on that fix).
        val systemLanguage = java.util.Locale.getDefault().language
        _locale.value = stored ?: if (systemLanguage == "rw" || systemLanguage == "fr") systemLanguage else "en"
    }

    fun set(context: Context, locale: String) {
        context.getSharedPreferences(LOCALE_PREFS_NAME, Context.MODE_PRIVATE).edit().putString(LOCALE_PREFS_KEY, locale).apply()
        _locale.value = locale
    }
}
