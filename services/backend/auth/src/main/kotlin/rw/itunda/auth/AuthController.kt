package rw.itunda.auth

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest): ResponseEntity<AuthResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request))

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<AuthResponse> =
        ResponseEntity.ok(authService.login(request))

    /** Public (see SecurityConfig): the refresh token itself, not an access token, is
     * the credential here — there's no valid access token to require by the time a
     * client needs this. Rotates the refresh token (old one is revoked immediately). */
    @PostMapping("/refresh")
    fun refresh(@RequestBody request: RefreshRequest): ResponseEntity<AuthResponse> =
        ResponseEntity.ok(authService.refresh(request.refreshToken))

    /** Requires a valid (not-yet-revoked) access token to call at all, then revokes
     * that exact token — and the refresh token too, if the client still has it. */
    @PostMapping("/logout")
    fun logout(
        @RequestHeader("Authorization") authHeader: String,
        @RequestBody(required = false) request: LogoutRequest?,
    ): ResponseEntity<Map<String, Boolean>> {
        val accessToken = authHeader.removePrefix("Bearer ")
        authService.logout(accessToken, request?.refreshToken)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @GetMapping("/profile")
    fun profile(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "user" to authService.getProfile(currentUser.userId)))

    @ExceptionHandler(PhoneAlreadyRegisteredException::class)
    fun handleConflict(ex: PhoneAlreadyRegisteredException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("PHONE_ALREADY_REGISTERED", ex.message ?: "Conflict"))

    @ExceptionHandler(InvalidCredentialsException::class)
    fun handleInvalidCredentials(ex: InvalidCredentialsException) =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError("INVALID_CREDENTIALS", ex.message ?: "Invalid credentials"))

    @ExceptionHandler(UserNotFoundException::class)
    fun handleNotFound(ex: UserNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("USER_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidRefreshTokenException::class)
    fun handleInvalidRefresh(ex: InvalidRefreshTokenException) =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError("INVALID_REFRESH_TOKEN", ex.message ?: "Invalid refresh token"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))

    @ExceptionHandler(ReferralCodeNotFoundException::class)
    fun handleReferralCodeNotFound(ex: ReferralCodeNotFoundException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("REFERRAL_CODE_NOT_FOUND", ex.message ?: "Referral code not found"))
}
