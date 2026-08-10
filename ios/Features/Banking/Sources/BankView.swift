//
//  BankView.swift
//  Ported from mobile_clients/ios (2026-07-10) into its correct Tuist module --
//  see ARCHITECTURE.md §3.
//
//  NOT build-verified -- see Core/Risk/Sources/ZeroTrust.swift for why.
//

import SwiftUI
import CoreDesignSystem

// Real 2026-08-09 parity fix -- checked whether iOS had the same "translated the
// wrong screen" mistake just found and fixed on web (HomeView vs OverviewView) and
// Android (HomeTab vs OverviewScreen.kt): it did. ContentView.swift's real default
// tab (tag 0, "Home") renders THIS file, not OverviewScreenView -- the screen the
// earlier localization pass actually translated. BankView had zero locale
// infrastructure before this. Same self-contained-dictionary approach
// TransferFlowScreens.swift already established for Features/Payments (this module
// can't depend back on App either, so App's AppLocale isn't visible here), reading
// the same UserDefaults key ("itunda.locale") every other screen writes to. Same
// honesty note as every prior screen: careful, good-faith translation, not verified
// by a native Kinyarwanda speaker.
private enum BankingLocale: String { case en, rw }

private func loadBankingLocale() -> BankingLocale {
    if let raw = UserDefaults.standard.string(forKey: "itunda.locale"), let locale = BankingLocale(rawValue: raw) {
        return locale
    }
    let preferred = Locale.preferredLanguages.first ?? "en"
    return preferred.hasPrefix("rw") ? .rw : .en
}

private let bankingStrings: [BankingLocale: [String: String]] = [
    .en: [
        "goodMorning": "Good morning",
        "notifications": "Notifications",
        "profile": "Profile",
        "totalBalance": "Itunda total balance",
        "balanceSubtitle": "Wallet, bank and mobile money in one place",
        "mainWallet": "Main wallet",
        "spendToday": "Spend today",
        "sendMoneyNow": "Send money now",
        "quickTransfer": "Transfer",
        "quickBills": "Bills",
        "quickMoMo": "MoMo",
        "quickSavings": "Savings",
        "savingsTitle": "Savings",
        "view": "View",
        "connectedMoneyTitle": "Connected money",
        "manage": "Manage",
        "spentThisMonth": "Spent this month",
        "viewTransactionHistory": "View transaction history",
        "rwandaServicesTitle": "For life in Rwanda",
        "more": "More",
        "rewardsTitle": "Rewards and savings",
        "discoverTitle": "Discover",
        "discoverNew": "NEW",
        "bkAccountTitle": "BK Bank account",
        "bkAccountSubtitle": "Salary and card settlement",
        "momoSubtitle": "Daily spending wallet",
        "airtelTitle": "Airtel Money",
        "airtelSubtitle": "Backup cash-out line",
        "connected": "Connected",
        "cashPowerTitle": "Pay CashPower",
        "cashPowerSubtitle": "Top up electricity instantly",
        "open": "Open",
        "iremboTitle": "Irembo services",
        "iremboSubtitle": "Government and document payments",
        "browse": "Browse",
        "mySpendingTitle": "My spending",
        "mySpendingSubtitle": "View monthly categories and trends",
        "seeAll": "See all",
        "rewardsRowTitle": "Itunda rewards",
        "rewardsRowSubtitle": "Claim today's cashback and offers",
        "goalSaverTitle": "Goal saver",
        "goalSaverSubtitle": "Rainy day fund progress",
        // Real itunda Bank product identity (2026-08-11) -- see Android's identical
        // BankHubScreen/BankSummaryCard doc comment for the full "itunda Bank vs
        // itunda wallet/Pay" research (KakaoPay/KakaoBank, Toss's own Payments/Bank)
        // this rebrand came out of. Same rows as before (SACCO/Ikimina/Moto/Harvest,
        // now also Loans/Invest -- see ContentView's own coopRows doc comment), just
        // given the real product identity they were missing.
        "coopRailTitle": "itunda Bank",
    ],
    .rw: [
        "goodMorning": "Mwaramutse",
        "notifications": "Amamenyesha",
        "profile": "Umwirondoro",
        "totalBalance": "Amafaranga yose ya itunda",
        "balanceSubtitle": "Wallet, banki na Mobile Money byose hamwe",
        "mainWallet": "Wallet nyamukuru",
        "spendToday": "Wakoresheje uyu munsi",
        "sendMoneyNow": "Ohereza amafaranga",
        "quickTransfer": "Kohereza",
        "quickBills": "Kwishyura fagitire",
        "quickMoMo": "MoMo",
        "quickSavings": "Ubwizigame",
        "savingsTitle": "Ubwizigame",
        "view": "Reba",
        "connectedMoneyTitle": "Amafaranga ahujwe",
        "manage": "Gucunga",
        "spentThisMonth": "Wakoresheje muri uku kwezi",
        "viewTransactionHistory": "Reba amateka y'ibikorwa",
        "rwandaServicesTitle": "Ubuzima muri Rwanda",
        "more": "Ibindi",
        "rewardsTitle": "Ibihembo n'ubwizigame",
        "discoverTitle": "Menya",
        "discoverNew": "GISHYA",
        "bkAccountTitle": "Konti ya BK Bank",
        "bkAccountSubtitle": "Umushahara n'ubwishyu bwa karita",
        "momoSubtitle": "Wallet yo gukoresha buri munsi",
        "airtelTitle": "Airtel Money",
        "airtelSubtitle": "Umurongo w'inyongera wo kubikuza",
        "connected": "Byahujwe",
        "cashPowerTitle": "Kwishyura CashPower",
        "cashPowerSubtitle": "Ongera amashanyarazi ako kanya",
        "open": "Fungura",
        "iremboTitle": "Serivisi za Irembo",
        "iremboSubtitle": "Kwishyura Leta n'inyandiko",
        "browse": "Reba",
        "mySpendingTitle": "Amakoreshereze yanjye",
        "mySpendingSubtitle": "Reba ibyiciro n'imigendekere y'ukwezi",
        "seeAll": "Reba byose",
        "rewardsRowTitle": "Ibihembo bya itunda",
        "rewardsRowSubtitle": "Saba amafaranga n'ibindi byiza by'uyu munsi",
        "goalSaverTitle": "Umugambi w'ubwizigame",
        "goalSaverSubtitle": "Imigendekere y'ubwizigame bw'ibihe bikomeye",
        "coopRailTitle": "itunda Bank",
    ],
]

