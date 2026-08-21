import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real Rwanda moto-taxi ownership savings-to-loan plan -- sourced beyond this
// session's usual Toss/Kakao/Naver/Coupang reference ecosystems. A real ~600,000 RWF
// entry-level moto-taxi bike is a documented purchase price (Anadolu Agency, 14 May
// 2021 -- profiles a rider who saved for years to buy her own bike after paying daily
// rent to a bike owner). Rent-to-own is a proven-relevant mechanic in this exact
// sector (Frontier Tech Hub's Kigali e-moto pilot: Ampersand's rent-to-own model
// increased driver revenue 78%/month; WeeTracker/WEF coverage of the same). This
// fills the gap left by Rwanda's dissolved taxi-moto cooperatives (Africa-Press,
// 2026). Honest v1 limitation: once converted to a loan, this is an UNSECURED
// facility -- itunda has no path to a real chattel lien or RURA vehicle-registry
// hold, so it cannot repossess the bike or verify it was actually purchased. Mirrors
// bank-mfe's MotoOwnershipView exactly, same no-ViewModel, "call NetworkClient.shared
// directly from Task {} blocks" convention VupLoanScreenView.swift/
// HarvestAdvanceScreenView.swift already established.

struct MotoOwnershipScreenView: View {
    var onBack: () -> Void = {}

    @State private var plans: [MotoOwnershipPlanDto]?
    @State private var error: String?
    @State private var busyId: String?

    @State private var bikePrice = ""
    @State private var dailyContribution = ""
    @State private var contributeAmounts: [String: String] = [:]
    @State private var repayAmounts: [String: String] = [:]

