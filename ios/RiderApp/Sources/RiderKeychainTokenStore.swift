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

    private enum Key: String {
        case userId = "user_id"
        case accessToken = "access_token"
        case refreshToken = "refresh_token"
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
