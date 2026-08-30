import SwiftUI
import CoreDesignSystem
import CoreNetwork

/// Real, from-scratch transfer flow matching Android's TransferFlow.kt exactly
/// (2026-07-12) -- both against the same real Toss reference screenshots
/// (user-provided). Two screens:
///  - RecipientEntryScreen: "어떤 계좌로 보낼까요?" -- account number input, bank
///    label (no real detection -- see this file's own note below), numeric keypad.
///  - TransferAmountScreen: "얼마나 보낼까요?" -- from/to summary with a connector
///    line, quick-amount chips, a Next bar, and a keypad.
///
/// Dumb views taking plain primitives + callbacks, not NetworkClient/Account types
/// directly -- this Feature module can't depend back on App (see BankView.swift's
/// own note on the same constraint). TransferViewModel (App/Sources) owns the real
/// quoteTransfer/confirmTransfer calls and is the only caller.

// Real 3rd screen of Kinyarwanda localization on iOS (2026-08-08) -- extends the
// pattern LoginScreen.swift/OverviewLoansCreditScoreScreens.swift established (see
// docs/DESIGN_REFERENCES.md Section 19) into this Feature module. Deliberately its
// own self-contained dictionary rather than reusing App/Sources' AppLocale/
// loadStoredLocale directly: this module can't depend back on App (see this file's
// own note above), so those `internal` symbols aren't visible here -- exactly the
// same per-module-resources necessity Android hit (android/features/payments/impl
// needed its own values-rw/strings.xml since FeaturePayments can't see :app's
// resources either). Reads the same UserDefaults key ("itunda.locale") the
// App-target switcher writes to, so both targets stay in sync without either
// depending on the other. Same honesty note as every prior screen/platform: a
// careful, good-faith translation, NOT verified by a native Kinyarwanda speaker.
// Widened to French (2026-08-15) -- real gap found: had zero French entries even
// after the App target's own AppLocale gained .fr the same session, because this is
// a structurally separate, self-contained enum (module-boundary necessity, see this
// file's own doc comment above), not a usage of AppLocale itself. Same class of
// staleness as App/Sources' SettingsScreen.swift/DeviceStepUpView.swift/
// TransferFlowContainer.swift, all found and fixed the same day.
private enum PaymentsLocale: String { case en, rw, fr }

private func loadPaymentsLocale() -> PaymentsLocale {
    if let raw = UserDefaults.standard.string(forKey: "itunda.locale"), let locale = PaymentsLocale(rawValue: raw) {
        return locale
    }
    let preferred = Locale.preferredLanguages.first ?? "en"
    if preferred.hasPrefix("rw") { return .rw }
    if preferred.hasPrefix("fr") { return .fr }
    return .en
}

