import SwiftUI
import CoreDesignSystem

private enum RiderScreen: Equatable {
    case loading
    case login
    case becomeRider
    case home
    case delivery(orderId: String)
    // Real Commerce/Shop package delivery -- see CommerceDeliveryDetailScreen's own
    // doc comment on why this carries the full order object rather than just an id.
    case commerceDelivery(order: CommerceOrderDto)
}

@main
struct ItundaRiderApp: App {
    // Real App Switcher privacy cover -- see PrivacySnapshotCover's own doc comment
    // (CoreDesignSystem) for the full sourced account (Apple Tech Note QA1838),
    // matching Android's own real FLAG_SECURE protection on this same app
    // (MainActivity.kt, 2026-08-09). Real earnings/delivery-address content
    // shouldn't sit in the task-switcher snapshot.
    @Environment(\.scenePhase) private var scenePhase
    @State private var showingPrivacyCover = false

    var body: some Scene {
        WindowGroup {
            ZStack {
                RiderRootView()
                if showingPrivacyCover {
                    PrivacySnapshotCover().transition(.identity)
                }
            }
            .onChange(of: scenePhase) { newPhase in
                showingPrivacyCover = newPhase == .background
            }
            // Matches the real app's own brand decision (see ItundaApp.swift's
            // own comment) -- makes it explicit at the app boundary rather than
            // leaving it to the simulator/device's own appearance setting, so
            // this app and the real app can't visually diverge.
            .preferredColorScheme(.dark)
        }
    }
}

private struct RiderRootView: View {
    @State private var screen: RiderScreen = .loading

    var body: some View {
        Group {
            switch screen {
            case .loading:
                ProgressView()
            case .login:
                LoginScreen(onLoggedIn: { screen = .becomeRider })
            case .becomeRider:
                BecomeRiderScreen(
                    onRegistered: { screen = .home },
                    onLogout: { RiderKeychainTokenStore.shared.clearSession(); screen = .login }
                )
                .task {
                    // Re-check on entry: a returning rider who just logged in again
                    // should skip straight to Home, not be shown "become a rider"
                    // every time.
                    if (try? await RiderNetworkClient.shared.getMyRiderProfile()) != nil {
                        screen = .home
                    }
                }
            case .home:
                RiderHomeScreen(
                    onOpenDelivery: { orderId in screen = .delivery(orderId: orderId) },
                    onOpenCommerceDelivery: { order in screen = .commerceDelivery(order: order) },
                    onLogout: { screen = .login }
                )
            case .delivery(let orderId):
                DeliveryDetailScreen(orderId: orderId, onBack: { screen = .home })
            case .commerceDelivery(let order):
                CommerceDeliveryDetailScreen(initialOrder: order, onBack: { screen = .home })
            }
        }
        .task { await resolveStartScreen() }
    }

    private func resolveStartScreen() async {
        guard RiderKeychainTokenStore.shared.hasSession() else {
            screen = .login
            return
        }
        if (try? await RiderNetworkClient.shared.getMyRiderProfile()) != nil {
            screen = .home
        } else {
            screen = .becomeRider
        }
    }
}
