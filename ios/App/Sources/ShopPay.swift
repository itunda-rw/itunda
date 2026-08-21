import SwiftUI
import UIKit
import CoreDesignSystem
import CoreNetwork
import CoreLocation


/// Real "pay a merchant" -- mirrors bank-mfe's `PayByCodeCard`/`PayByStaticQrCard` and
/// Android's `PayAMerchantSection` exactly. bank-mfe/Android already have both; this
/// was the first iOS client for either -- previously neither the dynamic per-sale flow
/// nor the static QR flow existed anywhere on this native consumer app.
/// Coupon-preview-before-pay (bank-mfe's own `previewPaymentIntent` flow, item 149/146)
/// closed 2026-08-01 -- see PayByCodeCard's own doc comment. Real camera QR scanning
/// added (Pay-parity port, §237, see QrScanCamera.swift) -- this app now has a real
/// scanner, closing the 9-day gap behind Android/web's own scanners.
// Real fix (2026-08-11) -- no longer private. This is itunda's real, working
// payment-collection UI (pay-by-code, pay-by-static-QR, Face Pay) -- see
// ContentView.swift's own PayScreen doc comment for why it's now called directly
// from there too, same App-target file as this one.
struct PayAMerchantSection: View {
    @Binding var paymentResult: CollectPaymentResultDto?
    // Real Face Pay -- see FacePaySettingsCard/PayByCodeCard's own doc comments. Lifted
    // here, same as bank-mfe's own ShoppingView, so this card and PayByCodeCard don't
    // each fetch enrollment status independently.
    @State private var facePayEnrolled: Bool?

    var body: some View {
        if let result = paymentResult {
            VStack(alignment: .leading, spacing: 6) {
                Text("Payment complete").font(.headline).bold().foregroundColor(IDS.Colors.textPrimary)
                Text(result.merchantName).font(.subheadline).foregroundColor(IDS.Colors.textPrimary)
                Text("\(Int(result.amount)) RWF").font(.title2).bold().foregroundColor(IDS.Colors.textPrimary)
                if result.cashbackEarned > 0 {
                    Text("+ \(Int(result.cashbackEarned)) RWF cashback").font(.footnote).foregroundColor(IDS.Colors.brand)
                }
                Button(action: { paymentResult = nil }) {
                    Text("Done").bold().foregroundColor(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(IDS.Colors.brand).cornerRadius(10)
                }
            }
            .padding(18).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
        } else {
            VStack(alignment: .leading, spacing: 10) {
                MyPaymentCodeCard()
                FacePaySettingsCard(enrolled: facePayEnrolled, onChanged: { Task { await loadFacePayStatus() } })
                PayByCodeCard(facePayEnrolled: facePayEnrolled ?? false, onPaid: { paymentResult = $0 })
                PayByStaticQrCard(onPaid: { paymentResult = $0 })
            }
            .task { await loadFacePayStatus() }
        }
    }

    private func loadFacePayStatus() async {
        facePayEnrolled = (try? await NetworkClient.shared.getFacePayStatus())?.enrolled
    }
}

/// Real Face Pay enroll/disable toggle -- see rw.itunda.merchant.FacePayService's own
/// doc comment. bank-mfe/Android already have this; this is the first iOS client.
/// Enrolling swaps Pay-by-code's own collect call to the Face Pay channel -- same manual
/// code entry, just a different real ledger channel label, matching bank-mfe's own
/// honest scope exactly (no device biometric prompt gates it on any client, itunda's own).
struct FacePaySettingsCard: View {
    let enrolled: Bool?
    let onChanged: () -> Void

    @State private var busy = false
    @State private var error: String?

    var body: some View {
        if enrolled == nil {
            Color(.secondarySystemBackground).frame(height: 64).cornerRadius(IDS.Layout.cardCornerRadius)
        } else {
            VStack(alignment: .leading, spacing: 6) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("😊 Face Pay").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                        Text(enrolled == true ? "Enabled — authorize payment codes with your face, no code re-entry needed" : "Not enabled on this account")
                            .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    }
                    Spacer()
                    Button(action: { Task { await toggle() } }) {
                        Text(busy ? "…" : (enrolled == true ? "Disable" : "Enable"))
                            .bold().font(.caption).foregroundColor(enrolled == true ? IDS.Colors.textPrimary : .white)
                            .padding(.horizontal, 14).padding(.vertical, 8)
                            .background(enrolled == true ? Color(.tertiarySystemBackground) : IDS.Colors.brand).cornerRadius(8)
                    }
                    .disabled(busy)
                }
                if let error {
                    Text(error).font(.caption).foregroundColor(.red)
                }
            }
            .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
        }
    }

    private func toggle() async {
        busy = true
        error = nil
        defer { busy = false }
        do {
            if enrolled == true {
                _ = try await NetworkClient.shared.revokeFacePay()
            } else {
                _ = try await NetworkClient.shared.enrollFacePay()
            }
            onChanged()
        } catch {
            self.error = "Could not update Face Pay."
        }
    }
}

