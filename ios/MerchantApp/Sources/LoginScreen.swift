import SwiftUI
import CoreDesignSystem

// Real French/Kinyarwanda/English localization (2026-08-15) -- this standalone target
// had zero localization of its own before this, same fix as RiderApp's identical
// LoginScreen.swift (see that file's own doc comment for the full reasoning: separate
// module can't reach App's private AppLocale type, .pbxproj has no
// file-system-synchronized groups, plain dictionary stays the honest minimum).
enum MerchantAppLocale: String { case en, rw, fr }

private let merchantLoginStrings: [MerchantAppLocale: [String: String]] = [
    .en: [
        "title": "Itunda Merchant",
        "subtitle": "Log in with your existing itunda account to run your shop.",
        "phoneNumber": "Phone number",
        "password": "Password",
        "loggingIn": "Logging in…",
        "logIn": "Log in",
        "noAccount": "Don't have an itunda account yet? Register in the main itunda app first, then come back here to run your shop.",
        "language": "Language",
        "emptyFields": "Enter your phone number and password.",
        "incorrectCredentials": "Incorrect phone number or password.",
        "unreachable": "Couldn't reach itunda. Try again.",
        "connectionError": "Couldn't reach itunda. Check your connection and try again.",
    ],
    .rw: [
        "title": "Itunda Merchant",
        "subtitle": "Injira ukoresheje konti yawe ya itunda kugira ngo utangire gucunga iduka ryawe.",
        "phoneNumber": "Numero ya telefoni",
        "password": "Ijambo ry'ibanga",
        "loggingIn": "Kwinjira…",
        "logIn": "Injira",
        "noAccount": "Ntufite konti ya itunda? Banza wifungurire konti muri porogaramu nkuru ya itunda, hanyuma ugaruke hano ugere gucunga iduka ryawe.",
        "language": "Ururimi",
        "emptyFields": "Andika numero yawe ya telefoni n'ijambo ry'ibanga.",
        "incorrectCredentials": "Numero ya telefoni cyangwa ijambo ry'ibanga sibyo.",
        "unreachable": "Ntibishoboka kugera kuri itunda. Gerageza nanone.",
        "connectionError": "Ntibishoboka kugera kuri itunda. Reba interineti yawe hanyuma ugerageze nanone.",
    ],
    .fr: [
        "title": "Itunda Merchant",
        "subtitle": "Connectez-vous avec votre compte itunda existant pour gérer votre boutique.",
        "phoneNumber": "Numéro de téléphone",
        "password": "Mot de passe",
        "loggingIn": "Connexion en cours…",
        "logIn": "Se connecter",
        "noAccount": "Vous n'avez pas encore de compte itunda ? Inscrivez-vous d'abord dans l'application principale itunda, puis revenez ici pour gérer votre boutique.",
        "language": "Langue",
        "emptyFields": "Entrez votre numéro de téléphone et votre mot de passe.",
        "incorrectCredentials": "Numéro de téléphone ou mot de passe incorrect.",
        "unreachable": "Impossible de joindre itunda. Réessayez.",
        "connectionError": "Impossible de joindre itunda. Vérifiez votre connexion et réessayez.",
    ],
]

private let merchantLocaleStorageKey = "itunda.locale"
private let merchantSupportedLocales: [MerchantAppLocale] = [.en, .rw, .fr]

private func loadMerchantStoredLocale() -> MerchantAppLocale {
    if let raw = UserDefaults.standard.string(forKey: merchantLocaleStorageKey), let locale = MerchantAppLocale(rawValue: raw) {
        return locale
    }
    let preferred = Locale.preferredLanguages.first ?? "en"
    if preferred.hasPrefix("rw") { return .rw }
    if preferred.hasPrefix("fr") { return .fr }
    return .en
}

struct LoginScreen: View {
    let onLoggedIn: () -> Void

    @State private var locale: MerchantAppLocale = loadMerchantStoredLocale()
    @State private var phoneNumber = ""
    @State private var password = ""
    @State private var error: String?
    @State private var busy = false

    private func t(_ key: String) -> String {
        merchantLoginStrings[locale]?[key] ?? merchantLoginStrings[.en]?[key] ?? key
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Spacer()
            HStack {
                Text(t("title")).font(.largeTitle).bold()
                Spacer()
                Button(action: {
                    let currentIndex = merchantSupportedLocales.firstIndex(of: locale) ?? 0
                    locale = merchantSupportedLocales[(currentIndex + 1) % merchantSupportedLocales.count]
                    UserDefaults.standard.set(locale.rawValue, forKey: merchantLocaleStorageKey)
                }) {
                    Text(locale.rawValue.uppercased())
                        .font(.subheadline).foregroundColor(.secondary)
                }
                .accessibilityLabel(t("language"))
            }
            Text(t("subtitle"))
                .font(.subheadline).foregroundColor(.secondary)

            IdsTextField(t("phoneNumber"), text: $phoneNumber, keyboardType: .phonePad)
            IdsTextField(t("password"), text: $password, isSecure: true)

            if let error {
                Text(error).foregroundColor(.red).font(.footnote)
            }

            Button(action: { Task { await login() } }) {
                Text(busy ? t("loggingIn") : t("logIn"))
                    .bold().foregroundColor(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 14)
                    .background(IDS.Colors.brand).cornerRadius(12)
            }
            .disabled(busy)

            Text(t("noAccount"))
                .font(.caption).foregroundColor(.secondary)
                .frame(maxWidth: .infinity, alignment: .center)
                .multilineTextAlignment(.center)

            Spacer()
        }
        .padding(24)
    }

    private func login() async {
        guard !phoneNumber.isEmpty, !password.isEmpty else {
            error = t("emptyFields")
            return
        }
        busy = true
        error = nil
        defer { busy = false }
        do {
            let res = try await MerchantNetworkClient.shared.login(LoginRequest(
                phoneNumber: phoneNumber,
                password: password,
                deviceId: MerchantDeviceStore.shared.getOrCreateDeviceId(),
                deviceName: MerchantDeviceStore.shared.getDeviceName()
            ))
            MerchantKeychainTokenStore.shared.saveSession(userId: res.user.id, accessToken: res.accessToken, refreshToken: res.refreshToken)
            // Real push device-token registration (item 130) -- best-effort,
            // fire-and-forget: a registration failure must never block an otherwise
            // successful login. See NetworkClient.swift's own doc comment.
            Task {
                _ = try? await MerchantNetworkClient.shared.registerDeviceToken(
                    RegisterDeviceTokenRequest(platform: "IOS", token: MerchantDeviceStore.shared.getOrCreateDeviceId())
                )
            }
            onLoggedIn()
        } catch let NetworkError.httpErrorWithMessage(statusCode, _) {
            error = statusCode == 401 ? t("incorrectCredentials") : t("unreachable")
        } catch {
            self.error = t("connectionError")
        }
    }
}
