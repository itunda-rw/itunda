import SwiftUI
import CoreDesignSystem

private struct CartLine: Identifiable {
    let product: MerchantProductDto
    var quantity: Int
    var id: String { product.id }
}

/// Real cash-register/POS UI, ported field-for-field from merchant-mfe's own
/// PosScreen.tsx and Android's own merchantapp -- checkout reuses the already-real
/// generateQr/chargeCard flows unmodified, just with a cart-derived amount/
/// description instead of a single typed-in amount.
struct PosTab: View {
    @State private var products: [MerchantProductDto]?
    @State private var cart: [CartLine] = []
    @State private var checkingOut = false

    private var total: Double { cart.reduce(0) { $0 + $1.product.price * Double($1.quantity) } }
    private var description: String { cart.map { "\($0.quantity)x \($0.product.name)" }.joined(separator: ", ") }

    var body: some View {
        Group {
            if checkingOut {
                CheckoutView(
                    total: total,
                    description: description,
                    onDone: { cart = []; checkingOut = false },
                    onCancel: { checkingOut = false }
                )
            } else {
                HStack(alignment: .top) {
                    ScrollView {
                        if let products {
                            if products.isEmpty {
                                Text("No products yet — add some in the Catalog tab first.").foregroundColor(.secondary).padding(16)
                            } else {
                                LazyVGrid(columns: [GridItem(.adaptive(minimum: 130))], spacing: 8) {
                                    ForEach(products) { product in
                                        Button(action: { addToCart(product) }) {
                                            VStack(alignment: .leading) {
                                                Text(product.name).bold().foregroundColor(.primary)
                                                Text("\(formattedRWF(product.price)) RWF").font(.footnote).foregroundColor(.secondary)
                                                Text(stockLabel(product))
                                                    .font(.footnote)
                                                    .foregroundColor(product.stockQuantity == 0 ? .red : .secondary)
                                            }
                                            .padding(14)
                                            .frame(maxWidth: .infinity, alignment: .leading)
                                            .background(Color(.secondarySystemBackground))
                                            .cornerRadius(12)
                                        }
                                        .disabled(product.stockQuantity == 0)
                                    }
                                }
                                .padding(16)
                            }
                        } else {
                            SkeletonBlock(height: 64).padding(16)
                        }
                    }
                    .frame(maxWidth: .infinity)

                    VStack(alignment: .leading, spacing: 8) {
                        Text("Cart").bold()
                        if cart.isEmpty {
                            Text("Tap a product to add it.").font(.footnote).foregroundColor(.secondary)
                        } else {
                            ForEach(cart) { line in
                                HStack {
                                    Text("\(line.quantity)x \(line.product.name)").font(.footnote)
                                    Spacer()
                                    Text("\(formattedRWF(line.product.price * Double(line.quantity))) RWF").font(.footnote)
                                }
                            }
                        }
                        Divider()
                        HStack {
                            Text("Total").bold()
                            Spacer()
                            Text("\(formattedRWF(total)) RWF").bold()
                        }
                        IdsButton(
                            text: "Checkout",
                            isEnabled: !cart.isEmpty,
                            variant: .filled,
                            size: .medium,
                            action: { checkingOut = true }
                        )
                    }
                    .padding(16)
                    .frame(width: 220)
                    .background(Color(.secondarySystemBackground))
                    .cornerRadius(12)
                    .padding(.trailing, 16)
                }
            }
        }
        .task {
            products = (try? await MerchantNetworkClient.shared.getProductCatalog().products) ?? []
        }
    }

    private func addToCart(_ product: MerchantProductDto) {
        if let index = cart.firstIndex(where: { $0.product.id == product.id }) {
            let nextQuantity = cart[index].quantity + 1
            guard product.stockQuantity == nil || nextQuantity <= product.stockQuantity! else { return }
            cart[index].quantity = nextQuantity
        } else {
            cart.append(CartLine(product: product, quantity: 1))
        }
    }

    private func stockLabel(_ product: MerchantProductDto) -> String {
        guard let stockQuantity = product.stockQuantity else { return "Unlimited stock" }
        return stockQuantity == 0 ? "Out of stock" : "\(stockQuantity) in stock"
    }
}

private struct CheckoutView: View {
    let total: Double
    let description: String
    let onDone: () -> Void
    let onCancel: () -> Void

    @State private var mode = "QR"

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Checkout").font(.title3).bold()
            Text("\(formattedRWF(total)) RWF").font(.title2).bold()
            Text(description).font(.footnote).foregroundColor(.secondary)

