import SwiftUI
import CoreDesignSystem

/// Real device binding step-up dialog (2026-07-28 port, item 97) -- shown when
/// chargeCard real-403s with DEVICE_NOT_VERIFIED. Mirrors ios/App/Sources/
/// DeviceStepUpView.swift and Android merchantapp's DeviceStepUpDialog.kt exactly:
/// same copy, same shape (password field, Cancel/Verify), self-contained here since
/// MerchantApp has no Settings screen or shared step-up host to factor this into.
struct DeviceStepUpDialog: View {
    let onVerified: () -> Void
    let onCancel: () -> Void

    @State private var password = ""
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                LockGlyph(size: 16)
                Text("Verify this device")
            }
                .font(IDS.scaledFont(size: 16, weight: .bold, relativeTo: .subheadline))
            Text("This is a new device for your account. Re-enter your password to allow it to move money, then try again.")
                .font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote))
                .foregroundColor(.secondary)
            SecureField("Password", text: $password)
                .padding(12)
                .background(Color(.secondarySystemBackground))
                .cornerRadius(10)
            if let error {
                Text(error).font(IDS.scaledFont(size: 12, weight: .regular, relativeTo: .caption1)).foregroundColor(.red)
            }
            HStack(spacing: 10) {
                Button("Cancel", action: onCancel)
                    .disabled(busy)
                Spacer()
                Button(action: { Task { await verify() } }) {
                    Text(busy ? "Verifying…" : "Verify device").fontWeight(.semibold)
                }
                .disabled(busy || password.isEmpty)
            }
        }
        .padding(16)
        .background(Color(.systemBackground))
        .cornerRadius(16)
        .shadow(radius: 12)
        .padding(.horizontal, 24)
    }

    private func verify() async {
        error = nil
        busy = true
        defer { busy = false }
        do {
            _ = try await MerchantNetworkClient.shared.verifyDevice(password: password)
            onVerified()
        } catch let NetworkError.httpError(statusCode) {
            error = statusCode == 400 ? "Incorrect password." : "Something went wrong. Please try again."
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}
