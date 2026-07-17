package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

// Forward-only, no CANCELLED in this pass -- real order cancellation/refund is a
// genuinely separate feature (a reversing ledger entry, same technique
// SupportService.reverseTransaction already established for support-ticket refunds),
// deliberately not attempted here. See OrderService's own doc comment.
enum class OrderStatus { PLACED, PACKED, SHIPPED, DELIVERED }

/**
 * A real Coupang-style multi-item order -- the third and last of the three new "super
 * app" phases named in the 2026-07-18 goal expansion. Reuses itunda's own already-real
 * `Merchant`/`MerchantProduct` catalog (built for the "Toss Place" register-software
 * gap) as the seller side, rather than inventing a second product-catalog system.
 *
 * Honestly, no real third-party courier/delivery-logistics network exists anywhere in
 * this backend or in Rwanda's real market the way it does for Toss's own Korean
 * Coupang-adjacent competitors -- `status` below is real, but self-declared by the
 * merchant (PACKED/SHIPPED/DELIVERED), the same way a real small Rwandan business
 * genuinely handles its own delivery or in-person pickup today, not a fake courier
 * simulation invented to look like a real logistics integration. See OrderService's own
 * doc comment for the full account.
 */
@Entity
@Table(name = "orders")
class Order(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "buyer_id", nullable = false, length = 64)
    val buyerId: String,

    @Column(name = "merchant_id", nullable = false, length = 64)
    val merchantId: String,

    @Column(name = "delivery_address", nullable = false, length = 500)
    val deliveryAddress: String,

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    val totalAmount: BigDecimal,

    @Column(nullable = false, precision = 18, scale = 2)
    val fee: BigDecimal,

    @Column(name = "transaction_id", nullable = false, length = 64)
    val transactionId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: OrderStatus = OrderStatus.PLACED,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    protected constructor() : this(
        id = "", buyerId = "", merchantId = "", deliveryAddress = "", totalAmount = BigDecimal.ZERO,
        fee = BigDecimal.ZERO, transactionId = "",
    )
}
