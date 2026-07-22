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

    private companion object {
        const val KEY_USER_ID = "user_id"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
    }
}
