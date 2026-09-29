import Foundation
import UIKit

/// Real device binding client-side counterpart (2026-07-28 port) -- standalone since
/// MerchantApp doesn't depend on the main app's shared CoreNetwork module. Mirrors
/// ios/App/Sources/DeviceStore.swift and Android merchantapp's DeviceStore.kt exactly:
/// same real, stable, per-install identifier persisted locally, never a derived
/// hardware fingerprint.
///
/// UserDefaults, not the Keychain -- a deviceId is not a secret; it's sent openly on
/// every login request already.
final class MerchantDeviceStore {
    static let shared = MerchantDeviceStore()

    private let defaults = UserDefaults.standard
    private let deviceIdKey = "itunda_merchant_device_id"

    private init() {}

    func getOrCreateDeviceId() -> String {
        if let existing = defaults.string(forKey: deviceIdKey) { return existing }
        let created = UUID().uuidString
        defaults.set(created, forKey: deviceIdKey)
        return created
    }

    func getDeviceName() -> String {
        UIDevice.current.name
    }
}
