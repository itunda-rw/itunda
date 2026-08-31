import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real KakaoBank 26주적금 (26-week savings) equivalent (2026-07-21) -- the first iOS UI
// this feature has ever had, direct sibling of InvestScreenView.swift (same top-bar
// shape, same "call NetworkClient.shared directly from Task {} blocks" convention, no
// separate view-model layer). See WeeklySavingsService.kt's own doc comment for the
// full sourced mechanics: the weekly auto-debit amount escalates every
// WeeklySavingsConstants.escalationStepWeeks weeks, interest accrues per-installment,
// and the bonus rate only survives an unbroken run all the way to real 26-week
// maturity -- a single missed installment forfeits it permanently.

struct WeeklySavingsScreenView: View {
    var onBack: () -> Void = {}
    @State private var plans: [WeeklySavingsPlanDto] = []
    @State private var loadError: String?
    @State private var loaded = false
    @State private var selectedPlanId: String?
    @State private var showCreate = false

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: { selectedPlanId != nil ? (selectedPlanId = nil) : onBack() }) {
                    IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body)
                }.accessibilityLabel("Back")
                Spacer()
                Text("26-Week Savings").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                if selectedPlanId == nil {
                    Button(action: { showCreate = true }) {
                        Image(systemName: "plus").foregroundColor(IDS.Colors.textPrimary)
                    }.accessibilityLabel("Add")
                } else {
                    Color.clear.frame(width: 20)
                }
            }
            .padding()

            if let planId = selectedPlanId {
                WeeklySavingsDetailContent(planId: planId, onChanged: { load() })
            } else {
                ScrollView {
                    WeeklySavingsListContent(
                        plans: plans,
                        error: loadError,
                        loaded: loaded,
                        onOpen: { selectedPlanId = $0.id },
                        onRetry: load
                    )
                }
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { load() }
        .sheet(isPresented: $showCreate) {
            CreateWeeklySavingsPlanView(onCreated: {
                showCreate = false
                load()
            }, onCancel: { showCreate = false })
        }
    }

    private func load() {
        Task {
            do {
                let res = try await NetworkClient.shared.getWeeklySavingsPlans()
                if res.success { plans = res.plans }
                loadError = nil
            } catch {
                loadError = "Could not load your real 26-week savings plans."
            }
            loaded = true
        }
    }
}

private struct WeeklySavingsListContent: View {
    let plans: [WeeklySavingsPlanDto]
    let error: String?
    let loaded: Bool
    let onOpen: (WeeklySavingsPlanDto) -> Void
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: 10) {
            if let error {
                WeeklySavingsErrorCard(message: error, onRetry: onRetry)
            } else if !loaded {
                ProgressView().frame(maxWidth: .infinity).padding(40)
            } else if plans.isEmpty {
                Text("No 26-week plans yet. Start one -- pick a weekly amount that steps up automatically, and hold an unbroken streak all the way to maturity for a bonus rate.")
                    .font(.caption).foregroundColor(IDS.Colors.textSecondary).padding()
            } else {
                ForEach(plans) { plan in
                    WeeklySavingsPlanRow(plan: plan) { onOpen(plan) }
                }
            }
        }
        .padding(.horizontal)
    }
}

// Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper -- a history/
// progress log of savings plans, kept the per-row Divider convention
// (docs/DESIGN_REFERENCES.md §274), matching Android's WeeklySavingsPlanRow
// (commit bdc1c5b0) and web's identical conversion (commit b1926c2a).
private struct WeeklySavingsPlanRow: View {
    let plan: WeeklySavingsPlanDto
    let onTap: () -> Void

    private var progress: Double {
        Double(plan.weeksElapsed) / Double(WeeklySavingsConstants.termWeeks)
    }

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(plan.name).font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                        Text(statusLabel(plan)).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    }
                    Spacer()
                    Text("\(formatMoney(plan.currentAmount)) RWF").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                }
                WeeklySavingsProgressBar(progress: progress)
                HStack {
                    Text("Week \(plan.weeksElapsed)/\(WeeklySavingsConstants.termWeeks)")
                        .font(.caption2).foregroundColor(IDS.Colors.textTertiary)
                    Spacer()
                    Text(plan.status == "ACTIVE" ? (plan.streakBroken ? "Streak broken -- bonus forfeited" : "On streak -- bonus on track") : statusLabel(plan))
                        .font(.caption2).bold()
                        .foregroundColor(plan.status == "ACTIVE" && plan.streakBroken ? .red : (plan.status == "ACTIVE" ? .green : IDS.Colors.textTertiary))
                }
            }
            .padding(.vertical, 10)
        }
        .buttonStyle(.plain)
        Divider().overlay(IDS.Colors.divider)
    }

    private func statusLabel(_ plan: WeeklySavingsPlanDto) -> String {
        switch plan.status {
        case "MATURED": return plan.withdrawnAt == nil ? "Matured -- ready to withdraw" : "Matured -- withdrawn"
        case "CANCELLED": return "Cancelled (early withdrawal)"
        default: return "Active"
        }
    }
}

