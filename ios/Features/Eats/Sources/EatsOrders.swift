import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper -- a real
// order-history log, kept the per-row Divider convention
// (docs/DESIGN_REFERENCES.md §274).
struct EatsOrderRow<Action: View>: View {
    let order: EatsOrderDto
    var restaurant: ShoppingMerchantDto? = nil
    @ViewBuilder let action: () -> Action

    @State private var showRoute = false
    @State private var showLiveTracking = false

    private var canShowRoute: Bool {
        restaurant?.latitude != nil && restaurant?.longitude != nil &&
            order.deliveryLatitude != nil && order.deliveryLongitude != nil
    }

    // Real live rider-location tracking (item 183) -- only while a rider is actually en
    // route, same gating as bank-mfe's own canShowLiveTracking / Android's item 182.
    private var canShowLiveTracking: Bool {
        canShowRoute && (order.status == "RIDER_ASSIGNED" || order.status == "PICKED_UP")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(eatsStatusLabel[order.status] ?? order.status).font(.subheadline).bold().foregroundColor(IDS.Colors.brand)
                    Text(order.deliveryAddress).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }
                Spacer()
                Text("\(formatAmount(Int(order.totalAmount))) RWF").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            }
            if let notes = order.deliveryNotes, !notes.isEmpty {
                Text("Note: \(notes)")
                    .font(.caption)
                    .foregroundColor(IDS.Colors.textPrimary)
                    .padding(.horizontal, 10).padding(.vertical, 8)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(IDS.Colors.chipBackground)
                    .cornerRadius(8)
            }
            if canShowLiveTracking {
                Button(action: { showLiveTracking.toggle(); showRoute = false }) {
                    Text(showLiveTracking ? "Hide live tracking" : "🛵 Track your rider live")
                        .font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                        .padding(.horizontal, 16).padding(.vertical, 10)
                        .background(IDS.Colors.chipBackground).cornerRadius(12)
                }
            }
            if showLiveTracking, let restaurant, let fromLat = restaurant.latitude, let fromLng = restaurant.longitude,
               let toLat = order.deliveryLatitude, let toLng = order.deliveryLongitude {
                LiveRiderMiniMap(orderId: order.id, fromLat: fromLat, fromLng: fromLng, toLat: toLat, toLng: toLng, fromLabel: restaurant.businessName, toLabel: "Delivery address")
            }
            // Real "view delivery route" (2026-07-19, item 8 on the Maps "100%" roadmap)
            // -- reuses itunda's own self-hosted OSRM directions via RouteMiniMap.
            if canShowRoute, !showLiveTracking {
                Button(action: { showRoute.toggle() }) {
                    Text(showRoute ? "Hide route" : "🚗 View real delivery route")
                        .font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                        .padding(.horizontal, 16).padding(.vertical, 10)
                        .background(IDS.Colors.chipBackground).cornerRadius(12)
                }
            }
            if showRoute, !showLiveTracking, let restaurant, let fromLat = restaurant.latitude, let fromLng = restaurant.longitude,
               let toLat = order.deliveryLatitude, let toLng = order.deliveryLongitude {
                RouteMiniMap(fromLat: fromLat, fromLng: fromLng, toLat: toLat, toLng: toLng, fromLabel: restaurant.businessName, toLabel: "Delivery address")
            }
            action()
        }
        .padding(.vertical, 10)
        Divider().overlay(IDS.Colors.divider)
    }
}

extension EatsOrderRow where Action == EmptyView {
    init(order: EatsOrderDto, restaurant: ShoppingMerchantDto? = nil) {
        self.order = order
        self.restaurant = restaurant
        self.action = { EmptyView() }
    }
}

struct MyEatsOrdersView: View {
    let onReorder: (EatsOrderDto) -> Void
    let reorderingId: String?
    let restaurants: [ShoppingMerchantDto]?

    @State private var orders: [EatsOrderDto]?
    @State private var error: String?
    @State private var cancellingId: String?
    // Real optimistic-hide for TipRiderPrompt -- same pattern bank-mfe's own
    // tippedOrderIds establishes.
    @State private var tippedOrderIds: Set<String> = []
    // Real pagination fix (2026-09-11, ported from bank-mfe's own fix and
    // Android's port -- see project_itunda_pagination_discard_sweep memory):
    // page 0 is polled every 4s for real-time order-status accuracy, so it
    // must always stay a live, page-0-only fetch. olderOrders is a separate
    // accumulator populated only by loadMoreOrders, never touched by the poll.
    @State private var olderOrders: [EatsOrderDto] = []
    @State private var ordersPage = 0
    @State private var ordersHasMore = false
    @State private var loadingMoreOrders = false

