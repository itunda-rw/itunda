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
    // Real passwordless-login rollout (2026-08-24) -- see DeviceService.
    // registerKeyDuringAuth's own doc comment. Optional: an older, not-yet-updated
    // client that doesn't send one just doesn't get the passwordless-login upgrade,
    // same additive-rollout convention deviceId/deviceName above already established.
    // Raw uncompressed P-256 point (0x04 || X || Y, base64), same real wire format
    // RegisterDeviceKeyRequest.publicKey already uses -- Android Keystore/iOS Secure
    // Enclave hand this back natively on either platform, no per-platform conversion.
    val devicePublicKey: String? = null,
)

data class LoginRequest(
    val phoneNumber: String,
    val password: String,
    val deviceId: String? = null,
    val deviceName: String? = null,
    // See RegisterRequest.devicePublicKey's own doc comment -- the same real one-time
    // key-registration fold-in, for a device that's logging in with its PIN/password
    // for the first time (or re-establishing a key after RegisterDeviceKeyRequest was
    // never called).
    val devicePublicKey: String? = null,
)

// Real Toss-sourced 6-digit PIN, replacing the free-form password RegisterRequest.password
// still accepts for backward compatibility (existing rows keep whatever shape they
// registered with) -- see User.pinSet's own doc comment. currentCredential re-proves
// ownership before the change takes effect: the caller's EXISTING password (any shape,
// pre-PIN-era) if pinSet is still false, or their current 6-digit PIN if pinSet is
// already true -- either way, the exact same passwordEncoder.matches check AuthService.
// login already does, just reused here instead of re-implemented.
data class SetPinRequest(val currentCredential: String, val newPin: String)

// Real passwordless LOGIN (2026-08-24) -- unauthenticated counterpart to
// DeviceChallengeResponse/VerifyDeviceSignatureRequest above, which both require an
// already-valid JWT (step-up re-verification of an existing session). This pair is for
// establishing a BRAND NEW session with no JWT at all -- see DeviceService.
// issueLoginChallenge/verifyLoginSignature's own doc comments.
data class LoginChallengeRequest(val phoneNumber: String, val deviceId: String)
data class LoginWithSignatureRequest(val phoneNumber: String, val deviceId: String, val signature: String)

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
    // Real Toss passwordless-login rollout (2026-08-24) -- see User.pinSet's own doc
    // comment. false means this account predates the 6-digit-PIN scheme -- a client
    // should prompt a real, non-blocking "set your new 6-digit PIN" upgrade via
    // PUT /api/v1/auth/pin, not silently ignore it.
    val pinSet: Boolean = true,
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
