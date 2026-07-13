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
    }

    private static func dismissKeyboard() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }

    // Toss-style biometric confirmation gate before a transfer completes, then the
    // real quote+confirm call -- mirrors Android's onConfirm handler in
    // ItundaAppScreen.kt exactly.
    private func confirm(accountNumber: String, amountRwf: Int) {
        errorMessage = nil
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
                }
            }
        }
    }
}
