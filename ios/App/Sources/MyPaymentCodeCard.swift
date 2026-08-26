import SwiftUI
import UIKit
import CoreDesignSystem
import CoreNetwork
import CoreLocation
import FeaturePayments

// Extracted from ShopPay.swift (2026-08-21) -- that file crossed the 500-line
// file-size-lint guideline; docs/ARCHITECTURE_GUIDELINES.md §2 says extract rather
// than keep adding, so MyPaymentCodeCard/AccountCardCarousel/
// MyPaymentCodeLocationFetcher (the largest, most self-contained piece) moved here
// unchanged. Same App target, so no import/visibility changes were needed beyond
// this file's own import list.

/// Real "my location" for the nearby-benefits row -- same per-file
/// CLLocationManager fetcher convention BikeRentalScreenView.swift/
/// DesignatedDriverScreenView.swift already establish.
private final class MyPaymentCodeLocationFetcher: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var coordinate: CLLocationCoordinate2D?
    private let manager = CLLocationManager()

    override init() {
        super.init()
        manager.delegate = self
    }

    func requestLocation() {
        let status = manager.authorizationStatus
        if status == .notDetermined {
            manager.requestWhenInUseAuthorization()
        } else if status == .authorizedWhenInUse || status == .authorizedAlways {
            manager.requestLocation()
        }
        // Denied/restricted: silent, same as every other nearby() caller in this
        // codebase -- the row just doesn't render.
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        if manager.authorizationStatus == .authorizedWhenInUse || manager.authorizationStatus == .authorizedAlways {
            manager.requestLocation()
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        coordinate = locations.last?.coordinate
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {}
}

/// Real swipeable funding-source cards -- see `MyPaymentCodeCard`'s own doc
/// comment for why this stays a deliberate, honest simplification of the real
/// reference's mixed account/card/membership row (itunda has no Samsung-Pay NFC
/// or membership equivalent to include honestly). Settling on a card is a real
/// selection: it's the accountId `MyPaymentCodeCard`'s own code is generated
/// against. `TabView` with `.page` style is SwiftUI's real equivalent of
/// Android's `HorizontalPager` -- no extra dependency needed.
private struct AccountCardCarousel: View {
    let accounts: [Account]
    let selectedAccountId: String?
    let onSelect: (String) -> Void

    private func cardColor(_ currency: String) -> Color {
        switch currency {
        case "RWF": return Color(red: 0x22 / 255, green: 0x72 / 255, blue: 0xEB / 255)
        case "USD": return Color(red: 0x04 / 255, green: 0xC0 / 255, blue: 0x65 / 255)
        case "EUR": return Color(red: 0x7C / 255, green: 0x5C / 255, blue: 0xFC / 255)
        case "GBP": return Color(red: 0x00 / 255, green: 0x89 / 255, blue: 0x8A / 255)
        default: return IDS.Colors.textSecondary
        }
    }

    var body: some View {
        TabView(selection: Binding(
            get: { selectedAccountId ?? accounts.first?.id ?? "" },
            set: { onSelect($0) }
        )) {
            ForEach(accounts, id: \.id) { w in
                VStack(alignment: .leading) {
                    // Real EMV chip + tap-to-pay silhouette (2026-08-26, direct
                    // user instruction: "all cards designs should resemble real
                    // card") -- see BankCardChip's own doc comment.
                    HStack {
                        BankCardChip(size: 30)
                        Spacer()
                        CardContactlessGlyph(size: 18)
                    }
                    Spacer()
                    Text(w.type == "PAY" ? "itunda Pay" : w.type == "MAIN" ? "itunda Bank" : "itunda Pay \(w.currency)")
                        .font(IDS.scaledFont(size: 13, weight: .bold, relativeTo: .footnote)).foregroundColor(.white)
                    Text("\(w.currency) \(w.currency == "RWF" ? String(Int(w.availableBalance)) : String(format: "%.2f", w.availableBalance))")
                        .font(IDS.scaledFont(size: 19, weight: .bold, relativeTo: .title3)).foregroundColor(.white)
                }
                .padding(18)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    ZStack {
                        LinearGradient(colors: [cardColor(w.currency), cardColor(w.currency), Color.black.opacity(0.18)], startPoint: .topLeading, endPoint: .bottomTrailing)
                        // Diagonal sheen -- the same "flat color read as a card"
                        // fix applied to every card-shaped visual in this app.
                        LinearGradient(colors: [Color.white.opacity(0.18), Color.clear], startPoint: .topLeading, endPoint: .bottomTrailing)
                    }
                )
                .cornerRadius(16)
                .padding(.horizontal, 4)
                .tag(w.id)
            }
        }
        .tabViewStyle(.page(indexDisplayMode: .always))
        .frame(height: 180)
    }
}
// Real customer-presented payment code (Pay-parity port, §239; corrected §241)
// -- see CustomerPaymentCodeResponse's own doc comment for the sourced contract.
// §239's first pass was QR-only, reasoning from Android's own implementation
// rather than a fresh real reference. Corrected the same way bank-mfe's
// identical §238 -> §240 fix was: the user pushed back directly ("100% kakaopay
// like"), and this session fetched KakaoPay's own real screenshots two ways --
// headless Chrome navigating their real App Store listing, and 3 real
// screenshots of the user's own live app. Both agree on a materially different
// design: the real code is a linear BARCODE (Code128, Korea's real 바코드결제
// standard, works with plain laser POS scanners) with a small QR secondary, not
// QR alone -- and the real card also shows the funding account and a real
// "nearby benefits" row (real nearby merchant discounts+distance), both real
// itunda data §239 dropped as "supplementary." A real "포인트 사용" toggle exists
// in the reference but itunda has no separate points balance -- deliberately
// still not faked, matching Android's own original honest scope-down. Barcode
// generation uses Core Image's native `CIFilter.code128BarcodeGenerator`
// (`QrScanCamera.swift`'s new `generateBarcodeImage`) -- no third-party library,
// same discipline `generateQrImage` already established for the QR half.
struct MyPaymentCodeCard: View {
    @State private var revealed = false
    @State private var code: CustomerPaymentCodeResponse?
    @State private var barcodeImage: UIImage?
    @State private var qrImage: UIImage?
    @State private var error: String?
    @State private var secondsLeft = 0
    @State private var accounts: [Account] = []
    @State private var selectedAccountId: String?
    @State private var linkedAccount: LinkedAccountDto?
    @State private var nearbyAds: [NearbyMerchantAdDto] = []
    @State private var refreshTask: Task<Void, Never>?
    @State private var countdownTask: Task<Void, Never>?
    @StateObject private var locationFetcher = MyPaymentCodeLocationFetcher()
    // Real "Toss Pay Money" detail/statement screen (user screenshots, 2026-08-21) --
    // see PayMoneyDetailScreen's own doc comment. Fetched+mapped here (App target,
    // already imports CoreNetwork) rather than inside PayMoneyDetailScreen itself,
    // same module-dependency-direction constraint as TransactionHistoryScreen's own
    // TransactionDisplayItem mapping in ContentView.
    @State private var showAccountDetail = false
    @State private var accountDetailTransactions: [TransactionDisplayItem]?
    @State private var accountDetailError: String?

