import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real Toss Bank 체크카드 (check/debit card) screen (2026-07-31, item 207) --
// bank-mfe/Android shipped first; this is the iOS client. See backend DebitCard.kt's
// own doc comment: itunda has no real card-network partnership, so "paying with your
// card" below is itunda's own honest, ledger-backed simulation of a card-present
// purchase. Same no-ViewModel, "call NetworkClient.shared directly from Task {} blocks"
// convention as YouthAccountScreenView.swift.
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
    // Real "카드 비밀번호 변경" (change card PIN) inline form (2026-09-01, direct
    // user-supplied Toss Bank card-management screenshots) -- matches web/Android's
    // identical setCardPin flow.
    @State private var showPinForm = false
    @State private var newPinInput = ""
    @State private var pinPasswordInput = ""
    @State private var pinError: String?
    @State private var pinSuccess = false
    // Real gap closed 2026-09-07 (Card product-completeness pass): bank-mfe's
    // BankDashboard.tsx has had a "Card benefits" section (live credit-score
    // card-usage factor + suggestion) since it was built; Android got it in the
    // same pass; iOS never did, despite already having getCreditScore()/
    // getCreditScoreSuggestions() wired elsewhere in the app -- this is UI wiring,
    // no new network code.
    @State private var cardUsageFactor: CreditScoreFactorDto?
    @State private var cardSuggestion: CreditScoreSuggestionDto?

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body)
                }.accessibilityLabel("Back")
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
                        // Real Toss Bank "which color do you like?" issuance step --
                        // direct user instruction 2026-08-27: "update itunda bank
                        // with all those cards designs allowing users to choose from
                        // those designs... that's how toss does it too". See
                        // CardDesignPicker's own doc comment for the full sourced
                        // account (this replaced the earlier single-design mockup).
                        CardDesignPicker(busy: busy, onIssue: issue)
                    case .active:
                        if let card {
                            // Real per-design colors (2026-08-27, direct user
                            // instruction: "update itunda bank with all those cards
                            // designs allowing users to choose from those designs") --
                            // the issued card renders the finish this account
                            // actually chose, not one hardcoded brand gradient. Text
                            // stays dark on Frost Onyx's light front, matching
                            // bank-mfe/Android's identical contrast fix.
                            let cardDesign = CardDesigns.byId(card.design)
                            let onFront: Color = (!card.frozen && cardDesign.frontLight) ? Color(hex: 0x191F28) : .white
                            VStack(alignment: .leading, spacing: 6) {
                                // Real EMV chip + tap-to-pay silhouette (2026-08-26,
                                // direct user instruction: "all cards designs
                                // should resemble real card") -- see
                                // BankCardChip's own doc comment.
                                HStack {
                                    BankCardChip(size: 32)
                                    Spacer()
                                    if card.frozen {
                                        LockGlyph(size: 18)
                                    } else {
                                        CardContactlessGlyph(size: 18, color: onFront.opacity(0.85))
                                    }
                                }
                                Spacer()
                                Text("itunda card").font(.caption).foregroundColor(onFront.opacity(0.85))
                                Text("•••• •••• •••• \(card.last4)").font(.title3).bold().foregroundColor(onFront)
                                Text(cardStatusLabel(card)).font(.caption).foregroundColor(onFront.opacity(0.85))
                            }
                            .padding(20)
                            .aspectRatio(1.586, contentMode: .fit)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(
                                ZStack {
                                    card.frozen ? Color.gray : cardDesign.front
                                    // Diagonal sheen -- the same "flat color read
                                    // as a card" fix applied to every card-shaped
                                    // visual in this app.
                                    LinearGradient(colors: [Color.white.opacity(0.18), Color.clear], startPoint: .topLeading, endPoint: .bottomTrailing)
                                }
                            )
                            .cornerRadius(16)

                            if card.lost || card.closedAt != nil {
                                CardActionButton(title: "Get a new card", disabled: busy, action: reissue)
                            } else {
                                CardActionButton(title: card.frozen ? "Unfreeze card" : "Freeze card", disabled: busy, action: toggleFreeze)
                            }

                            VStack(alignment: .leading, spacing: 8) {
                                Button(action: { showPinForm.toggle() }) {
                                    HStack {
                                        Text(card.pinSet ? "Change card PIN" : "Set card PIN").font(.subheadline).foregroundColor(IDS.Colors.textPrimary)
                                        Spacer()
                                    }
                                }
                                .disabled(busy || card.closedAt != nil)
                                if showPinForm {
                                    Text("A real 4-digit card PIN, separate from your login password. Confirm your current login password to change it.")
                                        .font(.caption2).foregroundColor(IDS.Colors.textTertiary)
                                    if let pinError {
                                        Text(pinError).font(.caption).foregroundColor(.red)
                                    }
                                    IdsTextField("New 4-digit PIN", text: $newPinInput, isSecure: true, keyboardType: .numberPad)
                                    IdsTextField("Current login password", text: $pinPasswordInput, isSecure: true)
                                    CardActionButton(title: busy ? "…" : "Save PIN", disabled: busy, action: setPin)
                                }
                            }
                            .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(16)
                            if pinSuccess {
                                Text("Card PIN saved.").font(.caption).foregroundColor(.green)
                            }
                            Button(action: reportLost) {
                                HStack {
                                    Text(card.lost ? "Reported lost or stolen" : "Report lost or stolen").font(.subheadline).foregroundColor(IDS.Colors.textPrimary)
                                    Spacer()
                                }
                            }
                            .disabled(busy || card.lost || card.closedAt != nil)
                            .padding(.horizontal, 4)
                            Button(action: closeCardAction) {
                                HStack {
                                    Text(card.closedAt != nil ? "Card closed" : "Close card").font(.subheadline).foregroundColor(card.closedAt != nil ? IDS.Colors.textSecondary : .red)
                                    Spacer()
                                }
                            }
                            .disabled(busy || card.closedAt != nil)
                            .padding(.horizontal, 4)

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
                                CardActionButton(title: payButtonLabel(card, busy: busy), disabled: busy || card.frozen, action: charge)
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

                            if cardUsageFactor != nil || cardSuggestion != nil {
                                VStack(alignment: .leading, spacing: 6) {
                                    Text("Card benefits").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                                    if let factor = cardUsageFactor {
                                        HStack {
                                            Text(factor.description).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                            Spacer()
                                            Text("+\(factor.points) credit score").font(.caption).bold().foregroundColor(.green)
                                        }
                                    }
                                    if let suggestion = cardSuggestion {
                                        HStack {
                                            Text(suggestion.description).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                            Spacer()
                                            Text("+\(suggestion.pointsGain) more").font(.caption).bold().foregroundColor(IDS.Colors.brand)
                                        }
                                    }
                                }
                                .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(16)
                            }
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
            cardUsageFactor = try? await NetworkClient.shared.getCreditScore().factors.first { $0.name == "Card usage" }
            cardSuggestion = try? await NetworkClient.shared.getCreditScoreSuggestions().suggestions.first {
                $0.action == "Use your itunda Card more" || $0.action == "Get an itunda Card"
            }
        }
    }

    private func issue(design: String) {
        busy = true
        error = nil
        Task {
            do {
                _ = try await NetworkClient.shared.issueCard(design: design)
                busy = false
                load()
            } catch let NetworkError.httpErrorWithCode(statusCode, _, _) where statusCode == 409 {
                // CARD_ALREADY_ISSUED in practice (matches Android's identical
                // CardScreen.kt fix, 2026-08-15) -- the account genuinely already has
                // a card. Resolve forward: load it instead of a dead-end error.
                // Pattern updated 2026-09-05 to httpErrorWithCode since issueCard
                // switched from authenticatedPostWithMessage to
                // authenticatedPostWithCode (see feedback_idempotency_key_sweep
                // memory) for Idempotency-Key support.
                busy = false
                load()
            } catch let NetworkError.httpErrorWithCode(_, _, message) {
                self.error = message ?? "Could not issue a card."
                busy = false
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
            } catch let NetworkError.httpErrorWithMessage(_, message) {
                self.error = message ?? "Could not update your card."
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
            } catch let NetworkError.httpErrorWithMessage(_, message) {
                self.error = message ?? "Could not update your limits."
            } catch {
                self.error = "Could not update your limits."
            }
            busy = false
        }
    }

    // Real "분실신고" (report lost or stolen) -- a distinct, one-way backend state
    // now exists, closing the gap web/Android's identical CardView already closed
    // this session.
    private func reportLost() {
        guard let current = card, !current.lost, current.closedAt == nil else { return }
        busy = true
        error = nil
        Task {
            do {
                card = try await NetworkClient.shared.reportCardLost().card
            } catch let NetworkError.httpErrorWithMessage(_, message) {
                self.error = message ?? "Could not update your card."
            } catch {
                self.error = "Could not update your card."
            }
            busy = false
        }
    }

    // Real "카드 해지하기" (close card) -- a deliberate, one-way retirement
    // distinct from a lost/stolen report; only reissue() below can recover from
    // either.
    private func closeCardAction() {
        guard let current = card, current.closedAt == nil else { return }
        busy = true
        error = nil
        Task {
            do {
                card = try await NetworkClient.shared.closeCard().card
            } catch let NetworkError.httpErrorWithMessage(_, message) {
                self.error = message ?? "Could not close your card."
            } catch {
                self.error = "Could not close your card."
            }
            busy = false
        }
    }

    // Real "카드 재발급" (reissue) -- the real recovery path from a lost/stolen
    // or closed card; regenerates last4 and clears the old PIN in place.
    private func reissue() {
        busy = true
        error = nil
        Task {
            do {
                card = try await NetworkClient.shared.reissueCard().card
            } catch let NetworkError.httpErrorWithMessage(_, message) {
                self.error = message ?? "Could not reissue your card."
            } catch {
                self.error = "Could not reissue your card."
            }
            busy = false
        }
    }

    private func setPin() {
        guard newPinInput.count == 4, newPinInput.allSatisfy({ $0.isNumber }) else {
            pinError = "Your card PIN must be exactly 4 digits."
            return
        }
        guard !pinPasswordInput.isEmpty else {
            pinError = "Enter your current login password."
            return
        }
        busy = true
        pinError = nil
        pinSuccess = false
        Task {
            do {
                card = try await NetworkClient.shared.setCardPin(newPin: newPinInput, currentCredential: pinPasswordInput).card
                newPinInput = ""
                pinPasswordInput = ""
                showPinForm = false
                pinSuccess = true
            } catch let NetworkError.httpErrorWithMessage(_, message) {
                pinError = message ?? "Could not update your card PIN."
            } catch {
                pinError = "Could not update your card PIN."
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
            } catch let NetworkError.httpErrorWithMessage(_, message) {
                chargeMessage = message ?? "Could not complete this purchase."
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

private func cardStatusLabel(_ card: CardDto) -> String {
    if card.closedAt != nil { return "Closed" }
    if card.lost { return "Reported lost or stolen" }
    if card.frozen { return "Frozen" }
    return "✓ Active"
}

private func payButtonLabel(_ card: CardDto, busy: Bool) -> String {
    if card.closedAt != nil { return "Card is closed" }
    if card.lost { return "Card reported lost" }
    if card.frozen { return "Card is frozen" }
    return busy ? "Paying…" : "Pay"
}

