import SwiftUI
import UIKit
import CoreDesignSystem
import CoreNetwork
import CoreLocation

// ReorderButton was Eats' own EatsOrders.swift (App/Sources-only until 2026-09-06,
// when it moved into FeatureEats) -- that file's own doc comment already named this
// exact real risk ("no Feature-module isolation boundary on iOS between these, unlike
// Android's Konsist-enforced split, which needed a real duplicate there"). Now that
// FeatureEats is a real Feature module, App/Sources can no longer reach it directly,
// so this is that same real duplicate Android already has.
private struct ReorderButton: View {
    let reordering: Bool
    let onClick: () -> Void
    var label = "Reorder"
    var reorderingLabel = "Reordering…"

    var body: some View {
        Button(action: onClick) {
            Text(reordering ? reorderingLabel : label)
                .font(.subheadline).bold().foregroundColor(.white)
                .padding(.horizontal, 16).padding(.vertical, 10)
                .background(IDS.Colors.brand).cornerRadius(12)
        }
        .disabled(reordering)
    }
}

// Real merchant-side Commerce order fulfillment queue (item 234) -- found via a
// sibling-consistency audit against bank-mfe's own MerchantOrdersView, which has had
// this since before this session, and Android's own port (this same series,
// 2026-08-05). iOS port, straight mirror of both.
let commerceMerchantStatusChain = ["PLACED", "PACKED", "SHIPPED", "DELIVERED"]

struct MerchantOrdersView: View {
    @State private var orders: [OrderDto]?
    @State private var error: String?
    @State private var busyOrderId: String?

    var body: some View {
        Group {
            if let error {
                VStack(alignment: .leading, spacing: 10) {
                    Text(error).foregroundColor(.red).font(.subheadline)
                    Button("Retry") { Task { await load() } }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper --
                // a lone error state.
                .padding(20)
            } else if let orders, !orders.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Orders for your store").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                    ForEach(orders) { order in
                        let next = nextStatus(order.status)
                        CommerceOrderRow(order: order) {
                            if let next {
                                Button(action: { Task { await advance(order, to: next) } }) {
                                    Text(busyOrderId == order.id ? "Updating…" : "Mark \((commerceStatusLabel[next] ?? next).lowercased())")
                                        .font(.subheadline).bold().foregroundColor(.white)
                                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                                        .background(IDS.Colors.brand).cornerRadius(12)
                                }
                                .disabled(busyOrderId == order.id)
                            }
                        }
                    }
                }
            }
        }
        .task {
            while !Task.isCancelled {
                await load()
                try? await Task.sleep(nanoseconds: 4_000_000_000)
            }
        }
    }

    private func nextStatus(_ current: String) -> String? {
        guard let idx = commerceMerchantStatusChain.firstIndex(of: current), idx + 1 < commerceMerchantStatusChain.count else { return nil }
        return commerceMerchantStatusChain[idx + 1]
    }

    private func load() async {
        do {
            orders = try await NetworkClient.shared.getMerchantOrders().orders
            error = nil
        } catch NetworkError.httpError(let statusCode) where statusCode == 404 {
            // A real, expected case for any account that hasn't registered as a
            // merchant -- stays silent, matching bank-mfe/Android's own MERCHANT_NOT_FOUND
            // handling.
            orders = []
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func advance(_ order: OrderDto, to next: String) async {
        busyOrderId = order.id
        error = nil
        defer { busyOrderId = nil }
        do {
            _ = try await NetworkClient.shared.updateOrderStatus(order.id, status: next)
            await load()
        } catch {
            self.error = "Couldn't update this order."
        }
    }
}

struct MerchantReturnQueueView: View {
    @State private var requests: [OrderReturnRequestDto]?
    @State private var error: String?
    @State private var busyId: String?

    var body: some View {
        Group {
            if let error {
                Text(error).foregroundColor(.red).font(.footnote)
            } else if let requests {
                let open = requests.filter { $0.status == "REQUESTED" }
                if !open.isEmpty {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Return & exchange requests").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                        // Real fix (2026-08-24, flat-design sweep): dropped the per-row Card
                        // -- history log of return requests, kept the per-row Divider
                        // convention (docs/DESIGN_REFERENCES.md §274).
                        ForEach(open) { r in
                            VStack(alignment: .leading, spacing: 6) {
                                HStack {
                                    Text(r.type == "RETURN" ? "Return requested" : "Exchange requested").font(.subheadline).bold()
                                    Spacer()
                                    Text(r.reasonCode.replacingOccurrences(of: "_", with: " ").lowercased()).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                }
                                if let note = r.reasonNote { Text(note).font(.subheadline).foregroundColor(IDS.Colors.textSecondary) }
                                HStack(spacing: 8) {
                                    Button(action: { Task { await decide(r.id, approve: true) } }) {
                                        Text(busyId == r.id ? "…" : "Approve").font(.subheadline).bold().foregroundColor(.white)
                                            .frame(maxWidth: .infinity).padding(.vertical, 10)
                                            .background(IDS.Colors.brand).cornerRadius(10)
                                    }
                                    .disabled(busyId == r.id)
                                    Button(action: { Task { await decide(r.id, approve: false) } }) {
                                        Text("Reject").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                                            .frame(maxWidth: .infinity).padding(.vertical, 10)
                                            .background(IDS.Colors.card).cornerRadius(10).idsCardBorder(cornerRadius: 10)
                                    }
                                    .disabled(busyId == r.id)
                                }
                            }
                            .padding(.vertical, 10)
                            Divider().overlay(IDS.Colors.divider)
                        }
                    }
                }
            }
        }
        .task {
            while !Task.isCancelled {
                await load()
                try? await Task.sleep(nanoseconds: 8_000_000_000)
            }
        }
    }

    private func load() async {
        do {
            requests = try await NetworkClient.shared.getMerchantReturnQueue().returnRequests
            error = nil
        } catch NetworkError.httpError(let statusCode) where statusCode == 404 {
            requests = []
        } catch {
            self.error = "Could not load return requests."
        }
    }

    private func decide(_ id: String, approve: Bool) async {
        busyId = id
        error = nil
        defer { busyId = nil }
        do {
            _ = try await NetworkClient.shared.decideOrderReturn(id, approve: approve)
            await load()
        } catch {
            self.error = "Could not decide this request."
        }
    }
}

