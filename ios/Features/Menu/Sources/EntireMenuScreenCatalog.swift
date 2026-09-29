import SwiftUI
import CoreDesignSystem

// Real fix (2026-08-10): a second, independent list of the exact same 17
// sections/rows rendered in EntireMenuScreen's own body, built purely so search can
// filter across all of them without touching the two `Group { }` blocks there --
// those are a real, version-sensitive Swift ViewBuilder child-count workaround (see
// EntireMenuScreen.swift's own header comment on why 15 children needed splitting
// for Xcode 14.3.1/Swift 5.8.1), and this file has no way to `xcodebuild` verify a
// restructuring of them (App target CocoaPods/RN-bridge gap, documented elsewhere in
// this repo). Duplicating the row list is a small, deliberate trade against a real
// risk of a silent, unverifiable break in the one iOS screen most repeatedly
// identified as needing "organized, not a data dump" -- not touching that structure
// at all is the safer failure mode. Every title/action pair below is a literal copy
// of the matching row in EntireMenuScreen.swift's own body; if one changes, the
// other must too.
//
// Split into its own file (2026-09-02, Menu Feature-module decomposition) purely to
// keep EntireMenuScreen.swift under the 500-line new-file cap -- same reasoning as
// every other big-screen decomposition this session used to split into multiple
// files.
extension EntireMenuScreen {
    var searchableMenuSections: [(title: String, rows: [FlatRow])] {
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
                FlatRow(title: "Shop", subtitle: "Coupang-style commerce", glyph: { AnyView(ShoppingBagGlyph(size: 28)) }, action: onOpenShop),
                FlatRow(title: "Eats", subtitle: "Food delivery, order or deliver", glyph: { AnyView(PlaceGlyph(category: "RESTAURANT", size: 28)) }, action: onOpenEats),
                // Real Kigali public-transit stored-value balance (2026-08-27) -- see
                // TransitScreenView.swift's own doc comment for the full sourced account.
                FlatRow(title: "Transit", subtitle: "Top up and tap to pay your real Kigali bus fare", symbol: "bus.fill", tint: Color(hex: 0x2F8F5B), action: onOpenTransit),
                FlatRow(title: "Map", subtitle: "Real Rwanda map, self-hosted", glyph: { AnyView(PinGlyph(size: 28)) }, action: onOpenMap),
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
            ]),
            ("Mini apps", [
                FlatRow(title: "Account balance", showChevron: true, action: onOpenAccountBalanceMiniApp),
                FlatRow(title: "Pay bills", showChevron: true, action: onOpenPayBillsMiniApp),
                FlatRow(title: "Reward tasks", showChevron: true, action: onOpenRewardsMiniApp),
                FlatRow(title: "Insurance", showChevron: true, action: onOpenInsuranceMiniApp),
            ]),
            ("Accounts & cards", [
                FlatRow(title: "Open account", subtitle: "Itunda Account, other banks, RSE brokerage", glyph: { AnyView(PlaceGlyph(category: "BANK", size: 28)) }, action: onOpenOverview),
                FlatRow(title: "My assets", subtitle: "Accounts, loans, RSE holdings, cards, points", glyph: { AnyView(MoneyBagGlyph(size: 28)) }, action: onOpenOverview),
                FlatRow(title: "Spending", subtitle: "Real, ledger-based category breakdown", glyph: { AnyView(BarChartGlyph(size: 28)) }, action: onOpenSpending),
                FlatRow(title: "Family", subtitle: "Link a guardian or child, view read-only spending", glyph: { AnyView(FamilyGlyph(size: 28)) }, action: onOpenFamilyLink),
                FlatRow(title: "Subscriptions", subtitle: "Detected recurring payments + merchant billing plans", glyph: { AnyView(CalendarGlyph(size: 28)) }, action: onOpenSubscriptions),
                FlatRow(title: "Digital certificate", subtitle: "Sign agreements in Itunda", glyph: { AnyView(ObjectPen(size: 28)) }, action: onOpenCertificate),
            ]),
            ("Send & pay", [
                FlatRow(title: "Transfer", subtitle: "Auto-transfer, split a bill", symbol: "paperplane.fill", tint: .accentIndigo, action: onOpenTransferHub),
                FlatRow(title: "Request money", subtitle: "Generate a real payment request code", glyph: { AnyView(ReceiptGlyph(size: 28)) }, action: onOpenRequestMoney),
                FlatRow(title: "Auto top-up", subtitle: "Refill your account automatically from a linked account", glyph: { AnyView(RefreshCardGlyph(size: 28)) }, action: onOpenAutoTopUp),
                FlatRow(title: "Mobile plan", subtitle: "MTN, Airtel, broadband", glyph: { AnyView(ObjectMobilePhone(size: 28)) }, action: onOpenPayBillsMiniApp),
            ]),
            ("Save & grow", [
                FlatRow(title: "Round-up savings", subtitle: "Auto-save spare change from every transfer", symbol: "arrow.up.circle.fill", tint: .accentOrange, action: onOpenRoundUp),
                FlatRow(title: "12-month deposit", subtitle: "\(String(format: "%.2f", upfrontDepositAnnualRate))%/yr interest paid upfront, principal locked", glyph: { AnyView(LockGlyph(size: 28)) }, action: onOpenUpfrontDeposit),
            ]),
            ("Borrow", [
                FlatRow(title: "Get a loan", subtitle: "Personal, salary-backed, SME working capital", glyph: { AnyView(MoneyBagGlyph(size: 28)) }, action: onOpenLoans),
                FlatRow(title: "Credit score", subtitle: "Free check, alternative data", glyph: { AnyView(NatureGlowingStar(size: 28)) }, action: onOpenCreditScore),
                FlatRow(title: "Moto-Taxi Ownership", subtitle: "Save a 30% down payment, then convert to a loan for your own bike", symbol: "bicycle", tint: .accentTeal, action: onOpenMotoOwnership),
            ]),
            ("Transport", [
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
            ]),
            ("Community & trust", [
                FlatRow(title: "Trust score", subtitle: "How your neighbors see you on Marketplace, Jobs, and Property", glyph: { AnyView(NatureStar(size: 28)) }, action: onOpenTrustScore),
                FlatRow(title: "Q&A", subtitle: "Ask a question, answer one, get adopted", glyph: { AnyView(SpeechBubbleGlyph(size: 28)) }, action: onOpenKnowledge),
            ]),
            ("Cash agent tools", [
                FlatRow(title: "Agent till", subtitle: "For assigned cash-agent operators: cash-in, cash-out, till count", glyph: { AnyView(PlaceGlyph(category: "ITUNDA_AGENT", size: 28)) }, action: onOpenAgentOperator),
                FlatRow(title: "Float marketplace", subtitle: "For assigned cash-agents: offer or request float from nearby agents", glyph: { AnyView(PlaceGlyph(category: "MARKET", size: 28)) }, action: onOpenFloatMarketplace),
            ]),
            ("Switch & save", [
                FlatRow(title: "Switch your personal loan", trailing: "12% ~ 24%", trailingIsLink: true, glyph: { AnyView(MoneyBagGlyph(size: 28)) }, action: onOpenLoans),
                FlatRow(title: "Switch your rent deposit loan", trailing: "9% ~ 15%", trailingIsLink: true, glyph: { AnyView(TravelHouse(size: 28)) }, action: onOpenLoans),
                FlatRow(title: "Switch your SME loan", trailing: "11% ~ 22%", trailingIsLink: true, glyph: { AnyView(PlaceGlyph(category: "MARKET", size: 28)) }, action: onOpenLoans),
            ]),
            ("Cards", [
                FlatRow(title: "Itunda Card", trailing: "5% back on bills", trailingIsLink: true, glyph: { AnyView(ObjectCreditCard(size: 28)) }, action: onOpenCard),
                FlatRow(title: "Virtual card", trailing: "Instant issue", glyph: { AnyView(ObjectCreditCard(size: 28)) }, action: onOpenCard),
            ]),
            ("Services", [
                FlatRow(title: "Rent deposit protection", glyph: { AnyView(TravelHouse(size: 28)) }),
                FlatRow(title: "Recurring payments", glyph: { AnyView(CalendarGlyph(size: 28)) }),
                FlatRow(title: "Import recurring payments", symbol: "shippingbox.fill", tint: .accentGray),
                FlatRow(title: "REG & WASAC bills", glyph: { AnyView(ObjectLightBulb(size: 28)) }, action: onOpenPayBillsMiniApp),
                FlatRow(title: "Interest earned this month", glyph: { AnyView(MoneyBagGlyph(size: 28)) }, action: onClaimInterest),
                FlatRow(title: "SME income tax estimate", glyph: { AnyView(ReceiptGlyph(size: 28)) }),
                FlatRow(title: "Split a bill with friends", glyph: { AnyView(SplitBillDice(size: 28)) }, action: onSwitchToTalk),
                FlatRow(title: "Shared calendar", glyph: { AnyView(CalendarGlyph(size: 28)) }),
                FlatRow(title: "Kids' allowance tasks", glyph: { AnyView(ChildGlyph(size: 28)) }),
            ]),
            ("Foreign currency", [
                FlatRow(title: "Foreign currency account", trailing: "100% rate preference", trailingIsLink: true, glyph: { AnyView(GlobeGlyph(size: 28)) }, action: onOpenForeignCurrency),
                FlatRow(title: "International transfer", glyph: { AnyView(GlobeGlyph(size: 28)) }, action: onOpenForeignCurrency),
            ]),
            ("Grow your money", [
                FlatRow(title: "RSE stocks", subtitle: "BOK, MTNR, BLR, IMR, CMR, EQTY", glyph: { AnyView(ChartIncreasingGlyph(size: 28)) }, action: onOpenInvest),
                FlatRow(title: "Bonds & fixed income", trailing: "7.5% ~ 12%", trailingIsLink: true, glyph: { AnyView(PlaceGlyph(category: "BANK", size: 28)) }, action: onOpenInvest),
                FlatRow(title: "IPO schedule", glyph: { AnyView(ChartIncreasingGlyph(size: 28)) }, action: onOpenInvest),
                FlatRow(title: "Brokerage account", trailing: "Up to 30,000 RWF", trailingIsLink: true, glyph: { AnyView(PlaceGlyph(category: "BANK", size: 28)) }, action: onOpenInvest),
            ]),
            ("Pension", [
                FlatRow(title: "Check my RSSB pension", glyph: { AnyView(PlaceGlyph(category: "BANK", size: 28)) }),
                FlatRow(title: "Pension products", glyph: { AnyView(MoneyBagGlyph(size: 28)) }),
            ]),
            ("Loans", [
                FlatRow(title: "Check my max limit", glyph: { AnyView(ChartIncreasingGlyph(size: 28)) }, action: onOpenLoans),
                FlatRow(title: "Personal loan", trailing: "11% ~ 24%", trailingIsLink: true, glyph: { AnyView(MoneyBagGlyph(size: 28)) }, action: onOpenLoans),
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
                FlatRow(title: "Report an issue with a transaction", glyph: { AnyView(WarningGlyph(size: 28)) }, showChevron: true, action: onOpenSupport),
                FlatRow(title: "My support tickets", showChevron: true, action: onOpenSupport),
                FlatRow(title: "Announcements"),
                FlatRow(title: "USSD access", glyph: { AnyView(ObjectMobilePhone(size: 28)) }, showChevron: true, action: onOpenUssdSettings),
            ]),
        ]
    }

    var matchingSearchSections: [(title: String, rows: [FlatRow])] {
        let query = menuSearchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else { return [] }
        return searchableMenuSections.compactMap { section in
            let matches = section.rows.filter { $0.title.localizedCaseInsensitiveContains(query) }
            return matches.isEmpty ? nil : (section.title, matches)
        }
    }
}
