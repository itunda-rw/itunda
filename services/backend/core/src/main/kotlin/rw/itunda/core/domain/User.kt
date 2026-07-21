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

    // RBAC for /api/v1/system/** (see SecurityConfig) -- no self-service promotion
    // flow exists yet, see V4__user_role.sql's comment.
    @Column(name = "role", nullable = false, length = 16)
    var role: String = "USER",

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    // Real referral subsystem (2026-07-17): a lazily-issued code (nullable -- accounts
    // created before this migration have none) an existing user shares, plus which
    // existing user's code a new registrant used, if any. See AuthService.register and
    // RewardsService's task_referral eligibility check.
    @Column(name = "referral_code", unique = true, length = 16)
    var referralCode: String? = null,

    @Column(name = "referred_by_user_id", length = 64)
    var referredByUserId: String? = null,

    // Real "complete your profile" state (2026-07-17) -- backs task_profile's real
    // eligibility check in RewardsService, see AuthService's profile endpoints.
    @Column(name = "profile_photo_url", length = 512)
    var profilePhotoUrl: String? = null,

    @Column(name = "email_verified", nullable = false)
    var emailVerified: Boolean = false,

    // Real hyperlocal neighborhood (2026-07-20) -- closes the "User has no address/
    // district field" gap Marketplace/Community/Jobs/RealEstate's own doc comments all
    // name. Set once via AuthService.setNeighborhood from a real coordinate the user
    // shares (same opt-in-location convention those modules already use), reverse-
    // geocoded through itunda's own self-hosted Nominatim (NominatimGeocodingClient.
    // reverseGeocode) into a real neighborhood/sector-level name -- never self-declared
    // free text, so it can't drift from where the user actually is.
    @Column(name = "neighborhood", length = 120)
    var neighborhood: String? = null,

    // Evidence from the actual location-confirmation flow, not a self-declared badge.
    // `neighborhoodVerificationCount` lets clients describe repeat confirmation without
    // retaining a user's precise historical coordinates.
    @Column(name = "neighborhood_verified_at")
    var neighborhoodVerifiedAt: Instant? = null,

    @Column(name = "neighborhood_verification_count", nullable = false)
    var neighborhoodVerificationCount: Int = 0,
) {
    // JPA requires a no-arg constructor; Kotlin generates one only when every
    // property has a default, which id/phoneNumber/etc. intentionally don't.
    protected constructor() : this(id = "", phoneNumber = "", firstName = "", lastName = "", passwordHash = "")
}
