import Foundation

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
        _ = try? await NetworkClient.shared.revokeDevice(deviceId: deviceId)
        await loadDevices()
    }

    func markRead(_ id: String) async {
        try? await NetworkClient.shared.markNotificationRead(id)
        await load()
    }

    func markAllRead() async {
        await markRead("all")
    }
}
