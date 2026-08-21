import SwiftUI
import CoreDesignSystem

/// Real Toss Bank reference (20 screenshots, 2026-08-21, direct user instruction:
/// "should look 100% like in this images pixels by pixels"): the real ledger
/// drill-in, reached by tapping AccountSummaryCard's balance -- structurally
/// parallel to bank-mfe's AccountDetailScreen.tsx and Android's own
/// AccountDetailScreen, all three built the same session from the same real
/// reference. Send is bottom-pinned (not scrolling with the ledger above it) per
/// the same live follow-up ("those buttons at bottom") that corrected the first
/// pass, which had the ledger folded directly into BankView. Each row carries a
/// real category icon (classified from the row's own title text, not a
/// fabricated per-merchant logo) matching Android's identical ledgerRowIcon fix
/// and bank-mfe's identical AccountDetailScreen.tsx one.
public struct AccountLedgerDetailView: View {
    let balanceText: String
    let accountNumber: String?
    let transactions: [RecentTransactionRowData]
    let onBack: () -> Void
    let onSend: () -> Void

    public init(balanceText: String, accountNumber: String?, transactions: [RecentTransactionRowData], onBack: @escaping () -> Void, onSend: @escaping () -> Void) {
        self.balanceText = balanceText
        self.accountNumber = accountNumber
        self.transactions = transactions
        self.onBack = onBack
        self.onSend = onSend
    }

    public var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .font(IDS.scaledFont(size: 18, weight: .medium, relativeTo: .body))
                        .foregroundColor(IDS.Colors.textPrimary)
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel("Back")
                Spacer()
            }
            .padding(.horizontal, 8)

            ScrollView {
                VStack(alignment: .leading, spacing: 6) {
                    if let accountNumber {
                        Text("itunda \(accountNumber.chunked(4).joined(separator: "-"))")
                            .font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote))
                            .foregroundColor(IDS.Colors.textTertiary)
                    }
                    Text(balanceText)
                        .font(IDS.scaledFont(size: 32, weight: .bold, relativeTo: .largeTitle))
                        .foregroundColor(IDS.Colors.textPrimary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 24)
                .padding(.top, 4)

                Spacer().frame(height: 24)

                if transactions.isEmpty {
                    Text("No transactions yet")
                        .font(IDS.scaledFont(size: 15, weight: .regular, relativeTo: .subheadline))
                        .foregroundColor(IDS.Colors.textTertiary)
                        .padding(.top, 24)
                } else {
                    VStack(spacing: 0) {
                        ForEach(transactions) { tx in
                            AccountLedgerDetailRow(tx: tx)
                            Divider()
                        }
                    }
                    .padding(.horizontal, 24)
                }
            }

            Divider()
            Button(action: onSend) {
                Text("Send")
                    .font(IDS.scaledFont(size: 16, weight: .semibold, relativeTo: .body))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
                    .background(IDS.Colors.brand)
                    .cornerRadius(999)
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 12)
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .navigationBarHidden(true)
    }
}

private struct AccountLedgerDetailRow: View {
    let tx: RecentTransactionRowData

    var body: some View {
        let category = ledgerRowIcon(for: tx.title)
        HStack(spacing: 12) {
            Circle()
                .fill(category.color)
                .frame(width: 38, height: 38)
                .overlay(
                    Image(systemName: category.symbol)
                        .font(IDS.scaledFont(size: 16, weight: .regular, relativeTo: .callout))
                        .foregroundColor(.white)
                )
            VStack(alignment: .leading, spacing: 2) {
                Text(tx.title).font(IDS.scaledFont(size: 14, weight: .semibold, relativeTo: .callout)).foregroundColor(IDS.Colors.textPrimary)
                Text(tx.subtitle).font(IDS.scaledFont(size: 12, weight: .regular, relativeTo: .footnote)).foregroundColor(IDS.Colors.textTertiary)
            }
            Spacer()
            Text(tx.amountText)
                .font(IDS.scaledFont(size: 14, weight: .semibold, relativeTo: .callout))
                .foregroundColor(tx.isOutgoing ? IDS.Colors.textPrimary : .blue)
        }
        .padding(.vertical, 10)
    }
}

/// Real category classification (2026-08-21) -- see AccountLedgerDetailView's own
/// header comment. Description keyword first (real itunda-specific products the
/// title already names), falling back to a generic transfer symbol for anything
/// not specifically named -- this plain display model has no separate
/// TransactionType field to fall back on the way Android's/bank-mfe's identical
/// classifiers do (RecentTransactionRowData only carries title/subtitle/
/// amountText/isOutgoing), so unclassified rows get the same honest generic
/// transfer icon rather than a guessed-wrong specific one.
private func ledgerRowIcon(for title: String) -> (symbol: String, color: Color) {
    let d = title.lowercased()
    if d.contains("ride") { return ("car.fill", .blue) }
    if d.contains("eats") || d.contains("booking") { return ("fork.knife", .orange) }
    if d.contains("gift") { return ("gift.fill", .purple) }
    if d.contains("escrow") || d.contains("marketplace") { return ("bag.fill", .teal) }
    if d.contains("cashback") { return ("percent", .orange) }
    if d.contains("interest") || d.contains("savings") { return ("building.columns.fill", .blue) }
    if d.contains("ussd") { return ("phone.fill", .purple) }
    if d.contains("bill") { return ("doc.text.fill", .orange) }
    if d.contains("airtime") { return ("simcard.fill", .purple) }
    if d.contains("loan") { return ("wallet.pass.fill", .blue) }
    return ("arrow.left.arrow.right", IDS.Colors.textSecondary)
}
