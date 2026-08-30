//
//  BankView.swift
//  Ported from mobile_clients/ios (2026-07-10) into its correct Tuist module --
//  see ARCHITECTURE.md §3.
//
//  Build-verified since 2026-08-30 (real xcodebuild, both the standalone
//  FeatureBanking scheme and the full ItundaApp) -- the "NOT build-verified" note
//  here previously was an environment limitation of an earlier session's sandbox
//  (see Core/Risk/Sources/ZeroTrust.swift's own comment for the same class of
//  historical caveat), not a property of this file's own code.
//

import SwiftUI
import CoreDesignSystem
import CoreNetwork

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
// Widened to French (2026-08-15) -- real gap found: this is iOS's actual default
// Home tab (see this file's own 2026-08-09 doc comment above) and had zero French
// entries even after the App target's own AppLocale gained .fr the same session,
// because this is a structurally separate, self-contained enum (module-boundary
// necessity). Same class of staleness found and fixed the same day across
// SettingsScreen.swift/DeviceStepUpView.swift/TransferFlowContainer.swift/
// TransferFlowScreens.swift.
enum BankingLocale: String { case en, rw, fr }

// Real gap found 2026-08-30 (project_itunda_money_formatting_sweep's own standing
// convention -- comma thousands-separator for every whole-number RWF amount --
// never reached this file). Same per-file shape TransactionHistoryScreen.swift
// already established.
private func formatAmount(_ value: Int) -> String {
    let formatter = NumberFormatter()
    formatter.numberStyle = .decimal
    formatter.groupingSeparator = ","
    return formatter.string(from: NSNumber(value: value)) ?? "0"
}

private func loadBankingLocale() -> BankingLocale {
    if let raw = UserDefaults.standard.string(forKey: "itunda.locale"), let locale = BankingLocale(rawValue: raw) {
        return locale
    }
    let preferred = Locale.preferredLanguages.first ?? "en"
    if preferred.hasPrefix("rw") { return .rw }
    if preferred.hasPrefix("fr") { return .fr }
    return .en
}

private let bankingStrings: [BankingLocale: [String: String]] = [
    .en: [
        "goodMorning": "Good morning",
        "notifications": "Notifications",
        "profile": "Profile",
        "totalBalance": "Itunda total balance",
        "balanceSubtitle": "Account, bank and mobile money in one place",
        "mainAccount": "Main account",
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
        "momoSubtitle": "Daily spending account",
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
        // itunda account/Pay" research (KakaoPay/KakaoBank, Toss's own Payments/Bank)
        // this rebrand came out of. Same rows as before (SACCO/Ikimina/Moto/Harvest,
        // now also Loans/Invest -- see ContentView's own coopRows doc comment), just
        // given the real product identity they were missing.
        "coopRailTitle": "itunda Bank",
        "bankStatusDisclosure": "itunda is not a licensed bank, and this is not real government deposit insurance. \"itunda Bank\" is itunda's own product name for these savings, SACCO/Ikimina, loan, and investment features -- not a separate licensed banking entity.",
    ],
    .rw: [
        "goodMorning": "Mwaramutse",
        "notifications": "Amamenyesha",
        "profile": "Umwirondoro",
        "totalBalance": "Amafaranga yose ya itunda",
        "balanceSubtitle": "Account, banki na Mobile Money byose hamwe",
        "mainAccount": "Account nyamukuru",
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
        "momoSubtitle": "Account yo gukoresha buri munsi",
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
        "bankStatusDisclosure": "itunda si banki ifite uruhushya, kandi iki si ubwishingizi nyakuri bw'ubwizigame bwa Leta. \"itunda Bank\" ni izina ry'ibicuruzwa bya itunda ku bwizigame, SACCO/Ikimina, inguzanyo, no gushora imari -- ntabwo ari urwego rwihariye rufite uruhushya rwa banki.",
    ],
    .fr: [
        "goodMorning": "Bonjour",
        "notifications": "Notifications",
        "profile": "Profil",
        "totalBalance": "Solde total itunda",
        "balanceSubtitle": "Portefeuille, banque et mobile money au même endroit",
        "mainAccount": "Portefeuille principal",
        "spendToday": "Dépensé aujourd'hui",
        "sendMoneyNow": "Envoyer de l'argent",
        "quickTransfer": "Virement",
        "quickBills": "Factures",
        "quickMoMo": "MoMo",
        "quickSavings": "Épargne",
        "savingsTitle": "Épargne",
        "view": "Voir",
        "connectedMoneyTitle": "Comptes liés",
        "manage": "Gérer",
        "spentThisMonth": "Dépensé ce mois-ci",
        "viewTransactionHistory": "Voir l'historique des transactions",
        "rwandaServicesTitle": "Pour la vie au Rwanda",
        "more": "Plus",
        "rewardsTitle": "Récompenses et épargne",
        "discoverTitle": "Découvrir",
        "discoverNew": "NOUVEAU",
        "bkAccountTitle": "Compte BK Bank",
        "bkAccountSubtitle": "Salaire et règlement par carte",
        "momoSubtitle": "Portefeuille de dépenses quotidiennes",
        "airtelTitle": "Airtel Money",
        "airtelSubtitle": "Ligne de retrait de secours",
        "connected": "Connecté",
        "cashPowerTitle": "Payer CashPower",
        "cashPowerSubtitle": "Rechargez l'électricité instantanément",
        "open": "Ouvrir",
        "iremboTitle": "Services Irembo",
        "iremboSubtitle": "Paiements gouvernementaux et documents",
        "browse": "Parcourir",
        "mySpendingTitle": "Mes dépenses",
        "mySpendingSubtitle": "Voir les catégories et tendances mensuelles",
        "seeAll": "Tout voir",
        "rewardsRowTitle": "Récompenses itunda",
        "rewardsRowSubtitle": "Réclamez le cashback et les offres du jour",
        "goalSaverTitle": "Épargne objectif",
        "goalSaverSubtitle": "Progression du fonds d'urgence",
        "coopRailTitle": "itunda Bank",
        "bankStatusDisclosure": "itunda n'est pas une banque agréée, et ceci n'est pas une véritable assurance-dépôts gouvernementale. « itunda Bank » est le nom de produit d'itunda pour ces fonctionnalités d'épargne, de SACCO/Ikimina, de prêt et d'investissement -- ce n'est pas une entité bancaire agréée distincte.",
    ],
]