private let paymentsStrings: [PaymentsLocale: [String: String]] = [
    .en: [
        "recipientHeadline": "Which account should\nwe send to?",
        "recipientInputLabel": "Enter phone or account number",
        "recipientInputDescription": "Phone or account number, up to 16 digits",
        "selectBank": "Select bank",
        "selectBankHint": "Optional, for your own reference",
        "contacts": "Contacts",
        "addContact": "+ Add",
        "cancel": "Cancel",
        "namePlaceholder": "Name",
        "phonePlaceholder": "Phone number",
        "saveContact": "Save contact",
        "noContacts": "No saved contacts yet.",
        "next": "Next",
        "fromAccount": "From Itunda Account",
        "availableBalance": "Available RWF %@",
        "toAccount": "To account %@",
        "newRecipient": "New recipient",
        "scamWarningTitle": "Caution needed before this transfer",
        "scamWarningBody": "This recipient has been reported by %d other itunda users. Double-check before sending.",
        "scamReportedThanks": "Thanks -- this number has been reported.",
        "reportScam": "Report this number as a scam",
        "amountQuestion": "How much to send?",
        "amountMax": "Max",
        "amountInsufficient": "Not enough balance -- you have RWF %@",
        "send": "Send",
        "deleteDigit": "Delete",
    ],
    .rw: [
        "recipientHeadline": "Ni iyihe konti\ntwohereza mo?",
        "recipientInputLabel": "Andika numero ya telefoni cyangwa konti",
        "recipientInputDescription": "Numero ya telefoni cyangwa konti, ntirengeje imibare 16",
        "selectBank": "Hitamo banki",
        "selectBankHint": "Si ngombwa, ni ukugira ngo ubimenye",
        "contacts": "Abo wabitse",
        "addContact": "+ Ongeraho",
        "cancel": "Hagarika",
        "namePlaceholder": "Izina",
        "phonePlaceholder": "Numero ya telefoni",
        "saveContact": "Bika uyu muntu",
        "noContacts": "Nta bantu wabitse. Ongeraho hejuru kugira ngo wihute ubutaha.",
        "next": "Komeza",
        "fromAccount": "Biva kuri Account ya Itunda",
        "availableBalance": "Amafaranga ahari RWF %@",
        "toAccount": "Kuri konti %@",
        "newRecipient": "Uwakira mushya",
        "scamWarningTitle": "Witondere mbere yo kohereza",
        "scamWarningBody": "Uyu muntu yatanzweho raporo n'abakoresha itunda %d. Genzura neza mbere yo kohereza.",
        "scamReportedThanks": "Murakoze -- iyi numero yatanzweho raporo.",
        "reportScam": "Tanga raporo kuri iyi numero",
        "amountQuestion": "Ni angahe ushaka kohereza?",
        "amountMax": "Byose",
        "amountInsufficient": "Amafaranga ntahagije -- ufite RWF %@",
        "send": "Ohereza",
        "deleteDigit": "Siba",
    ],
    .fr: [
        "recipientHeadline": "Vers quel compte\ndevons-nous envoyer ?",
        "recipientInputLabel": "Entrez un numéro de téléphone ou de compte",
        "recipientInputDescription": "Numéro de téléphone ou de compte, jusqu'à 16 chiffres",
        "selectBank": "Choisir la banque",
        "selectBankHint": "Facultatif, pour votre propre référence",
        "contacts": "Contacts",
        "addContact": "+ Ajouter",
        "cancel": "Annuler",
        "namePlaceholder": "Nom",
        "phonePlaceholder": "Numéro de téléphone",
        "saveContact": "Enregistrer le contact",
        "noContacts": "Aucun contact enregistré pour l'instant.",
        "next": "Suivant",
        "fromAccount": "Depuis le portefeuille Itunda",
        "availableBalance": "Disponible RWF %@",
        "toAccount": "Vers le compte %@",
        "newRecipient": "Nouveau destinataire",
        "scamWarningTitle": "Prudence avant ce virement",
        "scamWarningBody": "Ce destinataire a été signalé par %d autre(s) utilisateur(s) itunda. Vérifiez bien avant d'envoyer.",
        "scamReportedThanks": "Merci -- ce numéro a été signalé.",
        "reportScam": "Signaler ce numéro comme arnaque",
        "amountQuestion": "Combien voulez-vous envoyer ?",
        "amountMax": "Max",
        "amountInsufficient": "Solde insuffisant -- vous avez RWF %@",
        "send": "Envoyer",
        "deleteDigit": "Supprimer",
    ],
]

func pt(_ key: String) -> String {
    let locale = loadPaymentsLocale()
    return paymentsStrings[locale]?[key] ?? paymentsStrings[.en]?[key] ?? key
}

private func pt(_ key: String, _ arg: String) -> String {
    String(format: pt(key), arg)
}

private func pt(_ key: String, _ arg: Int) -> String {
    String(format: pt(key), arg)
}

// Real saved-contacts list (2026-07-22) -- see rw.itunda.contacts.ContactsController's
// own doc comment on the backend. This tiny UI-facing shape (not NetworkClient's own
// ContactDto) keeps this Feature module's existing independence from App's
// NetworkClient -- the actual fetch/add calls happen in TransferFlowContainer.swift
// (App/Sources), which already has API access, and are passed down here as plain
// data + callbacks.
public struct ContactUi: Identifiable {
    public let id: String
    public let name: String
    public let phoneNumber: String
    public let bank: String
    public init(id: String, name: String, phoneNumber: String, bank: String) {
        self.id = id; self.name = name; self.phoneNumber = phoneNumber; self.bank = bank
    }
}

