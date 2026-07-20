//
//  BenefitsShopAllScreens.swift
//  Rebuilt (2026-07-11) to close part of docs/TOSS_RWANDA_ALIGNMENT.md's "screen/
//  navigation taxonomy parity is still open" gap -- these three tabs (BenefitsScreen,
//  DiscoverScreen (the "Shop" tab), EntireMenuScreen (the "All" tab)) used to be
//  ContentView.swift's own crude, hardcoded-mock-data placeholders, unrelated to the
//  real design system. Content and structure are transcribed directly from
//  android/app/src/main/java/rw/itunda/app/ui/ItundaAppScreen.kt's BenefitsTab/
//  ShopTab/AllTab -- that file was built directly against real Toss reference
//  screenshots earlier this session (see ARCHITECTURE.md's own git-log-cited
//  history), so porting its already-verified structure here is not guessing at a
//  new design, it's applying an existing, sourced one to the platform that never
//  got it. Colors/type go through IDS/IdsPalette (this file's own new
//  CoreDesignSystem dependency, see Project.swift), the same tokens BankView.swift
//  already uses -- not a third, independent hardcoded palette.
//
//  Every row and label below is a straight transcription of Android's real content
//  (Icons.Outlined.X -> the closest real SF Symbol, not invented). MiniAppsSection's
//  three rows are transcribed as plain, non-functional list rows -- Android's
//  version launches real ReactActivity mini-app bundles, but no such runtime exists
//  on iOS yet (see ARCHITECTURE.md's backlog item 1: "Port the same brownfield
//  integration to iOS (nothing exists there yet)"), so wiring these to fake
//  behavior would be worse than leaving them inert with an honest comment.
//
//  Not build-verified -- see IDS.swift's header for why (no Xcode/Tuist in this
//  environment). Passes `swift -frontend -parse` only.
//

import SwiftUI
import UIKit
import CoreDesignSystem

// MARK: - Benefits tab

struct BenefitsScreen: View {
    var body: some View {
        ScrollView {
            VStack(spacing: IDS.Layout.cardGap) {
                IdsPlainTopBar(title: "Benefits")
                PromoBannerCard()
                BenefitsVisitCard()
                CashbackChanceCard()
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.top, IDS.Layout.screenTop)
            .padding(.bottom, IDS.Layout.sectionSpacing)
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }
}

private struct PromoBannerCard: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("🎁 Limited gift for Rwanda")
                .font(IDS.scaledFont(size: 18, weight: .bold, relativeTo: .body))
                .foregroundColor(.white)
            Text("25,000")
                .font(IDS.scaledFont(size: 54, weight: .heavy, relativeTo: .largeTitle))
                .foregroundColor(.white)
            Spacer().frame(height: 2)
            Text("Redeem for free")
                .font(IDS.scaledFont(size: 16, weight: .bold, relativeTo: .body))
                .foregroundColor(.white)
                .padding(.horizontal, 26)
                .padding(.vertical, 12)
                .background(Color(hex: 0xEF56FF))
                .clipShape(Capsule())
        }
        .padding(20)
        .frame(maxWidth: .infinity, minHeight: 220, alignment: .topLeading)
        .background(Color(hex: 0x5D2FE6))
        .cornerRadius(IDS.Layout.cardCornerRadius)
    }
}

private struct BenefitsVisitCard: View {
    private let rows: [(String, String)] = [
        ("Happy lottery", "die.face.5.fill"),
        ("Push the button", "hand.tap.fill"),
        ("Try on", "tshirt.fill"),
        ("Bring friends", "person.badge.plus"),
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("Visit 3 of 4 services and earn points")
                .font(IDS.scaledFont(size: 28, weight: .bold, relativeTo: .title1))
                .foregroundColor(IDS.Colors.textPrimary)
            ForEach(rows, id: \.0) { title, symbol in
                HStack {
                    ZStack {
                        RoundedRectangle(cornerRadius: 12).fill(IDS.Colors.chipBackground)
                        Image(systemName: symbol)
                            .font(IDS.scaledFont(size: 18, weight: .medium, relativeTo: .body))
                            .foregroundColor(IDS.Colors.textPrimary)
                    }
                    .frame(width: 38, height: 38)
                    Text(title)
                        .font(IDS.scaledFont(size: 18, weight: .semibold, relativeTo: .body))
                        .foregroundColor(IDS.Colors.textPrimary)
                    Spacer()
                    SmallBlueButton(label: "Visit")
                }
            }
        }
        .padding(24)
        .background(IDS.Colors.card)
        .cornerRadius(IDS.Layout.cardCornerRadius)
    }
}

