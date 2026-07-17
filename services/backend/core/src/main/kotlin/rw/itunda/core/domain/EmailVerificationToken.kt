package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A real, single-use, expiring token backing profile email verification -- same
 * persisted-expiry shape as P2pPaymentRequest.expiresAt, not a JWT (nothing here needs
 * to be independently verifiable off a signature, and a stored row is what lets a token
 * be marked used exactly once).
 */
@Entity
@Table(name = "email_verification_tokens")
class EmailVerificationToken(
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
