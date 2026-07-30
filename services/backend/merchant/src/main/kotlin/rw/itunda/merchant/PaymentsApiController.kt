package rw.itunda.merchant

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.web.ApiError
import rw.itunda.auth.RateLimiter
import rw.itunda.auth.RateLimitExceededException
import java.math.BigDecimal
import java.time.Duration

data class CreatePaymentRequest(
    val amount: BigDecimal,
    val description: String,
    val orderId: String? = null,
    val successUrl: String? = null,
    val failUrl: String? = null,
)

data class CancelPaymentRequest(
    val cancelReason: String,
    val cancelAmount: BigDecimal? = null,
)

/**
 * Real "Pay with itunda" external checkout API (2026-07-21) -- the itunda equivalent
 * of real Toss Payments (docs.tosspayments.com), a genuinely different product from
 * Toss Pay's own in-app consumer feature: any external merchant's OWN backend server
 * can integrate this directly, with zero itunda user login involved anywhere in the
 * flow, mirroring Toss Payments' own real API-key + widget shape (`requestPayment()`,
 * `successUrl`/`failUrl`). Found as a real gap by researching Toss Pay/Toss Payments'
 * actual documented online-checkout flow: every existing itunda merchant endpoint
 * (MerchantController) requires an interactive itunda-user JWT, so an external
 * website's own server had no way to create or check a payment non-interactively --
 * confirmed by grep, zero API-key auth mechanism existed anywhere before this.
 *
 * Real flow, mirroring Toss Payments' actual documented one exactly:
 * 1. The merchant's OWN backend calls `POST /payments` here with their secret API key
 *    (`X-Api-Key`, generated once via `MerchantController.generateApiKey`) -- gets back
 *    a `paymentKey` + `checkoutUrl`.
 * 2. The merchant's website redirects the customer's browser to `checkoutUrl` (a real
 *    itunda-hosted page, see `itunda.pay.checkout-base-url`) -- or, since this is real
 *    money movement into an itunda wallet balance, not a card network, the customer
 *    completes it the same way every other itunda QR/deep-link payment already works:
 *    scanning it with their itunda app. No new payment mechanism was invented, only a
 *    new, non-interactive way for an external server to create/check the same real
 *    `PaymentIntent`/`collect()` machinery every in-app flow already uses.
 * 3. The hosted checkout page (public, no API key -- a browser never holds the
 *    merchant's secret) polls `GET /checkout/{paymentKey}` and redirects to
 *    `successUrl`/`failUrl` once the real `collect()` flow completes.
 * 4. The merchant's own backend independently confirms via `GET /payments/{paymentKey}`
 *    (API-key authenticated again) before fulfilling the order -- the same
 *    "confirm, don't just trust the redirect" discipline every real payment gateway's
 *    docs recommend, since a customer's browser redirect alone is never proof of
 *    payment on its own.
 *
 * Deliberately permitAll at the Spring Security layer (see SecurityConfig, same
 * pattern as the `/api/v1/partners` prefix) -- API-key resolution happens inside this
 * controller/`MerchantService`, not the JWT filter chain, since neither a merchant's
 * server nor a paying customer's browser has an itunda user JWT at all.
 */
