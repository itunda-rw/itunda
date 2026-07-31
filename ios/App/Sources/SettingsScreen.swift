import SwiftUI
import CoreNetwork

/// Real account settings screen, matching Android's SettingsScreen.kt exactly
/// (2026-07-12): "내 정보" (real name/phone from /api/v1/auth/profile), a real
/// notifications list (/api/v1/notifications, mark-as-read already real on the
/// backend, just never surfaced anywhere on iOS), and logout.
///
/// Real device management added 2026-07-21 (see the Devices section below) --
/// closes the "no real device-management backend exists yet" gap this comment used
/// to name: there IS now a real device-binding backend (DeviceService.kt, modeled on
/// Toss's own published Gateway/Passport architecture), already shipped on web
/// (bank-mfe's Devices tab, 2026-07-20) and Android (SettingsScreen.kt, same day as
/// this). This is the iOS port, same real GET/POST/DELETE /api/v1/auth/devices
/// endpoints, same real list/revoke actions.
struct SettingsScreen: View {
    @StateObject private var viewModel = SettingsViewModel()
    let onDone: () -> Void
    @State private var appLockEnabled = KeychainTokenStore.shared.isAppLockEnabled()

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

                // Real device management (2026-07-21 port) -- see this screen's own
                // header comment. Mirrors bank-mfe's Devices tab/Android's Devices
                // section: every device this account has ever signed in from,
                // whether it's trusted (can move money) or merely seen, and a real
                // "Remove" action.
                Section("Devices") {
                    ForEach(viewModel.devices) { device in
                        HStack(spacing: 14) {
                            Image(systemName: "iphone")
                                .frame(width: 44, height: 44)
                                .background(Color.gray.opacity(0.15))
                                .clipShape(Circle())
                            VStack(alignment: .leading, spacing: 2) {
                                Text((device.deviceName ?? "Unknown device") + (device.deviceId == DeviceStore.shared.getOrCreateDeviceId() ? " (this device)" : ""))
                                    .font(.system(size: 15, weight: .semibold))
                                Text(device.trusted ? "Trusted -- can send money" : "Not verified -- can't send money yet")
                                    .font(.system(size: 12))
                                    .foregroundColor(device.trusted ? .secondary : .red)
                            }
                            Spacer()
                            Button("Remove") { Task { await viewModel.revokeDevice(device.deviceId) } }
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(.red)
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

                // Real biometric app-lock toggle -- see AppLockScreenView.swift's own
                // doc comment. Only shown when the device actually has biometrics
                // enrolled; a tappable row that goes nowhere is worse than not showing
                // it, same discipline Android's own SettingsScreen.kt establishes.
                if isBiometricUnlockAvailable() {
                    Section("Security") {
                        Toggle(isOn: $appLockEnabled) {
                            HStack(spacing: 14) {
                                Image(systemName: "faceid")
                                    .frame(width: 44, height: 44)
                                    .background(Color.gray.opacity(0.15))
                                    .clipShape(Circle())
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Unlock with biometrics").font(.system(size: 16, weight: .semibold))
                                    Text("Require Face/Touch ID to open Itunda").font(.system(size: 13)).foregroundColor(.secondary)
                                }
                            }
                        }
                        .onChange(of: appLockEnabled) { newValue in
                            KeychainTokenStore.shared.setAppLockEnabled(newValue)
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
