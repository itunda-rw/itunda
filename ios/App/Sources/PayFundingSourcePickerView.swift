import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real QR/FacePay funding-source picker (itunda Pay redesign, 2026-08-28, direct
// user reference: real Toss Pay "Facepay · QR Payment" bottom sheet, Recent/
// Account/Card tabs). Additive to the existing swipeable AccountCardCarousel in
// MyPaymentCodeCard.swift, not a replacement. "Card" tab routes to Card
// management rather than letting you select itunda's own card as a funding
// source -- CardService.chargeWithCard is a structurally separate ledger path
// from the QR/FacePay MerchantService.collect flow this code funds (confirmed
// via a full backend grep before building this: no LinkedCard/ExternalCard
// concept exists either).
private enum PickerTab: String, CaseIterable { case recent = "Recent", account = "Account", card = "Card" }

struct PayFundingSourcePickerView: View {
    let accounts: [Account]
    let selectedAccountId: String?
    let onSelectAccount: (String) -> Void
    let onOpenCard: () -> Void

    @State private var tab: PickerTab = .recent
    @State private var linkedAccounts: [LinkedAccountDto] = []
    @State private var hasCard: Bool?
    @State private var cardLast4: String?
    @State private var cardFrozen = false
    @Environment(\.dismiss) private var dismiss

    private var selected: Account? {
        accounts.first(where: { $0.id == selectedAccountId }) ?? accounts.first
    }

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                Picker("", selection: $tab) {
                    ForEach(PickerTab.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, 20).padding(.top, 12)

                Group {
                    switch tab {
                    case .recent:
                        if let selected {
                            Button(action: { onSelectAccount(selected.id); dismiss() }) {
                                accountRow(selected, showCheck: true)
                            }
                            .buttonStyle(.plain)
                        }
                    case .account:
                        ScrollView {
                            VStack(alignment: .leading, spacing: 0) {
                                ForEach(accounts, id: \.id) { account in
                                    Button(action: { onSelectAccount(account.id); dismiss() }) {
                                        accountRow(account, showCheck: account.id == selectedAccountId)
                                    }
                                    .buttonStyle(.plain)
                                    Divider()
                                }
                                if !linkedAccounts.isEmpty {
                                    Text("Linked accounts").font(.caption).foregroundColor(IDS.Colors.textSecondary).padding(.top, 12)
                                    // Real, deliberately non-selectable -- an external
                                    // linked account is a real, honest demo-balance
                                    // display, never a real funding source
                                    // MerchantService.collect can actually debit.
                                    ForEach(linkedAccounts) { linked in
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(linked.provider).font(.subheadline).bold()
                                            Text(linked.externalAccountNumberMasked).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                        }
                                        .padding(.vertical, 8)
                                    }
                                }
                            }
                            .padding(.horizontal, 20)
                        }
                    case .card:
                        VStack {
                            if hasCard == nil {
                                ProgressView()
                            } else if hasCard == true {
                                Button(action: { dismiss(); onOpenCard() }) {
                                    HStack {
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text("Card •••• \(cardLast4 ?? "")").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                                            Text(cardFrozen ? "Frozen" : "Active").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                        }
                                        Spacer()
                                    }
                                }
                                .buttonStyle(.plain)
                            } else {
                                VStack(spacing: 10) {
                                    Text("No card yet").font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
                                    Button(action: { dismiss(); onOpenCard() }) {
                                        Text("Get a card").font(.caption).bold()
                                    }
                                }
                                .frame(maxWidth: .infinity)
                                .padding(20)
                                .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(IDS.Colors.divider, style: StrokeStyle(lineWidth: 1, dash: [4, 3])))
                            }
                        }
                        .padding(.horizontal, 20)
                    }
                }
                Spacer()
            }
            .navigationTitle("Choose how to pay")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Close") { dismiss() }
                }
            }
        }
        .task {
            linkedAccounts = (try? await NetworkClient.shared.getLinkedAccounts().linkedAccounts.filter { $0.status == "LINKED" }) ?? []
            do {
                let card = try await NetworkClient.shared.getMyCard().card
                hasCard = true
                cardLast4 = card.last4
                cardFrozen = card.frozen
            } catch {
                hasCard = false
            }
        }
    }

    private func accountLabel(_ account: Account) -> String {
        account.type == "PAY" ? "itunda Pay" : account.type == "MAIN" ? "itunda Bank" : "itunda Pay \(account.currency)"
    }

    @ViewBuilder
    private func accountRow(_ account: Account, showCheck: Bool) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(accountLabel(account)).font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                Text("\(account.currency) \(Int(account.balance))").font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
            Spacer()
            if showCheck {
                Image(systemName: "checkmark").foregroundColor(IDS.Colors.brand).bold()
            }
        }
        .padding(.vertical, 12)
        .padding(.horizontal, showCheck && tab == .recent ? 20 : 0)
        .contentShape(Rectangle())
    }
}
