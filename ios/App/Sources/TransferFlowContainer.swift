import SwiftUI
import FeaturePayments
import CoreDesignSystem
import CoreIdentity
import CoreNetwork

// Real gap found live (2026-08-31, direct user reference of their own Toss app's
// "which account should the money come from" picker) -- previously
// TransferAmountScreen always debited the sender's MAIN account
// (TransferFlowContainer.availableBalance below) with no way to choose another one,
// even though a real itunda user can hold more than one debit-capable Account row.
// Mirrors Android's TransferFromAccount the same day. nil `fromAccount` on
// TransferFlowContainer keeps every existing entry point (Home's "Send", TransferHub)
// behaving exactly as before; only OverviewScreenView's new per-account Send button
// sets it.
struct TransferFromAccount: Equatable {
    let id: String
    let name: String
    let balance: Double
}

private enum TransferStep: Equatable {
    case recipient
    case amount(accountNumber: String)
    // Real Toss reference (2026-08-11, toss.im/tossfeed/article/why-motion-in-finance,
    // ported to iOS 2026-08-23 alongside a real user-supplied "Sent" screenshot):
    // Toss's own headline example of motion in a financial product is exactly this
    // moment -- a real check animation after a transfer completes, intuitively
    // communicating completion. Before this, a successful transfer called onDone()
    // directly -- the flow silently closed with zero acknowledgment that real money
    // had actually moved, matching (and now closing) the exact gap Android's own
    // TransferStep.Success doc comment describes having already fixed 2026-08-11.
    // fraudWarnings added 2026-09-02 (Toss security research thread) -- Swift enum
    // cases can't carry default associated-value parameters, so this is required at
    // both real construction sites below rather than defaulted.
    case success(amountRwf: Int, recipientLabel: String, fraudWarnings: [String])
}

// Localized 2026-08-08 (docs/DESIGN_REFERENCES.md Section 19) -- the scam-report
// reason sheet and the biometric-failure fallback message below are owned by this
// file (not TransferFlowScreens.swift's RecipientEntryScreen/TransferAmountScreen),
// so they get their own tiny dict here rather than crossing the module boundary.
// Reuses AppLocale/loadStoredLocale from LoginScreen.swift (same App target).
private let transferContainerStrings: [AppLocale: [String: String]] = [
    .en: [
        "scamReasonPlaceholder": "Why are you reporting this number?",
        "scamReasonTitle": "Report as a scam",
        "cancel": "Cancel",
        "reporting": "Reporting…",
        "report": "Report",
        "biometricFailed": "Couldn't verify. Try again.",
    ],
    .rw: [
        "scamReasonPlaceholder": "Kuki utanga raporo kuri iyi numero?",
        "scamReasonTitle": "Tanga raporo ko ari uburiganya",
        "cancel": "Hagarika",
        "reporting": "Kohereza raporo…",
        "report": "Ohereza raporo",
        "biometricFailed": "Ntibyashobotse kwemeza. Ongera ugerageze.",
    ],
    // Real gap found 2026-08-15: this dict had zero French entries -- same class of
    // staleness as SettingsScreen.swift/DeviceStepUpView.swift's identical gap, found
    // the same day.
    .fr: [
        "scamReasonPlaceholder": "Pourquoi signalez-vous ce numéro ?",
        "scamReasonTitle": "Signaler comme arnaque",
        "cancel": "Annuler",
        "reporting": "Signalement en cours…",
        "report": "Signaler",
        "biometricFailed": "Impossible de vérifier. Réessayez.",
    ],
]

private func tc(_ key: String) -> String {
    let locale = loadStoredLocale()
    return transferContainerStrings[locale]?[key] ?? transferContainerStrings[.en]?[key] ?? key
}

