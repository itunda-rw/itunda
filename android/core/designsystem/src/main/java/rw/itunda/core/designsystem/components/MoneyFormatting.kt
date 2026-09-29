package rw.itunda.core.designsystem.components

import java.math.BigDecimal
import java.util.Locale

// Promoted 2026-09-05 -- a repo-wide duplicated-helper sweep found this exact logic
// byte-for-byte duplicated as a private/internal fun under 19 different names
// (formatMoneyWeekly/formatMoneyGrow31/formatMoneyGroup/formatMoneyCard/etc.) across
// :app and 4 Feature modules. All 19 were verified identical (no divergence bug this
// time, unlike the historical formatAmount thousands-separator bug this same sweep
// technique found before) -- consolidated here to remove the duplication and the
// standing risk of a future silent divergence between copies.
//
// Real gap found 2026-09-06, same class as the web `.toLocaleString()` bug fixed the
// same session: `"%,d".format(...)`/`"%,.2f".format(...)` with no `Locale` argument
// resolves to `Locale.getDefault()`, i.e. the DEVICE's own system language -- verified
// live (`String.format(Locale.forLanguageTag("rw"), "%,d", 10346)` -> "10.346",
// period as thousands separator) that a Rwandan user with their phone set to French
// or Kinyarwanda would see money rendered in a different, non-comma format than
// itunda's own established "10,346 RWF" convention, on every single screen that uses
// this now-canonical helper. Fixed by pinning `Locale.US` explicitly on both formats.
fun formatMoney(value: BigDecimal): String {
    val rounded = value.stripTrailingZeros()
    return if (rounded.scale() <= 0) {
        String.format(Locale.US, "%,d", rounded.toBigInteger())
    } else {
        String.format(Locale.US, "%,.2f", rounded)
    }
}

fun formatMoney(value: Double): String {
    val rounded = Math.round(value * 100.0) / 100.0
    return if (rounded == Math.floor(rounded)) {
        String.format(Locale.US, "%,d", rounded.toLong())
    } else {
        String.format(Locale.US, "%,.2f", rounded)
    }
}
