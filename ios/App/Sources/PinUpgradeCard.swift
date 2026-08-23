import SwiftUI
import CoreNetwork
import CoreDesignSystem

/// Real Toss-sourced passwordless-login rollout (2026-08-23) -- see AccountPinPad
/// .swift's own doc comment and backend User.pinSet's own doc comment for the full
/// account. `pinSet == false` only for a pre-PIN-era account whose existing
/// password hasn't been upgraded to a real 6-digit PIN yet -- a real, non-blocking
/// offer, never forced: the existing password keeps working exactly as before
/// either way (AuthService.login is shape-agnostic). Own file (not inline in
/// BenefitsShopAllScreens.swift, already one of this codebase's own oversized
/// files) matching this session's own established file-size-lint discipline for
/// new, self-contained pieces. Flat, no card background -- matches this codebase's
/// own standing "new/touched screens are flat" design law
/// (docs/UI_UX_GUIDELINES.md), mirroring Android's/bank-mfe's identical choice here.
struct PinUpgradeCard: View {
    private enum Step { case closed, credential, create, confirm, success }

    @State private var pinSet: Bool?
    @State private var step: Step = .closed
    @State private var currentCredential = ""
    @State private var newPin: String?
    @State private var error: String?
    @State private var busy = false

    var body: some View {
        Group {
            if pinSet == false {
                content
            }
        }
        .task {
            do {
                pinSet = try await NetworkClient.shared.getProfile().user.pinSet ?? true
            } catch {
                // Best-effort, matching this screen's own real precedent (VerificationCard).
            }
        }
    }

    private var content: some View {
        VStack(alignment: .leading, spacing: 8) {
            if step == .success {
                Text("Your 6-digit PIN is set — use it to sign in next time.")
                    .font(.caption)
                    .foregroundColor(IDS.Colors.success)
            } else {
                Text("Set your 6-digit PIN")
                    .font(.subheadline).bold()
                    .foregroundColor(IDS.Colors.textPrimary)
                Text("A quick 6-digit PIN replaces typing your password to sign in — the same real simplification Toss uses.")
                    .font(.caption)
                    .foregroundColor(IDS.Colors.textSecondary)

                switch step {
                case .closed:
                    Button(action: { step = .credential }) {
                        Text("Set up my PIN")
                            .font(.caption).bold()
                            .foregroundColor(.white)
                            .padding(.horizontal, 14).padding(.vertical, 8)
                            .background(IDS.Colors.brand)
                            .cornerRadius(8)
                    }
                case .credential:
                    SecureField("Your current password", text: $currentCredential)
                        .padding(10)
                        .background(IDS.Colors.chipBackground)
                        .cornerRadius(8)
                    Button(action: { error = nil; step = .create }) {
                        Text("Continue")
                            .font(.caption).bold()
                            .foregroundColor(.white)
                            .padding(.horizontal, 14).padding(.vertical, 8)
                            .background(currentCredential.isEmpty ? IDS.Colors.textTertiary : IDS.Colors.brand)
                            .cornerRadius(8)
                    }
                    .disabled(currentCredential.isEmpty)
                case .create:
                    AccountPinPad(
                        headline: "Create a 6-digit PIN",
                        errorMessage: error,
                        onComplete: { entered in
                            error = nil
                            newPin = entered
                            step = .confirm
                        }
                    )
                case .confirm:
                    AccountPinPad(
                        headline: "Confirm your PIN",
                        errorMessage: error,
                        busy: busy,
                        onComplete: { entered in confirmPin(entered) }
                    )
                case .success:
                    EmptyView()
                }
            }
        }
    }

    private func confirmPin(_ entered: String) {
        guard entered == newPin else {
            error = "That didn't match. Try again."
            step = .create
            return
        }
        busy = true
        error = nil
        Task {
            let result = await SessionManager.shared.updateAccountPin(currentCredential: currentCredential, newPin: entered)
            busy = false
            switch result {
            case .success:
                step = .success
            case .failure(let message):
                error = message
                step = .credential
            }
        }
    }
}