private func bt(_ key: String, locale: BankingLocale) -> String {
    bankingStrings[locale]?[key] ?? bankingStrings[.en]?[key] ?? key
}

/// A row for BankView's real "Savings" section -- plain primitives, not the App
/// target's Wallet/SavingsGoal/InterestJar types, because Features/Banking (a Tuist
/// Feature module) cannot depend back on App (App depends on Feature, never the
/// reverse) -- see Project.swift's featureModules list. The caller (ContentView, in
/// App, which does have access to NetworkClient's real types) is responsible for
/// formatting real data into this shape, the same way it already formats
/// TransferQuoteScreen's recipientName/amount/fee as plain strings.
public struct SavingsRowData: Identifiable {
    public let id = UUID()
    public let title: String
    public let subtitle: String
    public let trailing: String
    public let onTap: (() -> Void)?

    public init(title: String, subtitle: String, trailing: String, onTap: (() -> Void)? = nil) {
        self.title = title
        self.subtitle = subtitle
        self.trailing = trailing
        self.onTap = onTap
    }
}

/// A row for BankView's real "Built for how Rwanda saves" rail (2026-08-10) -- see the
/// "itunda: the wedge, not the mirror" strategy memo from this same session, and the
/// identical fix on bank-mfe's HomeView / Android's HomeTab. SACCO shares, Ikimina,
/// Moto-Taxi Ownership, and Harvest advance are itunda's only real Rwanda-specific
/// products -- the ones MTN MoMo's own roadmap can't trivially replicate -- yet all
/// four lived only inside EntireMenuScreen's "Financial services"/"Switch & save"
/// rows, same visual weight as "Foreign currency". Unlike SavingsRowData (which maps
/// every row to the same "leaf"/green treatment), this carries its own symbol/tint per
/// row -- these four need to read as visually distinct products, not one more
/// generic list.
public struct CooperativeRowData: Identifiable {
    public let id = UUID()
    public let title: String
    public let subtitle: String
    public let symbol: String
    public let tint: Color
    public let onTap: () -> Void

