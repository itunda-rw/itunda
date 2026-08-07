import SwiftUI
import CoreDesignSystem
import CoreNetwork

/// Real Coupang Eats-style food ordering + a real rider role, folded into ShopScreen's
/// Shop/Eats toggle (2026-07-18) -- see rw.itunda.eats.EatsOrderService's own doc comment
/// for the full backend account, including the honest "flat delivery fee, no geo data"
/// scope. Restaurant/menu browsing reuses ShoppingMerchantDto/MerchantProductDto and the
/// existing getShoppingMerchants()/getMerchantProducts() calls -- zero new browse
/// endpoint. Mirrors bank-mfe's EatsView and Android's EatsContent 1:1.

private let eatsStatusLabel: [String: String] = [
    "PLACED": "Placed",
    "ACCEPTED": "Accepted by restaurant",
    "PREPARING": "Preparing",
    "READY_FOR_PICKUP": "Ready for pickup",
    "RIDER_ASSIGNED": "Rider on the way to restaurant",
    "PICKED_UP": "Picked up — on the way",
    "DELIVERED": "Delivered",
    "CANCELLED": "Cancelled — refunded",
]

private let riderStatusChain = ["RIDER_ASSIGNED", "PICKED_UP", "DELIVERED"]

private let dineInStatusLabel: [String: String] = [
    "PLACED": "Placed",
    "ACCEPTED": "Accepted by restaurant",
    "PREPARING": "Preparing",
    "SERVED": "Served",
    "CANCELLED": "Cancelled — refunded",
]

private func nextRiderStatus(_ current: String) -> String? {
    guard let idx = riderStatusChain.firstIndex(of: current), idx + 1 < riderStatusChain.count else { return nil }
    return riderStatusChain[idx + 1]
}

// Real per-configuration cart line (2026-07-21) -- ports bank-mfe's own EatsCartLine
// (BankDashboard.tsx) 1:1. `choiceIds` is empty for any item with no option groups --
// the pre-existing, unaffected case. Two lines for the same productId with DIFFERENT
// choiceIds are genuinely distinct cart entries (e.g. a Regular and a Large of the same
// burger, side by side) -- closes docs/DESIGN_REFERENCES.md's Eats recommendation #6.
struct EatsCartLine {
    let productId: String
    var quantity: Int
    let choiceIds: [String]
}

private func eatsCartKey(_ productId: String, _ choiceIds: [String]) -> String {
    choiceIds.isEmpty ? productId : "\(productId)::\(choiceIds.sorted().joined(separator: ","))"
}

// Real, human-readable summary of a resolved cart line's selected options -- mirrors
// the backend's own EatsOrderService.buildSelectedOptionsJson, but purely for display;
// pricing always comes from the real menu item + real choice deltas, never this string.
private func eatsOptionsSummary(_ item: MerchantProductDto, _ choiceIds: [String]) -> String {
    guard !choiceIds.isEmpty else { return "" }
    let names = (item.optionGroups ?? []).flatMap { $0.choices }.filter { choiceIds.contains($0.id) }.map { $0.name }
    return names.isEmpty ? "" : " (\(names.joined(separator: ", ")))"
}

private func eatsLineUnitPrice(_ item: MerchantProductDto, _ choiceIds: [String]) -> Double {
    let delta = (item.optionGroups ?? []).flatMap { $0.choices }.filter { choiceIds.contains($0.id) }.reduce(0.0) { $0 + $1.priceDelta }
    return item.price + delta
}

private enum EatsMode { case order, deliver }

struct EatsContent: View {
    @State private var mode: EatsMode = .order

    var body: some View {
        VStack(spacing: 0) {
            Picker("", selection: $mode) {
                Text("Order food").tag(EatsMode.order)
                Text("Deliver").tag(EatsMode.deliver)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.top, 8)

            switch mode {
            case .order: OrderFoodContent()
            case .deliver: DeliverContent()
            }
        }
    }
}

private enum OrderFoodView { case browse, favorites, orders }

// Real Coupang 와우 (Wow)-style unconditional delivery-fee waiver (item 211) -- see
// PlatformMembershipDto's own doc comment. bank-mfe/Android already have this; this is
// the iOS client. Deliberately a separate card from EatsMembershipCard below, not a
// replacement: waives the fee at every restaurant, no merchant opt-in required.
private struct PlatformMembershipCard: View {
    @State private var membership: PlatformMembershipDto?
    @State private var loaded = false
    @State private var busy = false
    @State private var error: String?

    private func load() async {
        do {
            membership = try await NetworkClient.shared.getMyPlatformMembership().membership
        } catch {
            // Real, non-critical -- the rest of Eats still works without this card.
        }
        loaded = true
    }

    private var isActive: Bool {
        guard let membership, let activeUntil = ISO8601DateFormatter().date(from: membership.activeUntil) ?? isoDateFormatterFractional.date(from: membership.activeUntil) else { return false }
        return activeUntil > Date()
    }

