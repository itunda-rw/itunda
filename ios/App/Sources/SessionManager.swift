import Foundation
import CoreNetwork

enum SessionState: Equatable {
    case loggedOut
    case loggedIn(userId: String)
}

enum AuthResult {
    case success
    case failure(message: String)
}

/// The real login/session flow this app has never had (see NetworkClient.swift's
/// header) -- orchestrates NetworkClient (services/backend's real /api/v1/auth/*
/// endpoints) and KeychainTokenStore, exposing @Published session state so
/// ItundaApp.swift can gate ContentView behind an actual login screen instead of
/// rendering it unconditionally, mirroring Android's SessionManager.kt exactly.
@MainActor
final class SessionManager: ObservableObject {
    static let shared = SessionManager()

    @Published private(set) var sessionState: SessionState = .loggedOut

    private init() {
        // Real gap found 2026-08-15 (matches Android's identical SessionManager.kt
        // fix): NetworkClient's refresh-retry logic clears the Keychain session and
        // posts this notification when the refresh token itself is invalid/expired --
        // without this observer, sessionState would keep claiming .loggedIn while
        // every screen silently got a fresh, unrecoverable 401 forever.
        NotificationCenter.default.addObserver(
            forName: NetworkClient.sessionExpiredNotification, object: nil, queue: .main
        ) { [weak self] _ in
            Task { @MainActor in
                self?.sessionState = .loggedOut
            }
        }
    }

    func restoreSession() {
        let store = KeychainTokenStore.shared
        if store.hasSession(), let userId = store.getUserId() {
            sessionState = .loggedIn(userId: userId)
        } else {
            sessionState = .loggedOut
        }
    }

    // deviceId/deviceName added 2026-07-21 -- real device binding (see
    // DeviceStore.swift), mirrors bank-mfe's real login()/register() calls exactly.
    func login(phoneNumber: String, password: String) async -> AuthResult {
        await runAuthCall {
            try await NetworkClient.shared.login(
                LoginRequest(
                    phoneNumber: phoneNumber, password: password,
                    deviceId: DeviceStore.shared.getOrCreateDeviceId(), deviceName: DeviceStore.shared.getDeviceName()
                )
            )
        }
    }

    func register(
        phoneNumber: String, password: String, firstName: String, lastName: String,
        email: String? = nil, referralCode: String? = nil
    ) async -> AuthResult {
        await runAuthCall {
            try await NetworkClient.shared.register(
                RegisterRequest(
                    phoneNumber: phoneNumber, email: email, firstName: firstName, lastName: lastName,
                    password: password, referralCode: referralCode,
                    deviceId: DeviceStore.shared.getOrCreateDeviceId(), deviceName: DeviceStore.shared.getDeviceName()
                )
            )
        }
    }

    func logout() async {
        let store = KeychainTokenStore.shared
        let accessToken = store.getAccessToken()
        let refreshToken = store.getRefreshToken()
        if let accessToken {
            // Best-effort server-side revocation, same reasoning as
            // SessionManager.kt's Android twin: a local "log out" tap must clear the
            // on-device session regardless of whether the network call succeeds.
            try? await NetworkClient.shared.logout(accessToken: accessToken, request: LogoutRequest(refreshToken: refreshToken))
            // Real push unregister-on-logout (item 232) -- best-effort, see
            // NetworkClient.unregisterDeviceToken's own doc comment. Must run before
            // store.clearSession() below, while the keychain token it reads is still valid.
            try? await NetworkClient.shared.unregisterDeviceToken(DeviceStore.shared.getOrCreateDeviceId())
        }
        store.clearSession()
        sessionState = .loggedOut
    }

    private func runAuthCall(_ call: () async throws -> AuthResponse) async -> AuthResult {
        do {
            let response = try await call()
            KeychainTokenStore.shared.saveSession(userId: response.user.id, accessToken: response.accessToken, refreshToken: response.refreshToken)
            sessionState = .loggedIn(userId: response.user.id)
            await registerDeviceToken()
            return .success
        } catch let NetworkError.httpError(statusCode) {
            return .failure(message: httpErrorMessage(statusCode))
        } catch {
            return .failure(message: "Couldn't reach itunda. Check your connection and try again.")
        }
    }

    // Real push device-token registration (item 121) -- see NetworkClient.swift's own
    // doc comment. Best-effort and fire-and-forget: a registration failure must never
    // block an otherwise-successful login/register.
    private func registerDeviceToken() async {
        do {
            _ = try await NetworkClient.shared.registerDeviceToken(
                RegisterDeviceTokenRequest(platform: .ios, token: DeviceStore.shared.getOrCreateDeviceId())
            )
        } catch {
            // Best-effort, see doc comment above.
        }
    }

    private func httpErrorMessage(_ statusCode: Int) -> String {
        switch statusCode {
        case 401: return "Incorrect phone number or password."
        case 409: return "An account with this phone number already exists."
        case 429: return "Too many attempts. Please wait a moment and try again."
        default: return "Something went wrong. Please try again."
        }
    }
}
