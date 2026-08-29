import SwiftUI
import FeatureBanking

// Real per-feature isolated preview app (2026-08-29) -- the "Example" half of
// Project.swift's makeMicroFeature helper, which has had the generic mechanism
// to declare this target since 2026-07-11 but found zero real content for it
// (docs/TOSS_ARCHITECTURE_FACTS.md §8's "Microfeatures" write-up: Toss's real
// per-feature Example app builds ~5x faster than the full app and is usable by
// design/PM for review without one). BankView's own public init already defaults
// every parameter specifically so it "compiles standalone" without a live backend
// (see its own doc comment) -- this app is that promise's first real call site.
@main
struct FeatureBankingExampleApp: App {
    var body: some Scene {
        WindowGroup {
            NavigationStack {
                List {
                    NavigationLink("Home (BankView)") {
                        BankView(
                            balanceText: "1,284,350 RWF",
                            accountNumber: "1234567890",
                            recentTransactions: Self.sampleTransactions
                        )
                    }
                    NavigationLink("Account ledger detail") {
                        AccountLedgerDetailView(
                            balanceText: "1,284,350 RWF",
                            accountNumber: "1234567890",
                            transactions: Self.sampleTransactions,
                            onBack: {},
                            onSend: {}
                        )
                    }
                }
                .navigationTitle("Banking")
            }
        }
    }

    private static let sampleTransactions: [RecentTransactionRowData] = [
        RecentTransactionRowData(
            id: "1", title: "Kigali Fresh Market", subtitle: "Completed",
            amountText: "-4,500 RWF", isOutgoing: true, type: "PAYMENT",
            createdAt: "2026-08-28T10:00:00Z", fee: 0, currency: "RWF", afterBalance: 1_279_850
        ),
        RecentTransactionRowData(
            id: "2", title: "Salary deposit", subtitle: "Completed",
            amountText: "+350,000 RWF", isOutgoing: false, type: "DEPOSIT",
            createdAt: "2026-08-25T09:00:00Z", fee: 0, currency: "RWF", afterBalance: 1_284_350
        ),
    ]
}