    var body: some View {
        Group {
            if loaded {
                VStack(alignment: .leading, spacing: 6) {
                    Text("itunda Plus").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    if let error {
                        Text(error).font(.caption).foregroundColor(.red)
                    }
                    if isActive, let membership {
                        let activeUntil = ISO8601DateFormatter().date(from: membership.activeUntil) ?? isoDateFormatterFractional.date(from: membership.activeUntil)
                        Text("Free delivery active until \(activeUntil.map { $0.formatted(date: .abbreviated, time: .omitted) } ?? "") at every restaurant, no participation required.")
                            .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    } else {
                        Text("Free delivery at every restaurant -- no minimum order, no restaurant opt-in required.")
                            .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        HStack(spacing: 8) {
                            ForEach(platformMembershipTiers, id: \.days) { tier in
                                Button(action: { Task { await subscribe(days: tier.days) } }) {
                                    Text(busy ? "…" : "\(tier.days) days -- \(tier.priceRwf) RWF")
                                        .font(.caption).bold().foregroundColor(.white)
                                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                                        .background(IDS.Colors.brand).cornerRadius(10)
                                }
                                .disabled(busy)
                            }
                        }
                    }
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(IDS.Colors.card)
                .cornerRadius(IDS.Layout.cardCornerRadius)
            }
        }
        .task { await load() }
    }

    private func subscribe(days: Int) async {
        busy = true
        error = nil
        do {
            _ = try await NetworkClient.shared.subscribePlatformMembership(days: days)
            await load()
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
        busy = false
    }
}

// Real Baemin Club (배민클럽)-style free-delivery membership (item 209) -- see
// EatsMembershipDto's own doc comment. bank-mfe/Android already have this; this is the
// iOS client.
private struct EatsMembershipCard: View {
    @State private var membership: EatsMembershipDto?
    @State private var loaded = false
    @State private var busy = false
    @State private var error: String?

    private func load() async {
        do {
            membership = try await NetworkClient.shared.getMyEatsMembership().membership
        } catch {
            // Real, non-critical -- the rest of Eats still works without this card.
        }
        loaded = true
    }

    private var isActive: Bool {
        guard let membership, let activeUntil = ISO8601DateFormatter().date(from: membership.activeUntil) ?? isoDateFormatterFractional.date(from: membership.activeUntil) else { return false }
        return activeUntil > Date()
    }

    var body: some View {
        Group {
            if loaded {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Eats Club").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    if let error {
                        Text(error).font(.caption).foregroundColor(.red)
                    }
                    if isActive, let membership {
                        let activeUntil = ISO8601DateFormatter().date(from: membership.activeUntil) ?? isoDateFormatterFractional.date(from: membership.activeUntil)
                        Text("Free delivery active until \(activeUntil.map { $0.formatted(date: .abbreviated, time: .omitted) } ?? "") at participating restaurants.")
                            .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    } else {
                        Text("Free delivery at participating restaurants -- no minimum order.")
                            .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        HStack(spacing: 8) {
                            ForEach(eatsMembershipTiers, id: \.days) { tier in
                                Button(action: { Task { await subscribe(days: tier.days) } }) {
                                    Text(busy ? "…" : "\(tier.days) days -- \(tier.priceRwf) RWF")
                                        .font(.caption).bold().foregroundColor(.white)
                                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                                        .background(IDS.Colors.brand).cornerRadius(10)
                                }
                                .disabled(busy)
                            }
                        }
                    }
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(IDS.Colors.card)
                .cornerRadius(IDS.Layout.cardCornerRadius)
            }
        }
        .task { await load() }
    }

    private func subscribe(days: Int) async {
        busy = true
        error = nil
        do {
            _ = try await NetworkClient.shared.subscribeEatsMembership(days: days)
            await load()
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
        busy = false
    }
}

private let isoDateFormatterFractional: ISO8601DateFormatter = {
    let f = ISO8601DateFormatter()
    f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    return f
}()

private struct OrderFoodContent: View {
    @State private var view: OrderFoodView = .browse
    @State private var restaurants: [ShoppingMerchantDto]?
    // Unfiltered, fetched once -- used to resolve a past order's restaurant for Reorder
    // even when that restaurant has been filtered out of the currently-browsed list.
    @State private var allRestaurants: [ShoppingMerchantDto]?
    @State private var categories: [String] = []
    @State private var selectedCategory: String?
    @State private var searchInput: String = ""
    @State private var filterTask: Task<Void, Never>?
    @State private var error: String?
    @State private var selectedRestaurant: ShoppingMerchantDto?
    @State private var menu: [MerchantProductDto]?
    @State private var cart: [String: EatsCartLine] = [:]
    @State private var showCheckout = false
    @State private var confirmedOrder: EatsOrderDto?
    // Real 배민오더-style table/QR in-store ordering (item 162) -- see
    // EatsCheckoutView's own doc comment.
    @State private var confirmedDineInOrder: DineInOrderDto?
    @State private var reorderingId: String?
    @State private var reorderError: String?
    // Real bookmarked/favorited restaurants (2026-07-19) -- a set of restaurant ids for
    // a fast star-toggle lookup on each browse card.
    @State private var favoriteIds: Set<String> = []
    @State private var favoritingId: String?

    var body: some View {
        Group {
            if let confirmedOrder {
                EatsOrderConfirmationView(order: confirmedOrder, onDone: {
                    self.confirmedOrder = nil
                    self.selectedRestaurant = nil
                    self.menu = nil
                    self.cart = [:]
                    self.showCheckout = false
                    self.view = .orders
                })
            } else if let confirmedDineInOrder {
                DineInOrderConfirmationView(order: confirmedDineInOrder, onDone: {
                    self.confirmedDineInOrder = nil
                    self.selectedRestaurant = nil
                    self.menu = nil
                    self.cart = [:]
                    self.showCheckout = false
                })
            } else if let restaurant = selectedRestaurant {
                if showCheckout {
                    EatsCheckoutView(
                        restaurant: restaurant,
                        cart: cart,
                        menu: menu ?? [],
                        onBack: { showCheckout = false },
                        onOrderPlaced: { confirmedOrder = $0 },
                        onDineInOrderPlaced: { confirmedDineInOrder = $0 }
                    )
                } else {
                    RestaurantMenuView(
                        restaurant: restaurant,
                        menu: menu,
                        cart: $cart,
                        onBack: { selectedRestaurant = nil },
                        onCheckout: { showCheckout = true }
                    )
                }
            } else {
                browseBody
            }
        }
        .task {
            if restaurants == nil { await loadRestaurants() }
            if allRestaurants == nil {
                do { allRestaurants = try await NetworkClient.shared.getShoppingMerchants().merchants } catch {}
            }
            if categories.isEmpty {
                do { categories = try await NetworkClient.shared.getMerchantCategories().categories } catch {}
            }
            await loadFavoriteIds()
        }
    }

    private func loadFavoriteIds() async {
        do {
            let favs = try await NetworkClient.shared.getMyFavoriteRestaurants().favorites
            favoriteIds = Set(favs.map { $0.restaurantId })
        } catch {
            // Real, non-critical -- only backs the star toggle.
        }
    }

    private func toggleFavorite(_ restaurantId: String) {
        favoritingId = restaurantId
        Task {
            do {
                if favoriteIds.contains(restaurantId) {
                    _ = try await NetworkClient.shared.removeFavoriteRestaurant(restaurantId)
                    favoriteIds.remove(restaurantId)
                } else {
                    _ = try await NetworkClient.shared.addFavoriteRestaurant(restaurantId)
                    favoriteIds.insert(restaurantId)
                }
            } catch {
                // Real, non-critical -- a failed toggle just leaves the star as-is.
            }
            favoritingId = nil
        }
    }

    // Real category/search filter (2026-07-19), debounced the same way
    // AddressAutocompleteField's own search-as-you-type already is.
    private func scheduleFilterReload() {
        filterTask?.cancel()
        filterTask = Task {
            try? await Task.sleep(nanoseconds: 300_000_000)
            guard !Task.isCancelled else { return }
            await loadRestaurants()
        }
    }

    private func selectCategory(_ category: String?) {
        selectedCategory = (category == selectedCategory) ? nil : category
        scheduleFilterReload()
    }

    private var browseBody: some View {
        ScrollView {
            VStack(spacing: IDS.Layout.cardGap) {
                PlatformMembershipCard()
                EatsMembershipCard()
                Picker("", selection: $view) {
                    Text("Restaurants").tag(OrderFoodView.browse)
                    Text("Favorites").tag(OrderFoodView.favorites)
                    Text("My orders").tag(OrderFoodView.orders)
                }
                .pickerStyle(.segmented)

                if view == .orders {
                    MyDineInOrdersView()
                    MyEatsOrdersView(onReorder: { order in Task { await handleReorder(order) } }, reorderingId: reorderingId, restaurants: allRestaurants)
                    if let reorderError {
                        Text(reorderError).foregroundColor(.red).font(.caption)
                    }
                } else if view == .favorites {
                    FavoriteRestaurantsView(
                        onOpen: { favorite in
                            let restaurant = allRestaurants?.first(where: { $0.merchantId == favorite.restaurantId })
                                ?? ShoppingMerchantDto(merchantId: favorite.restaurantId, businessName: favorite.businessName, category: favorite.category, cashbackRate: "1%")
                            Task { await openRestaurant(restaurant) }
                        },
                        onChanged: { Task { await loadFavoriteIds() } }
                    )
                } else {
                    SearchAndCategoryChips(
                        searchText: searchInput,
                        onSearchChange: { searchInput = $0; scheduleFilterReload() },
                        placeholder: "Search restaurants",
                        categories: categories,
                        selectedCategory: selectedCategory,
                        onSelectCategory: selectCategory
                    )

                    if let error {
                        VStack(alignment: .leading, spacing: 10) {
                            Text(error).foregroundColor(.red).font(.subheadline)
                            Button("Retry") { Task { await loadRestaurants() } }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(20)
                        .background(IDS.Colors.card)
                        .cornerRadius(IDS.Layout.cardCornerRadius)
                    } else if restaurants == nil {
                        SkeletonBlock(height: 120)
                    } else if restaurants!.isEmpty {
                        // Real copy-voice fix (item 244, round 5 of the empty-state pass,
                        // ported from the same-day Android fix): "registered yet" is
                        // honest about whose gap this is -- no restaurant has joined
                        // yet, not something the reader is missing a step on.
                        Text(selectedCategory != nil || !searchInput.trimmingCharacters(in: .whitespaces).isEmpty ? "No restaurants match your search — try a different category or search term." : "No restaurants registered yet — check back once restaurants in your area join itunda Eats.")
                            .foregroundColor(IDS.Colors.textSecondary)
                    } else {
                        ForEach(restaurants!) { restaurant in
                            HStack(spacing: 14) {
                                RestaurantPhotoThumb(imageUrl: restaurant.photoUrl)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(restaurant.businessName).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                                    // Real fix, 2026-07-21 (porting Android's own fix): every
                                    // restaurant card previously showed the exact same generic
                                    // "Real menu, real delivery" filler regardless of which
                                    // restaurant it was -- not real per-restaurant info a user
                                    // could actually scan and compare. ShoppingMerchantDto
                                    // already carries a real cashbackRate; show that instead.
                                    Text(restaurant.category.map { "\($0) · \(restaurant.cashbackRate) cashback" } ?? "\(restaurant.cashbackRate) cashback")
                                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                    // Real browse-card enrichment (2026-07-21) -- closes
                                    // docs/DESIGN_REFERENCES.md's Eats recommendations #1/#2:
                                    // rating previously sat one tap deeper inside
                                    // RestaurantMenuView only, and there was no distance/
                                    // delivery-time/min-order signal on the browse card at
                                    // all. Every clause conditionally rendered on real data
                                    // being present -- never a fabricated placeholder.
                                    if restaurant.rating != nil || restaurant.distanceKm != nil || restaurant.minOrderAmount != nil {
                                        let detailLine = [
                                            restaurant.distanceKm.map { String(format: "%.1f km", $0) },
                                            restaurant.deliveryTimeMinutes.map { "~\($0) min" },
                                            restaurant.minOrderAmount.map { "Min \(Int($0)) RWF" },
                                        ].compactMap { $0 }.joined(separator: " · ")
                                        HStack(spacing: 4) {
                                            if let rating = restaurant.rating {
                                                Image(systemName: "star.fill").font(.caption2).foregroundColor(.yellow)
                                                Text(String(format: "%.1f (%d)", rating, restaurant.reviewCount ?? 0))
                                                    .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                                if !detailLine.isEmpty {
                                                    Text("·").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                                }
                                            }
                                            if !detailLine.isEmpty {
                                                Text(detailLine).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                            }
                                        }
                                    }
                                }
                                Spacer()
                                Button(action: { toggleFavorite(restaurant.merchantId) }) {
                                    Image(systemName: favoriteIds.contains(restaurant.merchantId) ? "heart.fill" : "heart")
                                        .foregroundColor(favoriteIds.contains(restaurant.merchantId) ? .red : IDS.Colors.textSecondary)
                                }
                                .buttonStyle(.plain)
                                .disabled(favoritingId == restaurant.merchantId)
                            }
                            .padding(18)
                            .background(IDS.Colors.card)
                            .cornerRadius(IDS.Layout.cardCornerRadius)
                            .contentShape(Rectangle())
                            .onTapGesture { Task { await openRestaurant(restaurant) } }
                        }
                    }
                }
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.top, IDS.Layout.screenTop)
            .padding(.bottom, IDS.Layout.sectionSpacing)
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }

    private func loadRestaurants() async {
        do {
            let q = searchInput.trimmingCharacters(in: .whitespaces)
            let res = try await NetworkClient.shared.getShoppingMerchants(category: selectedCategory, q: q.isEmpty ? nil : q)
            restaurants = res.merchants
            error = nil
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func openRestaurant(_ restaurant: ShoppingMerchantDto) async {
        selectedRestaurant = restaurant
        cart = [:]
        menu = nil
        do {
            let res = try await NetworkClient.shared.getMerchantProducts(merchantId: restaurant.merchantId)
            menu = res.products
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    // Real "Reorder" button (2026-07-19): re-populate the cart from a past order's real
    // items, cross-referenced against the restaurant's current menu -- discontinued items
    // are silently dropped rather than added as phantom cart lines.
    private func handleReorder(_ order: EatsOrderDto) async {
        guard let restaurant = allRestaurants?.first(where: { $0.merchantId == order.restaurantId }) else {
            reorderError = "This restaurant is no longer available."
            return
        }
        reorderingId = order.id
        reorderError = nil
        defer { reorderingId = nil }
        do {
            async let orderDetailReq = NetworkClient.shared.getEatsOrder(order.id)
            async let menuReq = NetworkClient.shared.getMerchantProducts(merchantId: order.restaurantId)
            let (orderDetail, menuRes) = try await (orderDetailReq, menuReq)
            let activeProducts = Dictionary(uniqueKeysWithValues: menuRes.products.filter { $0.active }.map { ($0.id, $0) })
            // Real reorder-cart sanitization (2026-07-21) -- a reordered past order carries
            // no option selections. If the item now genuinely requires one, that bare line
            // can never check out -- drop it rather than let checkout silently 422, same
            // "discontinued item silently dropped" precedent already established below for
            // a menu item that's gone entirely.
            var newCart: [String: EatsCartLine] = [:]
            for item in orderDetail.items {
                guard let product = activeProducts[item.productId], (product.optionGroups?.isEmpty ?? true) else { continue }
                let key = eatsCartKey(item.productId, [])
                newCart[key] = EatsCartLine(productId: item.productId, quantity: (newCart[key]?.quantity ?? 0) + item.quantity, choiceIds: [])
            }
            if newCart.isEmpty {
                reorderError = "None of the items from that order are on the menu anymore."
                return
            }
            selectedRestaurant = restaurant
            menu = menuRes.products
            cart = newCart
            view = .browse
        } catch {
            reorderError = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

// Real post-delivery ratings & reviews (2026-07-18) -- itunda's own self-hosted rating
// system, ported from bank-mfe's own review UI (the template for this iOS version).
private struct StarRatingRow: View {
    let value: Int
    let onChange: (Int) -> Void

    var body: some View {
        HStack(spacing: 4) {
            ForEach(1...5, id: \.self) { n in
                Button(action: { onChange(n) }) {
                    Image(systemName: n <= value ? "star.fill" : "star")
                        .foregroundColor(n <= value ? .yellow : IDS.Colors.textTertiary)
                }
            }
        }
    }
}

// Real restaurant-photo thumbnail (2026-07-21) -- photoUrl is a merchant-supplied
// external URL (see backend Merchant.kt's own doc comment: no upload/storage layer
// exists in this backend, same "bring your own URL" convention ShopScreen's own
// ProductImageThumb already established for product images). AsyncImage (native
// SwiftUI, no third-party dependency) handles the nil/broken-URL case itself via its
// placeholder closure -- same fallback icon for "no photo set" and "photo failed to
// load," both real, valid states.
private struct RestaurantPhotoThumb: View {
    let imageUrl: String?
    var side: CGFloat = 44

    var body: some View {
        Group {
            if let imageUrl, let url = URL(string: imageUrl) {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().scaledToFill()
                    default:
                        placeholder
                    }
                }
            } else {
                placeholder
            }
        }
        .frame(width: side, height: side)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .background(IDS.Colors.chipBackground)
    }

    private var placeholder: some View {
        ZStack {
            IDS.Colors.chipBackground
            Image(systemName: "fork.knife").foregroundColor(IDS.Colors.brand)
        }
    }
}

// Real written-review list + owner-reply display (item 184/185/186) -- bank-mfe (item
// 184) and Android (item 185) already have this; this is the first iOS client. Mirrors
// ProductRatingBadge's own expand-on-tap pattern exactly (ShopScreen.swift, this app's
// Commerce equivalent).
private struct RestaurantRatingBadge: View {
    let restaurantId: String
    @State private var rating: EatsRatingResponse?
    @State private var open = false
    @State private var reviews: [EatsReviewDto]?

    var body: some View {
        Group {
            if let rating, rating.count > 0 {
                VStack(alignment: .leading, spacing: 4) {
                    Button(action: toggle) {
                        HStack(spacing: 4) {
                            Image(systemName: "star.fill").font(.caption).foregroundColor(.yellow)
                            Text(String(format: "%.1f (%d)", rating.average ?? 0.0, rating.count))
                                .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                    }
                    if open {
                        if let reviews {
                            if reviews.isEmpty {
                                EmptyStateView("No written reviews yet — be the first to share how it went.")
                            } else {
                                ForEach(reviews, id: \.id) { r in
                                    let stars = String(repeating: "★", count: r.restaurantRating) + String(repeating: "☆", count: 5 - r.restaurantRating)
                                    Text(r.restaurantComment.map { "\(stars) — \($0)" } ?? stars)
                                        .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                                    if let reply = r.ownerReply, !reply.isEmpty {
                                        Text("↳ Restaurant: \(reply)").font(.caption2).foregroundColor(IDS.Colors.textTertiary).padding(.leading, 12)
                                    }
                                }
                            }
                        } else {
                            Text("Loading reviews…").font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                        }
                    }
                }
            }
        }
        .task {
            do {
                rating = try await NetworkClient.shared.getRestaurantRating(restaurantId)
            } catch {
                // Real, non-critical -- a rating fetch failure shouldn't block browsing
                // the menu.
            }
        }
    }

    private func toggle() {
        open.toggle()
        guard open, reviews == nil else { return }
        Task {
            do {
                reviews = try await NetworkClient.shared.getRestaurantReviews(restaurantId).reviews
            } catch {
                reviews = []
            }
        }
    }
}

private struct ReviewOrderCard: View {
    let order: EatsOrderDto

    @State private var open = false
    @State private var done = false
    @State private var restaurantRating = 0
    @State private var restaurantComment = ""
    @State private var riderRating = 0
    @State private var riderComment = ""
    @State private var submitting = false
    @State private var error: String?

    var body: some View {
        if done {
            Text("Thanks for your review!").font(.caption).foregroundColor(IDS.Colors.textSecondary)
        } else if !open {
            Button(action: { open = true }) {
                Text("Rate this order").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    .background(IDS.Colors.chipBackground).cornerRadius(12)
            }
        } else {
            VStack(alignment: .leading, spacing: 10) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Restaurant").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    StarRatingRow(value: restaurantRating) { restaurantRating = $0 }
                    TextField("How was the food? (optional)", text: $restaurantComment)
                        .padding(10).background(IDS.Colors.chipBackground).cornerRadius(10)
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text("Rider").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    StarRatingRow(value: riderRating) { riderRating = $0 }
                    TextField("How was the delivery? (optional)", text: $riderComment)
                        .padding(10).background(IDS.Colors.chipBackground).cornerRadius(10)
                }
                if let error {
                    Text(error).font(.caption).foregroundColor(.red)
                }
                HStack(spacing: 10) {
                    Button(action: { open = false }) {
                        Text("Cancel").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(IDS.Colors.chipBackground).cornerRadius(12)
                    }
                    Button(action: { Task { await submit() } }) {
                        Text(submitting ? "Submitting…" : "Submit review").font(.subheadline).bold().foregroundColor(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(submitting ? IDS.Colors.textTertiary : IDS.Colors.brand).cornerRadius(12)
                    }
                    .disabled(submitting)
                }
            }
        }
    }

    private func submit() async {
        guard restaurantRating > 0, riderRating > 0 else {
            error = "Rate both the restaurant and the rider."
            return
        }
        submitting = true
        error = nil
        defer { submitting = false }
        do {
            _ = try await NetworkClient.shared.submitEatsReview(
                orderId: order.id,
                restaurantRating: restaurantRating,
                restaurantComment: restaurantComment.trimmingCharacters(in: .whitespaces).isEmpty ? nil : restaurantComment,
                riderRating: riderRating,
                riderComment: riderComment.trimmingCharacters(in: .whitespaces).isEmpty ? nil : riderComment
            )
            done = true
        } catch let NetworkError.httpError(statusCode) {
            // A 409 here is the real ORDER_ALREADY_REVIEWED case in practice -- this
            // card only ever renders for a real DELIVERED order, so the sibling "not
            // yet delivered" 409 can't actually occur through this UI path.
            if statusCode == 409 {
                done = true
            } else {
                error = TalkScreen.errorMessage(statusCode)
            }
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

private struct RestaurantMenuView: View {
    let restaurant: ShoppingMerchantDto
    let menu: [MerchantProductDto]?
    @Binding var cart: [String: EatsCartLine]
    let onBack: () -> Void
    let onCheckout: () -> Void

    // Real menu-options selection UI (2026-07-21, v1: required single-select only) --
    // ports bank-mfe's own MenuView 1:1. Only one item's option panel is expanded at a
    // time, matching this file's own established "inline-card-replaces-trigger"
    // convention (no modal-overlay pattern exists anywhere in this app).
    @State private var expandedProductId: String?
    @State private var pendingChoices: [String: String] = [:]

    private var cartCount: Int { cart.values.reduce(0) { $0 + $1.quantity } }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left").font(.system(size: 18, weight: .medium)).frame(width: 44, height: 44)
                }
                .accessibilityLabel("Back")
                Text(restaurant.businessName).font(IDS.Typography.title).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
            }
            .padding(.horizontal, 8)
            RestaurantRatingBadge(restaurantId: restaurant.merchantId)
                .padding(.horizontal, IDS.Layout.screenHorizontal)
                .padding(.top, 4)

            ScrollView {
                VStack(spacing: 10) {
                    if let menu {
                        if menu.isEmpty {
                            EmptyStateView("This restaurant hasn't added menu items yet — check back soon.")
                        }
                        ForEach(menu) { item in
                            menuItemCard(item)
                        }
                    } else {
                        ProgressView().padding(.top, 20)
                    }
                }
                .padding(.horizontal, IDS.Layout.screenHorizontal)
                .padding(.top, 12)
            }

            if cartCount > 0 {
                Button(action: onCheckout) {
                    HStack {
                        Image(systemName: "cart.fill")
                        Text("Checkout (\(cartCount) item\(cartCount == 1 ? "" : "s"))")
                    }
                    .font(IDS.Typography.bodyBold)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(IDS.Colors.brand)
                    .cornerRadius(16)
                }
                .padding(IDS.Layout.screenHorizontal)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }

    private func menuItemCard(_ item: MerchantProductDto) -> some View {
        let groups = item.optionGroups ?? []
        let hasOptions = !groups.isEmpty
        let simpleKey = eatsCartKey(item.id, [])
        let simpleQty = hasOptions ? 0 : (cart[simpleKey]?.quantity ?? 0)
        let isExpanded = expandedProductId == item.id
        let allGroupsChosen = groups.allSatisfy { pendingChoices[$0.id] != nil }

        return VStack(alignment: .leading, spacing: 0) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.name).font(IDS.Typography.bodyMedium).foregroundColor(IDS.Colors.textPrimary)
                    Text("\(Int(item.price)) RWF\(hasOptions ? " · options required" : "")").font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
                }
                Spacer()
                if hasOptions {
                    Button(action: { toggleExpand(item.id) }) {
                        Text(isExpanded ? "Close" : "Choose options")
                            .font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                            .padding(.horizontal, 12).padding(.vertical, 8)
                            .background(IDS.Colors.chipBackground).cornerRadius(10)
                    }
                } else {
                    HStack(spacing: 12) {
                        qtyButton("minus") { if simpleQty > 0 { setSimpleQty(item.id, simpleQty - 1) } }
                        Text("\(simpleQty)").frame(width: 24).font(.subheadline).bold()
                        qtyButton("plus") { setSimpleQty(item.id, simpleQty + 1) }
                    }
                }
            }

            if hasOptions && isExpanded {
                VStack(alignment: .leading, spacing: 12) {
                    ForEach(groups) { group in
                        VStack(alignment: .leading, spacing: 6) {
                            Text("\(group.name)").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                + Text(" · choose 1").font(.caption).foregroundColor(IDS.Colors.textTertiary)
                            VStack(alignment: .leading, spacing: 8) {
                                ForEach(group.choices) { choice in
                                    Button(action: { pendingChoices[group.id] = choice.id }) {
                                        HStack(spacing: 8) {
                                            Image(systemName: pendingChoices[group.id] == choice.id ? "largecircle.fill.circle" : "circle")
                                                .foregroundColor(pendingChoices[group.id] == choice.id ? IDS.Colors.brand : IDS.Colors.textTertiary)
                                            Text(choice.name + (choice.priceDelta > 0 ? " (+\(Int(choice.priceDelta)) RWF)" : ""))
                                                .font(.caption)
                                                .foregroundColor(IDS.Colors.textPrimary)
                                        }
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                    }
                    Button(action: { addConfiguredToCart(item) }) {
                        Text("Add to cart")
                            .font(IDS.Typography.bodyBold).foregroundColor(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(allGroupsChosen ? IDS.Colors.brand : IDS.Colors.textTertiary)
                            .cornerRadius(12)
                    }
                    .disabled(!allGroupsChosen)
                }
                .padding(.top, 14)
            }
        }
        .padding(16)
        .background(IDS.Colors.card)
        .cornerRadius(IDS.Layout.cardCornerRadius)
    }

    // For a no-option item only -- the original single-stepper interaction, completely
    // unchanged for the overwhelming majority of menu items that have no option groups.
    private func setSimpleQty(_ productId: String, _ qty: Int) {
        let key = eatsCartKey(productId, [])
        cart[key] = EatsCartLine(productId: productId, quantity: max(0, qty), choiceIds: [])
    }

    private func toggleExpand(_ productId: String) {
        pendingChoices = [:]
        expandedProductId = (expandedProductId == productId) ? nil : productId
    }

    private func addConfiguredToCart(_ item: MerchantProductDto) {
        let groups = item.optionGroups ?? []
        let choiceIds = groups.compactMap { pendingChoices[$0.id] }
        guard choiceIds.count == groups.count else { return } // one real required choice per group, enforced client-side too
        let key = eatsCartKey(item.id, choiceIds)
        cart[key] = EatsCartLine(productId: item.id, quantity: (cart[key]?.quantity ?? 0) + 1, choiceIds: choiceIds)
        pendingChoices = [:]
        expandedProductId = nil
    }

    private func qtyButton(_ symbol: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            ZStack {
                Circle().fill(IDS.Colors.chipBackground)
                Image(systemName: symbol).font(.caption).foregroundColor(IDS.Colors.textPrimary)
            }
            .frame(width: 30, height: 30)
        }
    }
}

/// Real self-hosted address-search autocomplete (2026-07-18) -- itunda's own Nominatim
/// geocoder, not a third-party Maps API. Mirrors bank-mfe's AddressAutocomplete/Android's
/// AddressAutocompleteField: debounced real search-as-you-type, a real suggestion list,
/// and on selection the real resolved coordinates are handed back to the caller so they
/// can be submitted explicitly (taking priority over EatsOrderService's own automatic
/// single-best-match fallback). Typing without selecting still places a real order via
/// that fallback.
private struct AddressAutocompleteField: View {
    let address: String
    let onAddressChange: (String) -> Void
    let onSuggestionSelected: (AddressSuggestionDto) -> Void

    @State private var suggestions: [AddressSuggestionDto] = []
    @State private var searchTask: Task<Void, Never>?

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            TextField("Delivery address", text: Binding(get: { address }, set: handleChange))
                .padding(12)
                .background(IDS.Colors.chipBackground)
                .cornerRadius(12)

            if !suggestions.isEmpty {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(suggestions) { suggestion in
                        Button(action: { selectSuggestion(suggestion) }) {
                            Text(suggestion.displayName)
                                .font(.caption)
                                .foregroundColor(IDS.Colors.textPrimary)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(12)
                        }
                    }
                }
                .background(IDS.Colors.chipBackground)
                .cornerRadius(12)
            }
        }
    }

    private func handleChange(_ text: String) {
        onAddressChange(text)
        suggestions = []
        searchTask?.cancel()
        let query = text.trimmingCharacters(in: .whitespaces)
        guard query.count >= 3 else { return }
        searchTask = Task {
            try? await Task.sleep(nanoseconds: 400_000_000)
            guard !Task.isCancelled else { return }
            do {
                let res = try await NetworkClient.shared.searchDeliveryAddress(query)
                if !Task.isCancelled { suggestions = res.suggestions }
            } catch {
                // Real, non-critical -- a failed suggestion fetch shouldn't block typing
                // a plain address; the order still places, just without a confirmed pin.
                if !Task.isCancelled { suggestions = [] }
            }
        }
    }

    private func selectSuggestion(_ suggestion: AddressSuggestionDto) {
        searchTask?.cancel()
        suggestions = []
        onSuggestionSelected(suggestion)
    }
}

// Real 배민오더-style table/QR in-store ordering (item 162) -- see
// DineInOrderController.kt's own doc comment on the backend. Same real checkout-mode
// toggle Android's own EatsCheckoutMode already established: reuses the exact same
// cart/menu-option selection as delivery, only the checkout step itself diverges (a
// table number replaces the address, no delivery fee, settles straight to the
// restaurant's wallet at placement).
private enum EatsCheckoutMode { case delivery, pickup, dineIn }

private struct EatsCheckoutView: View {
    let restaurant: ShoppingMerchantDto
    let cart: [String: EatsCartLine]
    let menu: [MerchantProductDto]
    let onBack: () -> Void
    let onOrderPlaced: (EatsOrderDto) -> Void
    let onDineInOrderPlaced: (DineInOrderDto) -> Void

    @State private var checkoutMode: EatsCheckoutMode = .delivery
    @State private var address = ""
    @State private var addressLatitude: Double?
    @State private var addressLongitude: Double?
    @State private var deliveryNotes = ""
    @State private var tableNumber = ""
    @State private var submitting = false
    @State private var error: String?
    // Real device binding step-up (2026-07-21) -- Eats checkout was a real gap:
    // already correctly enforced server-side (a real 403 DEVICE_NOT_VERIFIED) but
    // showed only a generic error, same fix already applied to Transfer/Savings.
    @State private var needsDeviceVerification = false

    private var lines: [(key: String, item: MerchantProductDto, line: EatsCartLine)] {
        cart.compactMap { key, line in
            guard line.quantity > 0, let item = menu.first(where: { $0.id == line.productId }) else { return nil }
            return (key, item, line)
        }
    }
    private var subtotal: Double { lines.reduce(0) { $0 + eatsLineUnitPrice($1.item, $1.line.choiceIds) * Double($1.line.quantity) } }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left").font(.system(size: 18, weight: .medium)).frame(width: 44, height: 44)
                }
                .accessibilityLabel("Back")
                Text("Checkout").font(IDS.Typography.title).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
            }
            .padding(.horizontal, 8)

            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(lines, id: \.key) { _, item, line in
                        HStack {
                            Text("\(item.name)\(eatsOptionsSummary(item, line.choiceIds)) x\(line.quantity)").foregroundColor(IDS.Colors.textPrimary)
                            Spacer()
                            Text("\(Int(eatsLineUnitPrice(item, line.choiceIds) * Double(line.quantity))) RWF").foregroundColor(IDS.Colors.textPrimary)
                        }
                    }
                    Divider()
                    HStack {
                        Text("Subtotal").foregroundColor(IDS.Colors.textPrimary)
                        Spacer()
                        Text("\(Int(subtotal)) RWF").foregroundColor(IDS.Colors.textPrimary)
                    }
                    Picker("", selection: $checkoutMode) {
                        Text("Delivery").tag(EatsCheckoutMode.delivery)
                        Text("Pickup").tag(EatsCheckoutMode.pickup)
                        Text("Order at table").tag(EatsCheckoutMode.dineIn)
                    }
                    .pickerStyle(.segmented)

                    if checkoutMode == .delivery {
                        Text("Plus a real delivery fee, added at checkout").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        AddressAutocompleteField(
                            address: address,
                            onAddressChange: { address = $0; addressLatitude = nil; addressLongitude = nil },
                            onSuggestionSelected: { suggestion in
                                address = suggestion.displayName
                                addressLatitude = suggestion.latitude
                                addressLongitude = suggestion.longitude
                            }
                        )
                        if addressLatitude != nil {
                            Text("Pinned -- real distance-based delivery fee applies").font(.caption).foregroundColor(.green)
                        }
                        TextField("Delivery notes (optional) -- e.g. Leave at the gate", text: Binding(
                            get: { deliveryNotes },
                            set: { deliveryNotes = String($0.prefix(500)) }
                        ))
                        .padding(12)
                        .background(IDS.Colors.chipBackground)
                        .cornerRadius(12)
                    } else if checkoutMode == .pickup {
                        // Real Baemin-style 포장주문 (Pickup) order type (item 208) --
                        // backend-complete since 2026-07-26; bank-mfe/Android clients
                        // 2026-07-31. See PlaceEatsOrderRequest's own doc comment.
                        Text("No delivery fee -- collect your order at the restaurant once it's ready").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        TextField("Pickup notes (optional)", text: Binding(
                            get: { deliveryNotes },
                            set: { deliveryNotes = String($0.prefix(500)) }
                        ))
                        .padding(12)
                        .background(IDS.Colors.chipBackground)
                        .cornerRadius(12)
                    } else {
                        TextField("Table number (e.g. 12, Patio 3)", text: $tableNumber)
                            .padding(12)
                            .background(IDS.Colors.chipBackground)
                            .cornerRadius(12)
                        TextField("Notes (optional) -- e.g. No onions", text: Binding(
                            get: { deliveryNotes },
                            set: { deliveryNotes = String($0.prefix(500)) }
                        ))
                        .padding(12)
                        .background(IDS.Colors.chipBackground)
                        .cornerRadius(12)
                    }
                    if let error {
                        Text(error).font(.caption).foregroundColor(.red)
                    }
                }
                .padding(.horizontal, IDS.Layout.screenHorizontal)
                .padding(.top, 12)
            }

            Button(action: { Task { await placeOrder() } }) {
                Text(submitting ? "Placing order…" : "Place order")
                    .font(IDS.Typography.bodyBold)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(submitting || !canSubmit ? IDS.Colors.textTertiary : IDS.Colors.brand)
                    .cornerRadius(16)
            }
            .disabled(submitting || !canSubmit)
            .padding(IDS.Layout.screenHorizontal)
            DeviceStepUpHost(
                visible: needsDeviceVerification,
                onDismiss: { needsDeviceVerification = false },
                onVerified: {
                    needsDeviceVerification = false
                    await placeOrder()
                }
            )
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }

    private var canSubmit: Bool {
        switch checkoutMode {
        case .delivery: return !address.isEmpty
        case .pickup: return true
        case .dineIn: return !tableNumber.trimmingCharacters(in: .whitespaces).isEmpty
        }
    }

    private func placeOrder() async {
        submitting = true
        error = nil
        needsDeviceVerification = false
        defer { submitting = false }
        do {
            if checkoutMode == .dineIn {
                let res = try await NetworkClient.shared.placeDineInOrder(PlaceDineInOrderRequest(
                    restaurantId: restaurant.merchantId,
                    tableNumber: tableNumber.trimmingCharacters(in: .whitespaces),
                    items: lines.map { DineInOrderItemRequest(menuItemId: $0.item.id, quantity: $0.line.quantity, selectedChoiceIds: $0.line.choiceIds.isEmpty ? nil : $0.line.choiceIds) },
                    notes: deliveryNotes.trimmingCharacters(in: .whitespaces).isEmpty ? nil : deliveryNotes.trimmingCharacters(in: .whitespaces)
                ))
                onDineInOrderPlaced(res.order)
            } else {
                let res = try await NetworkClient.shared.placeEatsOrder(PlaceEatsOrderRequest(
                    restaurantId: restaurant.merchantId,
                    items: lines.map { EatsOrderItemRequest(menuItemId: $0.item.id, quantity: $0.line.quantity, selectedChoiceIds: $0.line.choiceIds.isEmpty ? nil : $0.line.choiceIds) },
                    deliveryAddress: checkoutMode == .pickup ? "" : address.trimmingCharacters(in: .whitespaces),
                    deliveryLatitude: checkoutMode == .pickup ? nil : addressLatitude,
                    deliveryLongitude: checkoutMode == .pickup ? nil : addressLongitude,
                    deliveryNotes: deliveryNotes.trimmingCharacters(in: .whitespaces).isEmpty ? nil : deliveryNotes.trimmingCharacters(in: .whitespaces),
                    fulfillmentType: checkoutMode == .pickup ? "PICKUP" : "DELIVERY"
                ))
                onOrderPlaced(res.order)
            }
        } catch NetworkError.deviceNotVerified {
            needsDeviceVerification = true
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

private struct DineInOrderConfirmationView: View {
    let order: DineInOrderDto
    let onDone: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            Spacer()
            Image(systemName: "checkmark.seal.fill").font(.system(size: 44)).foregroundColor(.green)
            Text("Order placed").font(IDS.Typography.title).foregroundColor(IDS.Colors.textPrimary)
            Text("\(Int(order.totalAmount)) RWF").font(IDS.Typography.largeAmount).foregroundColor(IDS.Colors.textPrimary)
            Text("Table \(order.tableNumber)").font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
            Spacer()
            Button(action: onDone) {
                Text("Done").font(IDS.Typography.bodyBold).foregroundColor(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 16)
                    .background(IDS.Colors.brand).cornerRadius(16)
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
        }
        .padding(.bottom, 24)
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }
}

private struct EatsOrderConfirmationView: View {
    let order: EatsOrderDto
    let onDone: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Spacer()
            Text("Order placed").font(IDS.Typography.title).foregroundColor(IDS.Colors.textPrimary)
            Text("\(Int(order.totalAmount)) RWF").font(IDS.Typography.largeAmount).foregroundColor(IDS.Colors.textPrimary)
            Text("Delivering to \(order.deliveryAddress)").font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
            Spacer()
            Button(action: onDone) {
                Text("Track order").font(IDS.Typography.bodyBold).foregroundColor(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 16)
                    .background(IDS.Colors.brand).cornerRadius(16)
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.bottom, 40)
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }
}

private struct EatsOrderRow<Action: View>: View {
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
                Text("\(Int(order.totalAmount)) RWF").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
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
        .padding(18)
        .background(IDS.Colors.card)
        .cornerRadius(IDS.Layout.cardCornerRadius)
    }
}

extension EatsOrderRow where Action == EmptyView {
    init(order: EatsOrderDto, restaurant: ShoppingMerchantDto? = nil) {
        self.order = order
        self.restaurant = restaurant
        self.action = { EmptyView() }
    }
}

private struct MyEatsOrdersView: View {
    let onReorder: (EatsOrderDto) -> Void
    let reorderingId: String?
    let restaurants: [ShoppingMerchantDto]?

