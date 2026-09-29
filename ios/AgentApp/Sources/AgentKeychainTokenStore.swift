import Foundation
import Security

/// Real session storage, mirroring RiderApp/MerchantApp's own KeychainTokenStore
/// (same Security-framework convention) -- a distinct service name means its own
/// separate Keychain session. Mirrors Android agentapp's own TokenStore exactly:
/// deliberately simpler than the other native apps -- just the access token, no
/// separate refresh token or persisted userId, matching that same real scope
/// decision on the Android side.
final class AgentKeychainTokenStore {
    static let shared = AgentKeychainTokenStore()

    private let service = "rw.itunda.agent.session"
    private let key = "access_token"

    private init() {}

    func save(accessToken: String) {
        let data = Data(accessToken.utf8)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
        ]
        SecItemDelete(query as CFDictionary)
        var attributes = query
        attributes[kSecValueData as String] = data
        SecItemAdd(attributes as CFDictionary, nil)
    }

    func clear() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
        ]
        SecItemDelete(query as CFDictionary)
    }

    func token() -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess, let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    func hasSession() -> Bool { token() != nil }
}
