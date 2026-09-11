import SwiftUI
import CoreNetwork
import CoreDesignSystem

/// Real Toss Bank 관리 (Manage) screen (2026-09-01, direct user-supplied Toss
/// screenshots of that exact screen) -- ports web's own already-built
/// AccountManageScreen.tsx (services/micro-frontends/bank-mfe/src/AccountManageScreen.tsx)
/// and Android's identical port (android/.../AccountManageScreen.kt) to iOS, which
/// had NOTHING here before this -- AccountLedgerDetailView's top bar was just a
/// back button, no Card/Manage buttons at all. Every row below routes to an
/// already-built, real itunda screen or feature -- never a fabricated destination
/// -- via plain closures, since this file lives in FeatureBanking and cannot
/// import App-module types (CardScreenView/TransferHubContainer/
/// ForeignCurrencyScreenView/SaronitePayBillsView/SettingsScreen/SupportScreenView
/// all live in the App target or peer feature modules); ContentView.swift is
/// where these closures actually get wired to real navigation.
///
/// Real new features added in the same re-audit (2026-09-12): "Account nickname" (a
/// real, user-editable label, see AccountService.setNickname's own doc comment on
/// the backend -- distinct from the fixed, system-assigned accountName) calls
/// CoreNetwork's NetworkClient.setAccountNickname directly (no App-module type
/// needed). "Change password" (PUT /api/v1/auth/pin already existed and already
/// covered exactly this case -- see AuthService.setPin's own doc comment -- but had
/// no Manage-screen entry point on any platform until now) needs
/// SessionManager.updateAccountPin, which IS App-only -- `onChangePassword` takes
/// its place as a plain injected callback returning an error message on failure or
/// nil on success, the exact same pattern PinUpgradeCard.swift's own `onUpdatePin`
/// already established for this same underlying call. Mirrors web's
/// AccountManageScreen.tsx AccountNicknameScreen/ChangePasswordScreen and Android's
/// identical port exactly.
///
/// Real, named, deliberately NOT built here (matching AccountManageScreen.tsx's own
/// disclosure) -- genuinely Korea-specific banking infrastructure/regulation (Open
/// Banking/firm banking, tax-free limits, telecom fraud-sharing, ATM limits,
/// Credit Information Usage Policy) or a real itunda gap sized like its own feature
/// (primary-account designation, self-service account closing) -- not silently
/// dropped, see the disclosure text at the bottom.
///
/// "Delayed transfers" (Toss's real 지연이체서비스 anti-phishing hold-and-cancel
/// feature) now deep-links to a real DelayedTransferListScreen.swift (2026-09-01,
/// App/Sources) against the real NetworkClient methods added the same day --
/// closing what was previously a disclosed, un-built gap here.
///
/// "Manage devices" deep-links to a real standalone DeviceListScreen.swift
/// (2026-09-01, App/Sources) -- same real GET/POST/DELETE /api/v1/auth/devices
/// endpoints SettingsScreen.swift's own inline device Section already used,
/// closing what was originally a disclosed gap here (routing to general Settings).
///
/// "View interest earned" routes to the existing Interest Jar BucketDetailScreen
/// (same real bucketDetailTarget = .interestJar ContentView.swift already uses
/// elsewhere). "Verification method" and "Transfer limit" (2026-09-01, direct
/// user-supplied Toss Bank Manage-screen screenshot) are new standalone screens
/// (VerificationMethodScreen.swift/TransferLimitScreen.swift, App/Sources) -- both
/// pure informational reads of data the backend already exposes, matching web/
/// Android's identical additions -- built there rather than here for the same
/// App-module-type reason as Devices/Delayed transfers above.
public struct AccountManageScreen: View {
    let accountId: String
    let accountNumber: String
    let nickname: String?
    let onNicknameChanged: (String?) -> Void
    let onChangePassword: (_ currentCredential: String, _ newPin: String) async -> String?
    let onBack: () -> Void
    let onOpenCard: () -> Void
    let onOpenDevices: () -> Void
    let onOpenInterestJar: () -> Void
    let onOpenVerificationMethod: () -> Void
    let onOpenAutoTransfer: () -> Void
    let onOpenDelayedTransfers: () -> Void
    let onOpenTransferLimit: () -> Void
    let onOpenForeignCurrency: () -> Void
    let onOpenBills: () -> Void
    let onOpenSupport: () -> Void

