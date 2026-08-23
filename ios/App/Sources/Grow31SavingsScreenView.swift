import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real Toss Bank 키워봐요 31일적금 (Grow-it 31-day savings) equivalent -- the first iOS
// UI this feature has ever had (2026-08-15), direct sibling of
// WeeklySavingsScreenView.swift (same top-bar shape, same "call NetworkClient.shared
// directly from Task {} blocks" convention). Real structural difference from that
// screen: a deposit is an explicit daily user action ("Save today"), not something a
// scheduler pulls automatically, so this screen surfaces that action front and
// center rather than just a due-date countdown -- matches Android's
// Grow31SavingsScreen.kt and bank-mfe's Grow31SavingsSection, both built earlier the
// same session.

struct Grow31SavingsScreenView: View {
    var onBack: () -> Void = {}
    @State private var plans: [Grow31SavingsPlanDto] = []
    @State private var loadError: String?
    @State private var loaded = false
    @State private var selectedPlanId: String?
    @State private var showCreate = false

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: { selectedPlanId != nil ? (selectedPlanId = nil) : onBack() }) {
                    Image(systemName: "chevron.left").foregroundColor(IDS.Colors.textPrimary)
                }.accessibilityLabel("Back")
                Spacer()
                Text("31-Day Savings").font(.headline).foregroundColor(IDS.Colors.textPrimary)
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
                Grow31SavingsDetailContent(planId: planId, onChanged: { load() })
            } else {
                ScrollView {
                    Grow31SavingsListContent(
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
            CreateGrow31SavingsPlanView(onCreated: {
                showCreate = false
                load()
            }, onCancel: { showCreate = false })
        }
    }

    private func load() {
        Task {
            do {
                let res = try await NetworkClient.shared.getGrow31SavingsPlans()
                if res.success { plans = res.plans }
                loadError = nil
            } catch {
                loadError = "Could not load your real 31-day savings plans."
            }
            loaded = true
        }
    }
}

private struct Grow31SavingsListContent: View {
    let plans: [Grow31SavingsPlanDto]
    let error: String?
    let loaded: Bool
    let onOpen: (Grow31SavingsPlanDto) -> Void
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: 10) {
            if let error {
                Grow31SavingsErrorCard(message: error, onRetry: onRetry)
            } else if !loaded {
                ProgressView().frame(maxWidth: .infinity).padding(40)
            } else if plans.isEmpty {
                Text("No 31-day plans yet. Save a small fixed amount once every real day -- the longer your unbroken daily streak, the higher your bonus rate.")
                    .font(.caption).foregroundColor(IDS.Colors.textSecondary).padding()
            } else {
                ForEach(plans) { plan in
                    Grow31SavingsPlanRow(plan: plan) { onOpen(plan) }
                }
            }
        }
        .padding(.horizontal)
    }
}

private struct Grow31SavingsPlanRow: View {
    let plan: Grow31SavingsPlanDto
    let onTap: () -> Void

    private var progress: Double {
        Double(plan.daysElapsed) / Double(Grow31SavingsConstants.termDays)
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
                    Text("\(formatMoney(plan.totalSaved)) RWF").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                }
                Grow31SavingsProgressBar(progress: progress)
                HStack {
                    Text("Day \(min(plan.daysElapsed, Grow31SavingsConstants.termDays))/\(Grow31SavingsConstants.termDays) -- streak \(plan.currentStreak)")
                        .font(.caption2).foregroundColor(IDS.Colors.textTertiary)
                    Spacer()
                    let bonus = Grow31SavingsConstants.bonusRate(forStreak: plan.longestStreak)
                    Text(bonus > 0 ? "+\(Int(bonus))% bonus locked in" : "Save 3 days in a row for a bonus")
                        .font(.caption2).bold()
                        .foregroundColor(bonus > 0 ? .green : IDS.Colors.textTertiary)
                }
            }
            .padding()
            .background(IDS.Colors.card)
            .cornerRadius(14).idsCardBorder(cornerRadius: 14)
        }
        .buttonStyle(.plain)
    }

    private func statusLabel(_ plan: Grow31SavingsPlanDto) -> String {
        switch plan.status {
        case "MATURED": return plan.withdrawnAt == nil ? "Matured -- ready to withdraw" : "Matured -- withdrawn"
        case "CANCELLED": return "Cancelled (early withdrawal)"
        default: return "Active"
        }
    }
}

