import SwiftUI
import UIKit
import CoreDesignSystem
import CoreNetwork

// Real Explore primary bottom tab (renamed 2026-08-10 from All -- see
// ContentView.swift's own doc comment for the full history: My is its own primary
// tab now, ItundaTab.You, not reached via a profile icon here anymore). Mirrors
// Android's MenuScreen exactly (see ItundaAppScreen.kt's own doc comment on
// ItundaTab for the full history). Shop/Eats/Marketplace/Community/Jobs are real
// flat rows here (2026-08-10) since they lost their own primary tabs; Property
// already had its own Shortcuts-grid entry.
//
// Promoted here from App/Sources/BenefitsShopAllScreens.swift (2026-09-02, Menu
// Feature-module decomposition, matching Android's own already-real
// :features:menu:impl). Every one of this screen's ~45 destination screens is an
// App-only or other-Feature-owned type a Feature module cannot import directly --
// unlike Home/Pay's handful of App-only dependencies, this screen's entire role IS
// being a catalog of nearly every product in the app, so ALL of them convert to
// plain `onOpenX: () -> Void` callbacks, exactly matching Android's real
// MenuScreen.kt signature (48 onOpenX params, ContentView.swift's real counterpart
// to ItundaAppScreen.kt owning every destination's state + presentation). This is a
// deliberately different shape from FeatureHome/FeaturePay's generic-ViewBuilder-
// injection pattern -- with 45+ destinations, a `<A: View, B: View, C: View, ...>`
// generic-parameter list would be far less readable than Android's own proven
// long-callback-list shape, and the two platforms should converge on the same
// architecture for the same screen rather than diverge for aesthetic reasons.
// menuSearchQuery/expandedMenuSection/availableTaskCount stay local -- pure UI
// state with no App-only type involved.
public struct EntireMenuScreen: View {
    public var onOpenSettings: () -> Void
    public var onClaimInterest: () -> Void
    public var onSwitchToTalk: () -> Void
    public var onOpenTransferHub: () -> Void
    public var onOpenShop: () -> Void
    public var onOpenEats: () -> Void
    public var onOpenMarketplace: () -> Void
    public var onOpenCommunity: () -> Void
    public var onOpenJobs: () -> Void
    public var onOpenProperty: () -> Void
    // Real architectural fix (2026-08-13, matching the identical Android/web fix same
    // session, direct user directive): "itunda bank is a complete product... tabs are
    // not products, are just access points." BankView used to render directly as
    // ContentView's own Home tab (tag 0) -- real Bank-product content baked into what's
    // meant to be a generic access point. Moved to a real destination reachable from
    // here instead, wired to the "Bank" icon below (Quick access) which was previously
    // a dead tap (IconGridSection's onItemClick was never passed for this section).
    public var onOpenBank: () -> Void
    public var onOpenTransit: () -> Void
    public var onOpenMap: () -> Void
    public var onOpenInvest: () -> Void
    public var onOpenWeeklySavings: () -> Void
    public var onOpenGrow31Savings: () -> Void
    public var onOpenYouthAccount: () -> Void
    public var onOpenCard: () -> Void
    public var onOpenGroupAccounts: () -> Void
    public var onOpenIkimina: () -> Void
    public var onOpenSacco: () -> Void
    public var onOpenHarvestAdvance: () -> Void
    public var onOpenVupLoan: () -> Void
    public var onOpenStudentLoan: () -> Void
    public var onOpenRewardsMiniApp: () -> Void
    public var onOpenPayBillsMiniApp: () -> Void
    public var onOpenAccountBalanceMiniApp: () -> Void
    public var onOpenInsuranceMiniApp: () -> Void
    public var onOpenOverview: () -> Void
    public var onOpenSpending: () -> Void
    public var onOpenFamilyLink: () -> Void
    public var onOpenSubscriptions: () -> Void
    public var onOpenCertificate: () -> Void
    public var onOpenIdentity: () -> Void
    public var onOpenRequestMoney: () -> Void
    public var onOpenAutoTopUp: () -> Void
    public var onOpenRoundUp: () -> Void
    public var onOpenUpfrontDeposit: () -> Void
    public var onOpenLoans: () -> Void
    public var onOpenCreditScore: () -> Void
    public var onOpenMotoOwnership: () -> Void
    public var onOpenRides: () -> Void
    public var onOpenDesignatedDriver: () -> Void
    public var onOpenBikeRental: () -> Void
    public var onOpenParking: () -> Void
    public var onOpenBus: () -> Void
    public var onOpenMotoFareCollect: () -> Void
    public var onOpenVehicleInspection: () -> Void
    public var onOpenVehicleValuation: () -> Void
    public var onOpenTrustScore: () -> Void
    public var onOpenKnowledge: () -> Void
    public var onOpenAgentOperator: () -> Void
    public var onOpenFloatMarketplace: () -> Void
    public var onOpenForeignCurrency: () -> Void
    public var onOpenSupport: () -> Void

