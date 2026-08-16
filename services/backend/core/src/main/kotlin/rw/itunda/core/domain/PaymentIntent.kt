package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant

enum class PaymentIntentStatus { PENDING, COMPLETED, EXPIRED }

/**
 * A fixed-amount QR/payment-collection request a merchant generates (see
 * MerchantService.generateQr) and a customer pays (MerchantService.collect).
 * Named "intent" rather than "qr" on purpose -- the backend's job is the
 * amount/status/expiry record, not rendering a QR image, matching how a real
 * payment gateway's API returns a reference/code for the client to render, not
 * raw image bytes.
 */
@Entity
@Table(name = "payment_intents")
class PaymentIntent(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "merchant_id", nullable = false, length = 64)
    val merchantId: String,

    @Column(nullable = false, precision = 18, scale = 2)
    val amount: BigDecimal,

    @Column(nullable = false)
    val description: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: PaymentIntentStatus = PaymentIntentStatus.PENDING,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "completed_transaction_id", length = 64)
    var completedTransactionId: String? = null,

    @Column(name = "paid_by_user_id", length = 64)
    var paidByUserId: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    // Real external-checkout fields (2026-07-21) -- only ever set by
    // PaymentsApiController's API-key-authenticated create path, never by the in-app
    // merchant QR flow (MerchantController.generateQr leaves all three null). orderId
    // is the merchant's own reference for reconciliation, matching Toss Payments' real
    // orderId parameter; successUrl/failUrl are where itunda's hosted checkout page
    // redirects the customer's browser once payment completes or the intent expires,
    // matching Toss Payments' real requestPayment() successUrl/failUrl contract exactly.
    @Column(name = "order_id", length = 200, nullable = true)
    var orderId: String? = null,

    @Column(name = "success_url", length = 500, nullable = true)
    var successUrl: String? = null,

    @Column(name = "fail_url", length = 500, nullable = true)
    var failUrl: String? = null,

    // Real cancel/refund tracking (2026-07-21) -- see MerchantService.cancelPayment's
    // own doc comment. Supports Toss Payments' real partial-cancel model (multiple
    // cancels can accumulate up to the original amount) rather than a single
    // all-or-nothing boolean flag: a full refund is simply refundedAmount == amount,
    // no separate CANCELLED status needed.
    @Column(name = "refunded_amount", nullable = false, precision = 18, scale = 2)
    var refundedAmount: BigDecimal = BigDecimal.ZERO,

    /** Prevents concurrent collect/cancel/expiry transitions from silently overwriting each other. */
    @Version
    @Column(nullable = false)
    var version: Long = 0,

    // Real Toss Payments ARS결제-style USSD payment completion (2026-08-16, sourced
    // from Toss's own 2026 release notes: a payment method built for call-center/
    // telesales contexts where the customer has no app/browser open, confirming a
    // pending payment over the phone instead). itunda's real UUID-based paymentKey
    // (`id`, "pi_<uuid>") is unusable on a feature-phone numeric keypad -- this is a
    // short, real, USSD-typeable numeric alias generated alongside every intent
    // specifically for UssdService.handleCompletePayment to resolve by. Nullable only
    // for the protected no-arg JPA constructor below; every real intent always gets one
    // (see MerchantService.createIntent).
    @Column(name = "ussd_code", length = 6, unique = true)
    var ussdCode: String? = null,
) {
    protected constructor() : this(id = "", merchantId = "", amount = BigDecimal.ZERO, description = "", expiresAt = Instant.now())
}
