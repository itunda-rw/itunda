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

enum class P2pPaymentRequestStatus { PENDING, COMPLETED, EXPIRED }

/**
 * A fixed-amount QR/payment-request a regular itunda user generates to be paid by another
 * itunda user directly -- the person-to-person counterpart to Merchant's PaymentIntent
 * (deliberately a separate entity/table, not a shared one, since merchantId there is a real
 * NOT NULL foreign-key-style column and merchant collection charges a real fee; this doesn't).
 * "Named request rather than qr" for the same reason PaymentIntent is: rendering a QR image
 * is a client concern, this is the amount/status/expiry record behind it.
 */
@Entity
@Table(name = "p2p_payment_requests")
class P2pPaymentRequest(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "requester_user_id", nullable = false, length = 64)
    val requesterUserId: String,

    @Column(nullable = false, precision = 18, scale = 2)
    val amount: BigDecimal,

    @Column(nullable = false)
    val description: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: P2pPaymentRequestStatus = P2pPaymentRequestStatus.PENDING,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "completed_transaction_id", length = 64)
    var completedTransactionId: String? = null,

    @Column(name = "paid_by_user_id", length = 64)
    var paidByUserId: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(id = "", requesterUserId = "", amount = BigDecimal.ZERO, description = "", expiresAt = Instant.now())
}
