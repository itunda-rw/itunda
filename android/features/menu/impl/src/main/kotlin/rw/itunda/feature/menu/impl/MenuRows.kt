package rw.itunda.feature.menu.impl

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.ui.unit.dp
import rw.itunda.core.designsystem.components.FlatRow
import rw.itunda.core.designsystem.itundaface.BarChartGlyph
import rw.itunda.core.designsystem.itundaface.BellGlyph
import rw.itunda.core.designsystem.itundaface.BikeGlyph
import rw.itunda.core.designsystem.itundaface.BriefcaseGlyph
import rw.itunda.core.designsystem.itundaface.CalendarGlyph
import rw.itunda.core.designsystem.itundaface.ChartIncreasingGlyph
import rw.itunda.core.designsystem.itundaface.ChildGlyph
import rw.itunda.core.designsystem.itundaface.FamilyGlyph
import rw.itunda.core.designsystem.itundaface.GiftBox
import rw.itunda.core.designsystem.itundaface.GlobeGlyph
import rw.itunda.core.designsystem.itundaface.HandshakeGlyph
import rw.itunda.core.designsystem.itundaface.LockGlyph
import rw.itunda.core.designsystem.itundaface.MoneyBagGlyph
import rw.itunda.core.designsystem.itundaface.NatureGlowingStar
import rw.itunda.core.designsystem.itundaface.NatureStar
import rw.itunda.core.designsystem.itundaface.ObjectCreditCard
import rw.itunda.core.designsystem.itundaface.ObjectKey
import rw.itunda.core.designsystem.itundaface.ObjectLightBulb
import rw.itunda.core.designsystem.itundaface.ObjectMobilePhone
import rw.itunda.core.designsystem.itundaface.ObjectPen
import rw.itunda.core.designsystem.itundaface.ParkingGlyph
import rw.itunda.core.designsystem.itundaface.PinGlyph
import rw.itunda.core.designsystem.itundaface.PlaceBank
import rw.itunda.core.designsystem.itundaface.PlaceBusStop
import rw.itunda.core.designsystem.itundaface.PlaceItundaAgent
import rw.itunda.core.designsystem.itundaface.PlaceMarket
import rw.itunda.core.designsystem.itundaface.PlaceRestaurant
import rw.itunda.core.designsystem.itundaface.PlaceSchool
import rw.itunda.core.designsystem.itundaface.QuestionGlyph
import rw.itunda.core.designsystem.itundaface.ReceiptGlyph
import rw.itunda.core.designsystem.itundaface.RefreshCardGlyph
import rw.itunda.core.designsystem.itundaface.SeedlingGlyph
import rw.itunda.core.designsystem.itundaface.ShieldEmojiGlyph
import rw.itunda.core.designsystem.itundaface.ShoppingBagGlyph
import rw.itunda.core.designsystem.itundaface.SpeechBubbleGlyph
import rw.itunda.core.designsystem.itundaface.SplitBillDice
import rw.itunda.core.designsystem.itundaface.TravelCar
import rw.itunda.core.designsystem.itundaface.TravelHouse
import rw.itunda.core.designsystem.itundaface.VoucherTicket
import rw.itunda.core.designsystem.itundaface.WarningGlyph
import rw.itunda.core.designsystem.itundaface.WrenchGlyph
import rw.itunda.core.designsystem.theme.AccentGray
import rw.itunda.core.designsystem.theme.AccentIndigo
import rw.itunda.core.designsystem.theme.AccentTeal
import rw.itunda.core.designsystem.theme.IdsIcons

