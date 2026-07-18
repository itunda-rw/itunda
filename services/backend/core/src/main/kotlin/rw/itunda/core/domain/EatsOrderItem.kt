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
) {
    protected constructor() : this(
        id = "", orderId = "", productId = "", productName = "", unitPrice = BigDecimal.ZERO, quantity = 0,
    )
}
