package rw.itunda.merchant

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.WalletFrozenException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException

data class RegisterMerchantRequest(val businessName: String)
data class GenerateQrRequest(val amount: BigDecimal, val description: String)
data class SetWebhookUrlRequest(val webhookUrl: String)
data class SetLocationRequest(val latitude: Double, val longitude: Double)
data class SetCategoryRequest(val category: String)
data class SetCashbackRateRequest(val rate: BigDecimal?)
data class SetParticipatesInEatsMembershipRequest(val participates: Boolean)
data class SetAcceptsScheduledOrdersRequest(val accepts: Boolean)
data class SetPhotoUrlRequest(val photoUrl: String)
data class SetMinOrderAmountRequest(val minOrderAmount: BigDecimal?)
data class CollectPaymentRequest(val couponId: String? = null)
data class ChargeCardRequest(
    val amount: BigDecimal,
    val description: String,
    val cardNumber: String,
    val expiryMonth: Int,
    val expiryYear: Int,
    val cvc: String,
)

@RestController
@RequestMapping("/api/v1/merchant")
class MerchantController(
    private val merchantService: MerchantService,
    private val idempotencyService: IdempotencyService,
) {
    @PostMapping("/register")
    fun register(
        @RequestBody request: RegisterMerchantRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val merchant = merchantService.register(currentUser.userId, request.businessName)
        return ResponseEntity.ok(mapOf("success" to true, "merchant" to merchant))
    }

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "merchant" to merchantService.getMyMerchant(currentUser.userId)))

    // Real "Pay with itunda" external checkout API key (2026-07-21) -- see
    // MerchantService.generateApiKey's own doc comment. The raw key is returned exactly
    // once, here, and never again -- only its hash is ever stored, same convention as
    // PartnerController.register.
    @PostMapping("/api-key/generate")
    fun generateApiKey(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "apiKey" to merchantService.generateApiKey(currentUser.userId)))

    @PostMapping("/qr/generate")
    fun generateQr(
        @RequestBody request: GenerateQrRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val intent = merchantService.generateQr(currentUser.userId, request.amount, request.description)
        return ResponseEntity.ok(mapOf("success" to true, "paymentIntent" to intent))
    }

    // Real webhook registration (2026-07-13) -- see docs/TOSS_PARITY_MATRIX.md's Merchant
    // row and WebhookDeliveryService for the real delivery mechanism this feeds.
    @PostMapping("/webhook-url")
    fun setWebhookUrl(
        @RequestBody request: SetWebhookUrlRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val merchant = merchantService.setWebhookUrl(currentUser.userId, request.webhookUrl)
        return ResponseEntity.ok(mapOf("success" to true, "merchant" to merchant))
    }

    // Real location (2026-07-18) -- see MerchantService.setLocation's own doc comment.
    @PostMapping("/location")
    fun setLocation(
        @RequestBody request: SetLocationRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val merchant = merchantService.setLocation(currentUser.userId, request.latitude, request.longitude)
        return ResponseEntity.ok(mapOf("success" to true, "merchant" to merchant))
    }

    // Real category/cuisine (2026-07-19) -- see MerchantService.setCategory's own doc
    // comment.
    @PostMapping("/category")
    fun setCategory(
        @RequestBody request: SetCategoryRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val merchant = merchantService.setCategory(currentUser.userId, request.category)
        return ResponseEntity.ok(mapOf("success" to true, "merchant" to merchant))
    }

    // Real Naver Pay-style boosted cashback opt-in (2026-07-26) -- see
    // ShoppingCashbackService's own doc comment.
    @PostMapping("/cashback-rate")
    fun setCashbackRate(
        @RequestBody request: SetCashbackRateRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val merchant = merchantService.setCashbackRate(currentUser.userId, request.rate)
        return ResponseEntity.ok(mapOf("success" to true, "merchant" to merchant))
    }

    // Real Baemin Club-style participating-restaurant opt-in (2026-07-26) -- see
    // EatsMembership.kt's own doc comment.
    @PostMapping("/eats-membership-participation")
    fun setParticipatesInEatsMembership(
        @RequestBody request: SetParticipatesInEatsMembershipRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val merchant = merchantService.setParticipatesInEatsMembership(currentUser.userId, request.participates)
        return ResponseEntity.ok(mapOf("success" to true, "merchant" to merchant))
    }

    // Real 배달의민족 예약주문 (scheduled ordering) opt-in (2026-07-26) -- see
    // Merchant.kt's own doc comment.
    @PostMapping("/scheduled-orders-participation")
    fun setAcceptsScheduledOrders(
        @RequestBody request: SetAcceptsScheduledOrdersRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val merchant = merchantService.setAcceptsScheduledOrders(currentUser.userId, request.accepts)
        return ResponseEntity.ok(mapOf("success" to true, "merchant" to merchant))
    }

    // Real restaurant-card photo (2026-07-21) -- see MerchantService.setPhotoUrl's own
    // doc comment.
    @PostMapping("/photo")
    fun setPhotoUrl(
        @RequestBody request: SetPhotoUrlRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val merchant = merchantService.setPhotoUrl(currentUser.userId, request.photoUrl)
        return ResponseEntity.ok(mapOf("success" to true, "merchant" to merchant))
    }

    // Real merchant-set minimum order amount (2026-07-21) -- see
    // MerchantService.setMinOrderAmount's own doc comment.
    @PostMapping("/min-order")
    fun setMinOrderAmount(
        @RequestBody request: SetMinOrderAmountRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val merchant = merchantService.setMinOrderAmount(currentUser.userId, request.minOrderAmount)
        return ResponseEntity.ok(mapOf("success" to true, "merchant" to merchant))
    }

    @PostMapping("/collect/{intentId}")
    fun collect(
        @PathVariable intentId: String,
        @RequestBody(required = false) request: CollectPaymentRequest?,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val couponId = request?.couponId
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/merchant/collect/$intentId", idempotencyKey, mapOf("intentId" to intentId, "couponId" to couponId)) {
            200 to merchantService.collect(currentUser.userId, intentId, couponId = couponId)
        }
        return ResponseEntity.status(status).body(body)
    }

    // Real demo card-processing endpoint (2026-07-17) -- see
    // MerchantService.chargeCard's own doc comment. Idempotency-Key required, same
    // convention as every other money-moving endpoint in this backend.
    @PostMapping("/card/charge")
    fun chargeCard(
        @RequestBody request: ChargeCardRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/merchant/card/charge", idempotencyKey, request) {
            200 to merchantService.chargeCard(
                currentUser.userId, request.amount, request.description,
                request.cardNumber, request.expiryMonth, request.expiryYear, request.cvc,
            )
        }
        return ResponseEntity.status(status).body(body)
    }

    // Real merchant reports (2026-07-16) -- see MerchantService.getReport's doc comment.
    // Defaults to the last 7 days, same "sensible default when unspecified" convention
    // as SystemController.getReconciliation defaulting to today.
    @GetMapping("/reports")
    fun getReport(
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val toDate = to?.let { LocalDate.parse(it) } ?: LocalDate.now()
        val fromDate = from?.let { LocalDate.parse(it) } ?: toDate.minusDays(6)
        val report = merchantService.getReport(currentUser.userId, fromDate, toDate).map { day ->
            mapOf(
                "date" to day.date.toString(),
                "collectionCount" to day.collectionCount,
                "grossAmount" to day.grossAmount,
                "fees" to day.fees,
                "netAmount" to day.netAmount,
                "byChannel" to day.byChannel,
            )
        }
        return ResponseEntity.ok(mapOf("success" to true, "from" to fromDate.toString(), "to" to toDate.toString(), "days" to report))
    }

    @ExceptionHandler(DateTimeParseException::class)
    fun handleBadDate(ex: DateTimeParseException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_DATE_FORMAT", "from/to must be in YYYY-MM-DD format"))

    @ExceptionHandler(MerchantAlreadyRegisteredException::class)
    fun handleAlreadyRegistered(ex: MerchantAlreadyRegisteredException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("MERCHANT_ALREADY_REGISTERED", ex.message ?: "Conflict"))

    @ExceptionHandler(MerchantNotFoundException::class)
    fun handleNotFound(ex: MerchantNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MERCHANT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(MerchantNoWalletException::class)
    fun handleNoWallet(ex: MerchantNoWalletException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(PaymentIntentNotFoundException::class)
    fun handleIntentNotFound(ex: PaymentIntentNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("PAYMENT_CODE_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(PaymentIntentNotPayableException::class)
    fun handleIntentNotPayable(ex: PaymentIntentNotPayableException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("PAYMENT_CODE_NOT_PAYABLE", ex.message ?: "Conflict"))

    @ExceptionHandler(SelfPaymentException::class)
    fun handleSelfPayment(ex: SelfPaymentException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("SELF_PAYMENT_NOT_ALLOWED", ex.message ?: "Bad request"))

    @ExceptionHandler(CardDeclinedException::class)
    fun handleCardDeclined(ex: CardDeclinedException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("CARD_DECLINED", ex.message ?: "Card declined"))

    @ExceptionHandler(InvalidWebhookUrlException::class)
    fun handleInvalidWebhookUrl(ex: InvalidWebhookUrlException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_WEBHOOK_URL", ex.message ?: "Bad request"))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleConflict(ex: IdempotencyConflictException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleInProgress(ex: IdempotencyInProgressException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(WalletFrozenException::class)
    fun handleWalletFrozen(ex: WalletFrozenException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("WALLET_FROZEN", ex.message ?: "Wallet is frozen"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))

    @ExceptionHandler(InvalidCouponException::class)
    fun handleInvalidCoupon(ex: InvalidCouponException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_COUPON", ex.message ?: "Bad request"))

    @ExceptionHandler(CouponNotFoundException::class)
    fun handleCouponNotFound(ex: CouponNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("COUPON_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(CouponNotEligibleException::class)
    fun handleCouponNotEligible(ex: CouponNotEligibleException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("COUPON_NOT_ELIGIBLE", ex.message ?: "Forbidden"))

    @ExceptionHandler(CouponAlreadyRedeemedException::class)
    fun handleCouponAlreadyRedeemed(ex: CouponAlreadyRedeemedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("COUPON_ALREADY_REDEEMED", ex.message ?: "Conflict"))

    @ExceptionHandler(InvalidCoordinatesException::class)
    fun handleInvalidCoordinates(ex: InvalidCoordinatesException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_COORDINATES", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidCategoryException::class)
    fun handleInvalidCategory(ex: InvalidCategoryException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_CATEGORY", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidCashbackRateException::class)
    fun handleInvalidCashbackRate(ex: InvalidCashbackRateException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_CASHBACK_RATE", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidPhotoUrlException::class)
    fun handleInvalidPhotoUrl(ex: InvalidPhotoUrlException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_PHOTO_URL", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidMinOrderAmountException::class)
    fun handleInvalidMinOrderAmount(ex: InvalidMinOrderAmountException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_MIN_ORDER_AMOUNT", ex.message ?: "Bad request"))
}