// Lightweight dependency-free progress bar, same convention as
// WeeklySavingsProgressBar (no charting library exists anywhere in this app).
private struct Grow31SavingsProgressBar: View {
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

private struct Grow31SavingsErrorCard: View {
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

// MARK: - Create

private struct CreateGrow31SavingsPlanView: View {
    let onCreated: () -> Void
    let onCancel: () -> Void

    @State private var name = ""
    @State private var dailyAmount = ""
    @State private var error: String?
    @State private var submitting = false

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Pick a small amount you can realistically save every single day for \(Grow31SavingsConstants.termDays) days. Miss a day and your streak resets -- but your longest streak still locks in a bonus rate at maturity, up to +10% for a full unbroken run.")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)

                    VStack(alignment: .leading, spacing: 6) {
                        Text("Plan name").font(.caption).bold().foregroundColor(IDS.Colors.textSecondary)
                        TextField("e.g. New laptop", text: $name)
                            .padding(10)
                            .background(IDS.Colors.chipBackground)
                            .cornerRadius(10)
                    }

                    VStack(alignment: .leading, spacing: 6) {
                        Text("Daily amount (RWF)").font(.caption).bold().foregroundColor(IDS.Colors.textSecondary)
                        TextField("e.g. 500", text: $dailyAmount)
                            .keyboardType(.numberPad)
                            .padding(10)
                            .background(IDS.Colors.chipBackground)
                            .cornerRadius(10)
                    }

                    if let error {
                        Text(error).font(.caption).foregroundColor(.red)
                    }

                    Button(action: create) {
                        Text(submitting ? "Working…" : "Start 31-day plan")
                            .foregroundColor(.white).bold()
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(IDS.Colors.brand)
                            .cornerRadius(12)
                    }
                    .disabled(submitting)
                }
                .padding()
            }
            .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
            .navigationTitle("New 31-day plan")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", action: onCancel)
                }
            }
        }
    }

    private func create() {
        guard !name.trimmingCharacters(in: .whitespaces).isEmpty else {
            error = "Enter a real plan name."
            return
        }
        guard let amount = Double(dailyAmount), amount > 0 else {
            error = "Enter a real daily amount greater than zero."
            return
        }
        submitting = true
        Task {
            do {
                _ = try await NetworkClient.shared.createGrow31SavingsPlan(name: name, dailyAmount: amount)
                ToastCenter.shared.show("31-day plan started.")
                onCreated()
            } catch let NetworkError.httpError(statusCode) {
                error = Self.errorMessage(statusCode)
            } catch {
                self.error = "Couldn't reach itunda. Check your connection and try again."
            }
            submitting = false
        }
    }

    private static func errorMessage(_ statusCode: Int) -> String {
        switch statusCode {
        case 400: return "That amount isn't valid."
        case 429: return "Too many plans created recently -- try again in a bit."
        default: return "Could not start this plan. Please try again."
        }
    }
}

// MARK: - Detail

private struct Grow31SavingsDetailContent: View {
    let planId: String
    let onChanged: () -> Void

    @State private var plan: Grow31SavingsPlanDto?
    @State private var accountBalance: Double = 0
    @State private var deposits: [Grow31SavingsDepositDto] = []
    @State private var loadError: String?
    @State private var actionError: String?
    @State private var confirmingCancel = false
    @State private var submitting = false

    private var alreadyDepositedToday: Bool {
        guard let plan, let lastDepositDate = plan.lastDepositDate else { return false }
        let today = ISO8601DateFormatter().string(from: Date()).prefix(10)
        return lastDepositDate == today
    }

