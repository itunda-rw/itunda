import SwiftUI
import UIKit
import CoreDesignSystem
import CoreNetwork
import CoreLocation

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

/// Real Kakao Pay 정기결제/Toss 빌링키-style subscribe/cancel -- subscribing charges the
/// first cycle immediately (real "인증 + 첫결제"), same as
/// MerchantBillingService.subscribe's own doc comment. One real active subscription per
/// plan; cancelling stops future charges but doesn't refund the current cycle already
/// paid for. bank-mfe/Android already have this; this is the first iOS client.
struct BillingPlanRow: View {
    let plan: MerchantBillingPlanDto
    let subscription: MerchantBillingSubscriptionDto?
    let onChanged: () -> Void

    @State private var busy = false
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(plan.name).font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    Text("\(formatAmount(Int(plan.amount))) RWF every \(plan.intervalDays) days").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    if let description = plan.description, !description.isEmpty {
                        Text(description).font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                    }
                }
                Spacer()
                if let subscription {
                    Button(action: { Task { await cancel(subscription.id) } }) {
                        Text(busy ? "…" : "Cancel").bold().font(.caption)
                            .padding(.horizontal, 12).padding(.vertical, 8)
                            .background(Color(.tertiarySystemBackground)).cornerRadius(8)
                    }
                    .disabled(busy)
                } else {
                    Button(action: { Task { await subscribe() } }) {
                        Text(busy ? "…" : "Subscribe").bold().font(.caption).foregroundColor(.white)
                            .padding(.horizontal, 12).padding(.vertical, 8)
                            .background(IDS.Colors.brand).cornerRadius(8)
                    }
                    .disabled(busy)
                }
            }
            if let subscription {
                Text(subscription.status == "ACTIVE" ? "Next charge \(String(subscription.nextChargeAt.prefix(10)))" : "Cancelled")
                    .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
            }
            if let error {
                Text(error).font(.caption2).foregroundColor(.red)
            }
        }
        .padding(12).background(Color(.secondarySystemBackground)).cornerRadius(10)
    }

    private func subscribe() async {
        busy = true
        error = nil
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.subscribeToBillingPlan(plan.id)
            onChanged()
        } catch {
            self.error = "Could not subscribe to this plan."
        }
    }

    private func cancel(_ subscriptionId: String) async {
        busy = true
        error = nil
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.cancelBillingSubscription(subscriptionId)
            onChanged()
        } catch {
            self.error = "Could not cancel this subscription."
        }
    }
}

/// Real product-detail screen (2026-07-21), mirroring Android's
/// ProductDetailScreen in features/shop/impl/ShopScreen.kt: full-size image,
/// name, price row, rating badge, description, quantity stepper, and an
/// add/update-cart action -- reached by tapping a product card in
/// MerchantDetailView's grid (see that grid's own doc comment).
// Real Coupang 정기배송 (subscribe & save) -- see NetworkClient's ProductSubscriptionDto
// doc comment. A minimal delivery-address prompt via .alert rather than a full address
// form, matching bank-mfe's own compact-card scope (fixed qty=1, every 30d).
// Moved here from the real-booking half of this file (2026-08-25) when that half moved
// to Features/Maps/Sources/MapsBooking.swift (see that file's own doc comment) --
// recurring *product* delivery is real online-shopping, unlike a real-time physical
// appointment, so this half stays in Shop.
struct SubscribeAndSaveButton: View {
    let merchantId: String
    let productId: String

    @State private var showAlert = false
    @State private var address = ""
    @State private var busy = false
    @State private var done = false
    @State private var error: String?

    var body: some View {
        if done {
            Text("✓ Subscribed -- delivered every 30 days").font(.caption).foregroundColor(IDS.Colors.brand)
        } else {
            VStack(alignment: .leading, spacing: 4) {
                Button(action: { showAlert = true }) {
                    Text("Subscribe & save (every 30 days)").font(.caption).bold().foregroundColor(IDS.Colors.brand)
                }
                if let error { Text(error).font(.caption2).foregroundColor(.red) }
            }
            .alert("Subscribe & save", isPresented: $showAlert) {
                TextField("Delivery address", text: $address)
                Button("Subscribe") { Task { await subscribe() } }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("Delivered every 30 days. Cancel anytime.")
            }
        }
    }

    private func subscribe() async {
        guard !address.trimmingCharacters(in: .whitespaces).isEmpty else {
            error = "Enter a delivery address."
            return
        }
        busy = true
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.subscribeToProduct(merchantId: merchantId, productId: productId, quantity: 1, intervalDays: 30, deliveryAddress: address.trimmingCharacters(in: .whitespaces))
            done = true
        } catch {
            self.error = "Could not set up this subscription."
        }
    }
}
