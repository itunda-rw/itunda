import SwiftUI
import UIKit

// Real Toss TDS Toast component (Simplicity research, docs/DESIGN_REFERENCES.md
// Section 14): brief, auto-dismissing feedback -- 3000ms default, 5000ms when it
// carries an action button -- rather than a persistent banner the user has to
// dismiss themselves. Ported from bank-mfe's own Toast.tsx (2026-08-22, same
// session) -- iOS had ZERO toast/confirmation pattern anywhere before this (Android
// at least has native `Toast.makeText` in a few real spots, e.g.
// ItundaAppScreen.kt's savings deposit/claim flow; iOS's own equivalent
// SavingsFlowContainer.swift shows nothing on success at all, and neither does any
// of the 3 real Create*View flows -- CreateSavingsGoalScreen,
// CreateWeeklySavingsPlanView, CreateGrow31SavingsPlanView).
//
// SwiftUI has no built-in system toast, so this is a small app-wide singleton +
// overlay, mirroring the web version's own "single toast at a time, new one
// replaces whatever's showing" behavior (real Toss convention -- stacking transient
// confirmations reads as noisy, not informative).
@MainActor
public final class ToastCenter: ObservableObject {
    public static let shared = ToastCenter()

    @Published fileprivate var message: String?
    @Published fileprivate var actionLabel: String?
    fileprivate var onAction: (() -> Void)?
    private var dismissTask: Task<Void, Never>?

    private init() {}

    public func show(_ message: String, actionLabel: String? = nil, onAction: (() -> Void)? = nil) {
        dismissTask?.cancel()
        self.onAction = onAction
        withAnimation(.easeOut(duration: 0.22)) {
            self.message = message
            self.actionLabel = actionLabel
        }
        // Real VoiceOver announcement -- matches web's aria-live="polite" (heard
        // without stealing focus). A `.updatesFrequently` accessibility trait alone
        // (the first thing tried here) doesn't actually announce a one-shot,
        // ephemeral status change like this -- that trait is for elements that
        // change often and get re-read on demand, not "announce this once, now."
        UIAccessibility.post(notification: .announcement, argument: message)
        let duration = actionLabel != nil ? 5_000_000_000 : 3_000_000_000
        dismissTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(duration))
            guard !Task.isCancelled else { return }
            await self?.dismiss()
        }
    }

    fileprivate func dismiss() {
        withAnimation(.easeOut(duration: 0.22)) {
            message = nil
            actionLabel = nil
        }
    }
}

/// Mounts once at the app root (ContentView's own top-level ZStack/overlay), the
/// same way bank-mfe's <OverlayProvider> is mounted once at App.tsx's root rather
/// than per-screen.
public struct ToastOverlay: View {
    @ObservedObject private var center = ToastCenter.shared

    public init() {}

    public var body: some View {
        VStack {
            Spacer()
            if let message = center.message {
                HStack(spacing: 12) {
                    Text(message)
                        .font(IDS.scaledFont(size: 14, weight: .semibold, relativeTo: .footnote))
                        .foregroundColor(.white)
                    if let actionLabel = center.actionLabel {
                        Button(actionLabel) {
                            center.onAction?()
                            center.dismiss()
                        }
                        .font(IDS.scaledFont(size: 14, weight: .bold, relativeTo: .footnote))
                        // Fixed, theme-invariant accent -- matches IDS.Colors.brand's
                        // dark-mode value exactly (both light/dark IDS.Colors.brand
                        // resolve close to this indigo), kept as a literal here since
                        // the toast pill itself is deliberately fixed-dark regardless
                        // of the system's own light/dark appearance (see below), and
                        // a dynamic Color(light:dark:) would fight that.
                        .foregroundColor(Color(red: 0x5C / 255, green: 0x55 / 255, blue: 0xD8 / 255))
                    }
                }
                .padding(.horizontal, 18)
                .padding(.vertical, 14)
                .frame(maxWidth: 420)
                // Deliberately a fixed literal, not IDS.Colors.background/textPrimary
                // -- this surface must stay ONE fixed dark pill regardless of the
                // system's own light/dark appearance, for consistent max-contrast
                // ephemeral visibility (matches Toss's own real toast, and the exact
                // same real bug the web port of this component hit first: a
                // theme-reactive token here inverts to white in dark mode and goes
                // invisible).
                .background(Color(red: 0x19 / 255, green: 0x1F / 255, blue: 0x28 / 255))
                .cornerRadius(14)
                .shadow(color: .black.opacity(0.24), radius: 12, x: 0, y: 8)
                .padding(.bottom, 24)
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .accessibilityElement(children: .combine)
            }
        }
        .allowsHitTesting(center.message != nil)
        .animation(.easeOut(duration: 0.22), value: center.message)
    }
}
