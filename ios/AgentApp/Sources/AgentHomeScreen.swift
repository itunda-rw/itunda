import SwiftUI
import CoreDesignSystem

private enum TransactionMode: Equatable {
    case cashIn
    case cashOut
    case countTill
}

/// Mirrors Android agentapp's own AgentHomeScreen.kt exactly: a till summary, an
/// action picker (receive cash / pay cash / end-of-day count), and a collapsible
/// recent-activity list.
struct AgentHomeScreen: View {
    let onLogout: () -> Void

    @State private var action: TransactionMode?
    @State private var till: TillDto?
    @State private var activity: [ActivityDto] = []
    @State private var showingActivity = false
    @State private var error: String?
    @State private var loading = true

    var body: some View {
        Group {
            if let selected = action {
                CashOperationScreen(
                    mode: selected,
                    onBack: { action = nil },
                    onCompleted: { action = nil; Task { await refresh() } }
                )
            } else {
                homeBody
            }
        }
        .task { await refresh() }
    }

    private var homeBody: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    VStack(alignment: .leading) {
                        Text(till?.agentName ?? "Your agent store").font(.title2).bold()
                        Text("Today's cash desk").font(.subheadline).foregroundColor(.secondary)
                    }
                    Spacer()
                    Button("Sign out") {
                        AgentKeychainTokenStore.shared.clear()
                        onLogout()
                    }
                }

                if let till {
                    TillSummary(till: till)
                }

                ActionPicker(onAction: { action = $0 })

                if let error {
                    Text(error).foregroundColor(.red).font(.footnote)
                }

                Button(action: { showingActivity.toggle() }) {
                    Text(showingActivity ? "Hide today's activity" : "View today's activity")
                        .frame(maxWidth: .infinity)
                }
                .padding(.vertical, 8)

                if showingActivity {
                    Text(loading ? "Refreshing…" : "Today's activity").font(.headline)
                    if !loading && activity.isEmpty {
                        Text("No store transactions recorded yet.").foregroundColor(.secondary)
                    }
                    ForEach(activity.prefix(5)) { entry in
                        ActivityCard(entry: entry)
                    }
                }
            }
            .padding(16)
        }
    }

    private func refresh() async {
        do {
            till = try await AgentNetworkClient.shared.till().till
            activity = try await AgentNetworkClient.shared.activity().activity
            error = nil
        } catch {
            self.error = "Could not refresh store data. Check the connection and try again."
        }
        loading = false
    }
}

extension ActivityDto: Identifiable {}

private struct ActivityCard: View {
    let entry: ActivityDto

    var body: some View {
        let cashIn = entry.type == "CASH_IN"
        VStack(alignment: .leading, spacing: 4) {
            Text(cashIn ? "Cash in completed" : "Cash out completed").font(.subheadline).bold()
            Text("\(cashIn ? "+" : "−") RWF \(formattedRWF(entry.amount)) · Receipt \(entry.receiptNumber)")
            Text(entry.createdAt).font(.caption).foregroundColor(.secondary)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemBackground))
        .cornerRadius(12)
    }
}

private struct TillSummary: View {
    let till: TillDto

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Cash expected in till").font(.subheadline).foregroundColor(.secondary)
            Text("RWF \(formattedRWF(till.expectedCash))").font(.largeTitle).bold()
            Text("Today: RWF \(formattedRWF(till.todayCashIn)) in · RWF \(formattedRWF(till.todayCashOut)) out")
                .font(.subheadline).foregroundColor(.secondary)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemBackground))
        .cornerRadius(12)
    }
}

private struct ActionPicker: View {
    let onAction: (TransactionMode) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("What do you need to do?").font(.title3).bold()
            Button(action: { onAction(.cashIn) }) {
                Text("Receive cash from customer").bold().foregroundColor(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 14)
                    .background(IDS.Colors.brand).cornerRadius(12)
            }
            Button(action: { onAction(.cashOut) }) {
                Text("Pay cash to customer").bold().foregroundColor(IDS.Colors.brand)
                    .frame(maxWidth: .infinity).padding(.vertical, 14)
                    .background(IDS.Colors.brand.opacity(0.15)).cornerRadius(12)
            }
            Button(action: { onAction(.countTill) }) {
                Text("End-of-day cash count").frame(maxWidth: .infinity)
            }
            .padding(.top, 4)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemBackground))
        .cornerRadius(12)
    }
}

private struct CashOperationScreen: View {
    let mode: TransactionMode
    let onBack: () -> Void
    let onCompleted: () -> Void

