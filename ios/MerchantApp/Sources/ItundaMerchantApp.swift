import SwiftUI

private enum MerchantScreen: Equatable {
    case loading
    case login
    case becomeMerchant
    case home(merchant: MerchantDto)

    static func == (lhs: MerchantScreen, rhs: MerchantScreen) -> Bool {
        switch (lhs, rhs) {
        case (.loading, .loading), (.login, .login), (.becomeMerchant, .becomeMerchant): return true
        case let (.home(a), .home(b)): return a.id == b.id
        default: return false
        }
    }
}

@main
struct ItundaMerchantApp: App {
    var body: some Scene {
        WindowGroup {
            MerchantRootView()
                // Matches the real app's own brand decision (see ItundaApp.swift's
                // own comment) -- explicit at the app boundary so this app can't
                // visually diverge based on the simulator/device's own appearance.
                .preferredColorScheme(.dark)
        }
    }
}

private struct MerchantRootView: View {
    @State private var screen: MerchantScreen = .loading

    var body: some View {
        Group {
            switch screen {
            case .loading:
                ProgressView()
            case .login:
                LoginScreen(onLoggedIn: { screen = .becomeMerchant })
            case .becomeMerchant:
                BecomeMerchantScreen(
                    onRegistered: { merchant in screen = .home(merchant: merchant) },
                    onLogout: { MerchantKeychainTokenStore.shared.clearSession(); screen = .login }
                )
                .task {
                    // Re-check on entry: a returning merchant who just logged in
                    // again should skip straight to Home, not be shown "register
                    // your business" every time.
                    if let merchant = try? await MerchantNetworkClient.shared.getMyMerchant().merchant {
                        screen = .home(merchant: merchant)
                    }
                }
            case .home(let merchant):
                MerchantHomeScreen(merchant: merchant, onLogout: { screen = .login })
            }
        }
        .task { await resolveStartScreen() }
    }

    private func resolveStartScreen() async {
        guard MerchantKeychainTokenStore.shared.hasSession() else {
            screen = .login
            return
        }
        if let merchant = try? await MerchantNetworkClient.shared.getMyMerchant().merchant {
            screen = .home(merchant: merchant)
        } else {
            screen = .becomeMerchant
        }
    }
}
