package rw.itunda.payment.db.entity

import com.itunda.payment.domain.PaymentStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

/**
 * Durable payment state for the standalone payment service.
 *
 * A row is keyed by orderId so one order cannot create two independent payment
 * confirmation records. CONFIRMING is a durable claim that prevents another
 * replica from starting a second provider operation for the same order.
 */
@Entity
@Table(name = "payment_records")
class PaymentRecordEntity(
    @Id
    @Column(name = "order_id", nullable = false, updatable = false, length = 128)
    var orderId: String = "",

    @Column(name = "merchant_id", nullable = false, length = 128)
    var mId: String = "",

    @Column(name = "payment_key", nullable = false, length = 255)
    var paymentKey: String = "",

    @Column(name = "order_name", nullable = false, length = 255)
    var orderName: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: PaymentStatus = PaymentStatus.READY,

    @Column(name = "requested_at", nullable = false)
    var requestedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "approved_at")
    var approvedAt: OffsetDateTime? = null,

    @Column(name = "total_amount", nullable = false)
    var totalAmount: Long = 0,

    @Column(name = "balance_amount", nullable = false)
    var balanceAmount: Long = 0,

    @Column(nullable = false, length = 8)
    var currency: String = "RWF",

    @Column(name = "mobile_money_provider", length = 32)
    var mobileMoneyProvider: String? = null,

    @Column(name = "mobile_money_phone", length = 64)
    var mobileMoneyPhone: String? = null,

    @Column(name = "mobile_money_receipt_url", length = 512)
    var mobileMoneyReceiptUrl: String? = null,

    @Column(nullable = false, length = 8)
    var country: String = "RW",

    @Column(nullable = false, length = 32)
    var method: String = "MOMO"
)
