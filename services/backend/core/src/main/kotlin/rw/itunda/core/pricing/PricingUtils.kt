package rw.itunda.core.pricing

import rw.itunda.core.domain.ProductPriceTier
import java.math.BigDecimal

/**
 * Real bulk/wholesale price resolution -- see `ProductPriceTier`'s own doc comment. The
 * highest tier whose `minQuantity` the real ordered quantity actually meets, falling
 * back to the product's own flat `basePrice` when no tier qualifies (including the
 * common case of a product with no tiers at all) -- never a fabricated discount.
 */
fun effectiveUnitPrice(basePrice: BigDecimal, quantity: Int, tiers: List<ProductPriceTier>): BigDecimal =
    tiers.filter { it.minQuantity <= quantity }.maxByOrNull { it.minQuantity }?.unitPrice ?: basePrice
