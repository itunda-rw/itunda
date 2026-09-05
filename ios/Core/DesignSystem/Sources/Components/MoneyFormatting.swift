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
    formatter.numberStyle = .decimal
    formatter.groupingSeparator = ","
    return formatter.string(from: NSNumber(value: value)) ?? "0"
}