// Moved here from :app's ItundaAppScreen.kt (2026-09-02, Menu Feature-module
// decomposition) -- all the row-list definitions, split out of MenuScreen.kt into
// their own file purely to stay under the 500-line new-file cap; same reasoning as
// every other multi-file slice this session.
internal fun menuSections(
    availableTaskCount: Int?,
    onOpenShop: () -> Unit, onOpenEats: () -> Unit, onOpenMap: () -> Unit,
    onOpenMarketplace: () -> Unit, onOpenCommunity: () -> Unit, onOpenJobs: () -> Unit, onOpenProperty: () -> Unit,
    onOpenRewardTasksMiniApp: () -> Unit, onOpenInvest: () -> Unit,
    onOpenWeeklySavings: () -> Unit, onOpenGrow31Savings: () -> Unit,
    onOpenOverview: () -> Unit, onOpenCard: () -> Unit, onOpenTransit: () -> Unit, onOpenSpending: () -> Unit,
    onOpenGroupAccounts: () -> Unit, onOpenFamilyLink: () -> Unit, onOpenForeignCurrency: () -> Unit,
    onOpenSubscriptions: () -> Unit, onOpenCertificate: () -> Unit,
    onOpenTransferHub: () -> Unit, onOpenRequestMoney: () -> Unit, onOpenAutoTopUp: () -> Unit,
    onOpenPayBillsMiniApp: () -> Unit,
    onOpenUpfrontDeposit: () -> Unit, onOpenYouthAccount: () -> Unit, onOpenIkimina: () -> Unit, onOpenSacco: () -> Unit,
    onOpenLoans: () -> Unit, onOpenCreditScore: () -> Unit, onOpenHarvestAdvance: () -> Unit,
    onOpenVupLoan: () -> Unit, onOpenStudentLoan: () -> Unit, onOpenMotoOwnership: () -> Unit,
    onOpenRides: () -> Unit, onOpenDesignatedDriver: () -> Unit, onOpenBikeRental: () -> Unit,
    onOpenParking: () -> Unit, onOpenBus: () -> Unit, onOpenMotoFareCollect: () -> Unit,
    onOpenVehicleInspection: () -> Unit, onOpenVehicleValuation: () -> Unit,
    onOpenTrustScore: () -> Unit, onOpenKnowledge: () -> Unit,
    onOpenAgentOperator: () -> Unit, onOpenFloatMarketplace: () -> Unit,
    onClaimInterest: () -> Unit, onSwitchToTalk: () -> Unit, onOpenSupport: () -> Unit,
): List<Pair<String, List<FlatRow>>> {
    val quickLinksEverydayRows = listOf(
        FlatRow("Shop", subtitle = "Coupang-style commerce", glyph = { ShoppingBagGlyph(size = 28.dp) }, onClick = onOpenShop),
        FlatRow("Eats", subtitle = "Food delivery, order or deliver", glyph = { PlaceRestaurant(size = 28.dp) }, onClick = onOpenEats),
        FlatRow("Map", subtitle = "Real Rwanda map, self-hosted", glyph = { PinGlyph(size = 28.dp) }, onClick = onOpenMap),
    )
    val quickLinksNeighbourhoodRows = listOf(
        FlatRow("Marketplace", subtitle = "당근마켓-style neighborhood buy/sell", glyph = { PlaceMarket(size = 28.dp) }, onClick = onOpenMarketplace),
        FlatRow("Community", subtitle = "Neighborhood life, local questions and posts", glyph = { SpeechBubbleGlyph(size = 28.dp) }, onClick = onOpenCommunity),
        FlatRow("Jobs", subtitle = "Neighborhood gigs and part-time work", glyph = { BriefcaseGlyph(size = 28.dp) }, onClick = onOpenJobs),
        FlatRow("Property", subtitle = "Neighborhood rentals and sales", glyph = { TravelHouse(size = 28.dp) }, onClick = onOpenProperty),
    )
    val quickLinksMoneyRows = listOf(
        FlatRow(
            "Benefits",
            subtitle = when (val count = availableTaskCount) {
                null, 0 -> "Points, coupons, rewards"
                1 -> "1 reward task available"
                else -> "$count reward tasks available"
            },
            glyph = { GiftBox(size = 28.dp) },
            onClick = onOpenRewardTasksMiniApp,
        ),
        FlatRow("Invest", subtitle = "RSE stocks, real portfolio", glyph = { ChartIncreasingGlyph(size = 28.dp) }, onClick = onOpenInvest),
        FlatRow("26-Week Savings", subtitle = "Escalating auto-save, streak bonus", glyph = { MoneyBagGlyph(size = 28.dp) }, onClick = onOpenWeeklySavings),
        FlatRow("31-Day Savings", subtitle = "Daily streak, tiered bonus rate", glyph = { MoneyBagGlyph(size = 28.dp) }, onClick = onOpenGrow31Savings),
    )
    val accountsRows = listOf(
        FlatRow("Open account", subtitle = "Itunda Account, other banks, RSE brokerage", glyph = { PlaceBank(size = 28.dp) }, onClick = onOpenOverview),
        FlatRow("My assets", subtitle = "Accounts, loans, RSE holdings, cards, points", glyph = { MoneyBagGlyph(size = 28.dp) }, onClick = onOpenOverview),
        FlatRow("Card", subtitle = "App-controlled spend limits, one-tap freeze", glyph = { ObjectCreditCard(size = 28.dp) }, onClick = onOpenCard),
        FlatRow("Transit", subtitle = "Top up and tap to pay your real Kigali bus fare", glyph = { PlaceBusStop(size = 28.dp) }, onClick = onOpenTransit),
        FlatRow("Spending", subtitle = "Real, ledger-based category breakdown", glyph = { BarChartGlyph(size = 28.dp) }, onClick = onOpenSpending),
        FlatRow("Group account", subtitle = "Shared account with dues and split expenses", glyph = { HandshakeGlyph(size = 28.dp) }, onClick = onOpenGroupAccounts),
        FlatRow("Family", subtitle = "Link a guardian or child, view read-only spending", glyph = { FamilyGlyph(size = 28.dp) }, onClick = onOpenFamilyLink),
        FlatRow("Foreign currency", subtitle = "Hold and convert USD, EUR, GBP", glyph = { GlobeGlyph(size = 28.dp) }, onClick = onOpenForeignCurrency),
        FlatRow("Subscriptions", subtitle = "Detected recurring payments + merchant billing plans", glyph = { CalendarGlyph(size = 28.dp) }, onClick = onOpenSubscriptions),
        FlatRow("Digital certificate", subtitle = "Sign agreements in Itunda", glyph = { ObjectPen(size = 28.dp) }, onClick = onOpenCertificate),
    )
    val sendPayRows = listOf(
        FlatRow("Transfer", subtitle = "Auto-transfer, split a bill", icon = IdsIcons.Send, iconColor = AccentIndigo, onClick = onOpenTransferHub),
        FlatRow("Request money", subtitle = "Generate a real payment request code", glyph = { ReceiptGlyph(size = 28.dp) }, onClick = onOpenRequestMoney),
        FlatRow("Auto top-up", subtitle = "Refill your account automatically from a linked account", glyph = { RefreshCardGlyph(size = 28.dp) }, onClick = onOpenAutoTopUp),
        FlatRow("Mobile plan", subtitle = "MTN, Airtel, broadband", glyph = { ObjectMobilePhone(size = 28.dp) }, onClick = onOpenPayBillsMiniApp),
    )
    val saveGrowRows = listOf(
        FlatRow("26-week savings", subtitle = "Escalating weekly deposit plan", glyph = { MoneyBagGlyph(size = 28.dp) }, onClick = onOpenWeeklySavings),
        FlatRow("31-day savings", subtitle = "Daily streak, tiered bonus rate", glyph = { MoneyBagGlyph(size = 28.dp) }, onClick = onOpenGrow31Savings),
        FlatRow("12-month deposit", subtitle = "Interest paid upfront, principal locked", glyph = { LockGlyph(size = 28.dp) }, onClick = onOpenUpfrontDeposit),
        FlatRow("Youth account", subtitle = "Capped starter account, ages 7-18", glyph = { ChildGlyph(size = 28.dp) }, onClick = onOpenYouthAccount),
        FlatRow("Ikimina", subtitle = "Rotating savings group -- everyone takes a turn", glyph = { HandshakeGlyph(size = 28.dp) }, onClick = onOpenIkimina),
        FlatRow("SACCO shares", subtitle = "Buy cooperative shares, earn a real dividend", glyph = { PlaceBank(size = 28.dp) }, onClick = onOpenSacco),
    )
    val borrowRows = listOf(
        FlatRow("Get a loan", subtitle = "Personal, salary-backed, SME working capital", glyph = { MoneyBagGlyph(size = 28.dp) }, onClick = onOpenLoans),
        FlatRow("Credit score", subtitle = "Free check, alternative data", glyph = { NatureGlowingStar(size = 28.dp) }, onClick = onOpenCreditScore),
        FlatRow("Harvest advance", subtitle = "Coffee cooperative input financing", glyph = { SeedlingGlyph(size = 28.dp) }, onClick = onOpenHarvestAdvance),
        FlatRow("VUP Financial Services", subtitle = "Means-tested government microloan for farming, livestock, business", glyph = { ShieldEmojiGlyph(size = 28.dp) }, onClick = onOpenVupLoan),
        FlatRow("Student loan", subtitle = "BRD higher-education loan -- 11% undergraduate, 12% postgraduate", glyph = { PlaceSchool(size = 28.dp) }, onClick = onOpenStudentLoan),
        FlatRow("Moto-Taxi Ownership", subtitle = "Save a 30% down payment, then convert to a loan for your own bike", icon = Icons.Outlined.DirectionsBike, iconColor = AccentTeal, onClick = onOpenMotoOwnership),
    )
    val transportRows = listOf(
        FlatRow("Rides", subtitle = "Request a ride or drive for real fares", glyph = { TravelCar(size = 28.dp) }, onClick = onOpenRides),
        FlatRow("Designated driver", subtitle = "A driver takes you and your own car home", glyph = { ObjectKey(size = 28.dp) }, onClick = onOpenDesignatedDriver),
        FlatRow("Bike rental", subtitle = "Rent a nearby bike or scooter, billed by the minute", glyph = { BikeGlyph(size = 28.dp) }, onClick = onOpenBikeRental),
        FlatRow("Parking", subtitle = "Rent a nearby parking spot, billed by the hour", glyph = { ParkingGlyph(size = 28.dp) }, onClick = onOpenParking),
        FlatRow("Bus", subtitle = "Book intercity bus seats or post your own route", glyph = { PlaceBusStop(size = 28.dp) }, onClick = onOpenBus),
        FlatRow("Collect a moto fare", subtitle = "Drivers: tap or scan a rider's code to collect a real fare", glyph = { BikeGlyph(size = 28.dp) }, onClick = onOpenMotoFareCollect),
        FlatRow("Vehicle inspection", subtitle = "Pay a mechanic to inspect a used car before you buy", glyph = { WrenchGlyph(size = 28.dp) }, onClick = onOpenVehicleInspection),
        FlatRow("My vehicles", subtitle = "Track your car's estimated resale value", glyph = { TravelCar(size = 28.dp) }, onClick = onOpenVehicleValuation),
    )
    val communityTrustRows = listOf(
        FlatRow("Trust score", subtitle = "How your neighbors see you on Marketplace, Jobs, and Property", glyph = { NatureStar(size = 28.dp) }, onClick = onOpenTrustScore),
        FlatRow("Q&A", subtitle = "Ask a question, answer one, get adopted", glyph = { SpeechBubbleGlyph(size = 28.dp) }, onClick = onOpenKnowledge),
    )
    val cashAgentRows = listOf(
        FlatRow("Agent till", subtitle = "For assigned cash-agent operators: cash-in, cash-out, till count", glyph = { PlaceItundaAgent(size = 28.dp) }, onClick = onOpenAgentOperator),
        FlatRow("Float marketplace", subtitle = "For assigned cash-agents: offer or request float from nearby agents", glyph = { PlaceMarket(size = 28.dp) }, onClick = onOpenFloatMarketplace),
    )
    val switchSaveRows = listOf(
        FlatRow("Switch your personal loan", trailing = "12% ~ 24%", trailingIsLink = true, glyph = { MoneyBagGlyph(size = 28.dp) }, onClick = onOpenLoans),
        FlatRow("Switch your rent deposit loan", trailing = "9% ~ 15%", trailingIsLink = true, glyph = { TravelHouse(size = 28.dp) }, onClick = onOpenLoans),
        FlatRow("Switch your SME loan", trailing = "11% ~ 22%", trailingIsLink = true, glyph = { PlaceMarket(size = 28.dp) }, onClick = onOpenLoans)
    )
    val cardsRows = listOf(
        FlatRow("Itunda Card", trailing = "5% back on bills", trailingIsLink = true, glyph = { ObjectCreditCard(size = 28.dp) }, onClick = onOpenCard),
        FlatRow("Virtual card", trailing = "Instant issue", glyph = { ObjectCreditCard(size = 28.dp) }, onClick = onOpenCard)
    )
    val servicesRows = listOf(
        FlatRow("Rent deposit protection", glyph = { TravelHouse(size = 28.dp) }),
        FlatRow("Recurring payments", glyph = { CalendarGlyph(size = 28.dp) }, onClick = onOpenSubscriptions),
        FlatRow("Import recurring payments", icon = Icons.Outlined.LocalShipping, iconColor = AccentGray),
        FlatRow("REG & WASAC bills", glyph = { ObjectLightBulb(size = 28.dp) }, onClick = onOpenPayBillsMiniApp),
        FlatRow("Interest earned this month", glyph = { MoneyBagGlyph(size = 28.dp) }, onClick = onClaimInterest),
        FlatRow("SME income tax estimate", glyph = { ReceiptGlyph(size = 28.dp) }),
        FlatRow("Split a bill with friends", glyph = { SplitBillDice(size = 28.dp) }, onClick = onSwitchToTalk),
        FlatRow("Shared calendar", glyph = { CalendarGlyph(size = 28.dp) }),
        FlatRow("Kids' allowance tasks", glyph = { ChildGlyph(size = 28.dp) })
    )
    val foreignCurrencyRows = listOf(
        FlatRow("Foreign currency account", trailing = "100% rate preference", trailingIsLink = true, glyph = { GlobeGlyph(size = 28.dp) }, onClick = onOpenForeignCurrency),
        FlatRow("International transfer", glyph = { GlobeGlyph(size = 28.dp) }, onClick = onOpenForeignCurrency)
    )
    val growMoneyRows = listOf(
        FlatRow("RSE stocks", subtitle = "BOK, MTNR, BLR, IMR, CMR, EQTY", glyph = { ChartIncreasingGlyph(size = 28.dp) }, onClick = onOpenInvest),
        FlatRow("Bonds & fixed income", trailing = "7.5% ~ 12%", trailingIsLink = true, glyph = { PlaceBank(size = 28.dp) }, onClick = onOpenInvest),
        FlatRow("IPO schedule", glyph = { ChartIncreasingGlyph(size = 28.dp) }, onClick = onOpenInvest),
        FlatRow("Brokerage account", trailing = "Up to 30,000 RWF", trailingIsLink = true, glyph = { PlaceBank(size = 28.dp) }, onClick = onOpenInvest)
    )
    val pensionRows = listOf(
        FlatRow("Check my RSSB pension", glyph = { PlaceBank(size = 28.dp) }),
        FlatRow("Pension products", glyph = { MoneyBagGlyph(size = 28.dp) })
    )
    val loansRows = listOf(
        FlatRow("Check my max limit", glyph = { ChartIncreasingGlyph(size = 28.dp) }, onClick = onOpenLoans),
        FlatRow("Personal loan", trailing = "11% ~ 24%", trailingIsLink = true, glyph = { MoneyBagGlyph(size = 28.dp) }, onClick = onOpenLoans)
    )
    val supportRows = listOf(
        FlatRow("FAQ", glyph = { QuestionGlyph(size = 28.dp) }),
        FlatRow("Live chat", glyph = { SpeechBubbleGlyph(size = 28.dp) }),
        FlatRow("Call support", glyph = { ObjectMobilePhone(size = 28.dp) }),
        FlatRow("Report an issue with a transaction", glyph = { WarningGlyph(size = 28.dp) }, showChevron = true, onClick = onOpenSupport),
        FlatRow("My support tickets", glyph = { VoucherTicket(size = 28.dp) }, showChevron = true, onClick = onOpenSupport),
        FlatRow("Announcements", glyph = { BellGlyph(size = 28.dp) })
    )
    return listOf(
        "Everyday" to quickLinksEverydayRows,
        "Your neighbourhood" to quickLinksNeighbourhoodRows,
        "Money tools" to quickLinksMoneyRows,
        "Accounts & cards" to accountsRows,
        "Send & pay" to sendPayRows,
        "Save & grow" to saveGrowRows,
        "Borrow" to borrowRows,
        "Transport" to transportRows,
        "Community & trust" to communityTrustRows,
        "Cash agent tools" to cashAgentRows,
        "Switch & save" to switchSaveRows,
        "Cards" to cardsRows,
        "Services" to servicesRows,
        "Foreign currency" to foreignCurrencyRows,
        "Grow your money" to growMoneyRows,
        "Pension" to pensionRows,
        "Loans" to loansRows,
        "Support" to supportRows,
    )
}
