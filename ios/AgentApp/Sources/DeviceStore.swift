import Foundation

/// Real, stable, per-install identifier -- standalone since AgentApp doesn't depend on
/// the main app's shared CoreNetwork module. Mirrors RiderApp/MerchantApp's own
/// DeviceStore.swift and Android agentapp's DeviceStore.kt exactly: reused as this
/// demo's client-generated push token (see NetworkClient.swift's own doc comment on
/// registerDeviceToken), since this app has no real FCM/APNs SDK integrated.
///
/// UserDefaults, not the Keychain -- a deviceId is not a secret.
final class AgentDeviceStore {
    static let shared = AgentDeviceStore()

    private let defaults = UserDefaults.standard
    private let deviceIdKey = "itunda_agent_device_id"

    private init() {}

    func getOrCreateDeviceId() -> String {
        if let existing = defaults.string(forKey: deviceIdKey) { return existing }
        let created = UUID().uuidString
        defaults.set(created, forKey: deviceIdKey)
        return created
    }
}
