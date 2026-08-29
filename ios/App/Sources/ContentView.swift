import SwiftUI
import UIKit
import CoreNetwork
import CoreDesignSystem
import FeatureBanking
import FeatureMaps
import FeaturePayments

// Fixed (2026-07-11): every Text() in this file used .font(.system(size:weight:)) --
// a fixed point size that doesn't grow or shrink with iOS's Dynamic Type
// accessibility setting. Same bug, same fix as CoreDesignSystem's IDS.swift/
// IdsTheme.swift (see IDS.swift's Typography struct for the full reasoning). This
// file didn't import CoreDesignSystem for anything else at the time (a real import
// was added later, 2026-08-22, for ToastOverlay -- see the TabView's own doc
// comment below) -- kept as its own local copy rather than switching to
// IDS.scaledFont now that the import exists, since that's an unrelated cleanup out
// of scope for today's change.
// Real typeface fix (2026-08-13) -- see IDS.scaledFont's own doc comment for the full
// sourced account; kept as its own local copy for the same reason this function
// duplicates IDS.scaledFont's logic in the first place (see the comment above).
private func pretendardFont(size: CGFloat, weight: UIFont.Weight) -> UIFont {
    let postscriptName: String
    switch weight {
    case .bold, .heavy, .black: postscriptName = "Pretendard-Bold"
    case .semibold: postscriptName = "Pretendard-SemiBold"
    case .medium: postscriptName = "Pretendard-Medium"
    default: postscriptName = "Pretendard-Regular"
    }
    return UIFont(name: postscriptName, size: size) ?? UIFont.systemFont(ofSize: size, weight: weight)
}

func scaledFont(size: CGFloat, weight: UIFont.Weight, relativeTo style: UIFont.TextStyle) -> Font {
    Font(UIFontMetrics(forTextStyle: style).scaledFont(for: pretendardFont(size: size, weight: weight)))
}

// Tab taxonomy history: Home/Benefits/Shop/Pay/All (2026-07-11, matching Android's
// ItundaAppScreen.kt at the time) -> Home/Shop/Hood/Talk/My (2026-07-18, matching
// Android's own super-app nav redesign the same day -- see ContentView.body's own
// comment on the TabView for the current rationale).
/// Real savings deposit/claim flow (2026-07-12) -- see SavingsFlowContainer.swift.
enum SavingsFlowStep: Identifiable {
    case deposit(goalId: String, goalName: String)
    case claimInterest

    var id: String {
        switch self {
        case .deposit(let goalId, _): return "deposit-\(goalId)"
        case .claimInterest: return "claim"
        }
    }
}

