import SwiftUI
import CoreDesignSystem
import CoreIdentity
import CoreNetwork

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
//
// Widened to French (2026-08-15), matching the identical fix made the same day to
// AppLocale/localeStorageKey/loadStoredLocale moved to CoreDesignSystem's own
// AppLocale.swift (2026-08-30) -- see that file's doc comment. `.fr` case
// added rather than a separate enum, so every existing
// `[AppLocale: [String: String]]` dictionary and every `locale ==` comparison
// across both files keeps working unchanged; only the dictionaries
// themselves and the switcher's cycle logic below needed new entries.

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
        // Real Toss-sourced passwordless-login rollout (2026-08-23) -- see
        // AccountPinPad.swift's own doc comment.
        "pinCreateHeadline": "Create a 6-digit PIN",
        "pinCreateSubtitle": "You'll use this PIN to sign in next time.",
        "pinConfirmHeadline": "Confirm your PIN",
        "pinLoginHeadline": "Enter your PIN",
        "pinMismatch": "That didn't match. Try again.",
        "checkingDevice": "Checking this device…",
        // Real, sourced Toss simplification (2026-08-24, toss.tech/article/signup):
        // Toss found iOS users completed signup at a real, measurably higher rate
        // than Android, root-caused to iOS's first screen explaining WHY personal
        // info was being asked for, while Android's jumped straight to the field
        // with zero context -- adding the missing context closed the gap. itunda
        // had the identical divergence, just flipped: Android's own LoginScreen.kt
        // already had these two subtitles (login_subtitle_name/login_subtitle_phone
        // in strings.xml), iOS never did. Same copy, same tone, ported verbatim
        // rather than reworded, to stay consistent across platforms.
        "nameContext": "This is how you'll appear to friends and merchants.",
        "phoneContext": "We'll check if you already have an account.",
        // Real Toss/Korean-fintech-style 약관 동의 (terms consent), added to
        // backend/bank-mfe 2026-08-18 but never ported to this app until 2026-08-30 --
        // see NetworkClient.swift's RegisterRequest.acceptedTermsIds doc comment.
        "termsHeadline": "Agree to itunda's terms",
        "termsSubtitle": "Please review and accept before we create your account.",
        "termsAgreeAll": "Agree to all",
        "termsRequired": "Required",
        "termsOptional": "Optional",
        "termsContinue": "Continue",
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
        "pinCreateHeadline": "Shyiraho PIN y'imibare 6",
        "pinCreateSubtitle": "Uzakoresha iyi PIN igihe uzongera kwinjira.",
        "pinConfirmHeadline": "Emeza PIN yawe",
        "pinLoginHeadline": "Andika PIN yawe",
        "pinMismatch": "Ntibihuye. Ongera ugerageze.",
        "checkingDevice": "Kugenzura iyi terefone…",
        "nameContext": "Ni ko uzagaragara ku ncuti n'abacuruzi.",
        "phoneContext": "Tuzareba niba ufite konti isanzwe.",
        "termsHeadline": "Emeza amabwiriza ya itunda",
        "termsSubtitle": "Nyamuneka soma hanyuma wemeze mbere yo gufungura konti yawe.",
        "termsAgreeAll": "Emeza byose",
        "termsRequired": "Bisabwa",
        "termsOptional": "Si ngombwa",
        "termsContinue": "Komeza",
    ],
    .fr: [
        "tagline_register": "Créez votre compte",
        "tagline_login": "Connectez-vous pour continuer",
        "firstName": "Prénom",
        "lastName": "Nom",
        "referralCode": "Code de parrainage (facultatif)",
        "phoneNumber": "Numéro de téléphone",
        "password": "Mot de passe",
        "createAccount": "Créer un compte",
        "logIn": "Se connecter",
        "switchToLogin": "Vous avez déjà un compte ? Connectez-vous",
        "switchToRegister": "Nouveau sur itunda ? Créez un compte",
        "language": "Langue",
        "pinCreateHeadline": "Créez un code PIN à 6 chiffres",
        "pinCreateSubtitle": "Vous utiliserez ce code PIN pour vous connecter la prochaine fois.",
        "pinConfirmHeadline": "Confirmez votre code PIN",
        "pinLoginHeadline": "Entrez votre code PIN",
        "pinMismatch": "Cela ne correspond pas. Réessayez.",
        "checkingDevice": "Vérification de cet appareil…",
        "nameContext": "C'est ainsi que vous apparaîtrez auprès de vos amis et des commerçants.",
        "phoneContext": "Nous allons vérifier si vous avez déjà un compte.",
        "termsHeadline": "Acceptez les conditions d'itunda",
        "termsSubtitle": "Veuillez les consulter et les accepter avant de créer votre compte.",
        "termsAgreeAll": "Tout accepter",
        "termsRequired": "Obligatoire",
        "termsOptional": "Facultatif",
        "termsContinue": "Continuer",
    ],
]

