import SwiftUI
import CoreDesignSystem
import CoreNetwork
import FeatureMenu
import FeatureCredit
import FeatureMaps
import FeatureAssets
import FeatureCertificate
import FeatureIdentity
import FeatureSupport

// Real wrapper (2026-09-02, Menu Feature-module decomposition) around
// FeatureMenu's EntireMenuScreen, matching HomeTabContent's own established
// App-Feature-bridge role exactly. EntireMenuScreen itself is now a pure
// callback-driven catalog (matching Android's real MenuScreen.kt signature) with
// zero knowledge of any App-only or other-Feature-owned screen type -- this file
// is where ALL ~45 of its destinations actually get their state + presentation,
// the same role ContentView.swift's own showBank/showTransferHub/etc. already play
// for the 11 "bridge" callbacks that route further up into cross-tab state
// (Settings/ClaimInterest/SwitchToTalk/TransferHub/Shop/Eats/Marketplace/
// Community/Jobs/Property/Bank -- unchanged, passed straight through).
//
// Deliberately its OWN file rather than added directly to ContentView.swift:
// dumping ~47 new @State vars + ~45 sheet/fullScreenCover blocks into that
// already-large, already-once-baselined file would be exactly the "everything in
// one file" anti-pattern this whole Feature-module initiative exists to fix.
// None of these local state names collide with ContentView.swift's own
// similarly-named vars (e.g. its own showSacco/showLoans/showInvest, used by
// BankView's coop rail) despite naming overlap -- they're separate `@State` storage
// in a separate struct, and deliberately kept separate rather than shared: a
// single Bool bound to two independent `.sheet` modifiers in two different parts
// of the view tree (this tab vs. Bank's own fullScreenCover) risks both firing at
// once since SwiftUI's TabView keeps every tab's subtree mounted, which is also
// why ContentView.swift's own showSacco/etc. doc comment already documents
// deliberately duplicating rather than sharing state across independent
// presentation contexts.
struct MenuTabContent: View {
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
    var onOpenBank: () -> Void = {}

    @State private var showTransit = false
    @State private var showTransitCollect = false
    @State private var showMap = false
    @State private var showInvest = false
    @State private var showWeeklySavings = false
    @State private var showGrow31Savings = false
    @State private var showYouthAccount = false
    @State private var showCard = false
    @State private var showGroupAccounts = false
    @State private var showIkimina = false
    @State private var showSacco = false
    @State private var showHarvestAdvance = false
    @State private var showVupLoan = false
    @State private var showStudentLoan = false
    @State private var showRewardsMiniApp = false
    @State private var showPayBillsMiniApp = false
    @State private var showAccountBalanceMiniApp = false
    @State private var showInsuranceMiniApp = false
    @State private var showOverview = false
    @State private var showOverviewTransfer = false
    @State private var overviewTransferAccount: TransferFromAccount?
    @State private var showSpending = false
    @State private var showFamilyLink = false
    @State private var showSubscriptions = false
    @State private var showCertificate = false
    @State private var showIdentity = false
    @State private var showRequestMoney = false
    @State private var showAutoTopUp = false
    @State private var showRoundUp = false
    @State private var showUpfrontDeposit = false
    @State private var showLoans = false
    @State private var showCreditScore = false
    @State private var showMotoOwnership = false
    @State private var showRides = false
    @State private var showDesignatedDriver = false
    @State private var showBikeRental = false
    @State private var showParking = false
    @State private var showBus = false
    @State private var showMotoFareCollect = false
    @State private var showVehicleInspection = false
    @State private var showVehicleValuation = false
    @State private var showTrustScore = false
    @State private var showKnowledge = false
    @State private var showAgentOperator = false
    @State private var showFloatMarketplace = false
    @State private var showForeignCurrency = false
    @State private var showSupport = false

