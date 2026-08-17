package rw.itunda.eats

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.EatsOrderStatus
import rw.itunda.core.domain.EatsReview
import rw.itunda.core.domain.EatsReviewHelpfulVote
import rw.itunda.core.domain.EatsReviewReport
import rw.itunda.core.domain.EatsReviewReportReason
import rw.itunda.core.domain.Notification
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.EatsOrderRepository
import rw.itunda.core.repository.EatsReviewHelpfulVoteRepository
import rw.itunda.core.repository.EatsReviewReportRepository
import rw.itunda.core.repository.EatsReviewRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
import java.time.Duration
import java.time.Instant
import java.util.UUID

class EatsOrderNotYetDeliveredException(message: String) : RuntimeException(message)
class EatsOrderAlreadyReviewedException(message: String) : RuntimeException(message)
class InvalidEatsRatingException(message: String) : RuntimeException(message)
class EatsReviewNotFoundException(message: String) : RuntimeException(message)
class InvalidEatsReviewReplyException(message: String) : RuntimeException(message)
class OwnEatsReviewReportException(message: String) : RuntimeException(message)
class EatsReviewAlreadyReportedException(message: String) : RuntimeException(message)

data class RatingSummary(val average: Double?, val count: Long)

/**
 * Real post-delivery ratings & reviews -- the single biggest remaining Coupang
 * Eats-defining gap, built at the user's direct request ("eats should be 100% like
 * coupang eats for rwanda"). See `EatsReview.kt`'s own doc comment for the full entity
 * account, including why aggregate ratings are computed at read time rather than a
 * cached counter on `Merchant`/`Rider`.
 *
 * Real ownership + state checks: only the real buyer of an order that's actually
 * `DELIVERED` can review it (matching real Coupang Eats, which only ever prompts for a
 * review after real delivery, not before), and only once per order -- a real DB unique
 * constraint on `order_id` backs the same application-level check, not just the
 * application check alone (a genuine race between two concurrent submit attempts for
 * the same order still can't create two rows).
 *
 * `replyToRestaurantReview` (2026-07-26) closes a gap `MerchantBookingReview`'s own doc
 * comment explicitly named ("the genuinely new part `ProductReview`/`EatsReview` don't
 * have") when owner-side review replies first shipped for bookings -- see
 * `EatsReview.kt`'s own doc comment for the full account. Identical shape to
 * `MerchantBookingReviewService.replyToReview`: one editable reply per review, only the
 * restaurant's own real owner can post it, and the reviewing buyer gets a real
 * notification.
 */