public struct RecipientEntryScreen: View {
    @State private var accountNumber = ""
    @State private var showAddContact = false
    @State private var newContactName = ""
    @State private var newContactPhone = ""
    let onBack: () -> Void
    let contacts: [ContactUi]
    let onAddContact: (String, String) -> Void
    let onNext: (String) -> Void

    public init(
        onBack: @escaping () -> Void,
        contacts: [ContactUi] = [], onAddContact: @escaping (String, String) -> Void = { _, _ in },
        onNext: @escaping (String) -> Void
    ) {
        self.onBack = onBack
        self.contacts = contacts
        self.onAddContact = onAddContact
        self.onNext = onNext
    }

    public var body: some View {
        VStack(spacing: 0) {
            FlowTopBar(onBack: onBack)

            VStack(alignment: .leading, spacing: 0) {
                Text(pt("recipientHeadline"))
                    .font(IDS.scaledFont(size: 26, weight: .bold, relativeTo: .title1))
                    .foregroundColor(IDS.Colors.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)

                Spacer().frame(height: 28)

                // Copy widened 2026-07-20: this same digit keypad now also accepts a
                // real phone number (a local "07XXXXXXXX" or international
                // "2507XXXXXXXX" shape is recognized and normalized client-side -- see
                // TransferViewModel.normalizeRecipientIdentifier), not just an account
                // number, now that TransferViewModel.sendTransfer calls the real
                // rw.itunda.p2p.sendDirect.
                Text(pt("recipientInputLabel"))
                    .font(IDS.scaledFont(size: 14, weight: .semibold, relativeTo: .footnote))
                    .foregroundColor(IDS.Colors.brand)
                Spacer().frame(height: 6)

                TextField("", text: $accountNumber)
                    .font(IDS.scaledFont(size: 22, weight: .semibold, relativeTo: .title3))
                    .foregroundColor(IDS.Colors.textPrimary)
                    .keyboardType(.numberPad)
                    .accessibilityLabel(pt("recipientInputDescription"))
                    .onChange(of: accountNumber) { newValue in
                        accountNumber = String(newValue.filter(\.isNumber).prefix(16))
                    }
                Spacer().frame(height: 8)
                Rectangle().fill(IDS.Colors.brand).frame(height: 2)

                Spacer().frame(height: 28)

                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(pt("selectBank"))
                            .font(IDS.scaledFont(size: 17, weight: .regular, relativeTo: .body))
                            .foregroundColor(IDS.Colors.textTertiary)
                        // Real Toss auto-detects the bank from a real BIN registry;
                        // itunda has none to check against, so this doesn't claim to
                        // (2026-07-12, matching Android's RecipientEntryScreen fix).
                        if accountNumber.isEmpty {
                            Text(pt("selectBankHint"))
                                .font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote))
                                .foregroundColor(IDS.Colors.textTertiary)
                        }
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                        .foregroundColor(IDS.Colors.textTertiary)
                }
                .padding(.vertical, 14)

