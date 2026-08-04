import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real Rwanda coffee-cooperative harvest-advance / input financing -- sourced beyond
// this session's usual Toss/Kakao/Naver/Coupang reference ecosystems, grounded in
// Rwanda's own real coffee sector (Rwanda Coffee Cooperatives Federation: 13 member
// cooperatives, ~19,000 producer members). A direct itunda-to-farmer lending
// relationship (real loan_payable receivable), NOT a cooperative-pool redistribution
// like Ikimina. The third feature in this codebase not sourced from the reference
// ecosystems. Mirrors bank-mfe's HarvestAdvanceView exactly, same no-ViewModel,
// "call NetworkClient.shared directly from Task {} blocks" convention
// SaccoScreenView.swift/IkiminaScreenView.swift already established -- including the
// post-fix repayment contract: repayment is a single "Repay in full" action sending
// the advance's own principalAmount, never a user-editable amount field (a real
// debt-forgiveness bug this session caught and fixed before shipping: an arbitrary
// repay amount let a token payment silently close out the whole real debt).

struct HarvestAdvanceScreenView: View {
    var onBack: () -> Void = {}

    @State private var memberships: [CooperativeMembershipDto]?
    @State private var advances: [HarvestAdvanceDto]?
    @State private var error: String?
    @State private var busy = false