struct ContentView: View {
    @State private var selectedTab = 0
    // Real "message seller" hand-off from Hood to Talk (2026-07-18) -- see
    // TalkScreen.swift's own doc comment on `pendingConversationId` for the full
    // mechanism (mirrors bank-mfe's BankDashboard.tsx pendingConversationId/
    // onConsumedInitial pattern and Android's identical ItundaAppScreen.kt state).
    @State private var pendingConversationId: String?
    // Shop/Eats/Marketplace/Community/Jobs/Property all lost their own primary tab
    // (2026-08-10, see the TabView's own doc comment below) -- each reached as a real
    // full-screen-cover entry point from Explore instead, same established pattern
    // showSacco/showIkimina/etc. below already use. Property previously used a
    // pendingHoodOpenProperty deep-link into Hood's own chip; no longer needed now
    // that Property is its own direct destination.
    @State private var showShop = false
    @State private var showEats = false
    @State private var showMarketplace = false
    @State private var showCommunity = false
    @State private var showJobs = false
    @State private var showProperty = false
    // Real account/savings data (2026-07-11) -- see BankViewModel.swift for why this
    // lives here rather than inside BankView's own module.
    @StateObject private var bankViewModel = BankViewModel()
    // Real send-money flow (2026-07-12) -- "Send money now" was decorative until
    // now; see TransferFlowContainer.swift.
    @State private var showTransferFlow = false
    // Real savings deposit/claim flow (2026-07-12) -- see SavingsFlowContainer.swift.
    @State private var savingsFlowStep: SavingsFlowStep?
    // Real savings-goal creation (2026-08-22) -- see CreateSavingsGoalScreen.swift.
    // Its own separate boolean, not folded into savingsFlowStep/SavingsFlowContainer:
    // creation doesn't move money, so it needs none of that container's
    // MoneyActionResult/device-step-up machinery -- matches
    // CreateWeeklySavingsPlanView's own established self-contained-sheet pattern.
    @State private var showCreateSavingsGoal = false
    // Real transaction history (2026-07-12) -- see TransactionHistoryScreen.swift.
    @State private var showTransactionHistory = false
    // Real account settings screen (2026-07-12) -- see SettingsScreen.swift.
    @State private var showSettings = false
    // Real bell/profile icons inside BankView's own header (product-feel audit,
    // §235) -- see BankView.swift's TopBarActionButton doc comment for why these
    // route to Settings (the one real destination that already has a notification
    // feed) rather than a dedicated feed screen iOS doesn't have. Kept as its own
    // state var (not reusing $showSettings) since it needs to present as a sheet
    // ON TOP OF the already-showing $showBank fullScreenCover below, not stack a
    // second fullScreenCover on that binding's own separate presentation.
    @State private var showBankSettings = false
    @State private var showMapFromDeepLink = false
    @State private var mapSearchFromDeepLink: String?
    // Real Kakao Map-style shared-folder landing (2026-08-18) -- resolves a real
    // `itunda://maps/shared/{userId}/{folderName}` link (Android already resolved
    // this since 2026-08-14; iOS never did until now). Mutually exclusive with a plain
    // search link -- see the onOpenURL handler below.
    @State private var mapSharedFolderFromDeepLink: (ownerId: String, folderName: String)?
    // My's own real content (orders/favorites/listings) is its own primary tab now
    // (ItundaTab.You, 2026-08-10) -- no overlay state needed to reach it anymore.
    // Real Toss Bank 송금 (Transfer) full page (2026-07-24) -- reachable from the
    // 전체/All tab's own "Financial services" section, matching real Toss where Home's
    // own Send button stays a quick recipient-picker (unchanged) while the full grouped
    // page (Send money/Auto-transfer/history) lives one level into the menu. See
    // TransferHubScreen.swift's own doc comment.
    @State private var showTransferHub = false
    // Real product-positioning fix (2026-08-10, see the "itunda: the wedge, not the
    // mirror" strategy memo, and the identical fix on bank-mfe's HomeView / Android's
    // HomeTab): these 4 screens already existed but were only reachable from
    // EntireMenuScreen's own local @State -- same "each presenting view owns its own
    // sheet state" convention already used there, duplicated here so Home can reach
    // them directly instead of only through the All tab.
    @State private var showSacco = false
    @State private var showIkimina = false
    @State private var showMotoOwnership = false
    @State private var showHarvestAdvance = false
    // Real itunda Bank product identity (2026-08-11) -- see Android's identical
    // BankHubScreen/BankSummaryCard and bank-mfe's identical SavingsView rebrand for
    // the full "itunda Bank vs itunda account/Pay" research this came out of.
    // LoansScreenView/InvestScreenView already existed (reachable only from
    // EntireMenuScreen's own local @State before this) -- same "each presenting view
    // owns its own sheet state" duplication already used for showSacco above.
    @State private var showLoans = false
    @State private var showInvest = false
    // Real architectural fix (2026-08-13, matching the identical Android fix same
    // session, direct user directive): "all itunda product features are independent
    // and isolated -- itunda bank is a complete product... tabs are not products,
    // are just access points." BankView used to render directly as this TabView's
    // own Home (tag 0) -- real Bank-product content (balance, savings, coop rail,
    // connected money, discover, deposit protection) baked into what's meant to be
    // a generic access point. Reachable from Explore's "Bank" icon now instead (see
    // EntireMenuScreen's own onOpenBank doc comment), matching Android's identical
    // BankHubScreen move. Home no longer carries any Bank-specific data at all.
    @State private var showBank = false

