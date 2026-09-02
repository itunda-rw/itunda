import SwiftUI
import CoreLocation
import CoreDesignSystem
import CoreNetwork

// Real gap found 2026-08-30 (project_itunda_money_formatting_sweep's own standing
// convention -- comma thousands-separator for every whole-number RWF amount --
// never reached this file). Same per-file shape TransactionHistoryScreen.swift
// already established.
private func formatAmount(_ value: Int) -> String {
    let formatter = NumberFormatter()
    formatter.numberStyle = .decimal
    formatter.groupingSeparator = ","
    return formatter.string(from: NSNumber(value: value)) ?? "0"
}

// Real Toss Pay home reference (4 screenshots, 2026-08-22, direct user follow-up:
// "it should look 100% like toss pay UI/UX features everything") -- see PayScreen's
// own doc comment below. Every section here is real itunda data -- see each type's own
// doc comment for exactly what backs it and what's honestly scoped out (no real
// backend): the reference's cross-merchant coupon wallet and external online-merchant
// integrations.

/// Real "my location" for NearbyMerchantsBanner -- same per-file CLLocationManager
/// fetcher convention MyPaymentCodeCard.swift's own MyPaymentCodeLocationFetcher
/// already establishes.
final class NearbyMerchantsLoader: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var merchants: [NearbyMerchantDto] = []
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
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        if manager.authorizationStatus == .authorizedWhenInUse || manager.authorizationStatus == .authorizedAlways {
            manager.requestLocation()
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let coordinate = locations.last?.coordinate else { return }
        Task { @MainActor in
            merchants = (try? await NetworkClient.shared.getNearbyMerchants(latitude: coordinate.latitude, longitude: coordinate.longitude))?.merchants ?? []
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {}

    var averageCashbackRatePercent: Double? {
        guard !merchants.isEmpty else { return nil }
        let percent = merchants.reduce(0.0) { $0 + $1.cashbackRate } / Double(merchants.count) * 100
        return (percent * 10).rounded() / 10
    }
}

// Real MerchantDiscoveryService.nearby -- every ACTIVE merchant within radiusKm,
// distinct from the existing getNearbyMerchantAds rail inside MyPaymentCodeCard
// (that's paid ad placements, a subset -- this is the real total). Every merchant
// earns the payer real cashback on collect() (ShoppingCashbackService), so "earn
// cashback" is a true claim for all of them, not just FacePay-enrolled ones.
struct NearbyMerchantsBanner: View {
    let merchants: [NearbyMerchantDto]
    let onTap: () -> Void

    var body: some View {
        if !merchants.isEmpty {
            Button(action: onTap) {
                HStack {
                    HStack(spacing: 8) {
                        Image(systemName: "mappin.circle").foregroundColor(IDS.Colors.textSecondary)
                        Text("\(merchants.count) itunda merchant\(merchants.count == 1 ? "" : "s") nearby — earn cashback")
                            .font(IDS.scaledFont(size: 13, weight: .semibold, relativeTo: .footnote))
                            .foregroundColor(IDS.Colors.textPrimary)
                    }
                    Spacer()
                    Image(systemName: "chevron.right").font(IDS.scaledFont(size: 12, weight: .regular, relativeTo: .caption1)).foregroundColor(IDS.Colors.textTertiary)
                }
                .padding(.horizontal, 16).padding(.vertical, 12)
                .background(Color(.secondarySystemBackground))
                .cornerRadius(999)
            }
        }
    }
}

// Real destination for NearbyMerchantsBanner's tap -- a plain list of the same real
// merchants (name, category, distance, real per-merchant cashback rate), not a dead
// link. No embedded map here -- itunda's own self-hosted maps stack lives on a
// separate, heavier screen; this is a lightweight preview.
struct NearbyMerchantsSheet: View {
    let merchants: [NearbyMerchantDto]

    var body: some View {
        NavigationStack {
            List(merchants.prefix(20)) { merchant in
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(merchant.businessName).font(IDS.scaledFont(size: 14, weight: .semibold, relativeTo: .subheadline))
                        Text(merchant.category ?? "Merchant").font(IDS.scaledFont(size: 12, weight: .regular, relativeTo: .caption1)).foregroundColor(IDS.Colors.textSecondary)
                    }
                    Spacer()
                    Text(String(format: "%.1f km", merchant.distanceKm)).font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote)).foregroundColor(IDS.Colors.textSecondary)
                }
            }
            .navigationTitle("Merchants nearby")
        }
    }
}

