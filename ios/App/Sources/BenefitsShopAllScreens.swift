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
import CoreNetwork
import FeatureCredit

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

// Real 전체 (All services) primary bottom tab (2026-07-24) -- separated from the My tab
// at the user's own direct request in an earlier pass ("My and All screen should be
// separated like KakaoPay... accessed by click on menu icon in app bar"), then brought
// back to the bottom nav directly once mini-apps and (planned) games meant this
// exhaustive service catalog needed to be one tap away, not nested two taps under My --
// matching real Toss's own bottom nav (홈/혜택/쇼핑/페이/전체, no separate "My" tab at
// all; personal info lives at the top of 전체 instead). Mirrors Android's MenuScreen
// exactly (see ItundaAppScreen.kt's own doc comment on TossTab.All for the full history).
// MyTabView's own real content (orders/favorites/listings tracking) isn't dropped -- it's
// reachable one tap in via the profile icon this screen's own IdsAllTopBar now points at,
// the exact same nesting this tab used to have with Menu, just inverted.
struct EntireMenuScreen: View {
    var onOpenSettings: () -> Void = {}
    var onOpenMyTab: () -> Void = {}
    var onClaimInterest: () -> Void = {}
    var onSwitchToTalk: () -> Void = {}
    var onOpenTransferHub: () -> Void = {}

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
    // Real Overview/Loans/Credit score/Certificate/Identity/Support (2026-07-22 port)
    // -- every one of these was found fully built on the backend with zero client UI
    // anywhere until the same-day Android/bank-mfe ports that preceded this one.
    @State private var showOverview = false
    @State private var showLoans = false
    @State private var showCreditScore = false
    @State private var showCertificate = false
    @State private var showIdentity = false
    @State private var showSupport = false
    // Real 26-week savings screen (2026-07-21) -- same Quick-links pattern as Invest
    // above; this feature's backend has been real (ledger-backed, scheduler-driven)
    // since day one but had zero iOS UI until now.
    @State private var showWeeklySavings = false
    // Real KakaoBank mini-style capped starter wallet (2026-07-28, item 101) -- last
    // remaining client platform for this feature (bank-mfe/Android already have it).
    @State private var showMiniWallet = false

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
                    // No back button here (2026-07-24): this is now a persistent
                    // bottom-nav destination, not a screen pushed on top of one --
                    // there's nothing to back out to. My's own real content is one tap
                    // in via IdsAllTopBar's profile icon instead. Same real name/
                    // settings/profile top bar MyTabView used to own when All was
                    // nested under it -- inverted to live here now.
                    IdsAllTopBar(onOpenSettings: onOpenSettings, onOpenMyTab: onOpenMyTab)
                    FlatSection(title: "Quick links", rows: [
                        FlatRow(title: "Pay", subtitle: "Scan or pay by code", symbol: "qrcode", tint: .accentBlue, action: { showPay = true }),
                        FlatRow(title: "Benefits", subtitle: "Points, coupons, rewards", symbol: "gift.fill", tint: .accentOrange, action: { showBenefits = true }),
                        FlatRow(title: "Invest", subtitle: "RSE stocks, real portfolio", symbol: "chart.line.uptrend.xyaxis", tint: .accentPurple, action: { showInvest = true }),
                        FlatRow(title: "26-Week Savings", subtitle: "Escalating auto-save, streak bonus", symbol: "calendar.badge.clock", tint: .accentOrange, action: { showWeeklySavings = true }),
                        FlatRow(title: "Mini account", subtitle: "Capped starter wallet, ages 7-18", symbol: "banknote.fill", tint: .accentTeal, action: { showMiniWallet = true }),
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
                    // "Verify" now opens the real Identity screen (found 2026-07-22
                    // fully built on the backend with zero UI); every other item here
                    // stays purely decorative, unchanged.
                    IconGridSection(
                        title: "Recent services",
                        items: [
                            ("Open acct", "plus.circle"),
                            ("Photo transfer", "camera.fill"),
                            ("Verify", "checkmark.seal.fill"),
                            ("Send", "paperplane.fill"),
                            ("Group", "person.2.fill"),
                            ("Property", "house.fill"),
                            ("Insurance", "shield.fill"),
                            ("More", "ellipsis"),
                        ],
                        onItemClick: { label in if label == "Verify" { showIdentity = true } }
                    )
                    FlatSection(title: "Financial services", rows: [
                        FlatRow(title: "Open account", subtitle: "Itunda Wallet, other banks, RSE brokerage", symbol: "plus.circle", tint: .accentBlue),
                        FlatRow(title: "My assets", subtitle: "Accounts, loans, RSE holdings, cards, points", symbol: "chart.pie.fill", tint: .accentPurple, action: { showOverview = true }),
                        // Real Transfer full page (2026-07-24 port), matching real Toss's
                        // own 송금 row here ("자동이체 · 더치페이" subtitle) -- groups Send
                        // money/Auto-transfer/history in one place instead of Home's Send
                        // button (which stays a quick recipient-picker, unchanged) being the
                        // only entry point. See TransferHubScreen.swift's own doc comment.
                        FlatRow(title: "Transfer", subtitle: "Auto-transfer, split a bill", symbol: "paperplane.fill", tint: .accentBlue, action: onOpenTransferHub),
                        FlatRow(title: "Get a loan", subtitle: "Personal, salary-backed, SME working capital", symbol: "wallet.pass.fill", tint: .accentBlue, action: { showLoans = true }),
                        FlatRow(title: "Credit score", subtitle: "Free check, alternative data", symbol: "chart.line.uptrend.xyaxis", tint: .accentPurple, action: { showCreditScore = true }),
                        FlatRow(title: "Digital certificate", subtitle: "Sign agreements in Itunda", symbol: "checkmark.seal.fill", tint: .accentTeal, action: { showCertificate = true }),
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
                        FlatRow(title: "REG & WASAC bills", symbol: "bolt.fill", tint: .accentBlue, action: { showPayBillsMiniApp = true }),
                        FlatRow(title: "Claim interest now", symbol: "bolt.fill", tint: .accentPurple, action: onClaimInterest),
                        FlatRow(title: "SME income tax estimate", symbol: "banknote.fill", tint: .accentOrange),
                        // Real split-bill (found 2026-07-22 fully built with zero UI
                        // anywhere) lives inside a specific group's own thread (Talk
                        // tab), not a standalone flow -- hand off there instead of
                        // duplicating a group picker.
                        FlatRow(title: "Split a bill with friends", symbol: "person.3.fill", tint: .accentBlue, action: onSwitchToTalk),
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
                        FlatRow(title: "Check my max limit", symbol: "chart.line.uptrend.xyaxis", tint: .accentPurple, action: { showLoans = true }),
                        FlatRow(title: "Personal loan", trailing: "11% ~ 24%", trailingIsLink: true, symbol: "wallet.pass.fill", tint: .accentBlue, action: { showLoans = true }),
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
                        FlatRow(title: "Report an issue with a transaction", showChevron: true, action: { showSupport = true }),
                        FlatRow(title: "My support tickets", showChevron: true, action: { showSupport = true }),
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
        .sheet(isPresented: $showOverview) {
            OverviewScreenView(onBack: { showOverview = false })
        }
        .sheet(isPresented: $showLoans) {
            LoansScreenView(onBack: { showLoans = false })
        }
        .sheet(isPresented: $showCreditScore) {
            CreditScoreScreenView(onBack: { showCreditScore = false })
        }
        .sheet(isPresented: $showCertificate) {
            CertificateScreenView(onBack: { showCertificate = false })
        }
        .sheet(isPresented: $showIdentity) {
            IdentityScreenView(onBack: { showIdentity = false })
        }
        .sheet(isPresented: $showSupport) {
            SupportScreenView(onBack: { showSupport = false })
        }
        .sheet(isPresented: $showWeeklySavings) {
            WeeklySavingsScreenView(onBack: { showWeeklySavings = false })
        }
        .sheet(isPresented: $showMiniWallet) {
            MiniWalletScreenView(onBack: { showMiniWallet = false })
        }
    }
}

// Real Naver-style "My" personal hub (2026-07-22) -- at the user's direct request:
// "My should be like Naver style My since we have shopping and eats and other products
// where users need to easily get track of their orders, reservation, favorites." Every
// number/row here is a real fetched count or preview, not decoration -- the same "no
// fabricated numbers" discipline this app already follows elsewhere.
//
// Trimmed down (2026-07-24) to ONLY this unique content -- its old "Quick links" and
// "My account" sections are deleted, since both now fully duplicate rows already in
// EntireMenuScreen's own catalog now that All is the primary bottom tab (see that
// screen's own doc comment). Mirrors Android's trimmed MyTab exactly. No longer this
// tab's own body: reached instead as a real back-button overlay from the profile icon
// at the top of EntireMenuScreen (see ContentView.swift's own fullScreenCover), the
// exact same nesting All used to have under this tab, just inverted.
struct MyTabView: View {
    var onBack: () -> Void = {}
    var onSwitchToShop: () -> Void = {}
    var onSwitchToHood: () -> Void = {}

    @State private var shopOrders: [OrderDto] = []
    @State private var eatsOrders: [EatsOrderDto] = []
    @State private var favoriteListingsCount = 0
    @State private var favoriteJobPostsCount = 0
    @State private var favoritePropertyListingsCount = 0
    @State private var favoriteRestaurantsCount = 0
    @State private var myListingsCount = 0
    @State private var myJobPostsCount = 0
    @State private var myPropertyListingsCount = 0

    var body: some View {
        ScrollView {
            VStack(spacing: IDS.Layout.sectionSpacing) {
                HStack {
                    Button(action: onBack) { Image(systemName: "chevron.left").foregroundColor(IDS.Colors.textPrimary) }
                    Spacer()
                    Text("My").font(IDS.scaledFont(size: 20, weight: .bold, relativeTo: .title2)).foregroundColor(IDS.Colors.textPrimary)
                    Spacer()
                    Color.clear.frame(width: 20)
                }
                // Real order tracking -- Naver Pay/Shopping's own "My" tab leads with
                // recent orders across every product, not a settings list. Tapping
                // switches to that product's own tab where the full order-history view
                // already lives.
                if !shopOrders.isEmpty || !eatsOrders.isEmpty {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("My orders").font(IDS.scaledFont(size: 19, weight: .bold, relativeTo: .title2)).foregroundColor(IDS.Colors.textPrimary)
                        ForEach(shopOrders.prefix(3)) { order in
                            orderRow(label: "Shop order", status: order.status, amount: order.totalAmount, action: onSwitchToShop)
                        }
                        ForEach(eatsOrders.prefix(3)) { order in
                            orderRow(label: "Eats order", status: order.status, amount: order.totalAmount, action: onSwitchToShop)
                        }
                    }
                }
                // Real favorites/wishlist tracking across every product with one --
                // counts are real (GET .../favorites on each module); tapping switches
                // to the product's own tab where its dedicated wishlist view already
                // lives (Marketplace/Jobs/Property under Hood, restaurants under
                // Shop's Eats toggle) -- an honest one-more-tap scope, not a full deep
                // link into the nested sub-view.
                FlatSection(title: "My favorites", rows: [
                    FlatRow(title: "Marketplace wishlist", trailing: "\(favoriteListingsCount)", symbol: "heart", tint: .accentRed, action: onSwitchToHood),
                    FlatRow(title: "Jobs wishlist", trailing: "\(favoriteJobPostsCount)", symbol: "heart", tint: .accentRed, action: onSwitchToHood),
                    FlatRow(title: "Property wishlist", trailing: "\(favoritePropertyListingsCount)", symbol: "heart", tint: .accentRed, action: onSwitchToHood),
                    FlatRow(title: "Restaurant favorites", trailing: "\(favoriteRestaurantsCount)", symbol: "heart", tint: .accentRed, action: onSwitchToShop),
                ])
                // Real "my own posts" tracking (Marketplace/Jobs/Property listings I
                // created) -- same Naver-style "track your own activity" pattern.
                FlatSection(title: "My listings", rows: [
                    FlatRow(title: "Marketplace", trailing: "\(myListingsCount)", symbol: "storefront.fill", tint: .accentBlue, action: onSwitchToHood),
                    FlatRow(title: "Jobs posted", trailing: "\(myJobPostsCount)", symbol: "briefcase.fill", tint: .accentBlue, action: onSwitchToHood),
                    FlatRow(title: "Property listed", trailing: "\(myPropertyListingsCount)", symbol: "house.fill", tint: .accentTeal, action: onSwitchToHood),
                ])
                // "My account" (My assets/Get a loan/Credit score/etc) deliberately
                // dropped here (2026-07-24) -- every one of those rows already lives in
                // EntireMenuScreen's own "Financial services" section now that All is
                // the primary bottom tab; keeping a second copy here would just be stale
                // duplication. Mirrors Android's identical MyTab cleanup.
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.top, IDS.Layout.screenTop)
            .padding(.bottom, IDS.Layout.sectionSpacing)
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task {
            // Each fetch independent and best-effort -- one product's API hiccup must
            // never blank the rest of this real personal-activity summary.
            if let res = try? await NetworkClient.shared.getMyOrders() { shopOrders = res.orders }
            if let res = try? await NetworkClient.shared.getMyEatsOrders() { eatsOrders = res.orders }
            if let res = try? await NetworkClient.shared.getMyFavoriteListings() { favoriteListingsCount = res.favorites.count }
            if let res = try? await NetworkClient.shared.getMyFavoriteJobPosts() { favoriteJobPostsCount = res.favorites.count }
            if let res = try? await NetworkClient.shared.getMyFavoritePropertyListings() { favoritePropertyListingsCount = res.favorites.count }
            if let res = try? await NetworkClient.shared.getMyFavoriteRestaurants() { favoriteRestaurantsCount = res.favorites.count }
            if let res = try? await NetworkClient.shared.getMyListings() { myListingsCount = res.listings.count }
            if let res = try? await NetworkClient.shared.getMyJobPosts() { myJobPostsCount = res.posts.count }
            if let res = try? await NetworkClient.shared.getMyPropertyListings() { myPropertyListingsCount = res.listings.count }
        }
    }

    @ViewBuilder
    private func orderRow(label: String, status: String, amount: Double, action: @escaping () -> Void) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(label).font(IDS.scaledFont(size: 15, weight: .semibold, relativeTo: .subheadline)).foregroundColor(IDS.Colors.textPrimary)
                Text(status).font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
            Spacer()
            Text("RWF \(Int(amount))").foregroundColor(IDS.Colors.textPrimary)
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: action)
        .padding(.vertical, 6)
    }
}

// Was a text navbar -- "ID | Support | Settings" with pipe separators, then a hamburger
// icon leading to the Menu screen -- neither has an equivalent in real Toss. The 전체
// (All) tab top bar is just the user's name plus a profile icon (2026-07-24: inverted
// from a hamburger, since this bar itself now IS the Menu/전체 screen's own top bar --
// see EntireMenuScreen's own doc comment for the full history) and a settings icon;
// support/ID live as rows further down the list, not up here.
private struct IdsAllTopBar: View {
    var onOpenSettings: () -> Void = {}
    // Real My-activity screen (2026-07-24, inverted from Menu) -- see
    // EntireMenuScreen's own doc comment: the profile icon now leads to the
    // personal-activity screen (orders/favorites/listings), the exact reverse of this
    // bar's old Menu icon. Optional, nil default so this bar's only other real caller
    // (HomeTopBar-style reuse, if any) is unaffected.
    var onOpenMyTab: (() -> Void)? = nil

