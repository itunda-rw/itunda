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
                                            }
                                            .padding(14)
                                            .frame(maxWidth: .infinity, alignment: .leading)
                                            .background(Color(.secondarySystemBackground))
                                            .cornerRadius(12)
                                        }
                                    }
                                }
                                .padding(16)
                            }
                        } else {
                            ProgressView().padding(16)
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
                        Button(action: { checkingOut = true }) {
                            Text("Checkout").bold().foregroundColor(.white)
                                .frame(maxWidth: .infinity).padding(.vertical, 10)
                                .background(cart.isEmpty ? Color.gray : IDS.Colors.brand).cornerRadius(10)
                        }
                        .disabled(cart.isEmpty)
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
            cart[index].quantity += 1
        } else {
            cart.append(CartLine(product: product, quantity: 1))
        }
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

            Picker("", selection: $mode) {
                Text("QR code").tag("QR")
                Text("Card").tag("CARD")
            }
            .pickerStyle(.segmented)

            if mode == "QR" {
                QrCheckoutView(amount: total, description: description, onDone: onDone)
            } else {
                CardCheckoutView(amount: total, description: description, onDone: onDone)
            }

            Button("Back to cart", action: onCancel)
                .frame(maxWidth: .infinity, alignment: .center)

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
                Button(action: onDone) {
                    Text("Done — new sale").bold().foregroundColor(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(IDS.Colors.brand).cornerRadius(10)
                }
            } else {
                if let error {
                    Text(error).foregroundColor(.red).font(.footnote)
                }
                Button("Retry") { Task { await generate() } }
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

    var body: some View {
        if let result {
            VStack(spacing: 12) {
                Text("Card charged — •••• \(result)").bold()
                Button(action: onDone) {
                    Text("Done — new sale").bold().foregroundColor(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(IDS.Colors.brand).cornerRadius(10)
                }
            }
        } else {
            VStack(spacing: 10) {
                TextField("Card number", text: $cardNumber).keyboardType(.numberPad).padding(10).background(Color(.secondarySystemBackground)).cornerRadius(10)
                HStack {
                    TextField("MM", text: $expiryMonth).keyboardType(.numberPad).padding(10).background(Color(.secondarySystemBackground)).cornerRadius(10)
                    TextField("YYYY", text: $expiryYear).keyboardType(.numberPad).padding(10).background(Color(.secondarySystemBackground)).cornerRadius(10)
                    TextField("CVC", text: $cvc).keyboardType(.numberPad).padding(10).background(Color(.secondarySystemBackground)).cornerRadius(10)
                }
                if let error {
                    Text(error).foregroundColor(.red).font(.footnote)
                }
                Button(action: { Task { await charge() } }) {
                    Text(submitting ? "Charging…" : "Charge \(formattedRWF(amount)) RWF")
                        .bold().foregroundColor(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(IDS.Colors.brand).cornerRadius(10)
                }
                .disabled(submitting)
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
        defer { submitting = false }
        do {
            let charge = try await MerchantNetworkClient.shared.chargeCard(
                ChargeCardRequest(amount: amount, description: description, cardNumber: cardNumber.replacingOccurrences(of: " ", with: ""), expiryMonth: month, expiryYear: year, cvc: cvc)
            )
            result = charge.cardLast4
        } catch {
            self.error = "Could not charge this card."
        }
    }
}
