import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real "itunda Bank assets" hub (2026-08-31, direct user-supplied Toss Bank
// screenshots + explicit correction: "my asset screen is hub of all assets, itunda
// bank assets only itunda bank assets" -- scoped ONLY to itunda Bank's own money
// buckets, deliberately NOT the app-wide net-worth hub. A flat balance-summary list
// matching the real "My Toss Bank assets" screen, each row opening its own
// BucketDetailScreen. Same no-ViewModel, NetworkClient-direct-from-Task shape as
// WeeklySavingsScreenView.swift.
private enum BankAssetRow: Identifiable {
    case interestJar(InterestJar)
    case goal(SavingsGoal)
    case weekly(WeeklySavingsPlanDto)
    case grow31(Grow31SavingsPlanDto)
    case upfront(UpfrontDepositDto)
    case youth(Account)

    var id: String {
        switch self {
        case .interestJar: return "interestJar"
        case .goal(let g): return "goal-\(g.id)"
        case .weekly(let p): return "weekly-\(p.id)"
        case .grow31(let p): return "grow31-\(p.id)"
        case .upfront(let d): return "upfront-\(d.id)"
        case .youth(let a): return "youth-\(a.id)"
        }
    }

    var label: String {
        switch self {
        case .interestJar: return "Interest Jar"
        case .goal(let g): return g.name
        case .weekly(let p): return p.name
        case .grow31(let p): return p.name
        case .upfront: return "12-Month Deposit"
        case .youth: return "Youth Account"
        }
    }

    var balance: Double {
        switch self {
        case .interestJar(let jar): return jar.balance
        case .goal(let g): return g.currentAmount
        case .weekly(let p): return p.currentAmount
        case .grow31(let p): return p.totalSaved
        case .upfront(let d): return d.principal
        case .youth(let a): return a.balance
        }
    }
}

struct ItundaBankAssetsScreen: View {
    let onBack: () -> Void
    @State private var rows: [BankAssetRow]?
    @State private var openRow: BankAssetRow?

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    IDS.Icons.back(size: 18, color: IDS.Colors.textPrimary, relativeTo: .body).frame(width: 44, height: 44)
                }
                .accessibilityLabel("Back")
                Spacer()
            }
            .padding(.horizontal, 8)

            Text("itunda Bank assets")
                .font(IDS.scaledFont(size: 20, weight: .bold, relativeTo: .title2))
                .foregroundColor(IDS.Colors.textPrimary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 24)
                .padding(.bottom, 8)

            if rows == nil {
                Text("Loading…")
                    .font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote))
                    .foregroundColor(IDS.Colors.textTertiary)
                    .padding(.horizontal, 24)
                Spacer()
            } else if rows!.isEmpty {
                Text("You don't have any itunda Bank products open yet.")
                    .font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote))
                    .foregroundColor(IDS.Colors.textTertiary)
                    .padding(.horizontal, 24)
                Spacer()
            } else {
                ScrollView {
                    VStack(spacing: 0) {
                        ForEach(rows!) { row in
                            Button(action: { openRow = row }) {
                                HStack {
                                    Text(row.label).font(IDS.scaledFont(size: 14, weight: .semibold, relativeTo: .callout)).foregroundColor(IDS.Colors.textPrimary)
                                    Spacer()
                                    Text("\(formatBankAssetAmount(row.balance)) RWF").font(IDS.scaledFont(size: 14, weight: .bold, relativeTo: .callout)).foregroundColor(IDS.Colors.textPrimary)
                                }
                                .padding(.vertical, 12)
                            }
                            Divider().overlay(IDS.Colors.divider)
                        }
                    }
                    .padding(.horizontal, 24)
                }
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .navigationBarHidden(true)
        .task { await load() }
        .fullScreenCover(item: $openRow) { row in
            detailScreen(for: row)
        }
    }

    private func load() async {
        async let jarRes = try? NetworkClient.shared.getInterestJar()
        async let goalsRes = try? NetworkClient.shared.getSavingsGoals()
        async let weeklyRes = try? NetworkClient.shared.getWeeklySavingsPlans()
        async let grow31Res = try? NetworkClient.shared.getGrow31SavingsPlans()
        async let upfrontRes = try? NetworkClient.shared.getUpfrontDeposits()
        async let accountsRes = try? NetworkClient.shared.getAccounts()

        var built: [BankAssetRow] = []
        if let jar = await jarRes?.jar { built.append(.interestJar(jar)) }
        if let goals = await goalsRes?.goals { built.append(contentsOf: goals.filter { $0.status == "active" }.map { .goal($0) }) }
        if let plans = await weeklyRes?.plans { built.append(contentsOf: plans.filter { $0.status == "ACTIVE" }.map { .weekly($0) }) }
        if let plans = await grow31Res?.plans { built.append(contentsOf: plans.filter { $0.status == "ACTIVE" }.map { .grow31($0) }) }
        if let deposits = await upfrontRes?.deposits { built.append(contentsOf: deposits.filter { $0.withdrawnAt == nil }.map { .upfront($0) }) }
        if let youth = await accountsRes?.accounts.first(where: { $0.type == "MINI" }) { built.append(.youth(youth)) }
        rows = built
    }

    @ViewBuilder
    private func detailScreen(for row: BankAssetRow) -> some View {
        switch row {
        case .interestJar(let jar):
            BucketDetailScreen(
                title: "Interest Jar", subtitle: "Safe Box", balanceText: "\(formatBankAssetAmount(jar.balance)) RWF",
                fetchTransactions: { try await NetworkClient.shared.getInterestJarTransactions().transactions },
                onBack: { openRow = nil }
            )
        case .goal(let goal):
            BucketDetailScreen(
                title: goal.name, subtitle: "Savings Goal", balanceText: "\(formatBankAssetAmount(goal.currentAmount)) RWF",
                fetchTransactions: { try await NetworkClient.shared.getSavingsGoalTransactions(goalId: goal.id).transactions },
                onBack: { openRow = nil }
            )
        case .weekly(let plan):
            BucketDetailScreen(
                title: plan.name, subtitle: "26-Week Savings", balanceText: "\(formatBankAssetAmount(plan.currentAmount)) RWF",
                fetchTransactions: { try await NetworkClient.shared.getWeeklySavingsPlanTransactions(id: plan.id).transactions },
                onBack: { openRow = nil }
            )
        case .grow31(let plan):
            BucketDetailScreen(
                title: plan.name, subtitle: "31-Day Savings", balanceText: "\(formatBankAssetAmount(plan.totalSaved)) RWF",
                fetchTransactions: { try await NetworkClient.shared.getGrow31SavingsPlanTransactions(id: plan.id).transactions },
                onBack: { openRow = nil }
            )
        case .upfront(let deposit):
            BucketDetailScreen(
                title: "12-Month Deposit", subtitle: "Upfront Interest Deposit", balanceText: "\(formatBankAssetAmount(deposit.principal)) RWF",
                fetchTransactions: { try await NetworkClient.shared.getUpfrontDepositTransactions(id: deposit.id).transactions },
                onBack: { openRow = nil }
            )
        case .youth(let account):
            BucketDetailScreen(
                title: "Youth Account", subtitle: account.accountNumber, balanceText: "\(formatBankAssetAmount(account.balance)) RWF",
                fetchTransactions: {
                    let txs = try await NetworkClient.shared.getAccountTransactionHistory(accountId: account.id).transactions
                        .sorted(by: { $0.createdAt > $1.createdAt })
                    var runningBalance = account.balance
                    return txs.map { tx in
                        let balanceAfter = runningBalance
                        runningBalance -= tx.amount
                        return BucketTransactionDto(id: tx.id, description: tx.description, amount: tx.amount, isCredit: true, balanceAfter: balanceAfter, createdAt: tx.createdAt)
                    }
                },
                onBack: { openRow = nil }
            )
        }
    }
}

private func formatBankAssetAmount(_ value: Double) -> String {
    let formatter = NumberFormatter()
    formatter.numberStyle = .decimal
    formatter.groupingSeparator = ","
    formatter.maximumFractionDigits = 0
    return formatter.string(from: NSNumber(value: value)) ?? "0"
}
