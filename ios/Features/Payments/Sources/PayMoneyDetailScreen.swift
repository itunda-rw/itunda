import SwiftUI
import CoreDesignSystem

/// Real "Toss Pay Money" detail/statement screen (user screenshots, 2026-08-21):
/// reached by drilling into the balance row on MyPaymentCodeCard (App target,
/// ShopPay.swift), structurally parallel to this same module's TransactionHistoryScreen
/// but scoped to one specific account's own balance + transactions rather than card
/// spend. Deliberately flat throughout -- no card wrapper around the balance, the
/// Send/Add money buttons, or the transaction rows, matching both the real reference
/// screenshot and a direct 2026-08-21 user instruction to move itunda's designs toward
/// flat over card-heavy, same as bank-mfe's PayMoneyDetail.tsx / Android's
/// PayMoneyDetailScreen.kt. Takes plain values, not the App target's Account/
/// TransactionDto directly -- same module-dependency-direction constraint as
/// TransactionHistoryScreen's own TransactionDisplayItem (the App target maps the real
/// network DTOs into that plain model before calling in here).
public struct PayMoneyDetailScreen: View {
    let currency: String
    let balance: Double
    let transactions: [TransactionDisplayItem]?
    let errorMessage: String?
    let onBack: () -> Void
    let onSend: () -> Void
    let onAddMoney: () -> Void

    @State private var paymentsOnly = false

    public init(
        currency: String, balance: Double, transactions: [TransactionDisplayItem]?, errorMessage: String?,
        onBack: @escaping () -> Void, onSend: @escaping () -> Void, onAddMoney: @escaping () -> Void
    ) {
        self.currency = currency
        self.balance = balance
        self.transactions = transactions
        self.errorMessage = errorMessage
        self.onBack = onBack
        self.onSend = onSend
        self.onAddMoney = onAddMoney
    }

    private var visible: [TransactionDisplayItem] {
        (transactions ?? []).filter { !paymentsOnly || $0.type == "PAYMENT" }
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
                VStack(alignment: .leading, spacing: 0) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("itunda Pay Money")
                            .font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .subheadline))
                            .foregroundColor(IDS.Colors.textSecondary)
                        Text("\(currency) \(formatAmount(Int(balance)))")
                            .font(IDS.scaledFont(size: 32, weight: .bold, relativeTo: .largeTitle))
                            .foregroundColor(IDS.Colors.textPrimary)
                    }
                    .padding(.top, 4)

                    HStack(spacing: 12) {
                        IdsButton(text: "Send", action: onSend)
                        IdsButton(text: "Add money", action: onAddMoney)
                    }
                    .frame(height: 48)
                    .padding(.top, 20)
                    .padding(.bottom, 20)

                    // Real flat section break (matches the reference's own visual break
                    // between the balance/buttons and the statement, no card wrapper).
                    // The screen background itself is already IDS.Colors.backgroundPrimary
                    // (a neutral grey, see IDS.swift), so a filled block here would be
                    // invisible -- a plain system divider is the honest equivalent.
                    Divider()
                        .padding(.bottom, 16)

                    HStack {
                        Text(monthLabel)
                            .font(IDS.scaledFont(size: 14, weight: .bold, relativeTo: .subheadline))
                            .foregroundColor(IDS.Colors.textPrimary)
                        Spacer()
                        Text("See payment history only")
                            .font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote))
                            .foregroundColor(IDS.Colors.textSecondary)
                        Toggle("", isOn: $paymentsOnly)
                            .labelsHidden()
                    }
                    .padding(.bottom, 12)

                    if let errorMessage {
                        Text(errorMessage)
                            .font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote))
                            .foregroundColor(.red)
                    } else if transactions == nil {
                        HStack { Spacer(); ProgressView(); Spacer() }
                            .padding(.vertical, 24)
                    } else if visible.isEmpty {
                        VStack(spacing: 8) {
                            Text("No details found")
                                .font(IDS.scaledFont(size: 15, weight: .regular, relativeTo: .subheadline))
                                .foregroundColor(IDS.Colors.textTertiary)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 48)
                    } else {
                        VStack(spacing: 0) {
                            ForEach(visible) { tx in
                                PayMoneyLedgerRow(tx: tx)
                                Divider()
                            }
                        }
                    }
                }
                .padding(.horizontal, 24)
                .padding(.top, 4)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }

    private var monthLabel: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "MMMM"
        return formatter.string(from: Date())
    }
}

private struct PayMoneyLedgerRow: View {
    let tx: TransactionDisplayItem

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(tx.description)
                    .font(IDS.scaledFont(size: 14, weight: .semibold, relativeTo: .callout))
                    .foregroundColor(IDS.Colors.textPrimary)
                Text(tx.status.capitalized)
                    .font(IDS.scaledFont(size: 12, weight: .regular, relativeTo: .footnote))
                    .foregroundColor(IDS.Colors.textTertiary)
            }
            Spacer()
            Text("\(tx.isOutgoing ? "-" : "+")\(formatAmount(Int(tx.amount))) \(tx.currency)")
                .font(IDS.scaledFont(size: 14, weight: .semibold, relativeTo: .callout))
                .foregroundColor(tx.isOutgoing ? IDS.Colors.textPrimary : .blue)
        }
        .padding(.vertical, 10)
    }
}

private func formatAmount(_ value: Int) -> String {
    let formatter = NumberFormatter()
    formatter.numberStyle = .decimal
    formatter.groupingSeparator = ","
    return formatter.string(from: NSNumber(value: value)) ?? "0"
}
