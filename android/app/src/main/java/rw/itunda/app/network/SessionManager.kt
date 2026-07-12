package rw.itunda.app.network

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

    suspend fun login(phoneNumber: String, password: String): AuthResult =
        runAuthCall { NetworkClient.authApi.login(LoginRequest(phoneNumber, password)) }

    suspend fun register(
        phoneNumber: String,
        password: String,
        firstName: String,
        lastName: String,
        email: String? = null,
    ): AuthResult = runAuthCall {
        NetworkClient.authApi.register(RegisterRequest(phoneNumber, email, firstName, lastName, password))
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
        }
        tokenStore.clearSession()
        _sessionState.value = SessionState.LoggedOut
    }

    private suspend fun runAuthCall(call: suspend () -> AuthResponse): AuthResult = try {
        val response = call()
        NetworkClient.currentTokenStore().saveSession(response.user.id, response.accessToken, response.refreshToken)
        _sessionState.value = SessionState.LoggedIn(response.user.id)
        AuthResult.Success
    } catch (e: retrofit2.HttpException) {
        AuthResult.Failure(httpErrorMessage(e))
    } catch (e: Exception) {
        AuthResult.Failure("Couldn't reach itunda. Check your connection and try again.")
    }

    private fun httpErrorMessage(e: retrofit2.HttpException): String = when (e.code()) {
        401 -> "Incorrect phone number or password."
        409 -> "An account with this phone number already exists."
        429 -> "Too many attempts. Please wait a moment and try again."
        else -> "Something went wrong. Please try again."
    }
}