    public init(title: String, subtitle: String, symbol: String, tint: Color, onTap: @escaping () -> Void) {
        self.title = title
        self.subtitle = subtitle
        self.symbol = symbol
        self.tint = tint
        self.onTap = onTap
    }
}

/// A row for BankView's real "Discover" section -- plain primitives, same cross-module
/// data-passing pattern SavingsRowData already establishes. See
/// rw.itunda.discover.web.DiscoverController on the backend: a real curated promo rail
/// (a CMS-style catalog, not user-specific data), purely informational, no
/// click-through action or money movement. Android already has this (DiscoverSection
/// in ItundaAppScreen.kt, found real on backend + Android with zero client anywhere
/// else); this is the first iOS client.
public struct DiscoverRowData: Identifiable {
    public let id = UUID()
    public let title: String
    public let subtitle: String
    public let badge: String?
    public let isNew: Bool

    public init(title: String, subtitle: String, badge: String?, isNew: Bool) {
        self.title = title
        self.subtitle = subtitle
        self.badge = badge
        self.isNew = isNew
    }
}

public struct BankView: View {
    @State private var locale: BankingLocale = loadBankingLocale()
    private let balanceText: String
    private let savingsRows: [SavingsRowData]
    private let discoverRows: [DiscoverRowData]
    private let coopRows: [CooperativeRowData]
    private let onSend: () -> Void
    private let onOpenTransactionHistory: () -> Void

    /// Real data (2026-07-11) -- balanceText/savingsRows previously didn't exist;
    /// every number here was hardcoded ("RWF 1,284,350" etc). Defaults preserve the
    /// old numbers so this compiles standalone (the Example target/previews render
    /// something sensible without a real backend), but ContentView's real call site
    /// always passes real values from BankViewModel. onSend added 2026-07-12 -- "Send
    /// money now" was a decorative row with no action; it's the real entry point into
    /// the send-money flow now, matching Android's WalletHeroCard "Send" button.
    public init(
        balanceText: String = "RWF 0",
        savingsRows: [SavingsRowData] = [],
        discoverRows: [DiscoverRowData] = [],
        coopRows: [CooperativeRowData] = [],
        onSend: @escaping () -> Void = {},
        onOpenTransactionHistory: @escaping () -> Void = {}
    ) {
        self.balanceText = balanceText
        self.savingsRows = savingsRows
        self.discoverRows = discoverRows
        self.coopRows = coopRows
        self.onSend = onSend
        self.onOpenTransactionHistory = onOpenTransactionHistory
    }

    public var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: IDS.Layout.cardGap) {
                HomeTopBar(locale: locale)
                AccountSummaryCard(balanceText: balanceText, onSend: onSend, locale: locale)
                QuickActionsRow(locale: locale)
                if !coopRows.isEmpty {
                    HomeSectionCard(
                        title: bt("coopRailTitle", locale: locale),
                        actionLabel: "",
                        rows: coopRows.map {
                            HomeRowData(title: $0.title, subtitle: $0.subtitle, trailing: "", symbol: $0.symbol, iconBackground: $0.tint, onTap: $0.onTap)
                        }
                    )
                }
                if !savingsRows.isEmpty {
                    HomeSectionCard(
                        title: bt("savingsTitle", locale: locale),
                        actionLabel: bt("view", locale: locale),
                        rows: savingsRows.map {
                            HomeRowData(title: $0.title, subtitle: $0.subtitle, trailing: $0.trailing, symbol: "leaf", iconBackground: IDS.Colors.successTint, onTap: $0.onTap)
                        }
                    )
                }
                // "Spent this month" wired to real transaction history (2026-07-12) --
                // matching Android's "Spent in July" ShellRow real wiring; the other
                // rows in this section stay illustrative (no real spend-by-category
                // aggregation endpoint exists yet).
                HomeSectionCard(
                    title: bt("connectedMoneyTitle", locale: locale),
                    actionLabel: bt("manage", locale: locale),
                    rows: [HomeRowData(title: bt("spentThisMonth", locale: locale), subtitle: bt("viewTransactionHistory", locale: locale), trailing: "", symbol: "list.bullet", iconBackground: IDS.Colors.chipBackground, onTap: onOpenTransactionHistory)]
                        + BankViewData.connectedMoney(locale: locale)
                )
                HomeSectionCard(title: bt("rwandaServicesTitle", locale: locale), actionLabel: bt("more", locale: locale), rows: BankViewData.rwandaServices(locale: locale))
                HomeSectionCard(title: bt("rewardsTitle", locale: locale), actionLabel: bt("view", locale: locale), rows: BankViewData.rewards(locale: locale))
                if !discoverRows.isEmpty {
                    HomeSectionCard(
                        title: bt("discoverTitle", locale: locale),
                        actionLabel: "",
                        rows: discoverRows.enumerated().map { index, row in
                            HomeRowData(
                                title: row.isNew ? "\(row.title) · \(bt("discoverNew", locale: locale))" : row.title,
                                subtitle: row.subtitle,
                                trailing: row.badge ?? "",
                                symbol: "sparkles",
                                iconBackground: [IDS.Colors.successTint, IDS.Colors.pressed, IDS.Colors.warningTint, IDS.Colors.backgroundTertiary][index % 4]
                            )
                        }
                    )
                }
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.top, IDS.Layout.screenTop)
            .padding(.bottom, IDS.Layout.sectionSpacing)
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }
}

private enum BankViewData {
    // Functions taking `locale` rather than static `let` arrays (2026-08-09 localization
    // fix) -- a `static let` only ever evaluates once per process lifetime, which would
    // freeze these rows in whichever language was active the first time this enum was
    // touched, never picking up a later switch the way BankView's own @State locale can.
    static func connectedMoney(locale: BankingLocale) -> [HomeRowData] {
        [
            HomeRowData(title: bt("bkAccountTitle", locale: locale), subtitle: bt("bkAccountSubtitle", locale: locale), trailing: "RWF 842,000", symbol: "building.columns", iconBackground: IDS.Colors.backgroundTertiary),
            HomeRowData(title: "MTN MoMo", subtitle: bt("momoSubtitle", locale: locale), trailing: "RWF 118,400", symbol: "iphone", iconBackground: IDS.Colors.successTint),
            HomeRowData(title: bt("airtelTitle", locale: locale), subtitle: bt("airtelSubtitle", locale: locale), trailing: bt("connected", locale: locale), symbol: "creditcard", iconBackground: IDS.Colors.warningTint),
        ]
    }

    static func rwandaServices(locale: BankingLocale) -> [HomeRowData] {
        [
            HomeRowData(title: bt("cashPowerTitle", locale: locale), subtitle: bt("cashPowerSubtitle", locale: locale), trailing: bt("open", locale: locale), symbol: "doc.text", iconBackground: IDS.Colors.warningTint),
            HomeRowData(title: bt("iremboTitle", locale: locale), subtitle: bt("iremboSubtitle", locale: locale), trailing: bt("browse", locale: locale), symbol: "building.columns", iconBackground: IDS.Colors.pressed),
            HomeRowData(title: bt("mySpendingTitle", locale: locale), subtitle: bt("mySpendingSubtitle", locale: locale), trailing: bt("seeAll", locale: locale), symbol: "wallet.pass", iconBackground: IDS.Colors.backgroundTertiary),
        ]
    }

    static func rewards(locale: BankingLocale) -> [HomeRowData] {
        [
            HomeRowData(title: bt("rewardsRowTitle", locale: locale), subtitle: bt("rewardsRowSubtitle", locale: locale), trailing: "140 RWF", symbol: "sparkles", iconBackground: IDS.Colors.successTint),
            HomeRowData(title: bt("goalSaverTitle", locale: locale), subtitle: bt("goalSaverSubtitle", locale: locale), trailing: "62%", symbol: "leaf", iconBackground: IDS.Colors.pressed),
        ]
    }
}

private struct HomeRowData: Identifiable {
    let id = UUID()
    let title: String
    let subtitle: String
    let trailing: String
    let symbol: String
    let iconBackground: Color
    // Added 2026-07-12 for the real Savings section's rows (deposit/claim) --
    // default nil preserves every existing purely-promotional row unchanged.
    var onTap: (() -> Void)? = nil
}

private struct HomeTopBar: View {
    let locale: BankingLocale

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: IDS.Layout.tightGap) {
                Text(bt("goodMorning", locale: locale))
                    .font(IDS.Typography.bodyMedium)
                    .foregroundColor(IDS.Colors.textSecondary)
                Text("Itunda")
                    .font(IDS.Typography.header)
                    .foregroundColor(IDS.Colors.textPrimary)
            }

            Spacer()

            HStack(spacing: IDS.Layout.inlineGap) {
                TopBarActionButton(symbol: "bell", accessibilityLabel: bt("notifications", locale: locale))
                TopBarActionButton(symbol: "person", accessibilityLabel: bt("profile", locale: locale))
            }
        }
    }
}

// Icon-only buttons need an explicit label -- SwiftUI doesn't derive one from the SF
// Symbol name, so without this VoiceOver announced these as "Button" with no name
// (the same class of bug fixed in ItundaAppScreen.kt's TopIconButton on Android).
private struct TopBarActionButton: View {
    let symbol: String
    let accessibilityLabel: String

    var body: some View {
        Button(action: {}) {
            Image(systemName: symbol)
                .font(IDS.scaledFont(size: 18, weight: .medium, relativeTo: .body))
                .foregroundColor(IDS.Colors.iconPrimary)
                .frame(width: IDS.Layout.topBarActionSize, height: IDS.Layout.topBarActionSize)
                .background(IDS.Colors.backgroundSecondary)
                .clipShape(Circle())
        }
        .accessibilityLabel(accessibilityLabel)
    }
}

private struct AccountSummaryCard: View {
    let balanceText: String
    let onSend: () -> Void
    let locale: BankingLocale

    var body: some View {
        VStack(alignment: .leading, spacing: IDS.Layout.cardGap) {
            VStack(alignment: .leading, spacing: IDS.Layout.tightGap) {
                Text(bt("totalBalance", locale: locale))
                    .font(IDS.Typography.sectionLabel)
                    .foregroundColor(IDS.Colors.textSecondary)
                Text(balanceText)
                    .font(IDS.Typography.largeAmount)
                    .foregroundColor(IDS.Colors.textPrimary)
                Text(bt("balanceSubtitle", locale: locale))
                    .font(IDS.Typography.bodyMedium)
                    .foregroundColor(IDS.Colors.textSecondary)
            }

            HStack(spacing: IDS.Layout.inlineGap) {
                BalanceTile(title: bt("mainWallet", locale: locale), amount: balanceText)
                BalanceTile(title: bt("spendToday", locale: locale), amount: "RWF 18,200")
            }

            Button(action: onSend) {
                HStack(spacing: IDS.Layout.tightGap) {
                    Text(bt("sendMoneyNow", locale: locale))
                        .font(IDS.Typography.bodyBold)
                        .foregroundColor(IDS.Colors.textBrand)
                    Image(systemName: "chevron.right")
                        .foregroundColor(IDS.Colors.textBrand)
                        .font(IDS.scaledFont(size: 13, weight: .semibold, relativeTo: .footnote))
                }
                .padding(.horizontal, IDS.Layout.cardPadding)
                .padding(.vertical, IDS.Layout.inlineGap)
                .background(IDS.Colors.pressed)
                .clipShape(Capsule())
            }
            .buttonStyle(.plain)
        }
        .padding(IDS.Layout.cardPadding)
        .background(IDS.Colors.raisedCard)
        .cornerRadius(IDS.Layout.cardCornerRadius)
        .shadow(color: IDS.Colors.shadow, radius: 10, x: 0, y: 4)
    }
}

private struct BalanceTile: View {
    let title: String
    let amount: String

