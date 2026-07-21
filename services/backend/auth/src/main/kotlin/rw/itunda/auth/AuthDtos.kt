package rw.itunda.auth

import java.time.Instant

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
)

data class LoginRequest(val phoneNumber: String, val password: String, val deviceId: String? = null, val deviceName: String? = null)

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
    val neighborhood: String?,
    val neighborhoodVerifiedAt: Instant? = null,
    val neighborhoodVerificationCount: Int = 0,
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

// Real hyperlocal neighborhood (2026-07-20) -- see User.neighborhood's own doc comment.
// A coordinate in, never a self-declared free-text neighborhood name -- AuthService
// reverse-geocodes it through itunda's own self-hosted Nominatim.
data class SetNeighborhoodRequest(val latitude: Double, val longitude: Double)

// Real device binding (2026-07-20) -- see TrustedDevice's own doc comment. Re-proves
// password ownership on the caller's own current device (resolved from their JWT).
data class VerifyDeviceRequest(val password: String)
