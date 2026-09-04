import SwiftUI
import CoreDesignSystem

struct CatalogTab: View {
    @State private var products: [MerchantProductDto]?
    @State private var name = ""
    @State private var price = ""
    @State private var originalPrice = ""
    @State private var imageUrl = ""
    @State private var description = ""
    @State private var stockQuantity = ""
    @State private var error: String?
    @State private var submitting = false
    @State private var stockProduct: MerchantProductDto?
    @State private var stockDraft = ""
    // Real menu-item option groups (item 210) -- see MenuOptionGroupDto's own doc
    // comment. A selected product presents ProductOptionsView as a sheet.
    @State private var optionsProduct: MerchantProductDto?
    // Real bulk/wholesale pricing -- merchant-mfe/Android already have this; this is
    // the first iOS MerchantApp client. A selected product presents PriceTiersView.
    @State private var pricingProduct: MerchantProductDto?
    // Real Coupang WING 상품분석 (product analytics) -- ported from merchant-mfe/
    // Android (2026-09-03). A selected product presents ProductAnalyticsView.
    @State private var analyticsProduct: MerchantProductDto?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Add a product").bold()
                    IdsTextField("Name", text: $name)
                    IdsTextField("Price (RWF)", text: $price, keyboardType: .numberPad)
                    IdsTextField("Original price (optional, for a sale)", text: $originalPrice, keyboardType: .numberPad)
                    IdsTextField("Public image URL (optional)", text: $imageUrl, keyboardType: .URL).textInputAutocapitalization(.never)
                    TextField("Description (optional)", text: $description, axis: .vertical).lineLimit(2...4).padding(12).background(Color(.secondarySystemBackground)).cornerRadius(12)
                    IdsTextField("Stock (optional — blank means unlimited)", text: $stockQuantity, keyboardType: .numberPad)
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
                        // Real copy-voice fix (item 244, round 7): points back to the
                        // real "Add a product" form above, matching Android's
                        // already-shipped wording.
                        Text("No products yet — add your first one above.").foregroundColor(.secondary)
                    } else {
                        let lowStock = products.filter { ($0.stockQuantity ?? Int.max) <= 5 }
                        if !lowStock.isEmpty {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("\(lowStock.count) product\(lowStock.count == 1 ? "" : "s") need stock attention").bold()
                                Text(lowStock.map { "\($0.name) (\($0.stockQuantity == 0 ? "out of stock" : "\($0.stockQuantity!) left"))" }.joined(separator: ", "))
                                    .font(.footnote).foregroundColor(.secondary)
                            }
                            .padding(16)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color.orange.opacity(0.12))
                            .cornerRadius(12)
                        }
                        ForEach(products) { product in
                            HStack {
                                if let imageUrl = product.imageUrl, let url = URL(string: imageUrl) {
                                    AsyncImage(url: url) { image in image.resizable().scaledToFill() } placeholder: { Color.gray.opacity(0.15) }
                                        .frame(width: 44, height: 44).clipShape(RoundedRectangle(cornerRadius: 8))
                                }
                                VStack(alignment: .leading) {
                                    Text(product.name).bold()
                                    Text("\(formattedRWF(product.price)) RWF").font(.footnote)
                                    if let original = product.originalPrice { Text("Was \(formattedRWF(original)) RWF · \(product.discountPercent ?? 0)% off").font(.caption).foregroundColor(.accentColor) }
                                    if let description = product.description { Text(description).font(.caption).foregroundColor(.secondary) }
                                    Text(product.stockQuantity.map { $0 == 0 ? "Out of stock" : "\($0) in stock" } ?? "Unlimited stock")
                                        .font(.caption).foregroundColor(product.stockQuantity == 0 ? .red : .secondary)
                                }
                                Spacer()
                                Button("Adjust stock") {
                                    stockDraft = product.stockQuantity.map(String.init) ?? ""
                                    stockProduct = product
                                }
                                .font(.footnote)
                                Button("Options") { optionsProduct = product }
                                    .font(.footnote)
                                Button("Bulk pricing") { pricingProduct = product }
                                    .font(.footnote)
                                Button("Analytics") { analyticsProduct = product }
                                    .font(.footnote)
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
        .alert("Adjust stock", isPresented: Binding(
            get: { stockProduct != nil },
            set: { if !$0 { stockProduct = nil } }
        )) {
            IdsTextField("Available units (blank = unlimited)", text: $stockDraft, keyboardType: .numberPad)
            Button("Save") {
                guard let product = stockProduct else { return }
                let stock = stockDraft.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty.flatMap(Int.init)
                guard stockDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || (stock != nil && stock! >= 0) else {
                    error = "Stock must be a whole number of zero or more."
                    return
                }
                Task { await updateStock(product.id, stockQuantity: stock) }
            }
            Button("Cancel", role: .cancel) { stockProduct = nil }
        } message: {
            Text("Set available units, or leave the field blank for unlimited availability.")
        }
        .sheet(item: $optionsProduct) { product in
            ProductOptionsView(productId: product.id, productName: product.name)
        }
        .sheet(item: $pricingProduct) { product in
            PriceTiersView(productId: product.id, productName: product.name, regularPrice: product.price)
        }
        .sheet(item: $analyticsProduct) { product in
            ProductAnalyticsView(productId: product.id, productName: product.name)
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
        let previousPrice = originalPrice.isEmpty ? nil : Double(originalPrice)
        guard originalPrice.isEmpty || (previousPrice != nil && previousPrice! > amount) else {
            error = "Original price must be greater than the current price."
            return
        }
        let stock = stockQuantity.isEmpty ? nil : Int(stockQuantity)
        guard stockQuantity.isEmpty || (stock != nil && stock! >= 0) else {
            error = "Stock must be a whole number of zero or more."
            return
        }
        submitting = true
        error = nil
        defer { submitting = false }
        do {
            _ = try await MerchantNetworkClient.shared.addProduct(
                name: name, price: amount,
                imageUrl: imageUrl.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty,
                originalPrice: previousPrice,
                description: description.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty,
                stockQuantity: stock,
            )
            name = ""; price = ""; originalPrice = ""; imageUrl = ""; description = ""; stockQuantity = ""
            await load()
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            self.error = message ?? "Couldn't add this product. Try again."
        } catch {
            self.error = "Couldn't add this product. Try again."
        }
    }

    private func remove(_ productId: String) async {
        _ = try? await MerchantNetworkClient.shared.removeProduct(productId)
        await load()
    }

    private func updateStock(_ productId: String, stockQuantity: Int?) async {
        do {
            _ = try await MerchantNetworkClient.shared.updateProductStock(productId, stockQuantity: stockQuantity)
            stockProduct = nil
            await load()
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            self.error = message ?? "Couldn't update stock. Try again."
        } catch {
            self.error = "Couldn't update stock. Try again."
        }
    }
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}

// Real menu-item option groups (item 210) -- merchant-mfe/Android already have this;
// this is the iOS MerchantApp client. v1 scope matches merchant-mfe's own: required,
// single-select groups only (e.g. "Size": Small/Medium/Large, exactly one choice --
// see MenuOptionService.addOptionGroup's own doc comment on the backend).
private struct ProductOptionsView: View {
    let productId: String
    let productName: String
    @Environment(\.dismiss) private var dismiss

    @State private var groups: [MenuOptionGroupDto]?
    @State private var groupName = ""
    @State private var choiceRows: [(name: String, priceDelta: String)] = [("", "0"), ("", "0")]
    @State private var saving = false
    @State private var removingId: String?
    @State private var error: String?

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("Real option groups -- e.g. \"Size\" with Small/Medium/Large. A buyer picks exactly one choice per group.")
                        .font(.caption).foregroundColor(.secondary)
                    if let error {
                        Text(error).font(.caption).foregroundColor(.red)
                    }
                    if let groups {
                        if groups.isEmpty {
                            Text("No option groups yet.").font(.caption).foregroundColor(.secondary)
                        } else {
                            ForEach(groups) { group in
                                HStack(alignment: .top) {
                                    VStack(alignment: .leading) {
                                        Text(group.name).bold().font(.subheadline)
                                        Text(group.choices.map { $0.priceDelta > 0 ? "\($0.name) (+\(Int($0.priceDelta)) RWF)" : $0.name }.joined(separator: ", "))
                                            .font(.caption).foregroundColor(.secondary)
                                    }
                                    Spacer()
                                    Button(removingId == group.id ? "Removing…" : "Remove") { Task { await remove(group.id) } }
                                        .font(.caption).foregroundColor(.secondary)
                                        .disabled(removingId == group.id)
                                }
                                .padding(12)
                                .background(Color(.secondarySystemBackground))
                                .cornerRadius(10)
                            }
                        }
                    } else {
                        ProgressView()
                    }

                    Text("Add an option group").bold().font(.subheadline)
                    IdsTextField("Group name (e.g. Size)", text: $groupName)
                    ForEach(choiceRows.indices, id: \.self) { i in
                        HStack {
                            IdsTextField("Choice", text: Binding(get: { choiceRows[i].name }, set: { choiceRows[i].name = $0 }))
                            IdsTextField("+RWF", text: Binding(get: { choiceRows[i].priceDelta }, set: { choiceRows[i].priceDelta = $0 }), keyboardType: .numberPad)
                        }
                    }
                    Button("Add another choice") { choiceRows.append(("", "0")) }
                        .font(.caption)
                    Button(action: { Task { await addGroup() } }) {
                        Text(saving ? "Saving…" : "Add option group")
                            .bold().foregroundColor(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(IDS.Colors.brand).cornerRadius(10)
                    }
                    .disabled(saving)
                }
                .padding(16)
            }
            .navigationTitle(productName)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .task { await load() }
    }

    private func load() async {
        groups = (try? await MerchantNetworkClient.shared.getOptionGroups(productId).optionGroups) ?? []
    }

    private func addGroup() async {
        let choices = choiceRows.compactMap { row -> MenuOptionChoiceRequest? in
            let trimmed = row.name.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmed.isEmpty else { return nil }
            return MenuOptionChoiceRequest(name: trimmed, priceDelta: Double(row.priceDelta.trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0)
        }
        guard !groupName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, choices.count >= 2 else {
            error = "Enter a group name and at least 2 named choices."
            return
        }
        saving = true
        error = nil
        defer { saving = false }
        do {
            _ = try await MerchantNetworkClient.shared.addOptionGroup(productId, name: groupName.trimmingCharacters(in: .whitespacesAndNewlines), choices: choices)
            groupName = ""
            choiceRows = [("", "0"), ("", "0")]
            await load()
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            self.error = message ?? "Couldn't add this option group."
        } catch {
            self.error = "Couldn't add this option group."
        }
    }

    private func remove(_ groupId: String) async {
        removingId = groupId
        do {
            try await MerchantNetworkClient.shared.removeOptionGroup(productId, groupId: groupId)
            await load()
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            self.error = message ?? "Couldn't remove this option group."
        } catch {
            self.error = "Couldn't remove this option group."
        }
        removingId = nil
    }
}