/// Owns the real send-money flow's step state + network calls (2026-07-12) --
/// mirrors Android's ItundaAppScreen.kt transferStep handling exactly. Lives in the
/// App target because it needs TransferViewModel/NetworkClient, which
/// Features/Payments can't depend on (see BankView.swift's note on the same
/// constraint).
struct TransferFlowContainer: View {
    @StateObject private var viewModel = TransferViewModel()
    @State private var step: TransferStep = .recipient
    @State private var isSubmitting = false
    @State private var errorMessage: String?
    // Real device binding step-up (2026-07-21 port) -- see DeviceStepUpView's own doc
    // comment for the full account.
    @State private var showDeviceStepUp = false
    @State private var deviceStepUpBusy = false
    @State private var deviceStepUpError: String?
    // Remembers the in-flight amount across a device-not-verified -> verify -> retry
    // round trip -- `step` only carries the recipient's account number, not the
    // amount being sent.
    @State private var pendingAmountRwf = 0
    // Remembered across the same device-not-verified -> verify -> retry round trip as
    // pendingAmountRwf above -- see TransferAmountScreen's own doc comment for why
    // gift mode lives here rather than a separate screen.
    @State private var pendingIsGift = false
    @State private var pendingGiftNote: String?
    @State private var pendingGiftTheme: String?
    // Real saved-contacts list (found 2026-07-22 fully built on the backend with zero
    // client UI anywhere) -- fetched here rather than eagerly on app launch, since
    // it's only ever needed on this screen.
    @State private var contacts: [ContactUi] = []
    // Real Toss 사기계좌 조회-style pre-transfer warning (2026-07-31) -- see
    // rw.itunda.p2p.ScamReportService's own doc comment. Mirrors bank-mfe/Android's
    // own scamCheck/ReportScamLink exactly: a warning, not a hard block.
    @State private var scamReportCount: Int?
    @State private var scamReported = false
    @State private var showScamReportSheet = false
    @State private var scamReportReason = ""
    @State private var scamReportBusy = false
    // Real Toss/Kakao Bank-style recipient-name confirmation ("받는분 성함 확인",
    // 2026-08-23) -- see NetworkClient.resolveRecipient's own doc comment: this
    // endpoint already had real web/Android clients but iOS's transfer flow only ever
    // showed the raw account number, never a resolved name. Best-effort like the scam
    // check above it -- a failed lookup falls back to the account number on Success.
    @State private var recipientDisplayName: String?
    let availableBalance: Double
    var fromAccount: TransferFromAccount? = nil
    let onDone: () -> Void

