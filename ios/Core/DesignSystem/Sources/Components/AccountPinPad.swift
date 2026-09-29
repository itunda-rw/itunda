import SwiftUI
import CoreDesignSystem

/// Real Toss-sourced passwordless-login rollout (2026-08-23) -- see NetworkClient
/// .swift's RegisterRequest/LoginRequest devicePublicKey doc comment for the full
/// research (support.toss.im/toss.im/tosscert): a real 6-digit "비밀번호" (Toss's own
/// literal term), not a fabricated concept, replacing the free-form password
/// IdsTextField LoginScreen.swift used to render. Used by LoginScreen (login +
/// register credential entry) and PinUpgradeCard (existing pre-PIN-era account
/// upgrade). No local, device-only PIN component exists anywhere else in this
/// codebase to share with or conflict against (AppLockScreenView.swift is
/// biometric-only, confirmed via this session's own research pass before writing
/// this) -- mirrors Android's identical AccountPinPad.kt, built fresh there for the
/// same real reason (keeping this backend account credential distinct from any
/// future local app-unlock PIN). Auto-submits at 6 digits, matching every real PIN
/// entry convention (Toss, bank cards) -- no separate confirm button.
public struct AccountPinPad: View {
    let headline: String
    var subtitle: String? = nil
    var errorMessage: String? = nil
    var busy: Bool = false
    let onComplete: (String) -> Void

    @State private var pin = ""
    @State private var shakeOffset: CGFloat = 0
    // Real Toss-style "confirming" pulse (60fps.design's own real catalog of Toss's
    // named interactions, 2026-08-29) -- the dots had zero feedback the instant the
    // 6th digit landed; matches Android's own identical AccountPinPad.kt fix same
    // session.
    @State private var dotsScale: CGFloat = 1

    private let pinLength = 6

    public init(headline: String, subtitle: String? = nil, errorMessage: String? = nil, busy: Bool = false, onComplete: @escaping (String) -> Void) {
        self.headline = headline
        self.subtitle = subtitle
        self.errorMessage = errorMessage
        self.busy = busy
        self.onComplete = onComplete
    }

    public var body: some View {
        VStack(spacing: 0) {
            Text(headline)
                .font(IDS.Typography.title)
                .foregroundColor(IDS.Colors.textPrimary)

            if let message = errorMessage ?? subtitle {
                Text(message)
                    .font(IDS.Typography.bodyMedium)
                    .foregroundColor(errorMessage != nil ? IDS.Colors.danger : IDS.Colors.textSecondary)
                    .padding(.top, 8)
            }

            Spacer(minLength: 32)

            if busy {
                ProgressView().tint(IDS.Colors.brand)
            } else {
                HStack(spacing: 16) {
                    ForEach(0..<pinLength, id: \.self) { index in
                        Circle()
                            .fill(index < pin.count ? IDS.Colors.brand : IDS.Colors.divider)
                            .frame(width: 16, height: 16)
                    }
                }
                .offset(x: shakeOffset)
                .scaleEffect(dotsScale)
            }

            Spacer(minLength: 32)

            keypad
        }
        .onChange(of: errorMessage) { newValue in
            guard newValue != nil else { return }
            // Same real shake-then-clear sequence as Android's AccountPinPad.kt
            // (tween 60ms per leg) -- a network round trip means this can't know
            // success/failure the instant the 6th digit lands, so it waits for the
            // caller to report back via `errorMessage`.
            withAnimation(.linear(duration: 0.06)) { shakeOffset = 16 }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.06) {
                withAnimation(.linear(duration: 0.06)) { shakeOffset = -16 }
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.06) {
                    withAnimation(.linear(duration: 0.06)) { shakeOffset = 0 }
                }
            }
            pin = ""
        }
    }

    private func onDigit(_ digit: String) {
        guard !busy, pin.count < pinLength else { return }
        pin += digit
        if pin.count == pinLength {
            withAnimation(.linear(duration: 0.12)) { dotsScale = 1.15 }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.12) {
                withAnimation(.linear(duration: 0.12)) { dotsScale = 1 }
            }
            onComplete(pin)
        }
    }

    private var keypad: some View {
        VStack(spacing: 0) {
            ForEach([["1", "2", "3"], ["4", "5", "6"], ["7", "8", "9"]], id: \.self) { row in
                HStack(spacing: 0) {
                    ForEach(row, id: \.self) { digit in
                        pinKey(digit) { onDigit(digit) }
                    }
                }
            }
            HStack(spacing: 0) {
                Color.clear.frame(maxWidth: .infinity).frame(height: 64)
                pinKey("0") { onDigit("0") }
                Button(action: { if !pin.isEmpty { pin.removeLast() } }) {
                    Image(systemName: "delete.left")
                        .foregroundColor(IDS.Colors.textSecondary)
                }
                .accessibilityLabel("Delete last digit")
                .frame(maxWidth: .infinity)
                .frame(height: 64)
                .disabled(busy)
            }
        }
        .padding(.bottom, 24)
    }

    private func pinKey(_ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(IDS.Typography.title)
                .foregroundColor(IDS.Colors.textPrimary)
                .frame(maxWidth: .infinity)
                .frame(height: 64)
        }
        .disabled(busy)
    }
}