    var body: some View {
        HStack {
            Text("TUYIZERE ERIC")
                .font(IDS.scaledFont(size: 26, weight: .bold, relativeTo: .largeTitle))
                .foregroundColor(IDS.Colors.textPrimary)
            Spacer()
            if let onOpenMyTab {
                Button(action: onOpenMyTab) {
                    Image(systemName: "person.fill")
                        .font(IDS.scaledFont(size: 20, weight: .regular, relativeTo: .body))
                        .foregroundColor(IDS.Colors.textPrimary)
                }
                .accessibilityLabel("My activity")
            }
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

// Real shared browse-header view (2026-07-21) -- extracted from Eats'
// OrderFoodContent (the only place this pattern previously existed) so Shop's
// merchant browse can reuse the identical search+chips interaction instead of a
// second bespoke implementation. Callers own their own debounce/state; this just
// renders the field + optional chip row.
struct SearchAndCategoryChips: View {
    let searchText: String
    let onSearchChange: (String) -> Void
    let placeholder: String
    let categories: [String]
    let selectedCategory: String?
    let onSelectCategory: (String?) -> Void

    var body: some View {
        VStack(spacing: IDS.Layout.cardGap) {
            TextField(placeholder, text: Binding(get: { searchText }, set: onSearchChange))
                .padding(12)
                .background(IDS.Colors.chipBackground)
                .cornerRadius(12)

            if !categories.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(([nil] as [String?]) + categories.map { Optional($0) }, id: \.self) { c in
                            Button(action: { onSelectCategory(c) }) {
                                Text(c ?? "All")
                                    .font(.caption).bold()
                                    .foregroundColor(selectedCategory == c ? .white : IDS.Colors.textSecondary)
                                    .padding(.horizontal, 14).padding(.vertical, 6)
                                    .background(selectedCategory == c ? IDS.Colors.brand : IDS.Colors.chipBackground)
                                    .cornerRadius(16)
                            }
                        }
                    }
                }
            }
        }
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
    // Real click support (2026-07-22) -- optional, defaulting nil, so every existing
    // call site (purely decorative) is unaffected. Only "Verify" (EntireMenuScreen)
    // passes one, to open the real Identity screen.
    var onItemClick: ((String) -> Void)? = nil

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
                        .contentShape(Rectangle())
                        .onTapGesture { onItemClick?(label) }
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
