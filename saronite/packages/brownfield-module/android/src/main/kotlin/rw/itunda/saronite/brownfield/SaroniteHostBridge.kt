package rw.itunda.saronite.brownfield

import android.app.Activity

/**
 * What a host app must supply to run Saronite mini-apps.
 *
 * Granite (Toss's real framework) doesn't need an equivalent because its
 * private native implementation lives inside the Toss app itself, with
 * direct access to Toss's own session/auth internals. Saronite's Kotlin
 * module is a standalone library that itunda's app hasn't been wired into
 * yet (see saronite/README.md), so it can't reach into
 * `com.itunda.app.data.remote.RetrofitClient` directly. Instead the host
 * app implements this interface and hands it to `SaronitePackage` — the
 * same dependency-inversion a brownfield bridge needs regardless of which
 * concrete app embeds it.
 */
interface SaroniteHostBridge {
    /** Base URL of itunda's backend, e.g. BuildConfig.API_BASE_URL. */
    fun getApiBaseUrl(): String

    /** Bearer token for the signed-in user, or null if not authenticated. */
    fun getAuthToken(): String?

    /** The scheme URI mini-apps use to return control to the host app. */
    fun getSchemeUri(): String

    /**
     * Called when a mini-app asks to be closed. `activity` is whichever
     * Activity is currently hosting the React Native instance (from the
     * native module's own `currentActivity`, tracked by React Native
     * itself) — the host app doesn't need to track this separately.
     */
    fun onCloseView(activity: Activity?)
}
