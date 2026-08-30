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
import rw.itunda.core.domain.LegalDocumentCatalog
import rw.itunda.core.domain.TermsCatalog
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
    private val userVerificationService: UserVerificationService,
    private val deviceService: DeviceService,
) {

    // Real unified phone-first entry (2026-08-13) -- see PhoneCheckRequest's own doc
    // comment. Public (see SecurityConfig): called before a user has any credential
    // at all, same as register/login below.
    @PostMapping("/check-phone")
    fun checkPhone(@RequestBody request: PhoneCheckRequest): ResponseEntity<PhoneCheckResponse> =
        ResponseEntity.ok(authService.checkPhoneExists(request.phoneNumber))

    // Real Toss/Korean-fintech-style 약관 동의 (terms consent) catalog -- see
    // rw.itunda.core.domain.TermsCatalog's own doc comment. Public (see
    // SecurityConfig): a client renders this real list, checked-vs-not, before the
    // user has any credential at all, same as check-phone/register above.
    @GetMapping("/terms")
    fun getTerms(): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "terms" to TermsCatalog.documents))

    // Real itunda-branded legal document bodies -- see LegalDocumentCatalog's own doc
    // comment for the full sourced account of why this exists (Android's Settings
    // screen has carried an honestly-inert "Legal Documents" card since 2026-08-12
    // with nowhere real to link to). Public (see SecurityConfig), same reasoning as
    // /terms above -- reference material, not a consent gate.
    @GetMapping("/legal-documents")
    fun getLegalDocuments(): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "documents" to LegalDocumentCatalog.documents))

    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest): ResponseEntity<AuthResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request))

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<AuthResponse> =
        ResponseEntity.ok(authService.login(request))

    // Real passwordless-login rollout (2026-08-24) -- see AuthService.
    // loginWithDeviceSignature's own doc comment. Public (see SecurityConfig): there's
    // no valid JWT yet, that's the entire point -- a real, unauthenticated challenge/
    // signature pair establishing a BRAND NEW session, distinct from /devices/challenge
    // and /devices/verify-signature above (which both require an already-valid JWT and
    // are step-up re-verification of an EXISTING session, not this).
    @PostMapping("/login/device/challenge")
    fun issueLoginChallenge(@RequestBody request: LoginChallengeRequest): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "challenge" to deviceService.issueLoginChallenge(request.phoneNumber, request.deviceId)))

    @PostMapping("/login/device/verify")
    fun loginWithDeviceSignature(@RequestBody request: LoginWithSignatureRequest): ResponseEntity<AuthResponse> =
        ResponseEntity.ok(authService.loginWithDeviceSignature(request))

    // Real Toss-sourced "set your 6-digit PIN" flow (2026-08-24) -- see AuthService.
    // setPin's own doc comment. Authenticated (a valid JWT plus the real current
    // credential re-proof setPin itself requires) -- covers both a pre-PIN-era user's
    // real upgrade prompt and a real "forgot PIN" reset once phone re-verification has
    // re-established a fresh credential.
    @PutMapping("/pin")
    fun setPin(
        @RequestBody request: SetPinRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "user" to authService.setPin(currentUser.userId, request)))

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

    // See UserVerificationService.requestEmailVerification's doc comment: the real
    // token is delivered via a real Notification, never echoed back here.
    @PostMapping("/profile/verify-email")
    fun requestEmailVerification(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Boolean>> {
        userVerificationService.requestEmailVerification(currentUser.userId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/profile/verify-email/confirm")
    fun confirmEmailVerification(
        @RequestBody request: ConfirmEmailVerificationRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "user" to userVerificationService.confirmEmailVerification(currentUser.userId, request.token)))

    // Real phone verification (2026-07-26) -- see
    // UserVerificationService.requestPhoneVerification's own doc comment. A code is
    // already sent automatically at registration; this is the real resend action for an
    // expired/lost code.
    @PostMapping("/profile/verify-phone")
    fun requestPhoneVerification(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Boolean>> {
        userVerificationService.requestPhoneVerification(currentUser.userId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/profile/verify-phone/confirm")
    fun confirmPhoneVerification(
        @RequestBody request: ConfirmPhoneVerificationRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "user" to userVerificationService.confirmPhoneVerification(currentUser.userId, request.code)))

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

    // Real dual-neighborhood support (2026-08-04) -- see AuthService.setSecondNeighborhood's
    // own doc comment.
    @PostMapping("/profile/second-neighborhood")
    fun setSecondNeighborhood(
        @RequestBody request: SetNeighborhoodRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(
            mapOf(
                "success" to true,
                "user" to authService.setSecondNeighborhood(currentUser.userId, request.latitude, request.longitude),
            ),
        )

    @DeleteMapping("/profile/second-neighborhood")
    fun clearSecondNeighborhood(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "user" to authService.clearSecondNeighborhood(currentUser.userId)))

    // Real age-eligibility gate for the Youth account (2026-07-28) -- see
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

    // Real Keystore/Secure-Enclave-signed-challenge device verification (item 246) --
    // see TrustedDevice.publicKey's own doc comment and DeviceService.registerDeviceKey.
    // Same password re-proof as /devices/verify, plus a client-generated hardware-backed
    // key -- a one-time cost that makes every FUTURE step-up a biometric prompt instead
    // of retyping a password. Always the CURRENT device (currentUser.deviceId), never a
    // client-supplied one.
    @PostMapping("/devices/register-key")
    fun registerDeviceKey(
        @RequestBody request: RegisterDeviceKeyRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(
            mapOf(
                "success" to true,
                "device" to deviceService.registerDeviceKey(currentUser.userId, currentUser.deviceId, request.publicKey, request.password),
            ),
        )

    // A fresh single-use nonce for the current device to sign -- the first half of the
    // biometric step-up path /devices/verify-signature completes.
    @PostMapping("/devices/challenge")
    fun issueDeviceChallenge(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "challenge" to deviceService.issueChallenge(currentUser.userId, currentUser.deviceId)))

    // Verifies a signature over the challenge above against this device's registered
    // key -- the cheap, password-free step-up a biometric prompt drives once a key exists.
    @PostMapping("/devices/verify-signature")
    fun verifyDeviceSignature(
        @RequestBody request: VerifyDeviceSignatureRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(
            mapOf(
                "success" to true,
                "device" to deviceService.verifyDeviceBySignature(currentUser.userId, currentUser.deviceId, request.signature),
            ),
        )

    @ExceptionHandler(DeviceNotFoundException::class)
    fun handleDeviceNotFound(ex: DeviceNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("DEVICE_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidDeviceVerificationException::class)
    fun handleInvalidDeviceVerification(ex: InvalidDeviceVerificationException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_DEVICE_VERIFICATION", ex.message ?: "Bad request"))

    @ExceptionHandler(PhoneAlreadyRegisteredException::class)
    fun handleConflict(ex: PhoneAlreadyRegisteredException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("PHONE_ALREADY_REGISTERED", ex.message ?: "Conflict"))

    @ExceptionHandler(RequiredTermsNotAcceptedException::class)
    fun handleRequiredTermsNotAccepted(ex: RequiredTermsNotAcceptedException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("REQUIRED_TERMS_NOT_ACCEPTED", ex.message ?: "Bad request"))

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

    @ExceptionHandler(InvalidPinException::class)
    fun handleInvalidPin(ex: InvalidPinException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_PIN", ex.message ?: "Bad request"))
}