    // Real fix (2026-08-10) -- see IdsSearchBar's own doc comment for the full
    // account of the fake-search-bar bug this closes.
    // Not `private` -- EntireMenuScreenCatalog.swift's own extension (a separate
    // file, same module) needs to read this; Swift's `private` is file-scoped, not
    // type-scoped, so `internal` (the default, dropping the keyword) is what
    // actually allows same-module cross-file access here.
    @State var menuSearchQuery: String = ""
    // Real fix (2026-08-30, simplification-adoption thread, toss.tech/article/
    // Marketing_Writing principle 5: Toss measured a real 4x conversion increase
    // replacing vague copy ("missions") with a concrete count ("4 financial
    // missions available")) -- matches Android's identical same-day fix. Own,
    // independent fetch (this screen has no shared state with PayHomeExtras' own
    // rewardsTotal fetch to reuse).
    @State var availableTaskCount: Int? = nil
    var benefitsSubtitle: String {
        switch availableTaskCount {
        case nil, 0: return "Points, coupons, rewards"
        case 1: return "1 reward task available"
        case let count?: return "\(count) reward tasks available"
        }
    }
    // Real fix (2026-08-10): matches Android/web's identical collapse-by-default fix
    // (see CollapsibleFlatSection's own doc comment for the full Hick's Law account).
    // "Quick links"/"Mini apps" stay always-visible; only the 16 heavier categories
    // below them collapse. Safe to add without touching the two Group{} blocks' own
    // child count -- collapsing is internal to each CollapsibleFlatSection's own
    // rendering, not a change in how many views the parent Group receives.
    @State var expandedMenuSection: String? = nil

