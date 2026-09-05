import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real "Coupon box" (itunda Pay redesign, 2026-08-28, direct user reference: real
// Toss Pay Coupon box screen) -- see this file's own web sibling (CouponBoxView.tsx)
// for the full sourced account of what's honestly scoped in vs. out. "Used/expired"
// is sourced purely from real redemption history -- browseCoupons already excludes
// expired coupons server-side, so a coupon that expired unused simply never appears
// anywhere rather than fabricating a bucket the real data doesn't populate.
private enum CouponTab: String, CaseIterable { case received = "Received", used = "Used" }

struct CouponBoxScreenView: View {
    var onBack: () -> Void = {}
    var onBrowseMerchants: () -> Void = {}

    @State private var tab: CouponTab = .received
    @State private var coupons: [CouponBrowseViewDto]?
    @State private var redemptions: [CouponRedemptionDto]?
    @State private var error: String?

    private var received: [CouponBrowseViewDto] {
        (coupons ?? []).filter { $0.eligible && !$0.alreadyRedeemed }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Back")
                Spacer()
                Text("Coupon box").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            Picker("", selection: $tab) {
                Text("Received \(received.count)").tag(CouponTab.received)
                Text("Used \((redemptions ?? []).count)").tag(CouponTab.used)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 20)

            if let error {
                Text(error).font(.caption).foregroundColor(.red).padding()
            } else if coupons == nil || redemptions == nil {
                ProgressView().padding(40)
            } else if tab == .received {
                if received.isEmpty {
                    VStack(spacing: 12) {
                        Text("You don't have any coupons").font(.subheadline).bold()
                        Text("Want coupons you can get right now?").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        Button(action: onBrowseMerchants) {
                            Text("Find new coupons").bold().foregroundColor(.white)
                                .padding(.horizontal, 24).padding(.vertical, 10)
                                .background(IDS.Colors.brand).cornerRadius(999)
                        }
                    }
                    .padding(.top, 48)
                } else {
                    List(received) { view in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(view.coupon.title).font(.subheadline).bold()
                            Text("\(view.merchantName) · \(couponDiscountLabel(view.coupon))").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                        .padding(.vertical, 4)
                    }
                    .listStyle(.plain)
                }
            } else if (redemptions ?? []).isEmpty {
                Text("You haven't used any coupons yet.").font(.caption).foregroundColor(IDS.Colors.textSecondary).padding(.top, 48)
            } else {
                List(redemptions ?? []) { redemption in
                    HStack {
                        Text(String(redemption.redeemedAt.prefix(10))).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        Spacer()
                        Text("-\(formatAmount(Int(redemption.discountAmount))) RWF").font(.subheadline).bold()
                    }
                }
                .listStyle(.plain)
            }
            Spacer()
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task {
            do {
                coupons = try await NetworkClient.shared.browseCoupons().coupons
                redemptions = try await NetworkClient.shared.getMyCouponRedemptions().redemptions
            } catch {
                self.error = "Could not load your coupons."
            }
        }
    }

    private func couponDiscountLabel(_ coupon: MerchantCouponPreviewDto) -> String {
        coupon.discountType == "PERCENT" ? "\(coupon.discountValue)% off" : "\(formatAmount(Int(coupon.discountValue))) RWF off"
    }
}
