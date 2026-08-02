package rw.itunda.core.network

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Real session storage backing the login flow (2026-07-11) -- previously there was
 * nowhere to put a token at all (see NetworkClient's old `// TODO: Inject Token`
 * comment and SaroniteBridge.kt's ItundaSaroniteHostBridge.getAuthToken(), which
 * honestly returned null because there was nothing to return). Access/refresh tokens
 * are real bearer credentials -- EncryptedSharedPreferences (AES256-GCM-backed via
 * Android Keystore), not plain SharedPreferences, matching the standard this repo's
 * own SECURITY.md holds everything else to.
 *
 * Reads/writes are synchronous by design: OkHttp's Interceptor.intercept() runs on a
 * background thread already and has no suspend entry point, so NetworkClient's auth
 * interceptor needs a synchronous getAccessToken() to inject the header at all.
 */
class TokenStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "itunda_session",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun saveSession(userId: String, accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)
    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)
    fun hasSession(): Boolean = getAccessToken() != null

    // Real app-launch biometric unlock gate (2026-07-21) -- see AppLockScreen.kt's own
    // doc comment. Default-on (matching Toss's own default PIN/Face-ID-gated launch,
    // per docs/DESIGN_REFERENCES.md Section 8) whenever the device actually has
    // biometrics enrolled; a user can opt out from Settings.
    fun isAppLockEnabled(): Boolean = prefs.getBoolean(KEY_APP_LOCK_ENABLED, true)
    fun setAppLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply()
    }

    // Real in-app theme override (2026-08-03) -- IdsTheme always followed the OS's
    // isSystemInDarkTheme() with no in-app way to override it, so a phone left in
    // system dark mode renders the whole app in the dark palette with no way back to
    // the light Toss reference look from inside the app. Real Toss's own app ships a
    // 화면 모드 setting for exactly this. Default SYSTEM keeps existing behavior for
    // everyone who never touches the new Settings row.
    fun getThemeMode(): ThemeMode = when (prefs.getString(KEY_THEME_MODE, null)) {
        "light" -> ThemeMode.LIGHT
        "dark" -> ThemeMode.DARK
        else -> ThemeMode.SYSTEM
    }
    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name.lowercase()).apply()
    }

    private companion object {
        const val KEY_USER_ID = "user_id"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        const val KEY_THEME_MODE = "theme_mode"
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }
