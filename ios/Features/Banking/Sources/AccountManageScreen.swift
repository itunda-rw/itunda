import SwiftUI
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
/// Real, named, deliberately NOT built here (matching AccountManageScreen.tsx's own
/// disclosure) -- genuinely Korea-specific banking infrastructure/regulation (Open
/// Banking/firm banking, tax-free limits, telecom fraud-sharing, ATM limits,
/// Credit Information Usage Policy) or a real itunda gap not rushed into a UI pass
/// (changing your account password, an account nickname, closing your account) --
/// not silently dropped, see the disclosure text at the bottom.
///
/// One further, iOS-specific disclosed gap vs. web/Android: "Delayed transfers"
/// (Toss's real 예약송금 anti-phishing hold-and-cancel feature) has a real backend
/// (services/backend/p2p/P2pDelayedTransferService.kt) and a real web client
/// (bank-mfe/src/lib/delayedTransfers.ts), but iOS has never built a screen or
/// NetworkClient method for it -- omitted from the Transfer section entirely
/// rather than faked. A real follow-up item, not scoped into this pass.
///
/// "Manage devices" honestly routes to the general Settings screen here (unlike
/// Android, which could deep-link straight to its own standalone DeviceListScreen)
/// -- iOS has no standalone device-list screen; devices are a `Section` inside
/// SettingsScreen.swift, matching Android's OWN pre-this-session behavior for the
/// whole gear icon.
public struct AccountManageScreen: View {
    let accountNumber: String
    let onBack: () -> Void
    let onOpenCard: () -> Void
    let onOpenDevices: () -> Void
    let onOpenAutoTransfer: () -> Void
    let onOpenForeignCurrency: () -> Void
    let onOpenBills: () -> Void
    let onOpenSupport: () -> Void

    public init(
        accountNumber: String,
        onBack: @escaping () -> Void,
        onOpenCard: @escaping () -> Void,
        onOpenDevices: @escaping () -> Void,
        onOpenAutoTransfer: @escaping () -> Void,
        onOpenForeignCurrency: @escaping () -> Void,
        onOpenBills: @escaping () -> Void,
        onOpenSupport: @escaping () -> Void
    ) {
        self.accountNumber = accountNumber
        self.onBack = onBack
        self.onOpenCard = onOpenCard
        self.onOpenDevices = onOpenDevices
        self.onOpenAutoTransfer = onOpenAutoTransfer
        self.onOpenForeignCurrency = onOpenForeignCurrency
        self.onOpenBills = onOpenBills
        self.onOpenSupport = onOpenSupport
    }

    public var body: some View {
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
                    ])
                    ManageSection(title: "Security", rows: [
                        ManageRowData(title: "Manage devices", action: onOpenDevices),
                    ])
                    ManageSection(title: "Transfer", rows: [
                        ManageRowData(title: "Auto transfer", action: onOpenAutoTransfer),
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

                    Text("Some Toss Bank account-management features (Open Banking, tax-free limits, ATM networks, changing your account password) don't have a real itunda equivalent yet, and aren't shown here rather than being faked.")
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