// Real "Rewards you received" summary -- rewardsTotal (RewardsService) plus the real
// itunda balance where the caller has one. No coupon count -- itunda has no
// cross-merchant coupon wallet (coupons are scoped to one merchant at a time), the
// same honest scope-down this file applies everywhere else.
struct RewardsSummaryRow: View {
    let rewardsTotal: Double

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("Rewards earned").font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote)).foregroundColor(IDS.Colors.textSecondary)
            Text("\(formatAmount(Int(rewardsTotal))) RWF").font(IDS.scaledFont(size: 15, weight: .bold, relativeTo: .subheadline)).foregroundColor(IDS.Colors.textPrimary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// Real "FAQ / Send feedback" -- itunda has no FAQ-content system, so both honestly
// route to the real SupportScreenView rather than fabricating static FAQ copy -- same
// real destination, shown as two rows to match the reference's own layout.
struct GetHelpLinks: View {
    let onOpenSupport: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button(action: onOpenSupport) {
                Text("FAQ").font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote)).foregroundColor(IDS.Colors.textSecondary).frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.vertical, 10)
            Button(action: onOpenSupport) {
                Text("Send feedback").font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote)).foregroundColor(IDS.Colors.textSecondary).frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.vertical, 10)
        }
    }
}
struct RewardsPreviewSection: View {
    let tasks: [RewardTaskDto]
    let claimingId: String?
    let onClaim: (String) -> Void

    private var preview: [RewardTaskDto] {
        Array(tasks.filter { !$0.claimed && $0.eligible }.prefix(3))
    }

    var body: some View {
        if !preview.isEmpty {
            VStack(alignment: .leading, spacing: 4) {
                Text("Get more rewards")
                    .font(IDS.scaledFont(size: 16, weight: .bold, relativeTo: .callout))
                    .foregroundColor(IDS.Colors.textPrimary)
                ForEach(preview) { task in
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(task.title).font(IDS.scaledFont(size: 14, weight: .semibold, relativeTo: .subheadline)).foregroundColor(IDS.Colors.textPrimary)
                            Text(task.subtitle).font(IDS.scaledFont(size: 12, weight: .regular, relativeTo: .caption1)).foregroundColor(IDS.Colors.textSecondary)
                        }
                        Spacer()
                        Button(action: { onClaim(task.id) }) {
                            Text(claimingId == task.id ? "…" : "+\(formatAmount(Int(task.rewardAmount))) RWF")
                                .font(IDS.scaledFont(size: 13, weight: .bold, relativeTo: .footnote))
                                .foregroundColor(IDS.Colors.brand)
                                .padding(.horizontal, 12).padding(.vertical, 6)
                                .background(IDS.Colors.brand.opacity(0.12))
                                .cornerRadius(999)
                        }
                        .disabled(claimingId != nil)
                    }
                    .padding(.vertical, 10)
                }
            }
        }
    }
}