    @State private var account = ""
    @State private var amount = ""
    @State private var receipt = ""
    @State private var code = ""
    @State private var payoutChecked = false
    @State private var message: String?
    @State private var successMessage: String?
    @State private var busy = false

    private var title: String {
        switch mode {
        case .cashIn: return "Receive cash"
        case .cashOut: return "Pay cash"
        case .countTill: return "Cash count"
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(title).font(.title2).bold()
                Spacer()
                Button("Back", action: onBack).disabled(busy)
            }

            if mode == .countTill {
                IdsTextField("Counted cash (RWF)", text: $amount, keyboardType: .numberPad)
            } else {
                IdsTextField("Customer account number", text: $account)
                IdsTextField("Amount (RWF)", text: $amount, keyboardType: .numberPad)
                IdsTextField("Store receipt number", text: $receipt)
                if mode == .cashOut {
                    IdsTextField("Customer withdrawal code", text: $code)
                        .textInputAutocapitalization(.characters)
                        .onChange(of: code) { code = $0.uppercased() }
                    HStack(alignment: .top, spacing: 6) {
                        Button(action: { payoutChecked.toggle() }) {
                            Image(systemName: payoutChecked ? "checkmark.square.fill" : "square")
                        }
                        .disabled(busy)
                        Text("I checked the code and counted the cash. Do not hand over cash until Itunda confirms.")
                            .font(.footnote)
                    }
                }
            }

            if let message {
                Text(message).foregroundColor(.red).font(.footnote)
            }
            if let successMessage {
                Text(successMessage).foregroundColor(IDS.Colors.brand).font(.footnote)
            }

            Button(action: { Task { await submit() } }) {
                Text(busy ? "Submitting…" : confirmLabel)
                    .bold().foregroundColor(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 14)
                    .background(IDS.Colors.brand).cornerRadius(12)
            }
            .disabled(busy)
        }
        .padding(20)
    }

    private var confirmLabel: String {
        switch mode {
        case .cashIn: return "Confirm cash received"
        case .cashOut: return "Confirm cash paid"
        case .countTill: return "Submit count"
        }
    }

    private func submit() async {
        // Real, whole-RWF-only validation, mirroring Android's exact same rule
        // (a fractional RWF amount is never real in this backend).
        guard let numericAmount = Double(amount), numericAmount.truncatingRemainder(dividingBy: 1) == 0, numericAmount > 0 else {
            message = "RWF amounts must be whole numbers."
            return
        }
        if mode != .countTill, (account.trimmingCharacters(in: .whitespaces).isEmpty || receipt.trimmingCharacters(in: .whitespaces).isEmpty) {
            message = "Complete all required fields and confirm the cash check."
            return
        }
        if mode == .cashOut {
            let validWithdrawalCode = code.range(of: "^[0-9A-F]{12}$", options: .regularExpression) != nil
            if !validWithdrawalCode {
                message = "Enter the 12-character withdrawal code exactly as shown to the customer."
                return
            }
            if !payoutChecked {
                message = "Complete all required fields and confirm the cash check."
                return
            }
        }

        busy = true
        message = nil
        successMessage = nil
        defer { busy = false }
        do {
            switch mode {
            case .cashIn:
                _ = try await AgentNetworkClient.shared.cashIn(CashInRequest(accountNumber: account.trimmingCharacters(in: .whitespaces), amount: numericAmount, receiptNumber: receipt.trimmingCharacters(in: .whitespaces)))
            case .cashOut:
                _ = try await AgentNetworkClient.shared.cashOut(CashOutRequest(accountNumber: account.trimmingCharacters(in: .whitespaces), amount: numericAmount, receiptNumber: receipt.trimmingCharacters(in: .whitespaces), authorizationCode: code.trimmingCharacters(in: .whitespaces)))
            case .countTill:
                _ = try await AgentNetworkClient.shared.submitTillCount(TillCountRequest(countedCash: numericAmount))
            }
            successMessage = {
                switch mode {
                case .cashIn: return "Cash in confirmed. The customer balance has been updated."
                case .cashOut: return "Cash out confirmed. You can now hand the cash to the customer."
                case .countTill: return "Till count submitted for supervisor review."
                }
            }()
            account = ""; amount = ""; receipt = ""; code = ""; payoutChecked = false
            onCompleted()
        } catch {
            message = "Transaction was not completed. Check the details; do not give cash until confirmation succeeds."
        }
    }
}

func formattedRWF(_ amount: Double) -> String {
    let formatter = NumberFormatter()
    formatter.numberStyle = .decimal
    formatter.maximumFractionDigits = 0
    formatter.groupingSeparator = ","
    return formatter.string(from: NSNumber(value: amount)) ?? "\(Int(amount))"
}
