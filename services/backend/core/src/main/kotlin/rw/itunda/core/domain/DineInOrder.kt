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

// Forward-only, restaurant-driven only (no rider involved at all) -- PLACED -> ACCEPTED ->
// PREPARING -> SERVED. A real CANCELLED terminal state, reachable only from PLACED, same
// scope EatsOrderStatus.CANCELLED already established for the same reason: safest before
// the restaurant has started real fulfillment work.
enum class DineInOrderStatus { PLACED, ACCEPTED, PREPARING, SERVED, CANCELLED }

/**
 * Real 배민오더-style table/QR in-store ordering -- a buyer scans (or types) a table's own QR
 * code and orders straight off the same real `Merchant`/`MerchantProduct` catalog `EatsOrder`
 * already uses (a restaurant IS a `Merchant`, a menu item IS a `MerchantProduct`), but kept as
 * its own entity rather than extending `EatsOrder` -- the two have genuinely different
 * lifecycles (no delivery address, no rider, no delivery fee/holding leg at all -- payment
 * settles straight into the restaurant's own account at placement, minus the same platform
 * fee rate `EatsOrderService`/`OrderService`/`MerchantService` already use) and this
 * codebase's own established discipline is to favor purely additive new entities over
 * modifying already-tested money-movement code (see `EatsOrder.kt`'s own doc comment).
 *
 * `tableNumber` is free text read straight off a physical table tag/QR code (e.g. "12",
 * "Patio 3") -- no separate table/QR-token entity, the same "honest choice at this system's
 * real data scale" simplification `SavingsService.getGoalsDueForAutoContribution`'s own doc
 * comment already established elsewhere; a mislabeled table only misroutes where food gets
 * served, not money, so it doesn't carry the same integrity requirement a delivery address
 * does.
 */
@Entity
@Table(name = "dine_in_orders")
class DineInOrder(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "buyer_id", nullable = false, length = 64)
    val buyerId: String,

    @Column(name = "restaurant_id", nullable = false, length = 64)
    val restaurantId: String,

    @Column(name = "table_number", nullable = false, length = 50)
    val tableNumber: String,

    @Column(name = "items_subtotal", nullable = false, precision = 18, scale = 2)
    val itemsSubtotal: BigDecimal,

    @Column(name = "platform_fee", nullable = false, precision = 18, scale = 2)
    val platformFee: BigDecimal,

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    val totalAmount: BigDecimal,

    @Column(name = "transaction_id", nullable = false, length = 64)
    val transactionId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: DineInOrderStatus = DineInOrderStatus.PLACED,

    @Column(name = "notes", length = 500)
    val notes: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "refund_transaction_id", length = 64)
    var refundTransactionId: String? = null,

    // Buyer cancellation and restaurant status updates are independent requests.  A
    // version check prevents conflicting fulfillment and refund outcomes.
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(
        id = "", buyerId = "", restaurantId = "", tableNumber = "", itemsSubtotal = BigDecimal.ZERO,
        platformFee = BigDecimal.ZERO, totalAmount = BigDecimal.ZERO, transactionId = "",
    )
}