    public var body: some View {
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
                        FlatRow(title: "Shop", subtitle: "Coupang-style commerce", glyph: { AnyView(ShoppingBagGlyph(size: 28)) }, action: onOpenShop),
                        FlatRow(title: "Eats", subtitle: "Food delivery, order or deliver", glyph: { AnyView(PlaceGlyph(category: "RESTAURANT", size: 28)) }, action: onOpenEats),
                        FlatRow(title: "Transit", subtitle: "Top up and tap to pay your real Kigali bus fare", symbol: "bus.fill", tint: Color(hex: 0x2F8F5B), action: onOpenTransit),
                        FlatRow(title: "Map", subtitle: "Real Rwanda map, self-hosted", glyph: { AnyView(PinGlyph(size: 28)) }, action: onOpenMap),
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
                        FlatRow(title: "Benefits", subtitle: benefitsSubtitle, glyph: { AnyView(GiftBox(size: 28)) }, action: onOpenRewardsMiniApp),
                        FlatRow(title: "Invest", subtitle: "RSE stocks, real portfolio", glyph: { AnyView(ChartIncreasingGlyph(size: 28)) }, action: onOpenInvest),
                        // Real gap found live (2026-08-31, direct user follow-up: "keep
                        // improving itunda bank to more like toss bank"): matches the real
                        // Toss Bank reference's own catalog pattern (e.g. "31일 적금 --
                        // 1%~10% p.a." shown directly in the product list, no tap
                        // required) -- Android's own BankHubScreen already states these
                        // exact real rates inline, iOS/web never did. Sourced from
                        // WeeklySavingsService/Grow31SavingsService/
                        // UpfrontInterestDepositService's own real rate constants, not
                        // invented; same copy as Android's identical fix.
                        FlatRow(title: "26-Week Savings", subtitle: "5% base rate, escalates weekly", glyph: { AnyView(MoneyBagGlyph(size: 28)) }, action: onOpenWeeklySavings),
                        FlatRow(title: "31-Day Savings", subtitle: "Daily streak, up to 10% bonus rate", glyph: { AnyView(MoneyBagGlyph(size: 28)) }, action: onOpenGrow31Savings),
                        FlatRow(title: "Youth account", subtitle: "Capped starter account, ages 7-18", glyph: { AnyView(ChildGlyph(size: 28)) }, action: onOpenYouthAccount),
                        FlatRow(title: "Card", subtitle: "App-controlled spend limits, one-tap freeze", glyph: { AnyView(ObjectCreditCard(size: 28)) }, action: onOpenCard),
                        FlatRow(title: "Group account", subtitle: "Shared account with dues and split expenses", glyph: { AnyView(HandshakeGlyph(size: 28)) }, action: onOpenGroupAccounts),
                        FlatRow(title: "Ikimina", subtitle: "Rotating savings group -- everyone takes a turn", glyph: { AnyView(HandshakeGlyph(size: 28)) }, action: onOpenIkimina),
                        FlatRow(title: "SACCO shares", subtitle: "Buy cooperative shares, earn a real dividend", glyph: { AnyView(PlaceGlyph(category: "BANK", size: 28)) }, action: onOpenSacco),
                        FlatRow(title: "Harvest advance", subtitle: "Coffee cooperative input financing", glyph: { AnyView(SeedlingGlyph(size: 28)) }, action: onOpenHarvestAdvance),
                        // Real icon-clash fix (2026-08-24), same bug class as the Android
                        // MenuScreen fix this session (DESIGN_REFERENCES.md Section 65/193's
                        // "richer, distinct per-product iconography" principle) -- this row
                        // shared the identical banknote.fill symbol with "Youth account" above.
                        // "checkmark.shield.fill" mirrors Android's own real choice for this
                        // exact row (IdsIcons.ShieldCheck) semantically (a government-backed,
                        // verified program), without widening FlatRow's `symbol: String?` API
                        // to accept a custom Shape -- a bigger, separately-scoped change.
                        FlatRow(title: "VUP Financial Services", subtitle: "Means-tested government microloan for farming, livestock, business", glyph: { AnyView(ShieldEmojiGlyph(size: 28)) }, action: onOpenVupLoan),
                        // No real itundaface glyph for this row yet -- SF Symbol fallback,
                        // matching FlatRow's own established "keep the fallback rather than a
                        // fabricated glyph" convention.
                        FlatRow(title: "BRD Student Loan", subtitle: "Higher-education loan, real BRD 11%/12% rates", symbol: "graduationcap.fill", action: onOpenStudentLoan),
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
                            if label == "Youth" { onOpenYouthAccount() }
                        }
                    )
                    // All four now open real granite mini-apps (see this file's header) --
                    // matching Android's own real four-mini-app parity.
                    FlatSection(title: "Mini apps", rows: [
                        FlatRow(title: "Account balance", showChevron: true, action: onOpenAccountBalanceMiniApp),
                        FlatRow(title: "Pay bills", showChevron: true, action: onOpenPayBillsMiniApp),
                        FlatRow(title: "Reward tasks", showChevron: true, action: onOpenRewardsMiniApp),
                        FlatRow(title: "Insurance", showChevron: true, action: onOpenInsuranceMiniApp),
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
                            case "Open account": onOpenOverview()
                            case "Verify": onOpenIdentity()
                            case "Send": onOpenTransferHub()
                            case "Group": onOpenGroupAccounts()
                            case "Property": onOpenProperty()
                            case "Insurance": onOpenInsuranceMiniApp()
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
                        FlatRow(title: "Open account", subtitle: "Itunda Account, other banks, RSE brokerage", glyph: { AnyView(PlaceGlyph(category: "BANK", size: 28)) }, action: onOpenOverview),
                        FlatRow(title: "My assets", subtitle: "Accounts, loans, RSE holdings, cards, points", glyph: { AnyView(MoneyBagGlyph(size: 28)) }, action: onOpenOverview),
                        FlatRow(title: "Spending", subtitle: "Real, ledger-based category breakdown", glyph: { AnyView(BarChartGlyph(size: 28)) }, action: onOpenSpending),
                        FlatRow(title: "Family", subtitle: "Link a guardian or child, view read-only spending", glyph: { AnyView(FamilyGlyph(size: 28)) }, action: onOpenFamilyLink),
                        FlatRow(title: "Subscriptions", subtitle: "Detected recurring payments + merchant billing plans", glyph: { AnyView(CalendarGlyph(size: 28)) }, action: onOpenSubscriptions),
                        FlatRow(title: "Digital certificate", subtitle: "Sign agreements in Itunda", glyph: { AnyView(ObjectPen(size: 28)) }, action: onOpenCertificate),
                    ], isExpanded: expandedMenuSection == "Accounts & cards", onToggle: { expandedMenuSection = (expandedMenuSection == "Accounts & cards") ? nil : "Accounts & cards" })
                    CollapsibleFlatSection(title: "Send & pay", rows: [
                        // Real Transfer full page (2026-07-24 port), matching real Toss's
                        // own 송금 row here ("자동이체 · 더치페이" subtitle) -- groups Send
                        // money/Auto-transfer/history in one place instead of Home's Send
                        // button (which stays a quick recipient-picker, unchanged) being the
                        // only entry point. See TransferHubScreen.swift's own doc comment.
                        FlatRow(title: "Transfer", subtitle: "Auto-transfer, split a bill", symbol: "paperplane.fill", tint: .accentIndigo, action: onOpenTransferHub),
                        FlatRow(title: "Request money", subtitle: "Generate a real payment request code", glyph: { AnyView(ReceiptGlyph(size: 28)) }, action: onOpenRequestMoney),
                        FlatRow(title: "Auto top-up", subtitle: "Refill your account automatically from a linked account", glyph: { AnyView(RefreshCardGlyph(size: 28)) }, action: onOpenAutoTopUp),
                        // MTN/Airtel airtime and broadband are real billers inside the Pay
                        // Bills mini-app -- same real destination "REG & WASAC bills" below
                        // already uses, same fix as Android's identical dead tap.
                        FlatRow(title: "Mobile plan", subtitle: "MTN, Airtel, broadband", glyph: { AnyView(ObjectMobilePhone(size: 28)) }, action: onOpenPayBillsMiniApp),
                    ], isExpanded: expandedMenuSection == "Send & pay", onToggle: { expandedMenuSection = (expandedMenuSection == "Send & pay") ? nil : "Send & pay" })
                    CollapsibleFlatSection(title: "Save & grow", rows: [
                        FlatRow(title: "Round-up savings", subtitle: "Auto-save spare change from every transfer", symbol: "arrow.up.circle.fill", tint: .accentOrange, action: onOpenRoundUp),
                        FlatRow(title: "12-month deposit", subtitle: "2.80%/yr interest paid upfront, principal locked", glyph: { AnyView(LockGlyph(size: 28)) }, action: onOpenUpfrontDeposit),
                    ], isExpanded: expandedMenuSection == "Save & grow", onToggle: { expandedMenuSection = (expandedMenuSection == "Save & grow") ? nil : "Save & grow" })
                    CollapsibleFlatSection(title: "Borrow", rows: [
                        FlatRow(title: "Get a loan", subtitle: "Personal, salary-backed, SME working capital", glyph: { AnyView(MoneyBagGlyph(size: 28)) }, action: onOpenLoans),
                        FlatRow(title: "Credit score", subtitle: "Free check, alternative data", glyph: { AnyView(NatureGlowingStar(size: 28)) }, action: onOpenCreditScore),
                        FlatRow(title: "Moto-Taxi Ownership", subtitle: "Save a 30% down payment, then convert to a loan for your own bike", symbol: "bicycle", tint: .accentTeal, action: onOpenMotoOwnership),
                    ], isExpanded: expandedMenuSection == "Borrow", onToggle: { expandedMenuSection = (expandedMenuSection == "Borrow") ? nil : "Borrow" })
                    CollapsibleFlatSection(title: "Transport", rows: [
                        FlatRow(title: "Rides", subtitle: "Request a ride or drive for real fares", glyph: { AnyView(TravelCar(size: 28)) }, action: onOpenRides),
                        FlatRow(title: "Designated driver", subtitle: "A driver takes you and your own car home", glyph: { AnyView(ObjectKey(size: 28)) }, action: onOpenDesignatedDriver),
                        FlatRow(title: "Bike rental", subtitle: "Rent a nearby bike or scooter, billed by the minute", glyph: { AnyView(BikeGlyph(size: 28)) }, action: onOpenBikeRental),
                        FlatRow(title: "Parking", subtitle: "Rent a nearby parking spot, billed by the hour", glyph: { AnyView(ParkingGlyph(size: 28)) }, action: onOpenParking),
                        FlatRow(title: "Bus", subtitle: "Book intercity bus seats or post your own route", glyph: { AnyView(PlaceGlyph(category: "BUS_STOP", size: 28)) }, action: onOpenBus),
                        // Real "tap to pay your moto-taxi fare" (2026-08-27, direct user
                        // follow-up: "now we can make pay for tax and moto as well") -- see
                        // MotoFareCollectScreenView.swift's own doc comment.
                        FlatRow(title: "Collect a moto fare", subtitle: "Drivers: tap or scan a rider's code to collect a real fare", symbol: "bicycle", tint: Color(hex: 0xD97706), action: onOpenMotoFareCollect),
                        FlatRow(title: "Vehicle inspection", subtitle: "Pay a mechanic to inspect a used car before you buy", glyph: { AnyView(WrenchGlyph(size: 28)) }, action: onOpenVehicleInspection),
                        FlatRow(title: "My vehicles", subtitle: "Track your car's estimated resale value", glyph: { AnyView(TravelCar(size: 28)) }, action: onOpenVehicleValuation),
                    ], isExpanded: expandedMenuSection == "Transport", onToggle: { expandedMenuSection = (expandedMenuSection == "Transport") ? nil : "Transport" })
                    CollapsibleFlatSection(title: "Community & trust", rows: [
                        FlatRow(title: "Trust score", subtitle: "How your neighbors see you on Marketplace, Jobs, and Property", glyph: { AnyView(NatureStar(size: 28)) }, action: onOpenTrustScore),
                        FlatRow(title: "Q&A", subtitle: "Ask a question, answer one, get adopted", glyph: { AnyView(SpeechBubbleGlyph(size: 28)) }, action: onOpenKnowledge),
                    ], isExpanded: expandedMenuSection == "Community & trust", onToggle: { expandedMenuSection = (expandedMenuSection == "Community & trust") ? nil : "Community & trust" })
                    // Kept last and separately labeled, not blended into the rows above:
                    // these two are role-gated (only assigned cash-agent operators can use
                    // them), same reasoning as Android's identical split.
                    CollapsibleFlatSection(title: "Cash agent tools", rows: [
                        FlatRow(title: "Agent till", subtitle: "For assigned cash-agent operators: cash-in, cash-out, till count", glyph: { AnyView(PlaceGlyph(category: "ITUNDA_AGENT", size: 28)) }, action: onOpenAgentOperator),
                        FlatRow(title: "Float marketplace", subtitle: "For assigned cash-agents: offer or request float from nearby agents", glyph: { AnyView(PlaceGlyph(category: "MARKET", size: 28)) }, action: onOpenFloatMarketplace),
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
                        FlatRow(title: "Switch your personal loan", trailing: "12% ~ 24%", trailingIsLink: true, glyph: { AnyView(MoneyBagGlyph(size: 28)) }, action: onOpenLoans),
                        FlatRow(title: "Switch your rent deposit loan", trailing: "9% ~ 15%", trailingIsLink: true, glyph: { AnyView(TravelHouse(size: 28)) }, action: onOpenLoans),
                        FlatRow(title: "Switch your SME loan", trailing: "11% ~ 22%", trailingIsLink: true, glyph: { AnyView(PlaceGlyph(category: "MARKET", size: 28)) }, action: onOpenLoans),
                    ], isExpanded: expandedMenuSection == "Switch & save", onToggle: { expandedMenuSection = (expandedMenuSection == "Switch & save") ? nil : "Switch & save" })
                }
                Group {
                    // Real fix (2026-08-10, matching Android's 2026-08-03 fix): both rows
                    // point at the same real Card screen "Card" already opens elsewhere on
                    // this screen -- a second, previously-dead entry point into it, not a
                    // separate feature.
                    CollapsibleFlatSection(title: "Cards", rows: [
                        FlatRow(title: "Itunda Card", trailing: "5% back on bills", trailingIsLink: true, glyph: { AnyView(ObjectCreditCard(size: 28)) }, action: onOpenCard),
                        FlatRow(title: "Virtual card", trailing: "Instant issue", glyph: { AnyView(ObjectCreditCard(size: 28)) }, action: onOpenCard),
                    ], isExpanded: expandedMenuSection == "Cards", onToggle: { expandedMenuSection = (expandedMenuSection == "Cards") ? nil : "Cards" })
                    CollapsibleFlatSection(title: "Services", rows: [
                        FlatRow(title: "Rent deposit protection", glyph: { AnyView(TravelHouse(size: 28)) }),
                        FlatRow(title: "Recurring payments", glyph: { AnyView(CalendarGlyph(size: 28)) }),
                        FlatRow(title: "Import recurring payments", symbol: "shippingbox.fill", tint: .accentGray),
                        FlatRow(title: "REG & WASAC bills", glyph: { AnyView(ObjectLightBulb(size: 28)) }, action: onOpenPayBillsMiniApp),
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
                        FlatRow(title: "Foreign currency account", trailing: "100% rate preference", trailingIsLink: true, glyph: { AnyView(GlobeGlyph(size: 28)) }, action: onOpenForeignCurrency),
                        FlatRow(title: "International transfer", glyph: { AnyView(GlobeGlyph(size: 28)) }, action: onOpenForeignCurrency),
                    ], isExpanded: expandedMenuSection == "Foreign currency", onToggle: { expandedMenuSection = (expandedMenuSection == "Foreign currency") ? nil : "Foreign currency" })
                    // Real fix (2026-08-10, matching Android's 2026-08-03 fix): all 4 rows
                    // are the same real RSE investing screen ("Invest" quick link already
                    // opens) -- routed there instead of sitting dead.
                    CollapsibleFlatSection(title: "Grow your money", rows: [
                        FlatRow(title: "RSE stocks", subtitle: "BOK, MTNR, BLR, IMR, CMR, EQTY", glyph: { AnyView(ChartIncreasingGlyph(size: 28)) }, action: onOpenInvest),
                        FlatRow(title: "Bonds & fixed income", trailing: "7.5% ~ 12%", trailingIsLink: true, glyph: { AnyView(PlaceGlyph(category: "BANK", size: 28)) }, action: onOpenInvest),
                        FlatRow(title: "IPO schedule", glyph: { AnyView(ChartIncreasingGlyph(size: 28)) }, action: onOpenInvest),
                        FlatRow(title: "Brokerage account", trailing: "Up to 30,000 RWF", trailingIsLink: true, glyph: { AnyView(PlaceGlyph(category: "BANK", size: 28)) }, action: onOpenInvest),
                    ], isExpanded: expandedMenuSection == "Grow your money", onToggle: { expandedMenuSection = (expandedMenuSection == "Grow your money") ? nil : "Grow your money" })
                    CollapsibleFlatSection(title: "Pension", rows: [
                        FlatRow(title: "Check my RSSB pension", glyph: { AnyView(PlaceGlyph(category: "BANK", size: 28)) }),
                        FlatRow(title: "Pension products", glyph: { AnyView(MoneyBagGlyph(size: 28)) }),
                    ], isExpanded: expandedMenuSection == "Pension", onToggle: { expandedMenuSection = (expandedMenuSection == "Pension") ? nil : "Pension" })
                    CollapsibleFlatSection(title: "Loans", rows: [
                        FlatRow(title: "Check my max limit", glyph: { AnyView(ChartIncreasingGlyph(size: 28)) }, action: onOpenLoans),
                        FlatRow(title: "Personal loan", trailing: "11% ~ 24%", trailingIsLink: true, glyph: { AnyView(MoneyBagGlyph(size: 28)) }, action: onOpenLoans),
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
                        FlatRow(title: "Report an issue with a transaction", glyph: { AnyView(WarningGlyph(size: 28)) }, showChevron: true, action: onOpenSupport),
                        FlatRow(title: "My support tickets", showChevron: true, action: onOpenSupport),
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
        .task {
            if let result = try? await NetworkClient.shared.getRewardTasks() {
                availableTaskCount = result.tasks.filter { $0.eligible && !$0.claimed }.count
            }
        }
    }
}
