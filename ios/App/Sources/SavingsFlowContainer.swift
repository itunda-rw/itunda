import SwiftUI
import FeaturePayments

/// Owns the real savings deposit/claim flow's network calls (2026-07-12) -- mirrors
/// TransferFlowContainer.swift's pattern exactly. Lives in the App target because it
/// needs TransferViewModel/NetworkClient, which Features/Payments can't depend on.
struct SavingsFlowContainer: View {
    @StateObject private var viewModel = TransferViewModel()
    @State private var isSubmitting = false
    @State private var errorMessage: String?
    // Real offline queueing (2026-07-13) -- distinct from errorMessage (red) since
    // this isn't an error, it's confirmation the deposit was saved for later.
    @State private var queuedMessage: String?
    // Real device binding step-up (2026-07-21 port) -- see DeviceStepUpView's own doc
    // comment for the full account.
    @State private var showDeviceStepUp = false
    @State private var deviceStepUpBusy = false
    @State private var deviceStepUpError: String?
    // Remembers the in-flight deposit amount across a device-not-verified -> verify
    // -> retry round trip -- claimInterest takes no amount, so this is only read back
    // for the .deposit case.
    @State private var pendingAmountRwf = 0
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
                        pendingAmountRwf = amountRwf
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
            if let queuedMessage {
                Text(queuedMessage)
                    .font(.system(size: 13))
                    .foregroundColor(.blue)
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
        .overlay {
            if showDeviceStepUp {
                Color.black.opacity(0.3).ignoresSafeArea()
                DeviceStepUpView(
                    busy: deviceStepUpBusy,
                    error: deviceStepUpError,
                    onVerify: { password in verifyThenRetry(password: password) },
                    onCancel: { showDeviceStepUp = false; deviceStepUpError = nil }
                )
            }
        }
    }

    private func handle(_ result: MoneyActionResult) {
        switch result {
        case .success: onDone()
        case .queued(let message):
            queuedMessage = message
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { onDone() }
        case .failure(let message): errorMessage = message
        case .deviceNotVerified:
            deviceStepUpError = nil
            showDeviceStepUp = true
        }
    }

    private func verifyThenRetry(password: String) {
        deviceStepUpError = nil
        deviceStepUpBusy = true
        Task { @MainActor in
            let result = await viewModel.verifyDevice(password: password)
            deviceStepUpBusy = false
            switch result {
            case .success:
                showDeviceStepUp = false
                isSubmitting = true
                let retryResult: MoneyActionResult
                switch step {
                case .deposit(let goalId, _):
                    retryResult = await viewModel.depositToSavingsGoal(goalId: goalId, amountRwf: pendingAmountRwf)
                case .claimInterest:
                    retryResult = await viewModel.claimInterest()
                }
                isSubmitting = false
                if case .success = retryResult { onDone() }
                else if case .failure(let message) = retryResult { errorMessage = message }
            case .failure(let message):
                deviceStepUpError = message
            case .queued, .deviceNotVerified:
                break
            }
        }
    }
}
