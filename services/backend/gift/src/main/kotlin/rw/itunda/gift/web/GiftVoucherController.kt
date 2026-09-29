package rw.itunda.gift.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
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
import rw.itunda.gift.GiftVoucherAlreadyExtendedException
import rw.itunda.gift.GiftVoucherExpiredException
import rw.itunda.gift.GiftVoucherExpiryReminderScheduler
import rw.itunda.gift.GiftVoucherInvalidAmountException
import rw.itunda.gift.GiftVoucherMerchantNotFoundException
import rw.itunda.gift.GiftVoucherNoAccountException
import rw.itunda.gift.GiftVoucherNotActiveException
import rw.itunda.gift.GiftVoucherNotExtendableException
import rw.itunda.gift.GiftVoucherNotFoundException
import rw.itunda.gift.GiftVoucherProductNotFoundException
import rw.itunda.gift.GiftVoucherProductUnavailableException
import rw.itunda.gift.GiftVoucherRecipientNotFoundException
import rw.itunda.gift.GiftVoucherSelfException
import rw.itunda.gift.GiftVoucherService
import java.math.BigDecimal

data class PurchaseGiftVoucherRequest(
    val recipientPhoneNumber: String,
    val merchantId: String,
    val merchantProductId: String? = null,
    val amount: BigDecimal? = null,
)

// Real KakaoTalk-style 선물하기 기프티콘 (mobile gift voucher) -- see
// GiftVoucherService's own doc comment.
@RestController
@RequestMapping("/api/v1/gift-vouchers")
class GiftVoucherController(
    private val giftVoucherService: GiftVoucherService,
    private val idempotencyService: IdempotencyService,
    private val giftVoucherExpiryReminderScheduler: GiftVoucherExpiryReminderScheduler,
) {

    // Real KakaoTalk 기프티콘 사용기한 임박 알림 manual trigger -- same "expose the
    // scheduler's own real logic as a callable endpoint" convention
    // SavingsController/InsuranceController/CertificateController already establish, so
    // a real voucher's real expiresAt can be verified without waiting actual wall-clock
    // days for it to enter the reminder window.
    // Real gap found live (2026-08-31, market-readiness audit): this fires the
    // reminder job for EVERY user's expiring gift vouchers system-wide, yet had no
    // ADMIN gate -- any authenticated user could call it. ADMIN-gated the same
    // @PreAuthorize("hasRole('ADMIN')") way WeeklySavingsController.processDue already
    // is (this route doesn't live under /api/v1/system/**, so it doesn't inherit
    // SecurityConfig's blanket ADMIN gate there).
    @PostMapping("/process-expiry-reminders")
    @PreAuthorize("hasRole('ADMIN')")
    fun processExpiryReminders(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val processed = giftVoucherExpiryReminderScheduler.processDue()
        return ResponseEntity.ok(mapOf("success" to true, "processed" to processed))
    }

    @PostMapping
    fun purchaseVoucher(
        @RequestBody request: PurchaseGiftVoucherRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/gift-vouchers", idempotencyKey, request) {
            val voucher = giftVoucherService.purchaseVoucher(
                currentUser.userId, request.recipientPhoneNumber, request.merchantId, request.merchantProductId, request.amount,
            )
            201 to mapOf("success" to true, "voucher" to voucher)
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/{id}")
    fun getVoucher(@PathVariable id: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "voucher" to giftVoucherService.getVoucher(currentUser.userId, id)))

    @GetMapping("/conversations/{conversationId}")
    fun getVouchersForConversation(
        @PathVariable conversationId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "vouchers" to giftVoucherService.getVouchersForConversation(currentUser.userId, conversationId)))

    // Real gap found 2026-09-05 (feedback_idempotency_key_sweep re-audit) -- a lost
    // response after a successful extend would resubmit here and hit
    // GiftVoucherAlreadyExtendedException on the retry, a confusing conflict for an
    // extension that actually already succeeded. redeem below was already
    // protected; this was the outlier.
    @PostMapping("/{id}/extend")
    fun extendExpiry(
        @PathVariable id: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/gift-vouchers/$id/extend", idempotencyKey, id) {
            200 to mapOf("success" to true, "voucher" to giftVoucherService.extendExpiry(currentUser.userId, id))
        }
        return ResponseEntity.status(status).body(body)
    }

    // Real merchant-side redemption -- see GiftVoucherService.redeemVoucher's own doc
    // comment for why this is merchant-authenticated, not recipient self-serve.
    @PostMapping("/{id}/redeem")
    fun redeemVoucher(
        @PathVariable id: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/gift-vouchers/$id/redeem", idempotencyKey, id) {
            val voucher = giftVoucherService.redeemVoucher(currentUser.userId, id)
            200 to mapOf("success" to true, "voucher" to voucher)
        }
        return ResponseEntity.status(status).body(body)
    }

    @ExceptionHandler(GiftVoucherNotFoundException::class)
    fun handleNotFound(ex: GiftVoucherNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("GIFT_VOUCHER_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(GiftVoucherNotActiveException::class)
    fun handleNotActive(ex: GiftVoucherNotActiveException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("GIFT_VOUCHER_NOT_ACTIVE", ex.message ?: "Conflict"))

    @ExceptionHandler(GiftVoucherExpiredException::class)
    fun handleExpired(ex: GiftVoucherExpiredException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("GIFT_VOUCHER_EXPIRED", ex.message ?: "Conflict"))

    @ExceptionHandler(GiftVoucherSelfException::class)
    fun handleSelf(ex: GiftVoucherSelfException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("SELF_GIFT_NOT_ALLOWED", ex.message ?: "Bad request"))

    @ExceptionHandler(GiftVoucherNoAccountException::class)
    fun handleNoAccount(ex: GiftVoucherNoAccountException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(GiftVoucherRecipientNotFoundException::class)
    fun handleRecipientNotFound(ex: GiftVoucherRecipientNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("GIFT_VOUCHER_RECIPIENT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(GiftVoucherInvalidAmountException::class)
    fun handleInvalidAmount(ex: GiftVoucherInvalidAmountException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_AMOUNT", ex.message ?: "Bad request"))

    @ExceptionHandler(GiftVoucherMerchantNotFoundException::class)
    fun handleMerchantNotFound(ex: GiftVoucherMerchantNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MERCHANT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(GiftVoucherProductNotFoundException::class)
    fun handleProductNotFound(ex: GiftVoucherProductNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("PRODUCT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(GiftVoucherProductUnavailableException::class)
    fun handleProductUnavailable(ex: GiftVoucherProductUnavailableException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("PRODUCT_OUT_OF_STOCK", ex.message ?: "Conflict"))

    @ExceptionHandler(GiftVoucherNotExtendableException::class)
    fun handleNotExtendable(ex: GiftVoucherNotExtendableException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("GIFT_VOUCHER_NOT_EXTENDABLE", ex.message ?: "Unprocessable"))

    @ExceptionHandler(GiftVoucherAlreadyExtendedException::class)
    fun handleAlreadyExtended(ex: GiftVoucherAlreadyExtendedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("GIFT_VOUCHER_ALREADY_EXTENDED", ex.message ?: "Conflict"))

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
