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
// exactly (see ItundaAppScreen.kt's own doc comment on ItundaTab.All for the full history).
// MyTabView's own real content (orders/favorites/listings tracking) isn't dropped -- it's
// reachable one tap in via the profile icon this screen's own IdsAllTopBar now points at,
// the exact same nesting this tab used to have with Menu, just inverted.
struct EntireMenuScreen: View {
    var onOpenSettings: () -> Void = {}
    var onOpenMyTab: () -> Void = {}
    var onClaimInterest: () -> Void = {}
    var onSwitchToTalk: () -> Void = {}
    var onOpenTransferHub: () -> Void = {}
    var onOpenProperty: () -> Void = {}

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
    // Real Toss Bank 체크카드 (check/debit card) screen (2026-07-31, item 207) --
    // bank-mfe/Android shipped first; this is the last remaining client platform.
    @State private var showCard = false
    // Real Kakao Bank 모임통장 (group/shared account) screen (2026-07-28, item 105) --
    // last remaining client platform for this feature (bank-mfe always had it, Android
    // ported the same day as item 104).
    @State private var showGroupAccounts = false
    // Real ikimina (Rwanda's own rotating savings & credit association) -- the first
    // feature in this codebase not sourced from Toss/Kakao/Naver/Coupang.
    @State private var showIkimina = false
    // Real Umurenge SACCO-style shares & dividends -- the second Rwanda-specific
    // feature not sourced from Toss/Kakao/Naver/Coupang.
    @State private var showSacco = false
    // Real coffee-cooperative harvest-advance / input financing -- the third
    // Rwanda-specific feature not sourced from Toss/Kakao/Naver/Coupang.
    @State private var showHarvestAdvance = false
    // Real Kakao Pay spending categorization screen (2026-07-28, item 108) -- last
    // remaining client platform for this feature (bank-mfe item 106, Android item 107).
    @State private var showSpending = false
    // Real Kakao T-style ride-hailing screen (2026-07-28, item 110) -- last remaining
    // client platform for this feature (bank-mfe always had it, Android item 109).
    @State private var showRides = false
    // Real Kakao T 대리운전 (designated driver, item 221) -- bank-mfe/Android shipped
    // first; same pattern.
    @State private var showDesignatedDriver = false
    // Real Kakao T 바이크 (Kakao T Bike, item 222) -- bank-mfe/Android shipped first;
    // same pattern.
    @State private var showBikeRental = false
    // Real Kakao T 주차 (Kakao T Parking, item 223) -- bank-mfe/Android shipped first;
    // same pattern.
    @State private var showParking = false
    @State private var showBus = false
    // Real Naver 지식iN (Knowledge iN) open-topic community Q&A (item 225) --
    // bank-mfe shipped first; same pattern.
    @State private var showKnowledge = false
    // Real 당근마켓 중고차 정비소 동행 (used-car mechanic-inspection accompaniment)
    // screen (2026-07-31) -- bank-mfe and Android shipped first; same pattern.
    @State private var showVehicleInspection = false
    // Real Toss 내 차 시세 (my car's market value) screen (2026-07-31) -- bank-mfe and
    // Android shipped first; same pattern.
    @State private var showVehicleValuation = false
    // Real Toss 유스 (Toss Youth)-style guardian-child link screen (2026-07-31) --
    // bank-mfe and Android shipped first; same pattern.
    @State private var showFamilyLink = false
    // Real detected + merchant-billing subscriptions screen (2026-07-31) -- bank-mfe
    // and Android shipped first; same pattern.
    @State private var showSubscriptions = false
    // Real Kakao Pay round-up auto-saving screen (2026-07-28, item 113) -- last
    // remaining client platform for this feature (bank-mfe item 112, Android already
    // had it).
    @State private var showRoundUp = false
    // Real 토스뱅크 외화통장 (foreign-currency account) screen (item 160) -- last
    // remaining client platform for this feature (bank-mfe item 154, Android already
    // had it).
    @State private var showForeignCurrency = false
    // Real Toss Bank 먼저 이자받는 정기예금 (interest-paid-upfront term deposit) screen
    // (item 161) -- last remaining client platform for this feature (bank-mfe item 153,
    // Android already had it).
    @State private var showUpfrontDeposit = false
    // Real person-to-person payment request (item 171) -- last remaining client
    // platform for this feature (bank-mfe/item 167, Android/item 170).
    @State private var showRequestMoney = false
    @State private var showAutoTopUp = false
    // Real Karrot-Score-style trust/reputation self-view screen (item 152) --
    // bank-mfe/Android shipped first; same pattern.
    @State private var showTrustScore = false
    // Real Itunda cash-agent operator console (staff-facing till: cash-in/cash-out/
    // reconciliation) -- bank-mfe/Android shipped first; same pattern.
    @State private var showAgentOperator = false
    // Real peer-to-peer agent float rebalancing marketplace -- see
    // FloatMarketplaceScreenView.swift's own doc comment for the full sourced account.
    @State private var showFloatMarketplace = false
    // Real means-tested VUP (Vision 2020 Umurenge Programme) Financial Services
    // microloan -- see VupLoanScreenView.swift's own doc comment for the full sourced
    // account. bank-mfe shipped first; this is the first native client.
    @State private var showVupLoan = false
    // Real Rwanda moto-taxi ownership savings-to-loan plan -- see
    // MotoOwnershipScreenView.swift's own doc comment for the full sourced account.
    // bank-mfe shipped first (commits cf9d72fe/808a7ce4); this is the first native
    // client.
    @State private var showMotoOwnership = false
    // Real fix (2026-08-10) -- see IdsSearchBar's own doc comment for the full
    // account of the fake-search-bar bug this closes.
    @State private var menuSearchQuery: String = ""
    // Real fix (2026-08-10): matches Android/web's identical collapse-by-default fix
    // (see CollapsibleFlatSection's own doc comment for the full Hick's Law account).
    // "Quick links"/"Mini apps" stay always-visible; only the 16 heavier categories
    // below them collapse. Safe to add without touching the two Group{} blocks' own
    // child count -- collapsing is internal to each CollapsibleFlatSection's own
    // rendering, not a change in how many views the parent Group receives.
    @State private var expandedMenuSection: String? = nil

