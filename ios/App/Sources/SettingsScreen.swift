import SwiftUI
import CoreNetwork
import CoreIdentity
import CoreDesignSystem

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
///
/// Localized 2026-08-08 (docs/DESIGN_REFERENCES.md Section 19) -- the 4th screen in
/// the Kinyarwanda thread, and deliberately picked next: it's the only place in the
/// app where a logged-in user can reach a language switcher at all, since
/// LoginScreen.swift's toggle is only visible before signing in. Reuses
/// AppLocale/loadStoredLocale directly (same App target as LoginScreen.swift). Same
/// honesty note as every prior screen: careful, good-faith translation, not verified
/// by a native Kinyarwanda speaker.
private let settingsStrings: [AppLocale: [String: String]] = [
    .en: [
        "back": "Back",
        "title": "Settings",
        "language": "Language",
        "myInfo": "My info",
        "devices": "Devices",
        "unknownDevice": "Unknown device",
        "thisDevice": " (this device)",
        "trusted": "Trusted -- can send money",
        "notVerified": "Not verified -- can't send money yet",
        "remove": "Remove",
        "noNotifications": "No notifications",
        "notifications": "Notifications",
        "markAllRead": "Mark all read",
        "security": "Security",
        "unlockBiometrics": "Unlock with biometrics",
        "unlockBiometricsBody": "Require Face/Touch ID to open Itunda",
        "verifyBiometrics": "Verify this device with biometrics",
        "verifyBiometricsBody": "Skip retyping your password for step-up verification",
        "logOut": "Log out",
        "confirmPassword": "Confirm your password",
        "password": "Password",
        "cancel": "Cancel",
        "verifying": "Verifying…",
        "confirm": "Confirm",
        "confirmPasswordBody": "Enter your password once to enable biometric device verification.",
    ],
    .rw: [
        "back": "Subira inyuma",
        "title": "Igenamiterere",
        "language": "Ururimi",
        "myInfo": "Amakuru yanjye",
        "devices": "Ibikoresho",
        "unknownDevice": "Ikoresho kitazwi",
        "thisDevice": " (iki gikoresho)",
        "trusted": "Byemewe -- gishobora kohereza amafaranga",
        "notVerified": "Ntibyemejwe -- ntigishobora kohereza amafaranga",
        "remove": "Kuraho",
        "noNotifications": "Nta menyesha rihari",
        "notifications": "Amamenyesha",
        "markAllRead": "Yose yasomwe",
        "security": "Umutekano",
        "unlockBiometrics": "Fungura ukoresheje ibimenyetso by'umubiri",
        "unlockBiometricsBody": "Saba Face/Touch ID kugira ngo ufungure itunda",
        "verifyBiometrics": "Emeza iki gikoresho ukoresheje ibimenyetso by'umubiri",
        "verifyBiometricsBody": "Simbuka kwandika ijambo ry'ibanga ku kwemeza",
        "logOut": "Sohoka",
        "confirmPassword": "Emeza ijambo ry'ibanga ryawe",
        "password": "Ijambo ry'ibanga",
        "cancel": "Hagarika",
        "verifying": "Kwemeza…",
        "confirm": "Emeza",
        "confirmPasswordBody": "Andika ijambo ry'ibanga rimwe kugira ngo wemeze ibimenyetso by'umubiri ku gikoresho.",
    ],
    // Real gap found 2026-08-15: this dict had zero French entries even after AppLocale
    // itself was widened to .fr (LoginScreen.swift, d6a909b2) -- a user whose locale
    // was already set to French would silently fall back to English on this specific
    // screen (see t()'s own fallback below), the exact staleness this session's own
    // AlreadyX/localization sweeps were built to catch, just missed on this one file.
    .fr: [
        "back": "Retour",
        "title": "Paramètres",
        "language": "Langue",
        "myInfo": "Mes informations",
        "devices": "Appareils",
        "unknownDevice": "Appareil inconnu",
        "thisDevice": " (cet appareil)",
        "trusted": "Approuvé -- peut envoyer de l'argent",
        "notVerified": "Non vérifié -- ne peut pas encore envoyer d'argent",
        "remove": "Retirer",
        "noNotifications": "Aucune notification",
        "notifications": "Notifications",
        "markAllRead": "Tout marquer comme lu",
        "security": "Sécurité",
        "unlockBiometrics": "Déverrouiller avec la biométrie",
        "unlockBiometricsBody": "Exiger Face/Touch ID pour ouvrir Itunda",
        "verifyBiometrics": "Vérifier cet appareil avec la biométrie",
        "verifyBiometricsBody": "Évitez de retaper votre mot de passe pour la vérification renforcée",
        "logOut": "Se déconnecter",
        "confirmPassword": "Confirmez votre mot de passe",
        "password": "Mot de passe",
        "cancel": "Annuler",
        "verifying": "Vérification…",
        "confirm": "Confirmer",
        "confirmPasswordBody": "Entrez votre mot de passe une fois pour activer la vérification biométrique de l'appareil.",
    ],
]