    @State private var showNickname = false
    @State private var showChangePassword = false

    public init(
        accountId: String,
        accountNumber: String,
        nickname: String?,
        onNicknameChanged: @escaping (String?) -> Void,
        onChangePassword: @escaping (_ currentCredential: String, _ newPin: String) async -> String?,
        onBack: @escaping () -> Void,
        onOpenCard: @escaping () -> Void,
        onOpenDevices: @escaping () -> Void,
        onOpenInterestJar: @escaping () -> Void,
        onOpenVerificationMethod: @escaping () -> Void,
        onOpenAutoTransfer: @escaping () -> Void,
        onOpenDelayedTransfers: @escaping () -> Void,
        onOpenTransferLimit: @escaping () -> Void,
        onOpenForeignCurrency: @escaping () -> Void,
        onOpenBills: @escaping () -> Void,
        onOpenSupport: @escaping () -> Void
    ) {
        self.accountId = accountId
        self.accountNumber = accountNumber
        self.nickname = nickname
        self.onNicknameChanged = onNicknameChanged
        self.onChangePassword = onChangePassword
        self.onBack = onBack
        self.onOpenCard = onOpenCard
        self.onOpenDevices = onOpenDevices
        self.onOpenInterestJar = onOpenInterestJar
        self.onOpenVerificationMethod = onOpenVerificationMethod
        self.onOpenAutoTransfer = onOpenAutoTransfer
        self.onOpenDelayedTransfers = onOpenDelayedTransfers
        self.onOpenTransferLimit = onOpenTransferLimit
        self.onOpenForeignCurrency = onOpenForeignCurrency
        self.onOpenBills = onOpenBills
        self.onOpenSupport = onOpenSupport
    }

    public var body: some View {
        if showNickname {
            AccountNicknameScreen(
                accountId: accountId,
                currentNickname: nickname,
                onBack: { showNickname = false },
                onSaved: { updated in onNicknameChanged(updated); showNickname = false }
            )
        } else if showChangePassword {
            ChangePasswordScreen(onBack: { showChangePassword = false }, onChangePassword: onChangePassword)
        } else {
            mainContent
        }
    }

    private var mainContent: some View {
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
                    Text("itunda Bank \(accountNumber.chunked(4).joined(separator: "-"))")
                        .font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote))
                        .foregroundColor(IDS.Colors.textTertiary)
                        .padding(.bottom, 24)

                    ManageSection(title: "Account", rows: [
                        ManageRowData(title: "Debit card", action: onOpenCard),
                        ManageRowData(title: "View interest earned", action: onOpenInterestJar),
                        ManageRowData(title: "Account nickname", action: { showNickname = true }),
                    ])
                    ManageSection(title: "Security", rows: [
                        ManageRowData(title: "Manage devices", action: onOpenDevices),
                        ManageRowData(title: "Verification method", action: onOpenVerificationMethod),
                        ManageRowData(title: "Change password", action: { showChangePassword = true }),
                    ])
                    ManageSection(title: "Transfer", rows: [
                        ManageRowData(title: "Auto transfer", action: onOpenAutoTransfer),
                        ManageRowData(title: "Delayed transfers", action: onOpenDelayedTransfers),
                        ManageRowData(title: "Transfer limit", action: onOpenTransferLimit),
                    ])
                    ManageSection(title: "Foreign currency", rows: [
                        ManageRowData(title: "Exchange rates", action: onOpenForeignCurrency),
                    ])
                    ManageSection(title: "Taxes & bills", rows: [
                        ManageRowData(title: "Pay taxes & bills", action: onOpenBills),
                    ])
                    ManageSection(title: "Support", rows: [
                        ManageRowData(title: "Get help", action: onOpenSupport),
                    ])

                    Text("Some Toss Bank account-management features (Open Banking, tax-free limits, ATM networks, telecom-fraud history sharing) don't have a real itunda equivalent yet, and aren't shown here rather than being faked.")
                        .font(IDS.scaledFont(size: 11, weight: .regular, relativeTo: .caption2))
                        .foregroundColor(IDS.Colors.textTertiary)
                        .padding(.top, 8)
                        .padding(.bottom, 24)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 24)
                .padding(.top, 4)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .navigationBarHidden(true)
    }
}