func bt(_ key: String, locale: BankingLocale) -> String {
    bankingStrings[locale]?[key] ?? bankingStrings[.en]?[key] ?? key
}

/// A row for BankView's real "Savings" section -- plain primitives, not the App
/// target's Account/SavingsGoal/InterestJar types, because Features/Banking (a Tuist
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
    // Real "+ New savings goal" row (2026-08-22, product-feel/Toss-parity work) needs
    // its own plus-icon/neutral tint, distinct from every real goal/interest-jar
    // row's shared leaf icon below -- nil for those (unchanged, falls back to the
    // existing hardcoded "leaf"/successTint mapping), set only for the add-new row.
    public let symbol: String?
    public let iconBackground: Color?

    public init(title: String, subtitle: String, trailing: String, symbol: String? = nil, iconBackground: Color? = nil, onTap: (() -> Void)? = nil) {
        self.title = title
        self.subtitle = subtitle
        self.trailing = trailing
        self.symbol = symbol
        self.iconBackground = iconBackground
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

/// Real Toss Bank reference (20 screenshots, 2026-08-21): backs
/// AccountLedgerDetailView, the real ledger drill-in reached by tapping
/// AccountSummaryCard's balance (not inline on BankView itself -- see that
/// struct's own doc comment for the direct user correction that moved it here).
/// Plain display model (not CoreNetwork's own TransactionDto) matching this
/// file's established SavingsRowData/DiscoverRowData/CooperativeRowData
/// convention -- the App target maps the real transactions before calling in,
/// same pattern TransactionHistoryScreen's own TransactionDisplayItem already
/// establishes.
public struct RecentTransactionRowData: Identifiable {
    public let id: String
    public let title: String
    public let subtitle: String
    public let amountText: String
    public let isOutgoing: Bool
    // Real fields added 2026-08-24 for TransactionDetailScreen (see
    // AccountLedgerDetailView's own doc comment) -- `title` above is already the
    // FULL, untouched original description (matching Android/bank-mfe: the list row
    // strips the redundant category prefix locally via ledgerRowTitle, the detail
    // screen shows this raw field), and `subtitle` is already the capitalized
    // status. Everything the detail screen needs beyond those two.
    public let type: String
    public let createdAt: String
    public let fee: Double
    public let currency: String
    public let afterBalance: Double

    public init(id: String, title: String, subtitle: String, amountText: String, isOutgoing: Bool, type: String, createdAt: String, fee: Double, currency: String, afterBalance: Double) {
        self.id = id
        self.title = title
        self.subtitle = subtitle
        self.amountText = amountText
        self.isOutgoing = isOutgoing
        self.type = type
        self.createdAt = createdAt
        self.fee = fee
        self.currency = currency
        self.afterBalance = afterBalance
    }
}

public struct BankView: View {
    @State private var locale: BankingLocale = loadBankingLocale()
    // Real Deposit Protection Fund status (2026-08-11) -- see backend's
    // DepositProtectionFund.kt doc comment. Self-fetched via .task below, same
    // "each screen fetches its own minimal real data" shape `locale` above already
    // establishes for this view.
    @State private var depositProtection: DepositProtectionStatus?
    // Real Toss Bank reference (20 screenshots, 2026-08-21) -- see
    // AccountSummaryCard's own doc comment: opens AccountLedgerDetailView, the
    // real ledger drill-in, rather than rendering it inline on this screen.
    @State private var showAccountDetail = false
    private let balanceText: String
    private let accountNumber: String?
    private let savingsRows: [SavingsRowData]
    private let discoverRows: [DiscoverRowData]
    private let coopRows: [CooperativeRowData]
    private let recentTransactions: [RecentTransactionRowData]
    private let onSend: () -> Void
    private let onOpenTransactionHistory: () -> Void
    private let onOpenNotifications: () -> Void
    private let onOpenProfile: () -> Void
    private let payBalanceText: String?
    private let onOpenPay: () -> Void

    /// Real data (2026-07-11) -- balanceText/savingsRows previously didn't exist;
    /// every number here was hardcoded ("RWF 1,284,350" etc). Defaults preserve the
    /// old numbers so this compiles standalone (the Example target/previews render
    /// something sensible without a real backend), but ContentView's real call site
    /// always passes real values from BankViewModel. onSend added 2026-07-12 -- "Send
    /// money now" was a decorative row with no action; it's the real entry point into
    /// the send-money flow now, matching Android's AccountHeroCard "Send" button.
    /// accountNumber added 2026-08-11 -- see AccountSummaryCard's own doc comment.
    public init(
        balanceText: String = "RWF 0",
        accountNumber: String? = nil,
        savingsRows: [SavingsRowData] = [],
        discoverRows: [DiscoverRowData] = [],
        coopRows: [CooperativeRowData] = [],
        recentTransactions: [RecentTransactionRowData] = [],
        onSend: @escaping () -> Void = {},
        onOpenTransactionHistory: @escaping () -> Void = {},
        onOpenNotifications: @escaping () -> Void = {},
        onOpenProfile: @escaping () -> Void = {},
        // Dual-balance UI (2026-08-29, closing [[project_itunda_bank_pay_separation]]'s
        // last open item, ported from bank-mfe's identical AccountSummaryRow.tsx fix).
        payBalanceText: String? = nil,
        onOpenPay: @escaping () -> Void = {}
    ) {
        self.balanceText = balanceText
        self.accountNumber = accountNumber
        self.savingsRows = savingsRows
        self.discoverRows = discoverRows
        self.coopRows = coopRows
        self.recentTransactions = recentTransactions
        self.onSend = onSend
        self.onOpenTransactionHistory = onOpenTransactionHistory
        self.onOpenNotifications = onOpenNotifications
        self.onOpenProfile = onOpenProfile
        self.payBalanceText = payBalanceText
        self.onOpenPay = onOpenPay
    }

    public var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: IDS.Layout.cardGap) {
                HomeTopBar(locale: locale, onOpenNotifications: onOpenNotifications, onOpenProfile: onOpenProfile)
                // Real Toss Bank reference (20 screenshots, 2026-08-21): the account
                // ledger was first built inline right here, flat, directly below the
                // balance card -- the user's own direct follow-up ("those below they
                // are not supposed to be in itunda account details screen ... they
                // suppose to be in itunda bank home screen like toss does") corrected
                // that on the identical Android build: real Toss keeps the ledger on
                // its own separate screen, reached by a tap on the balance, not folded
                // into this catalog/home screen. AccountSummaryCard is now that tap
                // target; AccountLedgerDetailView (below) is the real drill-in.
                AccountSummaryCard(balanceText: balanceText, accountNumber: accountNumber, onSend: onSend, onOpenDetail: { showAccountDetail = true }, locale: locale, payBalanceText: payBalanceText, onOpenPay: onOpenPay)
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
                            HomeRowData(title: $0.title, subtitle: $0.subtitle, trailing: $0.trailing, symbol: $0.symbol ?? "leaf", iconBackground: $0.iconBackground ?? IDS.Colors.successTint, onTap: $0.onTap)
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
                // Real Deposit Protection Fund card (2026-08-11) -- see
                // DepositProtectionFund.kt's own doc comment: rather than just
                // disclosing an absence of real banking protections, this shows the
                // real, working, ledger-backed reserve itunda maintains as its own
                // internal simulation of what real deposit protection could look
                // like -- same "real mechanics, honestly labeled as itunda's own
                // scheme" discipline this codebase already applies to VUP/RSE/SACCO.
                if let dp = depositProtection {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Deposit Protection Fund (simulation)").font(IDS.scaledFont(size: 14, weight: .bold, relativeTo: .subheadline))
                        HStack {
                            Text("Your covered balance").font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote)).foregroundColor(IDS.Colors.textSecondary)
                            Spacer()
                            Text("\(formatAmount(Int(dp.yourCoveredBalance))) RWF").font(IDS.scaledFont(size: 13, weight: .semibold, relativeTo: .footnote))
                        }
                        Text("Covered up to \(formatAmount(Int(dp.coverageCapPerUser))) RWF per user").font(IDS.scaledFont(size: 11, weight: .regular, relativeTo: .caption2)).foregroundColor(IDS.Colors.textTertiary)
                        Text("itunda's reserve: \(formatAmount(Int(dp.fundReserveBalance))) RWF").font(IDS.scaledFont(size: 11, weight: .regular, relativeTo: .caption2)).foregroundColor(IDS.Colors.textTertiary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                // Real licensed-bank disclosure (2026-08-11) -- see
                // docs/TOSS_PARITY_MATRIX.md's own confirmation of "zero real banking-
                // license implementation anywhere" and TOSS_FEATURE_SPECIFICATION.md's
                // Pillar 3 listing "itunda Bank... RBDB licensed in Rwanda" as roadmap-
                // only, never built. This screen's "itunda Bank" section title
                // (coopRailTitle above) has carried that name with no disclosure
                // anywhere clarifying itunda's actual (unlicensed) status. Same fix on
                // Android's BankHubScreen and web's Bank tab the same day.
                Text(bt("bankStatusDisclosure", locale: locale))
                    .font(IDS.scaledFont(size: 11, weight: .regular, relativeTo: .caption2))
                    .foregroundColor(IDS.Colors.textTertiary)
                    .padding(.horizontal, 4)
                    .padding(.top, 4)
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.top, IDS.Layout.screenTop)
            .padding(.bottom, IDS.Layout.sectionSpacing)
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task {
            depositProtection = try? await NetworkClient.shared.getDepositProtectionStatus().status
        }
        .fullScreenCover(isPresented: $showAccountDetail) {
            NavigationStack {
                AccountLedgerDetailView(
                    balanceText: balanceText,
                    accountNumber: accountNumber,
                    transactions: recentTransactions,
                    onBack: { showAccountDetail = false },
                    onSend: onSend
                )
            }
        }
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
            HomeRowData(title: bt("mySpendingTitle", locale: locale), subtitle: bt("mySpendingSubtitle", locale: locale), trailing: bt("seeAll", locale: locale), symbol: "account.pass", iconBackground: IDS.Colors.backgroundTertiary),
        ]
    }

    static func rewards(locale: BankingLocale) -> [HomeRowData] {
        [
            HomeRowData(title: bt("rewardsRowTitle", locale: locale), subtitle: bt("rewardsRowSubtitle", locale: locale), trailing: "140 RWF", symbol: "sparkles", iconBackground: IDS.Colors.successTint),
            HomeRowData(title: bt("goalSaverTitle", locale: locale), subtitle: bt("goalSaverSubtitle", locale: locale), trailing: "62%", symbol: "leaf", iconBackground: IDS.Colors.pressed),
        ]
    }
}


struct BankView_Previews: PreviewProvider {
    static var previews: some View {
        BankView()
    }
}

// Real account-number display grouping (2026-08-11) -- see AccountSummaryCard's own
// doc comment. Matches Android's `accountNumber.chunked(4)` and web's
// `.match(/.{1,4}/g)` -- same 4-digit grouping on all 3 platforms. Internal (not
// fileprivate) since 2026-08-21 -- AccountLedgerDetailView.swift, a separate file
// in this same module, needs it too for the identical account-number display.
extension String {
    func chunked(_ size: Int) -> [String] {
        guard size > 0 else { return [self] }
        var result: [String] = []
        var index = startIndex
        while index < endIndex {
            let end = self.index(index, offsetBy: size, limitedBy: endIndex) ?? endIndex
            result.append(String(self[index..<end]))
            index = end
        }
        return result
    }
}