    var body: some View {
        ScrollView {
            if let loadError {
                Grow31SavingsErrorCard(message: loadError, onRetry: load)
                    .padding(.horizontal)
            } else if let plan {
                VStack(alignment: .leading, spacing: 16) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(plan.name).font(.title3).bold().foregroundColor(IDS.Colors.textPrimary)
                        Text("Account balance").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        Text("\(formatMoney(accountBalance)) RWF").font(.largeTitle).bold().foregroundColor(IDS.Colors.textPrimary)
                        Grow31SavingsProgressBar(progress: Double(plan.daysElapsed) / Double(Grow31SavingsConstants.termDays))
                            .padding(.top, 6)
                        Text("Day \(min(plan.daysElapsed, Grow31SavingsConstants.termDays)) of \(Grow31SavingsConstants.termDays)")
                            .font(.caption2).foregroundColor(IDS.Colors.textTertiary)
                    }
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(IDS.Colors.card)
                    .cornerRadius(14).idsCardBorder(cornerRadius: 14)

                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text("Streak").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            Spacer()
                            Text("Current \(plan.currentStreak) -- longest \(plan.longestStreak)").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                        }
                        HStack {
                            Text("Base rate").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            Spacer()
                            Text("\(String(format: "%.1f", plan.baseRate))%").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                        }
                        let bonus = Grow31SavingsConstants.bonusRate(forStreak: plan.longestStreak)
                        HStack {
                            Text("Streak bonus (longest run)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            Spacer()
                            Text(bonus > 0 ? "+\(Int(bonus))%" : "Not unlocked yet").font(.caption).bold().foregroundColor(bonus > 0 ? .green : IDS.Colors.textPrimary)
                        }
                        if let totalInterestPaid = plan.totalInterestPaid {
                            HStack {
                                Text("Interest paid").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                Spacer()
                                Text("\(formatMoney(totalInterestPaid)) RWF").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                            }
                        }
                    }
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(IDS.Colors.card)
                    .cornerRadius(14).idsCardBorder(cornerRadius: 14)

                    if !deposits.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Deposits").font(.caption).bold().foregroundColor(IDS.Colors.textSecondary)
                            ForEach(deposits.sorted { $0.dayNumber > $1.dayNumber }) { deposit in
                                HStack {
                                    Text("Day \(deposit.dayNumber) -- streak \(deposit.streakAtDeposit)").font(.caption).foregroundColor(IDS.Colors.textPrimary)
                                    Spacer()
                                    Text("\(formatMoney(deposit.amount)) RWF").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                }
                                .padding(.vertical, 4)
                            }
                        }
                        .padding()
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(IDS.Colors.card)
                        .cornerRadius(14).idsCardBorder(cornerRadius: 14)
                    }

                    if let actionError {
                        Text(actionError).font(.caption).foregroundColor(.red)
                    }

                    if plan.status == "ACTIVE" {
                        if confirmingCancel {
                            VStack(alignment: .leading, spacing: 10) {
                                Text("Cancelling now forfeits your streak bonus for good. You'll get principal plus base-rate-only interest, paid out immediately to your main account.")
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
                            .padding()
                            .background(IDS.Colors.card)
                            .cornerRadius(14).idsCardBorder(cornerRadius: 14)
                        } else {
                            VStack(spacing: 8) {
                                if !alreadyDepositedToday {
                                    Button(action: depositToday) {
                                        Text(submitting ? "Working…" : "Save today (+\(formatMoney(plan.dailyAmount)) RWF)")
                                            .foregroundColor(.white).bold()
                                            .frame(maxWidth: .infinity)
                                            .padding(.vertical, 14)
                                            .background(IDS.Colors.brand)
                                            .cornerRadius(12)
                                    }
                                    .disabled(submitting)
                                } else {
                                    Text("You've already saved today -- come back tomorrow to keep your streak.")
                                        .font(.caption).bold().foregroundColor(.green)
                                }
                                Button(action: { confirmingCancel = true }) {
                                    Text("Cancel plan (early withdrawal)")
                                        .foregroundColor(.red).bold()
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 14)
                                        .background(IDS.Colors.chipBackground)
                                        .cornerRadius(12)
                                }
                                .disabled(submitting)
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
                let res = try await NetworkClient.shared.getGrow31SavingsPlan(id: planId)
                if res.success {
                    plan = res.plan
                    accountBalance = res.accountBalance
                    deposits = res.deposits
                }
                loadError = nil
            } catch {
                loadError = "Could not load this real plan."
            }
        }
    }

    private func depositToday() {
        submitting = true
        Task {
            do {
                let res = try await NetworkClient.shared.depositGrow31SavingsToday(id: planId)
                plan = res.plan
                accountBalance = res.accountBalance
                deposits = res.deposits
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

    private func cancelPlan() {
        submitting = true
        Task {
            do {
                let res = try await NetworkClient.shared.cancelGrow31SavingsPlan(id: planId)
                plan = res.plan
                accountBalance = res.accountBalance
                deposits = res.deposits
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
                let res = try await NetworkClient.shared.withdrawGrow31SavingsPlan(id: planId)
                plan = res.plan
                accountBalance = res.accountBalance
                deposits = res.deposits
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
        case 422: return "Not enough available balance to save today."
        default: return "Something went wrong. Please try again."
        }
    }
}

private func formatMoney(_ value: Double) -> String {
    let rounded = (value * 100).rounded() / 100
    if rounded == rounded.rounded(.towardZero) {
        return String(Int(rounded))
    }
    return String(format: "%.2f", rounded)
}
