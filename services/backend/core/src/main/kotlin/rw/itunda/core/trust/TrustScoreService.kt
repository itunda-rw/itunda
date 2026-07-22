package rw.itunda.core.trust

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.JobPostStatus
import rw.itunda.core.domain.ListingStatus
import rw.itunda.core.domain.PropertyListingStatus
import rw.itunda.core.repository.JobPostRepository
import rw.itunda.core.repository.ListingRepository
import rw.itunda.core.repository.PropertyListingRepository
import rw.itunda.core.repository.UserRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.min

class TrustScoreUserNotFoundException(message: String) : RuntimeException(message)

data class TrustScoreFactor(val name: String, val points: Int, val description: String)
data class TrustScoreResult(val score: Int, val factors: List<TrustScoreFactor>, val computedAt: Instant)

/**
 * Real Karrot-Score-style numeric trust/reputation badge -- closes the "itunda's Hood
 * cards show no seller/poster reputation at all today" gap docs/DESIGN_REFERENCES.md's
 * Hood section names directly. See `User.trustScore`'s own doc comment for why this is
 * a plain 0-1000 score starting at 30, deliberately NOT a literal manner-temperature/
 * Celsius metaphor: Karrot's own real UK/Canada localization research (same doc)
 * found the temperature framing confusing and low scores insulting for non-Korean
 * users, replacing it with exactly this neutral global scale -- Rwanda is that same
 * kind of non-Korean market, so it gets the already-localized version directly rather
 * than the original domestic metaphor.
 *
 * Lives in `:core`, same "more than one feature module needs it" reasoning
 * `CreditScoreService`'s own doc comment already established (that service was moved
 * here from a standalone module for the exact same reason) -- `MarketplaceService`,
 * `JobPostService`, and `PropertyListingService` all recompute this at their own
 * mark-sold/mark-filled/mark-taken transition so a seller/poster/lister's cached
 * `User.trustScore` (what browse/nearby/my-listings endpoints batch-read for the card
 * badge -- see each controller's own `trustScores` map) reflects their real, current
 * completed-transaction count the moment it changes, not stale data from whenever they
 * last happened to view their own score. Also directly callable via
 * `GET /api/v1/trust-score` (see `:trustscore`'s own controller) so a user can see
 * their own factor breakdown, mirroring `CreditScoreController`'s shape exactly.
 *
 * Kept simple and honest for v1, matching this project's own "don't invent a complex
 * algorithm you can't justify" discipline: a real function of completed Hood
 * transactions, KYC verification, and account tenure. No review system exists yet to
 * also weight into this (see docs/TOSS_PARITY_MATRIX.md's Marketplace/Jobs/RealEstate
 * rows) -- a named v1 scope, not a hidden omission.
 */
@Service
class TrustScoreService(
    private val userRepository: UserRepository,
    private val listingRepository: ListingRepository,
    private val jobPostRepository: JobPostRepository,
    private val propertyListingRepository: PropertyListingRepository,
) {
    companion object {
        const val INITIAL_SCORE = 30
        const val MAX_SCORE = 1000
        private const val KYC_BONUS = 50
        private const val MAX_ACCOUNT_AGE_POINTS = 100
        private const val POINTS_PER_TRANSACTION = 15
    }

    @Transactional
    fun computeScore(userId: String): TrustScoreResult {
        val user = userRepository.findById(userId).orElseThrow { TrustScoreUserNotFoundException("User not found") }
        val factors = mutableListOf<TrustScoreFactor>()

        factors += TrustScoreFactor(
            "Base score", INITIAL_SCORE,
            "Starting point for every account, matching Karrot Score's own global baseline",
        )

        if (user.kycVerified) {
            factors += TrustScoreFactor("Identity verified", KYC_BONUS, "KYC submission reviewed and approved")
        }

        val accountAgeDays = ChronoUnit.DAYS.between(user.createdAt, Instant.now())
        val agePoints = min(MAX_ACCOUNT_AGE_POINTS, (accountAgeDays / 7).toInt())
        if (agePoints > 0) {
            factors += TrustScoreFactor("Account history", agePoints, "$accountAgeDays days since account creation")
        }

        // Real completed transactions across all three Hood surfaces -- a cheap COUNT
        // per repository, not a full listing load (see each repository's own
        // countBy...AndStatus doc comment).
        val completedListings = listingRepository.countBySellerIdAndStatus(userId, ListingStatus.SOLD)
        val completedJobs = jobPostRepository.countByPosterIdAndStatus(userId, JobPostStatus.FILLED)
        val completedProperties = propertyListingRepository.countByListerIdAndStatus(userId, PropertyListingStatus.TAKEN)
        val completedTransactions = completedListings + completedJobs + completedProperties
        if (completedTransactions > 0) {
            val transactionPoints = (completedTransactions * POINTS_PER_TRANSACTION).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            factors += TrustScoreFactor(
                "Completed Hood transactions", transactionPoints,
                "$completedTransactions marketplace/job/property transaction(s) marked sold/filled/taken",
            )
        }

        val score = min(MAX_SCORE, factors.sumOf { it.points })
        user.trustScore = score
        userRepository.save(user)

        return TrustScoreResult(score, factors, Instant.now())
    }
}
