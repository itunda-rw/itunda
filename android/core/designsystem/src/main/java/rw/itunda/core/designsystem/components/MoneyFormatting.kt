package rw.itunda.core.designsystem.components

import java.math.BigDecimal

// Promoted 2026-09-05 -- a repo-wide duplicated-helper sweep found this exact logic
// byte-for-byte duplicated as a private/internal fun under 19 different names
// (formatMoneyWeekly/formatMoneyGrow31/formatMoneyGroup/formatMoneyCard/etc.) across
// :app and 4 Feature modules. All 19 were verified identical (no divergence bug this
// time, unlike the historical formatAmount thousands-separator bug this same sweep
// technique found before) -- consolidated here to remove the duplication and the
// standing risk of a future silent divergence between copies.
fun formatMoney(value: BigDecimal): String {
    val rounded = value.stripTrailingZeros()
    return if (rounded.scale() <= 0) "%,d".format(rounded.toBigInteger()) else "%,.2f".format(rounded)
}

fun formatMoney(value: Double): String {
    val rounded = Math.round(value * 100.0) / 100.0
    return if (rounded == Math.floor(rounded)) "%,d".format(rounded.toLong()) else "%,.2f".format(rounded)
}