    // Real fix (2026-08-10): a second, independent list of the exact same 17
    // sections/rows rendered below, built purely so search can filter across all of
    // them without touching the two `Group { }` blocks below -- those are a real,
    // version-sensitive Swift ViewBuilder child-count workaround (see this struct's
    // own header comment on why 15 children needed splitting for Xcode 14.3.1/Swift
    // 5.8.1), and this file has no way to `xcodebuild` verify a restructuring of
    // them (App target CocoaPods/RN-bridge gap, documented elsewhere in this repo).
    // Duplicating the row list is a small, deliberate trade against a real risk of a
    // silent, unverifiable break in the one iOS screen most repeatedly identified as
    // needing "organized, not a data dump" -- not touching that structure at all is
    // the safer failure mode. Every title/action pair below is a literal copy of the
    // matching row two Groups down; if one changes, the other must too.
    private var searchableMenuSections: [(title: String, rows: [FlatRow])] {
        [
            ("Quick links", [
                FlatRow(title: "Pay", subtitle: "Scan or pay by code", symbol: "qrcode", tint: .accentBlue, action: { showPay = true }),
                FlatRow(title: "Benefits", subtitle: "Points, coupons, rewards", symbol: "gift.fill", tint: .accentOrange, action: { showBenefits = true }),
                FlatRow(title: "Invest", subtitle: "RSE stocks, real portfolio", symbol: "chart.line.uptrend.xyaxis", tint: .accentPurple, action: { showInvest = true }),
                FlatRow(title: "26-Week Savings", subtitle: "Escalating auto-save, streak bonus", symbol: "calendar.badge.clock", tint: .accentOrange, action: { showWeeklySavings = true }),
                FlatRow(title: "Mini account", subtitle: "Capped starter wallet, ages 7-18", symbol: "banknote.fill", tint: .accentTeal, action: { showMiniWallet = true }),
                FlatRow(title: "Card", subtitle: "App-controlled spend limits, one-tap freeze", symbol: "creditcard.fill", tint: .accentBlue, action: { showCard = true }),
                FlatRow(title: "Group account", subtitle: "Shared account with dues and split expenses", symbol: "person.2.fill", tint: .accentPurple, action: { showGroupAccounts = true }),
                FlatRow(title: "Ikimina", subtitle: "Rotating savings group -- everyone takes a turn", symbol: "arrow.triangle.2.circlepath", tint: .accentTeal, action: { showIkimina = true }),
                FlatRow(title: "SACCO shares", subtitle: "Buy cooperative shares, earn a real dividend", symbol: "chart.pie.fill", tint: .accentPurple, action: { showSacco = true }),
                FlatRow(title: "Harvest advance", subtitle: "Coffee cooperative input financing", symbol: "leaf.fill", tint: .accentTeal, action: { showHarvestAdvance = true }),
                FlatRow(title: "VUP Financial Services", subtitle: "Means-tested government microloan for farming, livestock, business", symbol: "banknote.fill", tint: .accentBlue, action: { showVupLoan = true }),
                FlatRow(title: "Map", subtitle: "Real Rwanda map, self-hosted", symbol: "map.fill", tint: .accentTeal, action: { showMap = true }),
            ]),
            ("Mini apps", [
                FlatRow(title: "Wallet balance", showChevron: true, action: { showWalletBalanceMiniApp = true }),
                FlatRow(title: "Pay bills", showChevron: true, action: { showPayBillsMiniApp = true }),
                FlatRow(title: "Reward tasks", showChevron: true, action: { showRewardTasksMiniApp = true }),
                FlatRow(title: "Insurance", showChevron: true, action: { showInsuranceMiniApp = true }),
            ]),
            ("Accounts & cards", [
                FlatRow(title: "Open account", subtitle: "Itunda Wallet, other banks, RSE brokerage", symbol: "plus.circle", tint: .accentBlue, action: { showOverview = true }),
                FlatRow(title: "My assets", subtitle: "Accounts, loans, RSE holdings, cards, points", symbol: "chart.pie.fill", tint: .accentPurple, action: { showOverview = true }),
                FlatRow(title: "Spending", subtitle: "Real, ledger-based category breakdown", symbol: "chart.pie.fill", tint: .accentBlue, action: { showSpending = true }),
                FlatRow(title: "Family", subtitle: "Link a guardian or child, view read-only spending", symbol: "person.2.fill", tint: .accentPurple, action: { showFamilyLink = true }),
                FlatRow(title: "Subscriptions", subtitle: "Detected recurring payments + merchant billing plans", symbol: "calendar", tint: .accentBlue, action: { showSubscriptions = true }),
                FlatRow(title: "Digital certificate", subtitle: "Sign agreements in Itunda", symbol: "checkmark.seal.fill", tint: .accentTeal, action: { showCertificate = true }),
            ]),
            ("Send & pay", [
                FlatRow(title: "Transfer", subtitle: "Auto-transfer, split a bill", symbol: "paperplane.fill", tint: .accentBlue, action: onOpenTransferHub),
                FlatRow(title: "Request money", subtitle: "Generate a real payment request code", symbol: "text.badge.plus", tint: .accentBlue, action: { showRequestMoney = true }),
                FlatRow(title: "Auto top-up", subtitle: "Refill your wallet automatically from a linked account", symbol: "arrow.triangle.2.circlepath", tint: .accentBlue, action: { showAutoTopUp = true }),
                FlatRow(title: "Mobile plan", subtitle: "MTN, Airtel, broadband", symbol: "globe", tint: .accentTeal, action: { showPayBillsMiniApp = true }),
            ]),
            ("Save & grow", [
                FlatRow(title: "Round-up savings", subtitle: "Auto-save spare change from every transfer", symbol: "arrow.up.circle.fill", tint: .accentOrange, action: { showRoundUp = true }),
                FlatRow(title: "12-month deposit", subtitle: "Interest paid upfront, principal locked 12 months", symbol: "lock.fill", tint: .accentTeal, action: { showUpfrontDeposit = true }),
            ]),
            ("Borrow", [
                FlatRow(title: "Get a loan", subtitle: "Personal, salary-backed, SME working capital", symbol: "wallet.pass.fill", tint: .accentBlue, action: { showLoans = true }),
                FlatRow(title: "Credit score", subtitle: "Free check, alternative data", symbol: "chart.line.uptrend.xyaxis", tint: .accentPurple, action: { showCreditScore = true }),
                FlatRow(title: "Moto-Taxi Ownership", subtitle: "Save a 30% down payment, then convert to a loan for your own bike", symbol: "bicycle", tint: .accentTeal, action: { showMotoOwnership = true }),
            ]),
            ("Transport", [
                FlatRow(title: "Rides", subtitle: "Request a ride or drive for real fares", symbol: "car.fill", tint: .accentBlue, action: { showRides = true }),
                FlatRow(title: "Designated driver", subtitle: "A driver takes you and your own car home", symbol: "arrow.left.arrow.right", tint: .accentTeal, action: { showDesignatedDriver = true }),
                FlatRow(title: "Bike rental", subtitle: "Rent a nearby bike or scooter, billed by the minute", symbol: "bicycle", tint: .accentBlue, action: { showBikeRental = true }),
                FlatRow(title: "Parking", subtitle: "Rent a nearby parking spot, billed by the hour", symbol: "parkingsign.circle.fill", tint: .accentPurple, action: { showParking = true }),
                FlatRow(title: "Bus", subtitle: "Book intercity bus seats or post your own route", symbol: "bus.fill", tint: .accentTeal, action: { showBus = true }),
                FlatRow(title: "Vehicle inspection", subtitle: "Pay a mechanic to inspect a used car before you buy", symbol: "wrench.and.screwdriver.fill", tint: .accentTeal, action: { showVehicleInspection = true }),
                FlatRow(title: "My vehicles", subtitle: "Track your car's estimated resale value", symbol: "car.fill", tint: .accentTeal, action: { showVehicleValuation = true }),
            ]),
            ("Community & trust", [
                FlatRow(title: "Trust score", subtitle: "How your neighbors see you on Marketplace, Jobs, and Property", symbol: "checkmark.seal.fill", tint: .accentTeal, action: { showTrustScore = true }),
                FlatRow(title: "Q&A", subtitle: "Ask a question, answer one, get adopted", symbol: "questionmark.circle.fill", tint: .accentPurple, action: { showKnowledge = true }),
            ]),
            ("Cash agent tools", [
                FlatRow(title: "Agent till", subtitle: "For assigned cash-agent operators: cash-in, cash-out, till count", symbol: "storefront.fill", tint: .accentBlue, action: { showAgentOperator = true }),
                FlatRow(title: "Float marketplace", subtitle: "For assigned cash-agents: offer or request float from nearby agents", symbol: "arrow.left.arrow.right.circle.fill", tint: .accentTeal, action: { showFloatMarketplace = true }),
            ]),
            ("Switch & save", [
                FlatRow(title: "Switch your personal loan", trailing: "12% ~ 24%", trailingIsLink: true, symbol: "wallet.pass.fill", tint: .accentBlue, action: { showLoans = true }),
                FlatRow(title: "Switch your rent deposit loan", trailing: "9% ~ 15%", trailingIsLink: true, symbol: "house.fill", tint: .accentTeal, action: { showLoans = true }),
                FlatRow(title: "Switch your SME loan", trailing: "11% ~ 22%", trailingIsLink: true, symbol: "storefront.fill", tint: .accentTeal, action: { showLoans = true }),
            ]),
            ("Cards", [
                FlatRow(title: "Itunda Card", trailing: "5% back on bills", trailingIsLink: true, symbol: "creditcard.fill", tint: .accentRed, action: { showCard = true }),
                FlatRow(title: "Virtual card", trailing: "Instant issue", symbol: "creditcard.fill", tint: .accentGray, action: { showCard = true }),
            ]),
            ("Services", [
                FlatRow(title: "Rent deposit protection", symbol: "house.fill", tint: .accentBlue),
                FlatRow(title: "Recurring payments", symbol: "doc.text.fill", tint: .accentBlue),
                FlatRow(title: "Import recurring payments", symbol: "shippingbox.fill", tint: .accentGray),
                FlatRow(title: "REG & WASAC bills", symbol: "bolt.fill", tint: .accentBlue, action: { showPayBillsMiniApp = true }),
                FlatRow(title: "Claim interest now", symbol: "bolt.fill", tint: .accentPurple, action: onClaimInterest),
                FlatRow(title: "SME income tax estimate", symbol: "banknote.fill", tint: .accentOrange),
                FlatRow(title: "Split a bill with friends", symbol: "person.3.fill", tint: .accentBlue, action: onSwitchToTalk),
                FlatRow(title: "Shared calendar", symbol: "calendar", tint: .accentBlue),
                FlatRow(title: "Kids' allowance tasks", symbol: "checkmark.circle.fill", tint: .accentOrange),
            ]),
            ("Foreign currency", [
                FlatRow(title: "Foreign currency wallet", trailing: "100% rate preference", trailingIsLink: true, symbol: "wallet.pass.fill", tint: .accentPurple, action: { showForeignCurrency = true }),
                FlatRow(title: "International transfer", symbol: "dollarsign.circle.fill", tint: .accentBlue, action: { showForeignCurrency = true }),
            ]),
            ("Grow your money", [
                FlatRow(title: "RSE stocks", subtitle: "BOK, MTNR, BLR, IMR, CMR, EQTY", symbol: "chart.line.uptrend.xyaxis", tint: .accentTeal, action: { showInvest = true }),
                FlatRow(title: "Bonds & fixed income", trailing: "7.5% ~ 12%", trailingIsLink: true, symbol: "building.columns.fill", tint: .accentBlue, action: { showInvest = true }),
                FlatRow(title: "IPO schedule", symbol: "chart.line.uptrend.xyaxis", tint: .accentRed, action: { showInvest = true }),
                FlatRow(title: "Brokerage account", trailing: "Up to 30,000 RWF", trailingIsLink: true, symbol: "building.columns.fill", tint: .accentTeal, action: { showInvest = true }),
            ]),
            ("Pension", [
                FlatRow(title: "Check my RSSB pension", symbol: "building.columns.fill", tint: .accentBlue),
                FlatRow(title: "Pension products", symbol: "percent", tint: .accentBlue),
            ]),
            ("Loans", [
                FlatRow(title: "Check my max limit", symbol: "chart.line.uptrend.xyaxis", tint: .accentPurple, action: { showLoans = true }),
                FlatRow(title: "Personal loan", trailing: "11% ~ 24%", trailingIsLink: true, symbol: "wallet.pass.fill", tint: .accentBlue, action: { showLoans = true }),
            ]),
            ("Notifications & consent", [
                FlatRow(title: "Notifications", showChevron: true, action: onOpenSettings),
                FlatRow(title: "Credit data usage policy"),
                FlatRow(title: "Privacy policy"),
                FlatRow(title: "Terms & consent"),
            ]),
            ("Support", [
                FlatRow(title: "FAQ"),
                FlatRow(title: "Live chat"),
                FlatRow(title: "Call support"),
                FlatRow(title: "Report an issue with a transaction", showChevron: true, action: { showSupport = true }),
                FlatRow(title: "My support tickets", showChevron: true, action: { showSupport = true }),
                FlatRow(title: "Announcements"),
            ]),
        ]
    }

