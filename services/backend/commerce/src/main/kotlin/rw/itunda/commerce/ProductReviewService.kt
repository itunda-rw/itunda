package rw.itunda.commerce

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.OrderStatus
import rw.itunda.core.domain.ProductReview
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.OrderItemRepository
import rw.itunda.core.repository.OrderRepository
import rw.itunda.core.repository.ProductReviewRepository
import rw.itunda.core.repository.RatingSummaryProjection
import java.time.Instant
import java.util.UUID

class OrderItemNotFoundException(message: String) : RuntimeException(message)
class ProductNotYetDeliveredException(message: String) : RuntimeException(message)
class ProductAlreadyReviewedException(message: String) : RuntimeException(message)
class InvalidProductRatingException(message: String) : RuntimeException(message)
class ProductReviewNotFoundException(message: String) : RuntimeException(message)
class InvalidProductReviewReplyException(message: String) : RuntimeException(message)

data class ProductRatingSummary(val average: Double?, val count: Long)

/**
 * Real post-delivery product reviews -- the Commerce row's real remaining
 * Coupang/Naver Shopping-defining gap, mirroring [rw.itunda.eats.EatsReviewService]'s
 * already-proven shape (built at the user's direct request that every product this
 * platform builds reach the same "fully like Coupang/Toss/Naver" depth). See
 * `ProductReview.kt`'s own doc comment for the full entity account.
 *
 * Real ownership + state checks: only the real buyer of an order that's actually
 * `DELIVERED` can review one of its line items, and only once per line item -- a real
 * DB unique constraint on `order_item_id` backs the same application-level check.
 *
 * `replyToProductReview` (2026-07-26) closes the last leg of the real, well-known
 * Coupang/Naver Smart Store seller-reply gap -- see `ProductReview.kt`'s own doc
 * comment. Identical shape to `EatsReviewService.replyToRestaurantReview`.
 */
@Service
class ProductReviewService(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val productReviewRepository: ProductReviewRepository,
    private val merchantRepository: MerchantRepository,
    private val notificationRepository: NotificationRepository,
) {
    @Transactional
    fun submitReview(buyerId: String, orderItemId: String, rating: Int, comment: String?): ProductReview {
        if (rating !in 1..5) {
            throw InvalidProductRatingException("Rating must be between 1 and 5")
        }
        val orderItem = orderItemRepository.findById(orderItemId)
            .orElseThrow { OrderItemNotFoundException("Order item not found") }
        val order = orderRepository.findById(orderItem.orderId)
            .orElseThrow { OrderItemNotFoundException("Order item not found") }
        if (order.buyerId != buyerId) {
            // Real 404 (not 403) -- same IDOR discipline every other resource-ownership
            // check in this codebase uses, EatsReviewService's own check included.
            throw OrderItemNotFoundException("Order item not found")
        }
        if (order.status != OrderStatus.DELIVERED) {
            throw ProductNotYetDeliveredException("Only a delivered order's items can be reviewed")
        }
        if (productReviewRepository.findByOrderItemId(orderItemId) != null) {
            throw ProductAlreadyReviewedException("This item has already been reviewed")
        }

        return productReviewRepository.save(
            ProductReview(
                id = "product_review_${UUID.randomUUID()}",
                orderItemId = orderItemId,
                orderId = order.id,
                buyerId = buyerId,
                productId = orderItem.productId,
                merchantId = order.merchantId,
                rating = rating,
                // Real bound, matching GiftService.sendGift's own `note?.trim()?.take(200)`
                // convention -- the `comment` column is VARCHAR(1000) under this DB's real
                // STRICT_TRANS_TABLES mode, which throws a raw DataIntegrityViolationException
                // (an unhandled 500, not a clean 400) on an over-length insert rather than
                // silently truncating. Bounding client-side input here is the honest fix.
                comment = comment?.trim()?.take(1000)?.ifBlank { null },
            ),
        )
    }

    fun getProductReviews(productId: String, pageable: Pageable): Page<ProductReview> =
        productReviewRepository.findByProductIdOrderByCreatedAtDesc(productId, pageable)

    fun getProductRating(productId: String): ProductRatingSummary {
        val summary: RatingSummaryProjection = productReviewRepository.getProductRatingSummary(productId)
        return ProductRatingSummary(summary.average, summary.count)
    }

    // Real owner-side reply -- see this class's own doc comment. Editable: re-posting
    // overwrites the same reply + timestamp, matching EatsReviewService/
    // MerchantBookingReviewService's own exact simplicity.
    @Transactional
    fun replyToProductReview(ownerUserId: String, reviewId: String, reply: String): ProductReview {
        val trimmedReply = reply.trim()
        if (trimmedReply.isEmpty() || trimmedReply.length > 1000) {
            throw InvalidProductReviewReplyException("Reply must be 1-1000 characters")
        }
        val merchant = merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw MerchantNotFoundException("This account is not registered as a merchant")
        val review = productReviewRepository.findById(reviewId).orElseThrow { ProductReviewNotFoundException("Review not found") }
        if (review.merchantId != merchant.id) {
            throw ProductReviewNotFoundException("Review not found")
        }
        review.ownerReply = trimmedReply
        review.ownerRepliedAt = Instant.now()
        val saved = productReviewRepository.save(review)
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = review.buyerId, type = "PRODUCT_REVIEW_REPLY",
                title = "${merchant.businessName} replied to your review", body = trimmedReply, isRead = false,
                createdAt = Instant.now(), dataJson = "{\"reviewId\":\"${review.id}\"}",
            ),
        )
        return saved
    }
}
