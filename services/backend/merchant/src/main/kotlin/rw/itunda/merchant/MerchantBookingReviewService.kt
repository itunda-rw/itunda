package rw.itunda.merchant

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.MerchantBookingReview
import rw.itunda.core.domain.MerchantBookingStatus
import rw.itunda.core.domain.Notification
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.MerchantBookingRepository
import rw.itunda.core.repository.MerchantBookingReviewRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.RatingSummaryProjection
import java.time.Duration
import java.time.Instant
import java.util.UUID

class InvalidBookingRatingException(message: String) : RuntimeException(message)
class BookingNotCompletedException(message: String) : RuntimeException(message)
class BookingAlreadyReviewedException(message: String) : RuntimeException(message)
class MerchantBookingReviewNotFoundException(message: String) : RuntimeException(message)
class InvalidReviewReplyException(message: String) : RuntimeException(message)

data class MerchantRatingSummary(val average: Double?, val count: Long)

/**
 * Real post-appointment reviews + owner-side reply -- see `MerchantBookingReview.kt`'s
 * own doc comment for the full account. `replyToReview`'s real push (2026-07-27) reuses
 * the same `PushNotificationService` `MerchantBookingService`'s own "new booking
 * request" notify already proved live -- see that class's `notify` for the identical
 * best-effort discipline.
 */
@Service
class MerchantBookingReviewService(
    private val merchantRepository: MerchantRepository,
    private val merchantBookingRepository: MerchantBookingRepository,
    private val merchantBookingReviewRepository: MerchantBookingReviewRepository,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
    private val rateLimiter: RateLimiter,
) {
    @Transactional
    fun submitReview(customerId: String, bookingId: String, rating: Int, comment: String?): MerchantBookingReview {
        // Real gap found live (2026-09-14, sibling-asymmetry sweep): this class had no
        // RateLimiter at all -- EatsReviewService.submitReview's own doc comment
        // documents a real live incident fixing the identical gap ("shipped with zero
        // rate limiting despite this same class's own helpful-vote/report endpoints
        // already having one") for the exact same review-submission shape this class
        // never got.
        rateLimiter.checkLimit("merchant:booking-review:submit:$customerId", limit = 20, window = Duration.ofHours(1))
        if (rating !in 1..5) {
            throw InvalidBookingRatingException("Rating must be between 1 and 5")
        }
        val booking = merchantBookingRepository.findById(bookingId)
            .orElseThrow { MerchantBookingReviewNotFoundException("Booking not found") }
        if (booking.customerId != customerId) {
            // Real 404 (not 403) -- same IDOR discipline ProductReviewService's own
            // ownership check already establishes.
            throw MerchantBookingReviewNotFoundException("Booking not found")
        }
        if (booking.status != MerchantBookingStatus.COMPLETED) {
            throw BookingNotCompletedException("Only a completed booking can be reviewed")
        }
        if (merchantBookingReviewRepository.findByBookingId(bookingId) != null) {
            throw BookingAlreadyReviewedException("This booking has already been reviewed")
        }
        return merchantBookingReviewRepository.save(
            MerchantBookingReview(
                id = "merchant_booking_review_${UUID.randomUUID()}", bookingId = bookingId, merchantId = booking.merchantId,
                customerId = customerId, serviceName = booking.serviceName, rating = rating,
                comment = comment?.trim()?.take(1000)?.ifBlank { null },
            ),
        )
    }

    fun getMerchantReviews(merchantId: String, pageable: Pageable): Page<MerchantBookingReview> =
        merchantBookingReviewRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId, pageable)

    fun getMyReviews(customerId: String, pageable: Pageable): Page<MerchantBookingReview> =
        merchantBookingReviewRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable)

    fun getMerchantRating(merchantId: String): MerchantRatingSummary {
        val summary: RatingSummaryProjection = merchantBookingReviewRepository.getMerchantRatingSummary(merchantId)
        return MerchantRatingSummary(summary.average, summary.count)
    }

    // Real owner-side reply -- the genuinely new capability this feature adds over
    // ProductReview/EatsReview. Editable: re-posting overwrites the same reply row
    // rather than versioning, matching RoundUpSettings' own upsert simplicity.
    @Transactional
    fun replyToReview(ownerUserId: String, reviewId: String, reply: String): MerchantBookingReview {
        val trimmedReply = reply.trim()
        if (trimmedReply.isEmpty() || trimmedReply.length > 1000) {
            throw InvalidReviewReplyException("Reply must be 1-1000 characters")
        }
        val merchant = merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw MerchantNotFoundException("This account is not registered as a merchant")
        val review = merchantBookingReviewRepository.findById(reviewId)
            .orElseThrow { MerchantBookingReviewNotFoundException("Review not found") }
        if (review.merchantId != merchant.id) {
            throw MerchantBookingReviewNotFoundException("Review not found")
        }
        review.ownerReply = trimmedReply
        review.ownerRepliedAt = Instant.now()
        val saved = merchantBookingReviewRepository.save(review)
        val title = "${merchant.businessName} replied to your review"
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = review.customerId, type = "MERCHANT_BOOKING_REVIEW_REPLY",
                title = title, body = trimmedReply, isRead = false,
                createdAt = Instant.now(), dataJson = "{\"reviewId\":\"${review.id}\"}",
            ),
        )
        // Real push (2026-07-27), same best-effort discipline MerchantBookingService's
        // own notify already establishes -- never allowed to make posting a reply look
        // like it failed.
        pushNotificationService.sendToUser(review.customerId, title, trimmedReply, mapOf("reviewId" to review.id))
        return saved
    }
}
