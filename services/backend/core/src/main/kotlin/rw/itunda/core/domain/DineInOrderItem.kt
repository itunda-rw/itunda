package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

/**
 * A real line item within a [DineInOrder] -- same snapshot-at-purchase-time convention as
 * `EatsOrderItem`, so a later menu price change never retroactively alters an already-paid
 * receipt.
 */
@Entity
@Table(name = "dine_in_order_items")
class DineInOrderItem(
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

    @Column(name = "selected_options_json", length = 4000, nullable = true)
    val selectedOptionsJson: String? = null,
) {
    protected constructor() : this(
        id = "", orderId = "", productId = "", productName = "", unitPrice = BigDecimal.ZERO, quantity = 0, selectedOptionsJson = null,
    )
}