    private func loadContacts() {
        Task {
            if let fetched = try? await NetworkClient.shared.getContacts().contacts {
                contacts = fetched.map { ContactUi(id: $0.id, name: $0.name, phoneNumber: $0.phoneNumber, bank: $0.bank) }
            }
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            switch step {
            case .recipient:
                RecipientEntryScreen(
                    onBack: onDone,
                    contacts: contacts,
                    onAddContact: { name, phoneNumber in
                        Task {
                            _ = try? await NetworkClient.shared.addContact(name: name, phoneNumber: phoneNumber)
                            loadContacts()
                        }
                    },
                    onNext: { accountNumber in
                        // Found live on-device (2026-07-12): the account-number
                        // field's real software keyboard was still first responder
                        // at this exact instant. Switching `step` here swaps this
                        // fullScreenCover's content to TransferAmountScreen (no
                        // focused field) *without* the keyboard ever being told to
                        // resign -- the presentation's window was left permanently
                        // shrunk/shifted to accommodate a keyboard that no view
                        // was still requesting, cutting off the top of the screen
                        // (including the back button, confirmed unreachable even by
                        // a raw coordinate tap). Resigning first responder before
                        // the step switch is the fix: the keyboard dismisses on its
                        // own terms instead of being abandoned mid-avoidance.
                        Self.dismissKeyboard()
                        step = .amount(accountNumber: accountNumber)
                    }
                )
            case .amount(let accountNumber):
                TransferAmountScreen(
                    recipientAccountNumber: accountNumber,
                    availableBalance: fromAccount?.balance ?? availableBalance,
                    isSubmitting: isSubmitting,
                    scamWarning: scamReportCount.map { ScamWarningUi(reportCount: $0) },
                    scamReported: scamReported,
                    onReportScam: { showScamReportSheet = true },
                    onBack: { step = .recipient },
                    onConfirm: { amountRwf, isGift, note, theme in confirm(accountNumber: accountNumber, amountRwf: amountRwf, isGift: isGift, note: note, theme: theme) },
                    fromAccountName: fromAccount?.name
                )
                .task(id: accountNumber) { await checkScamStatus(accountNumber) }
                .task(id: accountNumber) { await resolveRecipientName(accountNumber) }
                if let errorMessage {
                    Text(errorMessage)
                        .font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote))
                        .foregroundColor(.red)
                        .padding(.horizontal, 24)
                        .padding(.top, 8)
                }
            case .success(let amountRwf, let recipientLabel, let fraudWarnings):
                IdsCelebrationScreen(
                    headline: "\(amountRwf.formatted()) RWF sent",
                    message: "",
                    onDone: onDone,
                    recipientLabel: recipientLabel,
                    onShare: {
                        // Same real, already-proven UIActivityViewController pattern as
                        // ShopMerchantDetail.swift's own affiliate-link share -- walks
                        // to the topmost presentedViewController since this flow is
                        // itself already presented modally.
                        let text = "Sent RWF \(amountRwf.formatted()) to \(recipientLabel) via itunda"
                        let activityVC = UIActivityViewController(activityItems: [text], applicationActivities: nil)
                        if let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
                           let root = scene.windows.first?.rootViewController {
                            var top = root
                            while let presented = top.presentedViewController { top = presented }
                            top.present(activityVC, animated: true)
                        }
                    },
                    fraudWarnings: fraudWarnings
                )
            }
        }
        // Found live on-device (2026-07-12): typing into RecipientEntryScreen's
        // account-number field (real software/hardware keyboard focus) and then
        // advancing `step` to .amount within this *same* already-presented
        // fullScreenCover -- not a fresh presentation -- left the whole screen's
        // content, including the back button, rendered off-screen above the top
        // edge, confirmed unreachable even by a raw coordinate tap at its visual
        // location. An explicit full-bleed frame alone didn't fix it (still
        // reproduced after adding it) -- the actual cause is this VStack retaining a
        // keyboard-avoidance safe-area adjustment from the .recipient step's focused
        // TextField that never clears once `step` swaps to .amount, since it's the
        // same persistent container across both cases, not a fresh presentation.
        // Opting this container out of automatic keyboard-safe-area avoidance is the
        // real fix.
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .ignoresSafeArea(.keyboard)
        .overlay {
            if showDeviceStepUp {
                Color.black.opacity(0.3).ignoresSafeArea()
                DeviceStepUpView(
                    busy: deviceStepUpBusy,
                    error: deviceStepUpError,
                    onVerify: { password in verifyThenRetry(password: password) },
                    onCancel: { showDeviceStepUp = false; deviceStepUpError = nil }
                )
            }
        }
        .sheet(isPresented: $showScamReportSheet) {
            NavigationView {
                Form {
                    TextField(tc("scamReasonPlaceholder"), text: $scamReportReason)
                }
                .navigationTitle(tc("scamReasonTitle"))
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button(tc("cancel")) { showScamReportSheet = false }.disabled(scamReportBusy)
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button(scamReportBusy ? tc("reporting") : tc("report")) { submitScamReport() }
                            .disabled(scamReportBusy || scamReportReason.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                }
            }
        }
    }

    private func checkScamStatus(_ accountNumber: String) async {
        scamReported = false
        scamReportCount = nil
        guard let result = try? await NetworkClient.shared.checkScamStatus(identifier: accountNumber).result else { return }
        scamReportCount = result.warn ? result.reportCount : 0
    }

    private func resolveRecipientName(_ accountNumber: String) async {
        recipientDisplayName = nil
        guard let recipient = try? await NetworkClient.shared.resolveRecipient(identifier: accountNumber).recipient else { return }
        recipientDisplayName = recipient.displayName
    }

    private func submitScamReport() {
        guard case .amount(let accountNumber) = step else { return }
        scamReportBusy = true
        Task { @MainActor in
            _ = try? await NetworkClient.shared.reportScam(identifier: accountNumber, reason: scamReportReason.trimmingCharacters(in: .whitespaces))
            scamReportBusy = false
            showScamReportSheet = false
            scamReported = true
            scamReportReason = ""
        }
    }

    private func verifyThenRetry(password: String) {
        deviceStepUpError = nil
        deviceStepUpBusy = true
        Task { @MainActor in
            let result = await viewModel.verifyDevice(password: password)
            deviceStepUpBusy = false
            switch result {
            case .success:
                showDeviceStepUp = false
                if case .amount(let accountNumber) = step {
                    isSubmitting = true
                    let retryResult = pendingIsGift
                        ? await viewModel.sendGift(recipientPhoneNumber: accountNumber, amountRwf: pendingAmountRwf, note: pendingGiftNote, theme: pendingGiftTheme)
                        : await viewModel.sendTransfer(recipientAccountNumber: accountNumber, amountRwf: pendingAmountRwf, memo: pendingGiftNote ?? "", fromAccountId: fromAccount?.id)
                    isSubmitting = false
                    if case .success(_, let fraudWarnings) = retryResult { step = .success(amountRwf: pendingAmountRwf, recipientLabel: recipientDisplayName ?? accountNumber, fraudWarnings: fraudWarnings) }
                    else if case .failure(let message) = retryResult { errorMessage = message }
                }
            case .failure(let message):
                deviceStepUpError = message
            // sendTransfer/sendGift never actually return .goalDepositCompleted --
            // only TransferViewModel.depositToSavingsGoal does -- handled only
            // because MoneyActionResult is a shared enum, same reasoning as
            // .queued just below.
            case .queued, .deviceNotVerified, .goalDepositCompleted:
                break
            }
        }
    }

    private static func dismissKeyboard() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }

    // Toss-style biometric confirmation gate before a transfer completes, then the
    // real quote+confirm call -- mirrors Android's onConfirm handler in
    // ItundaAppScreen.kt exactly.
    private func confirm(accountNumber: String, amountRwf: Int, isGift: Bool = false, note: String? = nil, theme: String? = nil) {
        errorMessage = nil
        // Real double-tap/double-send gap found and fixed 2026-09-05: isSubmitting
        // used to flip to true only INSIDE authenticateForTransaction's success
        // callback, so the "Send" button (which this flag replaces with a
        // ProgressView -- see TransferFlowScreens.swift) stayed live and tappable
        // for the entire biometric-prompt window. Face ID's own system overlay
        // masks this on most devices, but Touch ID has no such overlay -- the
        // app's own UI stays fully interactive while the sensor waits for a
        // touch, so a second tap there could fire a second, independent
        // LAContext evaluation (authenticateForTransaction always creates a
        // fresh one) and, if both happened to succeed, a real double-send: each
        // call generates its OWN fresh Idempotency-Key, so the backend's
        // idempotency dedup never sees them as the same request. Setting this
        // synchronously here, before the biometric prompt even appears, closes
        // the window completely.
        isSubmitting = true
        pendingAmountRwf = amountRwf
        pendingIsGift = isGift
        pendingGiftNote = note
        pendingGiftTheme = theme
        NIDABiometricAuth.shared.authenticateForTransaction(reason: isGift ? "Confirm sending a \(amountRwf) RWF gift" : "Confirm sending \(amountRwf) RWF") { success, error in
            guard success else {
                isSubmitting = false
                errorMessage = error?.localizedDescription ?? tc("biometricFailed")
                return
            }
            Task { @MainActor in
                let result = isGift
                    ? await viewModel.sendGift(recipientPhoneNumber: accountNumber, amountRwf: amountRwf, note: note, theme: theme)
                    : await viewModel.sendTransfer(recipientAccountNumber: accountNumber, amountRwf: amountRwf, memo: note ?? "", fromAccountId: fromAccount?.id)
                isSubmitting = false
                switch result {
                case .success(_, let fraudWarnings):
                    step = .success(amountRwf: amountRwf, recipientLabel: recipientDisplayName ?? accountNumber, fraudWarnings: fraudWarnings)
                // sendTransfer never actually returns .queued -- a transfer confirm
                // is deliberately never queued offline (see
                // TransferViewModel.depositToSavingsGoal's doc comment for why) --
                // handled only because MoneyActionResult is a shared enum.
                case .queued:
                    onDone()
                case .failure(let message):
                    errorMessage = message
                case .deviceNotVerified:
                    deviceStepUpError = nil
                    showDeviceStepUp = true
                // sendTransfer/sendGift never actually return
                // .goalDepositCompleted -- only
                // TransferViewModel.depositToSavingsGoal does -- handled only
                // because MoneyActionResult is a shared enum, same reasoning as
                // .queued just above.
                case .goalDepositCompleted:
                    break
                }
            }
        }
    }
}
