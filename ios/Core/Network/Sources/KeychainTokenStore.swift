import Foundation
import Security

/// Real session storage backing the login flow (2026-07-11) -- the Keychain, not
/// UserDefaults: access/refresh tokens are real bearer credentials, same standard
/// Android's TokenStore.kt holds itself to via EncryptedSharedPreferences. Plain
/// Security-framework calls, no third-party dependency.
public final class KeychainTokenStore {
    public static let shared = KeychainTokenStore()

    private let service = "rw.itunda.app.session"

    private init() {}

    public func saveSession(userId: String, accessToken: String, refreshToken: String) {
        set(userId, forKey: .userId)
        set(accessToken, forKey: .accessToken)
        set(refreshToken, forKey: .refreshToken)
    }

    public func clearSession() {
        delete(.userId)
        delete(.accessToken)
        delete(.refreshToken)
    }

    public func getAccessToken() -> String? { get(.accessToken) }
    public func getRefreshToken() -> String? { get(.refreshToken) }
    public func getUserId() -> String? { get(.userId) }
    public func hasSession() -> Bool { getAccessToken() != nil }

    // Real app-launch biometric unlock gate -- mirrors Android's TokenStore.kt exactly:
    // a plain, non-secret boolean preference (UserDefaults here, EncryptedSharedPreferences
    // there), not the Keychain -- there's no credential to protect, just a UI toggle.
    // Default true, matching Android.
    private static let appLockEnabledKey = "rw.itunda.app.appLockEnabled"

    public func isAppLockEnabled() -> Bool {
        if UserDefaults.standard.object(forKey: Self.appLockEnabledKey) == nil { return true }
        return UserDefaults.standard.bool(forKey: Self.appLockEnabledKey)
    }

    public func setAppLockEnabled(_ enabled: Bool) {
        UserDefaults.standard.set(enabled, forKey: Self.appLockEnabledKey)
    }

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
