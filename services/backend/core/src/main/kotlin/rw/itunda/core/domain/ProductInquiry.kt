package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A real Coupang-style pre-purchase product Q&A ("상품문의") -- genuinely distinct from
 * [ProductReview]: a review requires a real `DELIVERED` order (post-purchase, rating +
 * comment); an inquiry needs no order or purchase at all (pre-purchase, question +
 * answer), the real reason a shopper who hasn't bought anything yet still needs a way
 * to ask a real seller a real question before deciding. Sourced from Coupang's own real
 * published feature (마이쿠팡 > 고객센터 > 상품문의내역 -- the buyer's own answer-tracking
 * view; a product's detail page shows the public Q&A history).
 *
 * Public, like a review: any authenticated user can read a product's real Q&A history
 * (helps other shoppers, same as Coupang's own real product-page display), but only the
 * real merchant who owns the product can post an `answer`. `answer`/`answeredAt` are
 * nullable and editable (re-answering overwrites, same "no separate versioning"
 * simplicity `MerchantBookingReview`'s own owner-reply already established) -- an
 * unanswered inquiry is a real, honest "awaiting seller response" state, not hidden or
 * fabricated.
 */
@Entity
@Table(name = "product_inquiries")
class ProductInquiry(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "product_id", nullable = false, length = 64)
    val productId: String,

    @Column(name = "merchant_id", nullable = false, length = 64)
    val merchantId: String,

    @Column(name = "buyer_id", nullable = false, length = 64)
    val buyerId: String,

    @Column(nullable = false, length = 500)
    val question: String,

    @Column(length = 1000)
    var answer: String? = null,

    @Column(name = "answered_at")
    var answeredAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", productId = "", merchantId = "", buyerId = "", question = "")
}
