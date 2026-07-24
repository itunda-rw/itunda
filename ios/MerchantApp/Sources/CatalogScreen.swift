import SwiftUI
import CoreDesignSystem

struct CatalogTab: View {
    @State private var products: [MerchantProductDto]?
    @State private var name = ""
    @State private var price = ""
    @State private var error: String?
    @State private var submitting = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Add a product").bold()
                    TextField("Name", text: $name).padding(12).background(Color(.secondarySystemBackground)).cornerRadius(12)
                    TextField("Price (RWF)", text: $price).keyboardType(.numberPad).padding(12).background(Color(.secondarySystemBackground)).cornerRadius(12)
                    if let error {
                        Text(error).foregroundColor(.red).font(.footnote)
                    }
                    Button(action: { Task { await addProduct() } }) {
                        Text(submitting ? "Adding…" : "Add")
                            .bold().foregroundColor(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(IDS.Colors.brand).cornerRadius(10)
                    }
                    .disabled(submitting)
                }
                .padding(16)
                .background(Color(.secondarySystemBackground))
                .cornerRadius(12)

                if let products {
                    if products.isEmpty {
                        Text("No products yet.").foregroundColor(.secondary)
                    } else {
                        ForEach(products) { product in
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(product.name).bold()
                                    Text("\(formattedRWF(product.price)) RWF").font(.footnote)
                                }
                                Spacer()
                                Button("Remove") { Task { await remove(product.id) } }
                                    .foregroundColor(.secondary)
                            }
                            .padding(16)
                            .background(Color(.secondarySystemBackground))
                            .cornerRadius(12)
                        }
                    }
                } else {
                    ProgressView()
                }
            }
            .padding(16)
        }
        .task { await load() }
    }

    private func load() async {
        products = (try? await MerchantNetworkClient.shared.getProductCatalog().products) ?? []
    }

    private func addProduct() async {
        guard let amount = Double(price), amount > 0, !name.isEmpty else {
            error = "Enter a name and a real price."
            return
        }
        submitting = true
        error = nil
        defer { submitting = false }
        do {
            _ = try await MerchantNetworkClient.shared.addProduct(name: name, price: amount)
            name = ""; price = ""
            await load()
        } catch {
            self.error = "Couldn't add this product. Try again."
        }
    }

    private func remove(_ productId: String) async {
        _ = try? await MerchantNetworkClient.shared.removeProduct(productId)
        await load()
    }
}
