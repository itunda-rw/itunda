package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

// Forward-only, same discipline as commerce's OrderStatus (see Order.kt). The first four
// states are restaurant-driven (PLACED -> ACCEPTED -> PREPARING -> READY_FOR_PICKUP), the
// next three are rider-driven (a rider claims a READY_FOR_PICKUP order, moving it to
// RIDER_ASSIGNED, then PICKED_UP, then DELIVERED -- the transition that triggers the real
// delivery-fee payout out of `eats_delivery_holding` into the rider's own wallet). A real
// CANCELLED terminal state (2026-07-18) is reachable only from PLACED -- before the
// restaurant has started real fulfillment work and before any rider is involved, the
// safest and simplest real scope. See EatsOrderService.cancelOrder's own doc comment for
// the reversing-ledger-entry technique this reuses from SupportService.reverseTransaction.
enum class EatsOrderStatus { PLACED, ACCEPTED, PREPARING, READY_FOR_PICKUP, RIDER_ASSIGNED, PICKED_UP, DELIVERED, CANCELLED }

/**
 * A real Coupang Eats-style food order -- built on top of the same real `Merchant`/
 * `MerchantProduct` catalog Commerce/Toss Shopping/Toss Place already reuse (a restaurant
 * IS a `Merchant`, a menu item IS a `MerchantProduct`; no new catalog system invented),
 * but kept as its own entity/table rather than extending commerce's `Order` -- the two
 * have genuinely different lifecycles (Eats needs a rider assignment and a longer,
 * delivery-specific status chain) and this session's own established discipline is to
 * favor purely additive new entities over modifying already-tested money-movement code.
 *
 * `totalAmount` = `itemsSubtotal` + `deliveryFee`, charged to the buyer in one atomic
 * ledger post at placement time. `platformFee` (itunda's cut, same 1.5% rate reasoning
 * `OrderService.feeRate`/`MerchantService.feeRate` already use) comes out of the
 * restaurant's share of `itemsSubtotal`. `deliveryFee` is real but flat (no real
 * distance/geo data exists anywhere in this backend, the same honest simplification
 * already named for the neighborhood marketplace) -- it's held in the real
 * `eats_delivery_holding` clearing account from placement until a real rider completes
 * the delivery, at which point `EatsOrderService` posts a second real ledger transaction
 * paying it straight into that rider's own wallet.
 */
@Entity
@Table(name = "eats_orders")
class EatsOrder(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "buyer_id", nullable = false, length = 64)
    val buyerId: String,

    @Column(name = "restaurant_id", nullable = false, length = 64)
    val restaurantId: String,

    @Column(name = "rider_id", length = 64)
    var riderId: String? = null,

    @Column(name = "delivery_address", nullable = false, length = 500)
    val deliveryAddress: String,

    @Column(name = "items_subtotal", nullable = false, precision = 18, scale = 2)
    val itemsSubtotal: BigDecimal,

    @Column(name = "delivery_fee", nullable = false, precision = 18, scale = 2)
    val deliveryFee: BigDecimal,

    @Column(name = "platform_fee", nullable = false, precision = 18, scale = 2)
    val platformFee: BigDecimal,

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    val totalAmount: BigDecimal,

    @Column(name = "transaction_id", nullable = false, length = 64)
    val transactionId: String,

    @Column(name = "delivery_payout_transaction_id", length = 64)
    var deliveryPayoutTransactionId: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: EatsOrderStatus = EatsOrderStatus.PLACED,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "refund_transaction_id", length = 64)
    var refundTransactionId: String? = null,
) {
    protected constructor() : this(
        id = "", buyerId = "", restaurantId = "", deliveryAddress = "", itemsSubtotal = BigDecimal.ZERO,
        deliveryFee = BigDecimal.ZERO, platformFee = BigDecimal.ZERO, totalAmount = BigDecimal.ZERO, transactionId = "",
    )
}
