import SwiftUI
import CoreNetwork
import CoreDesignSystem

/// Real "Verification method" screen (2026-09-01, direct user-supplied Toss Bank
/// Manage-screen screenshot) -- shows exactly two real, already-tracked facts
/// rather than a fabricated 2FA-method list: whether the phone on this account is
/// verified (PublicUser.phoneVerified) and whether THIS device has completed real
/// biometric/passwordless device-key registration (TrustedDeviceDto.publicKey !=
/// nil for the entry matching this device's own real
/// DeviceStore.shared.getOrCreateDeviceId()) -- mirrors web's
/// AccountManageScreen.tsx VerificationMethodScreen / Android's
/// VerificationMethodScreen exactly. Lives in App/Sources (not
/// FeatureBanking's AccountManageScreen.swift) because DeviceStore.shared is an
/// App-module type, same reason DeviceListScreen.swift lives here too.
struct VerificationMethodScreen: View {
    let onBack: () -> Void

    @State private var phoneVerified: Bool?
    @State private var deviceVerified: Bool?
    @State private var locale: AppLocale = loadStoredLocale()

    private let strings: [AppLocale: [String: String]] = [
        .en: [
            "title": "Verification method", "phone": "Phone number", "device": "This device",
            "verified": "Verified", "notVerified": "Not verified", "loading": "…",
            "footnote": "\"This device\" reflects real biometric/passwordless verification for the device you're using right now -- set it up from Manage devices.",
        ],
        .rw: [
            "title": "Uburyo bwo kwemeza", "phone": "Nimero ya telefoni", "device": "Iki gikoresho",
            "verified": "Byemejwe", "notVerified": "Ntibyemejwe", "loading": "…",
            "footnote": "\"Iki gikoresho\" bigaragaza kwemeza nyakuri (biometric/passwordless) ku gikoresho ukoreramo ubu -- bishyireho uhereye kuri Gucunga ibikoresho.",
        ],
        .fr: [
            "title": "Méthode de vérification", "phone": "Numéro de téléphone", "device": "Cet appareil",
            "verified": "Vérifié", "notVerified": "Non vérifié", "loading": "…",
            "footnote": "« Cet appareil » reflète la vérification biométrique/sans mot de passe réelle pour l'appareil que vous utilisez actuellement -- configurez-la depuis Gérer les appareils.",
        ],
    ]

    private func t(_ key: String) -> String {
        strings[locale]?[key] ?? strings[.en]?[key] ?? key
    }

    private func label(_ v: Bool?) -> String {
        guard let v else { return t("loading") }
        return v ? t("verified") : t("notVerified")
    }

    private func color(_ v: Bool?) -> Color {
        v == true ? IDS.Colors.textBrand : IDS.Colors.textSecondary
    }

    private func load() async {
        phoneVerified = try? await NetworkClient.shared.getProfile().user.phoneVerified
        let deviceId = DeviceStore.shared.getOrCreateDeviceId()
        let devices = (try? await NetworkClient.shared.getMyDevices().devices) ?? []
        deviceVerified = devices.contains { $0.deviceId == deviceId && $0.publicKey != nil }
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

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Text(t("title"))
                        .font(IDS.scaledFont(size: 20, weight: .bold, relativeTo: .title2))
                        .foregroundColor(IDS.Colors.textPrimary)
                        .padding(.bottom, 20)

                    HStack {
                        Text(t("phone")).font(IDS.scaledFont(size: 14, weight: .regular, relativeTo: .callout)).foregroundColor(IDS.Colors.textPrimary)
                        Spacer()
                        Text(label(phoneVerified)).font(IDS.scaledFont(size: 14, weight: .semibold, relativeTo: .callout)).foregroundColor(color(phoneVerified))
                    }
                    .padding(.vertical, 12)

                    HStack {
                        Text(t("device")).font(IDS.scaledFont(size: 14, weight: .regular, relativeTo: .callout)).foregroundColor(IDS.Colors.textPrimary)
                        Spacer()
                        Text(label(deviceVerified)).font(IDS.scaledFont(size: 14, weight: .semibold, relativeTo: .callout)).foregroundColor(color(deviceVerified))
                    }
                    .padding(.vertical, 12)

                    Text(t("footnote"))
                        .font(IDS.scaledFont(size: 11, weight: .regular, relativeTo: .caption2))
                        .foregroundColor(IDS.Colors.textTertiary)
                        .padding(.top, 16)
                        .padding(.bottom, 24)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 24)
                .padding(.top, 4)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .navigationBarHidden(true)
        .task { await load() }
    }
}
