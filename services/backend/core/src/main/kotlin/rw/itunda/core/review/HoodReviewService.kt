package rw.itunda.core.review

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.HoodTransactionReview
import rw.itunda.core.domain.HoodTransactionType
import rw.itunda.core.domain.JobPostStatus
import rw.itunda.core.domain.ListingStatus
import rw.itunda.core.domain.PropertyListingStatus
import rw.itunda.core.repository.HoodTransactionReviewRepository
import rw.itunda.core.repository.JobPostRepository
import rw.itunda.core.repository.ListingRepository
import rw.itunda.core.repository.PropertyListingRepository
import rw.itunda.core.trust.TrustScoreService
import java.util.UUID

class HoodReviewTransactionNotFoundException(message: String) : RuntimeException(message)
class HoodReviewTransactionNotCompletedException(message: String) : RuntimeException(message)
class HoodReviewNoCounterpartyException(message: String) : RuntimeException(message)
class HoodReviewNotPartyException(message: String) : RuntimeException(message)
class HoodReviewAlreadySubmittedException(message: String) : RuntimeException(message)

data class HoodReviewParties(val partyA: String, val partyB: String?)

/**
 * Real post-transaction review with Karrot's own asymmetric public/private visibility
 * -- see `HoodTransactionReview`'s own doc comment for the full account. Lives in
 * `:core`, same "more than one module needs it" reasoning `TrustScoreService`'s own doc
 * comment already established (this class directly reuses that service to bump a
 * good-reviewed user's score, and reads all three of Marketplace/Jobs/Property's
 * repositories the same way `TrustScoreSupport.trustScores` already does).
 *
 * `resolveParties` is the single piece of logic that makes this whole system non-
 * exploitable: a review can only be submitted by, and about, whichever two real user
 * ids are actually recorded on the completed transaction row itself (`sellerId`/
 * `buyerId`, `posterId`/`workerId`, or `listerId`/`counterpartyId`) -- never an
 * arbitrary user a caller names in the request body. If the transaction never recorded
 * a counterparty (buyer/worker/tenant identification was optional at mark-complete
 * time), there is structurally no one to review, and this throws rather than silently
 * accepting an unverifiable claim.
 */
@Service
class HoodReviewService(
    private val reviewRepository: HoodTransactionReviewRepository,
    private val listingRepository: ListingRepository,
    private val jobPostRepository: JobPostRepository,
    private val propertyListingRepository: PropertyListingRepository,
    private val trustScoreService: TrustScoreService,
) {
    companion object {
        // Real preset checklist (2026-07-24), not free text -- matches Karrot's own
        // real review UX named in docs/DESIGN_REFERENCES.md's Hood section.
        val GOOD_POINTS = listOf("RESPONSIVE", "AS_DESCRIBED", "ON_TIME", "FRIENDLY", "FAIR_PRICE")
        val UNCOMFORTABLE_POINTS = listOf("LATE", "NOT_AS_DESCRIBED", "UNRESPONSIVE", "RUDE", "PRICE_ISSUE")
    }

    private fun resolveParties(transactionType: HoodTransactionType, transactionId: String): HoodReviewParties = when (transactionType) {
        HoodTransactionType.LISTING -> {
            val listing = listingRepository.findById(transactionId).orElseThrow { HoodReviewTransactionNotFoundException("Listing not found") }
            if (listing.status != ListingStatus.SOLD) throw HoodReviewTransactionNotCompletedException("This listing hasn't been marked sold yet")
            HoodReviewParties(listing.sellerId, listing.buyerId)
        }
        HoodTransactionType.JOB_POST -> {
            val post = jobPostRepository.findById(transactionId).orElseThrow { HoodReviewTransactionNotFoundException("Job post not found") }
            if (post.status != JobPostStatus.FILLED) throw HoodReviewTransactionNotCompletedException("This job post hasn't been marked filled yet")
            HoodReviewParties(post.posterId, post.workerId)
        }
        HoodTransactionType.PROPERTY_LISTING -> {
            val listing = propertyListingRepository.findById(transactionId).orElseThrow { HoodReviewTransactionNotFoundException("Property listing not found") }
            if (listing.status != PropertyListingStatus.TAKEN) throw HoodReviewTransactionNotCompletedException("This property listing hasn't been marked taken yet")
            HoodReviewParties(listing.listerId, listing.counterpartyId)
        }
    }

    @Transactional
    fun submitReview(
        reviewerId: String,
        transactionType: HoodTransactionType,
        transactionId: String,
        goodPoints: List<String>,
        uncomfortablePoints: List<String>,
    ): HoodTransactionReview {
        val parties = resolveParties(transactionType, transactionId)
        val counterpartyId = parties.partyB ?: throw HoodReviewNoCounterpartyException(
            "No buyer/worker/tenant was recorded for this transaction, so it can't be reviewed",
        )
        val revieweeId = when (reviewerId) {
            parties.partyA -> counterpartyId
            counterpartyId -> parties.partyA
            else -> throw HoodReviewNotPartyException("You weren't a party to this transaction")
        }
        if (reviewRepository.existsByTransactionTypeAndTransactionIdAndReviewerId(transactionType, transactionId, reviewerId)) {
            throw HoodReviewAlreadySubmittedException("You already reviewed this transaction")
        }
        val validGood = goodPoints.filter { it in GOOD_POINTS }.distinct()
        val validUncomfortable = uncomfortablePoints.filter { it in UNCOMFORTABLE_POINTS }.distinct()

        val review = reviewRepository.save(
            HoodTransactionReview(
                id = "hood_review_${UUID.randomUUID()}",
                transactionType = transactionType,
                transactionId = transactionId,
                reviewerId = reviewerId,
                revieweeId = revieweeId,
                goodPoints = validGood.joinToString("|"),
                uncomfortablePoints = validUncomfortable.joinToString("|"),
            ),
        )
        // Real trust-score bump (2026-07-24) -- only a real, freshly-submitted good
        // review moves the needle; TrustScoreService.computeScore recomputes the whole
        // score fresh from every factor (including this one), same discipline it
        // already applies at mark-sold/filled/taken.
        if (validGood.isNotEmpty()) {
            trustScoreService.computeScore(revieweeId)
        }
        return review
    }

    // Real private view (2026-07-24) -- only the two real parties to a transaction can
    // ever see either review for it (including uncomfortablePoints, which never leaves
    // this pair). Returns both reviews if both parties have submitted one.
    fun getTransactionReviews(viewerId: String, transactionType: HoodTransactionType, transactionId: String): List<HoodTransactionReview> {
        val parties = resolveParties(transactionType, transactionId)
        if (viewerId != parties.partyA && viewerId != parties.partyB) {
            throw HoodReviewNotPartyException("You weren't a party to this transaction")
        }
        return reviewRepository.findByTransactionTypeAndTransactionId(transactionType, transactionId)
    }

    // Real public summary (2026-07-24) -- ONLY goodPoints ever surface here, matching
    // Karrot's own asymmetric-visibility design; uncomfortablePoints are structurally
    // excluded from this method's return shape, not just hidden by a UI convention.
    fun publicGoodPointCounts(userId: String): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        reviewRepository.findByRevieweeId(userId).forEach { review ->
            review.goodPointList().forEach { point -> counts[point] = (counts[point] ?: 0) + 1 }
        }
        return counts
    }
}