struct SettingsScreen: View {
    @StateObject private var viewModel = SettingsViewModel()
    let onDone: () -> Void
    @State private var locale: AppLocale = loadStoredLocale()
    @State private var appLockEnabled = KeychainTokenStore.shared.isAppLockEnabled()
    // Real Secure-Enclave-signed-challenge device verification (item 246) -- see
    // DeviceKeyManager's own doc comment. Mirrors Android's SettingsScreen.kt toggle
    // exactly: same password bar as the existing verifyDevice flow, since registering a
    // key is exactly as strong a trust decision.
    @State private var hasDeviceKey = DeviceKeyManager.shared.hasKey()
    @State private var showKeyPasswordPrompt = false
    @State private var keyPassword = ""
    @State private var keyRegisterError: String?
    @State private var keyRegistering = false

    private func t(_ key: String) -> String {
        settingsStrings[locale]?[key] ?? settingsStrings[.en]?[key] ?? key
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onDone) {
                    IDS.Icons.back(size: 18, relativeTo: .title3).frame(width: 44, height: 44)
                }
                .accessibilityLabel(t("back"))
                Text(t("title")).font(IDS.scaledFont(size: 20, weight: .bold, relativeTo: .title2))
                Spacer()
                // Real in-app language switcher for logged-in users (2026-08-08) -- see
                // this file's own top-of-file doc comment: LoginScreen.swift's own
                // toggle is unreachable once signed in, so this is the only place a
                // logged-in user can change it. Same UserDefaults key, so both screens
                // agree on the current language. Widened to a 3-way cycle (2026-08-15,
                // matching LoginScreen.swift's own identical fix) -- was still a binary
                // EN/RW toggle here, missed when AppLocale itself gained .fr.
                Button(action: {
                    let locales: [AppLocale] = [.en, .rw, .fr]
                    let currentIndex = locales.firstIndex(of: locale) ?? 0
                    locale = locales[(currentIndex + 1) % locales.count]
                    UserDefaults.standard.set(locale.rawValue, forKey: localeStorageKey)
                }) {
                    Text(locale.rawValue.uppercased())
                        .font(IDS.scaledFont(size: 15, weight: .medium, relativeTo: .subheadline))
                        .foregroundColor(.secondary)
                }
                .accessibilityLabel(t("language"))
            }
            .padding(.horizontal, 8)

            List {
                Section(t("myInfo")) {
                    HStack(spacing: 14) {
                        Circle().fill(Color.gray.opacity(0.2)).frame(width: 44, height: 44)
                            .overlay(Image(systemName: "person.fill"))
                        VStack(alignment: .leading, spacing: 2) {
                            Text(viewModel.profile.map { "\($0.firstName) \($0.lastName)" } ?? "—")
                                .font(IDS.scaledFont(size: 17, weight: .semibold, relativeTo: .body))
                            Text(viewModel.profile?.phoneNumber ?? "")
                                .font(IDS.scaledFont(size: 14, weight: .regular, relativeTo: .subheadline))
                                .foregroundColor(.secondary)
                        }
                    }
                }

                // Real device management (2026-07-21 port) -- see this screen's own
                // header comment. Mirrors bank-mfe's Devices tab/Android's Devices
                // section: every device this account has ever signed in from,
                // whether it's trusted (can move money) or merely seen, and a real
                // "Remove" action.
                Section(t("devices")) {
                    ForEach(viewModel.devices) { device in
                        HStack(spacing: 14) {
                            Image(systemName: "iphone")
                                .frame(width: 44, height: 44)
                                .background(Color.gray.opacity(0.15))
                                .clipShape(Circle())
                            VStack(alignment: .leading, spacing: 2) {
                                Text((device.deviceName ?? t("unknownDevice")) + (device.deviceId == DeviceStore.shared.getOrCreateDeviceId() ? t("thisDevice") : ""))
                                    .font(IDS.scaledFont(size: 15, weight: .semibold, relativeTo: .subheadline))
                                Text(device.trusted ? t("trusted") : t("notVerified"))
                                    .font(IDS.scaledFont(size: 12, weight: .regular, relativeTo: .caption1))
                                    .foregroundColor(device.trusted ? .secondary : .red)
                            }
                            Spacer()
                            Button(t("remove")) { Task { await viewModel.revokeDevice(device.deviceId) } }
                                .font(IDS.scaledFont(size: 13, weight: .semibold, relativeTo: .footnote))
                                .foregroundColor(.red)
                        }
                    }
                }

                Section {
                    if viewModel.notifications.isEmpty {
                        Text(t("noNotifications")).foregroundColor(.secondary)
                    } else {
                        ForEach(viewModel.notifications) { notification in
                            Button(action: { Task { await viewModel.markRead(notification.id) } }) {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(notification.title)
                                        .font(IDS.scaledFont(size: 15, weight: notification.isRead ? .regular : .bold, relativeTo: .subheadline))
                                        .foregroundColor(.primary)
                                    Text(notification.body).font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote)).foregroundColor(.secondary)
                                }
                            }
                            .buttonStyle(.plain)
                            .disabled(notification.isRead)
                        }
                    }
                } header: {
                    HStack {
                        Text(t("notifications"))
                        Spacer()
                        if viewModel.unreadCount > 0 {
                            Button(t("markAllRead")) { Task { await viewModel.markAllRead() } }
                                .font(IDS.scaledFont(size: 13, weight: .semibold, relativeTo: .footnote))
                        }
                    }
                }

                // Real biometric app-lock toggle -- see AppLockScreenView.swift's own
                // doc comment. Only shown when the device actually has biometrics
                // enrolled; a tappable row that goes nowhere is worse than not showing
                // it, same discipline Android's own SettingsScreen.kt establishes.
                if isBiometricUnlockAvailable() {
                    Section(t("security")) {
                        Toggle(isOn: $appLockEnabled) {
                            HStack(spacing: 14) {
                                Image(systemName: "faceid")
                                    .frame(width: 44, height: 44)
                                    .background(Color.gray.opacity(0.15))
                                    .clipShape(Circle())
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(t("unlockBiometrics")).font(IDS.scaledFont(size: 16, weight: .semibold, relativeTo: .callout))
                                    Text(t("unlockBiometricsBody")).font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote)).foregroundColor(.secondary)
                                }
                            }
                        }
                        .onChange(of: appLockEnabled) { newValue in
                            KeychainTokenStore.shared.setAppLockEnabled(newValue)
                        }

                        Toggle(isOn: Binding(
                            get: { hasDeviceKey },
                            set: { newValue in
                                if newValue {
                                    keyRegisterError = nil
                                    showKeyPasswordPrompt = true
                                } else {
                                    DeviceKeyManager.shared.removeKey()
                                    hasDeviceKey = false
                                }
                            }
                        )) {
                            HStack(spacing: 14) {
                                Image(systemName: "faceid")
                                    .frame(width: 44, height: 44)
                                    .background(Color.gray.opacity(0.15))
                                    .clipShape(Circle())
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(t("verifyBiometrics")).font(IDS.scaledFont(size: 16, weight: .semibold, relativeTo: .callout))
                                    Text(t("verifyBiometricsBody")).font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote)).foregroundColor(.secondary)
                                }
                            }
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
                        Label(t("logOut"), systemImage: "rectangle.portrait.and.arrow.right")
                    }
                }
            }
        }
        .task { await viewModel.load() }
        .alert(t("confirmPassword"), isPresented: $showKeyPasswordPrompt) {
            SecureField(t("password"), text: $keyPassword)
            Button(t("cancel"), role: .cancel) {
                keyPassword = ""
                keyRegisterError = nil
            }
            Button(keyRegistering ? t("verifying") : t("confirm")) {
                Task { await registerDeviceKey() }
            }
            .disabled(keyPassword.isEmpty || keyRegistering)
        } message: {
            Text(keyRegisterError ?? t("confirmPasswordBody"))
        }
    }

    private func registerDeviceKey() async {
        keyRegistering = true
        defer { keyRegistering = false }
        do {
            let publicKey = try DeviceKeyManager.shared.generateKeyPair()
            _ = try await NetworkClient.shared.registerDeviceKey(publicKey: publicKey, password: keyPassword)
            hasDeviceKey = true
            keyPassword = ""
        } catch let NetworkError.httpError(statusCode) {
            // Don't leave an unregistered key sitting in the Secure Enclave -- hasDeviceKey
            // must keep meaning "the server also has this key".
            DeviceKeyManager.shared.removeKey()
            keyRegisterError = statusCode == 400 ? "Incorrect password." : "Something went wrong. Please try again."
            showKeyPasswordPrompt = true
        } catch {
            DeviceKeyManager.shared.removeKey()
            keyRegisterError = "Couldn't reach itunda. Check your connection and try again."
            showKeyPasswordPrompt = true
        }
    }
}