    // Real, minimal usage signal on each tap (2026-08-10) -- same event name/metadata
    // shape bank-mfe's/Android's identical coop rails already fire, stable keys
    // (sacco/ikimina/moto_ownership/harvest_advance) rather than the localized title.
    private var coopRows: [CooperativeRowData] {
        [
            CooperativeRowData(title: "SACCO shares", subtitle: "Buy cooperative shares, earn a real dividend", symbol: "building.columns.fill", tint: Color.accentPurple.opacity(0.15), onTap: {
                NetworkClient.shared.recordAnalyticsEventBestEffort("coop_rail_tap", metadata: "sacco")
                showSacco = true
            }),
            CooperativeRowData(title: "Ikimina", subtitle: "Join a rotating savings circle with people you trust", symbol: "person.2.fill", tint: Color.accentTeal.opacity(0.15), onTap: {
                NetworkClient.shared.recordAnalyticsEventBestEffort("coop_rail_tap", metadata: "ikimina")
                showIkimina = true
            }),
            CooperativeRowData(title: "Moto-Taxi Ownership", subtitle: "Save toward your own bike, then convert to a loan", symbol: "bicycle", tint: Color.accentIndigo.opacity(0.15), onTap: {
                NetworkClient.shared.recordAnalyticsEventBestEffort("coop_rail_tap", metadata: "moto_ownership")
                showMotoOwnership = true
            }),
            CooperativeRowData(title: "Harvest advance", subtitle: "Input financing from your coffee cooperative", symbol: "leaf.fill", tint: Color.accentOrange.opacity(0.15), onTap: {
                NetworkClient.shared.recordAnalyticsEventBestEffort("coop_rail_tap", metadata: "harvest_advance")
                showHarvestAdvance = true
            }),
            CooperativeRowData(title: "Get a loan", subtitle: "Personal, salary-backed, SME working capital", symbol: "account.pass.fill", tint: Color.accentIndigo.opacity(0.15), onTap: {
                NetworkClient.shared.recordAnalyticsEventBestEffort("coop_rail_tap", metadata: "loans")
                showLoans = true
            }),
            CooperativeRowData(title: "Grow your money", subtitle: "RSE stocks, bonds & fixed income, IPOs", symbol: "chart.line.uptrend.xyaxis", tint: Color.accentTeal.opacity(0.15), onTap: {
                NetworkClient.shared.recordAnalyticsEventBestEffort("coop_rail_tap", metadata: "invest")
                showInvest = true
            }),
        ]
    }

