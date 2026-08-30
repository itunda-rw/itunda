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
import FeatureMaps
import FeatureAssets
import FeatureCertificate
import FeatureIdentity
import FeatureSupport

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

// Real fix (2026-08-13, matching the identical Android fix same day, direct user
// report: "entire app is still messy... give me something real"): BenefitsScreen/
// PromoBannerCard/BenefitsVisitCard/CashbackChanceCard used to live here -- a full
// screen of entirely fabricated content (fake "P 137" points, a fake "Limited gift
// for Rwanda / 25,000" banner, 4 dead "Visit" buttons with zero real backend, and a
// fake "3 chances to get money back / BK account -> TUYIZERE Eric" card). Removed;
// the "Benefits" row below now opens the real Reward tasks mini-app instead.

// DiscoverScreen (the old "Shop" tab -- Toss-Shopping-cashback-style browsing) removed
// 2026-07-18: the new Shop tab is real Coupang-style commerce (see ShopScreen.swift),
// replacing this entirely, same cleanup Android's ItundaAppScreen.kt went through when
// its own old ShopTab (and ShopTopBar/CategoryTabsRow/ShopPromoCard/PointActionsCard)
// were deleted for the same reason.

// MARK: - Explore tab

// Real Explore primary bottom tab (renamed 2026-08-10 from All -- see
// ContentView.swift's own doc comment for the full history: My is its own primary
// tab now, ItundaTab.You, not reached via a profile icon here anymore). Mirrors
// Android's MenuScreen exactly (see ItundaAppScreen.kt's own doc comment on
// ItundaTab for the full history). Shop/Eats/Marketplace/Community/Jobs are real
// flat rows here (2026-08-10) since they lost their own primary tabs; Property
// already had its own Shortcuts-grid entry.
struct EntireMenuScreen: View {
    var onOpenSettings: () -> Void = {}
    var onClaimInterest: () -> Void = {}
    var onSwitchToTalk: () -> Void = {}
    var onOpenTransferHub: () -> Void = {}
    var onOpenShop: () -> Void = {}
    var onOpenEats: () -> Void = {}
    var onOpenMarketplace: () -> Void = {}
    var onOpenCommunity: () -> Void = {}
    var onOpenJobs: () -> Void = {}
    var onOpenProperty: () -> Void = {}
    // Real architectural fix (2026-08-13, matching the identical Android/web fix same
    // session, direct user directive): "itunda bank is a complete product... tabs are
    // not products, are just access points." BankView used to render directly as
    // ContentView's own Home tab (tag 0) -- real Bank-product content baked into what's
    // meant to be a generic access point. Moved to a real destination reachable from
    // here instead, wired to the "Bank" icon below (Quick access) which was previously
    // a dead tap (IconGridSection's onItemClick was never passed for this section).
    var onOpenBank: () -> Void = {}

