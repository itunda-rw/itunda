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

private struct CommerceShopContent: View {
    @State private var merchants: [ShoppingMerchantDto]?
    @State private var error: String?
    @State private var selectedMerchant: ShoppingMerchantDto?
    @State private var products: [MerchantProductDto]?
    @State private var cart: [String: Int] = [:]
    @State private var showCheckout = false
    @State private var confirmedOrder: OrderDto?

    var body: some View {
        Group {
            if let confirmedOrder {
                OrderConfirmationView(order: confirmedOrder, onDone: {
                    self.confirmedOrder = nil
                    self.selectedMerchant = nil
                    self.products = nil
                    self.cart = [:]
                    self.showCheckout = false
                })
            } else if let merchant = selectedMerchant {
                if showCheckout {
                    CheckoutView(
                        merchant: merchant,
                        cart: cart,
                        products: products ?? [],
                        onBack: { showCheckout = false },
                        onOrderPlaced: { confirmedOrder = $0 }
                    )
                } else {
                    MerchantDetailView(
                        merchant: merchant,
                        products: products,
                        cart: $cart,
                        onBack: { selectedMerchant = nil },
                        onCheckout: { showCheckout = true }
                    )
                }
            } else {
                browseBody
            }
        }
        .task { if merchants == nil { await loadMerchants() } }
    }

    private var browseBody: some View {
        ScrollView {
            VStack(spacing: IDS.Layout.cardGap) {
                TdsPlainTopBar(title: "Shop")
                if let error {
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
            .padding(.bottom, IDS.Layout.sectionSpacing)
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
        cart = [:]
        products = nil
        do {
            let res = try await NetworkClient.shared.getMerchantProducts(merchantId: merchant.merchantId)
            products = res.products
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

private struct MerchantDetailView: View {
    let merchant: ShoppingMerchantDto
    let products: [MerchantProductDto]?
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
                                    qtyButton("minus") { if (cart[product.id] ?? 0) > 0 { cart[product.id]! -= 1 } }
                                    Text("\(cart[product.id] ?? 0)").frame(width: 24).font(.subheadline).bold()
                                    qtyButton("plus") { cart[product.id] = (cart[product.id] ?? 0) + 1 }
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

private struct CheckoutView: View {
    let merchant: ShoppingMerchantDto
    let cart: [String: Int]
    let products: [MerchantProductDto]
    let onBack: () -> Void
    let onOrderPlaced: (OrderDto) -> Void

    @State private var address = ""
    @State private var submitting = false
    @State private var error: String?
    private let idempotencyKey = UUID().uuidString

    private var lines: [(MerchantProductDto, Int)] {
        cart.compactMap { productId, qty in
            guard qty > 0, let product = products.first(where: { $0.id == productId }) else { return nil }
            return (product, qty)
        }
    }
    private var total: Double { lines.reduce(0) { $0 + $1.0.price * Double($1.1) } }

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
                    ForEach(lines, id: \.0.id) { product, qty in
                        HStack {
                            Text("\(product.name) x\(qty)").foregroundColor(IDS.Colors.textPrimary)
                            Spacer()
                            Text("\(Int(product.price * Double(qty))) RWF").foregroundColor(IDS.Colors.textPrimary)
                        }
                    }
                    Divider()
                    HStack {
                        Text("Total").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                        Spacer()
                        Text("\(Int(total)) RWF").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                    }
                    TextField("Delivery address", text: $address)
                        .padding(12)
                        .background(IDS.Colors.chipBackground)
                        .cornerRadius(12)
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
            let res = try await NetworkClient.shared.placeOrder(PlaceOrderRequest(
                merchantId: merchant.merchantId,
                items: lines.map { OrderItemRequest(productId: $0.0.id, quantity: $0.1) },
                deliveryAddress: address.trimmingCharacters(in: .whitespaces)
            ))
            onOrderPlaced(res.order)
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

private struct OrderConfirmationView: View {
    let order: OrderDto
    let onDone: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Spacer()
            Text("Order placed").font(IDS.Typography.title).foregroundColor(IDS.Colors.textPrimary)
            Text("\(Int(order.totalAmount)) RWF").font(IDS.Typography.largeAmount).foregroundColor(IDS.Colors.textPrimary)
            Text("Delivering to \(order.deliveryAddress)").font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
            Spacer()
            Button(action: onDone) {
                Text("Done").font(IDS.Typography.bodyBold).foregroundColor(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 16)
                    .background(IDS.Colors.brand).cornerRadius(16)
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.bottom, 40)
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }
}
