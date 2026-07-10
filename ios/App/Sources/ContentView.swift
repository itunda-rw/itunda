import SwiftUI
import FeatureBanking

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
    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                HeaderTitle(title: "Itunda Pay")
                CardItem(title: "Pay Balance", value: "32,050 RWF", buttonText: "Scan QR / Barcode", buttonColor: .blue)
                
                VStack(alignment: .leading, spacing: 0) {
                    Text("Nearby Merchants")
                        .font(.system(size: 18, weight: .bold))
                        .padding(.horizontal, 24)
                        .padding(.bottom, 16)
                    TransactionRow(title: "Kigali Heights", date: "1.2 km away", amount: "Pay with QR", isNegative: false)
                    TransactionRow(title: "Brioche Cafe", date: "2.0 km away", amount: "Pay with QR", isNegative: false)
                }
                .padding(.vertical, 24)
                .background(Color(.secondarySystemGroupedBackground))
                .cornerRadius(24)
                .padding(.horizontal, 20)
            }
            .padding(.top, 24)
        }
        .background(Color(.systemGroupedBackground).edgesIgnoringSafeArea(.all))
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
                .font(.system(size: 28, weight: .bold))
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
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(.secondary)
            
            Text(value)
                .font(.system(size: 22, weight: .bold))
                .foregroundColor(.primary)
            
            Button(action: {}) {
                Text(buttonText)
                    .font(.system(size: 16, weight: .semibold))
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
    
    var body: some View {
        HStack {
            Circle()
                .fill(Color.secondary.opacity(0.2))
                .frame(width: 40, height: 40)
            
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.primary)
                Text(date)
                    .font(.system(size: 14))
                    .foregroundColor(.secondary)
            }
            Spacer()
            Text(amount)
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(isNegative ? .primary : .blue)
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 12)
    }
}

#Preview {
    ContentView()
}