    private var matchingSearchSections: [(title: String, rows: [FlatRow])] {
        let query = menuSearchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else { return [] }
        return searchableMenuSections.compactMap { section in
            let matches = section.rows.filter { $0.title.localizedCaseInsensitiveContains(query) }
            return matches.isEmpty ? nil : (section.title, matches)
        }
    }

    var body: some View {
        ScrollView {
            // Real fix (2026-08-10): menuSearchQuery.isEmpty branch below is the
            // pre-existing browse view, completely unchanged. The non-empty branch
            // is new and independent -- see searchableMenuSections's own doc comment
            // for why this doesn't touch the Group split below at all.
            if menuSearchQuery.isEmpty {
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
                        FlatRow(title: "Card", subtitle: "App-controlled spend limits, one-tap freeze", symbol: "creditcard.fill", tint: .accentBlue, action: { showCard = true }),
                        FlatRow(title: "Group account", subtitle: "Shared account with dues and split expenses", symbol: "person.2.fill", tint: .accentPurple, action: { showGroupAccounts = true }),
                        FlatRow(title: "Ikimina", subtitle: "Rotating savings group -- everyone takes a turn", symbol: "arrow.triangle.2.circlepath", tint: .accentTeal, action: { showIkimina = true }),
                        FlatRow(title: "SACCO shares", subtitle: "Buy cooperative shares, earn a real dividend", symbol: "chart.pie.fill", tint: .accentPurple, action: { showSacco = true }),
                        FlatRow(title: "Harvest advance", subtitle: "Coffee cooperative input financing", symbol: "leaf.fill", tint: .accentTeal, action: { showHarvestAdvance = true }),
                        FlatRow(title: "VUP Financial Services", subtitle: "Means-tested government microloan for farming, livestock, business", symbol: "banknote.fill", tint: .accentBlue, action: { showVupLoan = true }),
                        FlatRow(title: "Map", subtitle: "Real Rwanda map, self-hosted", symbol: "map.fill", tint: .accentTeal, action: { showMap = true }),
                    ])
                    IdsSearchBar(text: $menuSearchQuery, placeholder: "Search everything else")
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
                    // Real gap found live (2026-08-10), same fix as Android's identical
                    // grid: titled "Recent services" but nothing here was ever actually
                    // tracked as recent, and 6 of its 8 icons were dead taps (tap
                    // highlight fires, nothing happens). Renamed "Shortcuts" (what it
                    // actually is) and wired to the same real destinations the rest of
                    // this screen uses. "Photo transfer" dropped outright, not wired --
                    // no OCR/photo-based transfer feature exists anywhere in this app,
                    // same "don't fake a destination that doesn't exist" call already
                    // made for QuickActions' Scan-to-Pay icon.
                    IconGridSection(
                        title: "Shortcuts",
                        items: [
                            ("Open account", "plus.circle"),
                            ("Verify", "checkmark.seal.fill"),
                            ("Send", "paperplane.fill"),
                            ("Group", "person.2.fill"),
                            ("Property", "house.fill"),
                            ("Insurance", "shield.fill"),
                        ],
                        onItemClick: { label in
                            switch label {
                            case "Open account": showOverview = true
                            case "Verify": showIdentity = true
                            case "Send": onOpenTransferHub()
                            case "Group": showGroupAccounts = true
                            case "Property": onOpenProperty()
                            case "Insurance": showInsuranceMiniApp = true
                            default: break
                            }
                        }
                    )
                    // Real gap found live (2026-08-10), user-flagged: this used to be one
                    // 25-row "Financial services" section mixing loans, savings, bus
                    // tickets, and vehicle inspection under one label -- the exact
                    // supply-side-dump anti-pattern Toss Tech's own product-restructuring
                    // writeup names and fixes (toss.tech/article/mydoc), and the same one
                    // Android's MenuScreen just got fixed for (see ItundaAppScreen.kt's
                    // own doc comment). Split into the same real, user-intent-based
                    // sections Android now uses, for consistency across platforms. Also
                    // fixes 2 dead taps found the same way Android's were: "Open account"
                    // and "Mobile plan" had no `action:` at all.
                    CollapsibleFlatSection(title: "Accounts & cards", rows: [
                        FlatRow(title: "Open account", subtitle: "Itunda Wallet, other banks, RSE brokerage", symbol: "plus.circle", tint: .accentBlue, action: { showOverview = true }),
                        FlatRow(title: "My assets", subtitle: "Accounts, loans, RSE holdings, cards, points", symbol: "chart.pie.fill", tint: .accentPurple, action: { showOverview = true }),
                        FlatRow(title: "Spending", subtitle: "Real, ledger-based category breakdown", symbol: "chart.pie.fill", tint: .accentBlue, action: { showSpending = true }),
                        FlatRow(title: "Family", subtitle: "Link a guardian or child, view read-only spending", symbol: "person.2.fill", tint: .accentPurple, action: { showFamilyLink = true }),
                        FlatRow(title: "Subscriptions", subtitle: "Detected recurring payments + merchant billing plans", symbol: "calendar", tint: .accentBlue, action: { showSubscriptions = true }),
                        FlatRow(title: "Digital certificate", subtitle: "Sign agreements in Itunda", symbol: "checkmark.seal.fill", tint: .accentTeal, action: { showCertificate = true }),
                    ], isExpanded: expandedMenuSection == "Accounts & cards", onToggle: { expandedMenuSection = (expandedMenuSection == "Accounts & cards") ? nil : "Accounts & cards" })
                    CollapsibleFlatSection(title: "Send & pay", rows: [
                        // Real Transfer full page (2026-07-24 port), matching real Toss's
                        // own 송금 row here ("자동이체 · 더치페이" subtitle) -- groups Send
                        // money/Auto-transfer/history in one place instead of Home's Send
                        // button (which stays a quick recipient-picker, unchanged) being the
                        // only entry point. See TransferHubScreen.swift's own doc comment.
                        FlatRow(title: "Transfer", subtitle: "Auto-transfer, split a bill", symbol: "paperplane.fill", tint: .accentBlue, action: onOpenTransferHub),
                        FlatRow(title: "Request money", subtitle: "Generate a real payment request code", symbol: "text.badge.plus", tint: .accentBlue, action: { showRequestMoney = true }),
                        FlatRow(title: "Auto top-up", subtitle: "Refill your wallet automatically from a linked account", symbol: "arrow.triangle.2.circlepath", tint: .accentBlue, action: { showAutoTopUp = true }),
                        // MTN/Airtel airtime and broadband are real billers inside the Pay
                        // Bills mini-app -- same real destination "REG & WASAC bills" below
                        // already uses, same fix as Android's identical dead tap.
                        FlatRow(title: "Mobile plan", subtitle: "MTN, Airtel, broadband", symbol: "globe", tint: .accentTeal, action: { showPayBillsMiniApp = true }),
                    ], isExpanded: expandedMenuSection == "Send & pay", onToggle: { expandedMenuSection = (expandedMenuSection == "Send & pay") ? nil : "Send & pay" })
                    CollapsibleFlatSection(title: "Save & grow", rows: [
                        FlatRow(title: "Round-up savings", subtitle: "Auto-save spare change from every transfer", symbol: "arrow.up.circle.fill", tint: .accentOrange, action: { showRoundUp = true }),
                        FlatRow(title: "12-month deposit", subtitle: "Interest paid upfront, principal locked 12 months", symbol: "lock.fill", tint: .accentTeal, action: { showUpfrontDeposit = true }),
                    ], isExpanded: expandedMenuSection == "Save & grow", onToggle: { expandedMenuSection = (expandedMenuSection == "Save & grow") ? nil : "Save & grow" })
                    CollapsibleFlatSection(title: "Borrow", rows: [
                        FlatRow(title: "Get a loan", subtitle: "Personal, salary-backed, SME working capital", symbol: "wallet.pass.fill", tint: .accentBlue, action: { showLoans = true }),
                        FlatRow(title: "Credit score", subtitle: "Free check, alternative data", symbol: "chart.line.uptrend.xyaxis", tint: .accentPurple, action: { showCreditScore = true }),
                        FlatRow(title: "Moto-Taxi Ownership", subtitle: "Save a 30% down payment, then convert to a loan for your own bike", symbol: "bicycle", tint: .accentTeal, action: { showMotoOwnership = true }),
                    ], isExpanded: expandedMenuSection == "Borrow", onToggle: { expandedMenuSection = (expandedMenuSection == "Borrow") ? nil : "Borrow" })
                    CollapsibleFlatSection(title: "Transport", rows: [
                        FlatRow(title: "Rides", subtitle: "Request a ride or drive for real fares", symbol: "car.fill", tint: .accentBlue, action: { showRides = true }),
                        FlatRow(title: "Designated driver", subtitle: "A driver takes you and your own car home", symbol: "arrow.left.arrow.right", tint: .accentTeal, action: { showDesignatedDriver = true }),
                        FlatRow(title: "Bike rental", subtitle: "Rent a nearby bike or scooter, billed by the minute", symbol: "bicycle", tint: .accentBlue, action: { showBikeRental = true }),
                        FlatRow(title: "Parking", subtitle: "Rent a nearby parking spot, billed by the hour", symbol: "parkingsign.circle.fill", tint: .accentPurple, action: { showParking = true }),
                        FlatRow(title: "Bus", subtitle: "Book intercity bus seats or post your own route", symbol: "bus.fill", tint: .accentTeal, action: { showBus = true }),
                        FlatRow(title: "Vehicle inspection", subtitle: "Pay a mechanic to inspect a used car before you buy", symbol: "wrench.and.screwdriver.fill", tint: .accentTeal, action: { showVehicleInspection = true }),
                        FlatRow(title: "My vehicles", subtitle: "Track your car's estimated resale value", symbol: "car.fill", tint: .accentTeal, action: { showVehicleValuation = true }),
                    ], isExpanded: expandedMenuSection == "Transport", onToggle: { expandedMenuSection = (expandedMenuSection == "Transport") ? nil : "Transport" })
                    CollapsibleFlatSection(title: "Community & trust", rows: [
                        FlatRow(title: "Trust score", subtitle: "How your neighbors see you on Marketplace, Jobs, and Property", symbol: "checkmark.seal.fill", tint: .accentTeal, action: { showTrustScore = true }),
                        FlatRow(title: "Q&A", subtitle: "Ask a question, answer one, get adopted", symbol: "questionmark.circle.fill", tint: .accentPurple, action: { showKnowledge = true }),
                    ], isExpanded: expandedMenuSection == "Community & trust", onToggle: { expandedMenuSection = (expandedMenuSection == "Community & trust") ? nil : "Community & trust" })
                    // Kept last and separately labeled, not blended into the rows above:
                    // these two are role-gated (only assigned cash-agent operators can use
                    // them), same reasoning as Android's identical split.
                    CollapsibleFlatSection(title: "Cash agent tools", rows: [
                        FlatRow(title: "Agent till", subtitle: "For assigned cash-agent operators: cash-in, cash-out, till count", symbol: "storefront.fill", tint: .accentBlue, action: { showAgentOperator = true }),
                        FlatRow(title: "Float marketplace", subtitle: "For assigned cash-agents: offer or request float from nearby agents", symbol: "arrow.left.arrow.right.circle.fill", tint: .accentTeal, action: { showFloatMarketplace = true }),
                    ], isExpanded: expandedMenuSection == "Cash agent tools", onToggle: { expandedMenuSection = (expandedMenuSection == "Cash agent tools") ? nil : "Cash agent tools" })
                    // Everything below is modeled directly on the real Toss Bank
                    // reference screens (see ItundaAppScreen.kt's own identical note),
                    // adapted to Rwanda rails (REG/WASAC/Irembo/RRA, MTN MoMo/Airtel
                    // Money, RSE tickers, RSSB pension) the same way Android already was.
                    // Real fix (2026-08-10, matching Android's own 2026-08-03 fix, never
                    // ported to iOS until now): this whole section was 3 dead taps --
                    // these are real Toss 갈아타기 entry points into the existing loan
                    // refinance flow (LoansScreen's own "Refinance to a lower rate"
                    // button), not a separate feature, so they route to the same real
                    // Loans screen every other loan row on this screen already uses.
                    CollapsibleFlatSection(title: "Switch & save", rows: [
                        FlatRow(title: "Switch your personal loan", trailing: "12% ~ 24%", trailingIsLink: true, symbol: "wallet.pass.fill", tint: .accentBlue, action: { showLoans = true }),
                        FlatRow(title: "Switch your rent deposit loan", trailing: "9% ~ 15%", trailingIsLink: true, symbol: "house.fill", tint: .accentTeal, action: { showLoans = true }),
                        FlatRow(title: "Switch your SME loan", trailing: "11% ~ 22%", trailingIsLink: true, symbol: "storefront.fill", tint: .accentTeal, action: { showLoans = true }),
                    ], isExpanded: expandedMenuSection == "Switch & save", onToggle: { expandedMenuSection = (expandedMenuSection == "Switch & save") ? nil : "Switch & save" })
                }
                Group {
                    // Real fix (2026-08-10, matching Android's 2026-08-03 fix): both rows
                    // point at the same real Card screen "Card" already opens elsewhere on
                    // this screen -- a second, previously-dead entry point into it, not a
                    // separate feature.
                    CollapsibleFlatSection(title: "Cards", rows: [
                        FlatRow(title: "Itunda Card", trailing: "5% back on bills", trailingIsLink: true, symbol: "creditcard.fill", tint: .accentRed, action: { showCard = true }),
                        FlatRow(title: "Virtual card", trailing: "Instant issue", symbol: "creditcard.fill", tint: .accentGray, action: { showCard = true }),
                    ], isExpanded: expandedMenuSection == "Cards", onToggle: { expandedMenuSection = (expandedMenuSection == "Cards") ? nil : "Cards" })
                    CollapsibleFlatSection(title: "Services", rows: [
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
                    ], isExpanded: expandedMenuSection == "Services", onToggle: { expandedMenuSection = (expandedMenuSection == "Services") ? nil : "Services" })
                    // Real fix (2026-08-10, matching Android's 2026-08-03 fix): both rows
                    // are the same real foreign-currency wallet "Mobile plan"/Send & pay's
                    // own routing doesn't cover -- this is its own real screen.
                    CollapsibleFlatSection(title: "Foreign currency", rows: [
                        FlatRow(title: "Foreign currency wallet", trailing: "100% rate preference", trailingIsLink: true, symbol: "wallet.pass.fill", tint: .accentPurple, action: { showForeignCurrency = true }),
                        FlatRow(title: "International transfer", symbol: "dollarsign.circle.fill", tint: .accentBlue, action: { showForeignCurrency = true }),
                    ], isExpanded: expandedMenuSection == "Foreign currency", onToggle: { expandedMenuSection = (expandedMenuSection == "Foreign currency") ? nil : "Foreign currency" })
                    // Real fix (2026-08-10, matching Android's 2026-08-03 fix): all 4 rows
                    // are the same real RSE investing screen ("Invest" quick link already
                    // opens) -- routed there instead of sitting dead.
                    CollapsibleFlatSection(title: "Grow your money", rows: [
                        FlatRow(title: "RSE stocks", subtitle: "BOK, MTNR, BLR, IMR, CMR, EQTY", symbol: "chart.line.uptrend.xyaxis", tint: .accentTeal, action: { showInvest = true }),
                        FlatRow(title: "Bonds & fixed income", trailing: "7.5% ~ 12%", trailingIsLink: true, symbol: "building.columns.fill", tint: .accentBlue, action: { showInvest = true }),
                        FlatRow(title: "IPO schedule", symbol: "chart.line.uptrend.xyaxis", tint: .accentRed, action: { showInvest = true }),
                        FlatRow(title: "Brokerage account", trailing: "Up to 30,000 RWF", trailingIsLink: true, symbol: "building.columns.fill", tint: .accentTeal, action: { showInvest = true }),
                    ], isExpanded: expandedMenuSection == "Grow your money", onToggle: { expandedMenuSection = (expandedMenuSection == "Grow your money") ? nil : "Grow your money" })
                    CollapsibleFlatSection(title: "Pension", rows: [
                        FlatRow(title: "Check my RSSB pension", symbol: "building.columns.fill", tint: .accentBlue),
                        FlatRow(title: "Pension products", symbol: "percent", tint: .accentBlue),
                    ], isExpanded: expandedMenuSection == "Pension", onToggle: { expandedMenuSection = (expandedMenuSection == "Pension") ? nil : "Pension" })
                    CollapsibleFlatSection(title: "Loans", rows: [
                        FlatRow(title: "Check my max limit", symbol: "chart.line.uptrend.xyaxis", tint: .accentPurple, action: { showLoans = true }),
                        FlatRow(title: "Personal loan", trailing: "11% ~ 24%", trailingIsLink: true, symbol: "wallet.pass.fill", tint: .accentBlue, action: { showLoans = true }),
                    ], isExpanded: expandedMenuSection == "Loans", onToggle: { expandedMenuSection = (expandedMenuSection == "Loans") ? nil : "Loans" })
                    // Real fix: "Notifications" now opens Settings, which already has a
                    // real notifications list + mark-as-read (SettingsScreen.swift) --
                    // reusing existing real infrastructure instead of leaving this row
                    // dead. The other 3 rows have no real backend/content anywhere in
                    // this codebase (no legal-copy source, no credit-data-usage-policy
                    // endpoint) -- honestly left as plain labels (no chevron, no tap
                    // affordance) rather than a fake destination.
                    CollapsibleFlatSection(title: "Notifications & consent", rows: [
                        FlatRow(title: "Notifications", showChevron: true, action: onOpenSettings),
                        FlatRow(title: "Credit data usage policy"),
                        FlatRow(title: "Privacy policy"),
                        FlatRow(title: "Terms & consent"),
                    ], isExpanded: expandedMenuSection == "Notifications & consent", onToggle: { expandedMenuSection = (expandedMenuSection == "Notifications & consent") ? nil : "Notifications & consent" })
                    // FAQ/Live chat/Call support/Announcements have no real backend
                    // behind them either (confirmed via Android's SupportScreen.kt doc
                    // comment, same real gap on this same screen's Android port) -- same
                    // honest no-chevron treatment. The 2 rows that DO have a real
                    // destination (transaction-ticket support, already shipped
                    // 2026-07-22) keep theirs.
                    CollapsibleFlatSection(title: "Support", rows: [
                        FlatRow(title: "FAQ"),
                        FlatRow(title: "Live chat"),
                        FlatRow(title: "Call support"),
                        FlatRow(title: "Report an issue with a transaction", showChevron: true, action: { showSupport = true }),
                        FlatRow(title: "My support tickets", showChevron: true, action: { showSupport = true }),
                        FlatRow(title: "Announcements"),
                    ], isExpanded: expandedMenuSection == "Support", onToggle: { expandedMenuSection = (expandedMenuSection == "Support") ? nil : "Support" })
                }
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.top, IDS.Layout.screenTop)
            .padding(.bottom, IDS.Layout.sectionSpacing)
            } else {
                VStack(alignment: .leading, spacing: IDS.Layout.sectionSpacing) {
                    IdsAllTopBar(onOpenSettings: onOpenSettings, onOpenMyTab: onOpenMyTab)
                    IdsSearchBar(text: $menuSearchQuery, placeholder: "Search everything else")
                    if matchingSearchSections.isEmpty {
                        Text("No match for \"\(menuSearchQuery.trimmingCharacters(in: .whitespacesAndNewlines))\".")
                            .font(IDS.scaledFont(size: 14, weight: .regular, relativeTo: .body))
                            .foregroundColor(IDS.Colors.textTertiary)
                    } else {
                        ForEach(matchingSearchSections, id: \.title) { section in
                            FlatSection(title: section.title, rows: section.rows)
                        }
                    }
                }
                .padding(.horizontal, IDS.Layout.screenHorizontal)
                .padding(.top, IDS.Layout.screenTop)
                .padding(.bottom, IDS.Layout.sectionSpacing)
            }
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
        .sheet(isPresented: $showTrustScore) {
            TrustScoreScreenView(onBack: { showTrustScore = false })
        }
        .sheet(isPresented: $showAgentOperator) {
            AgentOperatorScreenView(onBack: { showAgentOperator = false })
        }
        .sheet(isPresented: $showFloatMarketplace) {
            FloatMarketplaceScreenView(onBack: { showFloatMarketplace = false })
        }
        .sheet(isPresented: $showVupLoan) {
            VupLoanScreenView(onBack: { showVupLoan = false })
        }
        .sheet(isPresented: $showMotoOwnership) {
            MotoOwnershipScreenView(onBack: { showMotoOwnership = false })
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
        .sheet(isPresented: $showCard) {
            CardScreenView(onBack: { showCard = false })
        }
        .sheet(isPresented: $showGroupAccounts) {
            GroupAccountScreenView(onBack: { showGroupAccounts = false })
        }
        .sheet(isPresented: $showIkimina) {
            IkiminaScreenView(onBack: { showIkimina = false })
        }
        .sheet(isPresented: $showSacco) {
            SaccoScreenView(onBack: { showSacco = false })
        }
        .sheet(isPresented: $showHarvestAdvance) {
            HarvestAdvanceScreenView(onBack: { showHarvestAdvance = false })
        }
        .sheet(isPresented: $showSpending) {
            SpendingScreenView(onBack: { showSpending = false })
        }
        .sheet(isPresented: $showVehicleInspection) {
            VehicleInspectionScreenView(onBack: { showVehicleInspection = false })
        }
        .sheet(isPresented: $showVehicleValuation) {
            VehicleValuationScreenView(onBack: { showVehicleValuation = false })
        }
        .sheet(isPresented: $showFamilyLink) {
            FamilyLinkScreenView(onBack: { showFamilyLink = false })
        }
        .sheet(isPresented: $showSubscriptions) {
            SubscriptionsScreenView(onBack: { showSubscriptions = false })
        }
        .sheet(isPresented: $showRides) {
            RideScreenView(onBack: { showRides = false })
        }
        .sheet(isPresented: $showDesignatedDriver) {
            DesignatedDriverScreenView(onBack: { showDesignatedDriver = false })
        }
        .sheet(isPresented: $showBikeRental) {
            BikeRentalScreenView(onBack: { showBikeRental = false })
        }
        .sheet(isPresented: $showParking) {
            ParkingScreenView(onBack: { showParking = false })
        }
        .sheet(isPresented: $showBus) {
            BusScreenView(onBack: { showBus = false })
        }
        .sheet(isPresented: $showKnowledge) {
            KnowledgeScreenView(onBack: { showKnowledge = false })
        }
        .sheet(isPresented: $showRoundUp) {
            RoundUpSettingsView(onBack: { showRoundUp = false })
        }
        .sheet(isPresented: $showForeignCurrency) {
            ForeignCurrencyScreenView(onBack: { showForeignCurrency = false })
        }
        .sheet(isPresented: $showUpfrontDeposit) {
            UpfrontDepositScreenView(onBack: { showUpfrontDeposit = false })
        }
        .sheet(isPresented: $showAutoTopUp) {
            AutoTopUpScreenView(onBack: { showAutoTopUp = false })
        }
        .sheet(isPresented: $showRequestMoney) {
            RequestMoneyScreenView(onBack: { showRequestMoney = false })
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
    // Real 쿠팡파트너스 (Coupang Partners)-style affiliate earnings read-back (item 229)
    // -- link creation itself happens inline on the Shop product card's Share icon;
    // this is purely the read-back, mirroring bank-mfe's own AffiliateEarningsCard and
    // Android's own port.
    @State private var affiliateLinks: [AffiliateLinkDto] = []
    @State private var affiliateCommissions: [AffiliateCommissionDto] = []

    var body: some View {
        ScrollView {
            VStack(spacing: IDS.Layout.sectionSpacing) {
                HStack {
                    Button(action: onBack) { Image(systemName: "chevron.left").foregroundColor(IDS.Colors.textPrimary) }.accessibilityLabel("Back")
                    Spacer()
                    Text("My").font(IDS.scaledFont(size: 20, weight: .bold, relativeTo: .title2)).foregroundColor(IDS.Colors.textPrimary)
                    Spacer()
                    Color.clear.frame(width: 20)
                }
                ProfilePhotoCard()
                VerificationCard()
                if !affiliateLinks.isEmpty {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Partner earnings").font(IDS.scaledFont(size: 15, weight: .bold, relativeTo: .subheadline)).foregroundColor(IDS.Colors.textPrimary)
                        Text("Earn 3% on any purchase made through a product link you've shared.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        HStack {
                            Text("Links shared").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            Spacer()
                            Text("\(affiliateLinks.count)").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                        }
                        HStack {
                            Text("Total clicks").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            Spacer()
                            Text("\(affiliateLinks.reduce(0) { $0 + $1.clickCount })").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                        }
                        HStack {
                            Text("Total earned").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            Spacer()
                            Text("\(Int(affiliateCommissions.reduce(0) { $0 + $1.commissionAmount })) RWF").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                        }
                    }
                    .padding(14).frame(maxWidth: .infinity, alignment: .leading)
                    .background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
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
            if let res = try? await NetworkClient.shared.getMyAffiliateLinks() { affiliateLinks = res.links }
            if let res = try? await NetworkClient.shared.getMyAffiliateCommissions() { affiliateCommissions = res.commissions }
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

// Real email/phone verification (item 169/179) -- see NetworkClient.swift's own doc
// comment. bank-mfe (item 169) and Android (item 178) already have this; this is the
// iOS port. A real code is delivered via a real in-app Notification + push, no real
// SMS/email gateway exists.
// Real profile photo (URL, not a binary upload) -- also the real, buildable half of
// Rewards' task_profile. Found 2026-07-29 via a full-backend-endpoint sweep: a real,
// working `PUT /api/v1/auth/profile/photo` endpoint with zero client anywhere, and
// `PublicUser.profilePhotoUrl` wasn't even carried by this DTO until now.
private struct ProfilePhotoCard: View {
    @State private var profilePhotoUrl: String?
    @State private var urlInput = ""
    @State private var saving = false
    @State private var error: String?

    var body: some View {
        HStack(spacing: 12) {
            if let profilePhotoUrl, let url = URL(string: profilePhotoUrl) {
                AsyncImage(url: url) { image in
                    image.resizable().aspectRatio(contentMode: .fill)
                } placeholder: {
                    Circle().fill(IDS.Colors.chipBackground)
                }
                .frame(width: 56, height: 56)
                .clipShape(Circle())
            } else {
                Circle().fill(IDS.Colors.chipBackground).frame(width: 56, height: 56)
            }
            VStack(alignment: .leading, spacing: 6) {
                if let error { Text(error).font(.caption).foregroundColor(.red) }
                TextField("Profile photo URL", text: $urlInput)
                    .padding(8).background(IDS.Colors.chipBackground).cornerRadius(8)
                Button(action: { Task { await save() } }) {
                    Text(saving ? "Saving…" : "Save photo").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                        .padding(.horizontal, 10).padding(.vertical, 6)
                        .background(IDS.Colors.card).cornerRadius(10)
                }
                .disabled(saving)
            }
        }
        .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
        .task {
            do {
                let user = try await NetworkClient.shared.getProfile().user
                profilePhotoUrl = user.profilePhotoUrl
                urlInput = user.profilePhotoUrl ?? ""
            } catch {
                // Real, non-critical -- the rest of "My" still works without this.
            }
        }
    }

    private func save() async {
        let trimmed = urlInput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { error = "Enter a photo URL."; return }
        saving = true; error = nil
        defer { saving = false }
        do {
            profilePhotoUrl = try await NetworkClient.shared.updateProfilePhoto(profilePhotoUrl: trimmed).user.profilePhotoUrl
        } catch {
            self.error = "Could not update your profile photo."
        }
    }
}

private struct VerificationCard: View {
    @State private var email: String?
    @State private var emailVerified = true
    @State private var phoneVerified = true
    @State private var loaded = false

    private func load() async {
        do {
            let user = try await NetworkClient.shared.getProfile().user
            email = user.email
            emailVerified = user.emailVerified ?? true
            phoneVerified = user.phoneVerified ?? true
        } catch {
            // Best-effort, matching this card's own bank-mfe/Android precedent.
        }
        loaded = true
    }

    var body: some View {
        Group {
            if loaded && !(emailVerified && phoneVerified) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Verify your account").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    if !phoneVerified { VerificationRow(kind: "phone", hasEmail: true, onVerified: { Task { await load() } }) }
                    if !emailVerified { VerificationRow(kind: "email", hasEmail: email != nil, onVerified: { Task { await load() } }) }
                }
                .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
            }
        }
        .task { await load() }
    }
}

private struct VerificationRow: View {
    let kind: String
    let hasEmail: Bool
    let onVerified: () -> Void

    @State private var sent = false
    @State private var code = ""
    @State private var busy = false
    @State private var error: String?
    @FocusState private var codeFieldFocused: Bool

    var body: some View {
        if kind == "email" && !hasEmail {
            Text("No email address on file to verify.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
        } else {
            VStack(alignment: .leading, spacing: 4) {
                if !sent {
                    HStack {
                        Text(kind == "email" ? "Email not verified" : "Phone number not verified").font(.caption).foregroundColor(IDS.Colors.textPrimary)
                        Spacer()
                        Button(action: { Task { await send() } }) {
                            Text(busy ? "…" : "Send code").font(.caption).bold().foregroundColor(.white)
                                .padding(.horizontal, 10).padding(.vertical, 6)
                                .background(busy ? IDS.Colors.textTertiary : IDS.Colors.brand).cornerRadius(8)
                        }
                        .disabled(busy)
                    }
                } else {
                    HStack(spacing: 8) {
                        TextField("Enter code", text: $code)
                            .padding(10).background(IDS.Colors.chipBackground).cornerRadius(8)
                            .keyboardType(.numberPad)
                            .focused($codeFieldFocused)
                            // Real "Minimum Input" simplicity fix, closing
                            // docs/DESIGN_REFERENCES.md §11 recommendation #2's iOS gap
                            // (rule #4: auto-focus so the keyboard appears without an extra
                            // tap, matching web's already-shipped autoFocus on this exact
                            // field). Triggered once, right when the code field first
                            // appears (the moment "sent" flips true), not on every
                            // recomposition.
                            .onAppear { codeFieldFocused = true }
                            // Real "Minimum Input" simplicity fix (Toss's own researched,
                            // sourced pattern -- toss.tech/article/4-ways-for-minimum-input,
                            // rule #2: "for fixed-digit fields like ID or phone numbers, the
                            // CTA button becomes unnecessary" -- see
                            // docs/DESIGN_REFERENCES.md §11). This code is a real, fixed
                            // 6-digit OTP (AuthService.kt's own doc comment). Auto-confirms
                            // the instant the 6th digit is typed; the button stays visible as
                            // a manual fallback rather than being removed outright, since this
                            // is a security-sensitive identity-verification step.
                            .onChange(of: code) { newValue in
                                let trimmed = newValue.trimmingCharacters(in: .whitespaces)
                                if trimmed.count == 6 && trimmed.allSatisfy({ $0.isNumber }) && !busy {
                                    Task { await confirm() }
                                }
                            }
                        Button(action: { Task { await confirm() } }) {
                            Text(busy ? "…" : "Confirm").font(.caption).bold().foregroundColor(.white)
                                .padding(.horizontal, 12).padding(.vertical, 10)
                                .background((busy || code.trimmingCharacters(in: .whitespaces).isEmpty) ? IDS.Colors.textTertiary : IDS.Colors.brand).cornerRadius(8)
                        }
                        .disabled(busy || code.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                }
                if let error {
                    Text(error).font(.caption).foregroundColor(.red)
                }
            }
            .padding(.vertical, 6)
        }
    }

    private func send() async {
        busy = true
        error = nil
        defer { busy = false }
        do {
            if kind == "email" {
                _ = try await NetworkClient.shared.requestEmailVerification()
            } else {
                _ = try await NetworkClient.shared.requestPhoneVerification()
            }
            sent = true
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func confirm() async {
        busy = true
        error = nil
        defer { busy = false }
        do {
            if kind == "email" {
                _ = try await NetworkClient.shared.confirmEmailVerification(token: code.trimmingCharacters(in: .whitespaces))
            } else {
                _ = try await NetworkClient.shared.confirmPhoneVerification(code: code.trimmingCharacters(in: .whitespaces))
            }
            onVerified()
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
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

// Real fix (2026-08-10, matching Android's identical fix to ItundaAppScreen.kt's
// own SearchBar): this was pure decoration -- a Text label, no TextField, nothing
// typed into it ever did anything. Worse than no search bar at all: it promised a
// feature that wasn't there. Now a real bound text field; EntireMenuScreen wires it
// to actually filter every FlatSection row by title.
struct IdsSearchBar: View {
    @Binding var text: String
    let placeholder: String
    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass")
                .foregroundColor(IDS.Colors.textTertiary)
                .font(IDS.scaledFont(size: 15, weight: .regular, relativeTo: .body))
            TextField(placeholder, text: $text)
                .font(IDS.scaledFont(size: 16, weight: .regular, relativeTo: .body))
                .foregroundColor(IDS.Colors.textPrimary)
            if !text.isEmpty {
                Button(action: { text = "" }) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(IDS.Colors.textTertiary)
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
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
                    } else if row.showChevron && row.action != nil {
                        Image(systemName: "chevron.right")
                            .foregroundColor(IDS.Colors.textTertiary)
                    }
                }
                .padding(.vertical, 10)
                .contentShape(Rectangle())
                // Real dead-tap fix (item 247 follow-up, docs/DESIGN_REFERENCES.md §14
                // recommendation #2): the chevron/button-trait affordance below used to
                // render for rows with no real action too (Notifications/Privacy policy/
                // FAQ/etc. -- confirmed via Android's SupportScreen.kt doc comment that
                // no backend exists for any of them, same real gap on this screen's
                // Android port), matching Android's identical FlatRow bug fixed same day.
                // `row.action?()` is already a safe no-op with no visible pressed-state
                // in SwiftUI, so what actually misled a user was the chevron/trait, not
                // the gesture recognizer itself -- gating those two is the real fix.
                .onTapGesture { row.action?() }
                .accessibilityAddTraits(row.action != nil ? [.isButton] : [])
            }
        }
    }
}

// Real fix (2026-08-10): matches Android's identical fix to ItundaAppScreen.kt (see
// its own CollapsibleFlatSection doc comment for the full Hick's Law citation) --
// EntireMenuScreen showed every one of its non-"Quick links"/"Mini apps" categories
// fully expanded, always. Collapsed by default, one open at a time, real item count
// in the header so collapsing doesn't hide that the content exists. Row rendering
// below is a literal copy of FlatSection's own body -- only the header gained a tap
// target and a chevron.up/chevron.down icon, and the rows are wrapped in `if isExpanded`.
struct CollapsibleFlatSection: View {
    let title: String
    let rows: [FlatRow]
    let isExpanded: Bool
    let onToggle: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("\(title)  ·  \(rows.count)")
                    .font(IDS.scaledFont(size: 19, weight: .bold, relativeTo: .title2))
                    .foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                    .foregroundColor(IDS.Colors.textTertiary)
            }
            .padding(.bottom, isExpanded ? 6 : 0)
            .contentShape(Rectangle())
            .onTapGesture(perform: onToggle)
            if isExpanded {
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
                        } else if row.showChevron && row.action != nil {
                            Image(systemName: "chevron.right")
                                .foregroundColor(IDS.Colors.textTertiary)
                        }
                    }
                    .padding(.vertical, 10)
                    .contentShape(Rectangle())
                    .onTapGesture { row.action?() }
                    .accessibilityAddTraits(row.action != nil ? [.isButton] : [])
                }
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