                // Real saved contacts (2026-07-22), replacing the single hardcoded demo
                // row this section used to show -- see ContactUi's own doc comment
                // above; GET /api/v1/contacts had zero client UI anywhere until now.
                if accountNumber.isEmpty {
                    Spacer().frame(height: 28)
                    HStack {
                        Text(pt("contacts"))
                            .font(IDS.scaledFont(size: 14, weight: .semibold, relativeTo: .subheadline))
                            .foregroundColor(IDS.Colors.textSecondary)
                        Spacer()
                        Button(showAddContact ? pt("cancel") : pt("addContact")) { showAddContact.toggle() }
                            .font(IDS.scaledFont(size: 13, weight: .semibold, relativeTo: .footnote))
                            .foregroundColor(IDS.Colors.brand)
                    }
                    Spacer().frame(height: 12)
                    if showAddContact {
                        VStack(alignment: .leading, spacing: 8) {
                            TextField(pt("namePlaceholder"), text: $newContactName)
                                .padding(10).background(IDS.Colors.chipBackground).cornerRadius(8)
                            TextField(pt("phonePlaceholder"), text: $newContactPhone)
                                .keyboardType(.phonePad)
                                .padding(10).background(IDS.Colors.chipBackground).cornerRadius(8)
                            Button(pt("saveContact")) {
                                onAddContact(newContactName, newContactPhone)
                                newContactName = ""; newContactPhone = ""; showAddContact = false
                            }
                            .disabled(newContactName.isEmpty || newContactPhone.isEmpty)
                            .font(IDS.scaledFont(size: 14, weight: .semibold, relativeTo: .subheadline))
                        }
                        Spacer().frame(height: 12)
                    }
                    if contacts.isEmpty && !showAddContact {
                        Text(pt("noContacts"))
                            .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    }
                    ForEach(contacts) { contact in
                        RecentRecipientRow(name: contact.name, bankAndAccount: "\(contact.bank) - \(contact.phoneNumber)") {
                            accountNumber = String(contact.phoneNumber.filter(\.isNumber).prefix(16))
                        }
                    }
                }
            }
            .padding(.horizontal, 24)

            Spacer()

            if accountNumber.count >= 4 {
                FlowNextBar(enabled: true, label: pt("next")) { onNext(accountNumber) }
            }
            NumericKeypad(
                onDigit: { d in if accountNumber.count < 16 { accountNumber += d } },
                onDelete: { if !accountNumber.isEmpty { accountNumber.removeLast() } }
            )
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }
}

// Real Toss 사기계좌 조회-style pre-transfer warning (2026-07-31) -- see
// rw.itunda.p2p.ScamReportService's own doc comment on the backend. A warning, not a
// hard block, same as bank-mfe/Android's own scamCheck/ReportScamLink: itunda has no
// fraud-reimbursement protection scheme to withdraw for proceeding anyway, so this is
// simply the sender's own informed choice. Plain UI-facing shape, same
// module-independence convention ContactUi above already establishes -- the actual
// check/report calls happen in TransferViewModel (App/Sources).
public struct ScamWarningUi { public let reportCount: Int; public init(reportCount: Int) { self.reportCount = reportCount } }

public struct TransferAmountScreen: View {
    @State private var digits = ""
    // Real standalone "send as a gift" toggle (found via an uncalled-endpoint sweep
    // 2026-08-16, backend/bank-mfe docs Section 88) -- GiftService's own
    // POST /api/v1/gifts only resolves recipients by phone number, unlike sendDirect's
    // phone-or-account-number lookup, so gift mode is only offered here (not a
    // separate screen) and comes with an explicit "phone number only" note.
    @State private var isGift = false
    @State private var giftNote = ""
    @State private var giftTheme: String?
    let recipientAccountNumber: String
    let availableBalance: Double
    let isSubmitting: Bool
    let scamWarning: ScamWarningUi?
    let scamReported: Bool
    let onReportScam: () -> Void
    let onBack: () -> Void
    let onConfirm: (Int, Bool, String?, String?) -> Void
    // Real gap found live (2026-08-31, direct user reference of their own Toss app's
    // "which account should the money come from" picker): nil keeps the existing
    // generic "From Itunda Account" label (the sender's MAIN account, unchanged
    // default), set only when the caller opened this screen from a specific
    // non-default account (e.g. OverviewScreenView's new per-account Send button).
    let fromAccountName: String?

    public init(
        recipientAccountNumber: String,
        availableBalance: Double = 0,
        isSubmitting: Bool = false,
        scamWarning: ScamWarningUi? = nil,
        scamReported: Bool = false,
        onReportScam: @escaping () -> Void = {},
        onBack: @escaping () -> Void,
        onConfirm: @escaping (Int, Bool, String?, String?) -> Void,
        fromAccountName: String? = nil
    ) {
        self.recipientAccountNumber = recipientAccountNumber
        self.availableBalance = availableBalance
        self.isSubmitting = isSubmitting
        self.scamWarning = scamWarning
        self.scamReported = scamReported
        self.onReportScam = onReportScam
        self.onBack = onBack
        self.onConfirm = onConfirm
        self.fromAccountName = fromAccountName
    }

