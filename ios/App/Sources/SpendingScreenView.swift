import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real Kakao Pay 소비 리포트-style spending categorization (rw.itunda.wallet.
// WalletService.getSpendingInsight, real since 2026-07-13) -- first iOS client for this
// feature (item 108, found backend-only via a fresh matrix scan; bank-mfe/Android
// ported the same day as items 106/107). Same no-ViewModel,
// "call NetworkClient.shared directly from Task {} blocks" convention as
// MiniWalletScreenView.swift/GroupAccountScreenView.swift.
struct SpendingScreenView: View {
    var onBack: () -> Void = {}
    @State private var insight: SpendingInsightResponse?
    @State private var error: String?

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { Image(systemName: "chevron.left").foregroundColor(IDS.Colors.textPrimary) }
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
                    if let insight {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Total spent, all time").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            Text("\(formatMoneySpending(insight.totalSpent)) RWF").font(.title).bold().foregroundColor(IDS.Colors.textPrimary)
                            Text("Real, ledger-based -- what every wallet debit actually paid for.")
                                .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)

                        if insight.categories.isEmpty {
                            Text("No spending recorded yet.").font(.footnote).foregroundColor(IDS.Colors.textSecondary)
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
        }
    }
}

private func formatMoneySpending(_ value: Double) -> String {
    let rounded = (value * 100).rounded() / 100
    return rounded == rounded.rounded(.down) ? String(Int64(rounded)) : String(format: "%.2f", rounded)
}
