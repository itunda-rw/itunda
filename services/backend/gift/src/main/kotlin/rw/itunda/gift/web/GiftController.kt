package rw.itunda.gift.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.gift.GiftAlreadyResolvedException
import rw.itunda.gift.GiftExpiredException
import rw.itunda.gift.GiftInvalidAmountException
import rw.itunda.gift.GiftNoWalletException
import rw.itunda.gift.GiftNotFoundException
import rw.itunda.gift.GiftNotRecipientException
import rw.itunda.gift.GiftRecipientNotFoundException
import rw.itunda.gift.GiftSelfException
import rw.itunda.gift.GiftService
import java.math.BigDecimal

data class SendGiftRequest(val recipientPhoneNumber: String, val amount: BigDecimal, val note: String? = null)
data class SendGiftInConversationRequest(val amount: BigDecimal, val note: String? = null)

// Real KakaoTalk-style "선물하기" money gift -- see GiftService's own doc comment.
@RestController
@RequestMapping("/api/v1/gifts")
class GiftController(private val giftService: GiftService, private val idempotencyService: IdempotencyService) {

    @PostMapping
    fun sendGift(
        @RequestBody request: SendGiftRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/gifts", idempotencyKey, request) {
            val gift = giftService.sendGift(currentUser.userId, request.recipientPhoneNumber, request.amount, request.note)
            201 to mapOf("success" to true, "gift" to gift)
        }
        return ResponseEntity.status(status).body(body)
    }

    // Real chat-embedded gift -- the natural entry point (a "🎁" button inside an
    // already-open conversation, recipient resolved as "whichever participant isn't
    // me" rather than making the sender re-type a phone number they already know).
    @PostMapping("/conversations/{conversationId}")
    fun sendGiftInConversation(
        @PathVariable conversationId: String,
        @RequestBody request: SendGiftInConversationRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/gifts/conversations/$conversationId", idempotencyKey, request) {
            val gift = giftService.sendGiftInConversation(currentUser.userId, conversationId, request.amount, request.note)
            201 to mapOf("success" to true, "gift" to gift)
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/{id}")
    fun getGift(@PathVariable id: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "gift" to giftService.getGift(currentUser.userId, id)))

    @GetMapping("/conversations/{conversationId}")
    fun getGiftsForConversation(
        @PathVariable conversationId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "gifts" to giftService.getGiftsForConversation(currentUser.userId, conversationId)))

    @PostMapping("/{id}/claim")
    fun claimGift(
        @PathVariable id: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/gifts/$id/claim", idempotencyKey, id) {
            val gift = giftService.claimGift(currentUser.userId, id)
            200 to mapOf("success" to true, "gift" to gift)
        }
        return ResponseEntity.status(status).body(body)
    }

    @ExceptionHandler(GiftNotFoundException::class)
    fun handleNotFound(ex: GiftNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("GIFT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(GiftAlreadyResolvedException::class)
    fun handleAlreadyResolved(ex: GiftAlreadyResolvedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("GIFT_ALREADY_RESOLVED", ex.message ?: "Conflict"))

    @ExceptionHandler(GiftExpiredException::class)
    fun handleExpired(ex: GiftExpiredException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("GIFT_EXPIRED", ex.message ?: "Conflict"))

    @ExceptionHandler(GiftNotRecipientException::class)
    fun handleNotRecipient(ex: GiftNotRecipientException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("NOT_GIFT_RECIPIENT", ex.message ?: "Forbidden"))

    @ExceptionHandler(GiftSelfException::class)
    fun handleSelf(ex: GiftSelfException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("SELF_GIFT_NOT_ALLOWED", ex.message ?: "Bad request"))

    @ExceptionHandler(GiftNoWalletException::class)
    fun handleNoWallet(ex: GiftNoWalletException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(GiftRecipientNotFoundException::class)
    fun handleRecipientNotFound(ex: GiftRecipientNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("GIFT_RECIPIENT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(GiftInvalidAmountException::class)
    fun handleInvalidAmount(ex: GiftInvalidAmountException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_AMOUNT", ex.message ?: "Bad request"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleConflict(ex: IdempotencyConflictException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleInProgress(ex: IdempotencyInProgressException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))
}