    var body: some View {
        VStack(alignment: .leading, spacing: IDS.Layout.tightGap) {
            Text(title)
                .font(IDS.Typography.caption)
                .foregroundColor(IDS.Colors.textTertiary)
            Text(amount)
                .font(IDS.Typography.metric)
                .foregroundColor(IDS.Colors.textPrimary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(IDS.Layout.inlineGap)
        .background(IDS.Colors.backgroundPrimary)
        .cornerRadius(IDS.Layout.sectionCornerRadius)
    }
}

private struct QuickActionsRow: View {
    let locale: BankingLocale

    private var actions: [(String, String, Color)] {
        [
            (bt("quickTransfer", locale: locale), "arrow.up.right", IDS.Colors.successTint),
            (bt("quickBills", locale: locale), "doc.text", IDS.Colors.warningTint),
            (bt("quickMoMo", locale: locale), "iphone", IDS.Colors.pressed),
            (bt("quickSavings", locale: locale), "leaf", IDS.Colors.dangerTint),
        ]
    }

    var body: some View {
        HStack(spacing: IDS.Layout.inlineGap) {
            ForEach(actions, id: \.1) { action in
                VStack(spacing: IDS.Layout.tightGap) {
                    Image(systemName: action.1)
                        .font(IDS.scaledFont(size: 20, weight: .medium, relativeTo: .body))
                        .foregroundColor(IDS.Colors.iconPrimary)
                        .frame(width: IDS.Layout.quickActionIconSize, height: IDS.Layout.quickActionIconSize)
                        .background(action.2)
                        .cornerRadius(IDS.Layout.iconCornerRadius)
                    Text(action.0)
                        .font(IDS.Typography.caption)
                        .foregroundColor(IDS.Colors.textSecondary)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, IDS.Layout.inlineGap)
                .background(IDS.Colors.backgroundSecondary)
                .cornerRadius(IDS.Layout.sectionCornerRadius)
            }
        }
    }
}

private struct HomeSectionCard: View {
    let title: String
    let actionLabel: String
    let rows: [HomeRowData]

    var body: some View {
        VStack(alignment: .leading, spacing: IDS.Layout.rowGap) {
            HStack {
                Text(title)
                    .font(IDS.Typography.title)
                    .foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Text(actionLabel)
                    .font(IDS.Typography.bodyMedium)
                    .foregroundColor(IDS.Colors.textTertiary)
            }

            VStack(spacing: 0) {
                ForEach(Array(rows.enumerated()), id: \.element.id) { index, row in
                    CompactListRow(row: row)
                    if index < rows.count - 1 {
                        Divider()
                            .overlay(IDS.Colors.divider)
                    }
                }
            }
        }
        .padding(IDS.Layout.cardPadding)
        .background(IDS.Colors.card)
        .cornerRadius(IDS.Layout.sectionCornerRadius)
        .shadow(color: IDS.Colors.shadow.opacity(0.7), radius: 8, x: 0, y: 3)
    }
}

private struct CompactListRow: View {
    let row: HomeRowData

    var body: some View {
        Group {
            if let onTap = row.onTap {
                Button(action: onTap) { rowContent }.buttonStyle(.plain)
            } else {
                rowContent
            }
        }
    }

    private var rowContent: some View {
        HStack(spacing: IDS.Layout.inlineGap) {
            Image(systemName: row.symbol)
                .font(IDS.scaledFont(size: 19, weight: .medium, relativeTo: .body))
                .foregroundColor(IDS.Colors.iconPrimary)
                .frame(width: IDS.Layout.rowIconSize, height: IDS.Layout.rowIconSize)
                .background(row.iconBackground)
                .cornerRadius(IDS.Layout.iconCornerRadius)

            VStack(alignment: .leading, spacing: IDS.Layout.tightGap) {
                Text(row.title)
                    .font(IDS.Typography.bodyBold)
                    .foregroundColor(IDS.Colors.textPrimary)
                Text(row.subtitle)
                    .font(IDS.Typography.caption)
                    .foregroundColor(IDS.Colors.textTertiary)
            }

            Spacer()

            HStack(spacing: IDS.Layout.tightGap) {
                Text(row.trailing)
                    .font(IDS.Typography.bodyMedium)
                    .foregroundColor(IDS.Colors.textSecondary)
                Image(systemName: "chevron.right")
                    .font(IDS.scaledFont(size: 12, weight: .semibold, relativeTo: .caption1))
                    .foregroundColor(IDS.Colors.textTertiary)
            }
        }
        .padding(.vertical, IDS.Layout.tightGap)
    }
}

struct BankView_Previews: PreviewProvider {
    static var previews: some View {
        BankView()
    }
}
