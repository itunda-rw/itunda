package rw.itunda.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

    fun restoreSession() {
        val tokenStore = NetworkClient.currentTokenStore()
        val userId = tokenStore.getUserId()
        _sessionState.value = if (tokenStore.hasSession() && userId != null) {
            SessionState.LoggedIn(userId)
        } else {
            SessionState.LoggedOut
        }
    }

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
    private suspend fun registerDeviceToken() {
        try {
            val deviceStore = NetworkClient.currentDeviceStore()
            NetworkClient.authApi.registerDeviceToken(RegisterDeviceTokenRequest(DevicePlatform.ANDROID, deviceStore.getOrCreateDeviceId()))
        } catch (_: Exception) {
            // Best-effort, see doc comment above.
        }
    }

    private fun httpErrorMessage(e: retrofit2.HttpException): String = when (e.code()) {
        401 -> "Incorrect phone number or password."
        409 -> "An account with this phone number already exists."
        429 -> "Too many attempts. Please wait a moment and try again."
        else -> "Something went wrong. Please try again."
    }
}
