package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
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
) {
    protected constructor() : this(id = "", merchantId = "", amount = BigDecimal.ZERO, description = "", expiresAt = Instant.now())
}