@Service
class EatsReviewService(
    private val eatsOrderRepository: EatsOrderRepository,
    private val eatsReviewRepository: EatsReviewRepository,
    private val merchantRepository: MerchantRepository,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
    private val eatsReviewHelpfulVoteRepository: EatsReviewHelpfulVoteRepository,
    private val rateLimiter: RateLimiter,
    private val eatsReviewReportRepository: EatsReviewReportRepository,
) {
    companion object {
        // Same real threshold + reasoning MarketplaceService.REPORT_THRESHOLD (Section
        // 140) already established: itunda has no real moderation team to review each
        // report individually the way Baemin's own does, so this is the honest
        // crowd-threshold approximation -- enough distinct reporters that one bad-faith
        // report can never silently hide a legitimate review, but low enough that a real
        // problem review doesn't stay visible for long.
        private const val REPORT_THRESHOLD = 3
    }

    @Transactional
    fun submitReview(
        buyerId: String,
        orderId: String,
        restaurantRating: Int,
        restaurantComment: String?,
        riderRating: Int?,
        riderComment: String?,
        photoUrl: String? = null,
    ): EatsReview {
        if (restaurantRating !in 1..5) {
            throw InvalidEatsRatingException("Restaurant rating must be between 1 and 5")
        }
        val order = eatsOrderRepository.findById(orderId).orElseThrow { EatsOrderNotFoundException("Order not found") }
        if (order.buyerId != buyerId) {
            // Real 404 (not 403) -- same "don't reveal a resource exists to someone who
            // shouldn't see it" discipline every other IDOR check in this backend uses.
            throw EatsOrderNotFoundException("Order not found")
        }
        if (order.status != EatsOrderStatus.DELIVERED) {
            throw EatsOrderNotYetDeliveredException("Only a delivered order can be reviewed")
        }
        if (eatsReviewRepository.findByOrderId(orderId) != null) {
            throw EatsOrderAlreadyReviewedException("This order has already been reviewed")
        }
        // Real bug fix (2026-07-26): order.riderId is NOT guaranteed non-null here -- a
        // real Baemin-style PICKUP order reaches DELIVERED via EatsOrderService
        // .completePickup with no rider ever assigned (see EatsReview.kt's own doc
        // comment for the full account of the live-caught regression this fixes). A
        // PICKUP order simply has no rider to rate; riderRating is only validated/kept
        // when a real rider actually exists on the order.
        val resolvedRiderRating = if (order.riderId != null) {
            if (riderRating == null || riderRating !in 1..5) {
                throw InvalidEatsRatingException("Rider rating must be between 1 and 5")
            }
            riderRating
        } else {
            null
        }
        val resolvedRiderComment = if (order.riderId != null) riderComment else null

        return eatsReviewRepository.save(
            EatsReview(
                id = "eats_review_${UUID.randomUUID()}", orderId = orderId, buyerId = buyerId,
                restaurantId = order.restaurantId, riderId = order.riderId,
                // Real bound, matching GiftService.sendGift's `note?.trim()?.take(200)` and
                // ProductReviewService.submitReview's own identical fix (2026-07-20) --
                // both comment columns are VARCHAR(1000) under this DB's real
                // STRICT_TRANS_TABLES mode, which throws a raw
                // DataIntegrityViolationException (an unhandled 500, not a clean 400) on an
                // over-length insert rather than silently truncating.
                restaurantRating = restaurantRating, restaurantComment = restaurantComment?.trim()?.take(1000)?.ifBlank { null },
                riderRating = resolvedRiderRating, riderComment = resolvedRiderComment?.trim()?.take(1000)?.ifBlank { null },
                // Real bound (500, matching Merchant.photoUrl/webhookUrl's own identical
                // fix) -- same STRICT_TRANS_TABLES over-length-insert gotcha as the
                // comment fields above.
                photoUrl = photoUrl?.trim()?.take(500)?.ifBlank { null },
            ),
        )
    }

    fun getRestaurantReviews(restaurantId: String, pageable: Pageable): Page<EatsReview> =
        eatsReviewRepository.findByRestaurantIdAndHiddenFalseOrderByCreatedAtDesc(restaurantId, pageable)

    fun getRestaurantRating(restaurantId: String): RatingSummary {
        val summary = eatsReviewRepository.getRestaurantRatingSummary(restaurantId)
        return RatingSummary(summary.average, summary.count)
    }

    fun getRiderRating(riderId: String): RatingSummary {
        val summary = eatsReviewRepository.getRiderRatingSummary(riderId)
        return RatingSummary(summary.average, summary.count)
    }

    // Real owner-side reply -- see this class's own doc comment for the full account.
    // Editable: re-posting overwrites the same reply + timestamp, matching
    // MerchantBookingReviewService.replyToReview's exact simplicity.
    @Transactional
    fun replyToRestaurantReview(ownerUserId: String, reviewId: String, reply: String): EatsReview {
        val trimmedReply = reply.trim()
        if (trimmedReply.isEmpty() || trimmedReply.length > 1000) {
            throw InvalidEatsReviewReplyException("Reply must be 1-1000 characters")
        }
        val restaurant = merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw RestaurantNotFoundException("This account is not registered as a merchant")
        val review = eatsReviewRepository.findById(reviewId).orElseThrow { EatsReviewNotFoundException("Review not found") }
        if (review.restaurantId != restaurant.id) {
            throw EatsReviewNotFoundException("Review not found")
        }
        review.ownerReply = trimmedReply
        review.ownerRepliedAt = Instant.now()
        val saved = eatsReviewRepository.save(review)
        val title = "${restaurant.businessName} replied to your review"
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = review.buyerId, type = "EATS_REVIEW_REPLY",
                title = title, body = trimmedReply, isRead = false,
                createdAt = Instant.now(), dataJson = "{\"reviewId\":\"${review.id}\"}",
            ),
        )
        // Real push (item 123) -- same "seller replied" urgency as
        // MerchantBookingReviewService's owner-reply notify.
        pushNotificationService.sendToUser(review.buyerId, title, trimmedReply)
        return saved
    }

    // Real batch "which of these reviews has this viewer already marked helpful"
    // (2026-08-17) -- see EatsReviewHelpfulVoteRepository.findVotedReviewIds' own doc
    // comment. Attached client-side to a page of reviews the same way
    // MarketplaceService.likedListingIds already is for listings.
    fun helpfulVotedReviewIds(reviews: Collection<EatsReview>, userId: String?): Set<String> {
        if (userId == null || reviews.isEmpty()) return emptySet()
        return eatsReviewHelpfulVoteRepository.findVotedReviewIds(reviews.map { it.id }, userId).toSet()
    }

    // Real Baemin/Coupang-style "도움돼요" (helpful) idempotent toggle -- same shape
    // MarketplaceService.toggleLike already establishes (real cached counter, DB-unique
    // constraint as the real concurrency guard, real rate limit from day one). No
    // self-vote check, matching that same precedent -- toggleLike doesn't block a
    // seller liking their own listing either.
    @Transactional
    fun toggleHelpful(userId: String, reviewId: String): Boolean {
        val review = eatsReviewRepository.findById(reviewId).orElseThrow { EatsReviewNotFoundException("Review not found") }
        rateLimiter.checkLimit("eats:review:helpful:$userId", limit = 60, window = Duration.ofMinutes(1))

        val existing = eatsReviewHelpfulVoteRepository.findByReviewIdAndUserId(reviewId, userId)
        return if (existing != null) {
            eatsReviewHelpfulVoteRepository.delete(existing)
            review.helpfulCount = (review.helpfulCount - 1).coerceAtLeast(0)
            eatsReviewRepository.save(review)
            false
        } else {
            eatsReviewHelpfulVoteRepository.save(EatsReviewHelpfulVote(id = "eats_review_helpful_${UUID.randomUUID()}", reviewId = reviewId, userId = userId))
            review.helpfulCount += 1
            eatsReviewRepository.save(review)
            true
        }
    }

    // Real 배달의민족 리뷰 신고하기 (report a review) -- see EatsReviewReport.kt's own
    // doc comment for the real sourcing. One real report per (review, reporter), same
    // DB-unique concurrency guard toggleHelpful's own EatsReviewHelpfulVote already
    // establishes. Once REPORT_THRESHOLD distinct reporters accumulate, the review is
    // silently hidden (EatsReview.hidden -> true, excluded from getRestaurantReviews and
    // both rating summaries from that point on) -- no notification to anyone, matching
    // the same real "no friendlier flow than the sourced product has" discipline
    // MarketplaceService.reportListing (Section 140) already established.
    @Transactional
    fun reportReview(reporterId: String, reviewId: String, reason: EatsReviewReportReason, details: String?): EatsReviewReport {
        val review = eatsReviewRepository.findById(reviewId).orElseThrow { EatsReviewNotFoundException("Review not found") }
        if (review.buyerId == reporterId) {
            throw OwnEatsReviewReportException("You can't report your own review")
        }
        if (eatsReviewReportRepository.findByReviewIdAndReporterId(reviewId, reporterId) != null) {
            throw EatsReviewAlreadyReportedException("You've already reported this review")
        }
        rateLimiter.checkLimit("eats:review:report:$reporterId", limit = 20, window = Duration.ofMinutes(1))

        val saved = eatsReviewReportRepository.save(
            EatsReviewReport(
                id = "eats_review_report_${UUID.randomUUID()}", reviewId = reviewId, reporterId = reporterId,
                reason = reason, details = details?.trim()?.take(500)?.ifBlank { null },
            ),
        )

        if (!review.hidden && eatsReviewReportRepository.countByReviewId(reviewId) >= REPORT_THRESHOLD) {
            review.hidden = true
            eatsReviewRepository.save(review)
        }

        return saved
    }
}
