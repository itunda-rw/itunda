import SwiftUI

private enum AgentScreen: Equatable {
    case loading
    case login
    case home
}

@main
struct ItundaAgentApp: App {
    var body: some Scene {
        WindowGroup {
            AgentRootView()
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
