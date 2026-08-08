import SwiftUI
import CoreDesignSystem

// Real first slice of Kinyarwanda localization on iOS (2026-08-08) -- see Android's
// LoginScreen.kt and bank-mfe's src/i18n/ for the full context (docs/DESIGN_REFERENCES.md
// Section 19): itunda had zero locale infrastructure anywhere before this research thread.
// Kept entirely self-contained in this file rather than adding new files to the Xcode
// project: this project's .pbxproj has no file-system-synchronized groups (confirmed via
// grep before writing this), so every new Swift file needs a real, error-prone manual
// pbxproj edit -- not worth that risk for one screen's worth of strings. A plain dictionary
// (not .strings files + NSLocalizedString) for the same reason bank-mfe's own i18n avoided
// a full framework: right now this is 2 locales, a couple of screens, not the scope that
// needs it. Same honesty note as web/Android: the `rw` strings are a careful, good-faith
// translation, NOT verified by a native speaker, and should get real native-speaker review
// before being treated as production-final.
//
// AppLocale/loadStoredLocale are internal (not private), not because this needs to be a
// general-purpose module, but because OverviewScreenView (OverviewLoansCreditScoreScreens.swift,
// same target) needs the exact same locale-detection logic -- promoted once real duplication
// appeared, same "promote to shared only once it's needed twice" precedent
// packages/design-tokens already established for this codebase, not speculative reuse.
enum AppLocale: String { case en, rw }

private let loginStrings: [AppLocale: [String: String]] = [
    .en: [
        "tagline_register": "Create your account",
        "tagline_login": "Log in to continue",
        "firstName": "First name",
        "lastName": "Last name",
        "referralCode": "Referral code (optional)",
        "phoneNumber": "Phone number",
        "password": "Password",
        "createAccount": "Create account",
        "logIn": "Log in",
        "switchToLogin": "Already have an account? Log in",
        "switchToRegister": "New to itunda? Create an account",
        "language": "Language",
    ],
    .rw: [
        "tagline_register": "Fungura konti yawe",
        "tagline_login": "Injira ukomeze",
        "firstName": "Izina rya mbere",
        "lastName": "Izina rya nyuma",
        "referralCode": "Kode yo kwifashisha (si ngombwa)",
        "phoneNumber": "Numero ya telefoni",
        "password": "Ijambo ry'ibanga",
        "createAccount": "Fungura konti",
        "logIn": "Injira",
        "switchToLogin": "Usanzwe ufite konti? Injira",
        "switchToRegister": "Uri mushya kuri itunda? Fungura konti",
        "language": "Ururimi",
    ],
]

let localeStorageKey = "itunda.locale"

func loadStoredLocale() -> AppLocale {
    if let raw = UserDefaults.standard.string(forKey: localeStorageKey), let locale = AppLocale(rawValue: raw) {
        return locale
    }
    let preferred = Locale.preferredLanguages.first ?? "en"
    return preferred.hasPrefix("rw") ? .rw : .en
}

/// The login/register screen this app never had (see SessionManager.swift) --
/// gates ContentView in ItundaApp.swift behind a real authenticated session
/// instead of rendering the whole app unconditionally, mirroring Android's
/// LoginScreen.kt exactly against the same real
/// services/backend /api/v1/auth/register and /api/v1/auth/login endpoints.
struct LoginScreen: View {
    @ObservedObject var sessionManager: SessionManager

    @State private var locale: AppLocale = loadStoredLocale()
    @State private var isRegisterMode = false
    @State private var phoneNumber = ""
    @State private var password = ""
    @State private var firstName = ""
    @State private var lastName = ""
    @State private var referralCode = ""
    @State private var isSubmitting = false
    @State private var errorMessage: String?

    private func t(_ key: String) -> String {
        loginStrings[locale]?[key] ?? loginStrings[.en]?[key] ?? key
    }

