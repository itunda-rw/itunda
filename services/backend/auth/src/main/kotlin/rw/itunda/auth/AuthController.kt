package rw.itunda.auth

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: AuthService, private val deviceService: DeviceService) {

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

    // Real, buildable half of Rewards' task_profile -- see UpdateProfilePhotoRequest's
    // doc comment for why this is a URL, not a binary upload.
    @PutMapping("/profile/photo")
    fun updateProfilePhoto(
        @RequestBody request: UpdateProfilePhotoRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "user" to authService.updateProfilePhoto(currentUser.userId, request.profilePhotoUrl)))

    // See AuthService.requestEmailVerification's doc comment: the real token is
    // delivered via a real Notification, never echoed back here.
    @PostMapping("/profile/verify-email")
    fun requestEmailVerification(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Boolean>> {
        authService.requestEmailVerification(currentUser.userId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/profile/verify-email/confirm")
    fun confirmEmailVerification(
        @RequestBody request: ConfirmEmailVerificationRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "user" to authService.confirmEmailVerification(currentUser.userId, request.token)))

    // Real phone verification (2026-07-26) -- see AuthService.requestPhoneVerification's
    // own doc comment. A code is already sent automatically at registration; this is the
    // real resend action for an expired/lost code.
    @PostMapping("/profile/verify-phone")
    fun requestPhoneVerification(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Boolean>> {
        authService.requestPhoneVerification(currentUser.userId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/profile/verify-phone/confirm")
    fun confirmPhoneVerification(
        @RequestBody request: ConfirmPhoneVerificationRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "user" to authService.confirmPhoneVerification(currentUser.userId, request.code)))

    // Real hyperlocal neighborhood (2026-07-20) -- see AuthService.setNeighborhood's own
    // doc comment.
    @PostMapping("/profile/neighborhood")
    fun setNeighborhood(
        @RequestBody request: SetNeighborhoodRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(
            mapOf(
                "success" to true,
                "user" to authService.setNeighborhood(currentUser.userId, request.latitude, request.longitude),
            ),
        )

    // Real age-eligibility gate for the Mini wallet (2026-07-28) -- see
    // AuthService.setBirthDate's own doc comment.
    @PostMapping("/profile/birth-date")
    fun setBirthDate(
        @RequestBody request: SetBirthDateRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "user" to authService.setBirthDate(currentUser.userId, request.birthDate)))

    // Real device binding (2026-07-20) -- see TrustedDevice's own doc comment. Real
    // Toss-style device management: list every real device this account has ever
    // signed in from, which ones are trusted (can move money) vs merely seen.
    @GetMapping("/devices")
    fun getMyDevices(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "devices" to deviceService.getMyDevices(currentUser.userId)))

    // Real step-up re-verification for the CURRENT device (resolved from the caller's
    // own JWT, never a client-supplied id) -- re-proves password ownership, then marks
    // it trusted so it can move money going forward. See DeviceVerificationFilter for
    // where an unverified device is actually blocked.
    @PostMapping("/devices/verify")
    fun verifyDevice(
        @RequestBody request: VerifyDeviceRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "device" to deviceService.verifyDevice(currentUser.userId, currentUser.deviceId, request.password)))

    // Real "forget this device" -- self-service device management, same real control
    // Toss's own security settings page offers.
    @DeleteMapping("/devices/{deviceId}")
    fun revokeDevice(
        @PathVariable deviceId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Boolean>> {
        deviceService.revokeDevice(currentUser.userId, deviceId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @ExceptionHandler(DeviceNotFoundException::class)
    fun handleDeviceNotFound(ex: DeviceNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("DEVICE_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidDeviceVerificationException::class)
    fun handleInvalidDeviceVerification(ex: InvalidDeviceVerificationException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_DEVICE_VERIFICATION", ex.message ?: "Bad request"))

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

    @ExceptionHandler(NoEmailOnFileException::class)
    fun handleNoEmail(ex: NoEmailOnFileException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("NO_EMAIL_ON_FILE", ex.message ?: "No email on file"))

    @ExceptionHandler(EmailAlreadyVerifiedException::class)
    fun handleEmailAlreadyVerified(ex: EmailAlreadyVerifiedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("EMAIL_ALREADY_VERIFIED", ex.message ?: "Conflict"))

    @ExceptionHandler(PhoneAlreadyVerifiedException::class)
    fun handlePhoneAlreadyVerified(ex: PhoneAlreadyVerifiedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("PHONE_ALREADY_VERIFIED", ex.message ?: "Conflict"))

    @ExceptionHandler(InvalidVerificationTokenException::class)
    fun handleInvalidVerificationToken(ex: InvalidVerificationTokenException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_VERIFICATION_TOKEN", ex.message ?: "Invalid or expired token"))

    @ExceptionHandler(InvalidCoordinatesException::class)
    fun handleInvalidCoordinates(ex: InvalidCoordinatesException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_COORDINATES", ex.message ?: "Invalid coordinates"))

    @ExceptionHandler(NeighborhoodNotResolvedException::class)
    fun handleNeighborhoodNotResolved(ex: NeighborhoodNotResolvedException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("NEIGHBORHOOD_NOT_RESOLVED", ex.message ?: "Couldn't determine a neighborhood"))

    @ExceptionHandler(InvalidProfilePhotoUrlException::class)
    fun handleInvalidProfilePhotoUrl(ex: InvalidProfilePhotoUrlException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_PROFILE_PHOTO_URL", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidBirthDateException::class)
    fun handleInvalidBirthDate(ex: InvalidBirthDateException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_BIRTH_DATE", ex.message ?: "Bad request"))
}