            IdsTabs(
                ["QR code", "Card"],
                selectedIndex: Binding(
                    get: { mode == "CARD" ? 1 : 0 },
                    set: { mode = $0 == 1 ? "CARD" : "QR" }
                )
            )

            if mode == "QR" {
                QrCheckoutView(amount: total, description: description, onDone: onDone)
            } else {
                CardCheckoutView(amount: total, description: description, onDone: onDone)
            }

            IdsButton(
                text: "Back to cart",
                variant: .tinted,
                size: .medium,
                action: onCancel
            )

            Spacer()
        }
        .padding(16)
    }
}

private struct QrCheckoutView: View {
    let amount: Double
    let description: String
    let onDone: () -> Void

    @State private var qrContent: String?
    @State private var error: String?

    var body: some View {
        VStack(alignment: .center, spacing: 12) {
            if let qrContent, let image = generateQrImage(content: qrContent) {
                image.interpolation(.none).resizable().frame(width: 220, height: 220)
                IdsButton(text: "Done — new sale", size: .medium, action: onDone)
            } else {
                if let error {
                    IdsErrorText(error)
                }
                IdsButton(
                        text: "Retry",
                        variant: .tinted,
                        size: .medium,
                        fullWidth: false,
                        action: { Task { await generate() } }
                    )
            }
        }
        .frame(maxWidth: .infinity)
        .task { await generate() }
    }

    private func generate() async {
        error = nil
        qrContent = nil
        do {
            let intent = try await MerchantNetworkClient.shared.generateQr(amount: amount, description: description).paymentIntent
            qrContent = paymentIntentQrPayload(intent.id)
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            self.error = message ?? "Could not generate a QR code."
        } catch {
            self.error = "Could not generate a QR code."
        }
    }
}

private struct CardCheckoutView: View {
    let amount: Double
    let description: String
    let onDone: () -> Void

    @State private var cardNumber = ""
    @State private var expiryMonth = ""
    @State private var expiryYear = ""
    @State private var cvc = ""
    @State private var result: String?
    @State private var error: String?
    @State private var submitting = false
    // Real device step-up (2026-07-28 port, item 97) -- a real 403 DEVICE_NOT_VERIFIED
    // (this device hasn't been step-up-verified yet) gets its own case, not a generic error.
    @State private var needsDeviceVerification = false

    var body: some View {
        if needsDeviceVerification {
            ZStack {
                Color.black.opacity(0.3).ignoresSafeArea()
                // Real fix (2026-08-10) -- see PayrollScreen.swift's own identical fix
                // for the full account. Card fields are unchanged while the dialog is
                // up, so re-reading them via charge() on retry is the same charge the
                // merchant already confirmed.
                DeviceStepUpDialog(
                    onVerified: { Task { await charge() } },
                    onCancel: { needsDeviceVerification = false }
                )
            }
        } else if let result {
            VStack(spacing: 12) {
                Text("Card charged — •••• \(result)").bold()
                IdsButton(text: "Done — new sale", size: .medium, action: onDone)
            }
        } else {
            VStack(spacing: 10) {
                IdsTextField("Card number", text: $cardNumber, keyboardType: .numberPad)
                HStack {
                    IdsTextField("MM", text: $expiryMonth, keyboardType: .numberPad)
                    IdsTextField("YYYY", text: $expiryYear, keyboardType: .numberPad)
                    IdsTextField("CVC", text: $cvc, keyboardType: .numberPad)
                }
                if let error {
                    Text(error).foregroundColor(IDS.Colors.danger).font(.footnote)
                }
                IdsButton(
                    text: "Charge \(formattedRWF(amount)) RWF",
                    isLoading: submitting,
                    size: .medium,
                    action: { Task { await charge() } }
                )
            }
        }
    }

    private func charge() async {
        guard let month = Int(expiryMonth), let year = Int(expiryYear), !cardNumber.isEmpty, !cvc.isEmpty else {
            error = "Fill in every card field."
            return
        }
        submitting = true
        error = nil
        needsDeviceVerification = false
        defer { submitting = false }
        do {
            let charge = try await MerchantNetworkClient.shared.chargeCard(
                ChargeCardRequest(amount: amount, description: description, cardNumber: cardNumber.replacingOccurrences(of: " ", with: ""), expiryMonth: month, expiryYear: year, cvc: cvc)
            )
            result = charge.cardLast4
        } catch NetworkError.deviceNotVerified {
            needsDeviceVerification = true
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            self.error = message ?? "Could not charge this card."
        } catch {
            self.error = "Could not charge this card."
        }
    }
}
