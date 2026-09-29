import SwiftUI

// Promoted here from App/Sources/HomeTabContent.swift (2026-09-02, Home
// Feature-module decomposition, iOS parity pass for Android's own Feature-module
// isolation work) -- used by BucketDetailScreen/Grow31SavingsScreenView/
// UpfrontDepositScreen/WeeklySavingsScreenView (all staying in App/Sources) plus
// HomeTabContent (moving to FeatureHome), same "promote a cohesive, zero-coupling
// shared component" pattern Android's own LedgerFormatting/FlatRow promotions
// already established.
public struct TransactionRow: View {
    public let title: String
    public let date: String
    public let amount: String
    public let isNegative: Bool
    // Optional so a read-only usage (a past transaction, say) doesn't need to pass a
    // no-op closure -- PayScreen's merchant rows are the first real, tappable usage.
    public var action: (() -> Void)? = nil

    public init(title: String, date: String, amount: String, isNegative: Bool, action: (() -> Void)? = nil) {
        self.title = title
        self.date = date
        self.amount = amount
        self.isNegative = isNegative
        self.action = action
    }

    public var body: some View {
        let content = HStack {
            Circle()
                .fill(IDS.Colors.chipBackground)
                .frame(width: 40, height: 40)

            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(IDS.scaledFont(size: 16, weight: .semibold, relativeTo: .body))
                    .foregroundColor(IDS.Colors.textPrimary)
                Text(date)
                    .font(IDS.scaledFont(size: 14, weight: .regular, relativeTo: .footnote))
                    .foregroundColor(IDS.Colors.textSecondary)
            }
            Spacer()
            Text(amount)
                .font(IDS.scaledFont(size: 16, weight: .bold, relativeTo: .body))
                .foregroundColor(isNegative ? IDS.Colors.textPrimary : IDS.Colors.brand)
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 12)

        if let action {
            Button(action: action) { content }
                .buttonStyle(.plain)
        } else {
            content
        }
    }
}
