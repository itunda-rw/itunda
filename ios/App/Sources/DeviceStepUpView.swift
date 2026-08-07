import SwiftUI
import CoreNetwork

/// Real device binding step-up dialog (2026-07-21 port) -- shown wherever a
/// money-moving call real-403s with DEVICE_NOT_VERIFIED. Re-proves password
/// ownership on THIS device (resolved server-side from the caller's own JWT, never a
/// client-supplied id) and marks it trusted, matching the same real re-verification
/// Toss requires before a new device can move money. Mirrors bank-mfe's
/// DeviceStepUpPrompt (BankDashboard.tsx) / Android's DeviceStepUpDialog exactly --
/// same copy, same shape (password field, Cancel/Verify).
struct DeviceStepUpView: View {
    @State private var password = ""
    @FocusState private var passwordFocused: Bool
    // Real "Minimum Input" simplicity addition (docs/DESIGN_REFERENCES.md §11/§12), matching
    // the identical same-day fix on the shared IdsTextField's own isSecure toggle: a local
    // UI-only affordance, not a security control.
    @State private var passwordVisible = false
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
            // Real "Minimum Input" simplicity fix (docs/DESIGN_REFERENCES.md §11, rule #4),
            // matching the identical same-day fix on Android's DeviceStepUpDialog: this
            // password field is the sole meaningful action on the entire dialog.
            HStack {
                Group {
                    if passwordVisible {
                        TextField("Password", text: $password)
                    } else {
                        SecureField("Password", text: $password)
                    }
                }
                .focused($passwordFocused)
                Button(action: { passwordVisible.toggle() }) {
                    Image(systemName: passwordVisible ? "eye.slash" : "eye")
                        .foregroundColor(.secondary)
                }
                .accessibilityLabel(passwordVisible ? "Hide password" : "Show password")
            }
            .padding(12)
            .background(Color(.secondarySystemBackground))
            .cornerRadius(10)
            .onAppear { passwordFocused = true }
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

/// Real device binding step-up host, factored out 2026-07-21 after the fourth copy of
/// this exact busy/error/verify-then-retry pattern (Gift send/claim, Commerce
/// checkout, Eats checkout, Stocks buy/sell -- see TalkScreen.swift/ShopScreen.swift/
/// EatsScreen.swift/InvestScreenView.swift) would otherwise have hand-duplicated the
/// same logic. Mirrors Android's DeviceStepUpHost.kt exactly: verifies the current
/// device, then invokes the caller's retry closure.
struct DeviceStepUpHost: View {
    let visible: Bool
    let onDismiss: () -> Void
    let onVerified: () async -> Void

    @State private var busy = false
    @State private var error: String?

    var body: some View {
        if visible {
            Color.black.opacity(0.3).ignoresSafeArea()
            DeviceStepUpView(
                busy: busy,
                error: error,
                onVerify: { password in
                    error = nil
                    busy = true
                    Task { @MainActor in
                        do {
                            _ = try await NetworkClient.shared.verifyDevice(password: password)
                            busy = false
                            await onVerified()
                        } catch let NetworkError.httpError(statusCode) {
                            busy = false
                            error = statusCode == 400 ? "Incorrect password." : "Something went wrong. Please try again."
                        } catch {
                            busy = false
                            self.error = "Couldn't reach itunda. Check your connection and try again."
                        }
                    }
                },
                onCancel: onDismiss
            )
        }
    }
}