private let supportedLocales: [AppLocale] = [.en, .rw, .fr]

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

    // Real Toss-sourced passwordless-login rollout (2026-08-23) -- see
    // AccountPinPad.swift's own doc comment for the full sourced account.
    // `pinFirstEntry` holds the register-mode "create" step's PIN while the
    // "confirm" step is shown; `attemptingPasswordless` gates a silent biometric-
    // first login attempt (see the `.task` below) tried automatically on this
    // screen's appearance -- real Toss's own actual day-to-day login is Face ID/
    // fingerprint, with the PIN only as its standing fallback.
    @State private var pinFirstEntry: String?
    @State private var attemptingPasswordless = false

    // Real Toss/Korean-fintech-style 약관 동의 (terms consent) -- see
    // NetworkClient.swift's RegisterRequest.acceptedTermsIds own doc comment. Starts
    // empty on purpose (never pre-ticked, same dark-pattern-ban discipline
    // bank-mfe's RegisterPage.tsx already follows); `termsAccepted` gates the PIN
    // pad below since this screen has no separate "Next" step per field the way
    // Android's LoginScreen.kt does.
    @State private var terms: [TermsDocument] = []
    @State private var acceptedTermsIds: Set<String> = []
    @State private var termsAccepted = false

    private var allRequiredTermsAccepted: Bool {
        let required = terms.filter { $0.required }
        return !required.isEmpty && required.allSatisfy { acceptedTermsIds.contains($0.id) }
    }

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
                        // Real first in-app language switcher (2026-08-08), widened to
                        // a 3-way cycle (2026-08-15) when French joined as a real
                        // locale here too -- see this file's own top-of-file doc
                        // comment for the full context.
                        Button(action: {
                            let currentIndex = supportedLocales.firstIndex(of: locale) ?? 0
                            locale = supportedLocales[(currentIndex + 1) % supportedLocales.count]
                            UserDefaults.standard.set(locale.rawValue, forKey: localeStorageKey)
                        }) {
                            Text(locale.rawValue.uppercased())
                                .font(IDS.Typography.bodyMedium)
                                .foregroundColor(IDS.Colors.textSecondary)
                        }
                        .accessibilityLabel(t("language"))
                    }
                    Text(isRegisterMode ? t("tagline_register") : t("tagline_login"))
                        .font(IDS.Typography.bodyMedium)
                        .foregroundColor(IDS.Colors.textSecondary)

                    Spacer(minLength: 24)

                    // Real Toss-sourced passwordless-login rollout (2026-08-23) --
                    // while a real biometric-signed attempt is in flight (see this
                    // screen's own .task below), the whole form is replaced by this
                    // indicator, same as bank-mfe's identical "Checking this
                    // device…" LoginPage state -- Toss's own real day-to-day login
                    // is a near-blank screen plus Face ID, not a form sitting behind
                    // a biometric prompt.
                    if attemptingPasswordless {
                        VStack(spacing: 12) {
                            ProgressView().tint(IDS.Colors.brand)
                            Text(t("checkingDevice"))
                                .font(IDS.Typography.bodyMedium)
                                .foregroundColor(IDS.Colors.textSecondary)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 40)
                    } else {
                        // Real "Minimum Input" simplicity fix (item 244, docs/DESIGN_REFERENCES.md
                        // §11, rule #4), closing an iOS-only gap Android's own LoginScreen.kt
                        // already had a real fix for (rememberAutoFocus). Auto-focuses whichever
                        // field is first visible for the current mode.
                        if isRegisterMode {
                            // Real, sourced Toss simplification (2026-08-24) -- see
                            // this file's own loginStrings doc comment for
                            // nameContext/phoneContext. Toss's own real finding:
                            // explaining WHY before asking for personal info
                            // measurably reduces signup drop-off.
                            //
                            // Real Toss-sourced transition (2026-08-29, matching
                            // Android's own already-real LoginScreen.kt
                            // AnimatedContent -- "a subtle fade/rise, not a
                            // horizontal slide, since this isn't a page
                            // navigation") -- this whole block used to
                            // appear/disappear in the same frame as the
                            // login/register toggle tap, iOS's only real gap
                            // versus Android's identical flow (found via an
                            // audit fork this session).
                            VStack(alignment: .leading, spacing: 8) {
                                Text(t("nameContext"))
                                    .font(IDS.Typography.caption)
                                    .foregroundColor(IDS.Colors.textSecondary)
                                IdsTextField(t("firstName"), text: $firstName, autoFocus: true)
                                IdsTextField(t("lastName"), text: $lastName)
                                IdsTextField(t("referralCode"), text: $referralCode)
                                Text(t("phoneContext"))
                                    .font(IDS.Typography.caption)
                                    .foregroundColor(IDS.Colors.textSecondary)
                            }
                            .transition(.opacity.combined(with: .move(edge: .top)))
                        }

                        IdsTextField(t("phoneNumber"), text: $phoneNumber, keyboardType: .phonePad, autoFocus: !isRegisterMode)

                        // Real Toss-sourced passwordless-login rollout (2026-08-23) --
                        // replaces the free-form password IdsTextField this used to
                        // render with AccountPinPad (see that file's own doc comment).
                        // Auto-submits on its own at 6 digits, so the bottom
                        // Log in/Create account button below is gone entirely --
                        // register does a real create-then-confirm pair first.
                        if isRegisterMode {
                            Group {
                                if !termsAccepted {
                                    TermsSection(
                                        t: t, terms: terms, acceptedTermsIds: $acceptedTermsIds,
                                        canContinue: allRequiredTermsAccepted,
                                        onContinue: { withAnimation(.easeInOut(duration: 0.2)) { termsAccepted = true } }
                                    )
                                } else if pinFirstEntry == nil {
                                    AccountPinPad(
                                        headline: t("pinCreateHeadline"),
                                        subtitle: t("pinCreateSubtitle"),
                                        errorMessage: errorMessage,
                                        onComplete: { entered in
                                            errorMessage = nil
                                            withAnimation(.easeInOut(duration: 0.2)) { pinFirstEntry = entered }
                                        }
                                    )
                                } else {
                                    AccountPinPad(
                                        headline: t("pinConfirmHeadline"),
                                        errorMessage: errorMessage,
                                        busy: isSubmitting,
                                        onComplete: { entered in
                                            if entered == pinFirstEntry {
                                                password = entered
                                                submit()
                                            } else {
                                                withAnimation(.easeInOut(duration: 0.2)) { pinFirstEntry = nil }
                                                errorMessage = t("pinMismatch")
                                            }
                                        }
                                    )
                                }
                            }
                            .transition(.opacity.combined(with: .move(edge: .top)))
                        } else {
                            AccountPinPad(
                                headline: t("pinLoginHeadline"),
                                errorMessage: errorMessage,
                                busy: isSubmitting,
                                onComplete: { entered in
                                    password = entered
                                    submit()
                                }
                            )
                        }
                    }

                    Spacer(minLength: 16)
                }

                Group {
                    if !attemptingPasswordless {
                        Button(action: {
                            withAnimation(.easeInOut(duration: 0.2)) { isRegisterMode.toggle() }
                            errorMessage = nil
                            pinFirstEntry = nil
                            termsAccepted = false
                            acceptedTermsIds = []
                            if isRegisterMode && terms.isEmpty {
                                Task { terms = await sessionManager.getTerms() }
                            }
                        }) {
                            Text(isRegisterMode ? t("switchToLogin") : t("switchToRegister"))
                                .font(IDS.Typography.bodyMedium)
                                .foregroundColor(IDS.Colors.textBrand)
                        }
                        .padding(.top, 8)
                    }

                    Spacer(minLength: 80)
                }
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task {
            await tryPasswordlessLogin()
        }
    }

    // Real Toss-sourced passwordless-login rollout (2026-08-23) -- see this
    // screen's own attemptingPasswordless doc comment. Mirrors DeviceStepUpHost's
    // own tryBiometricStepUp exactly (same withCheckedContinuation bridge over
    // DeviceKeyManager's completion-handler-based signChallenge), except this
    // resolves the user from a remembered phone number rather than an
    // already-authenticated JWT -- this IS the initial login itself.
    @MainActor
    private func tryPasswordlessLogin() async {
        guard !isRegisterMode,
              let remembered = SessionManager.rememberedPhoneNumber(),
              DeviceKeyManager.shared.hasKey()
        else { return }
        phoneNumber = remembered
        attemptingPasswordless = true
        do {
            let challenge = try await sessionManager.loginDeviceChallenge(phoneNumber: remembered)
            guard let challengeData = Data(base64Encoded: challenge) else {
                attemptingPasswordless = false
                return
            }
            let signatureBase64: String? = await withCheckedContinuation { continuation in
                DeviceKeyManager.shared.signChallenge(challengeData, reason: "Sign in to itunda") { signature, _ in
                    continuation.resume(returning: signature)
                }
            }
            guard let signatureBase64 else {
                attemptingPasswordless = false
                return
            }
            // Falls through silently to the PIN pad already rendered below on any
            // failure (a declined/failed biometric attempt must never strand the
            // user with no other way in) -- attemptingPasswordless = false does
            // that on its own; success flips sessionManager.sessionState instead,
            // which the caller (ContentView/ItundaApp.swift) already observes to
            // swap away from this screen.
            _ = await sessionManager.loginWithDeviceSignature(phoneNumber: remembered, signatureBase64: signatureBase64)
            attemptingPasswordless = false
        } catch {
            attemptingPasswordless = false
        }
    }

    private func submit() {
        errorMessage = nil
        isSubmitting = true
        Task {
            let trimmedReferralCode = referralCode.trimmingCharacters(in: .whitespaces)
            // Real Toss-sourced passwordless-login rollout (2026-08-23) -- publish
            // this device's Secure Enclave key alongside the password/PIN submission
            // (same real key item 246 already established, see DeviceKeyManager
            // .exportPublicKeyIfPresent's own doc comment), so the NEXT login can
            // skip straight to the biometric attempt above with no separate
            // Settings-toggle trip required.
            let devicePublicKey = DeviceKeyManager.shared.exportPublicKeyIfPresent() ?? (try? DeviceKeyManager.shared.generateKeyPair())
            let result = isRegisterMode
                ? await sessionManager.register(
                    phoneNumber: phoneNumber, password: password, firstName: firstName, lastName: lastName,
                    referralCode: trimmedReferralCode.isEmpty ? nil : trimmedReferralCode,
                    devicePublicKey: devicePublicKey, acceptedTermsIds: Array(acceptedTermsIds)
                )
                : await sessionManager.login(phoneNumber: phoneNumber, password: password, devicePublicKey: devicePublicKey)
            isSubmitting = false
            if case let .failure(message) = result {
                errorMessage = message
            }
        }
    }
}
