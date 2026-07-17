package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

/**
 * A real line item within an [Order]. `productName`/`unitPrice` are snapshotted at
 * purchase time (not a live join against `MerchantProduct`) -- the same real receipt
 * convention any real e-commerce order uses, since a product's name or price can
 * change (or the product itself can be deactivated) after an order that referenced it
 * was already placed and paid for.
 */
@Entity
@Table(name = "order_items")
class OrderItem(
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