    // Real Toss Bank/Toss Pay separation (2026-08-21) -- this code always pays out
    // of itunda Pay money, not Bank (MerchantService.chargeByCustomerCode's own real
    // default, auto-topped from Bank if short), so the default funding source here
    // must be PAY, not MAIN -- same bug found+fixed on bank-mfe's PayHub and
    // Android's PayTab the same session. MAIN kept only as a defensive
    // pre-backfill fallback.
    private var account: Account? {
        accounts.first(where: { $0.id == selectedAccountId }) ?? accounts.first(where: { $0.type == "PAY" }) ?? accounts.first(where: { $0.type == "MAIN" }) ?? accounts.first
    }

    var body: some View {
        VStack(spacing: 16) {
            ZStack {
                RoundedRectangle(cornerRadius: 14).fill(Color(.tertiarySystemBackground))
                if !revealed {
                    // Real reveal gate, matching Android's identical 2026-08-13
                    // KakaoPay-researched fix: the code requires an explicit tap
                    // before it shows, protecting a customer whose unlocked phone
                    // someone else picks up.
                    Button(action: { revealed = true; startRefreshLoop() }) {
                        VStack(spacing: 14) {
                            // Real fix (2026-08-24): was a raw SF Symbol lock.fill,
                            // itunda already has its own real LockGlyph (itundaface)
                            // for this exact security concept.
                            Circle().fill(Color.white).frame(width: 56, height: 56)
                                .overlay(LockGlyph(size: 24))
                            VStack(spacing: 2) {
                                Text("Your payment code is hidden").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                                Text("Protects you if someone else has your phone").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            }
                            Text("Tap to show").bold().foregroundColor(.white)
                                .padding(.horizontal, 28).padding(.vertical, 10)
                                .background(IDS.Colors.brand).cornerRadius(999)
                        }
                        .padding(20)
                    }
                } else if let barcodeImage, let qrImage {
                    VStack(spacing: 8) {
                        HStack(spacing: 10) {
                            Image(uiImage: barcodeImage)
                                .interpolation(.none)
                                .resizable()
                                .scaledToFit()
                                .frame(height: 60)
                                .frame(maxWidth: .infinity)
                                .padding(4)
                                .background(Color.white)
                                .cornerRadius(6)
                            Image(uiImage: qrImage)
                                .interpolation(.none)
                                .resizable()
                                .frame(width: 56, height: 56)
                                .padding(3)
                                .background(Color.white)
                                .cornerRadius(6)
                        }
                        Text(secondsLeft > 0 ? "Refreshes in \(secondsLeft)s" : "Refreshing…")
                            .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    }
                    .padding(16)
                } else if let error {
                    Text(error).font(.caption).foregroundColor(.red).padding(20)
                } else {
                    Text("Loading…").font(.caption).foregroundColor(IDS.Colors.textSecondary).padding(20)
                }
            }
            .frame(minHeight: 140)

            if let account {
                // Real drill-in to the "Toss Pay Money" detail/statement screen (user
                // screenshots, 2026-08-21) -- see PayMoneyDetailScreen's own doc comment.
                Button(action: { Task { await openAccountDetail(account) } }) {
                    HStack {
                        Text("itunda Pay").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                        Spacer()
                        CountUpText("\(Int(account.balance)) RWF").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                        Image(systemName: "chevron.right").font(.caption).foregroundColor(IDS.Colors.textTertiary)
                    }
                }
                .buttonStyle(.plain)
            }
            if let linkedAccount {
                HStack {
                    Text("Funding account").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    Spacer()
                    Text("\(linkedAccount.provider) \(linkedAccount.externalAccountNumberMasked)").font(.caption).foregroundColor(IDS.Colors.textPrimary)
                }
            }
            if !nearbyAds.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Nearby benefits").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 16) {
                            ForEach(nearbyAds) { nearbyAd in
                                VStack(spacing: 4) {
                                    Circle().fill(IDS.Colors.brand.opacity(0.15)).frame(width: 40, height: 40)
                                        .overlay(Text(String(nearbyAd.businessName.prefix(1)).uppercased()).font(IDS.scaledFont(size: 16, weight: .bold, relativeTo: .subheadline)).foregroundColor(IDS.Colors.brand))
                                    Text(nearbyAd.businessName).font(IDS.scaledFont(size: 11, weight: .regular, relativeTo: .caption2)).foregroundColor(IDS.Colors.textPrimary).lineLimit(1)
                                    Text("\(Int(nearbyAd.distanceKm * 1000))m").font(IDS.scaledFont(size: 11, weight: .regular, relativeTo: .caption2)).foregroundColor(IDS.Colors.textSecondary)
                                }
                                .frame(width: 64)
                            }
                        }
                    }
                }
            }
            if accounts.count > 1 {
                AccountCardCarousel(accounts: accounts, selectedAccountId: selectedAccountId) { selectedAccountId = $0 }
            }
        }
        .padding(20).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius).idsCardBorder(cornerRadius: IDS.Layout.cardCornerRadius)
        .task {
            // Real gap found+fixed (2026-08-21, direct user confirmation): only the
            // real payment-eligible types, not the customer's full account list --
            // this used to include SAVINGS/INVESTMENT/LOAN/GROUP, none of which are
            // real payment products, matching the backend's own new
            // PAYMENT_ELIGIBLE_ACCOUNT_TYPES allowlist (MerchantService.kt).
            let fetched = (try? await NetworkClient.shared.getAccounts().accounts) ?? []
            accounts = fetched.filter { $0.type == "PAY" || $0.type == "MAIN" || $0.type == "FOREIGN_CURRENCY" }
            linkedAccount = (try? await NetworkClient.shared.getLinkedAccounts().linkedAccounts.first(where: { $0.status == "LINKED" })) ?? nil
            locationFetcher.requestLocation()
        }
        .onChange(of: locationFetcher.coordinate?.latitude) { _, _ in
            guard let coordinate = locationFetcher.coordinate else { return }
            Task {
                nearbyAds = (try? await NetworkClient.shared.getNearbyMerchantAds(latitude: coordinate.latitude, longitude: coordinate.longitude).ads) ?? []
            }
        }
        .onDisappear {
            refreshTask?.cancel()
            countdownTask?.cancel()
        }
        .fullScreenCover(isPresented: $showAccountDetail) {
            if let account {
                PayMoneyDetailScreen(
                    currency: account.currency, balance: account.balance,
                    transactions: accountDetailTransactions, errorMessage: accountDetailError,
                    onBack: { showAccountDetail = false },
                    // Real gap, honestly scoped out for now: this leaf card has no wired
                    // Send/transfer entry point to reuse (iOS's Pay tab, unlike bank-mfe's/
                    // Android's, is pay-a-merchant-only here -- Send/transfer lives under
                    // the Bank tab's own top-level state, not reachable from this view).
                    // Closing back to Pay rather than routing somewhere unrelated.
                    onSend: { showAccountDetail = false },
                    onAddMoney: { showAccountDetail = false }
                )
            }
        }
    }

    private func openAccountDetail(_ account: Account) async {
        accountDetailTransactions = nil
        accountDetailError = nil
        showAccountDetail = true
        do {
            let transactions = try await NetworkClient.shared.getAccountTransactionHistory(accountId: account.id).transactions
            accountDetailTransactions = transactions.map { tx in
                TransactionDisplayItem(
                    id: tx.id, description: tx.description, amount: tx.amount, currency: tx.currency,
                    status: tx.status, isOutgoing: tx.senderId == account.userId, type: tx.type, createdAt: tx.createdAt
                )
            }
        } catch {
            accountDetailError = "Could not load your transaction history."
        }
    }

    private func parseExpiry(_ raw: String) -> Date {
        ISO8601DateFormatter().date(from: raw) ?? isoDateFormatterFractional.date(from: raw) ?? Date()
    }

    private func startRefreshLoop() {
        refreshTask?.cancel()
        refreshTask = Task {
            while !Task.isCancelled {
                do {
                    let result = try await NetworkClient.shared.generateCustomerPaymentCode(accountId: account?.id)
                    code = result
                    error = nil
                    // Real contract: both encode the RAW code, no itunda://...
                    // URL wrapping -- matches the real merchant-scanner contract.
                    barcodeImage = generateBarcodeImage(from: result.code, width: 260, height: 60)
                    qrImage = generateQrImage(from: result.code, size: 160)
                    let waitSeconds = max(parseExpiry(result.expiresAt).timeIntervalSinceNow - 10, 5)
                    try await Task.sleep(nanoseconds: UInt64(waitSeconds * 1_000_000_000))
                } catch {
                    if !Task.isCancelled { self.error = "Could not load your payment code." }
                    return
                }
            }
        }
        startCountdown()
    }

    private func startCountdown() {
        countdownTask?.cancel()
        countdownTask = Task {
            while !Task.isCancelled {
                if let code {
                    secondsLeft = max(Int(parseExpiry(code.expiresAt).timeIntervalSinceNow), 0)
                }
                try? await Task.sleep(nanoseconds: 1_000_000_000)
            }
        }
    }
}
