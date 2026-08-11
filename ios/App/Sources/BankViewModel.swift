import Foundation
import FeatureBanking
import CoreNetwork

/// Real data backing BankView (2026-07-11) -- mirrors Android's MainViewModel.kt.
/// Lives in the App target, not Features/Banking, because BankView's module can't
/// depend back on App's NetworkClient/Wallet types (App depends on Feature, never
/// the reverse -- see Project.swift's featureModules list); ContentView owns this
/// and passes plain formatted values into BankView, the same way it already passes
/// plain strings into TransferQuoteScreen.
@MainActor
final class BankViewModel: ObservableObject {
    @Published private(set) var balanceText = "RWF 0"
    // Real Toss Bank reference (user-provided screenshots, 2026-08-11) -- see
    // AccountSummaryCard's own doc comment in BankView.swift.
    @Published private(set) var accountNumber: String?
    @Published private(set) var savingsRows: [SavingsRowData] = []
    @Published private(set) var discoverRows: [DiscoverRowData] = []
    @Published private(set) var isOffline = false
    // Raw values for screens that need to compute with them (send-money/deposit
    // flows), not just display them -- balanceText/savingsRows are formatted display
    // strings only.
    @Published private(set) var availableBalance: Double = 0
    @Published private(set) var savingsGoals: [SavingsGoal] = []
    @Published private(set) var interestJar: InterestJar?
    @Published private(set) var transactions: [TransactionDto] = []
    @Published private(set) var currentUserId: String?

    // Real offline queue + connectivity signal (2026-07-13) -- see
    // docs/TOSS_PARITY_MATRIX.md's Offline row. Started once, from init(), matching
    // Android's MainViewModel.init{} calling connectivityObserver.start immediately.
    private let connectivityObserver = ConnectivityObserver()
    private var replayRetryTimer: Timer?

    init() {
        connectivityObserver.start { [weak self] in
            Task { @MainActor in
                await self?.replayPendingActions()
            }
        }
        // Real bug found live (2026-07-13): the only other real replay trigger --
        // load()'s own opportunistic retry, see its doc comment -- only fires once,
        // right when a savings sheet closes (~1.5s after queuing, per
        // SavingsFlowContainer.swift's own delay). Confirmed live: that first
        // attempt lands *while the backend is still down* (a real outage doesn't
        // resolve in 1.5 seconds), fails, and nothing ever tries again -- the app
        // would sit on a permanently-stale queue until the user happened to
        // background/reopen the app or manually pull-to-refresh. A periodic retry
        // closes that gap for real: same idea as NWPathMonitor (react once
        // reachable again) but on a timer instead of an interface-level signal,
        // since nothing in this environment (host-shared Simulator networking, a
        // specific backend being down rather than the whole device) can signal
        // "the backend is back" proactively. 10s is a demo-appropriate interval,
        // not tuned against any real production SLA.
        replayRetryTimer = Timer.scheduledTimer(withTimeInterval: 10, repeats: true) { [weak self] _ in
            Task { @MainActor in
                await self?.replayPendingActions()
            }
        }
    }

    deinit {
        connectivityObserver.stop()
        replayRetryTimer?.invalidate()
    }

    /// Real replay of everything OfflineActionQueue has saved, via the same
    /// POST /api/v1/actions/batch endpoint a mobile client is meant to call. Each
    /// action carries its own already-generated idempotencyKey, so replaying the
    /// same queue twice never double-executes an already-completed deposit -- the
    /// backend's own IdempotencyService.replayOrExecute guarantees that
    /// server-side. Mirrors Android's MainViewModel.replayPendingActions exactly.
    /// Calls loadInternal(), not load(), to reload afterward -- see load()'s own
    /// comment for why that distinction matters.
    func replayPendingActions() async {
        let pending = OfflineActionQueue.shared.peekAll()
        guard !pending.isEmpty else { return }
        do {
            let actions = pending.map {
                BatchActionRequest(
                    clientActionId: $0.clientActionId,
                    type: "SAVINGS_DEPOSIT",
                    idempotencyKey: $0.idempotencyKey,
                    body: SavingsDepositActionBody(goalId: $0.goalId, amount: $0.amount)
                )
            }
            let response = try await NetworkClient.shared.submitActionBatch(BatchRequest(actions: actions))
            let handledIds = Set(response.results.map { $0.clientActionId })
            OfflineActionQueue.shared.removeByClientActionIds(handledIds)
            if !handledIds.isEmpty {
                await loadInternal()
            }
        } catch {
            // Still offline (or the reconnect was too brief) -- leave the queue
            // intact, the next real trigger will try again.
        }
    }

