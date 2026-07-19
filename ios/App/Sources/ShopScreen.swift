import SwiftUI
import CoreDesignSystem

/// Real Coupang-style multi-item checkout (2026-07-18) -- iOS mirror of Android's
/// (new) ShopTab (SuperAppTabs.kt), replacing the old Toss-Shopping-cashback
/// DiscoverScreen entirely. See NetworkClient.swift's Commerce extension and
/// rw.itunda.commerce.OrderService's own doc comment for the full backend account,
/// including the honest "self-declared fulfillment, no real courier network" scope.
///
/// Folds in real Coupang Eats-style food delivery (2026-07-18) via a Shop/Eats segmented
/// control -- the bottom nav has no free tab slot, mirrors Android's identical fold-in
/// in ShopTab (SuperAppTabs.kt). See EatsContent's own doc comment below.
private enum ShopMode { case shop, eats }

struct ShopScreen: View {
    @State private var mode: ShopMode = .shop

    var body: some View {
        VStack(spacing: 0) {
            Picker("", selection: $mode) {
                Text("Shop").tag(ShopMode.shop)
                Text("Eats").tag(ShopMode.eats)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.top, 8)

            switch mode {
            case .shop: CommerceShopContent()
            case .eats: EatsContent()
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }
}

private enum CommerceView { case browse, orders }

// Real cross-merchant cart (2026-07-20) -- closes the "real Coupang splits a
// multi-seller cart into per-seller orders, not attempted here" simplification the
// matrix named. Flattened (keyed by "merchantId:productId") rather than nested
// dictionaries, mirroring Android's own identical CommerceCartLine shape exactly.
private struct CommerceCartLine {
    let merchantId: String
    let businessName: String
    let product: MerchantProductDto
    var quantity: Int
}

private struct CommerceCheckoutResult: Identifiable {
    var id: String { merchantId }
    let merchantId: String
    let businessName: String
    let order: OrderDto?
    let error: String?
}

private struct CommerceShopContent: View {
    @State private var view: CommerceView = .browse
    @State private var merchants: [ShoppingMerchantDto]?
    @State private var error: String?
    @State private var selectedMerchant: ShoppingMerchantDto?
    @State private var products: [MerchantProductDto]?
    @State private var cart: [String: CommerceCartLine] = [:]
    @State private var showCart = false
    @State private var results: [CommerceCheckoutResult]?

    private var totalItems: Int { cart.values.reduce(0) { $0 + $1.quantity } }

    var body: some View {
        Group {
            if let results {
                MultiCartResultsView(results: results, onDone: {
                    self.results = nil
                    self.selectedMerchant = nil
                    self.products = nil
                    self.showCart = false
                    self.view = .orders
                })
            } else if showCart {
                MultiCartView(
                    cart: $cart,
                    onBack: { showCart = false },
                    onOrderPlaced: { checkoutResults in
                        for r in checkoutResults where r.order != nil {
                            cart = cart.filter { !$0.key.hasPrefix("\(r.merchantId):") }
                        }
                        results = checkoutResults
                    }
                )
            } else if let merchant = selectedMerchant {
                MerchantDetailView(
                    merchant: merchant,
                    products: products,
                    cart: $cart,
                    onBack: { selectedMerchant = nil },
                    onViewCart: { showCart = true }
                )
            } else {
                browseBody
            }
        }
        .task { if merchants == nil { await loadMerchants() } }
    }

    private var browseBody: some View {
        ZStack(alignment: .bottom) {
            ScrollView {
                VStack(spacing: IDS.Layout.cardGap) {
                    TdsPlainTopBar(title: "Shop")

                    Picker("", selection: $view) {
                        Text("Merchants").tag(CommerceView.browse)
                        Text("My orders").tag(CommerceView.orders)
                    }
                    .pickerStyle(.segmented)

                    if view == .orders {
                        MyCommerceOrdersView()
                    } else if let error {
                        VStack(alignment: .leading, spacing: 10) {
                            Text(error).foregroundColor(.red).font(.subheadline)
                            Button("Retry") { Task { await loadMerchants() } }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(20)
                        .background(IDS.Colors.card)
                        .cornerRadius(IDS.Layout.cardCornerRadius)
                    } else if merchants == nil {
                        ProgressView().frame(maxWidth: .infinity, minHeight: 120)
                    } else if merchants!.isEmpty {
                        Text("No stores registered yet.").foregroundColor(IDS.Colors.textSecondary)
                    } else {
                        ForEach(merchants!) { merchant in
                            Button(action: { Task { await openMerchant(merchant) } }) {
                                HStack(spacing: 14) {
                                    ZStack {
                                        RoundedRectangle(cornerRadius: 14).fill(IDS.Colors.chipBackground)
                                        Image(systemName: "storefront.fill").foregroundColor(IDS.Colors.brand)
                                    }
                                    .frame(width: 44, height: 44)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(merchant.businessName).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                                        Text("\(merchant.cashbackRate) cashback on QR/code payments")
                                            .font(.caption)
                                            .foregroundColor(IDS.Colors.textSecondary)
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
                .padding(.bottom, totalItems > 0 ? 80 : IDS.Layout.sectionSpacing)
            }
            if view == .browse && totalItems > 0 {
                CartFab(totalItems: totalItems, onTap: { showCart = true })
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }

    private func loadMerchants() async {
        do {
            let res = try await NetworkClient.shared.getShoppingMerchants()
            merchants = res.merchants
            error = nil
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func openMerchant(_ merchant: ShoppingMerchantDto) async {
        selectedMerchant = merchant
        products = nil
        do {
            let res = try await NetworkClient.shared.getMerchantProducts(merchantId: merchant.merchantId)
            products = res.products
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

private struct CartFab: View {
    let totalItems: Int
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack {
                Image(systemName: "cart.fill")
                Text("View cart (\(totalItems) item\(totalItems == 1 ? "" : "s"))")
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

private struct MerchantDetailView: View {
    let merchant: ShoppingMerchantDto
    let products: [MerchantProductDto]?
    @Binding var cart: [String: CommerceCartLine]
    let onBack: () -> Void
    let onViewCart: () -> Void

    private var totalItems: Int { cart.values.reduce(0) { $0 + $1.quantity } }
    private func qty(_ productId: String) -> Int { cart["\(merchant.merchantId):\(productId)"]?.quantity ?? 0 }
    private func setQty(_ product: MerchantProductDto, _ quantity: Int) {
        let key = "\(merchant.merchantId):\(product.id)"
        if quantity <= 0 { cart.removeValue(forKey: key) }
        else { cart[key] = CommerceCartLine(merchantId: merchant.merchantId, businessName: merchant.businessName, product: product, quantity: quantity) }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left").font(.system(size: 18, weight: .medium)).frame(width: 44, height: 44)
                }
                .accessibilityLabel("Back")
                Text(merchant.businessName).font(IDS.Typography.title).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
            }
            .padding(.horizontal, 8)

            ScrollView {
                VStack(spacing: 10) {
                    if let products {
                        if products.isEmpty {
                            Text("No products yet.").foregroundColor(IDS.Colors.textSecondary).padding(.top, 20)
                        }
                        ForEach(products) { product in
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(product.name).font(IDS.Typography.bodyMedium).foregroundColor(IDS.Colors.textPrimary)
                                    Text("\(Int(product.price)) RWF").font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
                                }
                                Spacer()
                                HStack(spacing: 12) {
                                    qtyButton("minus") { setQty(product, qty(product.id) - 1) }
                                    Text("\(qty(product.id))").frame(width: 24).font(.subheadline).bold()
                                    qtyButton("plus") { setQty(product, qty(product.id) + 1) }
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

            if totalItems > 0 {
                CartFab(totalItems: totalItems, onTap: onViewCart)
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

/// Real per-seller order splitting -- each merchant group becomes its own real,
/// independent placeOrder() call. Sequential, not concurrent: these are real
/// money-moving calls against the same buyer wallet, and a clear one-at-a-time
/// result list is more honest than a swallowed batch result. A failure on one
/// merchant's order does not block or roll back any other.
private struct MultiCartView: View {
    @Binding var cart: [String: CommerceCartLine]
    let onBack: () -> Void
    let onOrderPlaced: ([CommerceCheckoutResult]) -> Void

    @State private var address = ""
    @State private var submitting = false
    @State private var error: String?

    private var groups: [(merchantId: String, businessName: String, lines: [CommerceCartLine])] {
        Dictionary(grouping: cart.values, by: { $0.merchantId })
            .map { (merchantId: $0.key, businessName: $0.value.first!.businessName, lines: $0.value) }
    }
    private var grandTotal: Double { cart.values.reduce(0) { $0 + $1.product.price * Double($1.quantity) } }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left").font(.system(size: 18, weight: .medium)).frame(width: 44, height: 44)
                }
                .accessibilityLabel("Back")
                Text("Your cart").font(IDS.Typography.title).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
            }
            .padding(.horizontal, 8)

            if groups.isEmpty {
                Text("Your cart is empty.").foregroundColor(IDS.Colors.textSecondary).padding(.top, 20)
                Spacer()
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {
                        ForEach(groups, id: \.merchantId) { group in
                            VStack(alignment: .leading, spacing: 6) {
                                Text(group.businessName).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                                ForEach(group.lines, id: \.product.id) { line in
                                    HStack {
                                        Text("\(line.product.name) x\(line.quantity)").font(.subheadline).foregroundColor(IDS.Colors.textPrimary)
                                        Spacer()
                                        Text("\(Int(line.product.price * Double(line.quantity))) RWF").font(.subheadline).foregroundColor(IDS.Colors.textPrimary)
                                    }
                                }
                            }
                            .padding(16)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(IDS.Colors.card)
                            .cornerRadius(IDS.Layout.cardCornerRadius)
                        }
                        VStack(alignment: .leading, spacing: 10) {
                            HStack {
                                Text("Total (\(groups.count) order\(groups.count == 1 ? "" : "s"))").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                                Spacer()
                                Text("\(Int(grandTotal)) RWF").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                            }
                            TextField("Delivery address", text: $address)
                                .padding(12)
                                .background(IDS.Colors.chipBackground)
                                .cornerRadius(12)
                            if let error {
                                Text(error).font(.caption).foregroundColor(.red)
                            }
                        }
                        .padding(16)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(IDS.Colors.card)
                        .cornerRadius(IDS.Layout.cardCornerRadius)
                    }
                    .padding(.horizontal, IDS.Layout.screenHorizontal)
                    .padding(.top, 12)
                }

                Button(action: { Task { await placeOrders() } }) {
                    Text(submitting ? "Placing orders…" : "Place \(groups.count) order\(groups.count == 1 ? "" : "s")")
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
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }

    private func placeOrders() async {
        submitting = true
        error = nil
        defer { submitting = false }
        var results: [CommerceCheckoutResult] = []
        for group in groups {
            do {
                let res = try await NetworkClient.shared.placeOrder(PlaceOrderRequest(
                    merchantId: group.merchantId,
                    items: group.lines.map { OrderItemRequest(productId: $0.product.id, quantity: $0.quantity) },
                    deliveryAddress: address.trimmingCharacters(in: .whitespaces)
                ))
                results.append(CommerceCheckoutResult(merchantId: group.merchantId, businessName: group.businessName, order: res.order, error: nil))
            } catch let NetworkError.httpError(statusCode) {
                results.append(CommerceCheckoutResult(merchantId: group.merchantId, businessName: group.businessName, order: nil, error: TalkScreen.errorMessage(statusCode)))
            } catch {
                results.append(CommerceCheckoutResult(merchantId: group.merchantId, businessName: group.businessName, order: nil, error: "Couldn't reach itunda. Check your connection and try again."))
            }
        }
        onOrderPlaced(results)
    }
}

private struct MultiCartResultsView: View {
    let results: [CommerceCheckoutResult]
    let onDone: () -> Void

    private var successCount: Int { results.filter { $0.order != nil }.count }

    var body: some View {
        VStack(spacing: 16) {
            Spacer()
            Text("\(successCount) of \(results.count) order\(results.count == 1 ? "" : "s") placed")
                .font(IDS.Typography.title).foregroundColor(IDS.Colors.textPrimary)
            VStack(alignment: .leading, spacing: 8) {
                ForEach(results) { r in
                    HStack {
                        Text(r.businessName).font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                        Spacer()
                        if let order = r.order {
                            Text("\(Int(order.totalAmount)) RWF — placed").font(.subheadline).foregroundColor(.green)
                        } else {
                            Text(r.error ?? "Failed").font(.subheadline).foregroundColor(.red)
                        }
                    }
                }
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            Spacer()
            Button(action: onDone) {
                Text(results.contains { $0.order == nil } ? "Back to cart" : "Done")
                    .font(IDS.Typography.bodyBold).foregroundColor(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 16)
                    .background(IDS.Colors.brand).cornerRadius(16)
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.bottom, 40)
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }
}

private let commerceStatusLabel: [String: String] = [
    "PLACED": "Placed",
    "PACKED": "Packed",
    "SHIPPED": "Shipped",
    "DELIVERED": "Delivered",
    "CANCELLED": "Cancelled — refunded",
]

private struct CommerceOrderRow<Action: View>: View {
    let order: OrderDto
    @ViewBuilder let action: () -> Action

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(commerceStatusLabel[order.status] ?? order.status).font(.subheadline).bold().foregroundColor(IDS.Colors.brand)
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

extension CommerceOrderRow where Action == EmptyView {
    init(order: OrderDto) {
        self.order = order
        self.action = { EmptyView() }
    }
}

private struct MyCommerceOrdersView: View {
    @State private var orders: [OrderDto]?
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
                        CommerceOrderRow(order: order) {
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
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}
