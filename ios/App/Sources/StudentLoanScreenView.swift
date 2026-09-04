import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real gap found live (2026-08-31, market-readiness audit): itunda Bank iOS had zero
// client for the real BRD (Development Bank of Rwanda) higher-education student loan --
// backend + bank-mfe + Android all shipped 2026-08-04, iOS never got one. See
// StudentLoanDto's own doc comment for the full sourced account (Rwanda's real
// student-loan-and-bursary scheme since Law No. 44/2015, 11%/12% undergraduate/
// postgraduate rates, a real 6-12 month grace period after graduation, honest v1
// limitation that the 8%-of-income repayment is only ever suggested, never
// auto-deducted). Mirrors bank-mfe's StudentLoanView.tsx and Android's
// StudentLoanScreen.kt exactly. Also mirrors VupLoanScreenView.swift's own established
// iOS conventions for this same "Loans"-adjacent screen family: no ViewModel, calling
// NetworkClient.shared directly from Task {} blocks.
struct StudentLoanScreenView: View {
    var onBack: () -> Void = {}

    @State private var loans: [StudentLoanDto]?
    @State private var error: String?
    @State private var busyId: String?
    @State private var suggestedPayments: [String: StudentLoanSuggestedPaymentResponse] = [:]

    @State private var level = "UNDERGRADUATE"
    @State private var declaredIncome = ""
    @State private var amount = ""
    @State private var graduationDate = Date().addingTimeInterval(365 * 24 * 3600)

    private let levels: [(String, String)] = [("UNDERGRADUATE", "Undergraduate"), ("POSTGRADUATE", "Postgraduate")]

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Back")
                Spacer()
                Text("BRD Student Loan").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                VStack(spacing: 12) {
                    Text("Rwanda's real BRD higher-education student loan -- 11% undergraduate / 12% postgraduate, with a grace period after graduation before repayment starts. Declared household income is self-declared, not verified against BRD's real Financial Means Testing process.")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    if let error {
                        Text(error).font(.footnote).foregroundColor(.red)
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        Text("Apply for a new loan").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                        Text("Level").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        HStack(spacing: 8) {
                            ForEach(levels, id: \.0) { value, label in
                                Button(action: { level = value }) {
                                    Text(label).font(.footnote).bold()
                                        .foregroundColor(level == value ? .white : IDS.Colors.textPrimary)
                                        .padding(.horizontal, 12).padding(.vertical, 10)
                                        .background(level == value ? IDS.Colors.brand : Color(.tertiarySystemBackground))
                                        .cornerRadius(10)
                                }
                            }
                        }
                        IdsTextField("Declared annual household income (RWF)", text: $declaredIncome, keyboardType: .numberPad)
                        IdsTextField("Loan amount (RWF, up to 2,000,000)", text: $amount, keyboardType: .numberPad)
                        DatePicker("Expected graduation date", selection: $graduationDate, in: Date()..., displayedComponents: .date)
                            .font(.caption)
                        Button(action: { Task { await apply() } }) {
                            Text(busyId == "apply" ? "Applying…" : "Apply").bold().foregroundColor(.white)
                                .frame(maxWidth: .infinity).padding(.vertical, 12)
                                .background(IDS.Colors.brand).cornerRadius(10)
                        }
                        .disabled(busyId != nil || !((Double(declaredIncome) ?? 0) > 0) || !((Double(amount) ?? 0) > 0))
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)

                    VStack(alignment: .leading, spacing: 8) {
                        Text("My student loans").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                        if let loans {
                            if loans.isEmpty {
                                EmptyStateView("No student loans yet.")
                            } else {
                                ForEach(loans) { loan in loanRow(loan) }
                            }
                        } else {
                            ProgressView()
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
                }
                .padding(.horizontal)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await load() }
    }

