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
    // devicePublicKey added 2026-08-23 -- real Toss-sourced passwordless-login
    // rollout, see NetworkClient.swift's LoginRequest/RegisterRequest doc comments.
    // Computed by the UI layer (LoginScreen.swift, via DeviceKeyManager) and passed
    // in, not generated here -- keeps this file's own responsibility scoped to
    // session/network orchestration, matching Android's identical SessionManager.kt
    // split (:core:network not depending on :core:identity).
    func login(phoneNumber: String, password: String, devicePublicKey: String? = nil) async -> AuthResult {
        await runAuthCall(phoneNumber: phoneNumber) {
            try await NetworkClient.shared.login(
                LoginRequest(
                    phoneNumber: phoneNumber, password: password,
                    deviceId: DeviceStore.shared.getOrCreateDeviceId(), deviceName: DeviceStore.shared.getDeviceName(),
                    devicePublicKey: devicePublicKey
                )
            )
        }
    }

    func register(
        phoneNumber: String, password: String, firstName: String, lastName: String,
        email: String? = nil, referralCode: String? = nil, devicePublicKey: String? = nil,
        acceptedTermsIds: [String] = []
    ) async -> AuthResult {
        await runAuthCall(phoneNumber: phoneNumber) {
            try await NetworkClient.shared.register(
                RegisterRequest(
                    phoneNumber: phoneNumber, email: email, firstName: firstName, lastName: lastName,
                    password: password, referralCode: referralCode,
                    deviceId: DeviceStore.shared.getOrCreateDeviceId(), deviceName: DeviceStore.shared.getDeviceName(),
                    devicePublicKey: devicePublicKey, acceptedTermsIds: acceptedTermsIds
                )
            )
        }
    }

    // Real Toss/Korean-fintech-style 약관 동의 (terms consent) catalog -- see
    // RegisterRequest.acceptedTermsIds' own doc comment. Best-effort: LoginScreen.swift
    // fails toward MORE friction (a still-enforced-server-side, un-skippable gate) if
    // this throws, not less, same discipline bank-mfe's RegisterPage.tsx already uses.
    func getTerms() async -> [TermsDocument] {
        (try? await NetworkClient.shared.getTerms()) ?? []
    }

    // Real Toss-sourced passwordless-login rollout (2026-08-23) -- see
    // NetworkClient.loginDeviceChallenge/loginWithDeviceSignature's own doc comment.
    // Thin passthroughs (same shape as login/register above): the actual biometric
    // signing happens in LoginScreen.swift via DeviceKeyManager. A successful
    // passwordless login goes through the same real session-persisting runAuthCall a
    // password login already does, since it IS a real login, not a lesser variant.
    func loginDeviceChallenge(phoneNumber: String) async throws -> String {
        try await NetworkClient.shared.loginDeviceChallenge(
            LoginDeviceChallengeRequest(phoneNumber: phoneNumber, deviceId: DeviceStore.shared.getOrCreateDeviceId())
        ).challenge
    }

    func loginWithDeviceSignature(phoneNumber: String, signatureBase64: String) async -> AuthResult {
        await runAuthCall(phoneNumber: phoneNumber) {
            try await NetworkClient.shared.loginWithDeviceSignature(
                LoginWithDeviceSignatureRequest(
                    phoneNumber: phoneNumber, deviceId: DeviceStore.shared.getOrCreateDeviceId(), signature: signatureBase64
                )
            )
        }
    }

    // Real PIN upgrade (2026-08-23) -- see NetworkClient.setAccountPin's own doc
    // comment. Named `updateAccountPin`, not `setPin`, so it's never confused with
    // KeychainTokenStore's own local app-lock PIN concept (a completely different,
    // device-only credential) at any call site. Doesn't touch sessionState/
    // KeychainTokenStore -- the existing access token stays valid, only the
    // credential used on the NEXT login changes.
    func updateAccountPin(currentCredential: String, newPin: String) async -> AuthResult {
        do {
            _ = try await NetworkClient.shared.setAccountPin(currentCredential: currentCredential, newPin: newPin)
            return .success
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            return .failure(message: message ?? "Something went wrong. Please try again.")
        } catch {
            return .failure(message: "Couldn't reach itunda. Check your connection and try again.")
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

    // Real Toss-sourced passwordless-login rollout (2026-08-23) -- persisted so
    // LoginScreen can attempt a real biometric-signed login automatically on
    // appearance, with no phone number retyped first (mirrors bank-mfe's identical
    // getRememberedPhoneNumber/REMEMBERED_PHONE_KEY -- a phone number isn't a secret,
    // same accepted trade-off that file's own doc comment already establishes).
    // Deliberately NOT cleared by logout() below, same real reasoning.
    private static let rememberedPhoneKey = "itunda.rememberedPhoneNumber"

    static func rememberedPhoneNumber() -> String? {
        UserDefaults.standard.string(forKey: rememberedPhoneKey)
    }

    private func runAuthCall(phoneNumber: String? = nil, _ call: () async throws -> AuthResponse) async -> AuthResult {
        do {
            let response = try await call()
            KeychainTokenStore.shared.saveSession(userId: response.user.id, accessToken: response.accessToken, refreshToken: response.refreshToken)
            sessionState = .loggedIn(userId: response.user.id)
            if let phoneNumber {
                UserDefaults.standard.set(phoneNumber, forKey: Self.rememberedPhoneKey)
            }
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
