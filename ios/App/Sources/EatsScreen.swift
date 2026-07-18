import SwiftUI
import CoreDesignSystem

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

private func nextRiderStatus(_ current: String) -> String? {
    guard let idx = riderStatusChain.firstIndex(of: current), idx + 1 < riderStatusChain.count else { return nil }
    return riderStatusChain[idx + 1]
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

private enum OrderFoodView { case browse, orders }

private struct OrderFoodContent: View {
    @State private var view: OrderFoodView = .browse
    @State private var restaurants: [ShoppingMerchantDto]?
    @State private var error: String?
    @State private var selectedRestaurant: ShoppingMerchantDto?
    @State private var menu: [MerchantProductDto]?
    @State private var cart: [String: Int] = [:]
    @State private var showCheckout = false
    @State private var confirmedOrder: EatsOrderDto?

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
            } else if let restaurant = selectedRestaurant {
                if showCheckout {
                    EatsCheckoutView(
                        restaurant: restaurant,
                        cart: cart,
                        menu: menu ?? [],
                        onBack: { showCheckout = false },
                        onOrderPlaced: { confirmedOrder = $0 }
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
        .task { if restaurants == nil { await loadRestaurants() } }
    }

    private var browseBody: some View {
        ScrollView {
            VStack(spacing: IDS.Layout.cardGap) {
                Picker("", selection: $view) {
                    Text("Restaurants").tag(OrderFoodView.browse)
                    Text("My orders").tag(OrderFoodView.orders)
                }
                .pickerStyle(.segmented)

                if view == .orders {
                    MyEatsOrdersView()
                } else if let error {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(error).foregroundColor(.red).font(.subheadline)
                        Button("Retry") { Task { await loadRestaurants() } }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(20)
                    .background(IDS.Colors.card)
                    .cornerRadius(IDS.Layout.cardCornerRadius)
                } else if restaurants == nil {
                    ProgressView().frame(maxWidth: .infinity, minHeight: 120)
                } else if restaurants!.isEmpty {
                    Text("No restaurants registered yet.").foregroundColor(IDS.Colors.textSecondary)
                } else {
                    ForEach(restaurants!) { restaurant in
                        Button(action: { Task { await openRestaurant(restaurant) } }) {
                            HStack(spacing: 14) {
                                ZStack {
                                    RoundedRectangle(cornerRadius: 14).fill(IDS.Colors.chipBackground)
                                    Image(systemName: "fork.knife").foregroundColor(IDS.Colors.brand)
                                }
                                .frame(width: 44, height: 44)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(restaurant.businessName).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                                    Text("Real menu, real delivery").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                }
                                Spacer()
                            }
                            .padding(18)
                            .background(IDS.Colors.card)
                            .cornerRadius(IDS.Layout.cardCornerRadius)
                        }
                        .buttonStyle(.plain)
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
            let res = try await NetworkClient.shared.getShoppingMerchants()
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
}

private struct RestaurantMenuView: View {
    let restaurant: ShoppingMerchantDto
    let menu: [MerchantProductDto]?
    @Binding var cart: [String: Int]
    let onBack: () -> Void
    let onCheckout: () -> Void

    private var cartCount: Int { cart.values.reduce(0, +) }

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

            ScrollView {
                VStack(spacing: 10) {
                    if let menu {
                        if menu.isEmpty {
                            Text("No menu items yet.").foregroundColor(IDS.Colors.textSecondary).padding(.top, 20)
                        }
                        ForEach(menu) { item in
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(item.name).font(IDS.Typography.bodyMedium).foregroundColor(IDS.Colors.textPrimary)
                                    Text("\(Int(item.price)) RWF").font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
                                }
                                Spacer()
                                HStack(spacing: 12) {
                                    qtyButton("minus") { if (cart[item.id] ?? 0) > 0 { cart[item.id]! -= 1 } }
                                    Text("\(cart[item.id] ?? 0)").frame(width: 24).font(.subheadline).bold()
                                    qtyButton("plus") { cart[item.id] = (cart[item.id] ?? 0) + 1 }
                                }
                            }
                            .padding(16)
                            .background(IDS.Colors.card)
                            .cornerRadius(IDS.Layout.cardCornerRadius)
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

private struct EatsCheckoutView: View {
    let restaurant: ShoppingMerchantDto
    let cart: [String: Int]
    let menu: [MerchantProductDto]
    let onBack: () -> Void
    let onOrderPlaced: (EatsOrderDto) -> Void

    @State private var address = ""
    @State private var addressLatitude: Double?
    @State private var addressLongitude: Double?
    @State private var submitting = false
    @State private var error: String?

    private var lines: [(MerchantProductDto, Int)] {
        cart.compactMap { productId, qty in
            guard qty > 0, let item = menu.first(where: { $0.id == productId }) else { return nil }
            return (item, qty)
        }
    }
    private var subtotal: Double { lines.reduce(0) { $0 + $1.0.price * Double($1.1) } }

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
                    ForEach(lines, id: \.0.id) { item, qty in
                        HStack {
                            Text("\(item.name) x\(qty)").foregroundColor(IDS.Colors.textPrimary)
                            Spacer()
                            Text("\(Int(item.price * Double(qty))) RWF").foregroundColor(IDS.Colors.textPrimary)
                        }
                    }
                    Divider()
                    HStack {
                        Text("Subtotal").foregroundColor(IDS.Colors.textPrimary)
                        Spacer()
                        Text("\(Int(subtotal)) RWF").foregroundColor(IDS.Colors.textPrimary)
                    }
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
                    .background(submitting || address.isEmpty ? IDS.Colors.textTertiary : IDS.Colors.brand)
                    .cornerRadius(16)
            }
            .disabled(submitting || address.isEmpty)
            .padding(IDS.Layout.screenHorizontal)
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }

    private func placeOrder() async {
        submitting = true
        error = nil
        defer { submitting = false }
        do {
            let res = try await NetworkClient.shared.placeEatsOrder(PlaceEatsOrderRequest(
                restaurantId: restaurant.merchantId,
                items: lines.map { EatsOrderItemRequest(menuItemId: $0.0.id, quantity: $0.1) },
                deliveryAddress: address.trimmingCharacters(in: .whitespaces),
                deliveryLatitude: addressLatitude,
                deliveryLongitude: addressLongitude
            ))
            onOrderPlaced(res.order)
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
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
    @ViewBuilder let action: () -> Action

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
            action()
        }
        .padding(18)
        .background(IDS.Colors.card)
        .cornerRadius(IDS.Layout.cardCornerRadius)
    }
}

extension EatsOrderRow where Action == EmptyView {
    init(order: EatsOrderDto) {
        self.order = order
        self.action = { EmptyView() }
    }
}

private struct MyEatsOrdersView: View {
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
                ProgressView().frame(maxWidth: .infinity, minHeight: 120)
            } else if orders!.isEmpty {
                Text("No orders yet.").foregroundColor(IDS.Colors.textSecondary)
            } else {
                VStack(spacing: 10) {
                    ForEach(orders!) { order in
                        EatsOrderRow(order: order) {
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
                    ProgressView().frame(maxWidth: .infinity, minHeight: 120)
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
                        ProgressView().frame(maxWidth: .infinity, minHeight: 80)
                    } else if available!.isEmpty {
                        Text("No deliveries waiting right now.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
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