// A bold "Pay" wordmark plus a settings icon (routes to the You tab, tag 4 -- itunda
// has no dedicated Pay-settings screen, so this is an honest substitute), the real
// NearbyMerchantsBanner/RewardsSummaryRow/RewardsPreviewSection above, and GetHelpLinks
// routing to the real SupportScreenView -- rather than fabricating Toss-specific rows
// ("Toss Prime", "Google gift codes", cross-merchant coupon wallet, external
// online-merchant integrations) itunda has no real backend for.
public struct PayScreen<CardDestination: View, SupportDestination: View>: View {
    @State private var paymentResult: CollectPaymentResultDto?
    @State private var rewardsTotal: Double = 0
    @State private var rewardTasks: [RewardTaskDto] = []
    @State private var claimingRewardId: String?
    @State private var showSupport = false
    @State private var showNearbyMerchants = false
    @StateObject private var nearbyMerchantsLoader = NearbyMerchantsLoader()
    let onSwitchToYou: () -> Void
    // Real injected view-builder slots (2026-09-02, Pay Feature-module
    // decomposition) -- CardScreenView is shared with ContentView.swift/
    // BenefitsShopAllScreens.swift (stays in :App); SupportScreenView lives in
    // FeatureSupport's own Sources (not Interface), so a direct import from
    // FeaturePay would be a forbidden cross-Feature dependency
    // (scripts/ios-silo-boundary-check.py). Generic @ViewBuilder injection matches
    // this codebase's own established pattern (RoomLockGate/EatsOrderRow/
    // CommerceOrderRow's `<Content: View>` shape) rather than inventing a new one.
    // Each closure receives PayScreen's own dismiss action, so the injected
    // destination's real "back" button (CardScreenView(onBack:)/
    // SupportScreenView(onBack:)) can close the sheet PayScreen itself owns
    // (showCard/showSupport), not just rely on swipe-to-dismiss.
    @ViewBuilder let cardDestination: (@escaping () -> Void) -> CardDestination
    @ViewBuilder let supportDestination: (@escaping () -> Void) -> SupportDestination
    // Real injected callback, same onOpenRewards precedent FeatureAssets'
    // OverviewScreenView already established -- SaroniteRewardTasksView is
    // :App-only.
    let onOpenRewardsMiniApp: () -> Void
    // Real itunda Pay redesign (2026-08-28, direct user reference: real Toss Pay
    // screenshots) -- itunda's own real issued-card summary row, Coupon Box, and
    // adapted Membership screen. See PayFundingSourcePickerView.swift/
    // CouponBoxScreenView.swift/MembershipScreenView.swift's own doc comments.
    @State private var hasCard: Bool?
    @State private var cardLast4: String?
    @State private var cardFrozen = false
    @State private var payBalance: Double = 0
    @State private var showCard = false
    @State private var showCouponBox = false
    @State private var showMembership = false

