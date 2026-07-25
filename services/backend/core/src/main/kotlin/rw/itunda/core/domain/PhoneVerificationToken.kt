package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A real, single-use, expiring token backing phone-number verification -- same shape as
 * `EmailVerificationToken`, just a random 6-digit OTP-style `token` value instead of a
 * hex string (matching the real-world phone-OTP UX this mirrors) and issued at
 * registration rather than opt-in from a profile screen. See
 * `AuthService.requestPhoneVerification`'s own doc comment for the full delivery story.
 */
@Entity
@Table(name = "phone_verification_tokens")
class PhoneVerificationToken(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(nullable = false, unique = true, length = 64)
    val token: String,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "used_at")
    var usedAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", token = "", expiresAt = Instant.now())
}
