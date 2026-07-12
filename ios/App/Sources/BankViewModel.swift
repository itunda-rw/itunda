import Foundation
import FeatureBanking

/// Real data backing BankView (2026-07-11) -- mirrors Android's MainViewModel.kt.
/// Lives in the App target, not Features/Banking, because BankView's module can't
/// depend back on App's NetworkClient/Wallet types (App depends on Feature, never
/// the reverse -- see Project.swift's featureModules list); ContentView owns this
/// and passes plain formatted values into BankView, the same way it already passes
/// plain strings into TransferQuoteScreen.
@MainActor
final class BankViewModel: ObservableObject {
    @Published private(set) var balanceText = "RWF 0"
    @Published private(set) var savingsRows: [SavingsRowData] = []
    @Published private(set) var isOffline = false
    // Raw values for screens that need to compute with them (send-money/deposit
    // flows), not just display them -- balanceText/savingsRows are formatted display
    // strings only.
    @Published private(set) var availableBalance: Double = 0
    @Published private(set) var savingsGoals: [SavingsGoal] = []
    @Published private(set) var interestJar: InterestJar?
    @Published private(set) var transactions: [TransactionDto] = []
    @Published private(set) var currentUserId: String?

    func load() async {
        do {
            let walletsRes = try await NetworkClient.shared.getWallets()
            if walletsRes.success, let wallet = walletsRes.wallets.first(where: { $0.type == "MAIN" }) ?? walletsRes.wallets.first {
                balanceText = formatAmount(wallet.balance, currency: wallet.currency)
                availableBalance = wallet.availableBalance
                currentUserId = wallet.userId
            }

            let transactionsRes = try await NetworkClient.shared.getTransactionHistory()
            if transactionsRes.success {
                transactions = transactionsRes.transactions
            }

            var rows: [SavingsRowData] = []

            let jarRes = try await NetworkClient.shared.getInterestJar()
            if jarRes.success {
                interestJar = jarRes.jar
                rows.append(SavingsRowData(
                    title: "Interest jar",
                    subtitle: "Earned this month",
                    trailing: formatAmount(jarRes.jar.earnedThisMonth, currency: "RWF")
                ))
            }

            let savingsRes = try await NetworkClient.shared.getSavingsGoals()
            if savingsRes.success {
                savingsGoals = savingsRes.goals
                for goal in savingsRes.goals {
                    let percent = goal.targetAmount > 0 ? Int(goal.currentAmount / goal.targetAmount * 100) : 0
                    rows.append(SavingsRowData(
                        title: goal.name,
                        subtitle: "\(formatAmount(goal.currentAmount, currency: "RWF")) of \(formatAmount(goal.targetAmount, currency: "RWF"))",
                        trailing: "\(percent)%"
                    ))
                }
            }
            savingsRows = rows
            isOffline = false
        } catch is URLError {
            // Genuinely unreachable backend -- the only case that should fall back to
            // a placeholder; an HTTP error (expired session, etc.) is a real response
            // and is deliberately NOT caught here, matching MainViewModel.kt's
            // isOffline distinction.
            isOffline = true
        } catch {
            // Non-connectivity failure (e.g. decoding) -- leave existing state rather
            // than overwrite real or offline state with something misleading.
        }
    }

    private func formatAmount(_ value: Double, currency: String) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.maximumFractionDigits = 0
        formatter.groupingSeparator = ","
        let number = formatter.string(from: NSNumber(value: value)) ?? "0"
        return "\(currency) \(number)"
    }
}