private struct CashbackChanceCard: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("🍀 3 chances to get money back")
                .font(IDS.scaledFont(size: 28, weight: .bold, relativeTo: .title1))
                .foregroundColor(IDS.Colors.textPrimary)
            Text("We will notify you when new chances are available")
                .font(IDS.scaledFont(size: 15, weight: .regular, relativeTo: .subheadline))
                .foregroundColor(IDS.Colors.textSecondary)
            HStack {
                ZStack {
                    RoundedRectangle(cornerRadius: 14).fill(Color(hex: 0x246BFF))
                    Image(systemName: "arrow.left.arrow.right.circle.fill")
                        .font(IDS.scaledFont(size: 20, weight: .medium, relativeTo: .body))
                        .foregroundColor(.white)
                }
                .frame(width: 42, height: 42)
                VStack(alignment: .leading, spacing: 0) {
                    Text("RWF 5,000")
                        .font(IDS.scaledFont(size: 24, weight: .bold, relativeTo: .title2))
                        .foregroundColor(IDS.Colors.textPrimary)
                    Text("BK account -> TUYIZERE Eric")
                        .font(IDS.scaledFont(size: 15, weight: .regular, relativeTo: .subheadline))
                        .foregroundColor(IDS.Colors.textSecondary)
                }
                Spacer()
                SmallBlueButton(label: "Get back")
            }
        }
        .padding(24)
        .background(IDS.Colors.card)
        .cornerRadius(IDS.Layout.cardCornerRadius)
    }
}

// DiscoverScreen (the old "Shop" tab -- Toss-Shopping-cashback-style browsing) removed
// 2026-07-18: the new Shop tab is real Coupang-style commerce (see ShopScreen.swift),
// replacing this entirely, same cleanup Android's ItundaAppScreen.kt went through when
// its own old ShopTab (and ShopTopBar/CategoryTabsRow/ShopPromoCard/PointActionsCard)
// were deleted for the same reason.

// MARK: - All tab

struct EntireMenuScreen: View {
    var onOpenSettings: () -> Void = {}

    // Real granite mini-app launch, closing this file's own "MiniAppsSection... plain,
    // non-functional list rows" gap for real -- the CocoaPods/Tuist bridge plus the real
    // Fabric root-cause fix (see docs/ARCHITECTURE.md's mini-app host row, 2026-07-16→17)
    // makes a real RN root view presentable here. `pay-bills` proved the host out first,
    // independently verified (`SaroniteMiniAppLiveTest`); `wallet-balance`/`reward-tasks`/
    // `insurance` followed the same day once the host itself was proven real -- same
    // one-first-then-the-rest rollout Android itself did (2026-07-13→14).
    @State private var showPayBillsMiniApp = false
    @State private var showWalletBalanceMiniApp = false
    @State private var showRewardTasksMiniApp = false
    @State private var showInsuranceMiniApp = false
    // Benefits/Pay folded in here (2026-07-18) -- both lost their own top-level tab
    // when the bottom nav became Home/Shop/Hood/Talk/My, but stay just as reachable
    // as a real row instead of being dropped, same fix Android's own AllTab went
    // through (see ItundaAppScreen.kt's "Quick links" FlatSection).
    @State private var showBenefits = false
    @State private var showPay = false
    // Real self-hosted Rwanda map (2026-07-19) -- same Quick-links pattern as Pay/
    // Benefits above, since the 5-tab bottom nav has no free slot for a Map tab.
    @State private var showMap = false
    // Real Invest/Stocks screen (2026-07-20) -- same Quick-links pattern as Pay/
    // Benefits/Map above; this feature never had ANY mobile UI before now, not even
    // the original buy/sell/portfolio.
    @State private var showInvest = false

