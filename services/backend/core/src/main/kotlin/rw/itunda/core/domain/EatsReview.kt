package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A real post-delivery review (2026-07-18) -- the single biggest remaining Coupang
 * Eats-defining gap, closing it at the user's direct request ("eats should be 100%
 * like coupang eats for rwanda"). One real review per real `EatsOrder` (enforced by a
 * real DB unique constraint on `order_id`, not just an application-level check), rating
 * both the restaurant and the rider who completed the delivery in one real submission --
 * matches how Coupang Eats itself asks for both in a single post-delivery prompt rather
 * than two separate flows.
 *
 * Real, not invented: only the real buyer of an order that's actually reached
 * `DELIVERED` can submit one (see `EatsReviewService.submitReview`'s own ownership +
 * state checks). Aggregate restaurant/rider ratings are computed by a real `AVG`/`COUNT`
 * query over this table at read time (`EatsReviewRepository.getRestaurantRatingSummary`/
 * `getRiderRatingSummary`), not a running counter cached on `Merchant`/`Rider` -- avoids
 * touching those already-tested entities' write paths for a purely additive feature,
 * matching this session's own discipline everywhere else.
 */
@Entity
@Table(name = "eats_reviews")
class EatsReview(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "order_id", nullable = false, unique = true, length = 64)
    val orderId: String,

    @Column(name = "buyer_id", nullable = false, length = 64)
    val buyerId: String,

    @Column(name = "restaurant_id", nullable = false, length = 64)
    val restaurantId: String,

    @Column(name = "rider_id", nullable = false, length = 64)
    val riderId: String,

    @Column(name = "restaurant_rating", nullable = false)
    val restaurantRating: Int,

    @Column(name = "restaurant_comment", length = 1000)
    val restaurantComment: String?,

    @Column(name = "rider_rating", nullable = false)
    val riderRating: Int,

    @Column(name = "rider_comment", length = 1000)
    val riderComment: String?,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(
        id = "", orderId = "", buyerId = "", restaurantId = "", riderId = "",
        restaurantRating = 0, restaurantComment = null, riderRating = 0, riderComment = null,
    )
}