    @State private var orders: [EatsOrderDto]?
    @State private var error: String?
    @State private var cancellingId: String?

    var body: some View {
        Group {
            if let error {
                VStack(alignment: .leading, spacing: 10) {
                    Text(error).foregroundColor(.red).font(.subheadline)
                    Button("Retry") { Task { await load() } }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(20)
                .background(IDS.Colors.card)
                .cornerRadius(IDS.Layout.cardCornerRadius)
            } else if orders == nil {
                SkeletonBlock(height: 120)
            } else if orders!.isEmpty {
                EmptyStateView("No orders yet — order from a nearby restaurant and it'll show up here.")
            } else {
                VStack(spacing: 10) {
                    ForEach(orders!) { order in
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
                                    ReorderButton(reordering: reorderingId == order.id, onClick: { onReorder(order) })
                                }
                            } else if order.status == "CANCELLED" {
                                ReorderButton(reordering: reorderingId == order.id, onClick: { onReorder(order) })
                            }
                        }
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
            let res = try await NetworkClient.shared.getMyEatsOrders()
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
            _ = try await NetworkClient.shared.cancelEatsOrder(orderId)
            await load()
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
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
private struct MyDineInOrdersView: View {
    @State private var orders: [DineInOrderDto]?
    @State private var error: String?
    @State private var cancellingId: String?

    var body: some View {
        Group {
            if let error {
                VStack(alignment: .leading, spacing: 10) {
                    Text(error).foregroundColor(.red).font(.subheadline)
                    Button("Retry") { Task { await load() } }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(20)
                .background(IDS.Colors.card)
                .cornerRadius(IDS.Layout.cardCornerRadius)
            } else if orders == nil {
                SkeletonBlock(height: 80)
            } else if orders!.isEmpty {
                EmptyView()
            } else {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Table orders").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                    VStack(spacing: 10) {
                        ForEach(orders!) { order in
                            VStack(alignment: .leading, spacing: 6) {
                                HStack {
                                    Text(dineInStatusLabel[order.status] ?? order.status).font(.subheadline).bold().foregroundColor(IDS.Colors.brand)
                                    Spacer()
                                    Text("\(Int(order.totalAmount)) RWF").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
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
                            .padding(16)
                            .background(IDS.Colors.card)
                            .cornerRadius(IDS.Layout.cardCornerRadius)
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

private struct ReorderButton: View {
    let reordering: Bool
    let onClick: () -> Void

    var body: some View {
        Button(action: onClick) {
            Text(reordering ? "Reordering…" : "Reorder")
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
private struct FavoriteRestaurantsView: View {
    let onOpen: (FavoriteRestaurantDto) -> Void
    let onChanged: () -> Void

    @State private var favorites: [FavoriteRestaurantDto]?
    @State private var error: String?
    @State private var removingId: String?

    var body: some View {
        Group {
            if let error {
                VStack(alignment: .leading, spacing: 10) {
                    Text(error).foregroundColor(.red).font(.subheadline)
                    Button("Retry") { Task { await load() } }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(20)
                .background(IDS.Colors.card)
                .cornerRadius(IDS.Layout.cardCornerRadius)
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
                            Image(systemName: "heart.fill").foregroundColor(.red)
                        }
                        .buttonStyle(.plain)
                        .disabled(removingId == favorite.restaurantId)
                    }
                    .padding(18)
                    .background(IDS.Colors.card)
                    .cornerRadius(IDS.Layout.cardCornerRadius)
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

private struct DeliverContent: View {
    @State private var rider: RiderDto?
    @State private var loadedRider = false
    @State private var error: String?
    @State private var registering = false
    @State private var available: [EatsOrderDto]?
    @State private var mine: [EatsOrderDto]?
    @State private var busyOrderId: String?

    var body: some View {
        ScrollView {
            VStack(spacing: IDS.Layout.cardGap) {
                if !loadedRider {
                    SkeletonBlock(height: 120)
                } else if rider == nil {
                    riderOnboarding
                } else {
                    riderDashboard
                }
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.top, IDS.Layout.screenTop)
            .padding(.bottom, IDS.Layout.sectionSpacing)
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await loadRider() }
        .task {
            while !Task.isCancelled {
                if rider != nil { await loadDeliveries() }
                try? await Task.sleep(nanoseconds: 4_000_000_000)
            }
        }
    }

    private var riderOnboarding: some View {
        VStack(spacing: 12) {
            Image(systemName: "bicycle").font(.system(size: 30)).foregroundColor(IDS.Colors.brand)
            Text("Deliver with Itunda").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            Text("Earn a real delivery fee for every order you deliver, paid straight to your wallet.")
                .font(.subheadline)
                .foregroundColor(IDS.Colors.textSecondary)
                .multilineTextAlignment(.center)
            Button(action: { Task { await register() } }) {
                Text(registering ? "Registering…" : "Become a rider")
                    .font(IDS.Typography.bodyBold).foregroundColor(.white)
                    .padding(.horizontal, 24).padding(.vertical, 14)
                    .background(IDS.Colors.brand).cornerRadius(14)
            }
            .disabled(registering)
            if let error { Text(error).font(.caption).foregroundColor(.red) }
        }
        .frame(maxWidth: .infinity)
        .padding(28)
        .background(IDS.Colors.card)
        .cornerRadius(IDS.Layout.cardCornerRadius)
    }

    private var riderDashboard: some View {
        let rider = rider!
        let active = (mine ?? []).filter { $0.status != "DELIVERED" }
        let past = (mine ?? []).filter { $0.status == "DELIVERED" }

        return VStack(spacing: IDS.Layout.cardGap) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(rider.available ? "You're online" : "You're offline").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                    Text(rider.available ? "Visible for new deliveries" : "Go online to see deliveries").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }
                Spacer()
                Button(action: { Task { await toggleAvailability() } }) {
                    Text(rider.available ? "Go offline" : "Go online")
                        .font(.subheadline).bold().foregroundColor(.white)
                        .padding(.horizontal, 16).padding(.vertical, 10)
                        .background(rider.available ? Color.red : IDS.Colors.brand)
                        .cornerRadius(14)
                }
            }
            .padding(18)
            .background(IDS.Colors.card)
            .cornerRadius(IDS.Layout.cardCornerRadius)

            if let error { Text(error).font(.caption).foregroundColor(.red) }

            if !active.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Your active deliveries").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    ForEach(active) { order in
                        EatsOrderRow(order: order) {
                            if let next = nextRiderStatus(order.status) {
                                Button(action: { Task { await advanceRider(order, to: next) } }) {
                                    Text(busyOrderId == order.id ? "Updating…" : "Mark \((eatsStatusLabel[next] ?? next).lowercased())")
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

            if rider.available {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Available deliveries").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    if available == nil {
                        SkeletonBlock(height: 80)
                    } else if available!.isEmpty {
                        EmptyStateView("No deliveries waiting right now — stay online and you'll be notified.")
                    } else {
                        ForEach(available!) { order in
                            EatsOrderRow(order: order) {
                                Button(action: { Task { await claim(order) } }) {
                                    Text(busyOrderId == order.id ? "Claiming…" : "Claim delivery")
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

            if !past.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Completed").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    ForEach(past) { EatsOrderRow(order: $0) }
                }
            }
        }
    }

    private func loadRider() async {
        do {
            let res = try await NetworkClient.shared.getMyRiderProfile()
            rider = res.rider
            error = nil
        } catch let NetworkError.httpError(statusCode) where statusCode == 404 {
            rider = nil
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
        loadedRider = true
    }

    private func loadDeliveries() async {
        do {
            async let availableRes = NetworkClient.shared.getAvailableDeliveries()
            async let mineRes = NetworkClient.shared.getRiderDeliveries()
            let (a, m) = try await (availableRes, mineRes)
            available = a.orders
            mine = m.orders
        } catch {
            // Keep showing the last-known lists on a transient poll failure.
        }
    }

    private func register() async {
        registering = true
        error = nil
        defer { registering = false }
        do {
            rider = try await NetworkClient.shared.registerRider().rider
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func toggleAvailability() async {
        guard let rider else { return }
        do {
            self.rider = try await NetworkClient.shared.setRiderAvailability(!rider.available).rider
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func claim(_ order: EatsOrderDto) async {
        busyOrderId = order.id
        error = nil
        defer { busyOrderId = nil }
        do {
            _ = try await NetworkClient.shared.claimDelivery(order.id)
            await loadDeliveries()
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func advanceRider(_ order: EatsOrderDto, to status: String) async {
        busyOrderId = order.id
        error = nil
        defer { busyOrderId = nil }
        do {
            _ = try await NetworkClient.shared.updateRiderOrderStatus(order.id, status: status)
            await loadDeliveries()
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}
