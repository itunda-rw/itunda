import SwiftUI
import LocalAuthentication
import CoreDesignSystem
import CoreNetwork

/// Real app-launch biometric unlock gate -- closes the gap named in
/// docs/DESIGN_REFERENCES.md Section 8: Toss's real login credential is a 6-digit PIN
/// or Face ID, never a conventional password, but this was only ever real on Android
/// (`AppLockScreen.kt`, 2026-07-21), never wired on iOS. Pure reuse of
/// `LocalAuthentication`'s own real `LAContext.evaluatePolicy` biometric check -- the
/// direct iOS analog of Android's real Keystore/BiometricPrompt plumbing -- no new
/// crypto, no schema change, just a new call site gating entry into `ContentView` once
/// per process launch, shown only when the device actually has biometrics enrolled and
/// the user hasn't disabled it in Settings (see `KeychainTokenStore.isAppLockEnabled`).
///
/// Deliberately a single auto-triggered prompt, not a typed PIN field -- itunda has no
/// real PIN storage anywhere, matching Android's own honest scope. A device with no
/// biometrics enrolled skips this gate entirely (checked by the caller) rather than
/// blocking a real user out of the app.
struct AppLockScreenView: View {
    let onUnlocked: () -> Void

    @State private var error: String?
    @State private var checking = false

    var body: some View {
        ZStack {
            IDS.Colors.backgroundPrimary.ignoresSafeArea()
            VStack(spacing: 16) {
                ZStack {
                    Circle().fill(Color(.tertiarySystemBackground)).frame(width: 76, height: 76)
                    Image(systemName: "faceid").font(.system(size: 32)).foregroundColor(IDS.Colors.brand)
                }
                Text("Itunda is locked").font(.title2).bold().foregroundColor(IDS.Colors.textPrimary)
                if let error {
                    Text(error).font(.subheadline).foregroundColor(.red)
                } else {
                    Text("Verify your identity to continue").font(.subheadline).foregroundColor(.secondary)
                }
                Button(action: attemptUnlock) {
                    Text(checking ? "Checking…" : "Unlock")
                        .bold().foregroundColor(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 14)
                        .background(IDS.Colors.brand).cornerRadius(12)
                }
                .disabled(checking)
                .padding(.horizontal, 40)
            }
        }
        .onAppear { attemptUnlock() }
    }

    // Auto-trigger once on first appearance -- matching Face ID's own real-world
    // convention (prompt appears immediately, not behind an extra tap) rather than
    // making a real user tap an extra "Unlock" button just to see the prompt.
    private func attemptUnlock() {
        error = nil
        checking = true
        let context = LAContext()
        context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: "Unlock Itunda") { success, evalError in
            DispatchQueue.main.async {
                checking = false
                if success {
                    onUnlocked()
                } else {
                    error = evalError?.localizedDescription ?? "Could not verify your identity."
                }
            }
        }
    }
}

/// Real device-capability check -- lets a caller (the app-launch unlock gate, and its
/// own Settings toggle) decide whether to even offer a biometric prompt. Mirrors
/// Android's `NIDABiometricAuth.isAvailable()`.
func isBiometricUnlockAvailable() -> Bool {
    LAContext().canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
}
