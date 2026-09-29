package rw.itunda.merchant

import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta

data class SubmitBookingReviewRequest(val rating: Int, val comment: String? = null)
data class ReplyToReviewRequest(val reply: String)

// Real post-appointment reviews + owner-side reply -- see MerchantBookingReview.kt's
// own doc comment. Not money-moving, no Idempotency-Key, same discipline
// MerchantBookingController already established for its own non-money-moving writes.
@RestController
@RequestMapping("/api/v1/merchant")
class MerchantBookingReviewController(
    private val merchantBookingReviewService: MerchantBookingReviewService,
) {
    @PostMapping("/bookings/{bookingId}/review")
    fun submitReview(
        @PathVariable bookingId: String,
        @RequestBody request: SubmitBookingReviewRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val review = merchantBookingReviewService.submitReview(currentUser.userId, bookingId, request.rating, request.comment)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "review" to review))
    }

    @GetMapping("/{merchantId}/reviews")
    fun getMerchantReviews(
        @PathVariable merchantId: String,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Map<String, Any?>> {
        val page = merchantBookingReviewService.getMerchantReviews(merchantId, pageable)
        val rating = merchantBookingReviewService.getMerchantRating(merchantId)
        return ResponseEntity.ok(mapOf("success" to true, "reviews" to page.content, "rating" to rating) + pageMeta(page))
    }

    @GetMapping("/reviews/my-reviews")
    fun getMyReviews(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = merchantBookingReviewService.getMyReviews(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "reviews" to page.content) + pageMeta(page))
    }

    @PostMapping("/reviews/{reviewId}/reply")
    fun replyToReview(
        @PathVariable reviewId: String,
        @RequestBody request: ReplyToReviewRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val review = merchantBookingReviewService.replyToReview(currentUser.userId, reviewId, request.reply)
        return ResponseEntity.ok(mapOf("success" to true, "review" to review))
    }

    @ExceptionHandler(MerchantNotFoundException::class)
    fun handleMerchantNotFound(ex: MerchantNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MERCHANT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidBookingRatingException::class)
    fun handleInvalidRating(ex: InvalidBookingRatingException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_RATING", ex.message ?: "Bad request"))

    @ExceptionHandler(BookingNotCompletedException::class)
    fun handleNotCompleted(ex: BookingNotCompletedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("BOOKING_NOT_COMPLETED", ex.message ?: "Conflict"))

    @ExceptionHandler(BookingAlreadyReviewedException::class)
    fun handleAlreadyReviewed(ex: BookingAlreadyReviewedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("BOOKING_ALREADY_REVIEWED", ex.message ?: "Conflict"))

    @ExceptionHandler(MerchantBookingReviewNotFoundException::class)
    fun handleReviewNotFound(ex: MerchantBookingReviewNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("REVIEW_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidReviewReplyException::class)
    fun handleInvalidReply(ex: InvalidReviewReplyException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_REVIEW_REPLY", ex.message ?: "Bad request"))
}
