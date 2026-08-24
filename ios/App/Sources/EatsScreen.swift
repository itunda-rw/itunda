import SwiftUI
import CoreDesignSystem
import CoreNetwork


// Eats tab entry point + order-food browse content. Sub-views live in
// EatsMembership.swift / EatsRestaurantMenu.swift / EatsCheckout.swift /
// EatsOrders.swift / EatsDeliver.swift (split 2026-08-19 for real
// file-size decomposition).

let eatsStatusLabel: [String: String] = [
    "PLACED": "Placed",
    "ACCEPTED": "Accepted by restaurant",
    "PREPARING": "Preparing",
    "READY_FOR_PICKUP": "Ready for pickup",
    "RIDER_ASSIGNED": "Rider on the way to restaurant",
    "PICKED_UP": "Picked up — on the way",
    "DELIVERED": "Delivered",
    "CANCELLED": "Cancelled — refunded",
]

let riderStatusChain = ["RIDER_ASSIGNED", "PICKED_UP", "DELIVERED"]

let dineInStatusLabel: [String: String] = [
    "PLACED": "Placed",
    "ACCEPTED": "Accepted by restaurant",
    "PREPARING": "Preparing",
    "SERVED": "Served",
    "CANCELLED": "Cancelled — refunded",
]

func nextRiderStatus(_ current: String) -> String? {
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

func eatsCartKey(_ productId: String, _ choiceIds: [String]) -> String {
    choiceIds.isEmpty ? productId : "\(productId)::\(choiceIds.sorted().joined(separator: ","))"
}

// Real, human-readable summary of a resolved cart line's selected options -- mirrors
// the backend's own EatsOrderService.buildSelectedOptionsJson, but purely for display;
// pricing always comes from the real menu item + real choice deltas, never this string.
func eatsOptionsSummary(_ item: MerchantProductDto, _ choiceIds: [String]) -> String {
    guard !choiceIds.isEmpty else { return "" }
    let names = (item.optionGroups ?? []).flatMap { $0.choices }.filter { choiceIds.contains($0.id) }.map { $0.name }
    return names.isEmpty ? "" : " (\(names.joined(separator: ", ")))"
}

func eatsLineUnitPrice(_ item: MerchantProductDto, _ choiceIds: [String]) -> Double {
    let delta = (item.optionGroups ?? []).flatMap { $0.choices }.filter { choiceIds.contains($0.id) }.reduce(0.0) { $0 + $1.priceDelta }
    return item.price + delta
}

enum EatsMode { case order, deliver }

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

enum OrderFoodView { case browse, favorites, orders }

let isoDateFormatterFractional: ISO8601DateFormatter = {
    let f = ISO8601DateFormatter()
    f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    return f
}()

struct OrderFoodContent: View {
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
    // Real "recently viewed restaurants" rail (2026-08-23) -- see
    // RecentlyViewedStores.swift's own doc comment.
    @State private var recentlyViewedRestaurants: [RecentlyViewedRestaurant] = RecentlyViewedRestaurantsStore.shared.getAll()

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
                do { allRestaurants = try await NetworkClient.shared.getShoppingMerchants(businessType: "RESTAURANT").merchants } catch {}
            }
            if categories.isEmpty {
                do { categories = try await NetworkClient.shared.getMerchantCategories(businessType: "RESTAURANT").categories } catch {}
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

                    // Real "recently viewed restaurants" rail -- see
                    // RecentlyViewedStores.swift's own doc comment. Hidden once the user
                    // starts filtering, same "merchandising above the raw list, gone once
                    // actively searching" discipline this codebase's own equivalent rails
                    // already establish.
                    if selectedCategory == nil && searchInput.trimmingCharacters(in: .whitespaces).isEmpty && !recentlyViewedRestaurants.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("🕒 Recently viewed").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 10) {
                                    ForEach(recentlyViewedRestaurants) { rv in
                                        Button(action: {
                                            Task { await openRestaurant(ShoppingMerchantDto(merchantId: rv.merchantId, businessName: rv.businessName, category: rv.category, cashbackRate: "1%")) }
                                        }) {
                                            VStack(alignment: .leading, spacing: 6) {
                                                RestaurantPhotoThumb(imageUrl: rv.photoUrl, side: 96)
                                                    .frame(width: 120, height: 96)
                                                    .clipShape(RoundedRectangle(cornerRadius: 10))
                                                Text(rv.businessName).font(.caption).bold().foregroundColor(IDS.Colors.textPrimary).lineLimit(2)
                                            }
                                            .frame(width: 120)
                                        }
                                    }
                                }
                            }
                        }
                        .padding(.bottom, 8)
                    }

                    if let error {
                        VStack(alignment: .leading, spacing: 10) {
                            Text(error).foregroundColor(.red).font(.subheadline)
                            Button("Retry") { Task { await loadRestaurants() } }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        // Real fix (2026-08-24, flat-design sweep): dropped the Card
                        // wrapper -- a lone error state.
                        .padding(20)
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
                                                IDS.Icons.star(size: 12, color: .yellow)
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
                                    WishlistHeart(favorited: favoriteIds.contains(restaurant.merchantId), size: 18)
                                }
                                .accessibilityLabel(favoriteIds.contains(restaurant.merchantId) ? "Remove from favorites" : "Add to favorites")
                                .buttonStyle(.plain)
                                .disabled(favoritingId == restaurant.merchantId)
                            }
                            .padding(18)
                            .background(IDS.Colors.card)
                            .cornerRadius(IDS.Layout.cardCornerRadius).idsCardBorder(cornerRadius: IDS.Layout.cardCornerRadius)
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
            let res = try await NetworkClient.shared.getShoppingMerchants(category: selectedCategory, businessType: "RESTAURANT", q: q.isEmpty ? nil : q)
            restaurants = res.merchants
            error = nil
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func openRestaurant(_ restaurant: ShoppingMerchantDto) async {
        selectedRestaurant = restaurant
        recentlyViewedRestaurants = RecentlyViewedRestaurantsStore.shared.add(
            RecentlyViewedRestaurant(merchantId: restaurant.merchantId, businessName: restaurant.businessName, category: restaurant.category, photoUrl: restaurant.photoUrl)
        )
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

