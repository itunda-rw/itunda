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

// Forward-only PLACED -> PACKED -> SHIPPED -> DELIVERED, plus a real CANCELLED
// terminal state (2026-07-18) reachable only from PLACED -- before the seller has
// started real fulfillment work. See OrderService.cancelOrder's own doc comment for
// the reversing-ledger-entry technique this reuses from SupportService.reverseTransaction.
enum class OrderStatus { PLACED, PACKED, SHIPPED, DELIVERED, CANCELLED }

/**
 * A real Coupang-style multi-item order -- the third and last of the three new "super
 * app" phases named in the 2026-07-18 goal expansion. Reuses itunda's own already-real
 * `Merchant`/`MerchantProduct` catalog (built for the "Toss Place" register-software
 * gap) as the seller side, rather than inventing a second product-catalog system.
 *
 * No real third-party courier/delivery-logistics API integration exists anywhere in
 * this backend (nor is it realistically reachable the way it might be for Toss's own
 * Korean Coupang-adjacent competitors), so `status` remains real but merchant-declared
 * by default -- a real small Rwandan business genuinely handling its own delivery or
 * in-person pickup, not a fake courier simulation dressed up to look like a real
 * logistics integration.
 *
 * `riderId` (2026-07-26) closes that gap the same honest way Eats' own real internal
 * gig-rider network already did for food delivery: itunda's own `Rider`s (shared,
 * `:core`-level entity -- the same rider pool that already delivers Eats orders can
 * pick up Commerce packages too, exactly like a real gig-economy courier who carries
 * both food and parcels) can now claim a PACKED order, own its SHIPPED->DELIVERED leg,
 * and expose real live GPS position to the buyer while in transit. This is itunda's own
 * driver fleet (the honest equivalent of Coupang's own Coupang Flex contracted drivers,
 * not a "third-party" integration), not the previously-blocked external logistics API.
 * A merchant who never gets a rider claim can still self-declare SHIPPED/DELIVERED
 * exactly as before -- see OrderService.updateOrderStatus's own doc comment for the
 * exact boundary between the two paths.
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

    @Column(name = "refund_transaction_id", length = 64)
    var refundTransactionId: String? = null,

    @Column(name = "rider_id", length = 64)
    var riderId: String? = null,

    // Merchant, buyer, and rider actions can advance or cancel the same paid order.
    // Guard its lifecycle so a concurrent refund cannot coexist with a delivery update.
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(
        id = "", buyerId = "", merchantId = "", deliveryAddress = "", totalAmount = BigDecimal.ZERO,
        fee = BigDecimal.ZERO, transactionId = "",
    )
}
