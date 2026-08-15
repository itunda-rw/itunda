package rw.itunda.core.network

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom

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

    // Real app-lock PIN (2026-08-13, direct user correction with real Toss
    // screenshots): AppLockScreen.kt's own original doc comment argued against a PIN
    // store as "a fake-security shortcut" -- but that was comparing it to the wrong
    // thing. This isn't a second copy of the real login *password* (a server-verified
    // account credential); it's the same class of thing biometric already is here: a
    // local-only "is this still the person holding the phone" gate on an ALREADY
    // -authenticated session, never sent to or checked by the backend. Real Toss
    // itself uses a PIN as biometric's actual fallback (confirmed directly by the
    // user), and this store already holds the real access/refresh tokens behind
    // Keystore-backed AES256-GCM encryption (this class's own header comment) -- a
    // hashed PIN in the same encrypted store has strictly more real protection than
    // the "no re-entry challenge at all" gap this closes (MainActivity.kt used to
    // fall straight through to the app with zero challenge whenever biometrics
    // weren't available/enrolled, even with app-lock switched on). SHA-256 with a
    // random per-install salt on top of the store's own AES-GCM encryption is
    // defense in depth, not the whole defense.
    fun hasPin(): Boolean = prefs.getString(KEY_PIN_HASH, null) != null

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_PIN_SALT, salt.joinToString("") { "%02x".format(it) })
            .putString(KEY_PIN_HASH, hashPin(pin, salt))
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val saltHex = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val salt = ByteArray(saltHex.length / 2) { i -> saltHex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        return hashPin(pin, salt) == storedHash
    }

    fun clearPin() {
        prefs.edit().remove(KEY_PIN_HASH).remove(KEY_PIN_SALT).apply()
    }

    private fun hashPin(pin: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        return digest.digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }
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

    // Real Uber Simple Mode-style text-scale accessibility setting (2026-08-15) --
    // see TextScalePreference's own doc comment for the full sourced account. Same
    // "default preserves existing behavior for everyone who never touches the new
    // Settings row" discipline as getThemeMode above.
    fun getTextScaleOption(): TextScaleOption = when (prefs.getString(KEY_TEXT_SCALE, null)) {
        "large" -> TextScaleOption.LARGE
        "extra_large" -> TextScaleOption.EXTRA_LARGE
        else -> TextScaleOption.DEFAULT
    }
    fun setTextScaleOption(option: TextScaleOption) {
        prefs.edit().putString(KEY_TEXT_SCALE, option.name.lowercase()).apply()
    }

    private companion object {
        const val KEY_USER_ID = "user_id"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_TEXT_SCALE = "text_scale_option"
        const val KEY_PIN_HASH = "app_lock_pin_hash"
        const val KEY_PIN_SALT = "app_lock_pin_salt"
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }
