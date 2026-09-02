import SwiftUI
import CoreDesignSystem
import FeaturePayments

// Real per-feature isolated preview app (2026-08-29) -- see FeatureBankingExampleApp
// for the full rationale (docs/TOSS_ARCHITECTURE_FACTS.md §8). Payments has the most
// real public screens of any module (transfer flow, transaction history), so this
// is a picker list like Banking's, not a single direct screen. Pay money detail
// moved out to FeaturePay (2026-09-02, Pay Feature-module decomposition) -- its
// only real caller was MyPaymentCodeCard.swift, which moved there too.
@main
struct FeaturePaymentsExampleApp: App {
    var body: some Scene {
        WindowGroup {
            NavigationStack {
                List {
                    NavigationLink("Transaction history") {
                        TransactionHistoryScreen(transactions: Self.sampleTransactions, onBack: {})
                    }
                    NavigationLink("Recipient entry") {
                        RecipientEntryScreen(onBack: {}, onNext: { _ in })
                    }
                    NavigationLink("Transfer amount") {
                        TransferAmountScreen(
                            recipientAccountNumber: "1234567890", availableBalance: 42_500,
                            onBack: {}, onConfirm: { _, _, _, _ in }
                        )
                    }
                    NavigationLink("Transfer quote (real biometric gate)") {
                        TransferQuoteScreen(
                            recipientName: "TUYIZERE Eric", amount: "10,000", fee: "100",
                            onConfirm: {}, onCancel: {}
                        )
                    }
                }
                .navigationTitle("Payments")
            }
        }
    }

    private static let sampleTransactions: [TransactionDisplayItem] = [
        TransactionDisplayItem(id: "1", description: "Kigali Fresh Market", amount: 4_500, currency: "RWF", status: "COMPLETED", isOutgoing: true, type: "PAYMENT", createdAt: "2026-08-28T10:00:00Z"),
        TransactionDisplayItem(id: "2", description: "MTN Airtime", amount: 2_000, currency: "RWF", status: "COMPLETED", isOutgoing: true, type: "BILL", createdAt: "2026-08-27T14:00:00Z"),
    ]
}
