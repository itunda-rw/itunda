package rw.itunda.auth

import java.time.Instant
import java.time.LocalDate

data class RegisterRequest(
    val phoneNumber: String,
    val email: String?,
    val firstName: String,
    val lastName: String,
    val password: String,
    // An existing user's referral_code, optional. Resolved to that user's id and stored
    // as the new user's referredByUserId -- see AuthService.register.
    val referralCode: String? = null,
    // Real device binding (2026-07-20) -- see TrustedDevice's own doc comment. A real,
    // stable, client-generated identifier (a UUID persisted in local storage/Keychain/
    // SharedPreferences), optional so older, not-yet-updated clients keep working
    // exactly as before (see DeviceVerificationFilter's own doc comment on the rollout).
    val deviceId: String? = null,
    val deviceName: String? = null,
    // Real Toss/Korean-fintech-style 약관 동의 (terms consent) -- see
    // rw.itunda.core.domain.TermsCatalog's own doc comment for the full sourced
    // account. The client-checked subset of TermsCatalog.documents' real ids; every
    // TermsCatalog.requiredIds() must be present or AuthService.register real-400s
    // with RequiredTermsNotAcceptedException. Defaults to empty (not required=true by
    // default) so this stays additive rather than silently breaking a request built
    // against the old RegisterRequest shape -- the real enforcement lives in
    // AuthService.register's own explicit check, not a Kotlin default.
    val acceptedTermsIds: List<String> = emptyList(),
)

data class LoginRequest(val phoneNumber: String, val password: String, val deviceId: String? = null, val deviceName: String? = null)

// Real unified phone-first entry (2026-08-13, direct user description of the real
// Toss flow): rather than making a user pick "Log in" vs "Sign up" upfront, the
// phone number is entered once and the client asks the backend whether an account
// already exists for it, then branches into the right next step (password for an
// existing account, name/password for a new one) -- same pattern WhatsApp/Kakao/
// Toss all use. See AuthService.checkPhoneExists's own doc comment for the real
// existsByPhoneNumber reuse and rate-limiting.
data class PhoneCheckRequest(val phoneNumber: String)
data class PhoneCheckResponse(val exists: Boolean)

data class RefreshRequest(val refreshToken: String)

/** refreshToken is optional: a client that lost it (or never stored it) can still log
 * out the current access token; if provided, the refresh token is revoked too so it
 * can't be used to silently mint a new access token after the user thought they left. */
data class LogoutRequest(val refreshToken: String? = null)

data class PublicUser(
    val id: String,
    val phoneNumber: String,
    val email: String?,
    val firstName: String,
    val lastName: String,
    val kycVerified: Boolean,
    val creditScore: Int,
    val createdAt: Instant,
    val referralCode: String?,
    val profilePhotoUrl: String?,
    val emailVerified: Boolean,
    val phoneVerified: Boolean,
    val neighborhood: String?,
    val neighborhoodVerifiedAt: Instant? = null,
    val neighborhoodVerificationCount: Int = 0,
    val secondNeighborhood: String? = null,
    val birthDate: LocalDate? = null,
)

data class AuthResponse(
    val success: Boolean = true,
    val message: String,
    val user: PublicUser,
    val accessToken: String,
    val refreshToken: String,
)

/** A URL, not a binary upload -- there is no file-storage layer in this backend, same
 * honest simplification IdentityController.kt's documentReference already established. */
data class UpdateProfilePhotoRequest(val profilePhotoUrl: String)

data class ConfirmEmailVerificationRequest(val token: String)
data class ConfirmPhoneVerificationRequest(val code: String)

// Real hyperlocal neighborhood (2026-07-20) -- see User.neighborhood's own doc comment.
// A coordinate in, never a self-declared free-text neighborhood name -- AuthService
// reverse-geocodes it through itunda's own self-hosted Nominatim.
data class SetNeighborhoodRequest(val latitude: Double, val longitude: Double)

// Real age-eligibility gate for the Youth account (2026-07-28) -- see User.birthDate's own
// doc comment. Set once; AuthService validates it's a real, plausible past date.
data class SetBirthDateRequest(val birthDate: LocalDate)

// Real device binding (2026-07-20) -- see TrustedDevice's own doc comment. Re-proves
// password ownership on the caller's own current device (resolved from their JWT).
data class VerifyDeviceRequest(val password: String)

// Real Keystore/Secure-Enclave-signed-challenge device verification (item 246) -- see
// TrustedDevice.publicKey's own doc comment and DeviceService.registerDeviceKey. Password
// is required here too (not just a valid JWT): registering a key is exactly as strong a
// trust decision as VerifyDeviceRequest above, so it must clear the same bar -- a stolen
// JWT alone must never be enough to plant an attacker-controlled key.
data class RegisterDeviceKeyRequest(val publicKey: String, val password: String)

data class DeviceChallengeResponse(val challenge: String)

data class VerifyDeviceSignatureRequest(val signature: String)