@RestController
@RequestMapping("/api/v1/pay")
class PaymentsApiController(
    private val merchantService: MerchantService,
    private val idempotencyService: IdempotencyService,
    private val rateLimiter: RateLimiter,
    @Value("\${itunda.pay.checkout-base-url}")
    private val checkoutBaseUrl: String,
) {
    @PostMapping("/payments")
    fun createPayment(
        @RequestHeader("X-Api-Key") apiKey: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @RequestBody request: CreatePaymentRequest,
    ): ResponseEntity<Map<String, Any?>> {
        val merchant = merchantService.resolveMerchantByApiKey(apiKey)
        rateLimiter.checkLimit("merchant-api:${merchant.id}:payment-create", limit = 30, window = Duration.ofMinutes(1))
        // Checkout creation does not move money, but retrying it can otherwise create
        // several concurrently payable intents for one merchant order. The merchant ID
        // belongs in the idempotency scope: unlike a payment-key path, this collection
        // endpoint is shared by every merchant API credential.
        val (status, body) = idempotencyService.replayOrExecute(
            "POST /api/v1/pay/merchants/${merchant.id}/payments", idempotencyKey, request,
        ) {
            val intent = merchantService.createExternalPayment(
                merchant, request.amount, request.description, request.orderId, request.successUrl, request.failUrl,
            )
            HttpStatus.CREATED.value() to mapOf(
                "success" to true,
                "paymentKey" to intent.id,
                "checkoutUrl" to "$checkoutBaseUrl/checkout/${intent.id}",
                "status" to intent.status,
                "expiresAt" to intent.expiresAt,
            )
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/payments/{paymentKey}")
    fun getPaymentStatus(
        @RequestHeader("X-Api-Key") apiKey: String,
        @PathVariable paymentKey: String,
    ): ResponseEntity<Map<String, Any?>> {
        val merchant = merchantService.resolveMerchantByApiKey(apiKey)
        rateLimiter.checkLimit("merchant-api:${merchant.id}:payment-read", limit = 120, window = Duration.ofMinutes(1))
        val intent = merchantService.getPaymentStatusForMerchant(merchant, paymentKey)
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "paymentKey" to intent.id,
                "orderId" to intent.orderId,
                "amount" to intent.amount,
                "status" to intent.status,
                "completedTransactionId" to intent.completedTransactionId,
            ),
        )
    }

    // Real cancel/refund (2026-07-21) -- see MerchantService.cancelPayment's own doc
    // comment for the full account of the real Toss Payments cancel API this mirrors.
    // Idempotency-Key required, same convention as every other money-moving endpoint in
    // this backend (this one reverses real ledger legs, unlike createPayment above,
    // which only ever creates an intent -- no money moves until a customer actually
    // pays it).
    @PostMapping("/payments/{paymentKey}/cancel")
    fun cancelPayment(
        @RequestHeader("X-Api-Key") apiKey: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @PathVariable paymentKey: String,
        @RequestBody request: CancelPaymentRequest,
    ): ResponseEntity<Map<String, Any?>> {
        val merchant = merchantService.resolveMerchantByApiKey(apiKey)
        rateLimiter.checkLimit("merchant-api:${merchant.id}:payment-cancel", limit = 30, window = Duration.ofMinutes(1))
        val (status, body) = idempotencyService.replayOrExecute(
            "POST /api/v1/pay/payments/$paymentKey/cancel", idempotencyKey, request,
        ) {
            200 to (mapOf("success" to true) + merchantService.cancelPayment(merchant, paymentKey, request.cancelReason, request.cancelAmount))
        }
        return ResponseEntity.status(status).body(body)
    }

    // Public, no API key -- see this class's own doc comment for why (the customer's
    // browser, not the merchant's server, calls this).
    @GetMapping("/checkout/{paymentKey}")
    fun getCheckoutInfo(@PathVariable paymentKey: String): ResponseEntity<Map<String, Any?>> {
        val info = merchantService.getCheckoutInfo(paymentKey)
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "paymentKey" to info.paymentKey,
                "merchantName" to info.merchantName,
                "amount" to info.amount,
                "description" to info.description,
                "status" to info.status,
                "successUrl" to info.successUrl,
                "failUrl" to info.failUrl,
            ),
        )
    }

    @ExceptionHandler(InvalidApiKeyException::class)
    fun handleInvalidApiKey(ex: InvalidApiKeyException) =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError("INVALID_API_KEY", ex.message ?: "Invalid API key"))

    @ExceptionHandler(InvalidCheckoutRequestException::class)
    fun handleInvalidCheckoutRequest(ex: InvalidCheckoutRequestException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_CHECKOUT_REQUEST", ex.message ?: "Bad request"))

    @ExceptionHandler(PaymentIntentNotFoundException::class)
    fun handleNotFound(ex: PaymentIntentNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("PAYMENT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(MerchantNotFoundException::class)
    fun handleMerchantNotFound(ex: MerchantNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MERCHANT_NOT_FOUND", ex.message ?: "Not found"))

    // Distinguishes which header is actually missing -- this controller requires
    // X-Api-Key for merchant authentication and Idempotency-Key for every POST, so a
    // single hardcoded message would be wrong for the other case.
    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) =
        if (ex.headerName == "Idempotency-Key") {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))
        } else {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError("API_KEY_REQUIRED", "X-Api-Key header is required"))
        }

    @ExceptionHandler(PaymentIntentNotRefundableException::class)
    fun handleNotRefundable(ex: PaymentIntentNotRefundableException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("PAYMENT_NOT_REFUNDABLE", ex.message ?: "Conflict"))

    @ExceptionHandler(InvalidCancelRequestException::class)
    fun handleInvalidCancelRequest(ex: InvalidCancelRequestException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_CANCEL_REQUEST", ex.message ?: "Bad request"))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleIdempotencyConflict(ex: IdempotencyConflictException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleIdempotencyInProgress(ex: IdempotencyInProgressException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))
}
