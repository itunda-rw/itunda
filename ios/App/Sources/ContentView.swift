import SwiftUI
import UIKit
import CoreNetwork
import FeatureBanking
import FeaturePayments

// Fixed (2026-07-11): every Text() in this file used .font(.system(size:weight:)) --
// a fixed point size that doesn't grow or shrink with iOS's Dynamic Type
// accessibility setting. Same bug, same fix as CoreDesignSystem's IDS.swift/
// IdsTheme.swift (see IDS.swift's Typography struct for the full reasoning) --
// this file doesn't import CoreDesignSystem for anything else today, so a small
// local helper avoids adding a new cross-module dependency just for this.
private func scaledFont(size: CGFloat, weight: UIFont.Weight, relativeTo style: UIFont.TextStyle) -> Font {
    Font(UIFontMetrics(forTextStyle: style).scaledFont(for: UIFont.systemFont(ofSize: size, weight: weight)))
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
    // Real wallet/savings data (2026-07-11) -- see BankViewModel.swift for why this
    // lives here rather than inside BankView's own module.
    @StateObject private var bankViewModel = BankViewModel()
    // Real send-money flow (2026-07-12) -- "Send money now" was decorative until
    // now; see TransferFlowContainer.swift.
    @State private var showTransferFlow = false
    // Real savings deposit/claim flow (2026-07-12) -- see SavingsFlowContainer.swift.
    @State private var savingsFlowStep: SavingsFlowStep?
    // Real transaction history (2026-07-12) -- see TransactionHistoryScreen.swift.
    @State private var showTransactionHistory = false
    // Real account settings screen (2026-07-12) -- see SettingsScreen.swift.
    @State private var showSettings = false
    @State private var showMapFromDeepLink = false
    @State private var mapSearchFromDeepLink: String?
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
    // the full "itunda Bank vs itunda wallet/Pay" research this came out of.
    // LoansScreenView/InvestScreenView already existed (reachable only from
    // EntireMenuScreen's own local @State before this) -- same "each presenting view
    // owns its own sheet state" duplication already used for showSacco above.
    @State private var showLoans = false
    @State private var showInvest = false

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
            CooperativeRowData(title: "Moto-Taxi Ownership", subtitle: "Save toward your own bike, then convert to a loan", symbol: "bicycle", tint: Color.accentBlue.opacity(0.15), onTap: {
                NetworkClient.shared.recordAnalyticsEventBestEffort("coop_rail_tap", metadata: "moto_ownership")
                showMotoOwnership = true
            }),
            CooperativeRowData(title: "Harvest advance", subtitle: "Input financing from your coffee cooperative", symbol: "leaf.fill", tint: Color.accentOrange.opacity(0.15), onTap: {
                NetworkClient.shared.recordAnalyticsEventBestEffort("coop_rail_tap", metadata: "harvest_advance")
                showHarvestAdvance = true
            }),
            CooperativeRowData(title: "Get a loan", subtitle: "Personal, salary-backed, SME working capital", symbol: "wallet.pass.fill", tint: Color.accentBlue.opacity(0.15), onTap: {
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
                trailing: "RWF \(Int(jar.earnedThisMonth))",
                onTap: { savingsFlowStep = .claimInterest }
            ))
        }
        for goal in bankViewModel.savingsGoals {
            let percent = goal.targetAmount > 0 ? Int(goal.currentAmount / goal.targetAmount * 100) : 0
            rows.append(SavingsRowData(
                title: goal.name,
                subtitle: "RWF \(Int(goal.currentAmount)) of \(Int(goal.targetAmount))",
                trailing: "\(percent)%",
                onTap: { savingsFlowStep = .deposit(goalId: goal.id, goalName: goal.name) }
            ))
        }
        return rows
    }

    var body: some View {
        TabView(selection: $selectedTab) {
            // Real, ported screen (was previously unreachable from any navigation --
            // see docs/ARCHITECTURE.md §3's "New finding" note) replaces the crude,
            // hardcoded-mock-data BankScreen struct that used to live in this file,
            // same "delete the unreachable duplicate, wire in the real one" fix
            // Android already went through for its own legacy BankScreen.kt.
            BankView(
                balanceText: bankViewModel.balanceText,
                accountNumber: bankViewModel.accountNumber,
                savingsRows: savingsRows,
                discoverRows: bankViewModel.discoverRows,
                coopRows: coopRows,
                onSend: { showTransferFlow = true },
                onOpenTransactionHistory: { showTransactionHistory = true }
            )
                .task { await bankViewModel.load() }
                // Real, minimal usage signal (2026-08-10) -- see the "itunda: the
                // wedge, not the mirror" strategy memo, recommendation (ii), and
                // NetworkClient.recordAnalyticsEventBestEffort's own doc comment.
                // Fired once per real appearance of Home, the baseline every
                // retention question is measured against -- same event name/shape
                // bank-mfe's/Android's identical Home effects already fire.
                .onAppear { NetworkClient.shared.recordAnalyticsEventBestEffort("home_view") }
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
            PayScreen()
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
                onOpenProperty: { showProperty = true }
            )
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
                    CommerceShopContent()
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

            TalkScreen(pendingConversationId: $pendingConversationId)
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
        .fullScreenCover(isPresented: $showMapFromDeepLink) {
            MapScreenView(initialSearchQuery: mapSearchFromDeepLink)
        }
        .onOpenURL { url in
            guard url.scheme?.caseInsensitiveCompare("itunda") == .orderedSame,
                  url.host?.caseInsensitiveCompare("maps") == .orderedSame else { return }
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
struct PayScreen: View {
    @State private var paymentResult: CollectPaymentResultDto?

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                HeaderTitle(title: "Itunda Pay")
                PayAMerchantSection(paymentResult: $paymentResult)
                    .padding(.horizontal, 20)
            }
            .padding(.top, 24)
        }
        .background(Color(.systemGroupedBackground).edgesIgnoringSafeArea(.all))
    }
}