    private var amount: Int { Int(digits) ?? 0 }
    private var insufficientBalance: Bool { amount > 0 && Double(amount) > availableBalance }

    public var body: some View {
        VStack(spacing: 0) {
            FlowTopBar(onBack: onBack)

            VStack(alignment: .leading, spacing: 6) {
                TransferPartyRow(label: fromAccountName ?? pt("fromAccount"), sublabel: pt("availableBalance", transferFormatAmount(Int(availableBalance))), symbol: "creditcard")
                Rectangle().fill(IDS.Colors.divider).frame(width: 2, height: 20).padding(.leading, 21)
                TransferPartyRow(label: pt("toAccount", recipientAccountNumber), sublabel: pt("newRecipient"), symbol: "leaf")

                if let scamWarning, scamWarning.reportCount > 0 {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(pt("scamWarningTitle"))
                            .font(IDS.scaledFont(size: 13, weight: .bold, relativeTo: .footnote))
                            .foregroundColor(.red)
                        Text(pt("scamWarningBody", scamWarning.reportCount))
                            .font(IDS.scaledFont(size: 12, weight: .regular, relativeTo: .caption1))
                            .foregroundColor(.red)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(10)
                    .background(Color.red.opacity(0.08))
                    .cornerRadius(10)
                    .padding(.top, 12)
                }
                Button(action: onReportScam) {
                    Text(scamReported ? pt("scamReportedThanks") : pt("reportScam"))
                        .font(IDS.scaledFont(size: 12, weight: .regular, relativeTo: .caption1))
                        .foregroundColor(IDS.Colors.textTertiary)
                }
                .disabled(scamReported)
                .padding(.top, 8)

                Toggle(isOn: $isGift) {
                    HStack(spacing: 6) {
                        GiftThemeGlyph(theme: nil, size: 15)
                        Text("Send as a gift instead")
                    }
                    .font(IDS.scaledFont(size: 13, weight: .semibold, relativeTo: .footnote))
                }
                .padding(.top, 8)
                if isGift {
                    Text("Held until they claim it -- auto-refunded after 7 days if unclaimed. Only works if the recipient above is a phone number, not an account number.")
                        .font(IDS.scaledFont(size: 12, weight: .regular, relativeTo: .caption1))
                        .foregroundColor(IDS.Colors.textTertiary)
                    TextField("Add a note (optional)", text: $giftNote)
                        .font(IDS.scaledFont(size: 14, weight: .regular, relativeTo: .subheadline))
                        .padding(10)
                        .background(IDS.Colors.backgroundSecondary)
                        .cornerRadius(8)
                    Picker("Theme", selection: $giftTheme) {
                        Text("No theme (plain gift)").tag(String?.none)
                        ForEach(Array(giftThemeLabels.keys.sorted()), id: \.self) { key in
                            Text(giftThemeLabels[key] ?? key).tag(String?.some(key))
                        }
                    }
                    .pickerStyle(.menu)
                } else {
                    // Real gap found live (fresh Toss research, toss.tech/article/
                    // thinking-user-perspective's real "obvious to us, not to users"
                    // finding about an ambiguous memo field): P2pService.sendDirect
                    // already embeds this text into BOTH parties' own ledger-leg
                    // description ("Transfer - $description"), so the recipient
                    // genuinely sees whatever a sender types here -- this field had no
                    // UI on iOS at all until now, matching Android's identical port.
                    // Reuses giftNote's own state and the note param onConfirm already
                    // threads through -- the two modes are mutually exclusive.
                    TextField("Add a memo -- the recipient will see this", text: $giftNote)
                        .font(IDS.scaledFont(size: 14, weight: .regular, relativeTo: .subheadline))
                        .padding(10)
                        .background(IDS.Colors.backgroundSecondary)
                        .cornerRadius(8)
                        .padding(.top, 8)
                }
            }
            .padding(.horizontal, 24)

            Spacer().frame(height: 40)

            VStack(spacing: 16) {
                Text(pt("amountQuestion"))
                    .font(IDS.scaledFont(size: 16, weight: .regular, relativeTo: .callout))
                    .foregroundColor(IDS.Colors.textSecondary)
                Text(digits.isEmpty ? "0 RWF" : "\(transferFormatAmount(amount)) RWF")
                    .font(IDS.scaledFont(size: digits.isEmpty ? 32 : 42, weight: .bold, relativeTo: .largeTitle))
                    .foregroundColor(digits.isEmpty ? IDS.Colors.textTertiary : IDS.Colors.textPrimary)
                // Real gap found live (2026-08-10), applying Toss Tech's own "the best
                // error is one that never occurs" principle (toss.tech/article/21021):
                // this screen already knows the real balance (rendered above, and the
                // "Max" chip fills it in exactly), yet previously let a too-large amount
                // round-trip to the backend's 422 before saying anything. Same fix as
                // Android's TransferFlow.kt.
                if insufficientBalance {
                    Text(pt("amountInsufficient", transferFormatAmount(Int(availableBalance))))
                        .font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote))
                        .foregroundColor(.red)
                }
            }
            .frame(maxWidth: .infinity)
            .frame(maxHeight: .infinity)

