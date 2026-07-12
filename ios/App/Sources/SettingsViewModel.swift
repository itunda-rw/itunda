import Foundation

/// Real data backing SettingsScreen (2026-07-12) -- mirrors MainViewModel.kt's
/// loadSettingsData/markNotificationRead/markAllNotificationsRead exactly.
@MainActor
final class SettingsViewModel: ObservableObject {
    @Published private(set) var profile: PublicUser?
    @Published private(set) var notifications: [NotificationDto] = []
    @Published private(set) var unreadCount = 0

    func load() async {
        do {
            let profileRes = try await NetworkClient.shared.getProfile()
            if profileRes.success { profile = profileRes.user }

            let notificationsRes = try await NetworkClient.shared.getNotifications()
            if notificationsRes.success {
                notifications = notificationsRes.notifications
                unreadCount = notificationsRes.unreadCount
            }
        } catch {
            // Settings screen just shows whatever it already had -- not a
            // money-moving action, no need for an offline-placeholder treatment.
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
