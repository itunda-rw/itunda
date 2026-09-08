import SwiftUI
import CoreDesignSystem

private enum AgentScreen: Equatable {
    case loading
    case login
    case home
}

@main
struct ItundaAgentApp: App {
    // Real App Switcher privacy cover -- see PrivacySnapshotCover's own doc comment
    // (CoreDesignSystem) for the full sourced account (Apple Tech Note QA1838).
    // A real cash-in/cash-out agent screen showing a customer's amount/phone number
    // is exactly the kind of content that shouldn't sit in the task-switcher
    // snapshot, matching Android's own real FLAG_SECURE protection on this same app
    // (MainActivity.kt, 2026-08-09).
    @Environment(\.scenePhase) private var scenePhase
    @State private var showingPrivacyCover = false

    var body: some Scene {
        WindowGroup {
            ZStack {
                AgentRootView()
                if showingPrivacyCover {
                    PrivacySnapshotCover().transition(.identity)
                }
            }
            .onChange(of: scenePhase) { newPhase in
                showingPrivacyCover = newPhase == .background
            }
            // Matches RiderApp/MerchantApp's own brand decision (see
            // ItundaApp.swift's own comment) -- explicit at the app boundary
            // rather than left to the simulator/device's own appearance setting.
            .preferredColorScheme(.dark)
        }
    }
}

private struct AgentRootView: View {
    @State private var screen: AgentScreen = .loading

    var body: some View {
        Group {
            switch screen {
            case .loading:
                ProgressView()
            case .login:
                LoginScreen(onLoggedIn: { screen = .home })
            case .home:
                AgentHomeScreen(onLogout: { screen = .login })
            }
        }
        .task { await resolveStartScreen() }
    }

    private func resolveStartScreen() async {
        guard AgentKeychainTokenStore.shared.hasSession() else {
            screen = .login
            return
        }
        // Re-validate the role on every cold start -- an operator revoked by an admin
        // (ops-mfe's Agents tab, item 129) since the last session must not stay
        // sitting on a real cash-desk screen. Same real-time authorization discipline
        // LoginScreen's own doc comment establishes for a fresh sign-in.
        if (try? await AgentNetworkClient.shared.me()) != nil {
            screen = .home
        } else {
            AgentKeychainTokenStore.shared.clear()
            screen = .login
        }
    }
}