            HStack(spacing: 10) {
                QuickAmountChip(label: "+10,000") { digits = String((Int(digits) ?? 0) + 10_000) }
                QuickAmountChip(label: "+100,000") { digits = String((Int(digits) ?? 0) + 100_000) }
                QuickAmountChip(label: pt("amountMax")) { digits = String(Int(availableBalance)) }
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 12)

            if isSubmitting {
                ProgressView()
                    .tint(IDS.Colors.brand)
                    .padding(.vertical, 24)
            } else {
                FlowNextBar(enabled: !digits.isEmpty && amount > 0 && !insufficientBalance, label: isGift ? "Send gift" : pt("send")) {
                    onConfirm(amount, isGift, giftNote.trimmingCharacters(in: .whitespaces).isEmpty ? nil : giftNote, giftTheme)
                }
                NumericKeypad(
                    onDigit: { d in if digits.count < 9 { digits += d } },
                    onDelete: { if !digits.isEmpty { digits.removeLast() } }
                )
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }
}

public enum SavingsAmountMode {
    case deposit
    case withdraw
    case claimInterest
}

/// Real savings deposit/withdraw/claim amount screens, matching the two real Toss
/// reference screenshots (user-provided, 2026-07-12, re-referenced 2026-08-31): "얼마나
/// 채울까요?" (deposit) and "얼마나 꺼낼까요?" (withdraw). Same visual shape as
/// TransferAmountScreen above, reusing its private FlowTopBar/TransferPartyRow-
/// equivalent pieces directly since they live in this same file.
///
/// Real gap found live (2026-08-31): a withdraw endpoint now exists
/// (SavingsController.kt's real POST /api/v1/savings/withdraw, closing
/// SavingsService.depositToGoal's own 2026-08-23 doc comment naming this exact gap)
/// -- .withdraw is no longer faked against a nonexistent endpoint, it's real. Same
/// fix on Android's SavingsAmountScreen.kt the same day.
public struct SavingsAmountScreen: View {
    @State private var digits = ""
    let goalName: String
    let mode: SavingsAmountMode
    let availableBalance: Double
    let isSubmitting: Bool
    let onBack: () -> Void
    let onConfirm: (Int) -> Void

    public init(
        goalName: String,
        mode: SavingsAmountMode,
        availableBalance: Double,
        isSubmitting: Bool,
        onBack: @escaping () -> Void,
        onConfirm: @escaping (Int) -> Void
    ) {
        self.goalName = goalName
        self.mode = mode
        self.availableBalance = availableBalance
        self.isSubmitting = isSubmitting
        self.onBack = onBack
        self.onConfirm = onConfirm
    }

