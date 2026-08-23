import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real Kakao Pay 소비 리포트-style spending categorization (rw.itunda.account.
// AccountService.getSpendingInsight, real since 2026-07-13) -- first iOS client for this
// feature (item 108, found backend-only via a fresh matrix scan; bank-mfe/Android
// ported the same day as items 106/107). Same no-ViewModel,
// "call NetworkClient.shared directly from Task {} blocks" convention as
// YouthAccountScreenView.swift/GroupAccountScreenView.swift.
struct SpendingScreenView: View {
    var onBack: () -> Void = {}
    @State private var insight: SpendingInsightResponse?
    @State private var error: String?
    @State private var budgets: [BudgetViewDto]?
    @State private var monthlyReport: MonthlySpendingReportResponse?

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { Image(systemName: "chevron.left").foregroundColor(IDS.Colors.textPrimary) }.accessibilityLabel("Back")
                Spacer()
                Text("Spending").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let error {
                        Text(error).font(.caption).foregroundColor(.red)
                    }
                    if let monthlyReport {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("This month so far").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            HStack(alignment: .lastTextBaseline, spacing: 8) {
                                Text("\(formatMoneySpending(monthlyReport.currentTotal)) RWF").font(.title2).bold().foregroundColor(IDS.Colors.textPrimary)
                                if let change = monthlyReport.percentChange {
                                    Text("\(change > 0 ? "▲" : "▼") \(abs(change))% vs last month")
                                        .font(.caption).bold().foregroundColor(change > 0 ? .red : .green)
                                }
                            }
                            let changed = monthlyReport.categories.filter { $0.percentChange != nil }
                                .sorted { abs($0.percentChange ?? 0) > abs($1.percentChange ?? 0) }
                            ForEach(changed.prefix(3)) { c in
                                Text("\(c.name): \(formatMoneySpending(c.currentAmount)) RWF (\((c.percentChange ?? 0) > 0 ? "+" : "")\(c.percentChange ?? 0)% vs last month)")
                                    .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
                    }

                    if let insight {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Total spent, all time").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            Text("\(formatMoneySpending(insight.totalSpent)) RWF").font(.title).bold().foregroundColor(IDS.Colors.textPrimary)
                            Text("Real, ledger-based -- what every account debit actually paid for.")
                                .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)

                        if insight.categories.isEmpty {
                            EmptyStateView("No spending recorded yet — your breakdown will show up here once you use your account.")
                        } else {
                            let maxAmount = max(insight.categories.map(\.amount).max() ?? 1, 1)
                            VStack(alignment: .leading, spacing: 12) {
                                Text("By category").bold().foregroundColor(IDS.Colors.textPrimary)
                                ForEach(insight.categories, id: \.name) { category in
                                    VStack(alignment: .leading, spacing: 4) {
                                        HStack {
                                            Text(category.name).font(.subheadline)
                                            Spacer()
                                            Text("\(formatMoneySpending(category.amount)) RWF").font(.subheadline).bold()
                                        }
                                        GeometryReader { geo in
                                            ZStack(alignment: .leading) {
                                                Capsule().fill(Color(.tertiarySystemBackground)).frame(height: 6)
                                                Capsule().fill(IDS.Colors.brand)
                                                    .frame(width: geo.size.width * CGFloat(category.amount / maxAmount), height: 6)
                                            }
                                        }
                                        .frame(height: 6)
                                    }
                                }
                            }
                            .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
                        }

                        BudgetsSection(categories: insight.categories, budgets: budgets, onBudgetsChanged: { budgets = $0 })
                    } else {
                        ProgressView().frame(maxWidth: .infinity).padding(40)
                    }
                }
                .padding(.horizontal)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task {
            do {
                insight = try await NetworkClient.shared.getSpendingInsight()
            } catch {
                self.error = "Could not load your spending."
            }
            do {
                budgets = try await NetworkClient.shared.getBudgets().budgets
            } catch {
                budgets = []
            }
            monthlyReport = try? await NetworkClient.shared.getMonthlySpendingReport()
        }
    }
}

// Real Toss budgets/limits equivalent (item 173) -- mirrors bank-mfe's own
// BudgetsSection/SetBudgetForm (item 165) and Android's BudgetsSection/SetBudgetForm
// (item 172): per-category or overall (category == nil) monthly limit, progress bar
// color-coded by real UNDER/NEAR/OVER status.
private struct BudgetsSection: View {
    let categories: [SpendingCategoryDto]
    let budgets: [BudgetViewDto]?
    let onBudgetsChanged: ([BudgetViewDto]) -> Void

    @State private var showForm = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Budgets").bold().foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Button(showForm ? "Cancel" : "+ Set budget") { showForm.toggle() }
                    .font(.caption).foregroundColor(IDS.Colors.brand)
            }

            if showForm {
                SetBudgetForm(categories: categories, onSet: { category, limit in
                    showForm = false
                    Task {
                        do {
                            _ = try await NetworkClient.shared.setBudget(category: category, monthlyLimit: limit)
                            onBudgetsChanged(try await NetworkClient.shared.getBudgets().budgets)
                        } catch {
                            // leave existing budgets list as-is on failure
                        }
                    }
                })
            }

            if let budgets, !budgets.isEmpty {
                ForEach(budgets) { budget in BudgetCard(budget: budget) }
            }
        }
        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
    }
}

private struct BudgetCard: View {
    let budget: BudgetViewDto

    private var barColor: Color {
        switch budget.status {
        case "OVER": return .red
        case "NEAR": return Color(red: 0.96, green: 0.65, blue: 0.14)
        default: return IDS.Colors.brand
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(budget.category ?? "Overall spending").font(.subheadline)
                Spacer()
                Text("\(formatMoneySpending(budget.spent)) / \(formatMoneySpending(budget.monthlyLimit)) RWF")
                    .font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(Color(.tertiarySystemBackground)).frame(height: 6)
                    Capsule().fill(barColor)
                        .frame(width: geo.size.width * CGFloat(min(max(Double(budget.percentUsed) / 100, 0), 1)), height: 6)
                }
            }
            .frame(height: 6)
            if budget.status == "OVER" {
                Text("Over budget").font(.caption2).foregroundColor(barColor)
            } else if budget.status == "NEAR" {
                Text("Nearing your limit").font(.caption2).foregroundColor(barColor)
            }
        }
    }
}

private struct SetBudgetForm: View {
    let categories: [SpendingCategoryDto]
    let onSet: (String?, Double) -> Void

    @State private var selectedCategory: String?
    @State private var limitText = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Menu(selectedCategory ?? "Overall spending") {
                Button("Overall spending") { selectedCategory = nil }
                ForEach(categories, id: \.name) { category in
                    Button(category.name) { selectedCategory = category.name }
                }
            }
            .font(.subheadline).foregroundColor(IDS.Colors.textPrimary)

            IdsTextField("Monthly limit (RWF)", text: $limitText, keyboardType: .decimalPad)

            Button(action: {
                if let limit = Double(limitText.trimmingCharacters(in: .whitespaces)), limit > 0 {
                    onSet(selectedCategory, limit)
                }
            }) {
                Text("Save budget").bold().foregroundColor(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                    .background(IDS.Colors.brand).cornerRadius(10)
            }
        }
    }
}

private func formatMoneySpending(_ value: Double) -> String {
    let rounded = (value * 100).rounded() / 100
    return rounded == rounded.rounded(.down) ? String(Int64(rounded)) : String(format: "%.2f", rounded)
}
