import SwiftUI

/// Real device binding step-up dialog (2026-07-21 port) -- shown wherever a
/// money-moving call real-403s with DEVICE_NOT_VERIFIED. Re-proves password
/// ownership on THIS device (resolved server-side from the caller's own JWT, never a
/// client-supplied id) and marks it trusted, matching the same real re-verification
/// Toss requires before a new device can move money. Mirrors bank-mfe's
/// DeviceStepUpPrompt (BankDashboard.tsx) / Android's DeviceStepUpDialog exactly --
/// same copy, same shape (password field, Cancel/Verify).
struct DeviceStepUpView: View {
    @State private var password = ""
    let busy: Bool
    let error: String?
    let onVerify: (String) -> Void
    let onCancel: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("\u{1F512} Verify this device")
                .font(.system(size: 16, weight: .bold))
            Text("This is a new device for your account. Re-enter your password to allow it to send money, then try again.")
                .font(.system(size: 13))
                .foregroundColor(.secondary)
            SecureField("Password", text: $password)
                .padding(12)
                .background(Color(.secondarySystemBackground))
                .cornerRadius(10)
            if let error {
                Text(error).font(.system(size: 12)).foregroundColor(.red)
            }
            HStack(spacing: 10) {
                Button("Cancel", action: onCancel)
                    .disabled(busy)
                Spacer()
                Button(busy ? "Verifying…" : "Verify device") { onVerify(password) }
                    .disabled(busy || password.isEmpty)
                    .fontWeight(.semibold)
            }
        }
        .padding(16)
        .background(Color(.systemBackground))
        .cornerRadius(16)
        .shadow(radius: 12)
        .padding(.horizontal, 24)
    }
}