    private var amount: Int { Int(digits) ?? 0 }
    // For withdraw, `availableBalance` is the GOAL's own current balance (the real cap
    // on this action), not the destination account's balance -- see this screen's own
    // call site in SavingsFlowContainer.swift.
    private var insufficientBalance: Bool { mode != .claimInterest && amount > 0 && Double(amount) > availableBalance }

    public var body: some View {
        VStack(spacing: 0) {
            FlowTopBar(onBack: onBack)

            VStack(alignment: .leading, spacing: 6) {
                if mode == .withdraw {
                    TransferPartyRow(label: "From \(goalName)", sublabel: "Available RWF \(transferFormatAmount(Int(availableBalance)))", symbol: "leaf")
                    Rectangle().fill(IDS.Colors.divider).frame(width: 2, height: 20).padding(.leading, 21)
                    TransferPartyRow(label: "To Itunda Account", sublabel: "", symbol: "creditcard")
                } else {
                    TransferPartyRow(label: "From Itunda Account", sublabel: "Available RWF \(transferFormatAmount(Int(availableBalance)))", symbol: "creditcard")
                    Rectangle().fill(IDS.Colors.divider).frame(width: 2, height: 20).padding(.leading, 21)
                    TransferPartyRow(label: "To \(goalName)", sublabel: mode == .deposit ? "Savings goal" : "Interest jar", symbol: "leaf")
                }
            }
            .padding(.horizontal, 24)

            Spacer().frame(height: 40)

            VStack(spacing: 16) {
                // Real fix (2026-08-11): interest now auto-credits to the account the
                // instant it accrues (see backend SavingsService.accrueInterest's own
                // doc comment, matching real Toss Bank passbook interest) -- this
                // screen no longer moves money, it just acknowledges what already
                // arrived. Same fix on Android's SavingsAmountScreen the same day.
                Text(mode == .deposit ? "How much to save?" : mode == .withdraw ? "How much to withdraw?" : "Interest already added to your balance")
                    .font(IDS.scaledFont(size: 16, weight: .regular, relativeTo: .callout))
                    .foregroundColor(IDS.Colors.textSecondary)
                Text(digits.isEmpty ? "0 RWF" : "\(transferFormatAmount(amount)) RWF")
                    .font(IDS.scaledFont(size: digits.isEmpty ? 32 : 42, weight: .bold, relativeTo: .largeTitle))
                    .foregroundColor(digits.isEmpty ? IDS.Colors.textTertiary : IDS.Colors.textPrimary)
                // Same "the best error is one that never occurs" fix (2026-08-10) as
                // TransferAmountScreen above -- a deposit/withdraw larger than the real
                // available balance (already known here) previously only surfaced
                // after a wasted round trip to the backend's 422.
                if insufficientBalance {
                    Text("Not enough balance -- you have RWF \(transferFormatAmount(Int(availableBalance)))")
                        .font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote))
                        .foregroundColor(.red)
                }
            }
            .frame(maxWidth: .infinity)
            .frame(maxHeight: .infinity)

            if mode == .deposit || mode == .withdraw {
                HStack(spacing: 10) {
                    QuickAmountChip(label: "+10,000") { digits = String(min((Int(digits) ?? 0) + 10_000, Int(availableBalance))) }
                    QuickAmountChip(label: "+100,000") { digits = String(min((Int(digits) ?? 0) + 100_000, Int(availableBalance))) }
                    QuickAmountChip(label: "Max") { digits = String(Int(availableBalance)) }
                }
                .padding(.horizontal, 24)
                .padding(.vertical, 12)
            }

            if isSubmitting {
                ProgressView()
                    .tint(IDS.Colors.brand)
                    .padding(.vertical, 24)
            } else if mode == .claimInterest {
                FlowNextBar(enabled: true, label: "OK") { onConfirm(0) }
            } else {
                FlowNextBar(enabled: !digits.isEmpty && amount > 0 && !insufficientBalance, label: mode == .withdraw ? "Withdraw" : "Deposit") { onConfirm(amount) }
                NumericKeypad(
                    onDigit: { d in if digits.count < 9 { digits += d } },
                    onDelete: { if !digits.isEmpty { digits.removeLast() } }
                )
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }
}
