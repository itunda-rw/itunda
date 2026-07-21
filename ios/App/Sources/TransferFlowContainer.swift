import SwiftUI
import FeaturePayments
import CoreIdentity

private enum TransferStep: Equatable {
    case recipient
    case amount(accountNumber: String)
}

/// Owns the real send-money flow's step state + network calls (2026-07-12) --
/// mirrors Android's ItundaAppScreen.kt transferStep handling exactly. Lives in the
/// App target because it needs TransferViewModel/NetworkClient, which
/// Features/Payments can't depend on (see BankView.swift's note on the same
/// constraint).
struct TransferFlowContainer: View {
    @StateObject private var viewModel = TransferViewModel()
    @State private var step: TransferStep = .recipient
    @State private var isSubmitting = false
    @State private var errorMessage: String?
    // Real device binding step-up (2026-07-21 port) -- see DeviceStepUpView's own doc
    // comment for the full account.
    @State private var showDeviceStepUp = false
    @State private var deviceStepUpBusy = false
    @State private var deviceStepUpError: String?
    // Remembers the in-flight amount across a device-not-verified -> verify -> retry
    // round trip -- `step` only carries the recipient's account number, not the
    // amount being sent.
    @State private var pendingAmountRwf = 0
    let availableBalance: Double
    let onDone: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            switch step {
            case .recipient:
                RecipientEntryScreen(
                    onBack: onDone,
                    onNext: { accountNumber in
                        // Found live on-device (2026-07-12): the account-number
                        // field's real software keyboard was still first responder
                        // at this exact instant. Switching `step` here swaps this
                        // fullScreenCover's content to TransferAmountScreen (no
                        // focused field) *without* the keyboard ever being told to
                        // resign -- the presentation's window was left permanently
                        // shrunk/shifted to accommodate a keyboard that no view
                        // was still requesting, cutting off the top of the screen
                        // (including the back button, confirmed unreachable even by
                        // a raw coordinate tap). Resigning first responder before
                        // the step switch is the fix: the keyboard dismisses on its
                        // own terms instead of being abandoned mid-avoidance.
                        Self.dismissKeyboard()
                        step = .amount(accountNumber: accountNumber)
                    }
                )
            case .amount(let accountNumber):
                TransferAmountScreen(
                    recipientAccountNumber: accountNumber,
                    availableBalance: availableBalance,
                    isSubmitting: isSubmitting,
                    onBack: { step = .recipient },
                    onConfirm: { amountRwf in confirm(accountNumber: accountNumber, amountRwf: amountRwf) }
                )
                if let errorMessage {
                    Text(errorMessage)
                        .font(.system(size: 13))
                        .foregroundColor(.red)
                        .padding(.horizontal, 24)
                        .padding(.top, 8)
                }
            }
        }
        // Found live on-device (2026-07-12): typing into RecipientEntryScreen's
        // account-number field (real software/hardware keyboard focus) and then
        // advancing `step` to .amount within this *same* already-presented
        // fullScreenCover -- not a fresh presentation -- left the whole screen's
        // content, including the back button, rendered off-screen above the top
        // edge, confirmed unreachable even by a raw coordinate tap at its visual
        // location. An explicit full-bleed frame alone didn't fix it (still
        // reproduced after adding it) -- the actual cause is this VStack retaining a
        // keyboard-avoidance safe-area adjustment from the .recipient step's focused
        // TextField that never clears once `step` swaps to .amount, since it's the
        // same persistent container across both cases, not a fresh presentation.
        // Opting this container out of automatic keyboard-safe-area avoidance is the
        // real fix.
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .ignoresSafeArea(.keyboard)
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

    private func verifyThenRetry(password: String) {
        deviceStepUpError = nil
        deviceStepUpBusy = true
        Task { @MainActor in
            let result = await viewModel.verifyDevice(password: password)
            deviceStepUpBusy = false
            switch result {
            case .success:
                showDeviceStepUp = false
                if case .amount(let accountNumber) = step {
                    isSubmitting = true
                    let retryResult = await viewModel.sendTransfer(recipientAccountNumber: accountNumber, amountRwf: pendingAmountRwf)
                    isSubmitting = false
                    if case .success = retryResult { onDone() }
                    else if case .failure(let message) = retryResult { errorMessage = message }
                }
            case .failure(let message):
                deviceStepUpError = message
            case .queued, .deviceNotVerified:
                break
            }
        }
    }

    private static func dismissKeyboard() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }

    // Toss-style biometric confirmation gate before a transfer completes, then the
    // real quote+confirm call -- mirrors Android's onConfirm handler in
    // ItundaAppScreen.kt exactly.
    private func confirm(accountNumber: String, amountRwf: Int) {
        errorMessage = nil
        pendingAmountRwf = amountRwf
        NIDABiometricAuth.shared.authenticateForTransaction(reason: "Confirm sending \(amountRwf) RWF") { success, error in
            guard success else {
                errorMessage = error?.localizedDescription ?? "Couldn't verify. Try again."
                return
            }
            Task { @MainActor in
                isSubmitting = true
                let result = await viewModel.sendTransfer(recipientAccountNumber: accountNumber, amountRwf: amountRwf)
                isSubmitting = false
                switch result {
                case .success:
                    onDone()
                // sendTransfer never actually returns .queued -- a transfer confirm
                // is deliberately never queued offline (see
                // TransferViewModel.depositToSavingsGoal's doc comment for why) --
                // handled only because MoneyActionResult is a shared enum.
                case .queued:
                    onDone()
                case .failure(let message):
                    errorMessage = message
                case .deviceNotVerified:
                    deviceStepUpError = nil
                    showDeviceStepUp = true
                }
            }
        }
    }
}