// Lightweight dependency-free progress bar -- no charting/progress library exists
// anywhere in this app (see InvestScreenView.swift's own Sparkline for the same
// "not disproportionate to a real MVP" call), matching that file's GeometryReader
// convention rather than SwiftUI's own ProgressView(value:), which no other screen in
// this codebase uses either.
private struct WeeklySavingsProgressBar: View {
    let progress: Double

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                RoundedRectangle(cornerRadius: 4).fill(IDS.Colors.chipBackground)
                RoundedRectangle(cornerRadius: 4).fill(IDS.Colors.brand)
                    .frame(width: geo.size.width * CGFloat(min(max(progress, 0), 1)))
            }
        }
        .frame(height: 8)
    }
}

private struct WeeklySavingsErrorCard: View {
    let message: String
    let onRetry: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(message).font(.caption).foregroundColor(.red)
            Button("Retry", action: onRetry).font(.caption).bold().foregroundColor(IDS.Colors.brand)
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(IDS.Colors.card)
        .cornerRadius(14).idsCardBorder(cornerRadius: 14)
    }
}

// MARK: - Detail

private struct WeeklySavingsDetailContent: View {
    let planId: String
    let onChanged: () -> Void

    @State private var plan: WeeklySavingsPlanDto?
    @State private var accountBalance: Double = 0
    @State private var installments: [WeeklySavingsInstallmentDto] = []
    // Real per-bucket ledger (2026-08-31) -- see BucketTransactionRow's own doc
    // comment. Replaces the "Installments" section below with the real transaction
    // ledger this plan's own dedicated account always had, just never exposed.
    @State private var transactions: [BucketTransactionDto]?
    @State private var loadError: String?
    @State private var actionError: String?
    @State private var confirmingCancel = false
    @State private var submitting = false

