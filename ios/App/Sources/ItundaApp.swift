import SwiftUI
import CoreRisk
import CoreNetwork

@main
struct ItundaApp: App {
    // Root/FDS gate on the real app entry point, mirroring Android's
    // MainActivity.onCreate check (see docs/ARCHITECTURE.md §3) --
    // money-moving screens should not render on a compromised device.
    private let isDeviceTrusted = ZeroTrust.shared.verifyDeviceIntegrity()

    // Real login gate (2026-07-11) -- ContentView previously rendered
    // unconditionally with no session at all; see SessionManager.swift.
    @StateObject private var sessionManager = SessionManager.shared

    // Real app-launch biometric unlock gate -- tracked per PROCESS, not persisted, so a
    // view redraw doesn't re-prompt but a genuine fresh app launch always does. See
    // AppLockScreenView.swift's own doc comment; mirrors Android's MainActivity.kt
    // AppUnlockState exactly.
    @State private var unlockedThisProcess = false

    var body: some Scene {
        WindowGroup {
            if isDeviceTrusted {
                Group {
                    switch sessionManager.sessionState {
                    case .loggedIn:
                        if isBiometricUnlockAvailable() && KeychainTokenStore.shared.isAppLockEnabled() && !unlockedThisProcess {
                            AppLockScreenView(onUnlocked: { unlockedThisProcess = true })
                        } else {
                            ContentView()
                        }
                    case .loggedOut:
                        LoginScreen(sessionManager: sessionManager)
                            .onAppear { unlockedThisProcess = false }
                    }
                }
                .onAppear { sessionManager.restoreSession() }
                // Found live on-device (2026-07-12): LoginScreen's password field
                // is still real-keyboard-focused the instant login succeeds, and
                // this switch immediately swaps the whole view hierarchy from
                // LoginScreen to ContentView -- unlike a normal dismiss/back
                // navigation, that swap gives UIKit no chance to resign the
                // keyboard first. The result: the key window is left with a stale
                // keyboard-safe-area adjustment that every fullScreenCover
                // presented afterward inherits (confirmed identical on both the
                // transfer flow and the completely unrelated transaction-history
                // cover), cutting off the top of the screen -- including back
                // buttons -- with no way to recover short of restarting the app.
                // Resigning first responder before the hierarchy swap is the fix.
                .onChange(of: sessionManager.sessionState) { newState in
                    if case .loggedIn = newState {
                        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                    }
                }
                // The Android client and supplied Toss references use the dark
                // product surface. Make it explicit at the app boundary so the
                // simulator/device preference cannot split the two clients into
                // different visual systems; semantic IDS colors still provide the
                // correct contrast for every screen.
                .preferredColorScheme(.dark)
            } else {
                Text("Itunda can't run on a jailbroken or compromised device.")
                    .multilineTextAlignment(.center)
                    .padding()
            }
        }
    }
}
