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

    // Real Karrot-Score-style numeric trust/reputation badge (2026-07-21) -- closes
    // the "Hood cards show no seller/poster reputation at all" gap docs/
    // DESIGN_REFERENCES.md's Hood research names directly. Deliberately a plain
    // 0-1000 score starting at 30, NOT a literal manner-temperature/Celsius metaphor:
    // Karrot's own real localization research (same doc) found the temperature
    // framing confusing and low scores insulting for non-Korean users, and replaced
    // it with exactly this neutral scale for its own global markets -- Rwanda gets
    // that already-localized version directly. Kept simple and honest for v1: bumped
    // by a fixed amount (see rw.itunda.core.trust.TrustScore) each time one of this
    // user's own Hood listings/job posts/property listings completes a real
    // transaction (mark-sold/mark-filled/mark-taken), capped at 1000 -- a function of
    // real completed transactions, not a self-reported or free-text claim. No review
    // system exists yet to also weight into this (see docs/TOSS_PARITY_MATRIX.md's
    // Marketplace/Jobs/RealEstate rows), a named v1 scope, not a hidden omission.
    @Column(name = "trust_score", nullable = false)
    var trustScore: Int = 30,
) {
    // JPA requires a no-arg constructor; Kotlin generates one only when every
    // property has a default, which id/phoneNumber/etc. intentionally don't.
    protected constructor() : this(id = "", phoneNumber = "", firstName = "", lastName = "", passwordHash = "")
}
