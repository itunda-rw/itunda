package rw.itunda.eats

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.EatsOrderStatus
import rw.itunda.core.domain.EatsReview
import rw.itunda.core.repository.EatsOrderRepository
import rw.itunda.core.repository.EatsReviewRepository
import java.util.UUID

class EatsOrderNotYetDeliveredException(message: String) : RuntimeException(message)
class EatsOrderAlreadyReviewedException(message: String) : RuntimeException(message)
class InvalidEatsRatingException(message: String) : RuntimeException(message)

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
 */
@Service
class EatsReviewService(
    private val eatsOrderRepository: EatsOrderRepository,
    private val eatsReviewRepository: EatsReviewRepository,
) {
    @Transactional
    fun submitReview(
        buyerId: String,
        orderId: String,
        restaurantRating: Int,
        restaurantComment: String?,
        riderRating: Int,
        riderComment: String?,
    ): EatsReview {
        if (restaurantRating !in 1..5 || riderRating !in 1..5) {
            throw InvalidEatsRatingException("Ratings must be between 1 and 5")
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
        // order.riderId is guaranteed non-null here -- DELIVERED is only reachable via
        // RIDER_ASSIGNED -> PICKED_UP -> DELIVERED, all of which require a real assigned
        // rider (see EatsOrderService's own forward-only status chain).
        val riderId = order.riderId ?: throw EatsOrderNotYetDeliveredException("Only a delivered order can be reviewed")

        return eatsReviewRepository.save(
            EatsReview(
                id = "eats_review_${UUID.randomUUID()}", orderId = orderId, buyerId = buyerId,
                restaurantId = order.restaurantId, riderId = riderId,
                restaurantRating = restaurantRating, restaurantComment = restaurantComment?.trim()?.ifBlank { null },
                riderRating = riderRating, riderComment = riderComment?.trim()?.ifBlank { null },
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
}
