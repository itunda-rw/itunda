import SwiftUI
import FeaturePaymentsInterface
import CoreDesignSystem
import CoreIdentity

/// Toss-style Transfer Quote Screen
public struct TransferQuoteScreen: View {
    let recipientName: String
    let amount: String
    let fee: String
    let onConfirm: () -> Void
    let onCancel: () -> Void

    @State private var biometricError: String?

    public init(recipientName: String, amount: String, fee: String, onConfirm: @escaping () -> Void, onCancel: @escaping () -> Void) {
        self.recipientName = recipientName
        self.amount = amount
        self.fee = fee
        self.onConfirm = onConfirm
        self.onCancel = onCancel
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Transfer to \(recipientName)")
                .font(IdsTypeScale.title1)
                .foregroundColor(IdsPalette.gray900)
                .padding(.horizontal, 24)
                .padding(.top, 40)

            Spacer().frame(height: 32)

            IdsListRow(title: "Transfer Amount", rightText: "\(amount) RWF", action: {})

            IdsListRow(title: "Fee", rightText: fee == "0" ? "Free" : "\(fee) RWF", action: {})

            if let biometricError {
                Text(biometricError)
                    .font(IdsTypeScale.body2)
                    .foregroundColor(IdsPalette.red500)
                    .padding(.horizontal, 24)
                    .padding(.top, 8)
            }

            Spacer()

            HStack(spacing: 16) {
                IdsButton(text: "Cancel", action: onCancel)
                IdsButton(text: "Confirm & Send", action: confirmWithBiometrics)
            }
            .padding(24)
        }
    }

    // Toss-style biometric confirmation gate before a transfer completes --
    // see docs/ARCHITECTURE.md's NIDABiometricAuth note. Mirrors the Android
    // wiring in ItundaAppScreen.kt (2026-07-11); previously this class had
    // real LocalAuthentication logic with zero call sites anywhere in ios/.
    private func confirmWithBiometrics() {
        biometricError = nil
        NIDABiometricAuth.shared.authenticateForTransaction(reason: "Confirm sending \(amount) RWF") { success, error in
            if success {
                onConfirm()
            } else {
                biometricError = error?.localizedDescription ?? "Couldn't verify. Try again."
            }
        }
    }
}
