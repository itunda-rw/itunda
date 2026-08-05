import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real Toss Bank 체크카드 (check/debit card) screen (2026-07-31, item 207) --
// bank-mfe/Android shipped first; this is the iOS client. See backend DebitCard.kt's
// own doc comment: itunda has no real card-network partnership, so "paying with your
// card" below is itunda's own honest, ledger-backed simulation of a card-present
// purchase. Same no-ViewModel, "call NetworkClient.shared directly from Task {} blocks"
// convention as MiniWalletScreenView.swift.
private enum CardMode {
    case loading, noCard, active
}

struct CardScreenView: View {
    var onBack: () -> Void = {}
    @State private var mode: CardMode = .loading
    @State private var card: CardDto?
    @State private var transactions: [CardTransactionDto] = []
    @State private var dailyLimitInput = ""
    @State private var monthlyLimitInput = ""
    @State private var merchantName = ""
    @State private var chargeAmount = ""
    @State private var busy = false
    @State private var error: String?
    @State private var chargeMessage: String?

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left").foregroundColor(IDS.Colors.textPrimary)
                }
                Spacer()
                Text("Card").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let error {
                        Text(error).font(.footnote).foregroundColor(.red)
                    }
                    switch mode {
                    case .loading:
                        ProgressView().frame(maxWidth: .infinity).padding(40)
                    case .noCard:
                        Text("App-controlled spend limits and one-tap freeze -- no branch visit, no waiting.")
                            .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        CardActionButton(title: busy ? "Issuing…" : "Get your itunda card", disabled: busy, action: issue)
                    case .active:
                        if let card {
                            VStack(alignment: .leading, spacing: 6) {
                                Text("itunda card").font(.caption).foregroundColor(.white.opacity(0.85))
                                Text("•••• •••• •••• \(card.last4)").font(.title3).bold().foregroundColor(.white)
                                Text(card.frozen ? "🔒 Frozen" : "✓ Active").font(.caption).foregroundColor(.white.opacity(0.85))
                            }
                            .padding(20)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(card.frozen ? Color.gray : IDS.Colors.brand)
                            .cornerRadius(16)

                            CardActionButton(title: card.frozen ? "Unfreeze card" : "Freeze card", disabled: busy, action: toggleFreeze)

                            VStack(alignment: .leading, spacing: 8) {
                                Text("Spend limits").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                                Text("Today: \(formatMoney(card.spentToday)) / \(formatMoney(card.dailyLimit)) RWF").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                Text("This month: \(formatMoney(card.spentThisMonth)) / \(formatMoney(card.monthlyLimit)) RWF").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                HStack(spacing: 8) {
                                    IdsTextField("Daily limit", text: $dailyLimitInput, keyboardType: .numberPad)
                                    IdsTextField("Monthly limit", text: $monthlyLimitInput, keyboardType: .numberPad)
                                }
                                CardActionButton(title: "Save limits", disabled: busy, action: saveLimits)
                            }
                            .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(16)

                            VStack(alignment: .leading, spacing: 8) {
                                Text("Pay with your card").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                                Text("itunda has no real card-network partnership yet, so this simulates a real card-present purchase -- real money moves, real limits apply.")
                                    .font(.caption2).foregroundColor(IDS.Colors.textTertiary)
                                if let chargeMessage {
                                    Text(chargeMessage).font(.caption).foregroundColor(chargeMessage.hasPrefix("Paid") ? .green : .red)
                                }
                                IdsTextField("Merchant name", text: $merchantName)
                                IdsTextField("Amount (RWF)", text: $chargeAmount, keyboardType: .numberPad)
                                CardActionButton(title: card.frozen ? "Card is frozen" : (busy ? "Paying…" : "Pay"), disabled: busy || card.frozen, action: charge)
                            }
                            .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(16)

                            VStack(alignment: .leading, spacing: 6) {
                                Text("Recent card activity").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                                if transactions.isEmpty {
                                    EmptyStateView("No card purchases yet — once you use your card, they'll show up here.")
                                } else {
                                    ForEach(transactions) { t in
                                        HStack {
                                            Text(t.merchantName).font(.subheadline).foregroundColor(IDS.Colors.textPrimary)
                                            Spacer()
                                            Text("\(formatMoney(t.amount)) RWF").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                                        }
                                    }
                                }
                            }
                            .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(16)
                        }
                    }
                }
                .padding(.horizontal)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { load() }
    }

    private func load() {
        Task {
            do {
                let res = try await NetworkClient.shared.getMyCard()
                card = res.card
                dailyLimitInput = String(Int(res.card.dailyLimit))
                monthlyLimitInput = String(Int(res.card.monthlyLimit))
                mode = .active
            } catch NetworkError.httpError(let statusCode) where statusCode == 404 {
                mode = .noCard
            } catch {
                self.error = "Could not load your card."
            }
            if let transactionsRes = try? await NetworkClient.shared.getCardTransactions() {
                transactions = transactionsRes.transactions
            }
        }
    }

    private func issue() {
        busy = true
        error = nil
        Task {
            do {
                _ = try await NetworkClient.shared.issueCard()
                busy = false
                load()
            } catch {
                self.error = "Could not issue a card."
                busy = false
            }
        }
    }

    private func toggleFreeze() {
        guard let current = card else { return }
        busy = true
        error = nil
        Task {
            do {
                let res = current.frozen ? try await NetworkClient.shared.unfreezeCard() : try await NetworkClient.shared.freezeCard()
                card = res.card
            } catch {
                self.error = "Could not update your card."
            }
            busy = false
        }
    }

    private func saveLimits() {
        guard let daily = Double(dailyLimitInput), let monthly = Double(monthlyLimitInput), daily > 0, monthly > 0 else {
            error = "Enter real, positive limits."
            return
        }
        busy = true
        error = nil
        Task {
            do {
                let res = try await NetworkClient.shared.setCardLimits(dailyLimit: daily, monthlyLimit: monthly)
                card = res.card
            } catch {
                self.error = "Could not update your limits."
            }
            busy = false
        }
    }

    private func charge() {
        guard let parsedAmount = Double(chargeAmount), parsedAmount > 0, !merchantName.isEmpty else {
            chargeMessage = "Enter a real merchant name and amount."
            return
        }
        busy = true
        chargeMessage = nil
        Task {
            do {
                let res = try await NetworkClient.shared.chargeCard(amount: parsedAmount, merchantName: merchantName)
                card = res.card
                chargeMessage = "Paid \(formatMoney(res.transaction.amount)) RWF at \(res.transaction.merchantName)"
                merchantName = ""
                chargeAmount = ""
                load()
            } catch {
                chargeMessage = "Could not complete this purchase."
            }
            busy = false
        }
    }
}

private struct CardActionButton: View {
    let title: String
    let disabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title).bold().foregroundColor(.white)
                .frame(maxWidth: .infinity).padding(.vertical, 14)
                .background(disabled ? Color.gray : IDS.Colors.brand).cornerRadius(12)
        }
        .disabled(disabled)
    }
}

private func formatMoney(_ value: Double) -> String {
    let rounded = (value * 100).rounded() / 100
    return rounded == rounded.rounded(.down) ? String(Int64(rounded)) : String(format: "%.2f", rounded)
}
