import SwiftUI
import UIKit
import CoreDesignSystem
import CoreNetwork
import CoreLocation


struct ProductDetailView: View {
    let merchant: ShoppingMerchantDto
    let product: MerchantProductDto
    @Binding var cart: [String: CommerceCartLine]
    let onBack: () -> Void
    let onViewCart: () -> Void
    var favorited: Bool = false
    var favoriteBusy: Bool = false
    var onToggleFavorite: () -> Void = {}

    private var totalItems: Int { cart.values.reduce(0) { $0 + $1.quantity } }
    private var key: String { "\(merchant.merchantId):\(product.id)" }
    private var qty: Int { cart[key]?.quantity ?? 0 }
    private func setQty(_ quantity: Int) {
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
                VStack(alignment: .leading, spacing: 6) {
                    ZStack(alignment: .topTrailing) {
                        HStack {
                            Spacer()
                            ProductImageThumb(imageUrl: product.imageUrl, side: 220)
                            Spacer()
                        }
                        // Real Shop product wishlist button on the detail page
                        // (2026-07-24) -- Chloe Youn's Coupang case study
                        // (docs/DESIGN_REFERENCES.md Section 5) names wishlist as
                        // available directly alongside add-to-cart, not buried behind
                        // a sub-menu.
                        Button(action: onToggleFavorite) {
                            Image(systemName: favorited ? "heart.fill" : "heart")
                                .foregroundColor(favorited ? .red : IDS.Colors.textSecondary)
                                .padding(8)
                        }
                        .accessibilityLabel(favorited ? "Remove from favorites" : "Add to favorites")
                        .disabled(favoriteBusy)
                    }
                    Spacer().frame(height: 16)
                    Text(product.name).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                    Spacer().frame(height: 6)
                    ProductPriceRow(product: product)
                    Spacer().frame(height: 6)
                    ProductRatingBadge(productId: product.id)
                    if let description = product.description, !description.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        Spacer().frame(height: 12)
                        Text(description).font(.footnote).foregroundColor(IDS.Colors.textSecondary)
                    }
                    Spacer().frame(height: 16)
                    ProductInquirySection(productId: product.id)
                    Spacer().frame(height: 12)
                    SubscribeAndSaveButton(merchantId: merchant.merchantId, productId: product.id)
                    Spacer().frame(height: 20)
                    HStack(spacing: 10) {
                        Spacer()
                        qtyButton("minus") { setQty(qty - 1) }
                        Text("\(qty)").frame(width: 36).font(.headline).foregroundColor(IDS.Colors.textPrimary)
                        qtyButton("plus") { setQty(qty + 1) }
                        Spacer()
                    }
                    Spacer().frame(height: 16)
                    Button(action: { setQty(max(1, qty)) }) {
                        Text(qty > 0 ? "Update cart" : "Add to cart")
                            .font(IDS.Typography.bodyBold)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 16)
                            .background(IDS.Colors.brand)
                            .cornerRadius(16)
                    }
                }
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.top, 12)

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
        // Every real call site passes either "plus" or "minus" -- see this function's own
        // call sites -- so the label can be derived directly from the symbol instead of
        // threading a separate label param through every caller.
        .accessibilityLabel(symbol == "plus" ? "Increase quantity" : "Decrease quantity")
    }
}

/// Real per-seller order splitting -- each merchant group becomes its own real,
/// independent placeOrder() call. Sequential, not concurrent: these are real
/// money-moving calls against the same buyer account, and a clear one-at-a-time
/// result list is more honest than a swallowed batch result. A failure on one
/// merchant's order does not block or roll back any other.
struct MultiCartView: View {
    @Binding var cart: [String: CommerceCartLine]
    let onBack: () -> Void
    let onOrderPlaced: ([CommerceCheckoutResult]) -> Void

    @State private var address = ""
    @State private var submitting = false
    @State private var error: String?
    // Real device binding step-up (2026-07-21) -- every order in this batch shares
    // the same device/session, so hitting this once means every remaining order
    // would fail identically -- the loop below stops at the first one rather than
    // collecting N duplicate failures, same fix already applied to bank-mfe's
    // MultiCartView/Android's MultiCartView.
    @State private var needsDeviceVerification = false
    // Real fix (2026-08-10): found live-testing bank-mfe's identical checkout screen
    // -- retrying after device verification here used to just clear the flag with no
    // retry at all, and even a naive "just call placeOrders() again" retry would have
    // RE-PLACED every order that already succeeded before the failure (a real
    // duplicate-order bug). `groups` below is a Dictionary(grouping:) computed
    // property with no guaranteed stable iteration order, so resuming by index into a
    // freshly recomputed `groups` on retry could point at the wrong merchant entirely
    // -- these snapshot the ordered list once, on the first attempt, and reuse it.
    @State private var checkoutGroups: [(merchantId: String, businessName: String, lines: [CommerceCartLine])]?
    @State private var checkoutResults: [CommerceCheckoutResult] = []
    @State private var checkoutResumeIndex = 0

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
            DeviceStepUpHost(
                visible: needsDeviceVerification,
                onDismiss: { needsDeviceVerification = false },
                onVerified: { await placeOrders() }
            )
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }

    private func placeOrders() async {
        submitting = true
        error = nil
        needsDeviceVerification = false
        defer { submitting = false }
        let orderedGroups = checkoutGroups ?? groups
        checkoutGroups = orderedGroups
        for i in checkoutResumeIndex..<orderedGroups.count {
            let group = orderedGroups[i]
            do {
                let res = try await NetworkClient.shared.placeOrder(PlaceOrderRequest(
                    merchantId: group.merchantId,
                    items: group.lines.map { OrderItemRequest(productId: $0.product.id, quantity: $0.quantity) },
                    deliveryAddress: address.trimmingCharacters(in: .whitespaces)
                ))
                checkoutResults.append(CommerceCheckoutResult(merchantId: group.merchantId, businessName: group.businessName, order: res.order, error: nil))
            } catch NetworkError.deviceNotVerified {
                checkoutResumeIndex = i
                needsDeviceVerification = true
                return
            } catch let NetworkError.httpError(statusCode) {
                checkoutResults.append(CommerceCheckoutResult(merchantId: group.merchantId, businessName: group.businessName, order: nil, error: TalkScreen.errorMessage(statusCode)))
            } catch {
                checkoutResults.append(CommerceCheckoutResult(merchantId: group.merchantId, businessName: group.businessName, order: nil, error: "Couldn't reach itunda. Check your connection and try again."))
            }
        }
        onOrderPlaced(checkoutResults)
    }
}

struct MultiCartResultsView: View {
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

