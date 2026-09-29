import Foundation
import Security

/// Real session storage, mirroring the consumer app's own KeychainTokenStore
/// exactly (same Security-framework convention) -- a distinct service name and its
/// own separate app means its own separate Keychain session, not a shared one.
final class RiderKeychainTokenStore {
    static let shared = RiderKeychainTokenStore()

    private let service = "rw.itunda.rider.session"

    private init() {}

    func saveSession(userId: String, accessToken: String, refreshToken: String) {
        set(userId, forKey: .userId)
        set(accessToken, forKey: .accessToken)
        set(refreshToken, forKey: .refreshToken)
    }

    func clearSession() {
        delete(.userId)
        delete(.accessToken)
        delete(.refreshToken)
    }

    func getAccessToken() -> String? { get(.accessToken) }
    func getRefreshToken() -> String? { get(.refreshToken) }
    func getUserId() -> String? { get(.userId) }
    func hasSession() -> Bool { getAccessToken() != nil }

    // Real, stable, per-install identifier (item 130) -- reused as this demo's
    // client-generated push token (see NetworkClient.swift's own doc comment on
    // registerDeviceToken). Deliberately NOT cleared on logout -- the same physical
    // device is still the same device across a re-login, matching the consumer app's
    // own getOrCreateDeviceId persistence.
    func getOrCreateDeviceId() -> String {
        if let existing = get(.deviceId) { return existing }
        let generated = UUID().uuidString
        set(generated, forKey: .deviceId)
        return generated
    }

    private enum Key: String {
        case userId = "user_id"
        case accessToken = "access_token"
        case refreshToken = "refresh_token"
        case deviceId = "device_id"
    }

    private func set(_ value: String, forKey key: Key) {
        let data = Data(value.utf8)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key.rawValue,
        ]
        SecItemDelete(query as CFDictionary)
        var attributes = query
        attributes[kSecValueData as String] = data
        SecItemAdd(attributes as CFDictionary, nil)
    }

    private func get(_ key: Key) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key.rawValue,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess, let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private func delete(_ key: Key) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key.rawValue,
        ]
        SecItemDelete(query as CFDictionary)
    }
}
