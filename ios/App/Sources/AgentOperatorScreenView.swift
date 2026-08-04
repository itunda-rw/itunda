import SwiftUI
import CoreDesignSystem
import CoreNetwork

/// Real Itunda cash-agent operator console -- see AgentOperatorController.kt's own doc
/// comment: "Store-facing API: the operator's JWT determines the agent; callers never
/// supply an agent id." Distinct from the customer-facing withdrawal-code creation --
/// this is the STAFF side, real till balance + real cash-in/cash-out + real till
/// reconciliation. bank-mfe/Android already have this; this is the first iOS client.
struct AgentOperatorScreenView: View {
    var onBack: () -> Void = {}

    @State private var till: AgentTillSnapshotDto?
    @State private var activity: [AgentActivityItemDto] = []
    @State private var notOperator = false
    @State private var error: String?
    @State private var message: String?

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { Image(systemName: "chevron.left").foregroundColor(IDS.Colors.textPrimary) }
                Spacer()
                Text("Agent till").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            if notOperator {
                Text("You are not assigned as an Itunda agent till operator. Ask an Itunda staff admin to assign your account to a store.")
                    .font(.footnote).foregroundColor(IDS.Colors.textSecondary)
                    .padding()
                Spacer()
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        if let error { Text(error).font(.caption).foregroundColor(.red) }
                        if let message { Text(message).font(.caption).foregroundColor(IDS.Colors.brand) }
                        if let till {
                            TillSummaryCard(till: till)
                            CashInCard(onSubmitted: { balance in message = "Cash in accepted — new customer balance \(Int(balance)) RWF"; Task { await load() } }, onError: { error = $0 })
                            CashOutCard(onSubmitted: { balance in message = "Cash out paid — new customer balance \(Int(balance)) RWF"; Task { await load() } }, onError: { error = $0 })
                            TillCountCard(onSubmitted: { variance, status in message = "Till count submitted — variance \(Int(variance)) RWF (\(status))"; Task { await load() } }, onError: { error = $0 })

                            Text("Recent activity").bold()
                            if activity.isEmpty {
                                EmptyStateView("No cash movements yet today.")
                            } else {
                                ForEach(activity) { a in
                                    HStack {
                                        Text(a.type == "CASH_IN" ? "↓ Cash in · \(a.receiptNumber)" : "↑ Cash out · \(a.receiptNumber)").font(.footnote)
                                        Spacer()
                                        Text("\(Int(a.amount)) RWF").bold().font(.footnote)
                                    }
                                }
                            }
                        } else {
                            ProgressView()
                        }
                    }
                    .padding(IDS.Layout.screenHorizontal)
                }
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await load() }
    }

    private func load() async {
        error = nil
        do {
            till = try await NetworkClient.shared.getAgentTill().till
            notOperator = false
        } catch NetworkError.agentOperatorNotAuthorized {
            notOperator = true
        } catch {
            self.error = "Could not load your till."
        }
        activity = (try? await NetworkClient.shared.getAgentActivity())?.activity ?? []
    }
}

private struct TillSummaryCard: View {
    let till: AgentTillSnapshotDto

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(till.agentName).font(.caption).foregroundColor(IDS.Colors.textSecondary)
            Text("\(Int(till.expectedCash)) RWF expected in till").font(.title2).bold()
            Text("Today: \(Int(till.todayCashIn)) RWF in · \(Int(till.todayCashOut)) RWF out").font(.caption).foregroundColor(IDS.Colors.textSecondary)
            if let r = till.reconciliation {
                Text("Last count: \(Int(r.countedCash)) RWF (\(r.status), variance \(Int(r.variance)))").font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
    }
}

private struct CashInCard: View {
    let onSubmitted: (Double) -> Void
    let onError: (String) -> Void

