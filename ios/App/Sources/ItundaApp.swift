import SwiftUI
import CoreRisk
import CoreNetwork
import CoreDesignSystem

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

    // Real App Switcher privacy cover (Bank product-completeness pass, cycle 2,
    // 2026-09-08) -- Android's MainActivity.kt already has real FLAG_SECURE
    // protection (added 2026-08-09 during a Toss-parity security audit) blocking
    // screenshots/screen-recording AND the task-switcher thumbnail; iOS never got
    // its own equivalent. iOS has no direct screenshot-blocking API, but Apple's
    // own official guidance (Tech Note QA1838, "Hiding Sensitive Data in the App
    // Switcher") documents the real, standard fix: cover sensitive content with an
    // opaque view once the scene actually enters the background (not merely
    // .inactive, which also fires for Control Center/an incoming call and would
    // flicker the cover on every brief interruption) -- UIKit snapshots the scene
    // for the switcher shortly after backgrounding, so anything still visible then
    // is what a screen-locked device (or anyone scrolling the switcher) sees.
    @Environment(\.scenePhase) private var scenePhase
    @State private var showingPrivacyCover = false

    var body: some Scene {
        WindowGroup {
            if isDeviceTrusted {
                ZStack {
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

                    if showingPrivacyCover {
                        PrivacySnapshotCover()
                            .transition(.identity)
                    }
                }
                .onChange(of: scenePhase) { newPhase in
                    showingPrivacyCover = newPhase == .background
                }
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