    var body: some View {
        ScrollView {
            VStack(spacing: IDS.Layout.sectionSpacing) {
                // Split across two Group blocks (2026-07-11, found via a real
                // xcodebuild against Xcode 14.3.1/Swift 5.8.1 -- see this file's
                // own header for how that toolchain was located): that Swift
                // version's ViewBuilder only supports up to 10 children per
                // container (the parameter-pack-based unlimited-children ViewBuilder
                // arrived in Swift 5.9), and this VStack has 15. Group doesn't
                // change layout at all -- it's purely a ViewBuilder child-count
                // workaround, each Group still contributes its children directly
                // to the VStack's layout.
                Group {
                    IdsAllTopBar(onOpenSettings: onOpenSettings)
                    FlatSection(title: "Quick links", rows: [
                        FlatRow(title: "Pay", subtitle: "Scan or pay by code", symbol: "qrcode", tint: .accentBlue, action: { showPay = true }),
                        FlatRow(title: "Benefits", subtitle: "Points, coupons, rewards", symbol: "gift.fill", tint: .accentOrange, action: { showBenefits = true }),
                        FlatRow(title: "Invest", subtitle: "RSE stocks, real portfolio", symbol: "chart.line.uptrend.xyaxis", tint: .accentPurple, action: { showInvest = true }),
                        FlatRow(title: "Map", subtitle: "Real Rwanda map, self-hosted", symbol: "map.fill", tint: .accentTeal, action: { showMap = true }),
                    ])
                    IdsSearchBar(placeholder: "Search")
                    IconGridSection(title: "Quick access", items: [
                        ("Mini", "square.grid.2x2.fill"),
                        ("Games", "gamecontroller.fill"),
                        ("Bank", "building.columns.fill"),
                        ("Pick", "star.fill"),
                    ])
                    // All four now open real granite mini-apps (see this file's header) --
                    // matching Android's own real four-mini-app parity.
                    FlatSection(title: "Mini apps", rows: [
                        FlatRow(title: "Wallet balance", showChevron: true, action: { showWalletBalanceMiniApp = true }),
                        FlatRow(title: "Pay bills", showChevron: true, action: { showPayBillsMiniApp = true }),
                        FlatRow(title: "Reward tasks", showChevron: true, action: { showRewardTasksMiniApp = true }),
                        FlatRow(title: "Insurance", showChevron: true, action: { showInsuranceMiniApp = true }),
                    ])
                    IconGridSection(title: "Recent services", items: [
                        ("Open acct", "plus.circle"),
                        ("Photo transfer", "camera.fill"),
                        ("Verify", "checkmark.seal.fill"),
                        ("Send", "paperplane.fill"),
                        ("Group", "person.2.fill"),
                        ("Property", "house.fill"),
                        ("Insurance", "shield.fill"),
                        ("More", "ellipsis"),
                    ])
                    FlatSection(title: "Financial services", rows: [
                        FlatRow(title: "Open account", subtitle: "Itunda Wallet, other banks, RSE brokerage", symbol: "plus.circle", tint: .accentBlue),
                        FlatRow(title: "My assets", subtitle: "Accounts, loans, RSE holdings, cards, points", symbol: "chart.pie.fill", tint: .accentPurple),
                        FlatRow(title: "Get a loan", subtitle: "Personal, salary-backed, SME working capital", symbol: "wallet.pass.fill", tint: .accentBlue),
                        FlatRow(title: "Mobile plan", subtitle: "MTN, Airtel, broadband", symbol: "globe", tint: .accentTeal),
                    ])
                    // Everything below is modeled directly on the real Toss Bank
                    // reference screens (see ItundaAppScreen.kt's own identical note),
                    // adapted to Rwanda rails (REG/WASAC/Irembo/RRA, MTN MoMo/Airtel
                    // Money, RSE tickers, RSSB pension) the same way Android already was.
                    FlatSection(title: "Switch & save", rows: [
                        FlatRow(title: "Switch your personal loan", trailing: "12% ~ 24%", trailingIsLink: true, symbol: "wallet.pass.fill", tint: .accentBlue),
                        FlatRow(title: "Switch your rent deposit loan", trailing: "9% ~ 15%", trailingIsLink: true, symbol: "house.fill", tint: .accentTeal),
                        FlatRow(title: "Switch your SME loan", trailing: "11% ~ 22%", trailingIsLink: true, symbol: "storefront.fill", tint: .accentTeal),
                    ])
                }
                Group {
                    FlatSection(title: "Cards", rows: [
                        FlatRow(title: "Itunda Card", trailing: "5% back on bills", trailingIsLink: true, symbol: "creditcard.fill", tint: .accentRed),
                        FlatRow(title: "Virtual card", trailing: "Instant issue", symbol: "creditcard.fill", tint: .accentGray),
                    ])
                    FlatSection(title: "Services", rows: [
                        FlatRow(title: "Rent deposit protection", symbol: "house.fill", tint: .accentBlue),
                        FlatRow(title: "Recurring payments", symbol: "doc.text.fill", tint: .accentBlue),
                        FlatRow(title: "Import recurring payments", symbol: "shippingbox.fill", tint: .accentGray),
                        FlatRow(title: "REG & WASAC bills", symbol: "bolt.fill", tint: .accentBlue),
                        FlatRow(title: "Claim interest now", symbol: "bolt.fill", tint: .accentPurple),
                        FlatRow(title: "SME income tax estimate", symbol: "banknote.fill", tint: .accentOrange),
                        FlatRow(title: "Split a bill with friends", symbol: "person.3.fill", tint: .accentBlue),
                        FlatRow(title: "Shared calendar", symbol: "calendar", tint: .accentBlue),
                        FlatRow(title: "Kids' allowance tasks", symbol: "checkmark.circle.fill", tint: .accentOrange),
                    ])
                    FlatSection(title: "Foreign currency", rows: [
                        FlatRow(title: "Foreign currency wallet", trailing: "100% rate preference", trailingIsLink: true, symbol: "wallet.pass.fill", tint: .accentPurple),
                        FlatRow(title: "International transfer", symbol: "dollarsign.circle.fill", tint: .accentBlue),
                    ])
                    FlatSection(title: "Grow your money", rows: [
                        FlatRow(title: "RSE stocks", subtitle: "BOK, MTNR, BLR, IMR, CMR, EQTY", symbol: "chart.line.uptrend.xyaxis", tint: .accentTeal),
                        FlatRow(title: "Bonds & fixed income", trailing: "7.5% ~ 12%", trailingIsLink: true, symbol: "building.columns.fill", tint: .accentBlue),
                        FlatRow(title: "IPO schedule", symbol: "chart.line.uptrend.xyaxis", tint: .accentRed),
                        FlatRow(title: "Brokerage account", trailing: "Up to 30,000 RWF", trailingIsLink: true, symbol: "building.columns.fill", tint: .accentTeal),
                    ])
                    FlatSection(title: "Pension", rows: [
                        FlatRow(title: "Check my RSSB pension", symbol: "building.columns.fill", tint: .accentBlue),
                        FlatRow(title: "Pension products", symbol: "percent", tint: .accentBlue),
                    ])
                    FlatSection(title: "Loans", rows: [
                        FlatRow(title: "Check my max limit", symbol: "chart.line.uptrend.xyaxis", tint: .accentPurple),
                        FlatRow(title: "Personal loan", trailing: "11% ~ 24%", trailingIsLink: true, symbol: "wallet.pass.fill", tint: .accentBlue),
                    ])
                    FlatSection(title: "Notifications & consent", rows: [
                        FlatRow(title: "Notifications", showChevron: true),
                        FlatRow(title: "Credit data usage policy", showChevron: true),
                        FlatRow(title: "Privacy policy", showChevron: true),
                        FlatRow(title: "Terms & consent", showChevron: true),
                    ])
                    FlatSection(title: "Support", rows: [
                        FlatRow(title: "FAQ", showChevron: true),
                        FlatRow(title: "Live chat", showChevron: true),
                        FlatRow(title: "Call support", showChevron: true),
                        FlatRow(title: "Report fraud", showChevron: true),
                        FlatRow(title: "Announcements", showChevron: true),
                    ])
                }
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.top, IDS.Layout.screenTop)
            .padding(.bottom, IDS.Layout.sectionSpacing)
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .sheet(isPresented: $showPayBillsMiniApp) {
            SaronitePayBillsView()
        }
        .sheet(isPresented: $showWalletBalanceMiniApp) {
            SaroniteWalletBalanceView()
        }
        .sheet(isPresented: $showRewardTasksMiniApp) {
            SaroniteRewardTasksView()
        }
        .sheet(isPresented: $showInsuranceMiniApp) {
            SaroniteInsuranceView()
        }
        .sheet(isPresented: $showBenefits) {
            BenefitsScreen()
        }
        .sheet(isPresented: $showPay) {
            PayScreen()
        }
        .sheet(isPresented: $showMap) {
            MapScreenView()
        }
        .sheet(isPresented: $showInvest) {
            InvestScreenView(onBack: { showInvest = false })
        }
    }
}

private struct IdsAllTopBar: View {
    var onOpenSettings: () -> Void = {}

