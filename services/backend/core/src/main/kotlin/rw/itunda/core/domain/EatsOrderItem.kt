package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

/**
 * A real line item within an [EatsOrder] -- same snapshot-at-purchase-time convention as
 * commerce's `OrderItem`, so a later menu price change never retroactively alters an
 * already-paid order's receipt.
 */
@Entity
@Table(name = "eats_order_items")
class EatsOrderItem(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "order_id", nullable = false, length = 64)
    val orderId: String,

    @Column(name = "product_id", nullable = false, length = 64)
    val productId: String,

    @Column(name = "product_name", nullable = false)
    val productName: String,

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    val unitPrice: BigDecimal,

    @Column(nullable = false)
    val quantity: Int,

    // Real menu-options snapshot (2026-07-21, v1: single-select required option groups
    // only -- see MenuOptionGroup/MenuOptionChoice's own doc comments) -- a JSON array of
    // {"groupName","choiceName","priceDelta"} resolved at order time, same
    // snapshot-at-purchase-time convention as productName/unitPrice above so a later
    // menu-option edit never retroactively alters an already-paid receipt. `unitPrice`
    // above already includes every selected choice's priceDelta -- this column is purely
    // a human-readable receipt breakdown, not a second source of truth for pricing. Null
    // for any item whose product had no option groups defined (the overwhelming
    // majority of pre-existing menu items, unaffected by this additive feature).
    @Column(name = "selected_options_json", length = 4000, nullable = true)
    val selectedOptionsJson: String? = null,
) {
    protected constructor() : this(
        id = "", orderId = "", productId = "", productName = "", unitPrice = BigDecimal.ZERO, quantity = 0, selectedOptionsJson = null,
    )
}