    var body: some View {
        ScrollView {
            // Split across two Group blocks (2026-07-11, same fix as
            // BenefitsShopAllScreens.swift's EntireMenuScreen -- see that file's own
            // comment): this VStack has 12 direct children, and the Swift 5.8.1
            // toolchain this project builds against only supports ViewBuilder blocks
            // up to 10 children (the parameter-pack-based unlimited-children
            // ViewBuilder arrived in Swift 5.9). Group is purely a ViewBuilder
            // child-count workaround here -- it doesn't change layout.
            VStack(alignment: .leading, spacing: 12) {
                Group {
                    Spacer(minLength: 80)

                    HStack {
                        Text("itunda")
                            .font(IDS.Typography.header)
                            .foregroundColor(IDS.Colors.textPrimary)
                        Spacer()
                        // Real first in-app language switcher (2026-08-08) -- see this
                        // file's own top-of-file doc comment for the full context. Only
                        // 2 locales exist right now, so a simple toggle (shows the
                        // current selection, tap switches to the other) is the honest
                        // minimum, matching web/Android's own identical choice.
                        Button(action: {
                            locale = (locale == .en) ? .rw : .en
                            UserDefaults.standard.set(locale.rawValue, forKey: localeStorageKey)
                        }) {
                            Text(locale == .en ? "EN" : "RW")
                                .font(IDS.Typography.bodyMedium)
                                .foregroundColor(IDS.Colors.textSecondary)
                        }
                        .accessibilityLabel(t("language"))
                    }
                    Text(isRegisterMode ? t("tagline_register") : t("tagline_login"))
                        .font(IDS.Typography.bodyMedium)
                        .foregroundColor(IDS.Colors.textSecondary)

                    Spacer(minLength: 24)

                    // Real "Minimum Input" simplicity fix (item 244, docs/DESIGN_REFERENCES.md
                    // §11, rule #4), closing an iOS-only gap Android's own LoginScreen.kt
                    // already had a real fix for (rememberAutoFocus). Auto-focuses whichever
                    // field is first visible for the current mode.
                    if isRegisterMode {
                        IdsTextField(t("firstName"), text: $firstName, autoFocus: true)
                        IdsTextField(t("lastName"), text: $lastName)
                        IdsTextField(t("referralCode"), text: $referralCode)
                    }

                    IdsTextField(t("phoneNumber"), text: $phoneNumber, keyboardType: .phonePad, autoFocus: !isRegisterMode)

                    IdsTextField(t("password"), text: $password, isSecure: true)

                    if let errorMessage {
                        Text(errorMessage)
                            .font(IDS.Typography.caption)
                            .foregroundColor(.red)
                    }

                    Spacer(minLength: 16)
                }

                Group {
                    if isSubmitting {
                        HStack {
                            Spacer()
                            ProgressView().tint(IDS.Colors.brand)
                            Spacer()
                        }
                        .padding(.vertical, 16)
                    } else {
                        IdsButton(
                            text: isRegisterMode ? t("createAccount") : t("logIn"),
                            isEnabled: canSubmit,
                            action: { submit() }
                        )
                    }

                    Button(action: { isRegisterMode.toggle(); errorMessage = nil }) {
                        Text(isRegisterMode ? t("switchToLogin") : t("switchToRegister"))
                            .font(IDS.Typography.bodyMedium)
                            .foregroundColor(IDS.Colors.textBrand)
                    }
                    .padding(.top, 8)

                    Spacer(minLength: 80)
                }
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }

    private var canSubmit: Bool {
        let baseFieldsFilled = !phoneNumber.isEmpty && !password.isEmpty
        return isRegisterMode ? baseFieldsFilled && !firstName.isEmpty && !lastName.isEmpty : baseFieldsFilled
    }

    private func submit() {
        errorMessage = nil
        isSubmitting = true
        Task {
            let trimmedReferralCode = referralCode.trimmingCharacters(in: .whitespaces)
            let result = isRegisterMode
                ? await sessionManager.register(
                    phoneNumber: phoneNumber, password: password, firstName: firstName, lastName: lastName,
                    referralCode: trimmedReferralCode.isEmpty ? nil : trimmedReferralCode
                )
                : await sessionManager.login(phoneNumber: phoneNumber, password: password)
            isSubmitting = false
            if case let .failure(message) = result {
                errorMessage = message
            }
        }
    }
}