    var body: some View {
        HStack {
            Text("TUYIZERE ERIC")
                .font(IDS.scaledFont(size: 26, weight: .bold, relativeTo: .largeTitle))
                .foregroundColor(IDS.Colors.textPrimary)
            Spacer()
            // Real Settings screen (2026-07-12, see SettingsScreen.swift) --
            // previously wired directly to logout with no screen behind it at all,
            // same fix as Android's AllTopBar.
            Button(action: onOpenSettings) {
                Image(systemName: "gearshape")
                    .font(IDS.scaledFont(size: 20, weight: .regular, relativeTo: .body))
                    .foregroundColor(IDS.Colors.textPrimary)
            }
            // Found live via FocusOrderTests (2026-07-12): this button's action and
            // icon were changed from direct-logout to opening the real Settings
            // screen, but the accessibility label was never updated to match -- a
            // VoiceOver user would have been told "Log out" for a button that
            // actually opens Settings.
            .accessibilityLabel("Settings")
        }
    }
}

// MARK: - Shared components (mirror Android's IdsPlainTopBar/SearchBar/FlatSection/
// FlatRow/IconGridSection/SmallBlueButton in ItundaAppScreen.kt)

struct IdsPlainTopBar: View {
    let title: String
    var body: some View {
        HStack {
            Text(title)
                .font(IDS.scaledFont(size: 28, weight: .bold, relativeTo: .largeTitle))
                .foregroundColor(IDS.Colors.textPrimary)
            Spacer()
            Text("...")
                .font(IDS.scaledFont(size: 24, weight: .regular, relativeTo: .title1))
                .foregroundColor(IDS.Colors.textPrimary)
        }
    }
}

struct IdsSearchBar: View {
    let placeholder: String
    var body: some View {
        Text(placeholder)
            .font(IDS.scaledFont(size: 16, weight: .regular, relativeTo: .body))
            .foregroundColor(IDS.Colors.textSecondary)
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(IDS.Colors.chipBackground)
            .cornerRadius(14)
    }
}

struct SmallBlueButton: View {
    let label: String
    var body: some View {
        Text(label)
            .font(IDS.scaledFont(size: 14, weight: .bold, relativeTo: .footnote))
            .foregroundColor(IDS.Colors.brand)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(Color(hex: 0x223554))
            .cornerRadius(12)
    }
}

struct FlatRow {
    let title: String
    var subtitle: String? = nil
    var trailing: String? = nil
    var trailingIsLink: Bool = false
    var symbol: String? = nil
    var tint: Color = .accentBlue
    var showChevron: Bool = false
    // Real granite mini-app launch (2026-07-16) -- see this file's own header for the
    // "MiniAppsSection...plain, non-functional list rows" note this closes for "Pay
    // bills" specifically. Optional, defaulting nil, so every other FlatRow call site
    // in this file is unaffected.
    var action: (() -> Void)? = nil
}

struct FlatSection: View {
    let title: String
    let rows: [FlatRow]

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(IDS.scaledFont(size: 19, weight: .bold, relativeTo: .title2))
                .foregroundColor(IDS.Colors.textPrimary)
                .padding(.bottom, 6)
            ForEach(rows, id: \.title) { row in
                HStack {
                    if let symbol = row.symbol {
                        ZStack {
                            RoundedRectangle(cornerRadius: 10).fill(row.tint)
                            Image(systemName: symbol)
                                .font(IDS.scaledFont(size: 19, weight: .regular, relativeTo: .body))
                                .foregroundColor(.white)
                        }
                        .frame(width: 34, height: 34)
                    }
                    VStack(alignment: .leading, spacing: 2) {
                        Text(row.title)
                            .font(IDS.scaledFont(size: 17, weight: .medium, relativeTo: .body))
                            .foregroundColor(IDS.Colors.textPrimary)
                        if let subtitle = row.subtitle {
                            Text(subtitle)
                                .font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .caption1))
                                .foregroundColor(IDS.Colors.textTertiary)
                        }
                    }
                    Spacer()
                    if let trailing = row.trailing {
                        Text(trailing)
                            .font(IDS.scaledFont(size: 15, weight: row.trailingIsLink ? .semibold : .regular, relativeTo: .subheadline))
                            .foregroundColor(row.trailingIsLink ? IDS.Colors.brand : IDS.Colors.textSecondary)
                    } else if row.showChevron {
                        Image(systemName: "chevron.right")
                            .foregroundColor(IDS.Colors.textTertiary)
                    }
                }
                .padding(.vertical, 10)
                .contentShape(Rectangle())
                .onTapGesture { row.action?() }
            }
        }
    }
}

