import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real 배민오더-style table/QR in-store ordering (item 162) -- see
// DineInOrderController.kt's own doc comment on the backend. Same real checkout-mode
// toggle Android's own EatsCheckoutMode already established: reuses the exact same
// cart/menu-option selection as delivery, only the checkout step itself diverges (a
// table number replaces the address, no delivery fee, settles straight to the
// restaurant's account at placement).
enum EatsCheckoutMode { case delivery, pickup, dineIn }

struct EatsCheckoutView: View {
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
                    IDS.Icons.back(size: 18, relativeTo: .title3).frame(width: 44, height: 44)
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
                        Text("\(formatAmount(Int(subtotal))) RWF").foregroundColor(IDS.Colors.textPrimary)
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
        } catch let NetworkError.httpErrorWithMessage(statusCode, message) {
            // Real fix (2026-09-04): placeEatsOrder/placeDineInOrder now carry the
            // backend's own specific message (MinOrderAmountNotMet, MenuItemSoldOut,
            // the new pickup+promotion discount-stacking rejection, etc.) instead of
            // falling through to the same generic per-status-code bucket for every
            // 4xx -- see NetworkClient.postEatsOrder's own doc comment.
            error = message ?? TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

struct DineInOrderConfirmationView: View {
    let order: DineInOrderDto
    let onDone: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            Spacer()
            Image(systemName: "checkmark.seal.fill").font(IDS.scaledFont(size: 44, weight: .regular, relativeTo: .largeTitle)).foregroundColor(.green)
            Text("Order placed").font(IDS.Typography.title).foregroundColor(IDS.Colors.textPrimary)
            Text("\(formatAmount(Int(order.totalAmount))) RWF").font(IDS.Typography.largeAmount).foregroundColor(IDS.Colors.textPrimary)
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

struct EatsOrderConfirmationView: View {
    let order: EatsOrderDto
    let onDone: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Spacer()
            Text("Order placed").font(IDS.Typography.title).foregroundColor(IDS.Colors.textPrimary)
            Text("\(formatAmount(Int(order.totalAmount))) RWF").font(IDS.Typography.largeAmount).foregroundColor(IDS.Colors.textPrimary)
            // Real Baemin-style tiered order-amount promotion (2026-08-16) -- see
            // EatsPromotionCalculator's own doc comment on the backend.
            if order.promotionDiscount > 0 {
                Text("\(formatAmount(Int(order.promotionDiscount))) RWF off, on us").font(.caption).foregroundColor(.green)
            }
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

