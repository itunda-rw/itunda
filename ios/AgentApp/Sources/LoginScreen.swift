import SwiftUI
import CoreDesignSystem

// Real French/Kinyarwanda/English localization (2026-08-15) -- this standalone target
// had zero localization of its own before this, same fix as RiderApp's/MerchantApp's
// identical LoginScreen.swift (see RiderApp's own doc comment for the full reasoning).
enum AgentAppLocale: String { case en, rw, fr }

private let agentLoginStrings: [AgentAppLocale: [String: String]] = [
    .en: [
        "agent": "Agent",
        "subtitle": "Use the itunda account assigned to this store.",
        "phoneNumber": "Phone number",
        "password": "Password",
        "signingIn": "Signing in…",
        "signIn": "Sign in",
        "language": "Language",
        "emptyFields": "Enter your phone number and password.",
        "notAgentOrUnreachable": "This account is not an active agent operator, or the service could not be reached.",
    ],
    .rw: [
        "agent": "Umukozi",
        "subtitle": "Koresha konti ya itunda yahawe iri duka.",
        "phoneNumber": "Numero ya telefoni",
        "password": "Ijambo ry'ibanga",
        "signingIn": "Kwinjira…",
        "signIn": "Injira",
        "language": "Ururimi",
        "emptyFields": "Andika numero yawe ya telefoni n'ijambo ry'ibanga.",
        "notAgentOrUnreachable": "Iyi konti si iy'umukozi ukora, cyangwa serivisi ntiyagezweho.",
    ],
    .fr: [
        "agent": "Agent",
        "subtitle": "Utilisez le compte itunda attribué à cette boutique.",
        "phoneNumber": "Numéro de téléphone",
        "password": "Mot de passe",
        "signingIn": "Connexion en cours…",
        "signIn": "Se connecter",
        "language": "Langue",
        "emptyFields": "Entrez votre numéro de téléphone et votre mot de passe.",
        "notAgentOrUnreachable": "Ce compte n'est pas un agent opérateur actif, ou le service n'a pas pu être joint.",
    ],
]

private let agentLocaleStorageKey = "itunda.locale"
private let agentSupportedLocales: [AgentAppLocale] = [.en, .rw, .fr]

private func loadAgentStoredLocale() -> AgentAppLocale {
    if let raw = UserDefaults.standard.string(forKey: agentLocaleStorageKey), let locale = AgentAppLocale(rawValue: raw) {
        return locale
    }
    let preferred = Locale.preferredLanguages.first ?? "en"
    if preferred.hasPrefix("rw") { return .rw }
    if preferred.hasPrefix("fr") { return .fr }
    return .en
}

/// Real agent-operator login, mirroring Android agentapp's own LoginScreen.kt exactly:
/// validates the role before leaving the sign-in screen (a normal consumer login must
/// not look like a usable cashier session) by requiring a real GET /api/v1/agent/me
/// to succeed, not just a successful password check.
struct LoginScreen: View {
    let onLoggedIn: () -> Void

    @State private var locale: AgentAppLocale = loadAgentStoredLocale()
    @State private var phoneNumber = ""
    @State private var password = ""
    @State private var error: String?
    @State private var busy = false

    private func t(_ key: String) -> String {
        agentLoginStrings[locale]?[key] ?? agentLoginStrings[.en]?[key] ?? key
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Spacer()
            HStack {
                Text("itunda").font(.largeTitle).bold()
                Spacer()
                Button(action: {
                    let currentIndex = agentSupportedLocales.firstIndex(of: locale) ?? 0
                    locale = agentSupportedLocales[(currentIndex + 1) % agentSupportedLocales.count]
                    UserDefaults.standard.set(locale.rawValue, forKey: agentLocaleStorageKey)
                }) {
                    Text(locale.rawValue.uppercased())
                        .font(.subheadline).foregroundColor(.secondary)
                }
                .accessibilityLabel(t("language"))
            }
            Text(t("agent")).font(.title2).foregroundColor(.secondary)
            Text(t("subtitle"))
                .font(.subheadline).foregroundColor(.secondary)

            IdsTextField(t("phoneNumber"), text: $phoneNumber, keyboardType: .phonePad)
            IdsTextField(t("password"), text: $password, isSecure: true)

            if let error {
                Text(error).foregroundColor(.red).font(.footnote)
            }

            Button(action: { Task { await login() } }) {
                Text(busy ? t("signingIn") : t("signIn"))
                    .bold().foregroundColor(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 14)
                    .background(IDS.Colors.brand).cornerRadius(12)
            }
            .disabled(busy)

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
            let res = try await AgentNetworkClient.shared.login(LoginRequest(phoneNumber: phoneNumber, password: password))
            AgentKeychainTokenStore.shared.save(accessToken: res.accessToken)
            // Validate the role before leaving the sign-in screen -- see this
            // struct's own doc comment.
            _ = try await AgentNetworkClient.shared.me()
            // Real push device-token registration -- best-effort, fire-and-forget: a
            // registration failure must never block an otherwise successful login.
            // See NetworkClient.swift's own doc comment.
            Task {
                _ = try? await AgentNetworkClient.shared.registerDeviceToken(
                    RegisterDeviceTokenRequest(platform: "IOS", token: AgentDeviceStore.shared.getOrCreateDeviceId())
                )
            }
            onLoggedIn()
        } catch {
            AgentKeychainTokenStore.shared.clear()
            self.error = t("notAgentOrUnreachable")
        }
    }
}
