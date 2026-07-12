import SwiftUI

/// Real account settings screen, matching Android's SettingsScreen.kt exactly
/// (2026-07-12): "내 정보" (real name/phone from /api/v1/auth/profile), a real
/// notifications list (/api/v1/notifications, mark-as-read already real on the
/// backend, just never surfaced anywhere on iOS), and logout. "보안"-style rows
/// are deliberately NOT rendered -- no real 2FA/device-management backend exists
/// behind them yet, and a tappable row that goes nowhere is worse than not
/// claiming the feature.
struct SettingsScreen: View {
    @StateObject private var viewModel = SettingsViewModel()
    let onDone: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onDone) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 18, weight: .medium))
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel("Back")
                Text("Settings").font(.system(size: 20, weight: .bold))
                Spacer()
            }
            .padding(.horizontal, 8)

            List {
                Section("My info") {
                    HStack(spacing: 14) {
                        Circle().fill(Color.gray.opacity(0.2)).frame(width: 44, height: 44)
                            .overlay(Image(systemName: "person.fill"))
                        VStack(alignment: .leading, spacing: 2) {
                            Text(viewModel.profile.map { "\($0.firstName) \($0.lastName)" } ?? "—")
                                .font(.system(size: 17, weight: .semibold))
                            Text(viewModel.profile?.phoneNumber ?? "")
                                .font(.system(size: 14))
                                .foregroundColor(.secondary)
                        }
                    }
                }

                Section {
                    if viewModel.notifications.isEmpty {
                        Text("No notifications").foregroundColor(.secondary)
                    } else {
                        ForEach(viewModel.notifications) { notification in
                            Button(action: { Task { await viewModel.markRead(notification.id) } }) {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(notification.title)
                                        .font(.system(size: 15, weight: notification.isRead ? .regular : .bold))
                                        .foregroundColor(.primary)
                                    Text(notification.body).font(.system(size: 13)).foregroundColor(.secondary)
                                }
                            }
                            .buttonStyle(.plain)
                            .disabled(notification.isRead)
                        }
                    }
                } header: {
                    HStack {
                        Text("Notifications")
                        Spacer()
                        if viewModel.unreadCount > 0 {
                            Button("Mark all read") { Task { await viewModel.markAllRead() } }
                                .font(.system(size: 13, weight: .semibold))
                        }
                    }
                }

                Section {
                    Button(role: .destructive, action: {
                        Task {
                            await SessionManager.shared.logout()
                            onDone()
                        }
                    }) {
                        Label("Log out", systemImage: "rectangle.portrait.and.arrow.right")
                    }
                }
            }
        }
        .task { await viewModel.load() }
    }
}