    var body: some View {
        EntireMenuScreen(
            onOpenSettings: onOpenSettings,
            onClaimInterest: onClaimInterest,
            onSwitchToTalk: onSwitchToTalk,
            onOpenTransferHub: onOpenTransferHub,
            onOpenShop: onOpenShop,
            onOpenEats: onOpenEats,
            onOpenMarketplace: onOpenMarketplace,
            onOpenCommunity: onOpenCommunity,
            onOpenJobs: onOpenJobs,
            onOpenProperty: onOpenProperty,
            onOpenBank: onOpenBank,
            onOpenTransit: { showTransit = true },
            onOpenMap: { showMap = true },
            onOpenInvest: { showInvest = true },
            onOpenWeeklySavings: { showWeeklySavings = true },
            onOpenGrow31Savings: { showGrow31Savings = true },
            onOpenYouthAccount: { showYouthAccount = true },
            onOpenCard: { showCard = true },
            onOpenGroupAccounts: { showGroupAccounts = true },
            onOpenIkimina: { showIkimina = true },
            onOpenSacco: { showSacco = true },
            onOpenHarvestAdvance: { showHarvestAdvance = true },
            onOpenVupLoan: { showVupLoan = true },
            onOpenStudentLoan: { showStudentLoan = true },
            onOpenRewardsMiniApp: { showRewardsMiniApp = true },
            onOpenPayBillsMiniApp: { showPayBillsMiniApp = true },
            onOpenAccountBalanceMiniApp: { showAccountBalanceMiniApp = true },
            onOpenInsuranceMiniApp: { showInsuranceMiniApp = true },
            onOpenOverview: { showOverview = true },
            onOpenSpending: { showSpending = true },
            onOpenFamilyLink: { showFamilyLink = true },
            onOpenSubscriptions: { showSubscriptions = true },
            onOpenCertificate: { showCertificate = true },
            onOpenIdentity: { showIdentity = true },
            onOpenRequestMoney: { showRequestMoney = true },
            onOpenAutoTopUp: { showAutoTopUp = true },
            onOpenRoundUp: { showRoundUp = true },
            onOpenUpfrontDeposit: { showUpfrontDeposit = true },
            onOpenLoans: { showLoans = true },
            onOpenCreditScore: { showCreditScore = true },
            onOpenMotoOwnership: { showMotoOwnership = true },
            onOpenRides: { showRides = true },
            onOpenDesignatedDriver: { showDesignatedDriver = true },
            onOpenBikeRental: { showBikeRental = true },
            onOpenParking: { showParking = true },
            onOpenBus: { showBus = true },
            onOpenMotoFareCollect: { showMotoFareCollect = true },
            onOpenVehicleInspection: { showVehicleInspection = true },
            onOpenVehicleValuation: { showVehicleValuation = true },
            onOpenTrustScore: { showTrustScore = true },
            onOpenKnowledge: { showKnowledge = true },
            onOpenAgentOperator: { showAgentOperator = true },
            onOpenFloatMarketplace: { showFloatMarketplace = true },
            onOpenForeignCurrency: { showForeignCurrency = true },
            onOpenSupport: { showSupport = true }
        )
        .sheet(isPresented: $showTransit) {
            TransitScreenView(onBack: { showTransit = false }, onOpenCollect: { showTransitCollect = true })
        }
        .sheet(isPresented: $showTransitCollect) {
            TransitCollectScreenView(onBack: { showTransitCollect = false })
        }
        .sheet(isPresented: $showMap) {
            MapScreenView()
        }
        .sheet(isPresented: $showInvest) {
            InvestScreenView(onBack: { showInvest = false })
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
        .sheet(isPresented: $showVupLoan) {
            VupLoanScreenView(onBack: { showVupLoan = false })
        }
        .sheet(isPresented: $showStudentLoan) {
            StudentLoanScreenView(onBack: { showStudentLoan = false })
        }
        .sheet(isPresented: $showRewardsMiniApp) {
            SaroniteRewardTasksView()
        }
        .sheet(isPresented: $showPayBillsMiniApp) {
            SaronitePayBillsView()
        }
        .sheet(isPresented: $showAccountBalanceMiniApp) {
            SaroniteAccountBalanceView()
        }
        .sheet(isPresented: $showInsuranceMiniApp) {
            SaroniteInsuranceView()
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
                onOpenRewards: { showOverview = false; showRewardsMiniApp = true },
                onSend: { accountId, accountName, balance in
                    overviewTransferAccount = TransferFromAccount(id: accountId, name: accountName, balance: balance)
                    showOverviewTransfer = true
                }
            )
        }
        .fullScreenCover(isPresented: $showOverviewTransfer) {
            TransferFlowContainer(
                availableBalance: overviewTransferAccount?.balance ?? 0,
                fromAccount: overviewTransferAccount,
                onDone: { showOverviewTransfer = false; overviewTransferAccount = nil }
            )
        }
        .sheet(isPresented: $showSpending) {
            SpendingScreenView(onBack: { showSpending = false })
        }
        .sheet(isPresented: $showFamilyLink) {
            FamilyLinkScreenView(onBack: { showFamilyLink = false })
        }
        .sheet(isPresented: $showSubscriptions) {
            SubscriptionsScreenView(onBack: { showSubscriptions = false })
        }
        .sheet(isPresented: $showCertificate) {
            CertificateScreenView(onBack: { showCertificate = false })
        }
        .sheet(isPresented: $showIdentity) {
            IdentityScreenView(onBack: { showIdentity = false })
        }
        .sheet(isPresented: $showRequestMoney) {
            RequestMoneyScreenView(onBack: { showRequestMoney = false })
        }
        .sheet(isPresented: $showAutoTopUp) {
            AutoTopUpScreenView(onBack: { showAutoTopUp = false })
        }
        .sheet(isPresented: $showRoundUp) {
            RoundUpSettingsView(onBack: { showRoundUp = false })
        }
        .sheet(isPresented: $showUpfrontDeposit) {
            UpfrontDepositScreenView(onBack: { showUpfrontDeposit = false })
        }
        .sheet(isPresented: $showLoans) {
            LoansScreenView(onBack: { showLoans = false })
        }
        .sheet(isPresented: $showCreditScore) {
            CreditScoreScreenView(onBack: { showCreditScore = false })
        }
        .sheet(isPresented: $showMotoOwnership) {
            MotoOwnershipScreenView(onBack: { showMotoOwnership = false })
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
        .sheet(isPresented: $showMotoFareCollect) {
            MotoFareCollectScreenView(onBack: { showMotoFareCollect = false })
        }
        .sheet(isPresented: $showVehicleInspection) {
            VehicleInspectionScreenView(onBack: { showVehicleInspection = false })
        }
        .sheet(isPresented: $showVehicleValuation) {
            VehicleValuationScreenView(onBack: { showVehicleValuation = false })
        }
        .sheet(isPresented: $showTrustScore) {
            TrustScoreScreenView(onBack: { showTrustScore = false })
        }
        .sheet(isPresented: $showKnowledge) {
            KnowledgeScreenView(onBack: { showKnowledge = false })
        }
        .sheet(isPresented: $showAgentOperator) {
            AgentOperatorScreenView(onBack: { showAgentOperator = false })
        }
        .sheet(isPresented: $showFloatMarketplace) {
            FloatMarketplaceScreenView(onBack: { showFloatMarketplace = false })
        }
        .sheet(isPresented: $showForeignCurrency) {
            ForeignCurrencyScreenView(onBack: { showForeignCurrency = false })
        }
        .sheet(isPresented: $showSupport) {
            SupportScreenView(onBack: { showSupport = false })
        }
    }
}
