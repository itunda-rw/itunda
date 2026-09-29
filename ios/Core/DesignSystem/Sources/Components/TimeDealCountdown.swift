import Foundation

// Promoted here from Features/Pay/Sources/ShopPay.swift (2026-09-02, Pay
// Feature-module decomposition) -- App/Sources/ShopDealsCarousels.swift, staying
// in :App, calls formatTimeDealCountdownHms too.

// Real Coupang 타임특가 (Time Deal, item 226) countdown -- mirrors bank-mfe/Android's
// own formatDealCountdown exactly.
public func formatTimeDealCountdown(_ endsAt: String) -> String {
    guard let end = ISO8601DateFormatter(withFractionalSeconds: true).date(from: endsAt) ?? ISO8601DateFormatter().date(from: endsAt) else { return "Ending soon" }
    let secondsLeft = end.timeIntervalSinceNow
    if secondsLeft <= 0 { return "Ending soon" }
    let totalMinutes = Int(secondsLeft / 60)
    let hours = totalMinutes / 60
    let minutes = totalMinutes % 60
    return hours > 0 ? "\(hours)h \(minutes)m left" : "\(minutes)m left"
}

// Real live HH:MM:SS countdown (2026-08-25, direct Toss Shopping reference screenshot
// -- "⏰ 23:24:20 Limited time offer") -- same real TimeDeal.endsAt formatTimeDealCountdown
// above already reads, ticking to the second, same real data Android's identical
// formatTimeDealCountdownHms now uses.
public func formatTimeDealCountdownHms(_ endsAt: String) -> String {
    guard let end = ISO8601DateFormatter(withFractionalSeconds: true).date(from: endsAt) ?? ISO8601DateFormatter().date(from: endsAt) else { return "00:00:00" }
    let secondsLeft = Int(end.timeIntervalSinceNow)
    if secondsLeft <= 0 { return "00:00:00" }
    let hours = secondsLeft / 3600
    let minutes = (secondsLeft % 3600) / 60
    let seconds = secondsLeft % 60
    return String(format: "%02d:%02d:%02d", hours, minutes, seconds)
}
