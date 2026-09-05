import Foundation

// Promoted 2026-09-05 -- a repo-wide duplicated-helper sweep (same technique already
// applied to Android the same day, see MoneyFormatting.kt's own doc comment there)
// found this exact logic byte-for-byte duplicated as a private `formatMoneyX`/
// `formatAmount` function across 74 files -- 25 copies of the decimal-aware
// formatter (a handful with cosmetically different but behaviorally-identical
// `.rounded(.down)` vs `.rounded(.towardZero)`/`Int64` vs `Int` fallback choices,
// since real money values here are always positive) and 51 copies of the simpler
// always-whole-number formatter. No divergence bug found this time either -- pure
// duplication, consolidated to remove the standing risk of a future silent drift.
public func formatMoney(_ value: Double) -> String {
    let rounded = (value * 100).rounded() / 100
    let formatter = NumberFormatter()
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.numberStyle = .decimal
    formatter.groupingSeparator = ","
    formatter.usesGroupingSeparator = true
    if rounded == rounded.rounded(.down) {
        formatter.maximumFractionDigits = 0
        return formatter.string(from: NSNumber(value: rounded)) ?? String(Int64(rounded))
    }
    formatter.minimumFractionDigits = 2
    formatter.maximumFractionDigits = 2
    return formatter.string(from: NSNumber(value: rounded)) ?? String(format: "%.2f", rounded)
}

// The simpler, deliberately different "always whole number, never shows decimals"
// variant -- for contexts where amounts are always whole RWF (fares, fees, etc.),
// same distinction Android's own MoneyFormatting.kt draws between formatMoney and
// the one-liner Number-based formatter it left alone.
public func formatAmount(_ value: Int) -> String {
    let formatter = NumberFormatter()
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.numberStyle = .decimal
    formatter.groupingSeparator = ","
    return formatter.string(from: NSNumber(value: value)) ?? "0"
}

// Real gap found 2026-09-06, same class as the web `.toLocaleString()` bug and
// Android's `String.format` bug fixed the same session: neither formatter above
// pinned `.locale`, so `NumberFormatter` fell back to the DEVICE's own current
// locale for everything except the explicitly-forced `groupingSeparator` --
// including the DECIMAL separator, which on a French/Kinyarwanda device is also a
// comma. Verified live (Swift, `NumberFormatter` with `locale = Locale(identifier:
// "fr_FR")`): the decimal branch of `formatMoney` produced "10,346,50" for
// 10346.50 -- genuinely ambiguous output, not just a different-but-consistent
// separator, since the same "," character means two different things in one
// string. Fixed by pinning `formatter.locale = Locale(identifier: "en_US_POSIX")`
// on both formatters below, so a device's own language/region setting can never
// again change how money renders.