/// Real bulk/wholesale pricing -- closes the gap named in Baemin's own real 배민상회
/// B2B supplies marketplace research: the real differentiator between a B2B wholesale
/// listing and a normal retail one is that price genuinely depends on quantity. Up to
/// 3 real tiers, quantity + price fields -- see backend
/// MerchantProductService.setPriceTiers's own doc comment for the real "must actually
/// be a discount" validation this relies on server-side. merchant-mfe/Android already
/// have this; this is the first iOS MerchantApp client.
private struct PriceTiersView: View {
    let productId: String
    let productName: String
    let regularPrice: Double
    @Environment(\.dismiss) private var dismiss

    @State private var tierRows: [(minQuantity: String, unitPrice: String)] = [("", ""), ("", ""), ("", "")]
    @State private var loaded = false
    @State private var saving = false
    @State private var error: String?

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("Real bulk discounts -- e.g. buy 10+, pay less per unit. Leave a row blank to skip it.")
                        .font(.caption).foregroundColor(.secondary)
                    if let error {
                        Text(error).font(.caption).foregroundColor(.red)
                    }
                    if loaded {
                        ForEach(tierRows.indices, id: \.self) { i in
                            HStack {
                                IdsTextField("Min qty", text: Binding(get: { tierRows[i].minQuantity }, set: { tierRows[i].minQuantity = $0 }), keyboardType: .numberPad)
                                IdsTextField("Price each (RWF)", text: Binding(get: { tierRows[i].unitPrice }, set: { tierRows[i].unitPrice = $0 }), keyboardType: .numberPad)
                            }
                        }
                    } else {
                        ProgressView()
                    }
                    Button(action: { Task { await save() } }) {
                        Text(saving ? "Saving…" : "Save bulk pricing")
                            .bold().foregroundColor(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(IDS.Colors.brand).cornerRadius(10)
                    }
                    .disabled(saving || !loaded)
                }
                .padding(16)
            }
            .navigationTitle(productName)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .task { await load() }
    }

    private func load() async {
        if let tiers = try? await MerchantNetworkClient.shared.getPriceTiers(productId).tiers, !tiers.isEmpty {
            var rows = tiers.map { (minQuantity: String($0.minQuantity), unitPrice: String(Int($0.unitPrice))) }
            while rows.count < 3 { rows.append(("", "")) }
            tierRows = Array(rows.prefix(3))
        }
        loaded = true
    }

    private func save() async {
        let tiers = tierRows.compactMap { row -> PriceTierDto? in
            guard let qty = Int(row.minQuantity.trimmingCharacters(in: .whitespaces)),
                  let unitPrice = Double(row.unitPrice.trimmingCharacters(in: .whitespaces)) else { return nil }
            return PriceTierDto(minQuantity: qty, unitPrice: unitPrice)
        }
        saving = true
        error = nil
        defer { saving = false }
        do {
            _ = try await MerchantNetworkClient.shared.setPriceTiers(productId, tiers: tiers)
            dismiss()
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            self.error = message ?? "Couldn't save bulk pricing."
        } catch {
            self.error = "Couldn't save bulk pricing."
        }
    }
}
