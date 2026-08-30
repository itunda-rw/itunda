import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real multi-lender loan marketplace (2026-07-22 port, see
// docs/TOSS_PARITY_MATRIX.md's own per-row notes) -- extracted out of App/Sources'
// OverviewLoansCreditScoreScreens.swift (2026-08-30) into FeatureCredit, alongside
// CreditScoreScreenView (same lending/credit domain). See
// project_itunda_feature_isolation's own memory for the extraction order/rationale.

private enum LoansMode: String, CaseIterable { case offers = "Offers", myLoans = "My loans", overdraft = "Overdraft", postpaidCredit = "Postpaid credit" }

public struct LoansScreenView: View {
    public var onBack: () -> Void
    public init(onBack: @escaping () -> Void = {}) { self.onBack = onBack }

    @State private var mode: LoansMode = .offers
    @State private var offers: [LoanOfferDto]?
    @State private var myLoans: [LoanAccountDto]?
    @State private var lenders: [LenderDto]?
    @State private var lenderId: String?
    @State private var error: String?
    @State private var busyId: String?
    @State private var repayAmounts: [String: String] = [:]
    @State private var refinanceResult: RefinanceResult?
    // Real Toss writing-principle adoption ("숨은 감정 찾기" -- find the hidden emotion):
    // toss.tech/article/8-writing-principles-of-toss names a fully-repaid loan as their
    // own example of a moment that deserves more than transactional silence. Paying off
    // a loan just refreshed the list silently before this, even though the repay
    // response already tells us `remaining` hit zero.
    @State private var payoffMessage: String?

    public var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Back")
                Spacer()
                Text("Loans").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            Picker("", selection: $mode) {
                ForEach(LoansMode.allCases, id: \.self) { m in Text(m.rawValue).tag(m) }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal)
            .padding(.bottom, 8)

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let error { Text(error).font(.caption).foregroundColor(.red) }
                    if let payoffMessage { Text(payoffMessage).font(.subheadline).bold().foregroundColor(IDS.Colors.brand) }
                    if let refinanceResult {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Refinanced into \(refinanceResult.newLoanName)").font(.subheadline).bold()
                            Text("\(refinanceResult.oldInterestRate, specifier: "%.1f")% → \(refinanceResult.newInterestRate, specifier: "%.1f")%").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                        // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper --
                        // a lone info banner (docs/UI_UX_GUIDELINES.md §10).
                    }
                    if mode == .offers {
                        if let lenders {
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 6) {
                                    lenderChip(title: "All lenders", selected: lenderId == nil) { Task { await selectLender(nil) } }
                                    ForEach(lenders) { lender in
                                        lenderChip(title: lender.name, selected: lenderId == lender.id) { Task { await selectLender(lender.id) } }
                                    }
                                }
                            }
                        }
                        if let offers {
                            EmptyStateView("No offers from this lender right now.")
                            ForEach(offers) { offer in LoanOfferCard(offer: offer, busy: busyId == offer.id, onApply: { amount in Task { await apply(offer, amount) } }) }
                        } else { ProgressView() }
                    } else if mode == .myLoans {
                        if let myLoans {
                            if myLoans.isEmpty { Text("You have no loans yet.").font(.caption).foregroundColor(IDS.Colors.textSecondary) }
                            // Real fix (2026-08-24, flat-design sweep): dropped the per-row Card --
                            // a status/history log of active loans, kept the per-row Divider
                            // convention (docs/DESIGN_REFERENCES.md §274).
                            ForEach(myLoans) { loan in
                                VStack(alignment: .leading, spacing: 6) {
                                    Text("\(Int(loan.principal)) RWF loan").bold()
                                    Text("Outstanding: \(Int(loan.outstanding)) RWF").font(.subheadline)
                                    Text("Status: \(loan.status) · \(loan.interestRate)%").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                    if loan.status == "ACTIVE" {
                                        IdsTextField("Repay amount (RWF)", text: Binding(
                                            get: { repayAmounts[loan.id] ?? "" },
                                            set: { repayAmounts[loan.id] = $0 }
                                        ), keyboardType: .numberPad)
                                        Button(action: { Task { await repay(loan) } }) {
                                            Text(busyId == loan.id ? "Repaying…" : "Repay").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(10).background(IDS.Colors.brand).cornerRadius(8)
                                        }
                                        .disabled(busyId != nil)
                                        Button(action: { Task { await refinance(loan) } }) {
                                            Text(busyId == loan.id ? "Checking…" : "Refinance to a lower rate").bold().frame(maxWidth: .infinity).padding(10).background(IDS.Colors.chipBackground).cornerRadius(8)
                                        }
                                        .disabled(busyId != nil)
                                    }
                                }
                                .padding(.vertical, 10)
                                Divider().overlay(IDS.Colors.divider)
                            }
                        } else { ProgressView() }
                    } else if mode == .overdraft {
                        OverdraftPanel()
                    } else {
                        PostpaidCreditPanel()
                    }
                }
                .padding(IDS.Layout.screenHorizontal)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await refresh() }
    }

    private func refresh() async {
        do {
            offers = try await NetworkClient.shared.getLoanOffers().offers
            myLoans = try await NetworkClient.shared.getMyLoans().loans
            lenders = try await NetworkClient.shared.getLenders().lenders
            error = nil
        } catch { self.error = "Could not load loans." }
    }

    // Real "browse by lender" filter (2026-07-29 iOS port) -- see the bank-mfe port's own
    // comment: `getLenders`/`lenderId`-filtered `getLoanOffers` were both real backend
    // endpoints with zero client anywhere before this.
    private func selectLender(_ id: String?) async {
        lenderId = id
        error = nil
        do {
            offers = try await NetworkClient.shared.getLoanOffers(lenderId: id).offers
        } catch { self.error = "Could not load offers." }
    }

    private func lenderChip(title: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).font(.caption).bold()
                .foregroundColor(selected ? IDS.Colors.brand : IDS.Colors.textPrimary)
                .padding(.horizontal, 12).padding(.vertical, 6)
                .background(IDS.Colors.chipBackground)
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(selected ? IDS.Colors.brand : .clear, lineWidth: 1))
                .cornerRadius(14)
        }
    }

    private func apply(_ offer: LoanOfferDto, _ amount: Double) async {
        busyId = offer.id; error = nil
        defer { busyId = nil }
        do {
            _ = try await NetworkClient.shared.applyForLoan(loanId: offer.id, amount: amount)
            mode = .myLoans
            await refresh()
        } catch { self.error = "That loan application could not be completed." }
    }

    private func repay(_ loan: LoanAccountDto) async {
        guard let amount = Double(repayAmounts[loan.id] ?? "") else { return }
        busyId = loan.id; error = nil
        defer { busyId = nil }
        do {
            let result = try await NetworkClient.shared.repayLoan(loanId: loan.id, amount: amount)
            repayAmounts[loan.id] = nil
            payoffMessage = result.remaining <= 0 ? "You paid off this loan in full — one less thing to carry." : nil
            await refresh()
        } catch let NetworkError.httpErrorWithCode(_, code, _) where code == "LOAN_ALREADY_PAID" {
            repayAmounts[loan.id] = nil
            await refresh()
        } catch { self.error = "That repayment could not be completed." }
    }

    // Real 대환대출 (loan refinancing, 2026-07-29 iOS port) -- see NetworkClient's own
    // RefinanceLoanRequest comment; bank-mfe-only since 2026-07-26, ported to Android
    // the same session as this iOS port.
    private func refinance(_ loan: LoanAccountDto) async {
        busyId = loan.id; error = nil
        defer { busyId = nil }
        do {
            refinanceResult = try await NetworkClient.shared.refinanceLoan(loanId: loan.id)
            await refresh()
        } catch { self.error = "No better rate is available for this loan right now." }
    }
}

// Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper -- a browsable
// catalog list of loan offers, matching Android's/web's identical no-divider
// treatment for entity/product lists (docs/UI_UX_GUIDELINES.md §10).
private struct LoanOfferCard: View {
    let offer: LoanOfferDto
    let busy: Bool
    let onApply: (Double) -> Void
    @State private var amountText: String
    init(offer: LoanOfferDto, busy: Bool, onApply: @escaping (Double) -> Void) {
        self.offer = offer; self.busy = busy; self.onApply = onApply
        _amountText = State(initialValue: String(Int(offer.maxAmount)))
    }

    var body: some View {
        if busy {
            LoanApplyProgress()
        } else {
            VStack(alignment: .leading, spacing: 6) {
                Text(offer.name).bold()
                Text(offer.lenderName).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                Text("Up to \(Int(offer.maxAmount)) RWF · \(offer.interestRate)% · \(offer.term)").font(.subheadline)
                Text(offer.requirements).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                IdsTextField("Amount (RWF)", text: $amountText, keyboardType: .numberPad)
                Button(action: { if let n = Double(amountText), n > 0 { onApply(n) } }) {
                    Text("Apply").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(10).background(IDS.Colors.brand).cornerRadius(8)
                }
            }
            .padding(.vertical, 10)
        }
    }
}
