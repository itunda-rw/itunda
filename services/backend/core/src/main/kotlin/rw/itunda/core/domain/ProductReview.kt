package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A real post-delivery product review (2026-07-20) -- the Commerce row's real
 * remaining Coupang/Naver Shopping-defining gap, mirroring [EatsReview]'s own already
 * -proven shape ("Eats should be 100% like Coupang Eats" closed this exact pattern for
 * restaurants/riders on 2026-07-18). One real review per real [OrderItem] (enforced by
 * a real DB unique constraint on `order_item_id`), not per [Order] -- a Commerce order
 * can carry several different products from the same merchant, and real Coupang reviews
 * are per-product, not per-checkout, so tying this to the line item is the correct unit.
 *
 * Real, not invented: only the real buyer of an order that's actually `DELIVERED` can
 * review one of its line items (see `ProductReviewService.submitReview`'s own ownership
 * + state checks). Aggregate product ratings are computed by a real `AVG`/`COUNT` query
 * over this table at read time, not a running counter cached on `MerchantProduct` --
 * same "don't touch already-tested write paths for a purely additive feature" discipline
 * `EatsReview` already established.
 *
 * `ownerReply`/`ownerRepliedAt` (2026-07-26) close the last leg of a real, well-known
 * Coupang/Naver Smart Store seller-reply capability -- `MerchantBookingReview`'s own doc
 * comment first named this gap for `ProductReview`/`EatsReview` both; `EatsReview` closed
 * its half the same day (see that class's own doc comment). Identical shape here: one
 * editable reply per review, only the real merchant who owns the reviewed product can
 * post it. See `ProductReviewService.replyToProductReview` for the full account.
 *
 * `helpfulCount` (migration V295, 2026-08-25) -- a real Coupang/Naver-style "도움돼요"
 * counter, mirroring `EatsReview.helpfulCount`'s own doc comment exactly (same real
 * (review, user) `ProductReviewHelpfulVote` DB-unique guard, same denormalized-counter
 * convention). Closes the direct Toss Shopping reference screenshot gap: "OO명에게 도움
 * 됐어요" + thumbs-up under each product review.
 */
@Entity
@Table(name = "product_reviews")
class ProductReview(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "order_item_id", nullable = false, unique = true, length = 64)
    val orderItemId: String,

    @Column(name = "order_id", nullable = false, length = 64)
    val orderId: String,

    @Column(name = "buyer_id", nullable = false, length = 64)
    val buyerId: String,

    @Column(name = "product_id", nullable = false, length = 64)
    val productId: String,

    @Column(name = "merchant_id", nullable = false, length = 64)
    val merchantId: String,

    @Column(nullable = false)
    val rating: Int,

    @Column(length = 1000)
    val comment: String?,

    @Column(name = "owner_reply", length = 1000)
    var ownerReply: String? = null,

    @Column(name = "owner_replied_at")
    var ownerRepliedAt: Instant? = null,

    @Column(name = "helpful_count", nullable = false)
    var helpfulCount: Long = 0,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(
        id = "", orderItemId = "", orderId = "", buyerId = "", productId = "", merchantId = "",
        rating = 0, comment = null,
    )
}