    @State private var account = ""
    @State private var amount = ""
    @State private var receipt = ""
    @State private var busy = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Accept cash-in").bold()
            IdsTextField("Customer account number", text: $account)
            IdsTextField("Amount (RWF)", text: $amount, keyboardType: .decimalPad)
            IdsTextField("Receipt number", text: $receipt)
            Button(action: { Task { await submit() } }) {
                Text(busy ? "Working…" : "Accept cash-in").bold().foregroundColor(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                    .background(IDS.Colors.brand).cornerRadius(10)
            }
            .disabled(busy)
        }
        .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
    }

    private func submit() async {
        guard let value = Double(amount), value > 0, !account.trimmingCharacters(in: .whitespaces).isEmpty, !receipt.trimmingCharacters(in: .whitespaces).isEmpty else {
            onError("Enter a real account number, receipt number, and a positive amount.")
            return
        }
        busy = true
        defer { busy = false }
        do {
            let result = try await NetworkClient.shared.agentCashIn(AgentCashInRequest(accountNumber: account.trimmingCharacters(in: .whitespaces), amount: value, receiptNumber: receipt.trimmingCharacters(in: .whitespaces)))
            account = ""; amount = ""; receipt = ""
            onSubmitted(result.newBalance)
        } catch {
            onError("Could not accept this cash-in.")
        }
    }
}

private struct CashOutCard: View {
    let onSubmitted: (Double) -> Void
    let onError: (String) -> Void

    @State private var account = ""
    @State private var amount = ""
    @State private var receipt = ""
    @State private var code = ""
    @State private var busy = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Pay cash-out").bold()
            IdsTextField("Customer account number", text: $account)
            IdsTextField("Amount (RWF)", text: $amount, keyboardType: .decimalPad)
            IdsTextField("Receipt number", text: $receipt)
            IdsTextField("Customer's withdrawal code", text: $code)
            Button(action: { Task { await submit() } }) {
                Text(busy ? "Working…" : "Pay cash-out").bold().foregroundColor(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                    .background(IDS.Colors.brand).cornerRadius(10)
            }
            .disabled(busy)
        }
        .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
    }

    private func submit() async {
        guard let value = Double(amount), value > 0,
              !account.trimmingCharacters(in: .whitespaces).isEmpty,
              !receipt.trimmingCharacters(in: .whitespaces).isEmpty,
              !code.trimmingCharacters(in: .whitespaces).isEmpty else {
            onError("Enter a real account number, receipt number, withdrawal code, and a positive amount.")
            return
        }
        busy = true
        defer { busy = false }
        do {
            let result = try await NetworkClient.shared.agentCashOut(AgentCashOutRequest(accountNumber: account.trimmingCharacters(in: .whitespaces), amount: value, receiptNumber: receipt.trimmingCharacters(in: .whitespaces), authorizationCode: code.trimmingCharacters(in: .whitespaces)))
            account = ""; amount = ""; receipt = ""; code = ""
            onSubmitted(result.newBalance)
        } catch {
            onError("Could not pay this cash-out. Check the withdrawal code.")
        }
    }
}

private struct TillCountCard: View {
    let onSubmitted: (Double, String) -> Void
    let onError: (String) -> Void

    @State private var counted = ""
    @State private var busy = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Submit today's till count").bold()
            IdsTextField("Counted cash (RWF)", text: $counted, keyboardType: .decimalPad)
            Button(action: { Task { await submit() } }) {
                Text(busy ? "Working…" : "Submit count").bold()
                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                    .background(Color(.secondarySystemBackground)).cornerRadius(10)
            }
            .disabled(busy)
        }
        .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
    }

    private func submit() async {
        guard let value = Double(counted), value >= 0 else {
            onError("Enter a real counted-cash amount.")
            return
        }
        busy = true
        defer { busy = false }
        do {
            let result = try await NetworkClient.shared.submitAgentTillCount(value).reconciliation
            counted = ""
            onSubmitted(result.variance, result.status)
        } catch {
            onError("Could not submit this till count.")
        }
    }
}