// EntireMenuScreen (the "All" tab) also moved to BenefitsShopAllScreens.swift
// (2026-07-11) -- same reason as the comment above.

struct HeaderTitle: View {
    let title: String
    var body: some View {
        HStack {
            Text(title)
                .font(scaledFont(size: 28, weight: .bold, relativeTo: .largeTitle))
                .foregroundColor(.primary)
            Spacer()
            Image(systemName: "bell.fill")
                .foregroundColor(.secondary)
                .font(.title2)
        }
        .padding(.horizontal, 24)
        .padding(.top, 16)
    }
}

struct CardItem: View {
    let title: String
    let value: String
    let buttonText: String
    let buttonColor: Color
    
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(title)
                .font(scaledFont(size: 14, weight: .semibold, relativeTo: .footnote))
                .foregroundColor(.secondary)

            Text(value)
                .font(scaledFont(size: 22, weight: .bold, relativeTo: .title1))
                .foregroundColor(.primary)

            Button(action: {}) {
                Text(buttonText)
                    .font(scaledFont(size: 16, weight: .semibold, relativeTo: .body))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(buttonColor)
                    .foregroundColor(.white)
                    .cornerRadius(12)
            }
        }
        .padding(24)
        .background(Color(.secondarySystemGroupedBackground))
        .cornerRadius(24)
        .padding(.horizontal, 20)
    }
}

struct TransactionRow: View {
    let title: String
    let date: String
    let amount: String
    let isNegative: Bool
    // Optional so a read-only usage (a past transaction, say) doesn't need to pass a
    // no-op closure -- PayScreen's merchant rows are the first real, tappable usage.
    var action: (() -> Void)? = nil

    var body: some View {
        let content = HStack {
            Circle()
                .fill(Color.secondary.opacity(0.2))
                .frame(width: 40, height: 40)

            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(scaledFont(size: 16, weight: .semibold, relativeTo: .body))
                    .foregroundColor(.primary)
                Text(date)
                    .font(scaledFont(size: 14, weight: .regular, relativeTo: .footnote))
                    .foregroundColor(.secondary)
            }
            Spacer()
            Text(amount)
                .font(scaledFont(size: 16, weight: .bold, relativeTo: .body))
                .foregroundColor(isNegative ? .primary : .blue)
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 12)

        if let action {
            Button(action: action) { content }
                .buttonStyle(.plain)
        } else {
            content
        }
    }
}

// #Preview { ContentView() } removed (2026-07-11) -- the real, working Xcode
// toolchain discovered this session (DEVELOPER_DIR=/Applications/Xcode.app/...,
// Xcode 14.3.1 + Tuist 3.42.3 via mise, see ARCHITECTURE.md §3) rejected this with
// "use of unknown directive '#Preview'" -- the preview-macro plugin isn't available
// in this generated project's build configuration. #Preview has zero runtime/
// production effect (canvas-only, Xcode-GUI-only), and no interactive canvas can
// ever render in this non-GUI environment anyway, so removing it was the right call
// rather than fighting toolchain plumbing for a feature that could never be used
// here. This was the *only* error in an otherwise clean full xcodebuild of every
// target in the workspace.