    /// Real bug found live while building this (2026-07-13): NWPathMonitor only
    /// observes the *device's* network interface state (Wi-Fi/cellular up or down)
    /// -- it has no concept of whether *this app's specific backend* is reachable.
    /// A backend that's down while the device's own network is perfectly fine
    /// (confirmed live: killing the local dev backend process on the host Mac,
    /// with the iOS Simulator's shared network interface never changing) never
    /// flips NWPathMonitor's status, so connectivityObserver alone would never
    /// trigger a replay in that real, common failure mode -- only a genuine
    /// device-level connectivity loss would. So load() also opportunistically
    /// tries a replay after every real successful fetch, not just on the proactive
    /// NWPathMonitor signal: the next time the app proves the backend is actually
    /// reachable (any successful load, e.g. the automatic reload ContentView
    /// already triggers when a savings sheet closes), that's a strictly stronger
    /// signal than "the network interface is up" anyway.
    func load() async {
        await loadInternal()
        if !isOffline {
            await replayPendingActions()
        }
    }

    private func loadInternal() async {
        do {
            let walletsRes = try await NetworkClient.shared.getWallets()
            if walletsRes.success, let wallet = walletsRes.wallets.first(where: { $0.type == "MAIN" }) ?? walletsRes.wallets.first {
                balanceText = formatAmount(wallet.balance, currency: wallet.currency)
                accountNumber = wallet.accountNumber
                availableBalance = wallet.availableBalance
                currentUserId = wallet.userId
            }

            let transactionsRes = try await NetworkClient.shared.getTransactionHistory()
            if transactionsRes.success {
                transactions = transactionsRes.transactions
            }

            var rows: [SavingsRowData] = []

            // Real bug found live (2026-07-13, same class as Android's
            // MainViewModel.fetchData() fix): an account that has never made an
            // interest-jar-eligible deposit gets a real 404 INTEREST_JAR_NOT_FOUND
            // from the backend -- a legitimate state for any new account, not a
            // failure. Left uncaught, that 404 (NetworkError.httpError, not
            // URLError) would abort this whole do block, silently skipping the
            // getSavingsGoals() call below too -- so a brand-new account's savings
            // goals would never load. Scoped narrowly to 404; any other status
            // still propagates to the outer catch as before.
            do {
                let jarRes = try await NetworkClient.shared.getInterestJar()
                if jarRes.success {
                    interestJar = jarRes.jar
                    rows.append(SavingsRowData(
                        title: "Interest jar",
                        subtitle: "Earned this month",
                        trailing: formatAmount(jarRes.jar.earnedThisMonth, currency: "RWF")
                    ))
                }
            } catch let NetworkError.httpError(statusCode) where statusCode == 404 {
                interestJar = nil
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

            // Real curated promo rail -- see rw.itunda.discover.web.DiscoverController
            // on the backend. Purely informational, so scoped in its own try/catch,
            // same discipline as the interest-jar 404 handling above: a Discover
            // hiccup must never block the rest of Home from loading real data.
            if let discoverRes = try? await NetworkClient.shared.getDiscoverItems(), discoverRes.success {
                // Real server-side ranking (2026-08-11) -- see DiscoverItem's own doc
                // comment (Toss Intelligence-banner research). Backend already returns
                // items sorted by priority; sorting here too makes that explicit,
                // matching Android's/web's identical defensive re-sort.
                discoverRows = discoverRes.items.sorted { $0.priority > $1.priority }.map {
                    DiscoverRowData(title: $0.title, subtitle: $0.subtitle, badge: $0.badge, isNew: $0.isNew)
                }
            }

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
