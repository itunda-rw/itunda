package rw.itunda.eats

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.EatsOrderStatus
import rw.itunda.core.domain.EatsReview
import rw.itunda.core.domain.Notification
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.EatsOrderRepository
import rw.itunda.core.repository.EatsReviewRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
import java.time.Instant
import java.util.UUID

class EatsOrderNotYetDeliveredException(message: String) : RuntimeException(message)
class EatsOrderAlreadyReviewedException(message: String) : RuntimeException(message)
class InvalidEatsRatingException(message: String) : RuntimeException(message)
class EatsReviewNotFoundException(message: String) : RuntimeException(message)
class InvalidEatsReviewReplyException(message: String) : RuntimeException(message)

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
) {
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
        eatsReviewRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId, pageable)

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
}