    private var hasActivePlan: Bool { (plans ?? []).contains { $0.status == "SAVING" || $0.status == "LOAN_ACTIVE" } }
    private var previewDownPayment: Double? {
        guard let price = Double(bikePrice), price > 0 else { return nil }
        return price * 0.3
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { Image(systemName: "chevron.left").foregroundColor(IDS.Colors.textPrimary) }.accessibilityLabel("Back")
                Spacer()
                Text("Moto-Taxi Ownership Plan").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                VStack(spacing: 12) {
                    Text("Save toward a 30% down payment on your own moto-taxi bike (itunda's own down-payment policy), then convert the rest into an unsecured loan. A real entry-level bike costs around 600,000 RWF -- this fills the gap left since Rwanda's taxi-moto cooperatives, which used to help drivers become owner-operators, were dissolved.")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    if let error {
                        Text(error).font(.footnote).foregroundColor(.red)
                    }

                    if let plans {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Moto-Taxi Ownership Plan").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                            if hasActivePlan {
                                Text("You already have an active moto-taxi ownership plan -- complete or cancel it before starting another.")
                                    .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            } else {
                                IdsTextField("Bike price (RWF, 300,000-2,500,000)", text: $bikePrice, keyboardType: .numberPad)
                                IdsTextField("Daily contribution (RWF)", text: $dailyContribution, keyboardType: .numberPad)
                                if let previewDownPayment {
                                    Text("Down payment target (30%): \(formatMoneyMoto(previewDownPayment)) RWF")
                                        .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                                }
                                Button(action: { Task { await create() } }) {
                                    Text(busyId == "create" ? "Creating…" : "Start plan").bold().foregroundColor(.white)
                                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                                        .background(IDS.Colors.brand).cornerRadius(10)
                                }
                                .disabled(busyId != nil)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
                    } else {
                        ProgressView()
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        Text("My moto-taxi ownership plans").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                        if let plans {
                            if plans.isEmpty {
                                EmptyStateView("No moto-taxi ownership plans yet.")
                            } else {
                                ForEach(plans) { plan in
                                    VStack(alignment: .leading, spacing: 6) {
                                        HStack {
                                            Text("\(formatMoneyMoto(plan.bikePrice)) RWF bike").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                                            Spacer()
                                            Text(plan.status).font(.caption).bold().foregroundColor(IDS.Colors.brand)
                                        }
                                        if plan.status == "SAVING" {
                                            Text("Saved \(formatMoneyMoto(plan.savedAmount)) / \(formatMoneyMoto(plan.downPaymentTarget)) RWF down payment")
                                                .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                                            GeometryReader { geo in
                                                ZStack(alignment: .leading) {
                                                    RoundedRectangle(cornerRadius: 3).fill(Color(.tertiarySystemBackground)).frame(height: 6)
                                                    RoundedRectangle(cornerRadius: 3).fill(IDS.Colors.brand)
                                                        .frame(width: geo.size.width * CGFloat(progressPct(for: plan)), height: 6)
                                                }
                                            }
                                            .frame(height: 6)
                                            IdsTextField("Contribution amount (RWF)", text: Binding(
                                                get: { contributeAmounts[plan.id] ?? "" },
                                                set: { contributeAmounts[plan.id] = $0 }
                                            ), keyboardType: .numberPad)
                                            HStack(spacing: 8) {
                                                Button(action: { Task { await contribute(plan.id) } }) {
                                                    Text(busyId == plan.id ? "Saving…" : "Contribute").bold().font(.footnote).foregroundColor(IDS.Colors.textPrimary)
                                                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                                                        .background(Color(.tertiarySystemBackground)).cornerRadius(8)
                                                }
                                                .disabled(busyId != nil)
                                                Button(action: { Task { await cancel(plan.id) } }) {
                                                    Text("Cancel").bold().font(.footnote).foregroundColor(.red)
                                                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                                                        .background(Color(.tertiarySystemBackground)).cornerRadius(8)
                                                }
                                                .disabled(busyId != nil)
                                            }
                                            if plan.savedAmount >= plan.downPaymentTarget {
                                                Text("This releases your full \(formatMoneyMoto(plan.bikePrice)) RWF bike price to your account (your saved down payment plus a new unsecured loan for the rest) -- itunda cannot repossess the bike if you stop repaying.")
                                                    .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                                                Button(action: { Task { await convert(plan.id) } }) {
                                                    Text(busyId == plan.id ? "Converting…" : "Convert to loan").bold().font(.footnote).foregroundColor(.white)
                                                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                                                        .background(IDS.Colors.brand).cornerRadius(8)
                                                }
                                                .disabled(busyId != nil)
                                            }
                                        }
                                        if plan.status == "LOAN_ACTIVE" {
                                            Text("Loan outstanding: \(formatMoneyMoto(plan.loanOutstanding)) RWF")
                                                .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                                            IdsTextField("Repayment amount (RWF)", text: Binding(
                                                get: { repayAmounts[plan.id] ?? "" },
                                                set: { repayAmounts[plan.id] = $0 }
                                            ), keyboardType: .numberPad)
                                            Button(action: { Task { await repay(plan.id) } }) {
                                                Text(busyId == plan.id ? "Repaying…" : "Repay").bold().font(.footnote).foregroundColor(IDS.Colors.textPrimary)
                                                    .frame(maxWidth: .infinity).padding(.vertical, 10)
                                                    .background(Color(.tertiarySystemBackground)).cornerRadius(8)
                                            }
                                            .disabled(busyId != nil)
                                        }
                                        if plan.status == "COMPLETED" {
                                            Text("Paid off -- this bike is now fully yours.")
                                                .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
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

    private func progressPct(for plan: MotoOwnershipPlanDto) -> Double {
        guard plan.downPaymentTarget > 0 else { return 0 }
        return min(1, max(0, plan.savedAmount / plan.downPaymentTarget))
    }

    private func load() async {
        do {
            plans = try await NetworkClient.shared.getMyMotoOwnershipPlans().plans
            error = nil
        } catch {
            self.error = "Could not load your moto-taxi ownership plans."
        }
    }

    private func create() async {
        guard let price = Double(bikePrice), price > 0 else {
            error = "Enter a valid bike price."
            return
        }
        guard let contribution = Double(dailyContribution), contribution > 0 else {
            error = "Enter a valid daily contribution."
            return
        }
        busyId = "create"
        do {
            _ = try await NetworkClient.shared.createMotoOwnershipPlan(bikePrice: price, dailyContribution: contribution)
            bikePrice = ""
            dailyContribution = ""
            error = nil
            await load()
        } catch {
            self.error = "Could not create this moto-taxi ownership plan."
        }
        busyId = nil
    }

    private func contribute(_ planId: String) async {
        guard let value = Double(contributeAmounts[planId] ?? ""), value > 0 else {
            error = "Enter a valid contribution amount."
            return
        }
        busyId = planId
        do {
            _ = try await NetworkClient.shared.contributeToMotoOwnershipPlan(planId: planId, amount: value)
            contributeAmounts[planId] = nil
            error = nil
            await load()
        } catch {
            self.error = "Could not contribute to this plan."
        }
        busyId = nil
    }

    private func cancel(_ planId: String) async {
        busyId = planId
        do {
            _ = try await NetworkClient.shared.cancelMotoOwnershipPlan(planId: planId)
            error = nil
            await load()
        } catch {
            self.error = "Could not cancel this plan."
        }
        busyId = nil
    }

    private func convert(_ planId: String) async {
        busyId = planId
        do {
            _ = try await NetworkClient.shared.convertMotoOwnershipPlanToLoan(planId: planId)
            error = nil
            await load()
        } catch {
            self.error = "Could not convert this plan to a loan."
        }
        busyId = nil
    }

    private func repay(_ planId: String) async {
        guard let value = Double(repayAmounts[planId] ?? ""), value > 0 else {
            error = "Enter a valid repayment amount."
            return
        }
        busyId = planId
        do {
            _ = try await NetworkClient.shared.repayMotoOwnershipPlan(planId: planId, amount: value)
            repayAmounts[planId] = nil
            error = nil
            await load()
        } catch {
            self.error = "Could not repay this loan."
        }
        busyId = nil
    }
}

private func formatMoneyMoto(_ value: Double) -> String {
    let rounded = (value * 100).rounded() / 100
    return rounded == rounded.rounded(.down) ? String(Int64(rounded)) : String(format: "%.2f", rounded)
}
