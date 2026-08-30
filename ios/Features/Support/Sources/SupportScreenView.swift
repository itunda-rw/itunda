import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real customer support tickets (2026-07-22) -- found fully built on the backend
// (rw.itunda.support) with zero client UI anywhere. A ticket is always tied to a
// specific transaction (see SupportTicket.kt's own doc comment for why), so this
// screen has the user pick one from their real transaction history rather than
// filing a free-floating complaint. Extracted out of App/Sources'
// OverviewLoansCreditScoreScreens.swift (2026-08-30) into its own FeatureSupport
// module -- same real, sourced Toss one-feature-per-module precedent applied to
// Certificate/Identity's own extraction (toss.tech/article/slash23-iOS).
private let supportCategories = ["GENERAL", "PAYMENT_DISPUTE", "ACCOUNT_TAKEOVER"]

public struct SupportScreenView: View {
    public var onBack: () -> Void
    public init(onBack: @escaping () -> Void = {}) { self.onBack = onBack }

    @State private var tickets: [SupportTicketDto]?
    @State private var transactions: [TransactionDto] = []
    @State private var error: String?
    @State private var busy = false
    @State private var showNewForm = false
    @State private var selectedTransactionId: String?
    @State private var category = supportCategories[0]
    @State private var descriptionText = ""

    public var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Back")
                Spacer()
                Text("Support").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let error { Text(error).font(.caption).foregroundColor(.red) }
                    if !showNewForm {
                        Button(action: { showNewForm = true }) {
                            Text("Report an issue with a transaction").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(12).background(IDS.Colors.brand).cornerRadius(10)
                        }
                    } else {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Which transaction?").font(.caption).bold()
                            ForEach(transactions.prefix(10), id: \.id) { tx in
                                HStack {
                                    Text("\(tx.description) · \(tx.currency) \(Int(tx.amount))").font(.subheadline)
                                    Spacer()
                                    Image(systemName: selectedTransactionId == tx.id ? "largecircle.fill.circle" : "circle")
                                }
                                .onTapGesture { selectedTransactionId = tx.id }
                            }
                            Text("Category").font(.caption).bold()
                            HStack {
                                ForEach(supportCategories, id: \.self) { c in
                                    Button(c) { category = c }.font(.caption).bold()
                                        .foregroundColor(category == c ? .white : IDS.Colors.textPrimary)
                                        .padding(.horizontal, 8).padding(.vertical, 6)
                                        .background(category == c ? IDS.Colors.brand : IDS.Colors.chipBackground).cornerRadius(8)
                                }
                            }
                            IdsTextField("Describe the issue", text: $descriptionText)
                            Button(action: { Task { await submit() } }) {
                                Text(busy ? "Submitting…" : "Submit ticket").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(12).background(IDS.Colors.brand).cornerRadius(10)
                            }
                            .disabled(busy || selectedTransactionId == nil || descriptionText.isEmpty)
                        }
                        // Real fix (2026-08-24, flat-design sweep): dropped this Card and the
                        // per-row Card below (kept its per-row Divider -- a real status log of
                        // past tickets), same pattern as IdentityScreenView above
                        // (docs/UI_UX_GUIDELINES.md §10 / docs/DESIGN_REFERENCES.md §274).
                        .padding(.vertical, 10)
                    }

                    Divider().overlay(IDS.Colors.divider)
                    Text("Your tickets").bold()
                    if let tickets {
                        if tickets.isEmpty { Text("You have no support tickets.").font(.caption).foregroundColor(IDS.Colors.textSecondary) }
                        ForEach(tickets) { t in
                            VStack(alignment: .leading, spacing: 4) {
                                Text(t.category).bold()
                                Text(t.description).font(.subheadline)
                                Text("Status: \(t.status)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                if let resolution = t.resolution { Text("Resolution: \(resolution)").font(.caption).foregroundColor(IDS.Colors.textSecondary) }
                                Text("Filed: \(t.createdAt)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            }
                            .padding(.vertical, 10)
                            Divider().overlay(IDS.Colors.divider)
                        }
                    } else { ProgressView() }
                }
                .padding(IDS.Layout.screenHorizontal)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await refresh() }
    }

    private func refresh() async {
        do {
            tickets = try await NetworkClient.shared.getSupportTickets().tickets
            transactions = try await NetworkClient.shared.getTransactionHistory().transactions
            error = nil
        } catch { self.error = "Could not load support tickets." }
    }

    private func submit() async {
        guard let transactionId = selectedTransactionId else { return }
        busy = true; error = nil
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.createSupportTicket(transactionId: transactionId, category: category, description: descriptionText)
            showNewForm = false; selectedTransactionId = nil; descriptionText = ""
            await refresh()
        } catch { self.error = "That ticket could not be submitted." }
    }
}