/// Real "Account nickname" screen (2026-09-12, "계좌 별명" -- direct user-supplied
/// Toss Bank Manage-screen screenshots) -- see AccountService.setNickname's own doc
/// comment on the backend. A blank submission clears it back to unset, matching the
/// backend's own convention. Mirrors web's AccountNicknameScreen/Android's identical
/// port exactly.
private struct AccountNicknameScreen: View {
    let accountId: String
    let currentNickname: String?
    let onBack: () -> Void
    let onSaved: (String?) -> Void

    @State private var input: String
    @State private var error: String?
    @State private var busy = false

    init(accountId: String, currentNickname: String?, onBack: @escaping () -> Void, onSaved: @escaping (String?) -> Void) {
        self.accountId = accountId
        self.currentNickname = currentNickname
        self.onBack = onBack
        self.onSaved = onSaved
        _input = State(initialValue: currentNickname ?? "")
    }

    private func save() {
        busy = true
        error = nil
        Task {
            do {
                let response = try await NetworkClient.shared.setAccountNickname(accountId: accountId, nickname: input.trimmingCharacters(in: .whitespaces))
                busy = false
                onSaved(response.account.nickname)
            } catch let NetworkError.httpErrorWithMessage(_, message) {
                busy = false
                error = message ?? "Could not update your account nickname."
            } catch {
                busy = false
                self.error = "Could not update your account nickname."
            }
        }
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
                    Text("Account nickname")
                        .font(IDS.scaledFont(size: 20, weight: .bold, relativeTo: .title2))
                        .foregroundColor(IDS.Colors.textPrimary)
                        .padding(.bottom, 20)

                    TextField("e.g. My savings", text: $input)
                        .padding(12)
                        .background(IDS.Colors.chipBackground)
                        .cornerRadius(10)
                        .onChange(of: input) { newValue in
                            if newValue.count > 50 { input = String(newValue.prefix(50)) }
                        }

                    if let error {
                        Text(error)
                            .font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote))
                            .foregroundColor(IDS.Colors.danger)
                            .padding(.top, 8)
                    }

                    Button(action: save) {
                        Text(busy ? "Saving…" : "Save")
                            .font(IDS.scaledFont(size: 15, weight: .semibold, relativeTo: .body))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(IDS.Colors.brand)
                            .cornerRadius(10)
                    }
                    .disabled(busy)
                    .padding(.top, 12)

                    Text("Leave this blank to remove your nickname -- your account will show as \"itunda Bank\" again.")
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
    }
}

/// Real "Change password" screen (2026-09-12, direct user-supplied Toss Bank
/// Manage-screen screenshots) -- PUT /api/v1/auth/pin already exists and already
/// covers exactly this case (re-proving the current credential to set a new one);
/// reuses CoreDesignSystem's AccountPinPad for the new-PIN steps, the exact same
/// current-credential/create/confirm shape PinUpgradeCard.swift already
/// establishes, minus its one-time-only `pinSet == false` upgrade gate -- this is a
/// general "change it again" flow reachable any time from here. `onChangePassword`
/// is threaded in from ContentView.swift (wired to SessionManager.updateAccountPin,
/// an App-only type) rather than called directly, matching PinUpgradeCard's own
/// `onUpdatePin` convention.
private struct ChangePasswordScreen: View {
    private enum Step { case credential, create, confirm, success }

