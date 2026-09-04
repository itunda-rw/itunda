import SwiftUI
import CoreDesignSystem

// Real gap found 2026-08-30 (project_itunda_money_formatting_sweep's own standing
// convention -- comma thousands-separator for every whole-number RWF amount --
// never reached this file). Same per-file shape TransactionHistoryScreen.swift
// already established.
private func formatAmount(_ value: Int) -> String {
    let formatter = NumberFormatter()
    formatter.numberStyle = .decimal
    formatter.groupingSeparator = ","
    return formatter.string(from: NSNumber(value: value)) ?? "0"
}

/// Real Kakao Pay 정기결제/Toss Payments 빌링키-style recurring merchant billing (item 144)
/// -- see MerchantBillingController.kt's own doc comment. Owner-facing plan-management
/// half only; customer subscribe/cancel is already real on all 3 consumer clients.
/// merchant-mfe already has this (BillingScreen.tsx); this is the first iOS client.
struct BillingTab: View {
    @State private var plans: [MerchantBillingPlanDto]?
    @State private var error: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                CreatePlanCard(onCreated: { Task { await load() } })

                VStack(alignment: .leading, spacing: 8) {
                    Text("Your billing plans").font(.headline)
                    Text("A customer who subscribes is charged immediately, then again automatically every cycle until they cancel.")
                        .font(.footnote).foregroundColor(.secondary)
                    if let error {
                        Text(error).font(.footnote).foregroundColor(.red)
                    }
                    if let plans {
                        if plans.isEmpty {
                            // Real copy-voice fix (item 244, round 7): points back to
                            // the real CreatePlanCard form above, matching Android's
                            // already-shipped wording exactly.
                            Text("No billing plans yet — create one above for recurring charges.").font(.footnote).foregroundColor(.secondary)
                        } else {
                            ForEach(plans) { plan in
                                PlanRow(plan: plan, onChanged: { Task { await load() } })
                            }
                        }
                    } else {
                        Text("Loading…").font(.footnote).foregroundColor(.secondary)
                    }
                }
                .padding(16).frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(.secondarySystemBackground)).cornerRadius(12)
            }
            .padding(16)
        }
        .task { await load() }
    }

    private func load() async {
        error = nil
        do {
            plans = try await MerchantNetworkClient.shared.getMyBillingPlans().plans
        } catch {
            self.error = "Could not load your billing plans."
        }
    }
}

private struct CreatePlanCard: View {
    let onCreated: () -> Void

    @State private var name = ""
    @State private var description = ""
    @State private var amount = ""
    @State private var intervalDays = "30"
    @State private var error: String?
    @State private var submitting = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Create a billing plan").font(.headline)
            IdsTextField("Plan name (e.g. Monthly coffee subscription)", text: $name)
            IdsTextField("Description (optional)", text: $description)
            IdsTextField("Amount (RWF)", text: $amount, keyboardType: .decimalPad)
            IdsTextField("Every (days)", text: $intervalDays, keyboardType: .numberPad)
            if let error {
                Text(error).font(.footnote).foregroundColor(.red)
            }
            Button(action: { Task { await submit() } }) {
                Text(submitting ? "Creating…" : "Create plan")
                    .bold().foregroundColor(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                    .background(IDS.Colors.brand).cornerRadius(10)
            }
            .disabled(submitting)
        }
        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
    }

    private func submit() async {
        guard let amt = Double(amount), amt > 0, let days = Int(intervalDays), days > 0, !name.trimmingCharacters(in: .whitespaces).isEmpty else {
            error = "Enter a real plan name, amount, and interval."
            return
        }
        submitting = true
        error = nil
        defer { submitting = false }
        let trimmedDescription = description.trimmingCharacters(in: .whitespaces)
        do {
            _ = try await MerchantNetworkClient.shared.createBillingPlan(CreateBillingPlanRequest(
                name: name.trimmingCharacters(in: .whitespaces),
                description: trimmedDescription.isEmpty ? nil : trimmedDescription,
                amount: amt,
                intervalDays: days
            ))
            name = ""; description = ""; amount = ""; intervalDays = "30"
            onCreated()
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            self.error = message ?? "Could not create this plan."
        } catch {
            self.error = "Could not create this plan."
        }
    }
}

private struct PlanRow: View {
    let plan: MerchantBillingPlanDto
    let onChanged: () -> Void

    @State private var deactivating = false
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(plan.name).bold().font(.subheadline)
                Spacer()
                Text(plan.active ? "Active" : "Deactivated")
                    .font(.caption).bold()
                    .foregroundColor(plan.active ? IDS.Colors.brand : .secondary)
            }
            if let description = plan.description, !description.isEmpty {
                Text(description).font(.footnote)
            }
            Text("\(formatAmount(Int(plan.amount))) RWF every \(plan.intervalDays) day\(plan.intervalDays == 1 ? "" : "s")")
                .font(.footnote).foregroundColor(.secondary)
            if let error {
                Text(error).font(.caption).foregroundColor(.red)
            }
            if plan.active {
                Button(action: { Task { await deactivate() } }) {
                    Text(deactivating ? "…" : "Deactivate").bold().font(.caption)
                        .padding(.horizontal, 12).padding(.vertical, 8)
                        .background(Color(.tertiarySystemBackground)).cornerRadius(8)
                }
                .disabled(deactivating)
            }
        }
        .padding(12).frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.tertiarySystemBackground)).cornerRadius(10)
    }

    private func deactivate() async {
        deactivating = true
        error = nil
        defer { deactivating = false }
        do {
            _ = try await MerchantNetworkClient.shared.deactivateBillingPlan(plan.id)
            onChanged()
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            self.error = message ?? "Could not deactivate this plan."
        } catch {
            self.error = "Could not deactivate this plan."
        }
    }
}