    public init(
        onSwitchToYou: @escaping () -> Void = {},
        onOpenRewardsMiniApp: @escaping () -> Void = {},
        @ViewBuilder cardDestination: @escaping (@escaping () -> Void) -> CardDestination,
        @ViewBuilder supportDestination: @escaping (@escaping () -> Void) -> SupportDestination
    ) {
        self.onSwitchToYou = onSwitchToYou
        self.onOpenRewardsMiniApp = onOpenRewardsMiniApp
        self.cardDestination = cardDestination
        self.supportDestination = supportDestination
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                HStack {
                    Text("Pay")
                        .font(IDS.scaledFont(size: 28, weight: .bold, relativeTo: .title1))
                        .foregroundColor(IDS.Colors.textPrimary)
                    Spacer()
                    Button(action: onSwitchToYou) {
                        Image(systemName: "gearshape")
                            .font(IDS.scaledFont(size: 18, weight: .medium, relativeTo: .title3))
                            .foregroundColor(IDS.Colors.textSecondary)
                    }
                    .accessibilityLabel("Pay settings")
                }
                .padding(.horizontal, 20)
                NearbyMerchantsBanner(merchants: nearbyMerchantsLoader.merchants, onTap: { showNearbyMerchants = true })
                    .padding(.horizontal, 20)
                PayAMerchantSection(
                    paymentResult: $paymentResult, cashbackRatePercent: nearbyMerchantsLoader.averageCashbackRatePercent,
                    onOpenCard: { showCard = true },
                )
                    .padding(.horizontal, 20)
                RewardsSummaryRow(rewardsTotal: rewardsTotal)
                    .padding(.horizontal, 20)
                // Real itunda-issued card summary row -- mirrors the real reference's
                // own linked-card row using 100% real itunda data, never a fabricated
                // "auto-apply points" claim a real external card issuer would make.
                if let hasCard {
                    Group {
                        if hasCard {
                            Button(action: { showCard = true }) {
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("Card •••• \(cardLast4 ?? "")").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                                        Text(cardFrozen ? "Frozen" : "Active").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                    }
                                    Spacer()
                                    Image(systemName: "chevron.right").font(.caption).foregroundColor(IDS.Colors.textTertiary)
                                }
                            }
                            .buttonStyle(.plain)
                        } else {
                            HStack {
                                Text("No card yet").font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
                                Spacer()
                                Button(action: { showCard = true }) { Text("Get a card").font(.caption).bold() }
                            }
                            .padding(16)
                            .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(IDS.Colors.divider, style: StrokeStyle(lineWidth: 1, dash: [4, 3])))
                        }
                    }
                    .padding(.horizontal, 20)
                }
                // Real "Points · Pay Money" summary row -- the real reference's own
                // Membership-screen entry point.
                Button(action: { showMembership = true }) {
                    HStack {
                        Text("Points · Pay Money").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                        Spacer()
                        Text("\(Int(rewardsTotal + payBalance)) RWF").font(.subheadline).bold().foregroundColor(IDS.Colors.brand)
                        Image(systemName: "chevron.right").font(.caption).foregroundColor(IDS.Colors.textTertiary)
                    }
                }
                .buttonStyle(.plain)
                .padding(.horizontal, 20)
                // Real "Your Coupons" row -- see CouponBoxScreenView.swift's own doc
                // comment for the real GET /api/v1/merchant/coupons/browse endpoint
                // this now leads to.
                Button(action: { showCouponBox = true }) {
                    HStack {
                        Text("Your Coupons").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                        Spacer()
                        Image(systemName: "chevron.right").font(.caption).foregroundColor(IDS.Colors.textTertiary)
                    }
                }
                .buttonStyle(.plain)
                .padding(.horizontal, 20)
                RewardsPreviewSection(tasks: rewardTasks, claimingId: claimingRewardId, onClaim: claimReward)
                    .padding(.horizontal, 20)
                GetHelpLinks(onOpenSupport: { showSupport = true })
                    .padding(.horizontal, 24)
            }
            .padding(.top, 24)
        }
        .background(Color(.systemGroupedBackground).edgesIgnoringSafeArea(.all))
        .task {
            let result = try? await NetworkClient.shared.getRewardTasks()
            rewardTasks = result?.tasks ?? []
            rewardsTotal = result?.rewardsTotal ?? 0
            nearbyMerchantsLoader.requestLocation()
            payBalance = ((try? await NetworkClient.shared.getAccounts().accounts) ?? []).first(where: { $0.type == "PAY" })?.balance ?? 0
            do {
                let card = try await NetworkClient.shared.getMyCard().card
                hasCard = true
                cardLast4 = card.last4
                cardFrozen = card.frozen
            } catch {
                hasCard = false
            }
        }
        .sheet(isPresented: $showSupport) {
            supportDestination({ showSupport = false })
        }
        .sheet(isPresented: $showNearbyMerchants) {
            NearbyMerchantsSheet(merchants: nearbyMerchantsLoader.merchants)
        }
        .sheet(isPresented: $showCard) {
            cardDestination({ showCard = false })
        }
        .sheet(isPresented: $showCouponBox) {
            CouponBoxScreenView(onBack: { showCouponBox = false }, onBrowseMerchants: { showCouponBox = false })
        }
        .sheet(isPresented: $showMembership) {
            // Real gap, honestly scoped out for now (same shape as MyPaymentCodeCard.
            // swift's own already-documented "no wired Send entry point" gap): the
            // real Pay Money detail/statement screen needs its own transaction fetch
            // (see MyPaymentCodeCard.openAccountDetail), not duplicated here just for
            // this one row. Closing back to Pay rather than routing somewhere unrelated.
            MembershipScreenView(
                onBack: { showMembership = false },
                onOpenPayMoney: { showMembership = false },
                onOpenRewardsMiniApp: onOpenRewardsMiniApp,
            )
        }
    }

    private func claimReward(_ taskId: String) {
        Task {
            claimingRewardId = taskId
            defer { claimingRewardId = nil }
            _ = try? await NetworkClient.shared.claimRewardTask(taskId: taskId)
            let result = try? await NetworkClient.shared.getRewardTasks()
            rewardTasks = result?.tasks ?? []
            rewardsTotal = result?.rewardsTotal ?? 0
        }
    }
}
