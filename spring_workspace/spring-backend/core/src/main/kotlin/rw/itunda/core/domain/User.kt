package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Mirrors backend/src/types/index.ts User + the seed row in
 * backend/src/services/database.ts, so the Express and Kotlin backends model
 * the same account during the migration.
 */
@Entity
@Table(name = "users")
class User(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "phone_number", nullable = false, unique = true, length = 32)
    var phoneNumber: String,

    @Column(nullable = true)
    var email: String? = null,

    @Column(name = "first_name", nullable = false)
    var firstName: String,

    @Column(name = "last_name", nullable = false)
    var lastName: String,

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String,

    @Column(name = "kyc_verified", nullable = false)
    var kycVerified: Boolean = false,

    @Column(name = "credit_score", nullable = false)
    var creditScore: Int = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
) {
    // JPA requires a no-arg constructor; Kotlin generates one only when every
    // property has a default, which id/phoneNumber/etc. intentionally don't.
    protected constructor() : this(id = "", phoneNumber = "", firstName = "", lastName = "", passwordHash = "")
}
