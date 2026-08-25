import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real Toss Bank 먼저 이자받는 정기예금 (interest-paid-upfront term deposit) equivalent
// (item 161) -- see NetworkClient.swift's own doc comment. Android's main app already
// has this (UpfrontDepositScreen.kt); bank-mfe got it in item 153. This is the iOS
// port, mirroring Android's own screen shape (list + a separate "new deposit" mode,
// since unlike WeeklySavingsScreen there's no per-deposit detail screen -- everything
// a deposit needs to show fits on its list row, no installments, no early withdrawal).
private let termMonths = 12
private let annualRate = 2.80

private enum UpfrontDepositMode { case list, new }

struct UpfrontDepositScreenView: View {
    var onBack: () -> Void = {}

    @State private var mode: UpfrontDepositMode = .list
    @State private var deposits: [UpfrontDepositDto]?
    @State private var listError: String?

    private func loadDeposits() async {
        do {
            deposits = try await NetworkClient.shared.getUpfrontDeposits().deposits
            listError = nil
        } catch {
            listError = "Could not load your deposits."
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: { mode == .new ? (mode = .list) : onBack() }) {
                    IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body)
                }.accessibilityLabel("Back")
                Spacer()
                Text(mode == .new ? "New 12-month deposit" : "12-Month Deposit").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            switch mode {
            case .new:
                UpfrontDepositCreateView(onCreated: { mode = .list; Task { await loadDeposits() } })
            case .list:
                ScrollView {
                    VStack(alignment: .leading, spacing: 10) {
                        Button(action: { mode = .new }) {
                            Text("+ Open 12-month deposit").bold().foregroundColor(.white)
                                .frame(maxWidth: .infinity).padding(.vertical, 14)
                                .background(IDS.Colors.brand).cornerRadius(10)
                        }

                        if let listError {
                            VStack(alignment: .leading, spacing: 10) {
                                Text(listError).foregroundColor(.red)
                                Button("Retry") { Task { await loadDeposits() } }.foregroundColor(IDS.Colors.brand)
                            }
                            // Real fix (2026-08-24, flat-design sweep): dropped the Card
                            // wrapper -- a lone error state.
                            .padding(.vertical, 10)
                        } else if let deposits {
                            if deposits.isEmpty {
                                Text("No deposits yet. Open one and get the full \(termMonths) months' interest (\(annualRate, specifier: "%.2f")% per year) paid to your main account immediately -- the principal stays locked for the full term.")
                                    .font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
                            } else {
                                ForEach(deposits) { deposit in
                                    UpfrontDepositRow(deposit: deposit, onChanged: { Task { await loadDeposits() } })
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
        .task { await loadDeposits() }
    }
}

private struct UpfrontDepositRow: View {
    let deposit: UpfrontDepositDto
    let onChanged: () -> Void

    @State private var withdrawing = false
    @State private var error: String?

    private var statusLabel: String {
        if deposit.status == "MATURED" && deposit.withdrawnAt != nil { return "Withdrawn" }
        if deposit.status == "MATURED" { return "Matured" }
        return "Locked"
    }
    private var statusColor: Color {
        if deposit.status == "MATURED" && deposit.withdrawnAt != nil { return IDS.Colors.textSecondary }
        if deposit.status == "MATURED" { return .green }
        return IDS.Colors.brand
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("\(formatMoney(deposit.principal)) RWF").font(.title3).bold()
                Spacer()
                Text(statusLabel).font(.caption).bold().foregroundColor(statusColor)
            }
            Text("Interest paid upfront: \(formatMoney(deposit.interestPaid)) RWF at \(deposit.interestRate, specifier: "%.2f")%/yr")
                .font(.subheadline).foregroundColor(.green)
            Text(deposit.status == "ACTIVE" ? "Locked until \(formatDate(deposit.maturesAt))" : "Matured \(formatDate(deposit.maturesAt))")
                .font(.caption).foregroundColor(IDS.Colors.textSecondary)
            if let error { Text(error).font(.caption).foregroundColor(.red) }
            if deposit.status == "MATURED" && deposit.withdrawnAt == nil {
                Button(action: { Task { await withdraw() } }) {
                    Text(withdrawing ? "Working…" : "Withdraw to main account").bold().foregroundColor(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(IDS.Colors.brand).cornerRadius(10)
                }
                .disabled(withdrawing)
                .padding(.top, 4)
            }
        }
        // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper -- matches
        // Android's identical UpfrontDepositRow conversion, a history log of deposits.
        .padding(.vertical, 10)
        Divider().overlay(IDS.Colors.divider)
    }

    private func withdraw() async {
        withdrawing = true
        defer { withdrawing = false }
        do {
            _ = try await NetworkClient.shared.withdrawUpfrontDeposit(id: deposit.id)
            onChanged()
        } catch {
            self.error = "Could not withdraw this deposit."
        }
    }
}

private struct UpfrontDepositCreateView: View {
    let onCreated: () -> Void

    @State private var principalText = ""
    @State private var submitting = false
    @State private var error: String?

    private var previewInterest: Double? {
        guard let amount = Double(principalText.trimmingCharacters(in: .whitespaces)) else { return nil }
        return amount * annualRate / 100
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Unlike a regular fixed deposit, you get the full \(termMonths) months' interest paid to your main account the moment you open this -- not at maturity. In exchange, the principal is locked for the full \(termMonths) months with no early withdrawal.")
                .font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
            TextField("Deposit amount (RWF)", text: $principalText)
                .keyboardType(.decimalPad)
                .padding(12).background(IDS.Colors.backgroundPrimary).cornerRadius(10)
            if let previewInterest {
                Text("You'll receive \(formatMoney(previewInterest)) RWF immediately").font(.subheadline).bold().foregroundColor(.green)
            }
            Spacer()
            if let error { Text(error).font(.subheadline).foregroundColor(.red) }
            Button(action: { Task { await submit() } }) {
                Text(submitting ? "Working…" : "Open deposit").bold().foregroundColor(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 14)
                    .background(IDS.Colors.brand).cornerRadius(10)
            }
            .disabled(submitting)
        }
        .padding(IDS.Layout.screenHorizontal)
    }

    private func submit() async {
        guard let amount = Double(principalText.trimmingCharacters(in: .whitespaces)), amount > 0 else {
            error = "Enter a real deposit amount."
            return
        }
        submitting = true
        defer { submitting = false }
        do {
            _ = try await NetworkClient.shared.openUpfrontDeposit(OpenUpfrontDepositRequest(principal: amount))
            error = nil
            onCreated()
        } catch {
            self.error = "Could not open this deposit."
        }
    }
}

private func formatDate(_ iso: String) -> String { String(iso.prefix(10)) }

private func formatMoney(_ value: Double) -> String {
    let rounded = (value * 100).rounded() / 100
    let formatter = NumberFormatter()
    formatter.numberStyle = .decimal
    formatter.groupingSeparator = ","
    formatter.usesGroupingSeparator = true
    if rounded == rounded.rounded(.down) {
        formatter.maximumFractionDigits = 0
        return formatter.string(from: NSNumber(value: rounded)) ?? String(Int(rounded))
    }
    formatter.minimumFractionDigits = 2
    formatter.maximumFractionDigits = 2
    return formatter.string(from: NSNumber(value: rounded)) ?? String(format: "%.2f", rounded)
}
