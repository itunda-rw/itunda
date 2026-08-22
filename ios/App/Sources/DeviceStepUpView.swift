import SwiftUI
import CoreNetwork
import CoreIdentity
import CoreDesignSystem

/// Real device binding step-up dialog (2026-07-21 port) -- shown wherever a
/// money-moving call real-403s with DEVICE_NOT_VERIFIED. Re-proves password
/// ownership on THIS device (resolved server-side from the caller's own JWT, never a
/// client-supplied id) and marks it trusted, matching the same real re-verification
/// Toss requires before a new device can move money. Mirrors bank-mfe's
/// DeviceStepUpPrompt (BankDashboard.tsx) / Android's DeviceStepUpDialog exactly --
/// same copy, same shape (password field, Cancel/Verify).
///
/// Localized 2026-08-08 (docs/DESIGN_REFERENCES.md Section 19) -- reuses
/// AppLocale/loadStoredLocale from LoginScreen.swift directly since this file lives
/// in the same App target (unlike Features/Payments' TransferFlowScreens.swift,
/// which needed its own copy). Worth doing here specifically because this dialog is
/// shared by four money-moving flows (Gift, Commerce, Eats, Stocks -- see
/// DeviceStepUpHost's own doc comment below), not just Transfer.
private let deviceStepUpStrings: [AppLocale: [String: String]] = [
    .en: [
        "title": "Verify this device",
        "body": "This is a new device for your account. Re-enter your password to allow it to send money, then try again.",
        "password": "Password",
        "showPassword": "Show password",
        "hidePassword": "Hide password",
        "cancel": "Cancel",
        "verifying": "Verifying…",
        "verifyDevice": "Verify device",
    ],
    .rw: [
        "title": "Emeza iyi terefoni",
        "body": "Iyi ni terefoni nshya kuri konti yawe. Ongera wandike ijambo ry'ibanga kugira ngo wemeze ko ishobora kohereza amafaranga, hanyuma ugerageze nanone.",
        "password": "Ijambo ry'ibanga",
        "showPassword": "Erekana ijambo ry'ibanga",
        "hidePassword": "Hisha ijambo ry'ibanga",
        "cancel": "Hagarika",
        "verifying": "Kwemeza…",
        "verifyDevice": "Emeza terefoni",
    ],
    // Real gap found 2026-08-15: this dict had zero French entries -- same class of
    // staleness as SettingsScreen.swift's identical gap, found the same day.
    .fr: [
        "title": "Vérifiez cet appareil",
        "body": "Il s'agit d'un nouvel appareil pour votre compte. Ressaisissez votre mot de passe pour l'autoriser à envoyer de l'argent, puis réessayez.",
        "password": "Mot de passe",
        "showPassword": "Afficher le mot de passe",
        "hidePassword": "Masquer le mot de passe",
        "cancel": "Annuler",
        "verifying": "Vérification…",
        "verifyDevice": "Vérifier l'appareil",
    ],
]

private func dsu(_ key: String) -> String {
    let locale = loadStoredLocale()
    return deviceStepUpStrings[locale]?[key] ?? deviceStepUpStrings[.en]?[key] ?? key
}

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
            HStack(spacing: 6) {
                LockGlyph(size: 16)
                Text(dsu("title"))
            }
                .font(.system(size: 16, weight: .bold))
            Text(dsu("body"))
                .font(.system(size: 13))
                .foregroundColor(.secondary)
            // Real "Minimum Input" simplicity fix (docs/DESIGN_REFERENCES.md §11, rule #4),
            // matching the identical same-day fix on Android's DeviceStepUpDialog: this
            // password field is the sole meaningful action on the entire dialog.
            HStack {
                Group {
                    if passwordVisible {
                        TextField(dsu("password"), text: $password)
                    } else {
                        SecureField(dsu("password"), text: $password)
                    }
                }
                .focused($passwordFocused)
                // Real touch-target-size fix (WCAG 2.5.8, this session's own established
                // convention): a bare Image(systemName:) inside a Button has no automatic
                // minimum tap area on iOS.
                Button(action: { passwordVisible.toggle() }) {
                    Image(systemName: passwordVisible ? "eye.slash" : "eye")
                        .foregroundColor(.secondary)
                        .frame(width: 24, height: 24)
                }
                .accessibilityLabel(passwordVisible ? dsu("hidePassword") : dsu("showPassword"))
            }
            .padding(12)
            .background(Color(.secondarySystemBackground))
            .cornerRadius(10)
            .onAppear { passwordFocused = true }
            if let error {
                Text(error).font(.system(size: 12)).foregroundColor(.red)
            }
            HStack(spacing: 10) {
                Button(dsu("cancel"), action: onCancel)
                    .disabled(busy)
                Spacer()
                Button(busy ? dsu("verifying") : dsu("verifyDevice")) { onVerify(password) }
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
            .task(id: visible) {
                await tryBiometricStepUp()
            }
        }
    }

    // Real biometric-first step-up (item 246): if this device already registered a
    // Secure Enclave key (Settings > Security > "Verify this device with biometrics"),
    // try that path automatically as soon as this dialog appears -- a challenge +
    // hardware-bound signature, no password retyping. Any failure (declined,
    // unavailable, no key) falls through silently to the password field already
    // rendered above, never blocking the user on a biometric-only path.
    @MainActor
    private func tryBiometricStepUp() async {
        guard DeviceKeyManager.shared.hasKey() else { return }
        busy = true
        do {
            let challengeResponse = try await NetworkClient.shared.issueDeviceChallenge()
            guard let challengeData = Data(base64Encoded: challengeResponse.challenge) else {
                busy = false
                return
            }
            let signatureBase64: String? = await withCheckedContinuation { continuation in
                DeviceKeyManager.shared.signChallenge(challengeData, reason: "Verify this device to continue") { signature, _ in
                    continuation.resume(returning: signature)
                }
            }
            guard let signatureBase64 else {
                busy = false
                return
            }
            _ = try await NetworkClient.shared.verifyDeviceSignature(signature: signatureBase64)
            busy = false
            await onVerified()
        } catch {
            busy = false
        }
    }
}
