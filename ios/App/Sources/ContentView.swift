import SwiftUI
import UIKit
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

    // Real Savings section rows with real tap targets (2026-07-12) -- built here,
    // not inside BankViewModel, because triggering savingsFlowStep needs
    // ContentView's own @State (see BankView.swift's note on why Feature-module
    // views take plain data rather than owning navigation state themselves).
    private var savingsRows: [SavingsRowData] {
        var rows: [SavingsRowData] = []
        if let jar = bankViewModel.interestJar {
            rows.append(SavingsRowData(
                title: "Interest jar",
                subtitle: "Earned this month",
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
                savingsRows: savingsRows,
                onSend: { showTransferFlow = true },
                onOpenTransactionHistory: { showTransactionHistory = true }
            )
                .task { await bankViewModel.load() }
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
                .tabItem {
                    Image(systemName: "house.fill")
                    Text("Home")
                }
                .tag(0)

            // Real super-app bottom nav (2026-07-18): Home/Shop/Hood/Talk/My,
            // replacing the previous Home/Benefits/Shop/Pay/All layout now that
            // itunda has real Coupang-style commerce (Shop), 당근마켓-style
            // marketplace (Hood), and Kakao-style messaging (Talk) backends to put
            // behind top-level tabs -- exact same restructure Android's
            // ItundaAppScreen.kt just went through, see that file's own header
            // comment for the full reasoning (Benefits/Pay folded into My below,
            // not dropped).
            ShopScreen()
                .tabItem {
                    Image(systemName: "bag.fill")
                    Text("Shop")
                }
                .tag(1)

            HoodScreen(pendingConversationId: $pendingConversationId, onSwitchToTalk: { selectedTab = 3 })
                .tabItem {
                    Image(systemName: "location.fill")
                    Text("Hood")
                }
                .tag(2)

            TalkScreen(pendingConversationId: $pendingConversationId)
                .tabItem {
                    Image(systemName: "bubble.left.and.bubble.right.fill")
                    Text("Talk")
                }
                .tag(3)

            EntireMenuScreen(onOpenSettings: { showSettings = true })
                .fullScreenCover(isPresented: $showSettings) {
                    SettingsScreen(onDone: { showSettings = false })
                }
                .tabItem {
                    Image(systemName: "person.fill")
                    Text("My")
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
// PayScreen stays defined here and is now reached from My (EntireMenuScreen's real
// "Quick links" row), not its own top-level tab -- same fold-in Android's AllTab
// went through. Still real-UI-only (no real backend quote/confirm wired -- see this
// struct's own header comment below), that scope gap is unrelated to the nav move.
struct PayScreen: View {
    // Wires the real, ported TransferQuoteScreen (ios/Features/Payments/
    // Sources/TransferScreen.swift) in for the first time -- it had zero call
    // sites anywhere in ios/ despite being real code with a working biometric
    // confirm gate (docs/ARCHITECTURE.md §3's "BankView wired in" note names
    // this same pattern for BankView; this is the same fix for
    // TransferQuoteScreen). No real backend session exists on iOS yet (no
    // login flow -- same honest caveat android/features/payments/impl/
    // TransferFlow.kt's own header states for Android), so recipient/amount/
    // fee are UI-only placeholder state, not a real quote.
    @State private var selectedMerchant: String?
    @State private var showTransferSheet = false

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                HeaderTitle(title: "Itunda Pay")
                CardItem(title: "Pay Balance", value: "32,050 RWF", buttonText: "Scan QR / Barcode", buttonColor: .blue)

                VStack(alignment: .leading, spacing: 0) {
                    Text("Nearby Merchants")
                        .font(scaledFont(size: 18, weight: .bold, relativeTo: .headline))
                        .padding(.horizontal, 24)
                        .padding(.bottom, 16)
                    TransactionRow(title: "Kigali Heights", date: "1.2 km away", amount: "Pay with QR", isNegative: false) {
                        selectedMerchant = "Kigali Heights"
                        showTransferSheet = true
                    }
                    TransactionRow(title: "Brioche Cafe", date: "2.0 km away", amount: "Pay with QR", isNegative: false) {
                        selectedMerchant = "Brioche Cafe"
                        showTransferSheet = true
                    }
                }
                .padding(.vertical, 24)
                .background(Color(.secondarySystemGroupedBackground))
                .cornerRadius(24)
                .padding(.horizontal, 20)
            }
            .padding(.top, 24)
        }
        .background(Color(.systemGroupedBackground).edgesIgnoringSafeArea(.all))
        .sheet(isPresented: $showTransferSheet) {
            TransferQuoteScreen(
                recipientName: selectedMerchant ?? "Merchant",
                amount: "2,000",
                fee: "0",
                onConfirm: { showTransferSheet = false },
                onCancel: { showTransferSheet = false }
            )
        }
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
