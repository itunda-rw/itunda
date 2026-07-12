import SwiftUI
import FeaturePayments

/// Owns the real savings deposit/claim flow's network calls (2026-07-12) -- mirrors
/// TransferFlowContainer.swift's pattern exactly. Lives in the App target because it
/// needs TransferViewModel/NetworkClient, which Features/Payments can't depend on.
struct SavingsFlowContainer: View {
    @StateObject private var viewModel = TransferViewModel()
    @State private var isSubmitting = false
    @State private var errorMessage: String?
    let step: SavingsFlowStep
    let availableBalance: Double
    let onDone: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            switch step {
            case .deposit(let goalId, let goalName):
                SavingsAmountScreen(
                    goalName: goalName,
                    mode: .deposit,
                    availableBalance: availableBalance,
                    isSubmitting: isSubmitting,
                    onBack: onDone,
                    onConfirm: { amountRwf in
                        Task { @MainActor in
                            isSubmitting = true
                            let result = await viewModel.depositToSavingsGoal(goalId: goalId, amountRwf: amountRwf)
                            isSubmitting = false
                            handle(result)
                        }
                    }
                )
            case .claimInterest:
                SavingsAmountScreen(
                    goalName: "Interest jar",
                    mode: .claimInterest,
                    availableBalance: availableBalance,
                    isSubmitting: isSubmitting,
                    onBack: onDone,
                    onConfirm: { _ in
                        Task { @MainActor in
                            isSubmitting = true
                            let result = await viewModel.claimInterest()
                            isSubmitting = false
                            handle(result)
                        }
                    }
                )
            }
            if let errorMessage {
                Text(errorMessage)
                    .font(.system(size: 13))
                    .foregroundColor(.red)
                    .padding(.horizontal, 24)
                    .padding(.top, 8)
            }
        }
        // Defensive, matching TransferFlowContainer.swift's real fix (2026-07-12) --
        // this container isn't reachable via the exact same keyboard-focus-then-step-
        // switch path that broke transfer (savingsFlowStep is a fullScreenCover(item:),
        // re-presented fresh on each change, not an internal step switch within one
        // already-presented cover), but there's no reason to leave it without the same
        // explicit full-bleed frame.
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func handle(_ result: MoneyActionResult) {
        switch result {
        case .success: onDone()
        case .failure(let message): errorMessage = message
        }
    }
}
