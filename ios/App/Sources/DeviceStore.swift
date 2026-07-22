import Foundation
import UIKit
import CoreNetwork

/// Real device binding client-side counterpart (2026-07-21) -- see the backend's
/// TrustedDevice.kt / DeviceService.kt doc comments for the full account (modeled on
/// Toss's own real, published Gateway/Passport architecture). Ported from bank-mfe's
/// lib/device.ts (2026-07-20, web-only) and Android's DeviceStore.kt (same day):
/// closes the "Android and iOS haven't been ported yet" gap
/// docs/TOSS_PARITY_MATRIX.md's Device Security row named as its own honest
/// follow-up. Same real, stable, per-install identifier persisted locally -- never a
/// derived hardware fingerprint.
///
/// UserDefaults, not the Keychain unlike KeychainTokenStore -- a deviceId is not a
/// secret; it's sent openly on every login/register request already, matching
/// Android's plain (unencrypted) SharedPreferences choice for the same reason.
final class DeviceStore {
    static let shared = DeviceStore()

    private let defaults = UserDefaults.standard
    private let deviceIdKey = "itunda_device_id"

    private init() {}

    func getOrCreateDeviceId() -> String {
        if let existing = defaults.string(forKey: deviceIdKey) { return existing }
        let created = UUID().uuidString
        defaults.set(created, forKey: deviceIdKey)
        return created
    }

    // A real, honest, minimal device label -- enough for a user to recognize "oh,
    // that's my phone" on their own Devices screen, matching bank-mfe's
    // getDeviceName()/Android's DeviceStore.getDeviceName() in spirit.
    func getDeviceName() -> String {
        UIDevice.current.name
    }
}
