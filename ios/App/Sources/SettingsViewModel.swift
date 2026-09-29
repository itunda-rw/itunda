import Foundation
import CoreNetwork

/// Real data backing SettingsScreen (2026-07-12) -- mirrors MainViewModel.kt's
/// loadSettingsData/markNotificationRead/markAllNotificationsRead exactly.
@MainActor
final class SettingsViewModel: ObservableObject {
    @Published private(set) var profile: PublicUser?
    @Published private(set) var notifications: [NotificationDto] = []
    @Published private(set) var unreadCount = 0
    // Real device management (2026-07-21 port) -- mirrors bank-mfe's Devices tab /
    // Android's MainViewModel.devices exactly.
    @Published private(set) var devices: [TrustedDeviceDto] = []
    // Real gap found 2026-09-05: revokeDevice() used try? to silently discard a
    // failed revoke -- a security-relevant action (removing a device's ability to
    // send money) failing with zero feedback could leave the user believing a
    // device was removed when it wasn't. Matches DeviceListScreen.swift's own
    // identical same-day fix for the standalone device screen.
    @Published private(set) var deviceError: String?

    func load() async {
        do {
            let profileRes = try await NetworkClient.shared.getProfile()
            if profileRes.success { profile = profileRes.user }

            let notificationsRes = try await NetworkClient.shared.getNotifications()
            if notificationsRes.success {
                notifications = notificationsRes.notifications
                unreadCount = notificationsRes.unreadCount
            }

            await loadDevices()
        } catch {
            // Settings screen just shows whatever it already had -- not a
            // money-moving action, no need for an offline-placeholder treatment.
        }
    }

    func loadDevices() async {
        if let devicesRes = try? await NetworkClient.shared.getMyDevices(), devicesRes.success {
            devices = devicesRes.devices
        }
    }

    func revokeDevice(_ deviceId: String) async {
        do {
            _ = try await NetworkClient.shared.revokeDevice(deviceId: deviceId)
            deviceError = nil
            await loadDevices()
        } catch {
            deviceError = "removeFailed"
        }
    }

    func markRead(_ id: String) async {
        try? await NetworkClient.shared.markNotificationRead(id)
        await load()
    }

    func markAllRead() async {
        await markRead("all")
    }
}