    private var allOrders: [EatsOrderDto] { (orders ?? []) + olderOrders }

    var body: some View {
        Group {
            // Real fix (2026-08-24, flat-design sweep): dropped this error-state Card --
            // the whole screen's content at this point (docs/UI_UX_GUIDELINES.md §10).
            if let error {
                VStack(alignment: .leading, spacing: 10) {
                    Text(error).foregroundColor(.red).font(.subheadline)
                    Button("Retry") { Task { await load() } }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(20)
            } else if orders == nil {
                SkeletonBlock(height: 120)
            } else if allOrders.isEmpty {
                EmptyStateView("No orders yet — order from a nearby restaurant and it'll show up here.")
            } else {
                VStack(spacing: 10) {
                    ForEach(allOrders) { order in
                        EatsOrderRow(order: order, restaurant: restaurants?.first(where: { $0.merchantId == order.restaurantId })) {
                            if order.status == "PLACED" {
                                Button(action: { Task { await cancel(order.id) } }) {
                                    Text(cancellingId == order.id ? "Cancelling…" : "Cancel order")
                                        .font(.subheadline).bold().foregroundColor(.white)
                                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                                        .background(Color.red).cornerRadius(12)
                                }
                                .disabled(cancellingId == order.id)
                            } else if order.status == "DELIVERED" {
                                VStack(alignment: .leading, spacing: 8) {
                                    ReviewOrderCard(order: order)
                                    if order.riderId != nil, order.tipAmount == nil, !tippedOrderIds.contains(order.id) {
                                        TipRiderPrompt(orderId: order.id, onTipped: { tippedOrderIds.insert(order.id) })
                                    }
                                    ReorderButton(reordering: reorderingId == order.id, onClick: { onReorder(order) })
                                }
                            } else if order.status == "CANCELLED" {
                                ReorderButton(reordering: reorderingId == order.id, onClick: { onReorder(order) })
                            }
                        }
                    }
                    if ordersHasMore {
                        Button(loadingMoreOrders ? "Loading…" : "Load more") {
                            Task { await loadMoreOrders() }
                        }
                        .disabled(loadingMoreOrders)
                    }
                }
            }
        }
        .task {
            // Real poll for order-tracking status, same 4s cadence as Talk's own poll.
            while !Task.isCancelled {
                await load()
                try? await Task.sleep(nanoseconds: 4_000_000_000)
            }
        }
    }

    private func load() async {
        do {
            let res = try await NetworkClient.shared.getMyEatsOrders(page: 0)
            orders = res.orders
            ordersHasMore = res.page + 1 < res.totalPages
            error = nil
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func loadMoreOrders() async {
        let nextPage = ordersPage + 1
        loadingMoreOrders = true
        defer { loadingMoreOrders = false }
        guard let res = try? await NetworkClient.shared.getMyEatsOrders(page: nextPage) else { return }
        olderOrders += res.orders
        ordersPage = nextPage
        ordersHasMore = res.page + 1 < res.totalPages
    }

    private func cancel(_ orderId: String) async {
        cancellingId = orderId
        error = nil
        defer { cancellingId = nil }
        do {
            _ = try await NetworkClient.shared.cancelEatsOrder(orderId)
            await load()
        } catch let NetworkError.httpErrorWithMessage(statusCode, message) {
            error = message ?? errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

// Real 배민오더-style table/QR in-store ordering (item 163) -- see
// DineInOrderConfirmationView's own doc comment. Closes the "My dine-in orders" gap
// named as a real follow-up in item 162: a customer who places a table order could
// see the confirmation but never look it up again. Deliberately simpler than
// MyEatsOrdersView -- no reorder/review (a real, honest, still-open v1 scope, matching
// bank-mfe's own item 155 scoping), just cancel while still PLACED.
struct MyDineInOrdersView: View {
    @State private var orders: [DineInOrderDto]?
    @State private var error: String?
    @State private var cancellingId: String?

    var body: some View {
        Group {
            // Real fix (2026-08-24, flat-design sweep): dropped this error-state Card.
            if let error {
                VStack(alignment: .leading, spacing: 10) {
                    Text(error).foregroundColor(.red).font(.subheadline)
                    Button("Retry") { Task { await load() } }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(20)
            } else if orders == nil {
                SkeletonBlock(height: 80)
            } else if orders!.isEmpty {
                EmptyView()
            } else {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Table orders").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                    // Real fix (2026-08-24, flat-design sweep): dropped the per-row Card --
                    // a real order-history log, kept the per-row Divider convention.
                    VStack(spacing: 0) {
                        ForEach(orders!) { order in
                            VStack(alignment: .leading, spacing: 6) {
                                HStack {
                                    Text(dineInStatusLabel[order.status] ?? order.status).font(.subheadline).bold().foregroundColor(IDS.Colors.brand)
                                    Spacer()
                                    Text("\(formatAmount(Int(order.totalAmount))) RWF").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                                }
                                Text("Table \(order.tableNumber)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                if order.status == "PLACED" {
                                    Button(action: { Task { await cancel(order.id) } }) {
                                        Text(cancellingId == order.id ? "Cancelling…" : "Cancel order")
                                            .font(.subheadline).bold().foregroundColor(.white)
                                            .frame(maxWidth: .infinity).padding(.vertical, 10)
                                            .background(Color.red).cornerRadius(12)
                                    }
                                    .disabled(cancellingId == order.id)
                                }
                            }
                            .padding(.vertical, 10)
                            Divider().overlay(IDS.Colors.divider)
                        }
                    }
                }
            }
        }
        .task { await load() }
    }

    private func load() async {
        do {
            orders = try await NetworkClient.shared.getMyDineInOrders().orders
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
            _ = try await NetworkClient.shared.cancelDineInOrder(id: orderId)
            await load()
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

struct ReorderButton: View {
    let reordering: Bool
    let onClick: () -> Void
    // Real Coupang/Amazon-style "Buy it again" (2026-08-23) -- Commerce/Shop's own
    // ShopMerchantOrders.swift reuses this exact button directly (no Feature-module
    // isolation boundary on iOS between these, unlike Android's Konsist-enforced
    // split, which needed a real duplicate there) with its own copy instead of
    // "Reorder"/"Reordering…", defaulting to Eats' original text so every existing
    // call site here stays unchanged.
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

// Real bookmarked/favorited restaurants (2026-07-19) -- self-contained, mirroring
// MyEatsOrdersView's own load/local-state pattern; onChanged resyncs OrderFoodContent's
// favoriteIds set so the Browse tab's hearts stay correct after an unfavorite here.
struct FavoriteRestaurantsView: View {
    let onOpen: (FavoriteRestaurantDto) -> Void
    let onChanged: () -> Void

    @State private var favorites: [FavoriteRestaurantDto]?
    @State private var error: String?
    @State private var removingId: String?

    var body: some View {
        Group {
            // Real fix (2026-08-24, flat-design sweep): dropped this error-state Card and
            // the per-row Card below -- a favorites list, matching GroupAccountScreen's/
            // FamilyLinkScreen's identical entity-list conversion, no divider
            // (docs/UI_UX_GUIDELINES.md §10).
            if let error {
                VStack(alignment: .leading, spacing: 10) {
                    Text(error).foregroundColor(.red).font(.subheadline)
                    Button("Retry") { Task { await load() } }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(20)
            } else if favorites == nil {
                SkeletonBlock(height: 120)
            } else if favorites!.isEmpty {
                EmptyStateView("No favorite restaurants yet. Tap the heart on a restaurant to save it here.")
            } else {
                ForEach(favorites!) { favorite in
                    HStack(spacing: 14) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 14).fill(IDS.Colors.chipBackground)
                            Image(systemName: "fork.knife").foregroundColor(IDS.Colors.brand)
                        }
                        .frame(width: 44, height: 44)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(favorite.businessName).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                            Text(favorite.category.map { "\($0) · Real menu, real delivery" } ?? "Real menu, real delivery").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                        Spacer()
                        Button(action: { Task { await remove(favorite.restaurantId) } }) {
                            HeartFilled(size: 18)
                        }.accessibilityLabel("Remove from favorites")
                        .buttonStyle(.plain)
                        .disabled(removingId == favorite.restaurantId)
                    }
                    .padding(.vertical, 10)
                    .contentShape(Rectangle())
                    .onTapGesture { onOpen(favorite) }
                }
            }
        }
        .task { if favorites == nil { await load() } }
    }

    private func load() async {
        do {
            favorites = try await NetworkClient.shared.getMyFavoriteRestaurants().favorites
            error = nil
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func remove(_ restaurantId: String) async {
        removingId = restaurantId
        do {
            _ = try await NetworkClient.shared.removeFavoriteRestaurant(restaurantId)
            favorites?.removeAll { $0.restaurantId == restaurantId }
            onChanged()
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
        removingId = nil
    }
}

// ============================== DELIVER (Rider) ==============================