    // Real Savings section rows with real tap targets (2026-07-12) -- built here,
    // not inside BankViewModel, because triggering savingsFlowStep needs
    // ContentView's own @State (see BankView.swift's note on why Feature-module
    // views take plain data rather than owning navigation state themselves).
    private var savingsRows: [SavingsRowData] {
        var rows: [SavingsRowData] = []
        // Real "+ New savings goal" entry point (2026-08-22, product-feel/Toss-parity
        // work) -- matches web's CreateGoalForm/Android's identical row, both already
        // real. Leads the section, like web's own placement, with a distinct
        // plus-icon/neutral tint (not the shared leaf used by real goals/jar below).
        rows.append(SavingsRowData(
            title: "New savings goal",
            subtitle: "Set a target and save toward it",
            trailing: "",
            symbol: "plus.circle",
            iconBackground: Color(.systemGray5),
            onTap: { showCreateSavingsGoal = true }
        ))
        if let jar = bankViewModel.interestJar {
            // Real interest-methodology transparency (2026-08-11) -- this row only
            // ever showed the opaque earned-this-month figure, never the rate or
            // accrual frequency backing it (SavingsService.accrueInterest() divides
            // jar.rate, the real annual rate, by 365 for a real daily accrual -- that
            // math was never shown to the user on this platform). Matches the same
            // fix applied to bank-mfe's mislabeled "daily interest" copy and Android's
            // equivalent row the same day.
            rows.append(SavingsRowData(
                title: "Interest jar",
                subtitle: String(format: "%.1f%% annual, accrued daily", jar.rate),
                trailing: "\(Int(jar.earnedThisMonth)) RWF",
                onTap: { savingsFlowStep = .claimInterest }
            ))
        }
        for goal in bankViewModel.savingsGoals {
            let percent = goal.targetAmount > 0 ? Int(goal.currentAmount / goal.targetAmount * 100) : 0
            // Real fix (2026-08-23, same session as a backend guard against depositing
            // into an already-completed goal): this row never distinguished a completed
            // goal at all -- matches Android's identical fix the same day, and web's own
            // "· Completed 🎉" subtitle marker (BankDashboard.tsx) that already existed.
            let completedSuffix = goal.status == "completed" ? " · Completed 🎉" : ""
            rows.append(SavingsRowData(
                title: goal.name,
                subtitle: "\(Int(goal.currentAmount)) of \(Int(goal.targetAmount)) RWF\(completedSuffix)",
                trailing: "\(percent)%",
                onTap: { savingsFlowStep = .deposit(goalId: goal.id, goalName: goal.name) }
            ))
        }
        return rows
    }

