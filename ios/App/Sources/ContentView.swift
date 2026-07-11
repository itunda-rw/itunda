import SwiftUI
import UIKit
import FeatureBanking
import FeaturePayments

// Fixed (2026-07-11): every Text() in this file used .font(.system(size:weight:)) --
// a fixed point size that doesn't grow or shrink with iOS's Dynamic Type
// accessibility setting. Same bug, same fix as CoreDesignSystem's IDS.swift/
// TdsTheme.swift (see IDS.swift's Typography struct for the full reasoning) --
// this file doesn't import CoreDesignSystem for anything else today, so a small
// local helper avoids adding a new cross-module dependency just for this.
private func scaledFont(size: CGFloat, weight: UIFont.Weight, relativeTo style: UIFont.TextStyle) -> Font {
    Font(UIFontMetrics(forTextStyle: style).scaledFont(for: UIFont.systemFont(ofSize: size, weight: weight)))
}

// Tab labels renamed (2026-07-11) to match android/app/.../ItundaAppScreen.kt's
// established taxonomy exactly: Home/Benefits/Shop/Pay/All -- this file previously
// said "Discover"/"Entire" instead of "Shop"/"All", the "screen/navigation taxonomy
// parity is still open" gap docs/TOSS_RWANDA_ALIGNMENT.md's Current Repository Gap
// List names. Not a cosmetic rename: Android's Shop tab contains its own internal
// "Discover" section (ShopTab -> ShellSection(title = "Discover", ...) backed by the
// same discover-items data this screen's card content already resembles), and "All"
// is the deliberate translation of Toss's 전체 tab Android's own ShopTab/AllTopBar
// comments already establish -- "Entire" was simply the wrong word.
struct ContentView: View {
    @State private var selectedTab = 0

    var body: some View {
        TabView(selection: $selectedTab) {
            // Real, ported screen (was previously unreachable from any navigation --
            // see docs/ARCHITECTURE.md §3's "New finding" note) replaces the crude,
            // hardcoded-mock-data BankScreen struct that used to live in this file,
            // same "delete the unreachable duplicate, wire in the real one" fix
            // Android already went through for its own legacy BankScreen.kt.
            BankView()
                .tabItem {
                    Image(systemName: "house.fill")
                    Text("Home")
                }
                .tag(0)

            BenefitsScreen()
                .tabItem {
                    Image(systemName: "diamond.fill")
                    Text("Benefits")
                }
                .tag(1)

            DiscoverScreen()
                .tabItem {
                    Image(systemName: "bag.fill")
                    Text("Shop")
                }
                .tag(2)

            PayScreen()
                .tabItem {
                    Image(systemName: "creditcard.fill")
                    Text("Pay")
                }
                .tag(3)

            EntireMenuScreen()
                .tabItem {
                    Image(systemName: "line.3.horizontal")
                    Text("All")
                }
                .tag(4)
        }
        .accentColor(.primary)
    }
}

struct BenefitsScreen: View {
    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                HeaderTitle(title: "Benefits")
                CardItem(title: "Daily Lucky Draw", value: "Tap to win up to 500 RWF!", buttonText: "Play Now", buttonColor: .blue)
                CardItem(title: "Interest Jar", value: "Earned: 450 RWF", buttonText: "Claim", buttonColor: .blue)
            }
            .padding(.top, 24)
        }
        .background(Color(.systemGroupedBackground).edgesIgnoringSafeArea(.all))
    }
}

struct DiscoverScreen: View {
    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                HeaderTitle(title: "Discover")
                CardItem(title: "Flash Deal", value: "50% off Canal+ Subscription", buttonText: "Buy Now", buttonColor: .pink)
                CardItem(title: "Featured", value: "Order from Jumia & save 10%", buttonText: "Order", buttonColor: .orange)
            }
            .padding(.top, 24)
        }
        .background(Color(.systemGroupedBackground).edgesIgnoringSafeArea(.all))
    }
}

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

struct EntireMenuScreen: View {
    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                HeaderTitle(title: "Entire")
                CardItem(title: "Loans", value: "Get up to 5,000,000 RWF", buttonText: "Apply", buttonColor: .blue)
                CardItem(title: "Bills & Airtime", value: "Cashpower, Airtel, MTN", buttonText: "Pay Bills", buttonColor: .blue)
                CardItem(title: "Irembo Services", value: "Pay government services directly", buttonText: "Access", buttonColor: .blue)
            }
            .padding(.top, 24)
        }
        .background(Color(.systemGroupedBackground).edgesIgnoringSafeArea(.all))
    }
}

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

#Preview {
    ContentView()
}
