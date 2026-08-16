package rw.itunda.core.geo

import java.math.BigDecimal

// Real Baemin (배달의민족) tiered order-amount promotion, sourced from Baemin's own
// 2026-04 fee/promotion restructuring: automatic RWF-value-tiered discounts applied
// at checkout with no coupon code needed, platform-funded rather than
// restaurant-funded (see EatsOrderService.placeOrder's own doc comment for the real
// double-entry mechanic this backs -- restaurant payout is never reduced). itunda's
// own honest RWF-scaled tier thresholds, not copied KRW figures -- same "itunda's own
// choice for the Rwandan market" discipline DebitCard's default limits already
// establish, since Baemin's real KRW amounts have no sourced RWF equivalent.
object EatsPromotionCalculator {
    private data class Tier(val minSubtotal: BigDecimal, val discount: BigDecimal)

    // Ordered highest-threshold-first so the first match in fold-style iteration below
    // is always the best real discount the order actually qualifies for.
    private val tiers = listOf(
        Tier(BigDecimal("15000"), BigDecimal("4000")),
        Tier(BigDecimal("10000"), BigDecimal("2500")),
        Tier(BigDecimal("5000"), BigDecimal("1000")),
    )

    fun calculateDiscount(itemsSubtotal: BigDecimal): BigDecimal =
        tiers.firstOrNull { itemsSubtotal >= it.minSubtotal }?.discount ?: BigDecimal.ZERO
}