func couponDiscountLabel(_ c: MerchantCouponPreviewDto) -> String {
    c.discountType == "PERCENT" ? "\(Int(c.discountValue))% off" : "\(Int(c.discountValue)) RWF off"
}

// Real Coupang 타임특가 (Time Deal, item 226) countdown -- mirrors bank-mfe/Android's
// own formatDealCountdown exactly.
func formatTimeDealCountdown(_ endsAt: String) -> String {
    guard let end = ISO8601DateFormatter(withFractionalSeconds: true).date(from: endsAt) ?? ISO8601DateFormatter().date(from: endsAt) else { return "Ending soon" }
    let secondsLeft = end.timeIntervalSinceNow
    if secondsLeft <= 0 { return "Ending soon" }
    let totalMinutes = Int(secondsLeft / 60)
    let hours = totalMinutes / 60
    let minutes = totalMinutes % 60
    return hours > 0 ? "\(hours)h \(minutes)m left" : "\(minutes)m left"
}

/// Real coupon-preview-before-pay (item 149/146) -- closes the deliberate scope-down
/// this struct's own doc comment previously named. Mirrors bank-mfe's PayByCodeCard
/// exactly: a non-Face-Pay code with real eligible coupons stops at a preview step
/// (merchant/amount + coupon picker) before the actual collect() call; Face Pay and a
/// code with zero eligible coupons both skip straight to a direct pay.

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
                    // Small light rectangle mimicking a real card's EMV chip -- a
                    // cheap, honest visual cue that reads as "card" at a glance,
                    // matching Android's identical real-card metaphor.
                    RoundedRectangle(cornerRadius: 4).fill(Color.white.opacity(0.35)).frame(width: 32, height: 24)
                    Spacer()
                    Text(w.type == "MAIN" ? "itunda Pay" : "itunda Pay \(w.currency)")
                        .font(.system(size: 13, weight: .bold)).foregroundColor(.white)
                    Text("\(w.currency) \(w.currency == "RWF" ? String(Int(w.availableBalance)) : String(format: "%.2f", w.availableBalance))")
                        .font(.system(size: 19, weight: .bold)).foregroundColor(.white)
                }
                .padding(18)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(cardColor(w.currency))
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

    private var account: Account? {
        accounts.first(where: { $0.id == selectedAccountId }) ?? accounts.first(where: { $0.type == "MAIN" }) ?? accounts.first
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
                            Circle().fill(Color.white).frame(width: 56, height: 56)
                                .overlay(Image(systemName: "lock.fill").foregroundColor(IDS.Colors.textSecondary))
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
                HStack {
                    Text("itunda Pay").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    Spacer()
                    Text("\(Int(account.balance)) RWF").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                }
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
                                        .overlay(Text(String(nearbyAd.businessName.prefix(1)).uppercased()).font(.system(size: 16, weight: .bold)).foregroundColor(IDS.Colors.brand))
                                    Text(nearbyAd.businessName).font(.system(size: 11)).foregroundColor(IDS.Colors.textPrimary).lineLimit(1)
                                    Text("\(Int(nearbyAd.distanceKm * 1000))m").font(.system(size: 11)).foregroundColor(IDS.Colors.textSecondary)
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
        .padding(20).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
        .task {
            accounts = (try? await NetworkClient.shared.getAccounts().accounts) ?? []
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

struct PayByCodeCard: View {
    let facePayEnrolled: Bool
    let onPaid: (CollectPaymentResultDto) -> Void

    @State private var code = ""
    @State private var submitting = false
    @State private var error: String?
    @State private var needsDeviceVerification = false
    @State private var preview: PaymentIntentPreviewResponse?
    @State private var eligibleCoupons: [MerchantCouponViewDto] = []
    @State private var selectedCouponId: String?
    // Real camera-scan support (Pay-parity port, §237) -- see QrScanCamera.swift's
    // own header for the sourced contract. `manualEntry` alone decides capture mode
    // (NOT `facePayEnrolled`, which only changes how the found code is authorized
    // afterward) -- bank-mfe's own identical card briefly had this coupled and it
    // was a real, confirmed bug (Face-Pay users locked out of scanning entirely, see
    // docs/DESIGN_REFERENCES.md §234) -- built correctly here from the start.
    @State private var scanUnavailable = false
    @State private var manualEntry = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(manualEntry ? "Pay by code" : "Scan to pay").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
            Text(manualEntry
                ? (facePayEnrolled ? "Enter the code the merchant shows you to authorize with your face." : "Enter the payment code the merchant shows you.")
                : (facePayEnrolled ? "Point your camera at the merchant's QR code — you'll confirm with your face." : "Point your camera at the merchant's QR code to pay instantly and earn cashback."))
                .font(.caption).foregroundColor(IDS.Colors.textSecondary)
            if let preview {
                Text(preview.businessName).font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                Text("\(Int(preview.amount)) RWF").font(.title2).bold().foregroundColor(IDS.Colors.textPrimary)
                Text("Apply a coupon?").font(.footnote).bold().foregroundColor(IDS.Colors.textPrimary)
                Button(action: { selectedCouponId = nil }) {
                    HStack {
                        Image(systemName: selectedCouponId == nil ? "largecircle.fill.circle" : "circle")
                        Text("No coupon").font(.footnote)
                    }.foregroundColor(IDS.Colors.textPrimary)
                }
                ForEach(eligibleCoupons) { c in
                    Button(action: { selectedCouponId = c.coupon.id }) {
                        HStack {
                            Image(systemName: selectedCouponId == c.coupon.id ? "largecircle.fill.circle" : "circle")
                            Text("\(c.coupon.title) — \(couponDiscountLabel(c.coupon))").font(.footnote)
                        }.foregroundColor(IDS.Colors.textPrimary)
                    }
                }
                if let error {
                    Text(error).font(.caption).foregroundColor(.red)
                }
                HStack(spacing: 10) {
                    Button(action: { Task { await payDirect(couponId: selectedCouponId) } }) {
                        Text(submitting ? "Paying…" : "Pay").bold().foregroundColor(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(IDS.Colors.brand).cornerRadius(10)
                    }
                    .disabled(submitting)
                    Button(action: cancelPreview) {
                        Text("Cancel").bold().foregroundColor(IDS.Colors.textPrimary)
                            .padding(.horizontal, 16).padding(.vertical, 12)
                            .background(Color(.tertiarySystemBackground)).cornerRadius(10)
                    }
                    .disabled(submitting)
                }
            } else if !manualEntry {
                if !scanUnavailable && !submitting {
                    QrScanCameraView(onDetect: handleScan, onUnavailable: { scanUnavailable = true })
                        .frame(height: 220).cornerRadius(12).clipped()
                }
                if submitting {
                    Text("Looking up code…").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }
                if let error {
                    Text(error).font(.caption).foregroundColor(.red)
                }
                Button(action: { manualEntry = true }) {
                    Text(scanUnavailable ? "Enter code manually" : "No camera? Enter code instead")
                        .bold().foregroundColor(IDS.Colors.textPrimary)
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(Color(.tertiarySystemBackground)).cornerRadius(10)
                }
            } else {
                HStack(spacing: 10) {
                    TextField("Payment code", text: $code)
                        .padding(12).background(IDS.Colors.backgroundPrimary).cornerRadius(10)
                    Button(action: { Task { await submit() } }) {
                        Text(submitting ? (facePayEnrolled ? "Authorizing…" : "Paying…") : (facePayEnrolled ? "😊 Pay" : "Pay"))
                            .bold().foregroundColor(.white)
                            .padding(.horizontal, 16).padding(.vertical, 14)
                            .background(submitting || code.isEmpty ? IDS.Colors.textTertiary : IDS.Colors.brand)
                            .cornerRadius(10)
                    }
                    .disabled(submitting || code.isEmpty)
                }
                if let error {
                    Text(error).font(.caption).foregroundColor(.red)
                }
                if !scanUnavailable {
                    Button(action: { manualEntry = false }) {
                        Text("Scan a QR code instead").bold().foregroundColor(IDS.Colors.textPrimary)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(Color(.tertiarySystemBackground)).cornerRadius(10)
                    }
                }
            }
        }
        .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
        // Real fix (2026-08-10) -- see MultiCartView's own identical fix above for the
        // full account. payDirect resets needsDeviceVerification itself.
        DeviceStepUpHost(visible: needsDeviceVerification, onDismiss: { needsDeviceVerification = false }, onVerified: { await payDirect(couponId: selectedCouponId) })
    }

    private func handleScan(_ raw: String) {
        code = parseQrParam(raw, key: "intentId")
        Task { await submit() }
    }

    private func payDirect(couponId: String? = nil) async {
        submitting = true
        error = nil
        needsDeviceVerification = false
        defer { submitting = false }
        do {
            let result = facePayEnrolled
                ? try await NetworkClient.shared.collectWithFacePay(intentId: code.trimmingCharacters(in: .whitespaces))
                : try await NetworkClient.shared.collectPayment(intentId: code.trimmingCharacters(in: .whitespaces), couponId: couponId)
            code = ""
            preview = nil
            eligibleCoupons = []
            selectedCouponId = nil
            onPaid(result)
        } catch NetworkError.deviceNotVerified {
            needsDeviceVerification = true
        } catch {
            self.error = "Could not complete this payment."
        }
    }

    private func submit() async {
        error = nil
        if facePayEnrolled {
            await payDirect()
            return
        }
        submitting = true
        do {
            let r = try await NetworkClient.shared.previewPaymentIntent(intentId: code.trimmingCharacters(in: .whitespaces))
            let eligible = r.coupons.filter { $0.eligible && !$0.alreadyRedeemed }
            if eligible.isEmpty {
                submitting = false
                await payDirect()
            } else {
                preview = r
                eligibleCoupons = eligible
                submitting = false
            }
        } catch {
            self.error = "Could not look up this payment code."
            submitting = false
        }
    }

    private func cancelPreview() {
        preview = nil
        eligibleCoupons = []
        selectedCouponId = nil
        error = nil
    }
}

struct PayByStaticQrCard: View {
    let onPaid: (CollectPaymentResultDto) -> Void

    @State private var merchantId = ""
    @State private var amount = ""
    @State private var submitting = false
    @State private var error: String?
    // Real camera-scan support (Pay-parity port, §237) -- see QrScanCamera.swift's
    // own header. Same real Kakao Pay 정액 QR distinction PayByCodeCard's own doc
    // comment establishes: the merchant's id is fixed/scanned, the amount always
    // stays customer-typed, so a scan lands in the typed-amount view with merchantId
    // prefilled rather than paying immediately -- matches bank-mfe's identical
    // `PayByStaticQrCard` handleScan exactly.
    @State private var scanUnavailable = false
    @State private var manualEntry = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Pay a merchant's static QR").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
            Text("For a merchant with one permanent code (like a market stall) — scan their code, then say how much you're paying.")
                .font(.caption).foregroundColor(IDS.Colors.textSecondary)
            if !manualEntry && merchantId.isEmpty {
                if !scanUnavailable {
                    QrScanCameraView(onDetect: handleScan, onUnavailable: { scanUnavailable = true })
                        .frame(height: 220).cornerRadius(12).clipped()
                }
                Button(action: { manualEntry = true }) {
                    Text(scanUnavailable ? "Enter merchant ID manually" : "No camera? Enter merchant ID instead")
                        .bold().foregroundColor(IDS.Colors.textPrimary)
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(Color(.tertiarySystemBackground)).cornerRadius(10)
                }
            } else {
                TextField("Merchant ID", text: $merchantId)
                    .padding(12).background(IDS.Colors.backgroundPrimary).cornerRadius(10)
                HStack(spacing: 10) {
                    TextField("Amount (RWF)", text: $amount)
                        .keyboardType(.decimalPad)
                        .padding(12).background(IDS.Colors.backgroundPrimary).cornerRadius(10)
                    Button(action: { Task { await pay() } }) {
                        Text(submitting ? "Paying…" : "Pay").bold().foregroundColor(.white)
                            .padding(.horizontal, 16).padding(.vertical, 14)
                            .background(submitting || merchantId.isEmpty || Double(amount) == nil ? IDS.Colors.textTertiary : IDS.Colors.brand)
                            .cornerRadius(10)
                    }
                    .disabled(submitting || merchantId.isEmpty || Double(amount) == nil)
                }
                if !scanUnavailable {
                    Button(action: { manualEntry = false; merchantId = "" }) {
                        Text("Scan a QR code instead").bold().foregroundColor(IDS.Colors.textPrimary)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(Color(.tertiarySystemBackground)).cornerRadius(10)
                    }
                }
            }
            if let error {
                Text(error).font(.caption).foregroundColor(.red)
            }
        }
        .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
    }

    private func handleScan(_ raw: String) {
        merchantId = parseQrParam(raw, key: "merchantId")
        manualEntry = true
    }

    private func pay() async {
        guard let numericAmount = Double(amount), numericAmount > 0 else {
            error = "Enter a valid amount."
            return
        }
        submitting = true
        error = nil
        defer { submitting = false }
        do {
            let result = try await NetworkClient.shared.payByStaticQr(merchantId: merchantId.trimmingCharacters(in: .whitespaces), amount: numericAmount)
            merchantId = ""
            amount = ""
            onPaid(result)
        } catch {
            self.error = "Could not complete this payment."
        }
    }
}
