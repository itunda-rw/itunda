import SwiftUI
import CoreNetwork
import CoreDesignSystem

/// Real standalone device-management screen (2026-09-01) -- closes the disclosed
/// gap in AccountManageScreen.swift's own doc comment: "Manage devices" used to
/// route to the general Settings screen because iOS had no standalone
/// device-list screen (devices were only a `Section` inside SettingsScreen.swift).
/// Same real GET/POST/DELETE /api/v1/auth/devices endpoints SettingsScreen.swift
/// already uses (NetworkClient.getMyDevices/revokeDevice) -- this screen owns its
/// own lightweight @State-driven fetch rather than depending on
/// SettingsViewModel (internal to SettingsScreen.swift, and coupling two
/// unrelated screens' state would be worse than a few lines of duplication).
/// Row shape and locale strings mirror SettingsScreen.swift's own Devices
/// section exactly (same real EN/RW/FR copy, not re-translated).
struct DeviceListScreen: View {
    let onBack: () -> Void

    @State private var devices: [TrustedDeviceDto] = []
    @State private var loaded = false
    @State private var locale: AppLocale = loadStoredLocale()

    private let strings: [AppLocale: [String: String]] = [
        .en: [
            "title": "Devices", "unknownDevice": "Unknown device", "thisDevice": " (this device)",
            "trusted": "Trusted -- can send money", "notVerified": "Not verified -- can't send money yet",
            "remove": "Remove", "empty": "No devices found.",
        ],
        .rw: [
            "title": "Ibikoresho", "unknownDevice": "Ikoresho kitazwi", "thisDevice": " (iki gikoresho)",
            "trusted": "Byemewe -- gishobora kohereza amafaranga", "notVerified": "Ntibyemejwe -- ntigishobora kohereza amafaranga",
            "remove": "Kuraho", "empty": "Nta bikoresho biboneka.",
        ],
        .fr: [
            "title": "Appareils", "unknownDevice": "Appareil inconnu", "thisDevice": " (cet appareil)",
            "trusted": "Approuvé -- peut envoyer de l'argent", "notVerified": "Non vérifié -- ne peut pas encore envoyer d'argent",
            "remove": "Retirer", "empty": "Aucun appareil trouvé.",
        ],
    ]

    private func t(_ key: String) -> String {
        strings[locale]?[key] ?? strings[.en]?[key] ?? key
    }

    private func load() async {
        devices = (try? await NetworkClient.shared.getMyDevices().devices) ?? []
        loaded = true
    }

    private func revoke(_ deviceId: String) async {
        _ = try? await NetworkClient.shared.revokeDevice(deviceId: deviceId)
        await load()
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    IDS.Icons.back(size: 18, color: IDS.Colors.textPrimary, relativeTo: .body).frame(width: 44, height: 44)
                }
                .accessibilityLabel("Back")
                Spacer()
            }
            .padding(.horizontal, 8)

            Text(t("title"))
                .font(IDS.scaledFont(size: 20, weight: .bold, relativeTo: .title2))
                .foregroundColor(IDS.Colors.textPrimary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 24)
                .padding(.top, 4)
                .padding(.bottom, 12)

            if !loaded {
                Spacer()
            } else if devices.isEmpty {
                Text(t("empty"))
                    .font(IDS.scaledFont(size: 14, weight: .regular, relativeTo: .callout))
                    .foregroundColor(IDS.Colors.textSecondary)
                    .padding(.horizontal, 24)
                    .padding(.top, 8)
                Spacer()
            } else {
                ScrollView {
                    VStack(spacing: 0) {
                        ForEach(devices) { device in
                            HStack(spacing: 14) {
                                Image(systemName: "iphone")
                                    .frame(width: 44, height: 44)
                                    .background(Color.gray.opacity(0.15))
                                    .clipShape(Circle())
                                VStack(alignment: .leading, spacing: 2) {
                                    Text((device.deviceName ?? t("unknownDevice")) + (device.deviceId == DeviceStore.shared.getOrCreateDeviceId() ? t("thisDevice") : ""))
                                        .font(IDS.scaledFont(size: 15, weight: .semibold, relativeTo: .subheadline))
                                        .foregroundColor(IDS.Colors.textPrimary)
                                    Text(device.trusted ? t("trusted") : t("notVerified"))
                                        .font(IDS.scaledFont(size: 12, weight: .regular, relativeTo: .caption1))
                                        .foregroundColor(device.trusted ? IDS.Colors.textSecondary : .red)
                                }
                                Spacer()
                                Button(t("remove")) { Task { await revoke(device.deviceId) } }
                                    .font(IDS.scaledFont(size: 13, weight: .semibold, relativeTo: .footnote))
                                    .foregroundColor(.red)
                            }
                            .padding(.vertical, 10)
                        }
                    }
                    .padding(.horizontal, 24)
                }
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .navigationBarHidden(true)
        .task { await load() }
    }
}