    var body: some View {
        TabView(selection: $selectedTab) {
            // Real minimal access-point Home (2026-08-13) -- see showBank's own doc
            // comment above for why this used to be BankView directly. Discover is
            // real, already-fetched data (bankViewModel.discoverRows); isOffline was
            // already tracked by BankViewModel but never rendered anywhere -- same
            // "tracked but never shown" bug just found and fixed on Android's
            // identical HomeTab.
            HomeTabContent(bankViewModel: bankViewModel)
                .fullScreenCover(isPresented: $showTransferFlow) {
                    TransferFlowContainer(
                        availableBalance: bankViewModel.availableBalance,
                        onDone: {
                            showTransferFlow = false
                            Task { await bankViewModel.load() }
                        }
                    )
                }
                .fullScreenCover(item: $savingsFlowStep) { step in
                    SavingsFlowContainer(
                        step: step,
                        availableBalance: bankViewModel.availableBalance,
                        onDone: {
                            savingsFlowStep = nil
                            Task { await bankViewModel.load() }
                        }
                    )
                }
                .sheet(isPresented: $showCreateSavingsGoal) {
                    CreateSavingsGoalScreen(
                        onCreated: {
                            showCreateSavingsGoal = false
                            Task { await bankViewModel.load() }
                        },
                        onCancel: { showCreateSavingsGoal = false }
                    )
                }
                .fullScreenCover(isPresented: $showTransactionHistory) {
                    TransactionHistoryScreen(
                        transactions: bankViewModel.transactions.map { tx in
                            TransactionDisplayItem(
                                id: tx.id,
                                description: tx.description,
                                amount: tx.amount,
                                currency: tx.currency,
                                status: tx.status,
                                isOutgoing: tx.senderId == bankViewModel.currentUserId
                            )
                        },
                        onBack: { showTransactionHistory = false }
                    )
                }
                .tabItem {
                    Image(systemName: "house.fill")
                    Text("Home")
                }
                .tag(0)

            // Real super-app bottom nav: Home/Pay/Explore/Messages/You (2026-08-10),
            // replacing the previous Home/Shop/Hood/Talk/All layout -- an explicit
            // product decision after directly comparing both, matching bank-mfe's
            // BankDashboard.tsx and Android's ItundaAppScreen.kt the same session (see
            // docs/DESIGN_REFERENCES.md Section 41 in the web repo for the full
            // comparison). itunda is bank-first, so Pay and You (profile/account) get
            // dedicated primary slots instead of being nested a tap into Explore/My the
            // way the previous layout had them. Shop, Eats, Marketplace, Community,
            // Jobs, and Property all lose their own tabs -- none demoted for being
            // weak, all real, fully-built features -- and are each their own flat,
            // individually reachable Explore entry point (same established
            // full-screen-cover pattern showSacco/showIkimina/etc. already use). Real
            // same-day correction: an earlier pass nested Shop+Eats behind
            // ShopScreen's own Picker and Marketplace+Community+Jobs+Property behind
            // HoodScreen's own Picker -- a tab bar inside a tab, noise a flat catalog
            // shouldn't have -- so each is flat instead; see HoodSectionScreen's own
            // doc comment (HoodScreen.swift) for the fuller account.
            PayScreen(onSwitchToYou: { selectedTab = 4 })
                .tabItem {
                    Image(systemName: "creditcard.fill")
                    Text("Pay")
                }
                .tag(1)

            EntireMenuScreen(
                onOpenSettings: { showSettings = true },
                onClaimInterest: { savingsFlowStep = .claimInterest },
                onSwitchToTalk: { selectedTab = 3 },
                onOpenTransferHub: { showTransferHub = true },
                onOpenShop: { showShop = true },
                onOpenEats: { showEats = true },
                onOpenMarketplace: { showMarketplace = true },
                onOpenCommunity: { showCommunity = true },
                onOpenJobs: { showJobs = true },
                onOpenProperty: { showProperty = true },
                onOpenBank: { showBank = true }
            )
                .fullScreenCover(isPresented: $showBank) {
                    // BankView was built to be a TabView root (no back button of its
                    // own -- the tab bar was navigation enough); presented here as a
                    // real destination instead, so it needs one, matching every other
                    // fullScreenCover destination in this file (SaccoScreenView etc.
                    // each take their own onBack).
                    // Real fix (2026-08-24, TransactionDetailScreen's "Balance
                    // after" -- matches Android's/bank-mfe's identical running-
                    // balance computation): explicitly re-sorted newest-first
                    // (matching those two platforms' own defensive re-sort) since a
                    // running balance is only correct walked in that order.
                    let sortedTransactions = bankViewModel.transactions.sorted { $0.createdAt > $1.createdAt }
                    var runningBalance = bankViewModel.balance
                    let recentTransactionRows: [RecentTransactionRowData] = sortedTransactions.map { tx in
                        let isOutgoing = tx.senderId == bankViewModel.currentUserId
                        let afterBalance = runningBalance
                        runningBalance -= isOutgoing ? tx.amount : -tx.amount
                        return RecentTransactionRowData(
                            id: tx.id,
                            title: tx.description,
                            subtitle: tx.status.capitalized,
                            amountText: "\(isOutgoing ? "-" : "+")\(Int(tx.amount)) \(tx.currency)",
                            isOutgoing: isOutgoing,
                            type: tx.type,
                            createdAt: tx.createdAt,
                            fee: tx.fee,
                            currency: tx.currency,
                            afterBalance: afterBalance
                        )
                    }
                    NavigationStack {
                        BankView(
                            balanceText: bankViewModel.balanceText,
                            accountNumber: bankViewModel.accountNumber,
                            savingsRows: savingsRows,
                            discoverRows: bankViewModel.discoverRows,
                            coopRows: coopRows,
                            recentTransactions: recentTransactionRows,
                            onSend: { showTransferFlow = true },
                            onOpenTransactionHistory: { showTransactionHistory = true },
                            onOpenNotifications: { showBankSettings = true },
                            onOpenProfile: { showBankSettings = true },
                            payBalanceText: bankViewModel.payBalanceText,
                            onOpenPay: { showBank = false; selectedTab = 1 }
                        )
                            .toolbar {
                                ToolbarItem(placement: .navigationBarLeading) {
                                    Button("Close") { showBank = false }
                                }
                            }
                    }
                        .task { await bankViewModel.load() }
                        .sheet(isPresented: $showBankSettings) {
                            SettingsScreen(onDone: { showBankSettings = false })
                        }
                        .sheet(isPresented: $showSacco) {
                            SaccoScreenView(onBack: { showSacco = false })
                        }
                        .sheet(isPresented: $showIkimina) {
                            IkiminaScreenView(onBack: { showIkimina = false })
                        }
                        .sheet(isPresented: $showMotoOwnership) {
                            MotoOwnershipScreenView(onBack: { showMotoOwnership = false })
                        }
                        .sheet(isPresented: $showHarvestAdvance) {
                            HarvestAdvanceScreenView(onBack: { showHarvestAdvance = false })
                        }
                        .sheet(isPresented: $showLoans) {
                            LoansScreenView(onBack: { showLoans = false })
                        }
                        .sheet(isPresented: $showInvest) {
                            InvestScreenView(onBack: { showInvest = false })
                        }
                }
                .fullScreenCover(isPresented: $showSettings) {
                    SettingsScreen(onDone: { showSettings = false })
                }
                .fullScreenCover(isPresented: $showTransferHub) {
                    TransferHubContainer(
                        onBack: { showTransferHub = false },
                        onSendMoney: { showTransferHub = false; showTransferFlow = true },
                        onSplitBill: { showTransferHub = false; selectedTab = 3 },
                        onOpenHistory: { showTransferHub = false; showTransactionHistory = true }
                    )
                }
                .fullScreenCover(isPresented: $showShop) {
                    CommerceShopContent(onMessageSeller: { conversationId in
                        pendingConversationId = conversationId
                        showShop = false
                        selectedTab = 3
                    })
                }
                .fullScreenCover(isPresented: $showEats) {
                    EatsContent()
                }
                .fullScreenCover(isPresented: $showMarketplace) {
                    HoodSectionScreen(mode: .marketplace, pendingConversationId: $pendingConversationId, onSwitchToTalk: { showMarketplace = false; selectedTab = 3 })
                }
                .fullScreenCover(isPresented: $showCommunity) {
                    HoodSectionScreen(mode: .community, pendingConversationId: $pendingConversationId, onSwitchToTalk: { showCommunity = false; selectedTab = 3 })
                }
                .fullScreenCover(isPresented: $showJobs) {
                    HoodSectionScreen(mode: .jobs, pendingConversationId: $pendingConversationId, onSwitchToTalk: { showJobs = false; selectedTab = 3 })
                }
                .fullScreenCover(isPresented: $showProperty) {
                    HoodSectionScreen(mode: .property, pendingConversationId: $pendingConversationId, onSwitchToTalk: { showProperty = false; selectedTab = 3 })
                }
                .tabItem {
                    Image(systemName: "square.grid.2x2.fill")
                    Text("Explore")
                }
                .tag(2)

            // Real service-channel ctaRoute navigation (itunda Talk redesign,
            // 2026-08-28) -- a real, honest partial router: routes to the right real
            // top-level destination by prefix (this app has no generic route-string
            // sub-screen dispatcher), not the exact sub-screen every ctaRoute names.
            // A real, named, deliberate scope limit, not fabricated navigation.
            TalkScreen(pendingConversationId: $pendingConversationId, onNavigateRoute: { route in
                if route.hasPrefix("/bank") { showBank = true }
                else if route.hasPrefix("/pay") { selectedTab = 1 }
                else if route.hasPrefix("/hood") || route.hasPrefix("/marketplace") { showMarketplace = true }
            })
                .tabItem {
                    Image(systemName: "bubble.left.and.bubble.right.fill")
                    Text("Messages")
                }
                .tag(3)

            // Real, dedicated primary tab (2026-08-10, see this TabView's own doc
            // comment) -- MyTabView's own real content (orders/favorites/listings) is
            // completely unchanged, just reached directly instead of via Explore's
            // profile icon.
            MyTabView(
                onSwitchToShop: { showShop = true },
                onSwitchToEats: { showEats = true },
                onSwitchToMarketplace: { showMarketplace = true },
                onSwitchToJobs: { showJobs = true },
                onSwitchToProperty: { showProperty = true }
            )
                .tabItem {
                    Image(systemName: "person.fill")
                    Text("You")
                }
                .tag(4)
        }
        .accentColor(.primary)
        // Real Toast mount point (2026-08-22) -- see CoreDesignSystem's Toast.swift
        // for the full account. Attached here, not deeper, so it's visible on the
        // Home tab's own layer the moment a .sheet/.fullScreenCover presented from
        // it (CreateSavingsGoalScreen, SavingsFlowContainer, TransferFlowContainer,
        // ...) dismisses itself and calls ToastCenter.shared.show(...) right before
        // its own onCreated/onDone callback. A SwiftUI .overlay can't render ABOVE
        // an active .sheet/.fullScreenCover (those present in their own separate
        // UIKit modal layer, genuinely on top of any overlay on the presenter) --
        // that's a real SwiftUI limitation, not an oversight here; every real call
        // site fires its toast at (or after) the moment its own cover/sheet is
        // already dismissing, which is the actual use case this needs to cover.
        .overlay(ToastOverlay())
        .fullScreenCover(isPresented: $showMapFromDeepLink) {
            MapScreenView(initialSearchQuery: mapSearchFromDeepLink, initialSharedFolder: mapSharedFolderFromDeepLink)
        }
        .onOpenURL { url in
            guard url.scheme?.caseInsensitiveCompare("itunda") == .orderedSame,
                  url.host?.caseInsensitiveCompare("maps") == .orderedSame else { return }
            // Real shared-folder link (2026-08-18) -- itunda://maps/shared/{userId}/{folderName},
            // matching Android's own identical path-shape check and bank-mfe's own
            // ?sharedOwner=&sharedFolder= query-param equivalent.
            let pathParts = url.path.split(separator: "/").map(String.init)
            if pathParts.count == 3, pathParts[0].caseInsensitiveCompare("shared") == .orderedSame {
                mapSharedFolderFromDeepLink = (ownerId: pathParts[1], folderName: pathParts[2].removingPercentEncoding ?? pathParts[2])
                mapSearchFromDeepLink = nil
                showMapFromDeepLink = true
                return
            }
            mapSharedFolderFromDeepLink = nil
            mapSearchFromDeepLink = url.path.caseInsensitiveCompare("/search") == .orderedSame
                ? url.queryValue(named: "query")?.trimmingCharacters(in: .whitespacesAndNewlines).prefix(160).description
                : nil
            showMapFromDeepLink = true
        }
    }
}

