import SwiftUI
import CoreLocation
import CoreDesignSystem
import CoreNetwork

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
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(IDS.Colors.textPrimary)
                    }
                    Spacer()
                    Image(systemName: "chevron.right").font(.system(size: 12)).foregroundColor(IDS.Colors.textTertiary)
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
                        Text(merchant.businessName).font(.system(size: 14, weight: .semibold))
                        Text(merchant.category ?? "Merchant").font(.system(size: 12)).foregroundColor(IDS.Colors.textSecondary)
                    }
                    Spacer()
                    Text(String(format: "%.1f km", merchant.distanceKm)).font(.system(size: 13)).foregroundColor(IDS.Colors.textSecondary)
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
            Text("Rewards earned").font(.system(size: 13)).foregroundColor(IDS.Colors.textSecondary)
            Text("\(Int(rewardsTotal)) RWF").font(.system(size: 15, weight: .bold)).foregroundColor(IDS.Colors.textPrimary)
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
                Text("FAQ").font(.system(size: 13)).foregroundColor(IDS.Colors.textSecondary).frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.vertical, 10)
            Button(action: onOpenSupport) {
                Text("Send feedback").font(.system(size: 13)).foregroundColor(IDS.Colors.textSecondary).frame(maxWidth: .infinity, alignment: .leading)
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
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(IDS.Colors.textPrimary)
                ForEach(preview) { task in
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(task.title).font(.system(size: 14, weight: .semibold)).foregroundColor(IDS.Colors.textPrimary)
                            Text(task.subtitle).font(.system(size: 12)).foregroundColor(IDS.Colors.textSecondary)
                        }
                        Spacer()
                        Button(action: { onClaim(task.id) }) {
                            Text(claimingId == task.id ? "…" : "+\(Int(task.rewardAmount)) RWF")
                                .font(.system(size: 13, weight: .bold))
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
struct PayScreen: View {
    @State private var paymentResult: CollectPaymentResultDto?
    @State private var rewardsTotal: Double = 0
    @State private var rewardTasks: [RewardTaskDto] = []
    @State private var claimingRewardId: String?
    @State private var showSupport = false
    @State private var showNearbyMerchants = false
    @StateObject private var nearbyMerchantsLoader = NearbyMerchantsLoader()
    var onSwitchToYou: () -> Void = {}

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                HStack {
                    Text("Pay")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundColor(IDS.Colors.textPrimary)
                    Spacer()
                    Button(action: onSwitchToYou) {
                        Image(systemName: "gearshape")
                            .font(.system(size: 18, weight: .medium))
                            .foregroundColor(IDS.Colors.textSecondary)
                    }
                    .accessibilityLabel("Pay settings")
                }
                .padding(.horizontal, 20)
                NearbyMerchantsBanner(merchants: nearbyMerchantsLoader.merchants, onTap: { showNearbyMerchants = true })
                    .padding(.horizontal, 20)
                PayAMerchantSection(paymentResult: $paymentResult, cashbackRatePercent: nearbyMerchantsLoader.averageCashbackRatePercent)
                    .padding(.horizontal, 20)
                RewardsSummaryRow(rewardsTotal: rewardsTotal)
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
        }
        .sheet(isPresented: $showSupport) {
            SupportScreenView(onBack: { showSupport = false })
        }
        .sheet(isPresented: $showNearbyMerchants) {
            NearbyMerchantsSheet(merchants: nearbyMerchantsLoader.merchants)
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
