import Foundation

// Promoted here from Features/Payments/Sources/TransactionHistoryScreen.swift
// (2026-09-02, Pay Feature-module decomposition) -- MyPaymentCodeCard.swift/
// PayMoneyDetailScreen.swift, both now in FeaturePay, need this same plain data
// model TransactionHistoryScreen (staying in FeaturePayments) also uses; a
// FeaturePay-to-FeaturePayments Sources import isn't allowed cross-feature, so
// this moves to CoreDesignSystem instead -- same "promote a cohesive,
// zero-unique-coupling plain data type" pattern as this same slice's
// DiscoverRowData promotion.

/// Plain display model, not the App target's TransactionDto directly -- same
/// module-dependency-direction constraint as BankView/SavingsRowData. ContentView
/// maps the real TransactionDto list into this shape before calling screens that
/// display it.
public struct TransactionDisplayItem: Identifiable {
    public let id: String
    public let description: String
    public let amount: Double
    public let currency: String
    public let status: String
    public let isOutgoing: Bool
    // Real "see payment history only" filter on PayMoneyDetailScreen (2026-08-21) --
    // needs the transaction's own type ("PAYMENT" etc.), not just status/direction.
    public let type: String
    public let createdAt: String

    public init(id: String, description: String, amount: Double, currency: String, status: String, isOutgoing: Bool, type: String = "", createdAt: String = "") {
        self.id = id
        self.description = description
        self.amount = amount
        self.currency = currency
        self.status = status
        self.isOutgoing = isOutgoing
        self.type = type
        self.createdAt = createdAt
    }
}
