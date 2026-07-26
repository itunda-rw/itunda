package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

enum class OrderReturnType { RETURN, EXCHANGE }
enum class OrderReturnStatus { REQUESTED, APPROVED, REJECTED }

/**
 * A real Coupang-style post-delivery Return & Exchange request (반품/교환 신청) -- see
 * OrderReturnService's own doc comment for the full account and sourcing. Deliberately
 * its own entity, not a repurposed field on [Order]: a DELIVERED order can have exactly
 * one open return/exchange lifecycle layered on top of it without touching `Order`'s
 * own forward-only `status` chain or `OrderService`'s already-tested cancellation path
 * at all -- this is a genuinely separate, additive real-world event, matching Coupang's
 * own real product distinction between pre-fulfillment cancellation and post-delivery
 * return/exchange.
 */
@Entity
@Table(name = "order_return_requests")
class OrderReturnRequest(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "order_id", nullable = false, length = 64)
    val orderId: String,

    @Column(name = "buyer_id", nullable = false, length = 64)
    val buyerId: String,

    @Column(name = "merchant_id", nullable = false, length = 64)
    val merchantId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    val type: OrderReturnType,

    @Column(name = "reason_code", nullable = false, length = 32)
    val reasonCode: String,

    @Column(name = "reason_note", length = 500)
    val reasonNote: String?,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: OrderReturnStatus = OrderReturnStatus.REQUESTED,

    @Column(name = "refund_transaction_id", length = 64)
    var refundTransactionId: String? = null,

    @Column(name = "requested_at", nullable = false)
    val requestedAt: Instant = Instant.now(),

    @Column(name = "decided_at")
    var decidedAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    protected constructor() : this(
        id = "", orderId = "", buyerId = "", merchantId = "", type = OrderReturnType.RETURN,
        reasonCode = "", reasonNote = null,
    )
}