struct IconGridSection: View {
    let title: String
    let items: [(String, String)]

    private var rows: [[(String, String)]] {
        stride(from: 0, to: items.count, by: 4).map { Array(items[$0..<min($0 + 4, items.count)]) }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(title)
                .font(IDS.scaledFont(size: 14, weight: .semibold, relativeTo: .footnote))
                .foregroundColor(IDS.Colors.textSecondary)
            ForEach(Array(rows.enumerated()), id: \.offset) { _, rowItems in
                HStack {
                    ForEach(rowItems, id: \.0) { label, symbol in
                        VStack(spacing: 8) {
                            ZStack {
                                RoundedRectangle(cornerRadius: 18).fill(IDS.Colors.chipBackground)
                                Image(systemName: symbol)
                                    .font(IDS.scaledFont(size: 24, weight: .regular, relativeTo: .title2))
                                    .foregroundColor(IDS.Colors.textPrimary)
                            }
                            .frame(width: 54, height: 54)
                            Text(label)
                                .font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .caption1))
                                .foregroundColor(IDS.Colors.textSecondary)
                        }
                        .frame(maxWidth: .infinity)
                    }
                }
            }
        }
    }
}

extension Color {
    static let accentBlue = IdsPalette.accentBlue
    static let accentTeal = IdsPalette.accentTeal
    static let accentPurple = IdsPalette.accentPurple
    static let accentOrange = IdsPalette.accentOrange
    static let accentRed = IdsPalette.accentRed
    static let accentGray = IdsPalette.accentGray
}
