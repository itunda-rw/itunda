package rw.itunda.core.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed interface SessionState {
    data object LoggedOut : SessionState
    data class LoggedIn(val userId: String) : SessionState
}

sealed interface AuthResult {
    data object Success : AuthResult
    data class Failure(val message: String) : AuthResult
}

/**
 * The real login/session flow this app has never had (see NetworkClient's old
 * "TODO: Inject Token" comment) -- orchestrates AuthApi (services/backend's real
 * auth endpoints under api/v1/auth) and TokenStore (encrypted on-device persistence),
 * and exposes the session as a StateFlow so MainActivity can gate ItundaAppScreen
 * behind an actual login screen instead of rendering it unconditionally.
 */
object SessionManager {
    private val _sessionState = MutableStateFlow<SessionState>(SessionState.LoggedOut)
    val sessionState: StateFlow<SessionState> = _sessionState

    // App-process-lifetime scope for fire-and-forget background work (currently just
    // registerDeviceToken() below) -- SessionManager is itself a singleton with the
    // same lifetime, so this never needs its own explicit cancellation.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun restoreSession() {
        val tokenStore = NetworkClient.currentTokenStore()
        val userId = tokenStore.getUserId()
        _sessionState.value = if (tokenStore.hasSession() && userId != null) {
            SessionState.LoggedIn(userId)
        } else {
            SessionState.LoggedOut
        }
        // Real bug found live (2026-08-12): registerDeviceToken() was only ever called
        // from runAuthCall (a fresh login/register) -- an already-logged-in user
        // reopening the app (restoreSession(), the actual common case: most app opens
        // are a restore, not a fresh login) never registered a real push token at all.
        // Confirmed on a real physical device: notification permission granted, real
        // Firebase config live, yet zero device-token rows had ever reached the
        // backend, because this device's session was restored, not freshly logged
        // into, after the FCM work shipped. onNewToken() (ItundaMessagingService)
        // alone isn't a reliable substitute -- it only fires for a genuinely
        // new/refreshed token, and even then races this very function during cold
        // start. registerDeviceToken() itself is a safe, idempotent upsert
        // (DeviceTokenController.register's own doc comment), so calling it again
        // here on every restore is not wasted work.
        if (_sessionState.value is SessionState.LoggedIn) {
            scope.launch { registerDeviceToken() }
        }
    }

    // Real unified phone-first entry (2026-08-13) -- see ApiService.kt's identical
    // PhoneCheckRequest/checkPhone doc comment. Non-suspend-wrapped in a runAuthCall
    // (that helper expects an AuthResponse-shaped result) -- callers handle the raw
    // exists flag directly instead.
    suspend fun checkPhoneExists(phoneNumber: String): Boolean =
        NetworkClient.authApi.checkPhone(PhoneCheckRequest(phoneNumber)).exists

    // deviceId/deviceName added 2026-07-21 -- real device binding (see DeviceStore.kt),
    // mirrors bank-mfe's real login()/register() calls exactly. A stable per-install
    // id, not a one-off random value per call -- DeviceStore persists it.
    suspend fun login(phoneNumber: String, password: String): AuthResult {
        val deviceStore = NetworkClient.currentDeviceStore()
        return runAuthCall {
            NetworkClient.authApi.login(
                LoginRequest(phoneNumber, password, deviceStore.getOrCreateDeviceId(), deviceStore.getDeviceName()),
            )
        }
    }

    suspend fun register(
        phoneNumber: String,
        password: String,
        firstName: String,
        lastName: String,
        email: String? = null,
        referralCode: String? = null,
    ): AuthResult {
        val deviceStore = NetworkClient.currentDeviceStore()
        return runAuthCall {
            NetworkClient.authApi.register(
                RegisterRequest(
                    phoneNumber, email, firstName, lastName, password, referralCode,
                    deviceStore.getOrCreateDeviceId(), deviceStore.getDeviceName(),
                ),
            )
        }
    }

    suspend fun logout() {
        val tokenStore = NetworkClient.currentTokenStore()
        val accessToken = tokenStore.getAccessToken()
        val refreshToken = tokenStore.getRefreshToken()
        if (accessToken != null) {
            try {
                NetworkClient.authApi.logout("Bearer $accessToken", LogoutRequest(refreshToken))
            } catch (_: Exception) {
                // Best-effort server-side revocation -- even if this call fails (backend
                // unreachable, token already expired), the on-device session must still
                // clear below. A user who taps "log out" needs it to work locally
                // regardless of network state.
            }
            try {
                val deviceStore = NetworkClient.currentDeviceStore()
                NetworkClient.authApi.unregisterDeviceToken("Bearer $accessToken", deviceStore.getOrCreateDeviceId())
            } catch (_: Exception) {
                // Real push unregister-on-logout (item 232) -- best-effort, see
                // ApiService.unregisterDeviceToken's own doc comment.
            }
        }
        tokenStore.clearSession()
        _sessionState.value = SessionState.LoggedOut
    }

    // Real gap found 2026-08-15: NetworkClient's own refresh Authenticator calls this
    // when the refresh token itself is invalid/expired (JwtService's real 7-day
    // expiry) -- unlike logout() above, there is no valid access token left to send a
    // real server-side revocation call with, so this only clears local state and
    // drops the session back to the real login screen. Not calling the backend here
    // is correct, not an oversight: the token is already unusable server-side too.
    fun forceLocalLogout() {
        NetworkClient.currentTokenStore().clearSession()
        _sessionState.value = SessionState.LoggedOut
    }

    private suspend fun runAuthCall(call: suspend () -> AuthResponse): AuthResult = try {
        val response = call()
        NetworkClient.currentTokenStore().saveSession(response.user.id, response.accessToken, response.refreshToken)
        _sessionState.value = SessionState.LoggedIn(response.user.id)
        registerDeviceToken()
        AuthResult.Success
    } catch (e: retrofit2.HttpException) {
        AuthResult.Failure(httpErrorMessage(e))
    } catch (e: Exception) {
        AuthResult.Failure("Couldn't reach itunda. Check your connection and try again.")
    }

    // Real push device-token registration (item 120) -- see ApiService.kt's own doc
    // comment. Best-effort and fire-and-forget: a registration failure must never
    // block an otherwise-successful login/register.
    //
    // Real FCM token (2026-08-12) -- replaces the client-generated device id this used
    // to register as a "push token", the exact gap PushSender.kt's own doc comment
    // named ("everything up to and including which real device tokens should receive
    // this push ... is fully real; only the actual network hop ... is simulated").
    // currentPushToken() falls back to the device id below whenever Firebase isn't
    // configured (no google-services.json, see :app's build.gradle.kts), so this stays
    // exactly as safe/best-effort as before on an unconfigured build.
    suspend fun registerDeviceToken() {
        try {
            val deviceStore = NetworkClient.currentDeviceStore()
            val token = currentPushToken() ?: deviceStore.getOrCreateDeviceId()
            NetworkClient.authApi.registerDeviceToken(RegisterDeviceTokenRequest(DevicePlatform.ANDROID, token))
        } catch (_: Exception) {
            // Best-effort, see doc comment above.
        }
    }

    // Public (called again from ItundaMessagingService.onNewToken whenever FCM issues a
    // fresh token, e.g. after a reinstall or the app's own local data being cleared --
    // the token registered at login time can go stale independent of the session
    // itself). DeviceTokenController.register's own doc comment: re-registering the
    // same or a new token is a natural idempotent upsert, never a duplicate row.
    private suspend fun currentPushToken(): String? = try {
        // FirebaseApp.getInstance() throws IllegalStateException when no
        // google-services.json was present at build time (see :app's build.gradle.kts)
        // -- the real, cheap way to check "is Firebase actually configured here" without
        // this module needing to hold onto an Application Context of its own.
        com.google.firebase.FirebaseApp.getInstance()
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
    } catch (_: Exception) {
        null
    }

    // Real gap found live (2026-08-10): the two other backend-message helpers in this
    // app (ErrorMessages.kt's superAppErrorMessage, MainViewModel's own
    // backendErrorMessage) both call apiErrorMessage(e) first for a real, specific
    // backend message -- this one, backing login/register, itunda's own first-touch
    // flow, never did. Also adds explicit 503/504 handling matching those two: a real
    // backend outage here is the single worst place to leave a user on a vague
    // "something went wrong" -- it's the very first thing a new or returning user
    // sees, with the least reason yet to trust the app if it looks broken.
    private fun httpErrorMessage(e: retrofit2.HttpException): String = apiErrorMessage(e) ?: when (e.code()) {
        401 -> "Incorrect phone number or password."
        409 -> "An account with this phone number already exists."
        429 -> "Too many attempts. Please wait a moment and try again."
        503, 504 -> "itunda is having a brief hiccup on our end -- not something you did. Try again in a moment."
        else -> "Something went wrong. Please try again."
    }
}
