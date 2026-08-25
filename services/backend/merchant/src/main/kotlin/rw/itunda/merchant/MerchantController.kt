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
import rw.itunda.core.ledger.AccountFrozenException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import java.time.LocalDate
import java.time.format.DateTimeParseException

@RestController
@RequestMapping("/api/v1/merchant")
class MerchantController(
    private val merchantService: MerchantService,
    private val idempotencyService: IdempotencyService,
    private val webhookDeliveryService: WebhookDeliveryService,
    private val merchantStaticQrService: MerchantStaticQrService,
    private val merchantFeeWaiverService: MerchantFeeWaiverService,
    private val merchantLoyaltyPointsService: MerchantLoyaltyPointsService,
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

    // Real Naver Pay 영세 가맹점 수수료 지원 (small-merchant fee-waiver support program) --
    // see MerchantFeeWaiverService's own doc comment.
    @PostMapping("/fee-waiver/apply")
    fun applyForFeeWaiver(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "merchant" to merchantFeeWaiverService.applyForFeeWaiver(currentUser.userId)))

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

    // Real Kakao Pay 정액 QR (static/fixed merchant QR) -- see MerchantStaticQrService's
    // own doc comment. Public merchantId lookup, not the authenticated payer's own
    // merchant -- any real active merchant can be paid this way, the same "any
    // registered merchant already accepts dynamic QR" openness this feature honestly
    // extends, not a new authorization surface (only the customer's own money moves).
    @PostMapping("/{merchantId}/static-qr/pay")
    fun payByStaticQr(
        @PathVariable merchantId: String,
        @RequestBody request: StaticQrPayRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/merchant/$merchantId/static-qr/pay", idempotencyKey, request) {
            val result = merchantStaticQrService.payByStaticQr(currentUser.userId, merchantId, request.amount, request.description)
            200 to (mapOf("success" to true) + result)
        }
        return ResponseEntity.status(status).body(body)
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

    @GetMapping("/webhook-deliveries")
    fun webhookDeliveries(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val merchant = merchantService.getMyMerchant(currentUser.userId)
        return ResponseEntity.ok(mapOf("success" to true, "deliveries" to webhookDeliveryService.deliveryHistory(merchant.id)))
    }

    @PostMapping("/webhook-deliveries/{deliveryId}/replay")
    fun replayWebhookDelivery(
        @PathVariable deliveryId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val merchant = merchantService.getMyMerchant(currentUser.userId)
        val webhookUrl = merchant.webhookUrl ?: return ResponseEntity.badRequest().body(mapOf("success" to false, "error" to "WEBHOOK_URL_NOT_CONFIGURED"))
        val replay = webhookDeliveryService.replayExhausted(merchant.id, deliveryId, webhookUrl)
            ?: return ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("success" to false, "error" to "WEBHOOK_DELIVERY_NOT_REPLAYABLE"))
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(mapOf("success" to true, "delivery" to replay))
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

    // Real Baemin CEO app 영업일시중지 (temporarily pause business) -- see
    // MerchantService.setAcceptingOrders's own doc comment.
    @PostMapping("/accepting-orders")
    fun setAcceptingOrders(
        @RequestBody request: SetAcceptingOrdersRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val merchant = merchantService.setAcceptingOrders(currentUser.userId, request.accepting)
        return ResponseEntity.ok(mapOf("success" to true, "merchant" to merchant))
    }

    // Real Baemin CEO app 휴무일 설정 (recurring weekly closed-day schedule) -- see
    // MerchantService.setClosedWeekdays's own doc comment.
    @PostMapping("/closed-weekdays")
    fun setClosedWeekdays(
        @RequestBody request: SetClosedWeekdaysRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val merchant = merchantService.setClosedWeekdays(currentUser.userId, request.weekdays)
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

    // Real merchant-set phone number + opening hours (2026-08-09) -- see
    // MerchantService.setPhoneNumber/setOpeningHours's own doc comments.
    @PostMapping("/phone")
    fun setPhoneNumber(
        @RequestBody request: SetPhoneNumberRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val merchant = merchantService.setPhoneNumber(currentUser.userId, request.phoneNumber)
        return ResponseEntity.ok(mapOf("success" to true, "merchant" to merchant))
    }

    @PostMapping("/hours")
    fun setOpeningHours(
        @RequestBody request: SetOpeningHoursRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val merchant = merchantService.setOpeningHours(currentUser.userId, request.openingHours)
        return ResponseEntity.ok(mapOf("success" to true, "merchant" to merchant))
    }

    // Real per-merchant kitchen-prep time (2026-08-16) -- see
    // MerchantService.setAvgPrepTimeMinutes's own doc comment.
    @PostMapping("/prep-time")
    fun setAvgPrepTimeMinutes(
        @RequestBody request: SetAvgPrepTimeMinutesRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val merchant = merchantService.setAvgPrepTimeMinutes(currentUser.userId, request.avgPrepTimeMinutes)
        return ResponseEntity.ok(mapOf("success" to true, "merchant" to merchant))
    }

    // Real Baemin 포장할인 (pickup discount) -- see MerchantService.setPickupDiscount's
    // own doc comment.
    @PostMapping("/pickup-discount")
    fun setPickupDiscount(
        @RequestBody request: SetPickupDiscountRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val merchant = merchantService.setPickupDiscount(currentUser.userId, request.pickupDiscountPercent)
        return ResponseEntity.ok(mapOf("success" to true, "merchant" to merchant))
    }

    // Real read-only preview (item 149) -- see MerchantService.previewIntent's own doc
    // comment. Lets a payer see the merchant/amount/their own real coupon eligibility
    // before committing to collect().
    @GetMapping("/intent/{intentId}")
    fun previewIntent(
        @PathVariable intentId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true) + merchantService.previewIntent(currentUser.userId, intentId))

    @PostMapping("/collect/{intentId}")
    fun collect(
        @PathVariable intentId: String,
        @RequestBody(required = false) request: CollectPaymentRequest?,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val couponId = request?.couponId
        val pointsToRedeem = request?.pointsToRedeem
        val (status, body) = idempotencyService.replayOrExecute(
            "POST /api/v1/merchant/collect/$intentId", idempotencyKey,
            mapOf("intentId" to intentId, "couponId" to couponId, "pointsToRedeem" to pointsToRedeem),
        ) {
            200 to merchantService.collect(currentUser.userId, intentId, couponId = couponId, pointsToRedeem = pointsToRedeem)
        }
        return ResponseEntity.status(status).body(body)
    }

    // Real Toss Place-style 자동 적립 balance check (2026-08-18) -- see
    // MerchantLoyaltyPointsService's own doc comment. Lets a real customer see their
    // real point balance at this specific store before choosing to redeem it at
    // checkout, same "know before you act" convention P2pService.resolveRecipient's
    // own preview endpoint already establishes for an unrelated flow.
    @GetMapping("/{merchantId}/loyalty-balance")
    fun getLoyaltyBalance(
        @PathVariable merchantId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "pointBalance" to merchantLoyaltyPointsService.getBalance(merchantId, currentUser.userId)))

    // Real customer-presented payment code (2026-08-11) -- see
    // MerchantService.generateCustomerPaymentCode's own doc comment. Called by the
    // PAYING customer from their own Pay tab -- any logged-in user, not merchant-role-
    // specific, same as previewIntent/collect above.
    @PostMapping("/pay/customer-code")
    fun generateCustomerPaymentCode(
        @RequestBody(required = false) request: GenerateCustomerPaymentCodeRequest?,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val paymentCode = merchantService.generateCustomerPaymentCode(currentUser.userId, request?.accountId)
        return ResponseEntity.ok(
            mapOf(
                "success" to true, "code" to paymentCode.code, "expiresAt" to paymentCode.expiresAt.toString(),
                "accountId" to paymentCode.accountId,
            ),
        )
    }

    // Real customer-presented payment charge (2026-08-11) -- see
    // MerchantService.chargeByCustomerCode's own doc comment. Called by the MERCHANT
    // after scanning the customer's code -- currentUser here is the merchant owner,
    // resolved to their real Merchant row inside the service.
    @PostMapping("/pay/charge-by-code")
    fun chargeByCustomerCode(
        @RequestBody request: ChargeByCustomerCodeRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/merchant/pay/charge-by-code", idempotencyKey, request) {
            200 to merchantService.chargeByCustomerCode(currentUser.userId, request.code, request.amount)
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

    // Real Coupang WING-style top-selling-products report -- see
    // MerchantService.getTopSellingProducts's own doc comment.
    @GetMapping("/reports/top-products")
    fun getTopSellingProducts(
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) limit: Int?,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val toDate = to?.let { LocalDate.parse(it) } ?: LocalDate.now()
        val fromDate = from?.let { LocalDate.parse(it) } ?: toDate.minusDays(6)
        val products = merchantService.getTopSellingProducts(currentUser.userId, fromDate, toDate, limit ?: 10)
        return ResponseEntity.ok(mapOf("success" to true, "from" to fromDate.toString(), "to" to toDate.toString(), "products" to products))
    }

    @ExceptionHandler(DateTimeParseException::class)
    fun handleBadDate(ex: DateTimeParseException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_DATE_FORMAT", "from/to must be in YYYY-MM-DD format"))

    @ExceptionHandler(InvalidReportRangeException::class)
    fun handleInvalidReportRange(ex: InvalidReportRangeException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_REPORT_RANGE", ex.message ?: "Invalid report range"))

    @ExceptionHandler(MerchantAlreadyRegisteredException::class)
    fun handleAlreadyRegistered(ex: MerchantAlreadyRegisteredException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("MERCHANT_ALREADY_REGISTERED", ex.message ?: "Conflict"))

    @ExceptionHandler(MerchantNotFoundException::class)
    fun handleNotFound(ex: MerchantNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MERCHANT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(MerchantNoAccountException::class)
    fun handleNoAccount(ex: MerchantNoAccountException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(PaymentIntentNotFoundException::class)
    fun handleIntentNotFound(ex: PaymentIntentNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("PAYMENT_CODE_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(PaymentIntentNotPayableException::class)
    fun handleIntentNotPayable(ex: PaymentIntentNotPayableException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("PAYMENT_CODE_NOT_PAYABLE", ex.message ?: "Conflict"))

    @ExceptionHandler(CustomerPaymentCodeNotFoundException::class)
    fun handleCustomerCodeNotFound(ex: CustomerPaymentCodeNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("CUSTOMER_PAYMENT_CODE_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(CustomerPaymentCodeNotPayableException::class)
    fun handleCustomerCodeNotPayable(ex: CustomerPaymentCodeNotPayableException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("CUSTOMER_PAYMENT_CODE_NOT_PAYABLE", ex.message ?: "Conflict"))

    @ExceptionHandler(PaymentCodeAccountNotOwnedException::class)
    fun handlePaymentCodeAccountNotOwned(ex: PaymentCodeAccountNotOwnedException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_OWNED", ex.message ?: "Not found"))

    @ExceptionHandler(PaymentCodeAccountNotEligibleException::class)
    fun handlePaymentCodeAccountNotEligible(ex: PaymentCodeAccountNotEligibleException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("ACCOUNT_NOT_PAYMENT_ELIGIBLE", ex.message ?: "Unprocessable"))

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

    @ExceptionHandler(AccountFrozenException::class)
    fun handleAccountFrozen(ex: AccountFrozenException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("ACCOUNT_FROZEN", ex.message ?: "Account is frozen"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))

    @ExceptionHandler(InvalidCouponException::class)
    fun handleInvalidCoupon(ex: InvalidCouponException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_COUPON", ex.message ?: "Bad request"))

    @ExceptionHandler(InsufficientLoyaltyPointsException::class)
    fun handleInsufficientLoyaltyPoints(ex: InsufficientLoyaltyPointsException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INSUFFICIENT_LOYALTY_POINTS", ex.message ?: "Bad request"))

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

    @ExceptionHandler(InvalidClosedWeekdaysException::class)
    fun handleInvalidClosedWeekdays(ex: InvalidClosedWeekdaysException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_CLOSED_WEEKDAYS", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidCashbackRateException::class)
    fun handleInvalidCashbackRate(ex: InvalidCashbackRateException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_CASHBACK_RATE", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidPhotoUrlException::class)
    fun handleInvalidPhotoUrl(ex: InvalidPhotoUrlException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_PHOTO_URL", ex.message ?: "Bad request"))

    @ExceptionHandler(MerchantAlreadyWaivedException::class)
    fun handleAlreadyWaived(ex: MerchantAlreadyWaivedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("MERCHANT_ALREADY_WAIVED", ex.message ?: "Conflict"))

    @ExceptionHandler(MerchantNotEligibleForFeeWaiverException::class)
    fun handleNotEligibleForFeeWaiver(ex: MerchantNotEligibleForFeeWaiverException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("MERCHANT_NOT_ELIGIBLE_FOR_FEE_WAIVER", ex.message ?: "Unprocessable"))

    @ExceptionHandler(InvalidStaticQrAmountException::class)
    fun handleInvalidStaticQrAmount(ex: InvalidStaticQrAmountException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_AMOUNT", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidMinOrderAmountException::class)
    fun handleInvalidMinOrderAmount(ex: InvalidMinOrderAmountException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_MIN_ORDER_AMOUNT", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidPhoneNumberException::class)
    fun handleInvalidPhoneNumber(ex: InvalidPhoneNumberException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_PHONE_NUMBER", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidOpeningHoursException::class)
    fun handleInvalidOpeningHours(ex: InvalidOpeningHoursException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_OPENING_HOURS", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidAvgPrepTimeException::class)
    fun handleInvalidAvgPrepTime(ex: InvalidAvgPrepTimeException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_AVG_PREP_TIME", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidPickupDiscountException::class)
    fun handleInvalidPickupDiscount(ex: InvalidPickupDiscountException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_PICKUP_DISCOUNT", ex.message ?: "Bad request"))
}
