import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real Rwanda VUP (Vision 2020 Umurenge Programme) Financial Services means-tested
// microloan -- sourced beyond this session's usual Toss/Kakao/Naver/Coupang reference
// ecosystems. VUP, run by LODA since 2008, subsidizes microloans for income-generating
// activities (farming, livestock, small business) targeted at households in poorer
// Ubudehe categories (NISR EICV7 2023/24: ~100,000 RWF average loan). Since a real
// 2014-07-29 Cabinet decision, administration moved to Umurenge SACCOs, which set the
// rate at 11% (Rwanda Inspirer: uptake fell after that rate hike). Honest v1
// limitation: Ubudehe category is self-declared by the user, not verified against
// Rwanda's real government Ubudehe household-classification registry. Mirrors
// bank-mfe's VupLoanView exactly, same no-ViewModel, "call NetworkClient.shared
// directly from Task {} blocks" convention HarvestAdvanceScreenView.swift/
// SaccoScreenView.swift already established. Unlike HarvestAdvanceScreenView.swift's
// fixed "repay in full" contract, the backend here clamps an overshooting repay
// amount to the real outstanding balance server-side before touching the ledger, so a
// free-form repay input field (matching bank-mfe's own VupLoanView exactly) is safe
// here.

struct VupLoanScreenView: View {
    var onBack: () -> Void = {}

    @State private var loans: [VupLoanDto]?
    @State private var eligibility: VupLoanEligibilityResponse?
    @State private var error: String?
    @State private var busyId: String?

    @State private var category = 1
    @State private var purpose = "FARMING"
    @State private var amount = ""
    @State private var repayAmounts: [String: String] = [:]

    private let purposes: [(String, String)] = [("FARMING", "Farming"), ("LIVESTOCK", "Livestock"), ("BUSINESS", "Business")]

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { Image(systemName: "chevron.left").foregroundColor(IDS.Colors.textPrimary) }.accessibilityLabel("Back")
                Spacer()
                Text("VUP Financial Services").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                VStack(spacing: 12) {
                    Text("Rwanda's Vision 2020 Umurenge Programme subsidized microloan for farming, livestock, or small business. Ubudehe category is self-declared -- not verified against a real government registry.")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    if let error {
                        Text(error).font(.footnote).foregroundColor(.red)
                    }

                    if let eligibility {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("\(Int(eligibility.interestRate * 100))% interest · Ubudehe categories \(eligibility.minUbudeheCategory)-\(eligibility.maxUbudeheCategory) only")
                                .font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)

                            if !eligibility.canApply {
                                Text("You already have an active VUP loan -- repay it before applying for another.")
                                    .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            } else {
                                Text("Ubudehe category").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                HStack(spacing: 8) {
                                    ForEach([1, 2, 3], id: \.self) { c in
                                        Button(action: { category = c }) {
                                            Text("Category \(c)").font(.footnote).bold()
                                                .foregroundColor(category == c ? .white : IDS.Colors.textPrimary)
                                                .padding(.horizontal, 14).padding(.vertical, 10)
                                                .background(category == c ? IDS.Colors.brand : Color(.tertiarySystemBackground))
                                                .cornerRadius(10)
                                        }
                                    }
                                }
                                Text("Purpose").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                HStack(spacing: 8) {
                                    ForEach(purposes, id: \.0) { value, label in
                                        Button(action: { purpose = value }) {
                                            Text(label).font(.footnote).bold()
                                                .foregroundColor(purpose == value ? .white : IDS.Colors.textPrimary)
                                                .padding(.horizontal, 12).padding(.vertical, 10)
                                                .background(purpose == value ? IDS.Colors.brand : Color(.tertiarySystemBackground))
                                                .cornerRadius(10)
                                        }
                                    }
                                }
                                IdsTextField("Loan amount (RWF, up to \(Int(eligibility.maxAmount)))", text: $amount, keyboardType: .numberPad)
                                Button(action: { Task { await apply() } }) {
                                    Text(busyId == "apply" ? "Applying…" : "Apply").bold().foregroundColor(.white)
                                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                                        .background(IDS.Colors.brand).cornerRadius(10)
                                }
                                .disabled(busyId != nil || !((Double(amount) ?? 0) > 0))
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
                    } else {
                        ProgressView()
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        Text("My VUP loans").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                        if let loans {
                            if loans.isEmpty {
                                EmptyStateView("No VUP loans yet.")
                            } else {
                                ForEach(loans) { loan in
                                    VStack(alignment: .leading, spacing: 6) {
                                        HStack {
                                            Text("\(formatMoneyVup(loan.principalAmount)) RWF · \(loan.purpose)").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                                            Spacer()
                                            Text(loan.status).font(.caption).foregroundColor(loan.status == "OVERDUE" ? .red : IDS.Colors.textSecondary)
                                        }
                                        Text("Outstanding: \(formatMoneyVup(loan.outstandingPrincipal)) RWF" + (loan.dueDate.map { " · Due \(String($0.prefix(10)))" } ?? ""))
                                            .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                                        if loan.status == "REQUESTED" {
                                            Text("Demo: instantly approved -- stands in for the real SACCO officer approval step.")
                                                .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                                            Button(action: { Task { await disburse(loan.id) } }) {
                                                Text(busyId == loan.id ? "…" : "Disburse").bold().font(.footnote).foregroundColor(.white)
                                                    .frame(maxWidth: .infinity).padding(.vertical, 10)
                                                    .background(IDS.Colors.brand).cornerRadius(8)
                                            }
                                            .disabled(busyId != nil)
                                        }
                                        if loan.status == "DISBURSED" || loan.status == "OVERDUE" {
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
                                }
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

    private func load() async {
        do {
            loans = try await NetworkClient.shared.getMyVupLoans().loans
            eligibility = try await NetworkClient.shared.getVupLoanEligibility()
            error = nil
        } catch {
            self.error = "Could not load your VUP loans."
        }
    }

    private func apply() async {
        guard let value = Double(amount), value > 0 else {
            error = "Enter a valid loan amount."
            return
        }
        busyId = "apply"
        do {
            _ = try await NetworkClient.shared.applyForVupLoan(declaredUbudeheCategory: category, purpose: purpose, amount: value)
            amount = ""
            error = nil
            await load()
        } catch {
            self.error = "Could not apply for this VUP loan."
        }
        busyId = nil
    }

    private func disburse(_ loanId: String) async {
        busyId = loanId
        do {
            _ = try await NetworkClient.shared.disburseVupLoan(loanId: loanId)
            error = nil
            await load()
        } catch {
            self.error = "Could not disburse this loan."
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
            _ = try await NetworkClient.shared.repayVupLoan(loanId: loanId, amount: value)
            repayAmounts[loanId] = nil
            error = nil
            await load()
        } catch {
            self.error = "Could not repay this loan."
        }
        busyId = nil
    }
}

private func formatMoneyVup(_ value: Double) -> String {
    let rounded = (value * 100).rounded() / 100
    return rounded == rounded.rounded(.down) ? String(Int64(rounded)) : String(format: "%.2f", rounded)
}