private extension URL {
    func queryValue(named name: String) -> String? {
        URLComponents(url: self, resolvingAgainstBaseURL: false)?
            .queryItems?
            .first(where: { $0.name == name })?
            .value
    }
}

// BenefitsScreen moved to BenefitsShopAllScreens.swift (2026-07-11) -- rebuilt
// against Android's real, Toss-screenshot-verified ItundaAppScreen.kt content
// instead of this file's old crude hardcoded-mock-data placeholders. See that
// file's own header for why. (DiscoverScreen, the old "Shop" tab, was removed
// 2026-07-18 -- see BenefitsShopAllScreens.swift's own note on why.)
//
// Real fix (2026-08-11, itunda Pay research pass -- user-provided KakaoPay/Toss
// Pay screenshots): this whole tab was a decorative mockup -- hardcoded "32,050
// RWF" balance, hardcoded "Kigali Heights"/"Brioche Cafe" merchant rows with fake
// distances, a "Scan QR / Barcode" button that was a literal no-op
// (Button(action: {})), and tapping a merchant opened a hardcoded fake quote
// ("2,000" RWF) that never called any API. The REAL payment-collection UI
// (pay-by-code, pay-by-static-QR, Face Pay -- all genuinely wired to
// rw.itunda.merchant.MerchantService.collect) already existed, just misfiled
// inside the unrelated Shop screen (ShopScreen.swift's own PayAMerchantSection)
// where the Pay tab could never reach it. This is that real UI, moved to where
// "Pay" actually means pay -- same fix as Android's identical PayTab mock the
// same day.