    var body: some View {
        ScrollView {
            if let loadError {
                WeeklySavingsErrorCard(message: loadError, onRetry: load)
                    .padding(.horizontal)
            } else if let plan {
                // Real fix (2026-08-24, flat-design sweep): dropped every Card wrapper in
                // this detail screen -- 3-4 real sections render together, so Dividers mark
                // each boundary instead of individually boxing every section
                // (docs/UI_UX_GUIDELINES.md §10). Matches Android's WeeklySavingsScreen.kt
                // (commit bdc1c5b0) and web's WeeklySavingsPlanDetailView (commit b1926c2a)
                // conversion of this exact same real screen.
                VStack(alignment: .leading, spacing: 16) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(plan.name).font(.title3).bold().foregroundColor(IDS.Colors.textPrimary)
                        Text("Account balance").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        Text("\(formatMoney(accountBalance)) RWF").font(.largeTitle).bold().foregroundColor(IDS.Colors.textPrimary)
                        WeeklySavingsProgressBar(progress: Double(plan.weeksElapsed) / Double(WeeklySavingsConstants.termWeeks))
                            .padding(.top, 6)
                        Text("Week \(plan.weeksElapsed)/\(WeeklySavingsConstants.termWeeks) -- \(plan.installmentsCollected) installments collected")
                            .font(.caption2).foregroundColor(IDS.Colors.textTertiary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    Divider().overlay(IDS.Colors.divider)

                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text("Streak status").font(.caption).bold().foregroundColor(IDS.Colors.textSecondary)
                            Spacer()
                            Text(plan.streakBroken ? "Streak broken -- bonus forfeited" : "On streak -- bonus on track")
                                .font(.caption).bold()
                                .foregroundColor(plan.streakBroken ? .red : .green)
                        }
                        HStack {
                            Text("Base rate").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            Spacer()
                            Text("\(String(format: "%.1f", plan.baseRate))%").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                        }
                        HStack {
                            Text("Bonus rate (unbroken streak only)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            Spacer()
                            Text("+\(String(format: "%.1f", plan.bonusRate))%").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                        }
                        if let totalInterestPaid = plan.totalInterestPaid {
                            HStack {
                                Text("Interest paid").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                Spacer()
                                Text("\(formatMoney(totalInterestPaid)) RWF").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                            }
                        }
                        if plan.status == "ACTIVE" {
                            HStack {
                                Text("Next installment due").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                Spacer()
                                Text(formatDate(plan.nextInstallmentDueAt)).font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    Divider().overlay(IDS.Colors.divider)
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Transactions").font(.caption).bold().foregroundColor(IDS.Colors.textSecondary)
                        if let transactions, !transactions.isEmpty {
                            ForEach(transactions.sorted(by: { $0.createdAt > $1.createdAt })) { tx in
                                BucketTransactionRow(tx: tx)
                            }
                        } else if transactions == nil {
                            Text("Loading…").font(.caption).foregroundColor(IDS.Colors.textTertiary)
                        } else {
                            Text("No transactions to show yet.").font(.caption).foregroundColor(IDS.Colors.textTertiary)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    if let actionError {
                        Text(actionError).font(.caption).foregroundColor(.red)
                    }

                    if plan.status == "ACTIVE" {
                        if confirmingCancel {
                            VStack(alignment: .leading, spacing: 10) {
                                Text("Cancelling now forfeits the streak bonus for good. You'll get your principal plus base-rate-only interest, paid out immediately to your main account.")
                                    .font(.caption).foregroundColor(.red)
                                HStack {
                                    Button("Keep saving") { confirmingCancel = false }
                                        .font(.caption).bold().foregroundColor(IDS.Colors.textSecondary)
                                    Spacer()
                                    Button(submitting ? "Working…" : "Confirm early withdrawal") { cancelPlan() }
                                        .font(.caption).bold().foregroundColor(.red)
                                        .disabled(submitting)
                                }
                            }
                        } else {
                            Button(action: { confirmingCancel = true }) {
                                Text("Cancel plan (early withdrawal)")
                                    .foregroundColor(.red).bold()
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 14)
                                    .background(IDS.Colors.chipBackground)
                                    .cornerRadius(12)
                            }
                        }
                    } else if plan.status == "MATURED" && plan.withdrawnAt == nil {
                        Button(action: withdrawPlan) {
                            Text(submitting ? "Working…" : "Withdraw to main account")
                                .foregroundColor(.white).bold()
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 14)
                                .background(IDS.Colors.brand)
                                .cornerRadius(12)
                        }
                        .disabled(submitting)
                    }
                }
                .padding()
            } else {
                ProgressView().frame(maxWidth: .infinity).padding(40)
            }
        }
        .task { load() }
    }

    private func load() {
        Task {
            do {
                let res = try await NetworkClient.shared.getWeeklySavingsPlan(id: planId)
                if res.success {
                    plan = res.plan
                    accountBalance = res.accountBalance
                    installments = res.installments
                }
                loadError = nil
            } catch {
                loadError = "Could not load this real plan."
            }
            transactions = (try? await NetworkClient.shared.getWeeklySavingsPlanTransactions(id: planId).transactions) ?? []
        }
    }

    private func cancelPlan() {
        submitting = true
        Task {
            do {
                let res = try await NetworkClient.shared.cancelWeeklySavingsPlan(id: planId)
                plan = res.plan
                accountBalance = res.accountBalance
                installments = res.installments
                confirmingCancel = false
                actionError = nil
                onChanged()
            } catch let NetworkError.httpError(statusCode) {
                actionError = Self.errorMessage(statusCode)
            } catch {
                actionError = "Couldn't reach itunda. Check your connection and try again."
            }
            submitting = false
        }
    }

    private func withdrawPlan() {
        submitting = true
        Task {
            do {
                let res = try await NetworkClient.shared.withdrawWeeklySavingsPlan(id: planId)
                plan = res.plan
                accountBalance = res.accountBalance
                installments = res.installments
                actionError = nil
                onChanged()
            } catch let NetworkError.httpError(statusCode) {
                actionError = Self.errorMessage(statusCode)
            } catch {
                actionError = "Couldn't reach itunda. Check your connection and try again."
            }
            submitting = false
        }
    }

    private static func errorMessage(_ statusCode: Int) -> String {
        switch statusCode {
        case 404: return "This plan or its account couldn't be found."
        case 409: return "This plan isn't in the right state for that action anymore."
        default: return "Something went wrong. Please try again."
        }
    }
}

// Reuses the ISO8601DateFormatter(withFractionalSeconds:) convenience initializer
// TalkScreen.swift's GiftBubble already established for parsing this same backend's
// java.time.Instant JSON strings, which may or may not carry a fractional-seconds
// component.
private func formatDate(_ iso: String) -> String {
    let date = ISO8601DateFormatter(withFractionalSeconds: true).date(from: iso) ?? ISO8601DateFormatter(withFractionalSeconds: false).date(from: iso)
    guard let date else { return iso }
    let display = DateFormatter()
    display.dateStyle = .medium
    display.timeStyle = .none
    return display.string(from: date)
}

private func formatMoney(_ value: Double) -> String {
    let rounded = (value * 100).rounded() / 100
    let formatter = NumberFormatter()
    formatter.numberStyle = .decimal
    formatter.groupingSeparator = ","
    formatter.usesGroupingSeparator = true
    if rounded == rounded.rounded(.towardZero) {
        formatter.maximumFractionDigits = 0
        return formatter.string(from: NSNumber(value: rounded)) ?? String(Int(rounded))
    }
    formatter.minimumFractionDigits = 2
    formatter.maximumFractionDigits = 2
    return formatter.string(from: NSNumber(value: rounded)) ?? String(format: "%.2f", rounded)
}