struct MyCommerceOrdersView: View {
    let onReorder: (OrderDto) -> Void
    let reorderingId: String?

    @State private var orders: [OrderDto]?
    @State private var error: String?
    @State private var cancellingId: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            MyReturnRequestsView()
            Group {
                if let error {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(error).foregroundColor(.red).font(.subheadline)
                        Button("Retry") { Task { await load() } }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper --
                    // a lone error state.
                    .padding(20)
                } else if orders == nil {
                    SkeletonBlock(height: 120)
                } else if orders!.isEmpty {
                    EmptyStateView("No orders yet — browse a merchant's shop and your first order will show up here.")
                } else {
                    VStack(spacing: 10) {
                        ForEach(orders!) { order in
                            CommerceOrderRow(order: order) {
                                if order.status == "PLACED" {
                                    Button(action: { Task { await cancel(order.id) } }) {
                                        Text(cancellingId == order.id ? "Cancelling…" : "Cancel order")
                                            .font(.subheadline).bold().foregroundColor(.white)
                                            .frame(maxWidth: .infinity).padding(.vertical, 10)
                                            .background(Color.red).cornerRadius(12)
                                    }
                                    .disabled(cancellingId == order.id)
                                } else if order.status == "SHIPPED" {
                                    LiveTrackingToggle(orderId: order.id)
                                } else if order.status == "DELIVERED" {
                                    VStack(alignment: .leading, spacing: 8) {
                                        OrderItemReviews(order: order)
                                        ReturnExchangeAction(orderId: order.id)
                                        ReorderButton(
                                            reordering: reorderingId == order.id,
                                            onClick: { onReorder(order) },
                                            label: "Buy again", reorderingLabel: "Reordering…"
                                        )
                                    }
                                } else if order.status == "CANCELLED" {
                                    ReorderButton(
                                        reordering: reorderingId == order.id,
                                        onClick: { onReorder(order) },
                                        label: "Buy again", reorderingLabel: "Reordering…"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        .task {
            // Real poll for order-tracking status, same 4s cadence as Eats' own poll.
            while !Task.isCancelled {
                await load()
                try? await Task.sleep(nanoseconds: 4_000_000_000)
            }
        }
    }

    private func load() async {
        do {
            let res = try await NetworkClient.shared.getMyOrders()
            orders = res.orders
            error = nil
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func cancel(_ orderId: String) async {
        cancellingId = orderId
        error = nil
        defer { cancellingId = nil }
        do {
            _ = try await NetworkClient.shared.cancelOrder(orderId)
            await load()
        } catch let NetworkError.httpError(statusCode) {
            error = errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

/// Real live rider-location tracking for Commerce orders (item 230) -- see
/// SimpleLiveRiderMiniMap's own doc comment. bank-mfe/Android shipped this first
/// (2026-08-05); this is the iOS port.
struct LiveTrackingToggle: View {
    let orderId: String
    @State private var tracking = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Button(action: { tracking.toggle() }) {
                Text(tracking ? "Hide live tracking" : "🛵 Track your rider live")
                    .font(.subheadline).bold().foregroundColor(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 10)
                    .background(IDS.Colors.brand).cornerRadius(12)
            }
            if tracking {
                SimpleLiveRiderMiniMap(orderId: orderId)
            }
        }
    }
}

