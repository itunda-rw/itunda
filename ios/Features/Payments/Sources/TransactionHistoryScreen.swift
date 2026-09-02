import SwiftUI
import CoreDesignSystem

// TransactionDisplayItem moved to Core/DesignSystem/Sources/Components/
// TransactionDisplayItem.swift (2026-09-02, Pay Feature-module decomposition) --
// see that file's doc comment for why.

/// Real transaction history screen, matching the real Toss card-detail reference
/// screenshot (user-provided, 2026-07-12): spend-this-month total up top, then a
/// real list -- an honest empty state ("No transactions yet") when there's nothing,
/// matching Toss's own "아직 내역이 없어요" rather than inventing fake rows. Backed by
/// services/backend/account's real getTransactionHistory endpoint. No real card
/// issuance/network exists, so this is framed as spend history, not a real card.
public struct TransactionHistoryScreen: View {
    let transactions: [TransactionDisplayItem]
    let onBack: () -> Void

    public init(transactions: [TransactionDisplayItem], onBack: @escaping () -> Void) {
        self.transactions = transactions
        self.onBack = onBack
    }

    private var spentThisMonth: Double {
        transactions.filter { $0.isOutgoing && $0.status == "COMPLETED" }.reduce(0) { $0 + $1.amount }
    }

    public var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    IDS.Icons.back(size: 18, color: IDS.Colors.textPrimary, relativeTo: .body).frame(width: 44, height: 44)
                }
                .accessibilityLabel("Back")
                Spacer()
            }
            .padding(.horizontal, 8)

            VStack(alignment: .leading, spacing: 6) {
                Text("Spent this month")
                    .font(IDS.scaledFont(size: 15, weight: .regular, relativeTo: .subheadline))
                    .foregroundColor(IDS.Colors.textSecondary)
                Text("\(formatAmount(Int(spentThisMonth))) RWF")
                    .font(IDS.scaledFont(size: 32, weight: .bold, relativeTo: .largeTitle))
                    .foregroundColor(IDS.Colors.textPrimary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 24)

            Spacer().frame(height: 24)

            if transactions.isEmpty {
                Spacer()
                Text("No transactions yet")
                    .font(IDS.scaledFont(size: 15, weight: .regular, relativeTo: .subheadline))
                    .foregroundColor(IDS.Colors.textTertiary)
                Spacer()
            } else {
                List(transactions) { tx in
                    TransactionRowView(tx: tx)
                        .listRowBackground(IDS.Colors.backgroundPrimary)
                        .listRowSeparator(.hidden)
                }
                .listStyle(.plain)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }
}

private struct TransactionRowView: View {
    let tx: TransactionDisplayItem

    var body: some View {
        HStack(spacing: 14) {
            Circle()
                .fill(tx.isOutgoing ? IDS.Colors.dangerTint : IDS.Colors.successTint)
                .frame(width: 40, height: 40)
                .overlay(
                    Image(systemName: tx.isOutgoing ? "arrow.up" : "arrow.down")
                        .font(IDS.scaledFont(size: 16, weight: .regular, relativeTo: .callout))
                        .foregroundColor(tx.isOutgoing ? .red : .green)
                )
            VStack(alignment: .leading, spacing: 2) {
                Text(tx.description).font(IDS.scaledFont(size: 16, weight: .semibold, relativeTo: .callout)).foregroundColor(IDS.Colors.textPrimary)
                Text(tx.status.capitalized).font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote)).foregroundColor(IDS.Colors.textTertiary)
            }
            Spacer()
            Text("\(tx.isOutgoing ? "-" : "+")\(formatAmount(Int(tx.amount))) \(tx.currency)")
                .font(IDS.scaledFont(size: 15, weight: .semibold, relativeTo: .subheadline))
                .foregroundColor(tx.isOutgoing ? IDS.Colors.textPrimary : .green)
        }
        .padding(.vertical, 6)
    }
}

private func formatAmount(_ value: Int) -> String {
    let formatter = NumberFormatter()
    formatter.numberStyle = .decimal
    formatter.groupingSeparator = ","
    return formatter.string(from: NSNumber(value: value)) ?? "0"
}
