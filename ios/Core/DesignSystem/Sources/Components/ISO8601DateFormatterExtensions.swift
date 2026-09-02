import Foundation

// Promoted here from App/Sources/TalkMessageBubbles.swift (2026-09-02, Pay
// Feature-module decomposition -- ShopPay.swift's real Coupang 타임특가 countdown
// formatters, now in FeaturePay, need this same convenience initializer). Stays
// in use by TalkMessageBubbles.swift/WeeklySavingsScreenView.swift in :App --
// zero unique coupling, plain Foundation extension, same "promote a cohesive,
// widely-shared component" pattern as this same slice's other promotions.
extension ISO8601DateFormatter {
    public convenience init(withFractionalSeconds: Bool) {
        self.init()
        if withFractionalSeconds { formatOptions.insert(.withFractionalSeconds) }
    }
}

// Promoted here from App/Sources/EatsScreen.swift alongside the above (2026-09-02,
// same Pay Feature-module decomposition) -- MyPaymentCodeCard.swift, now in
// FeaturePay, shares this same fractional-seconds formatter with
// EatsMembership.swift, which stays in :App.
public let isoDateFormatterFractional: ISO8601DateFormatter = {
    let f = ISO8601DateFormatter()
    f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    return f
}()
