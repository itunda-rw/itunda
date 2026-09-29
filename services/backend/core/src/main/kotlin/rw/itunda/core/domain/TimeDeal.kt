package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant

/**
 * Real Coupang 타임특가 (Time Deal) -- a time-boxed, quantity-capped discount OVERLAY on
 * an existing real `MerchantProduct`, not a second parallel catalog or payment pipeline.
 * Sourced from Coupang's own real, currently-live Time Deal mechanic: a scarcity-priced
 * event that's only live for a fixed window and/or a capped stock, distinct from this
 * catalog's existing always-on `MerchantProduct.discountPercent`/`originalPrice` (a
 * permanent price override) and from `ProductPriceTier` (a permanent bulk-quantity
 * discount) -- neither of those is time-boxed or independently stock-capped.
 *
 * `dealPrice` is real merchant-set, validated cheaper than the product's own flat
 * `price` at creation time (see `TimeDealService`'s own validation). `originalPrice` is
 * snapshotted from `MerchantProduct.price` at creation so a later catalog price change
 * never retroactively changes an already-running deal's advertised discount -- the same
 * "snapshot, don't recompute at read time" discipline `OrderItem.unitPrice` already
 * establishes for a paid order's own receipt.
 *
 * `remainingQuantity` is decremented atomically inside the same real checkout
 * transaction `OrderService.placeOrder` already uses for stock (`@Version` guards the
 * same real oversell race `MerchantProduct.stockQuantity`'s own optimistic-locking
 * comment already documents). A deal that's expired or sold out is never deleted --
 * its row is just excluded from the real "active now" query, preserving a real
 * historical record of what ran and sold.
 */
@Entity
@Table(name = "time_deals")
class TimeDeal(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "merchant_id", nullable = false, length = 64)
    val merchantId: String,

    @Column(name = "product_id", nullable = false, length = 64)
    val productId: String,

    @Column(name = "deal_price", nullable = false, precision = 18, scale = 2)
    val dealPrice: BigDecimal,

    @Column(name = "original_price", nullable = false, precision = 18, scale = 2)
    val originalPrice: BigDecimal,

    @Column(name = "total_quantity", nullable = false)
    val totalQuantity: Int,

    @Column(name = "remaining_quantity", nullable = false)
    var remainingQuantity: Int,

    @Column(name = "starts_at", nullable = false)
    val startsAt: Instant,

    @Column(name = "ends_at", nullable = false)
    var endsAt: Instant,

    @Version
    @Column(nullable = false)
    var version: Long = 0,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(
        id = "", merchantId = "", productId = "", dealPrice = BigDecimal.ZERO, originalPrice = BigDecimal.ZERO,
        totalQuantity = 0, remainingQuantity = 0, startsAt = Instant.EPOCH, endsAt = Instant.EPOCH,
    )
}