    // Real granite mini-app launch, closing this file's own "MiniAppsSection... plain,
    // non-functional list rows" gap for real -- the CocoaPods/Tuist bridge plus the real
    // Fabric root-cause fix (see docs/ARCHITECTURE.md's mini-app host row, 2026-07-16→17)
    // makes a real RN root view presentable here. `pay-bills` proved the host out first,
    // independently verified (`SaroniteMiniAppLiveTest`); `account-balance`/`reward-tasks`/
    // `insurance` followed the same day once the host itself was proven real -- same
    // one-first-then-the-rest rollout Android itself did (2026-07-13→14).
    @State private var showPayBillsMiniApp = false
    @State private var showAccountBalanceMiniApp = false
    @State private var showRewardTasksMiniApp = false
    @State private var showInsuranceMiniApp = false
    // Shop/Eats lost their own primary tab the same day (2026-08-10, real user
    // correction: nesting them behind one row with an internal Shop/Eats toggle --
    // ShopScreen's own retired Picker -- is noise a flat catalog shouldn't have).
    // Self-contained local sheets here, not routed through ContentView, since
    // neither needs the cross-tab "message seller -> Talk" hop Marketplace/
    // Community/Jobs/Property do (hence those four stay ContentView-level callbacks,
    // matching onOpenProperty's own existing pattern).
    @State private var showShop = false
    @State private var showEats = false
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
    // Real Toss Bank 키워봐요 31일적금 (2026-08-15) -- last remaining client platform
    // for this feature (Android since 2026-08-12, bank-mfe since earlier the same
    // session as this).
    @State private var showGrow31Savings = false
    // Real KakaoBank mini-style capped starter account (2026-07-28, item 101) -- last
    // remaining client platform for this feature (bank-mfe/Android already have it).
    @State private var showYouthAccount = false
    // Real Toss Bank 체크카드 (check/debit card) screen (2026-07-31, item 207) --
    // bank-mfe/Android shipped first; this is the last remaining client platform.
    @State private var showCard = false
    @State private var showTransit = false
    @State private var showTransitCollect = false
    @State private var showMotoFareCollect = false
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
            // Real IA fix (2026-08-30, simplification-adoption thread, toss.tech/
            // article/uxresearcher-cardsorting-core): Toss's own card-sorting research
            // found users reject a flat list of many services, grouping instead by
            // real-world context -- this exact 17-row "Quick links" list was the
            // flat-list anti-pattern the article's research replaced, while bank-mfe's
            // own EXPLORE_TAB_GROUPS had already solved the identical problem for the
            // identical features (never ported to native, matching the Android fix
            // same day). Split into 3 sub-lists reusing web's own already-validated
            // category names/membership, not a newly-invented taxonomy.
            ("Everyday", [
                FlatRow(title: "Shop", subtitle: "Coupang-style commerce", glyph: { AnyView(ShoppingBagGlyph(size: 28)) }, action: { showShop = true }),
                FlatRow(title: "Eats", subtitle: "Food delivery, order or deliver", glyph: { AnyView(PlaceGlyph(category: "RESTAURANT", size: 28)) }, action: { showEats = true }),
                // Real Kigali public-transit stored-value balance (2026-08-27) -- see
                // TransitScreenView.swift's own doc comment for the full sourced account.
                FlatRow(title: "Transit", subtitle: "Top up and tap to pay your real Kigali bus fare", symbol: "bus.fill", tint: Color(hex: 0x2F8F5B), action: { showTransit = true }),
                FlatRow(title: "Map", subtitle: "Real Rwanda map, self-hosted", glyph: { AnyView(PinGlyph(size: 28)) }, action: { showMap = true }),
            ]),
            ("Your neighbourhood", [
                FlatRow(title: "Marketplace", subtitle: "당근마켓-style neighborhood buy/sell", glyph: { AnyView(PlaceGlyph(category: "MARKET", size: 28)) }, action: onOpenMarketplace),
                FlatRow(title: "Community", subtitle: "Neighborhood life, local questions and posts", glyph: { AnyView(SpeechBubbleGlyph(size: 28)) }, action: onOpenCommunity),
                FlatRow(title: "Jobs", subtitle: "Neighborhood gigs and part-time work", glyph: { AnyView(BriefcaseGlyph(size: 28)) }, action: onOpenJobs),
                FlatRow(title: "Property", subtitle: "Neighborhood rentals and sales", glyph: { AnyView(TravelHouse(size: 28)) }, action: onOpenProperty),
            ]),
            ("Money tools", [
                // Real fix (2026-08-13, matching the identical Android fix same day, direct
                // user report: "entire app is still messy... give me something
                // real"): used to open BenefitsShopAllScreens' own BenefitsView, a
                // full screen of entirely fabricated content -- a fake points pill, a
                // fake "Limited gift for Rwanda / 25,000" banner, dead "Visit"
                // buttons, and a fake "3 chances to get money back / BK account ->
                // TUYIZERE Eric" card. This row's own subtitle ("Points, coupons,
                // rewards") already describes exactly what the real "Reward tasks"
                // mini-app does -- points there instead of a second, fake destination.
                FlatRow(title: "Benefits", subtitle: "Points, coupons, rewards", glyph: { AnyView(GiftBox(size: 28)) }, action: { showRewardTasksMiniApp = true }),
                FlatRow(title: "Invest", subtitle: "RSE stocks, real portfolio", glyph: { AnyView(ChartIncreasingGlyph(size: 28)) }, action: { showInvest = true }),
                FlatRow(title: "26-Week Savings", subtitle: "Escalating auto-save, streak bonus", glyph: { AnyView(MoneyBagGlyph(size: 28)) }, action: { showWeeklySavings = true }),
                FlatRow(title: "31-Day Savings", subtitle: "Daily save, streak-tiered bonus rate", glyph: { AnyView(MoneyBagGlyph(size: 28)) }, action: { showGrow31Savings = true }),
                FlatRow(title: "Youth account", subtitle: "Capped starter account, ages 7-18", glyph: { AnyView(ChildGlyph(size: 28)) }, action: { showYouthAccount = true }),
                FlatRow(title: "Card", subtitle: "App-controlled spend limits, one-tap freeze", glyph: { AnyView(ObjectCreditCard(size: 28)) }, action: { showCard = true }),
                FlatRow(title: "Group account", subtitle: "Shared account with dues and split expenses", glyph: { AnyView(HandshakeGlyph(size: 28)) }, action: { showGroupAccounts = true }),
                FlatRow(title: "Ikimina", subtitle: "Rotating savings group -- everyone takes a turn", glyph: { AnyView(HandshakeGlyph(size: 28)) }, action: { showIkimina = true }),
                FlatRow(title: "SACCO shares", subtitle: "Buy cooperative shares, earn a real dividend", glyph: { AnyView(PlaceGlyph(category: "BANK", size: 28)) }, action: { showSacco = true }),
                FlatRow(title: "Harvest advance", subtitle: "Coffee cooperative input financing", glyph: { AnyView(SeedlingGlyph(size: 28)) }, action: { showHarvestAdvance = true }),
                // Real icon-clash fix (2026-08-24), same bug class as the Android
                // MenuScreen fix this session (DESIGN_REFERENCES.md Section 65/193's
                // "richer, distinct per-product iconography" principle) -- this row
                // shared the identical banknote.fill symbol with "Youth account" above.
                // "checkmark.shield.fill" mirrors Android's own real choice for this
                // exact row (IdsIcons.ShieldCheck) semantically (a government-backed,
                // verified program), without widening FlatRow's `symbol: String?` API
                // to accept a custom Shape -- a bigger, separately-scoped change.
                FlatRow(title: "VUP Financial Services", subtitle: "Means-tested government microloan for farming, livestock, business", glyph: { AnyView(ShieldEmojiGlyph(size: 28)) }, action: { showVupLoan = true }),
            ]),
            ("Mini apps", [
                FlatRow(title: "Account balance", showChevron: true, action: { showAccountBalanceMiniApp = true }),
                FlatRow(title: "Pay bills", showChevron: true, action: { showPayBillsMiniApp = true }),
                FlatRow(title: "Reward tasks", showChevron: true, action: { showRewardTasksMiniApp = true }),
                FlatRow(title: "Insurance", showChevron: true, action: { showInsuranceMiniApp = true }),
            ]),
            ("Accounts & cards", [
                FlatRow(title: "Open account", subtitle: "Itunda Account, other banks, RSE brokerage", glyph: { AnyView(PlaceGlyph(category: "BANK", size: 28)) }, action: { showOverview = true }),
                FlatRow(title: "My assets", subtitle: "Accounts, loans, RSE holdings, cards, points", glyph: { AnyView(MoneyBagGlyph(size: 28)) }, action: { showOverview = true }),
                FlatRow(title: "Spending", subtitle: "Real, ledger-based category breakdown", glyph: { AnyView(BarChartGlyph(size: 28)) }, action: { showSpending = true }),
                FlatRow(title: "Family", subtitle: "Link a guardian or child, view read-only spending", glyph: { AnyView(FamilyGlyph(size: 28)) }, action: { showFamilyLink = true }),
                FlatRow(title: "Subscriptions", subtitle: "Detected recurring payments + merchant billing plans", glyph: { AnyView(CalendarGlyph(size: 28)) }, action: { showSubscriptions = true }),
                FlatRow(title: "Digital certificate", subtitle: "Sign agreements in Itunda", glyph: { AnyView(ObjectPen(size: 28)) }, action: { showCertificate = true }),
            ]),
            ("Send & pay", [
                FlatRow(title: "Transfer", subtitle: "Auto-transfer, split a bill", symbol: "paperplane.fill", tint: .accentIndigo, action: onOpenTransferHub),
                FlatRow(title: "Request money", subtitle: "Generate a real payment request code", glyph: { AnyView(ReceiptGlyph(size: 28)) }, action: { showRequestMoney = true }),
                FlatRow(title: "Auto top-up", subtitle: "Refill your account automatically from a linked account", glyph: { AnyView(RefreshCardGlyph(size: 28)) }, action: { showAutoTopUp = true }),
                FlatRow(title: "Mobile plan", subtitle: "MTN, Airtel, broadband", glyph: { AnyView(ObjectMobilePhone(size: 28)) }, action: { showPayBillsMiniApp = true }),
            ]),
            ("Save & grow", [
                FlatRow(title: "Round-up savings", subtitle: "Auto-save spare change from every transfer", symbol: "arrow.up.circle.fill", tint: .accentOrange, action: { showRoundUp = true }),
                FlatRow(title: "12-month deposit", subtitle: "Interest paid upfront, principal locked 12 months", glyph: { AnyView(LockGlyph(size: 28)) }, action: { showUpfrontDeposit = true }),
            ]),
            ("Borrow", [
                FlatRow(title: "Get a loan", subtitle: "Personal, salary-backed, SME working capital", glyph: { AnyView(MoneyBagGlyph(size: 28)) }, action: { showLoans = true }),
                FlatRow(title: "Credit score", subtitle: "Free check, alternative data", glyph: { AnyView(NatureGlowingStar(size: 28)) }, action: { showCreditScore = true }),
                FlatRow(title: "Moto-Taxi Ownership", subtitle: "Save a 30% down payment, then convert to a loan for your own bike", symbol: "bicycle", tint: .accentTeal, action: { showMotoOwnership = true }),
            ]),
            ("Transport", [
                FlatRow(title: "Rides", subtitle: "Request a ride or drive for real fares", glyph: { AnyView(TravelCar(size: 28)) }, action: { showRides = true }),
                FlatRow(title: "Designated driver", subtitle: "A driver takes you and your own car home", glyph: { AnyView(ObjectKey(size: 28)) }, action: { showDesignatedDriver = true }),
                FlatRow(title: "Bike rental", subtitle: "Rent a nearby bike or scooter, billed by the minute", glyph: { AnyView(BikeGlyph(size: 28)) }, action: { showBikeRental = true }),
                FlatRow(title: "Parking", subtitle: "Rent a nearby parking spot, billed by the hour", glyph: { AnyView(ParkingGlyph(size: 28)) }, action: { showParking = true }),
                FlatRow(title: "Bus", subtitle: "Book intercity bus seats or post your own route", glyph: { AnyView(PlaceGlyph(category: "BUS_STOP", size: 28)) }, action: { showBus = true }),
                // Real "tap to pay your moto-taxi fare" (2026-08-27, direct user
                // follow-up: "now we can make pay for tax and moto as well") -- see
                // MotoFareCollectScreenView.swift's own doc comment.
                FlatRow(title: "Collect a moto fare", subtitle: "Drivers: tap or scan a rider's code to collect a real fare", symbol: "bicycle", tint: Color(hex: 0xD97706), action: { showMotoFareCollect = true }),
                FlatRow(title: "Vehicle inspection", subtitle: "Pay a mechanic to inspect a used car before you buy", glyph: { AnyView(WrenchGlyph(size: 28)) }, action: { showVehicleInspection = true }),
                FlatRow(title: "My vehicles", subtitle: "Track your car's estimated resale value", glyph: { AnyView(TravelCar(size: 28)) }, action: { showVehicleValuation = true }),
            ]),
            ("Community & trust", [
                FlatRow(title: "Trust score", subtitle: "How your neighbors see you on Marketplace, Jobs, and Property", glyph: { AnyView(NatureStar(size: 28)) }, action: { showTrustScore = true }),
                FlatRow(title: "Q&A", subtitle: "Ask a question, answer one, get adopted", glyph: { AnyView(SpeechBubbleGlyph(size: 28)) }, action: { showKnowledge = true }),
            ]),
            ("Cash agent tools", [
                FlatRow(title: "Agent till", subtitle: "For assigned cash-agent operators: cash-in, cash-out, till count", glyph: { AnyView(PlaceGlyph(category: "ITUNDA_AGENT", size: 28)) }, action: { showAgentOperator = true }),
                FlatRow(title: "Float marketplace", subtitle: "For assigned cash-agents: offer or request float from nearby agents", glyph: { AnyView(PlaceGlyph(category: "MARKET", size: 28)) }, action: { showFloatMarketplace = true }),
            ]),
            ("Switch & save", [
                FlatRow(title: "Switch your personal loan", trailing: "12% ~ 24%", trailingIsLink: true, glyph: { AnyView(MoneyBagGlyph(size: 28)) }, action: { showLoans = true }),
                FlatRow(title: "Switch your rent deposit loan", trailing: "9% ~ 15%", trailingIsLink: true, glyph: { AnyView(TravelHouse(size: 28)) }, action: { showLoans = true }),
                FlatRow(title: "Switch your SME loan", trailing: "11% ~ 22%", trailingIsLink: true, glyph: { AnyView(PlaceGlyph(category: "MARKET", size: 28)) }, action: { showLoans = true }),
            ]),
            ("Cards", [
                FlatRow(title: "Itunda Card", trailing: "5% back on bills", trailingIsLink: true, glyph: { AnyView(ObjectCreditCard(size: 28)) }, action: { showCard = true }),
                FlatRow(title: "Virtual card", trailing: "Instant issue", glyph: { AnyView(ObjectCreditCard(size: 28)) }, action: { showCard = true }),
            ]),
            ("Services", [
                FlatRow(title: "Rent deposit protection", glyph: { AnyView(TravelHouse(size: 28)) }),
                FlatRow(title: "Recurring payments", glyph: { AnyView(CalendarGlyph(size: 28)) }),
                FlatRow(title: "Import recurring payments", symbol: "shippingbox.fill", tint: .accentGray),
                FlatRow(title: "REG & WASAC bills", glyph: { AnyView(ObjectLightBulb(size: 28)) }, action: { showPayBillsMiniApp = true }),
                FlatRow(title: "Interest earned this month", glyph: { AnyView(MoneyBagGlyph(size: 28)) }, action: onClaimInterest),
                FlatRow(title: "SME income tax estimate", glyph: { AnyView(ReceiptGlyph(size: 28)) }),
                FlatRow(title: "Split a bill with friends", glyph: { AnyView(SplitBillDice(size: 28)) }, action: onSwitchToTalk),
                FlatRow(title: "Shared calendar", glyph: { AnyView(CalendarGlyph(size: 28)) }),
                FlatRow(title: "Kids' allowance tasks", glyph: { AnyView(ChildGlyph(size: 28)) }),
            ]),
            ("Foreign currency", [
                FlatRow(title: "Foreign currency account", trailing: "100% rate preference", trailingIsLink: true, glyph: { AnyView(GlobeGlyph(size: 28)) }, action: { showForeignCurrency = true }),
                FlatRow(title: "International transfer", glyph: { AnyView(GlobeGlyph(size: 28)) }, action: { showForeignCurrency = true }),
            ]),
            ("Grow your money", [
                FlatRow(title: "RSE stocks", subtitle: "BOK, MTNR, BLR, IMR, CMR, EQTY", glyph: { AnyView(ChartIncreasingGlyph(size: 28)) }, action: { showInvest = true }),
                FlatRow(title: "Bonds & fixed income", trailing: "7.5% ~ 12%", trailingIsLink: true, glyph: { AnyView(PlaceGlyph(category: "BANK", size: 28)) }, action: { showInvest = true }),
                FlatRow(title: "IPO schedule", glyph: { AnyView(ChartIncreasingGlyph(size: 28)) }, action: { showInvest = true }),
                FlatRow(title: "Brokerage account", trailing: "Up to 30,000 RWF", trailingIsLink: true, glyph: { AnyView(PlaceGlyph(category: "BANK", size: 28)) }, action: { showInvest = true }),
            ]),
            ("Pension", [
                FlatRow(title: "Check my RSSB pension", glyph: { AnyView(PlaceGlyph(category: "BANK", size: 28)) }),
                FlatRow(title: "Pension products", glyph: { AnyView(MoneyBagGlyph(size: 28)) }),
            ]),
            ("Loans", [
                FlatRow(title: "Check my max limit", glyph: { AnyView(ChartIncreasingGlyph(size: 28)) }, action: { showLoans = true }),
                FlatRow(title: "Personal loan", trailing: "11% ~ 24%", trailingIsLink: true, glyph: { AnyView(MoneyBagGlyph(size: 28)) }, action: { showLoans = true }),
            ]),
            ("Notifications & consent", [
                FlatRow(title: "Notifications", showChevron: true, action: onOpenSettings),
                FlatRow(title: "Credit data usage policy"),
                FlatRow(title: "Privacy policy"),
                FlatRow(title: "Terms & consent"),
            ]),
            ("Support", [
                FlatRow(title: "FAQ", glyph: { AnyView(QuestionGlyph(size: 28)) }),
                FlatRow(title: "Live chat", glyph: { AnyView(SpeechBubbleGlyph(size: 28)) }),
                FlatRow(title: "Call support", glyph: { AnyView(ObjectMobilePhone(size: 28)) }),
                FlatRow(title: "Report an issue with a transaction", glyph: { AnyView(WarningGlyph(size: 28)) }, showChevron: true, action: { showSupport = true }),
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
                    // there's nothing to back out to. My's own real content is its own
                    // primary tab now (ItundaTab.You), not reached from here.
                    IdsAllTopBar(onOpenSettings: onOpenSettings)
                    // Real IA fix (2026-08-30) -- see searchableMenuSections's own doc
                    // comment above (this Group's literal duplicate) for the full
                    // account; keep both copies in lockstep.
                    FlatSection(title: "Everyday", rows: [
                        FlatRow(title: "Shop", subtitle: "Coupang-style commerce", glyph: { AnyView(ShoppingBagGlyph(size: 28)) }, action: { showShop = true }),
                        FlatRow(title: "Eats", subtitle: "Food delivery, order or deliver", glyph: { AnyView(PlaceGlyph(category: "RESTAURANT", size: 28)) }, action: { showEats = true }),
                        FlatRow(title: "Transit", subtitle: "Top up and tap to pay your real Kigali bus fare", symbol: "bus.fill", tint: Color(hex: 0x2F8F5B), action: { showTransit = true }),
                        FlatRow(title: "Map", subtitle: "Real Rwanda map, self-hosted", glyph: { AnyView(PinGlyph(size: 28)) }, action: { showMap = true }),
                    ])
                    FlatSection(title: "Your neighbourhood", rows: [
                        FlatRow(title: "Marketplace", subtitle: "당근마켓-style neighborhood buy/sell", glyph: { AnyView(PlaceGlyph(category: "MARKET", size: 28)) }, action: onOpenMarketplace),
                        FlatRow(title: "Community", subtitle: "Neighborhood life, local questions and posts", glyph: { AnyView(SpeechBubbleGlyph(size: 28)) }, action: onOpenCommunity),
                        FlatRow(title: "Jobs", subtitle: "Neighborhood gigs and part-time work", glyph: { AnyView(BriefcaseGlyph(size: 28)) }, action: onOpenJobs),
                        FlatRow(title: "Property", subtitle: "Neighborhood rentals and sales", glyph: { AnyView(TravelHouse(size: 28)) }, action: onOpenProperty),
                    ])
                    FlatSection(title: "Money tools", rows: [
                        // Real fix (2026-08-13, matching the identical Android fix same day, direct
                // user report: "entire app is still messy... give me something
                // real"): used to open BenefitsShopAllScreens' own BenefitsView, a
                // full screen of entirely fabricated content -- a fake points pill, a
                // fake "Limited gift for Rwanda / 25,000" banner, dead "Visit"
                // buttons, and a fake "3 chances to get money back / BK account ->
                // TUYIZERE Eric" card. This row's own subtitle ("Points, coupons,
                // rewards") already describes exactly what the real "Reward tasks"
                // mini-app does -- points there instead of a second, fake destination.
                FlatRow(title: "Benefits", subtitle: "Points, coupons, rewards", glyph: { AnyView(GiftBox(size: 28)) }, action: { showRewardTasksMiniApp = true }),
                        FlatRow(title: "Invest", subtitle: "RSE stocks, real portfolio", glyph: { AnyView(ChartIncreasingGlyph(size: 28)) }, action: { showInvest = true }),
                        FlatRow(title: "26-Week Savings", subtitle: "Escalating auto-save, streak bonus", glyph: { AnyView(MoneyBagGlyph(size: 28)) }, action: { showWeeklySavings = true }),
                FlatRow(title: "31-Day Savings", subtitle: "Daily save, streak-tiered bonus rate", glyph: { AnyView(MoneyBagGlyph(size: 28)) }, action: { showGrow31Savings = true }),
                        FlatRow(title: "Youth account", subtitle: "Capped starter account, ages 7-18", glyph: { AnyView(ChildGlyph(size: 28)) }, action: { showYouthAccount = true }),
                        FlatRow(title: "Card", subtitle: "App-controlled spend limits, one-tap freeze", glyph: { AnyView(ObjectCreditCard(size: 28)) }, action: { showCard = true }),
                        FlatRow(title: "Group account", subtitle: "Shared account with dues and split expenses", glyph: { AnyView(HandshakeGlyph(size: 28)) }, action: { showGroupAccounts = true }),
                        FlatRow(title: "Ikimina", subtitle: "Rotating savings group -- everyone takes a turn", glyph: { AnyView(HandshakeGlyph(size: 28)) }, action: { showIkimina = true }),
                        FlatRow(title: "SACCO shares", subtitle: "Buy cooperative shares, earn a real dividend", glyph: { AnyView(PlaceGlyph(category: "BANK", size: 28)) }, action: { showSacco = true }),
                        FlatRow(title: "Harvest advance", subtitle: "Coffee cooperative input financing", glyph: { AnyView(SeedlingGlyph(size: 28)) }, action: { showHarvestAdvance = true }),
                        // Same icon-clash fix as this file's first FlatRow list above --
                        // shared banknote.fill with "Youth account", now differentiated.
                        FlatRow(title: "VUP Financial Services", subtitle: "Means-tested government microloan for farming, livestock, business", glyph: { AnyView(ShieldEmojiGlyph(size: 28)) }, action: { showVupLoan = true }),
                    ])
                    IdsSearchBar(text: $menuSearchQuery, placeholder: "Search everything else")
                    IconGridSection(
                        title: "Quick access",
                        items: [
                            // Renamed from bare "Mini" (2026-08-23, see
                            // docs/UI_UX_GUIDELINES.md §12) -- collided with the real
                            // "Mini apps" section immediately below, and was itself a
                            // real, silent dead tap (no case for it in onItemClick at
                            // all, unlike Android's equivalent grid which at least
                            // opened the Youth account screen). Wired to the same real
                            // showYouthAccount destination the "Youth account" FlatRow
                            // above already uses, matching Android's fixed behavior
                            // exactly.
                            ("Youth", "banknote.fill"),
                            ("Games", "gamecontroller.fill"),
                            ("Bank", "building.columns.fill"),
                            ("Pick", "star.fill"),
                        ],
                        onItemClick: { label in
                            if label == "Bank" { onOpenBank() }
                            if label == "Youth" { showYouthAccount = true }
                        }
                    )
                    // All four now open real granite mini-apps (see this file's header) --
                    // matching Android's own real four-mini-app parity.
                    FlatSection(title: "Mini apps", rows: [
                        FlatRow(title: "Account balance", showChevron: true, action: { showAccountBalanceMiniApp = true }),
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
                        FlatRow(title: "Open account", subtitle: "Itunda Account, other banks, RSE brokerage", glyph: { AnyView(PlaceGlyph(category: "BANK", size: 28)) }, action: { showOverview = true }),
                        FlatRow(title: "My assets", subtitle: "Accounts, loans, RSE holdings, cards, points", glyph: { AnyView(MoneyBagGlyph(size: 28)) }, action: { showOverview = true }),
                        FlatRow(title: "Spending", subtitle: "Real, ledger-based category breakdown", glyph: { AnyView(BarChartGlyph(size: 28)) }, action: { showSpending = true }),
                        FlatRow(title: "Family", subtitle: "Link a guardian or child, view read-only spending", glyph: { AnyView(FamilyGlyph(size: 28)) }, action: { showFamilyLink = true }),
                        FlatRow(title: "Subscriptions", subtitle: "Detected recurring payments + merchant billing plans", glyph: { AnyView(CalendarGlyph(size: 28)) }, action: { showSubscriptions = true }),
                        FlatRow(title: "Digital certificate", subtitle: "Sign agreements in Itunda", glyph: { AnyView(ObjectPen(size: 28)) }, action: { showCertificate = true }),
                    ], isExpanded: expandedMenuSection == "Accounts & cards", onToggle: { expandedMenuSection = (expandedMenuSection == "Accounts & cards") ? nil : "Accounts & cards" })
                    CollapsibleFlatSection(title: "Send & pay", rows: [
                        // Real Transfer full page (2026-07-24 port), matching real Toss's
                        // own 송금 row here ("자동이체 · 더치페이" subtitle) -- groups Send
                        // money/Auto-transfer/history in one place instead of Home's Send
                        // button (which stays a quick recipient-picker, unchanged) being the
                        // only entry point. See TransferHubScreen.swift's own doc comment.
                        FlatRow(title: "Transfer", subtitle: "Auto-transfer, split a bill", symbol: "paperplane.fill", tint: .accentIndigo, action: onOpenTransferHub),
                        FlatRow(title: "Request money", subtitle: "Generate a real payment request code", glyph: { AnyView(ReceiptGlyph(size: 28)) }, action: { showRequestMoney = true }),
                        FlatRow(title: "Auto top-up", subtitle: "Refill your account automatically from a linked account", glyph: { AnyView(RefreshCardGlyph(size: 28)) }, action: { showAutoTopUp = true }),
                        // MTN/Airtel airtime and broadband are real billers inside the Pay
                        // Bills mini-app -- same real destination "REG & WASAC bills" below
                        // already uses, same fix as Android's identical dead tap.
                        FlatRow(title: "Mobile plan", subtitle: "MTN, Airtel, broadband", glyph: { AnyView(ObjectMobilePhone(size: 28)) }, action: { showPayBillsMiniApp = true }),
                    ], isExpanded: expandedMenuSection == "Send & pay", onToggle: { expandedMenuSection = (expandedMenuSection == "Send & pay") ? nil : "Send & pay" })
                    CollapsibleFlatSection(title: "Save & grow", rows: [
                        FlatRow(title: "Round-up savings", subtitle: "Auto-save spare change from every transfer", symbol: "arrow.up.circle.fill", tint: .accentOrange, action: { showRoundUp = true }),
                        FlatRow(title: "12-month deposit", subtitle: "Interest paid upfront, principal locked 12 months", glyph: { AnyView(LockGlyph(size: 28)) }, action: { showUpfrontDeposit = true }),
                    ], isExpanded: expandedMenuSection == "Save & grow", onToggle: { expandedMenuSection = (expandedMenuSection == "Save & grow") ? nil : "Save & grow" })
                    CollapsibleFlatSection(title: "Borrow", rows: [
                        FlatRow(title: "Get a loan", subtitle: "Personal, salary-backed, SME working capital", glyph: { AnyView(MoneyBagGlyph(size: 28)) }, action: { showLoans = true }),
                        FlatRow(title: "Credit score", subtitle: "Free check, alternative data", glyph: { AnyView(NatureGlowingStar(size: 28)) }, action: { showCreditScore = true }),
                        FlatRow(title: "Moto-Taxi Ownership", subtitle: "Save a 30% down payment, then convert to a loan for your own bike", symbol: "bicycle", tint: .accentTeal, action: { showMotoOwnership = true }),
                    ], isExpanded: expandedMenuSection == "Borrow", onToggle: { expandedMenuSection = (expandedMenuSection == "Borrow") ? nil : "Borrow" })
                    CollapsibleFlatSection(title: "Transport", rows: [
                        FlatRow(title: "Rides", subtitle: "Request a ride or drive for real fares", glyph: { AnyView(TravelCar(size: 28)) }, action: { showRides = true }),
                        FlatRow(title: "Designated driver", subtitle: "A driver takes you and your own car home", glyph: { AnyView(ObjectKey(size: 28)) }, action: { showDesignatedDriver = true }),
                        FlatRow(title: "Bike rental", subtitle: "Rent a nearby bike or scooter, billed by the minute", glyph: { AnyView(BikeGlyph(size: 28)) }, action: { showBikeRental = true }),
                        FlatRow(title: "Parking", subtitle: "Rent a nearby parking spot, billed by the hour", glyph: { AnyView(ParkingGlyph(size: 28)) }, action: { showParking = true }),
                        FlatRow(title: "Bus", subtitle: "Book intercity bus seats or post your own route", glyph: { AnyView(PlaceGlyph(category: "BUS_STOP", size: 28)) }, action: { showBus = true }),
                // Real "tap to pay your moto-taxi fare" (2026-08-27, direct user
                // follow-up: "now we can make pay for tax and moto as well") -- see
                // MotoFareCollectScreenView.swift's own doc comment.
                FlatRow(title: "Collect a moto fare", subtitle: "Drivers: tap or scan a rider's code to collect a real fare", symbol: "bicycle", tint: Color(hex: 0xD97706), action: { showMotoFareCollect = true }),
                        FlatRow(title: "Vehicle inspection", subtitle: "Pay a mechanic to inspect a used car before you buy", glyph: { AnyView(WrenchGlyph(size: 28)) }, action: { showVehicleInspection = true }),
                        FlatRow(title: "My vehicles", subtitle: "Track your car's estimated resale value", glyph: { AnyView(TravelCar(size: 28)) }, action: { showVehicleValuation = true }),
                    ], isExpanded: expandedMenuSection == "Transport", onToggle: { expandedMenuSection = (expandedMenuSection == "Transport") ? nil : "Transport" })
                    CollapsibleFlatSection(title: "Community & trust", rows: [
                        FlatRow(title: "Trust score", subtitle: "How your neighbors see you on Marketplace, Jobs, and Property", glyph: { AnyView(NatureStar(size: 28)) }, action: { showTrustScore = true }),
                        FlatRow(title: "Q&A", subtitle: "Ask a question, answer one, get adopted", glyph: { AnyView(SpeechBubbleGlyph(size: 28)) }, action: { showKnowledge = true }),
                    ], isExpanded: expandedMenuSection == "Community & trust", onToggle: { expandedMenuSection = (expandedMenuSection == "Community & trust") ? nil : "Community & trust" })
                    // Kept last and separately labeled, not blended into the rows above:
                    // these two are role-gated (only assigned cash-agent operators can use
                    // them), same reasoning as Android's identical split.
                    CollapsibleFlatSection(title: "Cash agent tools", rows: [
                        FlatRow(title: "Agent till", subtitle: "For assigned cash-agent operators: cash-in, cash-out, till count", glyph: { AnyView(PlaceGlyph(category: "ITUNDA_AGENT", size: 28)) }, action: { showAgentOperator = true }),
                        FlatRow(title: "Float marketplace", subtitle: "For assigned cash-agents: offer or request float from nearby agents", glyph: { AnyView(PlaceGlyph(category: "MARKET", size: 28)) }, action: { showFloatMarketplace = true }),
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
                        FlatRow(title: "Switch your personal loan", trailing: "12% ~ 24%", trailingIsLink: true, glyph: { AnyView(MoneyBagGlyph(size: 28)) }, action: { showLoans = true }),
                        FlatRow(title: "Switch your rent deposit loan", trailing: "9% ~ 15%", trailingIsLink: true, glyph: { AnyView(TravelHouse(size: 28)) }, action: { showLoans = true }),
                        FlatRow(title: "Switch your SME loan", trailing: "11% ~ 22%", trailingIsLink: true, glyph: { AnyView(PlaceGlyph(category: "MARKET", size: 28)) }, action: { showLoans = true }),
                    ], isExpanded: expandedMenuSection == "Switch & save", onToggle: { expandedMenuSection = (expandedMenuSection == "Switch & save") ? nil : "Switch & save" })
                }
                Group {
                    // Real fix (2026-08-10, matching Android's 2026-08-03 fix): both rows
                    // point at the same real Card screen "Card" already opens elsewhere on
                    // this screen -- a second, previously-dead entry point into it, not a
                    // separate feature.
                    CollapsibleFlatSection(title: "Cards", rows: [
                        FlatRow(title: "Itunda Card", trailing: "5% back on bills", trailingIsLink: true, glyph: { AnyView(ObjectCreditCard(size: 28)) }, action: { showCard = true }),
                        FlatRow(title: "Virtual card", trailing: "Instant issue", glyph: { AnyView(ObjectCreditCard(size: 28)) }, action: { showCard = true }),
                    ], isExpanded: expandedMenuSection == "Cards", onToggle: { expandedMenuSection = (expandedMenuSection == "Cards") ? nil : "Cards" })
                    CollapsibleFlatSection(title: "Services", rows: [
                        FlatRow(title: "Rent deposit protection", glyph: { AnyView(TravelHouse(size: 28)) }),
                        FlatRow(title: "Recurring payments", glyph: { AnyView(CalendarGlyph(size: 28)) }),
                        FlatRow(title: "Import recurring payments", symbol: "shippingbox.fill", tint: .accentGray),
                        FlatRow(title: "REG & WASAC bills", glyph: { AnyView(ObjectLightBulb(size: 28)) }, action: { showPayBillsMiniApp = true }),
                        FlatRow(title: "Interest earned this month", glyph: { AnyView(MoneyBagGlyph(size: 28)) }, action: onClaimInterest),
                        FlatRow(title: "SME income tax estimate", glyph: { AnyView(ReceiptGlyph(size: 28)) }),
                        // Real split-bill (found 2026-07-22 fully built with zero UI
                        // anywhere) lives inside a specific group's own thread (Talk
                        // tab), not a standalone flow -- hand off there instead of
                        // duplicating a group picker.
                        FlatRow(title: "Split a bill with friends", glyph: { AnyView(SplitBillDice(size: 28)) }, action: onSwitchToTalk),
                        FlatRow(title: "Shared calendar", glyph: { AnyView(CalendarGlyph(size: 28)) }),
                        FlatRow(title: "Kids' allowance tasks", glyph: { AnyView(ChildGlyph(size: 28)) }),
                    ], isExpanded: expandedMenuSection == "Services", onToggle: { expandedMenuSection = (expandedMenuSection == "Services") ? nil : "Services" })
                    // Real fix (2026-08-10, matching Android's 2026-08-03 fix): both rows
                    // are the same real foreign-currency account "Mobile plan"/Send & pay's
                    // own routing doesn't cover -- this is its own real screen.
                    CollapsibleFlatSection(title: "Foreign currency", rows: [
                        FlatRow(title: "Foreign currency account", trailing: "100% rate preference", trailingIsLink: true, glyph: { AnyView(GlobeGlyph(size: 28)) }, action: { showForeignCurrency = true }),
                        FlatRow(title: "International transfer", glyph: { AnyView(GlobeGlyph(size: 28)) }, action: { showForeignCurrency = true }),
                    ], isExpanded: expandedMenuSection == "Foreign currency", onToggle: { expandedMenuSection = (expandedMenuSection == "Foreign currency") ? nil : "Foreign currency" })
                    // Real fix (2026-08-10, matching Android's 2026-08-03 fix): all 4 rows
                    // are the same real RSE investing screen ("Invest" quick link already
                    // opens) -- routed there instead of sitting dead.
                    CollapsibleFlatSection(title: "Grow your money", rows: [
                        FlatRow(title: "RSE stocks", subtitle: "BOK, MTNR, BLR, IMR, CMR, EQTY", glyph: { AnyView(ChartIncreasingGlyph(size: 28)) }, action: { showInvest = true }),
                        FlatRow(title: "Bonds & fixed income", trailing: "7.5% ~ 12%", trailingIsLink: true, glyph: { AnyView(PlaceGlyph(category: "BANK", size: 28)) }, action: { showInvest = true }),
                        FlatRow(title: "IPO schedule", glyph: { AnyView(ChartIncreasingGlyph(size: 28)) }, action: { showInvest = true }),
                        FlatRow(title: "Brokerage account", trailing: "Up to 30,000 RWF", trailingIsLink: true, glyph: { AnyView(PlaceGlyph(category: "BANK", size: 28)) }, action: { showInvest = true }),
                    ], isExpanded: expandedMenuSection == "Grow your money", onToggle: { expandedMenuSection = (expandedMenuSection == "Grow your money") ? nil : "Grow your money" })
                    CollapsibleFlatSection(title: "Pension", rows: [
                        FlatRow(title: "Check my RSSB pension", glyph: { AnyView(PlaceGlyph(category: "BANK", size: 28)) }),
                        FlatRow(title: "Pension products", glyph: { AnyView(MoneyBagGlyph(size: 28)) }),
                    ], isExpanded: expandedMenuSection == "Pension", onToggle: { expandedMenuSection = (expandedMenuSection == "Pension") ? nil : "Pension" })
                    CollapsibleFlatSection(title: "Loans", rows: [
                        FlatRow(title: "Check my max limit", glyph: { AnyView(ChartIncreasingGlyph(size: 28)) }, action: { showLoans = true }),
                        FlatRow(title: "Personal loan", trailing: "11% ~ 24%", trailingIsLink: true, glyph: { AnyView(MoneyBagGlyph(size: 28)) }, action: { showLoans = true }),
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
                        FlatRow(title: "FAQ", glyph: { AnyView(QuestionGlyph(size: 28)) }),
                        FlatRow(title: "Live chat", glyph: { AnyView(SpeechBubbleGlyph(size: 28)) }),
                        FlatRow(title: "Call support", glyph: { AnyView(ObjectMobilePhone(size: 28)) }),
                        FlatRow(title: "Report an issue with a transaction", glyph: { AnyView(WarningGlyph(size: 28)) }, showChevron: true, action: { showSupport = true }),
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
                    IdsAllTopBar(onOpenSettings: onOpenSettings)
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
        .sheet(isPresented: $showAccountBalanceMiniApp) {
            SaroniteAccountBalanceView()
        }
        .sheet(isPresented: $showRewardTasksMiniApp) {
            SaroniteRewardTasksView()
        }
        .sheet(isPresented: $showInsuranceMiniApp) {
            SaroniteInsuranceView()
        }
        .sheet(isPresented: $showShop) {
            CommerceShopContent()
        }
        .sheet(isPresented: $showEats) {
            EatsContent()
        }
        .sheet(isPresented: $showMap) {
            MapScreenView()
        }
        .sheet(isPresented: $showInvest) {
            InvestScreenView(onBack: { showInvest = false })
        }
        .sheet(isPresented: $showOverview) {
            OverviewScreenView(
                onBack: { showOverview = false },
                onOpenCard: { showOverview = false; showCard = true },
                onOpenLoans: { showOverview = false; showLoans = true },
                onOpenInvest: { showOverview = false; showInvest = true },
                onOpenProperty: { showOverview = false; onOpenProperty() },
                onOpenVehicleValuation: { showOverview = false; showVehicleValuation = true },
                onOpenInsurance: { showOverview = false; showInsuranceMiniApp = true },
                onOpenBills: { showOverview = false; showPayBillsMiniApp = true },
                onOpenRewards: { showOverview = false; showRewardTasksMiniApp = true },
            )
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
        .sheet(isPresented: $showGrow31Savings) {
            Grow31SavingsScreenView(onBack: { showGrow31Savings = false })
        }
        .sheet(isPresented: $showYouthAccount) {
            YouthAccountScreenView(onBack: { showYouthAccount = false })
        }
        .sheet(isPresented: $showCard) {
            CardScreenView(onBack: { showCard = false })
        }
        .sheet(isPresented: $showTransit) {
            TransitScreenView(onBack: { showTransit = false }, onOpenCollect: { showTransitCollect = true })
        }
        .sheet(isPresented: $showTransitCollect) {
            TransitCollectScreenView(onBack: { showTransitCollect = false })
        }
        .sheet(isPresented: $showMotoFareCollect) {
            MotoFareCollectScreenView(onBack: { showMotoFareCollect = false })
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
// EntireMenuScreen's own catalog. Mirrors Android's trimmed MyTab exactly. This is
// now its own primary tab (ItundaTab.You, 2026-08-10, see ContentView.swift's own
// doc comment) -- not reached via EntireMenuScreen's profile icon anymore.
struct MyTabView: View {
    var onBack: () -> Void = {}
    var onSwitchToShop: () -> Void = {}
    var onSwitchToEats: () -> Void = {}
    var onSwitchToMarketplace: () -> Void = {}
    var onSwitchToJobs: () -> Void = {}
    var onSwitchToProperty: () -> Void = {}

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
                    Button(action: onBack) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Back")
                    Spacer()
                    Text("My").font(IDS.scaledFont(size: 20, weight: .bold, relativeTo: .title2)).foregroundColor(IDS.Colors.textPrimary)
                    Spacer()
                    Color.clear.frame(width: 20)
                }
                ProfilePhotoCard()
                // Real Toss-sourced passwordless-login rollout (2026-08-23) -- see
                // PinUpgradeCard.swift's own doc comment. Own file, not inline here,
                // matching this session's own file-size-lint discipline for this
                // already-large file.
                PinUpgradeCard()
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
                    // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper --
                    // matches "My orders" below (already flat) and the identical Android/
                    // web Partner-earnings conversion (docs/UI_UX_GUIDELINES.md §10).
                    .padding(.vertical, 10).frame(maxWidth: .infinity, alignment: .leading)
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
                            orderRow(label: "Eats order", status: order.status, amount: order.totalAmount, action: onSwitchToEats)
                        }
                    }
                }
                // Real favorites/wishlist tracking across every product with one --
                // counts are real (GET .../favorites on each module); tapping switches
                // directly to that real destination's own dedicated wishlist view.
                // Each of Marketplace/Jobs/Property is its own flat destination now
                // (2026-08-10, Hood's segmented Picker retired), so this is a real,
                // direct deep link, not "one more tap" into a shared sub-view.
                FlatSection(title: "My favorites", rows: [
                    FlatRow(title: "Marketplace wishlist", trailing: "\(favoriteListingsCount)", glyph: { AnyView(WishlistHeart(favorited: true, size: 28)) }, action: onSwitchToMarketplace),
                    FlatRow(title: "Jobs wishlist", trailing: "\(favoriteJobPostsCount)", glyph: { AnyView(WishlistHeart(favorited: true, size: 28)) }, action: onSwitchToJobs),
                    FlatRow(title: "Property wishlist", trailing: "\(favoritePropertyListingsCount)", glyph: { AnyView(WishlistHeart(favorited: true, size: 28)) }, action: onSwitchToProperty),
                    FlatRow(title: "Restaurant favorites", trailing: "\(favoriteRestaurantsCount)", glyph: { AnyView(WishlistHeart(favorited: true, size: 28)) }, action: onSwitchToEats),
                ])
                // Real "my own posts" tracking (Marketplace/Jobs/Property listings I
                // created) -- same Naver-style "track your own activity" pattern.
                FlatSection(title: "My listings", rows: [
                    FlatRow(title: "Marketplace", trailing: "\(myListingsCount)", glyph: { AnyView(PlaceGlyph(category: "MARKET", size: 28)) }, action: onSwitchToMarketplace),
                    FlatRow(title: "Jobs posted", trailing: "\(myJobPostsCount)", glyph: { AnyView(BriefcaseGlyph(size: 28)) }, action: onSwitchToJobs),
                    FlatRow(title: "Property listed", trailing: "\(myPropertyListingsCount)", glyph: { AnyView(TravelHouse(size: 28)) }, action: onSwitchToProperty),
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
            Text("\(formatAmount(Int(amount))) RWF").foregroundColor(IDS.Colors.textPrimary)
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
                        .background(IDS.Colors.card).cornerRadius(10).idsCardBorder(cornerRadius: 10)
                }
                .disabled(saving)
            }
        }
        .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius).idsCardBorder(cornerRadius: IDS.Layout.cardCornerRadius)
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
                // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper --
                // matches Android's identical VerificationCard conversion, a lone
                // conditional section (docs/UI_UX_GUIDELINES.md §10).
                .padding(.vertical, 10)
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
    // Real Toss-style "OTP Successful Animation" + wrong-code shake (60fps.design's
    // own real catalog of Toss's named interactions, 2026-08-29) -- reuses
    // IdsCelebrationScreen's exact spring/haptic checkmark language and
    // AccountPinPad's exact shake sequence, matching Android's identical fix same
    // session.
    @State private var verified = false
    @State private var checkScale: CGFloat = 0.3
    @State private var shakeOffset: CGFloat = 0
    // Real Toss "Verification Code Shimmer Animation" equivalent (web/Android's own
    // VerificationRow got this same session -- closing the parity gap now): a
    // subtle pulse on the input while the submitted code is being verified.
    @State private var inputOpacity: Double = 1

    var body: some View {
        if kind == "email" && !hasEmail {
            Text("No email address on file to verify.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
        } else {
            VStack(alignment: .leading, spacing: 4) {
                if verified {
                    HStack(spacing: 8) {
                        ZStack {
                            Circle().fill(IDS.Colors.success).frame(width: 22, height: 22)
                            Image(systemName: "checkmark").font(.system(size: 11, weight: .bold)).foregroundColor(.white)
                        }
                        .scaleEffect(checkScale)
                        Text(kind == "email" ? "Email verified" : "Phone number verified").font(.caption).bold().foregroundColor(IDS.Colors.success)
                    }
                } else if !sent {
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
                            .opacity(inputOpacity)
                            .onChange(of: busy) { isBusy in
                                if isBusy {
                                    withAnimation(.easeInOut(duration: 0.45).repeatForever(autoreverses: true)) { inputOpacity = 0.55 }
                                } else {
                                    withAnimation(.linear(duration: 0.15)) { inputOpacity = 1 }
                                }
                            }
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
                        // Real CTA-label-clarity fix (2026-08-24, docs/DESIGN_REFERENCES.md §11)
                        // -- same fix as Android/web's identical VerificationRow (commit
                        // 58d58259): a bare "Confirm" doesn't state the outcome, per Toss's own
                        // dark-pattern-prevention CTA rule.
                        Button(action: { Task { await confirm() } }) {
                            Text(busy ? "…" : (kind == "email" ? "Verify email" : "Verify phone number")).font(.caption).bold().foregroundColor(.white)
                                .padding(.horizontal, 12).padding(.vertical, 10)
                                .background((busy || code.trimmingCharacters(in: .whitespaces).isEmpty) ? IDS.Colors.textTertiary : IDS.Colors.brand).cornerRadius(8)
                        }
                        .disabled(busy || code.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                    .offset(x: shakeOffset)
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
        } catch NetworkError.httpError(let statusCode) where statusCode == 409 {
            // Real gap found live (Toss-style error-handling audit, 2026-08-30): only
            // reachable via stale client state (verified on another device/tab between
            // this row rendering and the tap) -- not really a failure, resolve forward.
            // 409 is unambiguous for this specific call: EMAIL_ALREADY_VERIFIED/
            // PHONE_ALREADY_VERIFIED are the only 409s either endpoint can return.
            onVerified()
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
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            verified = true
            withAnimation(IDS.Motion.springMedium) { checkScale = 1 }
            try? await Task.sleep(nanoseconds: 500_000_000)
            onVerified()
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
            shake()
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
            shake()
        }
    }

    private func shake() {
        withAnimation(.linear(duration: 0.06)) { shakeOffset = 16 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.06) {
            withAnimation(.linear(duration: 0.06)) { shakeOffset = -16 }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.06) {
                withAnimation(.linear(duration: 0.06)) { shakeOffset = 0 }
            }
        }
    }
}

// Was a text navbar -- "ID | Support | Settings" with pipe separators, then a hamburger
// icon leading to the Menu screen -- neither has an equivalent in real Toss. The
// Explore tab top bar is just the user's name plus a settings icon; support/ID live
// as rows further down the list, not up here. The profile icon this bar showed
// 2026-07-24 - 2026-08-10 is gone -- You is its own primary tab now (see
// ContentView.swift's Home/Pay/Explore/Messages/You), so a second way to reach the
// same screen from here would be a real duplicate, not a convenience (same fix as
// Android's AllTopBar).
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
            .buttonStyle(PressScaleButtonStyle())
            // Found live via FocusOrderTests (2026-07-12): this button's action and
            // icon were changed from direct-logout to opening the real Settings
            // screen, but the accessibility label was never updated to match -- a
            // VoiceOver user would have been told "Log out" for a button that
            // actually opens Settings.
            .accessibilityLabel("Settings")
        }
    }
}