    let onBack: () -> Void
    let onChangePassword: (_ currentCredential: String, _ newPin: String) async -> String?

    @State private var step: Step = .credential
    @State private var currentCredential = ""
    @State private var newPin: String?
    @State private var error: String?
    @State private var busy = false

    private func confirmPin(_ entered: String) {
        guard entered == newPin else {
            error = "That didn't match. Try again."
            step = .create
            return
        }
        busy = true
        error = nil
        Task {
            let failureMessage = await onChangePassword(currentCredential, entered)
            busy = false
            if let failureMessage {
                error = failureMessage
                step = .credential
            } else {
                step = .success
            }
        }
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

            VStack(alignment: .leading, spacing: 0) {
                if step != .success && step != .create && step != .confirm {
                    Text("Change password")
                        .font(IDS.scaledFont(size: 20, weight: .bold, relativeTo: .title2))
                        .foregroundColor(IDS.Colors.textPrimary)
                        .padding(.bottom, 20)
                }

                switch step {
                case .success:
                    Text("Your password has been updated.")
                        .font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote))
                        .foregroundColor(IDS.Colors.success)
                    Button(action: onBack) {
                        Text("Done")
                            .font(IDS.scaledFont(size: 15, weight: .semibold, relativeTo: .body))
                            .foregroundColor(IDS.Colors.brand)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(IDS.Colors.chipBackground)
                            .cornerRadius(10)
                    }
                    .padding(.top, 12)
                case .credential:
                    SecureField("Your current password", text: $currentCredential)
                        .padding(12)
                        .background(IDS.Colors.chipBackground)
                        .cornerRadius(10)
                    if let error {
                        Text(error)
                            .font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote))
                            .foregroundColor(IDS.Colors.danger)
                            .padding(.top, 8)
                    }
                    Button(action: { error = nil; step = .create }) {
                        Text("Continue")
                            .font(IDS.scaledFont(size: 15, weight: .semibold, relativeTo: .body))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(currentCredential.isEmpty ? IDS.Colors.textTertiary : IDS.Colors.brand)
                            .cornerRadius(10)
                    }
                    .disabled(currentCredential.isEmpty)
                    .padding(.top, 12)
                case .create:
                    AccountPinPad(
                        headline: "Create a new 6-digit password",
                        errorMessage: error,
                        onComplete: { entered in
                            error = nil
                            newPin = entered
                            step = .confirm
                        }
                    )
                case .confirm:
                    AccountPinPad(
                        headline: "Confirm your new password",
                        errorMessage: error,
                        busy: busy,
                        onComplete: { entered in confirmPin(entered) }
                    )
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 24)
            .padding(.top, 4)

            Spacer()
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .navigationBarHidden(true)
    }
}

private struct ManageRowData: Identifiable {
    let id = UUID()
    let title: String
    let action: () -> Void
}

private struct ManageSection: View {
    let title: String
    let rows: [ManageRowData]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title)
                .font(IDS.scaledFont(size: 15, weight: .bold, relativeTo: .subheadline))
                .foregroundColor(IDS.Colors.textPrimary)
                .padding(.top, 20)
                .padding(.bottom, 6)
            ForEach(rows) { row in
                Button(action: row.action) {
                    HStack {
                        Text(row.title)
                            .font(IDS.scaledFont(size: 14, weight: .regular, relativeTo: .callout))
                            .foregroundColor(IDS.Colors.textPrimary)
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.system(size: 13))
                            .foregroundColor(IDS.Colors.textTertiary)
                    }
                    .padding(.vertical, 12)
                }
            }
        }
    }
}
