package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

/**
 * Real bulk/wholesale pricing (2026-07-25) -- closes the real gap named in Baemin's
 * (우아한형제들) own 배민상회 B2B supplies marketplace research: the real differentiator
 * between a B2B wholesale listing and a normal consumer retail one isn't a separate
 * catalog system, it's that price genuinely depends on quantity ("save money on
 * sourcing," Baemin's own real positioning for restaurant owners buying in bulk).
 * itunda already has real merchant/product infra (Shopping, Eats) -- this reuses it
 * completely rather than inventing a second B2B-only marketplace, matching the "no
 * duplicate catalog system" discipline `EatsOrderService`/`DineInOrderService` already
 * established.
 *
 * A [MerchantProduct] with no real tiers behaves exactly as before this feature existed
 * (flat `price`, unaffected) -- purely additive. `minQuantity` is the real threshold at
 * which `unitPrice` applies (e.g. "buy 10+, pay 4,500 each instead of 5,000"); see
 * `MerchantProductService.setPriceTiers`'s own doc comment for the real "must actually
 * be a discount, not a markup" validation this enforces.
 */
@Entity
@Table(name = "product_price_tiers")
class ProductPriceTier(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "product_id", nullable = false, length = 64)
    val productId: String,

    @Column(name = "min_quantity", nullable = false)
    val minQuantity: Int,

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    val unitPrice: BigDecimal,
) {
    protected constructor() : this(id = "", productId = "", minQuantity = 1, unitPrice = BigDecimal.ZERO)
}