    @State private var coopId = ""
    @State private var coopName = ""
    @State private var coopCrop = "COFFEE"
    @State private var advanceAmount = ""
    @State private var advancePurpose = "INPUT_FINANCING"
    @State private var harvestMonthsAway = ""

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { Image(systemName: "chevron.left").foregroundColor(IDS.Colors.textPrimary) }
                Spacer()
                Text("Harvest advance").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                VStack(spacing: 12) {
                    Text("Real input-financing and post-harvest advances for coffee cooperative members, matching Rwanda's own real coffee-sector financing gap.")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    if let error {
                        Text(error).font(.footnote).foregroundColor(.red)
                    }

                    if memberships?.isEmpty ?? true {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Register a cooperative").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                            IdsTextField("Cooperative name", text: $coopName)
                            IdsTextField("Crop (e.g. COFFEE)", text: $coopCrop)
                            Button(action: { Task { await registerCooperative() } }) {
                                Text(busy ? "…" : "Register & join").bold().foregroundColor(.white)
                                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                                    .background(IDS.Colors.brand).cornerRadius(10)
                            }
                            .disabled(busy || coopName.trimmingCharacters(in: .whitespaces).isEmpty)

                            Text("Already have a cooperative ID?").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            IdsTextField("Cooperative ID", text: $coopId)
                            Button(action: { Task { await joinCooperative() } }) {
                                Text(busy ? "…" : "Join").bold().foregroundColor(IDS.Colors.textPrimary)
                                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                                    .background(Color(.secondarySystemBackground)).cornerRadius(10)
                            }
                            .disabled(busy || coopId.trimmingCharacters(in: .whitespaces).isEmpty)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
                    } else if let membership = memberships?.first {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Request an advance").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                            IdsTextField("Amount (RWF, max 500,000)", text: $advanceAmount, keyboardType: .numberPad)
                            IdsTextField("Purpose (INPUT_FINANCING / POST_HARVEST)", text: $advancePurpose)
                            IdsTextField("Expected harvest (months from now)", text: $harvestMonthsAway, keyboardType: .numberPad)
                            Button(action: { Task { await requestAdvance(membershipId: membership.id) } }) {
                                Text(busy ? "…" : "Request advance").bold().foregroundColor(.white)
                                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                                    .background(IDS.Colors.brand).cornerRadius(10)
                            }
                            .disabled(busy || !((Double(advanceAmount) ?? 0) > 0) || Int(harvestMonthsAway) == nil)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        Text("My advances").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                        if let advances {
                            if advances.isEmpty {
                                EmptyStateView("No advances yet.")
                            } else {
                                ForEach(advances) { a in
                                    VStack(alignment: .leading, spacing: 6) {
                                        HStack {
                                            Text("\(formatMoneyHarvest(a.principalAmount)) RWF · \(a.purpose)").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                                            Spacer()
                                            Text(a.status).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                        }
                                        Text("Repay by \(String(a.repaymentDueDate.prefix(10)))").font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                                        if a.status == "REQUESTED" {
                                            Button(action: { Task { await disburse(a.id) } }) {
                                                Text(busy ? "…" : "Disburse").bold().font(.footnote).foregroundColor(.white)
                                                    .frame(maxWidth: .infinity).padding(.vertical, 10)
                                                    .background(IDS.Colors.brand).cornerRadius(8)
                                            }
                                            .disabled(busy)
                                        }
                                        if a.status == "DISBURSED" || a.status == "OVERDUE" {
                                            Button(action: { Task { await repayInFull(a) } }) {
                                                Text(busy ? "…" : "Repay in full (\(formatMoneyHarvest(a.principalAmount)) RWF)").bold().font(.footnote).foregroundColor(IDS.Colors.textPrimary)
                                                    .frame(maxWidth: .infinity).padding(.vertical, 10)
                                                    .background(Color(.tertiarySystemBackground)).cornerRadius(8)
                                            }
                                            .disabled(busy)
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
            memberships = try await NetworkClient.shared.getMyCooperativeMemberships().memberships
            advances = try await NetworkClient.shared.getMyHarvestAdvances().advances
            error = nil
        } catch {
            self.error = "Could not load your cooperative data."
        }
    }

    private func registerCooperative() async {
        guard !coopName.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        busy = true
        do {
            let coop = try await NetworkClient.shared.registerCooperative(name: coopName.trimmingCharacters(in: .whitespaces), cropType: coopCrop.trimmingCharacters(in: .whitespaces).isEmpty ? "COFFEE" : coopCrop, registrationNumber: nil).cooperative
            _ = try await NetworkClient.shared.joinCooperative(cooperativeId: coop.id)
            coopName = ""
            error = nil
            await load()
        } catch {
            self.error = "Could not register this cooperative."
        }
        busy = false
    }

    private func joinCooperative() async {
        guard !coopId.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        busy = true
        do {
            _ = try await NetworkClient.shared.joinCooperative(cooperativeId: coopId.trimmingCharacters(in: .whitespaces))
            coopId = ""
            error = nil
            await load()
        } catch {
            self.error = "Could not join this cooperative."
        }
        busy = false
    }

    private func requestAdvance(membershipId: String) async {
        guard let amount = Double(advanceAmount), amount > 0, let months = Int(harvestMonthsAway), months > 0 else { return }
        busy = true
        do {
            let harvestDate = Calendar.current.date(byAdding: .day, value: months * 30, to: Date()) ?? Date()
            let formatter = ISO8601DateFormatter()
            _ = try await NetworkClient.shared.requestHarvestAdvance(
                membershipId: membershipId, principalAmount: amount, purpose: advancePurpose, expectedHarvestDate: formatter.string(from: harvestDate),
            )
            advanceAmount = ""
            harvestMonthsAway = ""
            error = nil
            await load()
        } catch {
            self.error = "Could not request this advance."
        }
        busy = false
    }

    private func disburse(_ advanceId: String) async {
        busy = true
        do {
            _ = try await NetworkClient.shared.disburseHarvestAdvance(advanceId: advanceId)
            error = nil
            await load()
        } catch {
            self.error = "Could not disburse this advance."
        }
        busy = false
    }

    private func repayInFull(_ advance: HarvestAdvanceDto) async {
        busy = true
        do {
            _ = try await NetworkClient.shared.repayHarvestAdvance(advanceId: advance.id, amount: advance.principalAmount)
            error = nil
            await load()
        } catch {
            self.error = "Could not repay this advance."
        }
        busy = false
    }
}

private func formatMoneyHarvest(_ value: Double) -> String {
    let rounded = (value * 100).rounded() / 100
    return rounded == rounded.rounded(.down) ? String(Int64(rounded)) : String(format: "%.2f", rounded)
}