    @ViewBuilder
    private func loanRow(_ loan: StudentLoanDto) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("\(formatMoneyStudentLoan(loan.principalAmount)) RWF · \(loan.level.capitalized)").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Text(loan.status).font(.caption).foregroundColor(loan.status == "OVERDUE" ? .red : IDS.Colors.textSecondary)
            }
            Text("Outstanding: \(formatMoneyStudentLoan(loan.outstandingBalance)) RWF" + (loan.graceEndsAt.map { " · Grace ends \(String($0.prefix(10)))" } ?? ""))
                .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
            if loan.status == "REQUESTED" {
                Text("Demo: instantly approved -- stands in for the real BRD/MINEDUC approval step.")
                    .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                Button(action: { Task { await disburse(loan.id) } }) {
                    Text(busyId == loan.id ? "…" : "Disburse").bold().font(.footnote).foregroundColor(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                        .background(IDS.Colors.brand).cornerRadius(8)
                }
                .disabled(busyId != nil)
            }
            if loan.status == "DISBURSED" {
                Text("Demo: declare graduation to end the grace period on this loan.")
                    .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                Button(action: { Task { await declareGraduated(loan.id) } }) {
                    Text(busyId == loan.id ? "…" : "Declare graduated").bold().font(.footnote).foregroundColor(IDS.Colors.textPrimary)
                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                        .background(Color(.tertiarySystemBackground)).cornerRadius(8)
                }
                .disabled(busyId != nil)
            }
            if loan.status == "REPAYING" || loan.status == "OVERDUE" {
                if let suggested = suggestedPayments[loan.id] {
                    Text(suggested.note).font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                    Text("Suggested: \(formatMoneyStudentLoan(suggested.suggestedMonthlyPayment)) RWF/month").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                }
                IdsTextField("Repayment amount (RWF)", text: Binding(
                    get: { repayAmounts[loan.id] ?? "" },
                    set: { repayAmounts[loan.id] = $0 }
                ), keyboardType: .numberPad)
                Button(action: { Task { await repay(loan.id) } }) {
                    Text(busyId == loan.id ? "…" : "Repay").bold().font(.footnote).foregroundColor(IDS.Colors.textPrimary)
                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                        .background(Color(.tertiarySystemBackground)).cornerRadius(8)
                }
                .disabled(busyId != nil)
            }
        }
        .padding(12).background(Color(.tertiarySystemBackground)).cornerRadius(10)
        .task(id: loan.id) { await loadSuggestedPayment(loan) }
    }

    @State private var repayAmounts: [String: String] = [:]
    private static let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    private func load() async {
        do {
            loans = try await NetworkClient.shared.getMyStudentLoans().loans
            error = nil
        } catch {
            self.error = "Could not load your student loans."
        }
    }

    private func loadSuggestedPayment(_ loan: StudentLoanDto) async {
        guard loan.status == "REPAYING" || loan.status == "OVERDUE" else { return }
        guard let suggested = try? await NetworkClient.shared.getStudentLoanSuggestedPayment(loanId: loan.id) else { return }
        suggestedPayments[loan.id] = suggested
    }

    private func apply() async {
        guard let income = Double(declaredIncome), income > 0 else {
            error = "Enter a valid declared household income."
            return
        }
        guard let value = Double(amount), value > 0 else {
            error = "Enter a valid loan amount."
            return
        }
        busyId = "apply"
        do {
            _ = try await NetworkClient.shared.applyForStudentLoan(
                level: level,
                declaredAnnualHouseholdIncome: income,
                amount: value,
                expectedGraduationDate: Self.dateFormatter.string(from: graduationDate)
            )
            declaredIncome = ""
            amount = ""
            error = nil
            await load()
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            self.error = message ?? "Could not apply for this student loan."
        } catch {
            self.error = "Could not apply for this student loan."
        }
        busyId = nil
    }

    private func disburse(_ loanId: String) async {
        busyId = loanId
        do {
            _ = try await NetworkClient.shared.disburseStudentLoan(loanId: loanId)
            error = nil
            await load()
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            self.error = message ?? "Could not disburse this loan."
        } catch {
            self.error = "Could not disburse this loan."
        }
        busyId = nil
    }

    private func declareGraduated(_ loanId: String) async {
        busyId = loanId
        do {
            _ = try await NetworkClient.shared.declareStudentLoanGraduated(loanId: loanId)
            error = nil
            await load()
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            self.error = message ?? "Could not declare this loan graduated."
        } catch {
            self.error = "Could not declare this loan graduated."
        }
        busyId = nil
    }

    private func repay(_ loanId: String) async {
        guard let value = Double(repayAmounts[loanId] ?? ""), value > 0 else {
            error = "Enter a valid repayment amount."
            return
        }
        busyId = loanId
        do {
            _ = try await NetworkClient.shared.repayStudentLoan(loanId: loanId, amount: value)
            repayAmounts[loanId] = nil
            error = nil
            await load()
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            self.error = message ?? "Could not repay this loan."
        } catch {
            self.error = "Could not repay this loan."
        }
        busyId = nil
    }
}

private func formatMoneyStudentLoan(_ value: Double) -> String {
    let rounded = (value * 100).rounded() / 100
    let formatter = NumberFormatter()
    formatter.numberStyle = .decimal
    formatter.groupingSeparator = ","
    formatter.usesGroupingSeparator = true
    if rounded == rounded.rounded(.down) {
        formatter.maximumFractionDigits = 0
        return formatter.string(from: NSNumber(value: rounded)) ?? String(Int64(rounded))
    }
    formatter.minimumFractionDigits = 2
    formatter.maximumFractionDigits = 2
    return formatter.string(from: NSNumber(value: rounded)) ?? String(format: "%.2f", rounded)
}
