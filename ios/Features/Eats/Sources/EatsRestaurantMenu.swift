import SwiftUI
import CoreDesignSystem
import CoreNetwork

// ShopBestSellerBadge/colorFromHex are real, trivial App-only helpers this file
// already depended on before Eats moved into its own Feature module -- local
// duplicate copies, matching the same "small App-only utility gets its own local
// per-file copy in a new Feature module" convention FeatureMaps' own colorFromHex
// duplicate and TalkScreen.errorMessage's own per-file copy already established
// (see Project.swift's own doc comment), rather than promoting either into a
// shared Core module for a single small use.
private struct ShopBestSellerBadge: View {
    var body: some View {
        Text("Best seller")
            .font(.system(size: 11, weight: .bold))
            .foregroundColor(.white)
            .padding(.horizontal, 6).padding(.vertical, 2)
            .background(IDS.Colors.brand)
            .clipShape(RoundedRectangle(cornerRadius: 4))
    }
}

private func colorFromHex(_ hex: String) -> Color {
    var sanitized = hex.trimmingCharacters(in: .whitespacesAndNewlines)
    if sanitized.hasPrefix("#") { sanitized.removeFirst() }
    guard sanitized.count == 6, let value = UInt64(sanitized, radix: 16) else {
        return Color(red: 0.961, green: 0.651, blue: 0.137) // the default star-yellow, same fallback RideScreenView.swift's own copy uses
    }
    return Color(
        red: Double((value >> 16) & 0xFF) / 255,
        green: Double((value >> 8) & 0xFF) / 255,
        blue: Double(value & 0xFF) / 255
    )
}

// Real restaurant-photo thumbnail (2026-07-21) -- photoUrl is a merchant-supplied
// external URL (see backend Merchant.kt's own doc comment: no upload/storage layer
// exists in this backend, same "bring your own URL" convention ShopScreen's own
// ProductImageThumb already established for product images). AsyncImage (native
// SwiftUI, no third-party dependency) handles the nil/broken-URL case itself via its
// placeholder closure -- same fallback icon for "no photo set" and "photo failed to
// load," both real, valid states.
struct RestaurantPhotoThumb: View {
    let imageUrl: String?
    var side: CGFloat = 44

    var body: some View {
        Group {
            if let imageUrl, let url = URL(string: imageUrl) {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().scaledToFill()
                    default:
                        placeholder
                    }
                }
            } else {
                placeholder
            }
        }
        .frame(width: side, height: side)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .background(IDS.Colors.chipBackground)
    }

    private var placeholder: some View {
        ZStack {
            IDS.Colors.chipBackground
            Image(systemName: "fork.knife").foregroundColor(IDS.Colors.brand)
        }
    }
}

struct RestaurantMenuView: View {
    let restaurant: ShoppingMerchantDto
    let menu: [MerchantProductDto]?
    @Binding var cart: [String: EatsCartLine]
    let onBack: () -> Void
    let onCheckout: () -> Void

    // Real menu-options selection UI (2026-07-21, v2 2026-08-28: real optional +
    // multi-select groups, ports bank-mfe/Android's own just-built rework 1:1) -- ports
    // bank-mfe's own MenuView 1:1. Only one item's option panel is expanded at a time,
    // matching this file's own established "inline-card-replaces-trigger" convention
    // (no modal-overlay pattern exists anywhere in this app). pendingChoices reworked
    // from a single choiceId per group to an array, since a multiSelect group can now
    // hold more than one.
    @State private var expandedProductId: String?
    @State private var pendingChoices: [String: [String]] = [:]
    // Real "frequently ordered together" cross-sell key (itunda Eats redesign,
    // 2026-08-28) -- the most recently added cart line's productId, same "keyed off the
    // last cart addition" precedent web/Android's own just-built rail already uses.
    @State private var lastAddedProductId: String?

    private var cartCount: Int { cart.values.reduce(0) { $0 + $1.quantity } }
    private var cartSubtotal: Double {
        cart.values.reduce(0.0) { total, line in
            guard let item = menuItem(line.productId) else { return total }
            return total + eatsLineUnitPrice(item, line.choiceIds) * Double(line.quantity)
        }
    }
    private var cartOriginalSubtotal: Double {
        cart.values.reduce(0.0) { total, line in
            guard let item = menuItem(line.productId) else { return total }
            let delta = (item.optionGroups ?? []).flatMap { $0.choices }.filter { line.choiceIds.contains($0.id) }.reduce(0.0) { $0 + $1.priceDelta }
            return total + ((item.originalPrice ?? item.price) + delta) * Double(line.quantity)
        }
    }

    private func menuItem(_ productId: String) -> MerchantProductDto? {
        (menu ?? []).first(where: { $0.id == productId })
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    IDS.Icons.back(size: 18, relativeTo: .title3).frame(width: 44, height: 44)
                }
                .accessibilityLabel("Back")
                Text(restaurant.businessName).font(IDS.Typography.title).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
            }
            .padding(.horizontal, 8)
            RestaurantRatingBadge(restaurantId: restaurant.merchantId)
                .padding(.horizontal, IDS.Layout.screenHorizontal)
                .padding(.top, 4)

            ScrollView {
                VStack(spacing: 10) {
                    if let menu {
                        if menu.isEmpty {
                            EmptyStateView("This restaurant hasn't added menu items yet — check back soon.")
                        }
                        ForEach(menu) { item in
                            menuItemCard(item)
                        }
                    } else {
                        ProgressView().padding(.top, 20)
                    }
                }
                .padding(.horizontal, IDS.Layout.screenHorizontal)
                .padding(.top, 12)
            }

            if cartCount > 0 {
                // Real "frequently ordered together" cross-sell (itunda Eats redesign,
                // 2026-08-28) -- see EatsFrequentlyOrderedWith's own doc comment. Only
                // adds a no-option quick-add line, same real, deliberate scope
                // limitation web/Android's own just-built rail already documents (this
                // rail carries no option-group configuration).
                if let lastAddedProductId {
                    EatsFrequentlyOrderedWith(productId: lastAddedProductId, onAdd: { item in
                        let key = eatsCartKey(item.id, [])
                        cart[key] = EatsCartLine(productId: item.id, quantity: (cart[key]?.quantity ?? 0) + 1, choiceIds: [])
                    })
                    .padding(.horizontal, IDS.Layout.screenHorizontal)
                }
                Button(action: onCheckout) {
                    HStack {
                        Image(systemName: "cart.fill")
                        VStack(alignment: .leading, spacing: 0) {
                            Text("Checkout (\(cartCount) item\(cartCount == 1 ? "" : "s"))").font(IDS.Typography.bodyBold)
                            // Real cart-bar subtotal (itunda Eats redesign, 2026-08-28) --
                            // a pure client-side sum of already-fetched cart line prices,
                            // same real strikethrough-original-price treatment web/Android
                            // already show. bank-mfe/Android already have this.
                            HStack(spacing: 6) {
                                Text("\(formatAmount(Int(cartSubtotal))) RWF").font(.caption).bold()
                                if cartOriginalSubtotal > cartSubtotal {
                                    Text("\(formatAmount(Int(cartOriginalSubtotal))) RWF").font(.caption2).strikethrough().foregroundColor(.white.opacity(0.7))
                                }
                            }
                        }
                        Spacer()
                    }
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14).padding(.horizontal, 16)
                    .background(IDS.Colors.brand)
                    .cornerRadius(16)
                }
                .padding(IDS.Layout.screenHorizontal)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }

    private func menuItemCard(_ item: MerchantProductDto) -> some View {
        let groups = item.optionGroups ?? []
        let hasOptions = !groups.isEmpty
        let simpleKey = eatsCartKey(item.id, [])
        let simpleQty = hasOptions ? 0 : (cart[simpleKey]?.quantity ?? 0)
        let isExpanded = expandedProductId == item.id
        // Real optional/multi-select option-group support (itunda Eats redesign,
        // 2026-08-28) -- the backend already supported 4 real required×multiSelect
        // combinations, but this UI only ever rendered required-single-select; an
        // optional group now needs no selection at all, matching web/Android's own
        // just-built rework.
        let allGroupsChosen = groups.allSatisfy { !$0.required || !(pendingChoices[$0.id] ?? []).isEmpty }

        return VStack(alignment: .leading, spacing: 0) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(item.name).font(IDS.Typography.bodyMedium).foregroundColor(IDS.Colors.textPrimary)
                        if item.discountPercent != nil, let discount = item.discountPercent, discount > 0 {
                            Text("-\(discount)%").font(.caption2).bold().foregroundColor(.white)
                                .padding(.horizontal, 5).padding(.vertical, 1).background(Color.red).cornerRadius(4)
                        } else if item.isBestSeller {
                            ShopBestSellerBadge()
                        }
                    }
                    // Real discount price block (itunda Eats redesign, 2026-08-28) --
                    // originalPrice/discountPercent already flow through the shared
                    // MerchantProduct-backed menu endpoint; this UI never rendered them.
                    HStack(spacing: 6) {
                        Text("\(formatAmount(Int(item.price))) RWF").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                        if let originalPrice = item.originalPrice, originalPrice > item.price {
                            Text("\(formatAmount(Int(originalPrice))) RWF").font(.caption).strikethrough().foregroundColor(IDS.Colors.textTertiary)
                        }
                        if hasOptions {
                            Text("· options available").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                    }
                }
                Spacer()
                if hasOptions {
                    Button(action: { toggleExpand(item.id) }) {
                        Text(isExpanded ? "Close" : "Choose options")
                            .font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                            .padding(.horizontal, 12).padding(.vertical, 8)
                            .background(IDS.Colors.chipBackground).cornerRadius(10)
                    }
                } else {
                    HStack(spacing: 12) {
                        qtyButton("minus") { if simpleQty > 0 { setSimpleQty(item.id, simpleQty - 1) } }
                        Text("\(simpleQty)").frame(width: 24).font(.subheadline).bold()
                        qtyButton("plus") { setSimpleQty(item.id, simpleQty + 1) }
                    }
                }
            }

            if hasOptions && isExpanded {
                VStack(alignment: .leading, spacing: 12) {
                    ForEach(groups) { group in
                        // Real groupHint (itunda Eats redesign, 2026-08-28) -- matches
                        // web/Android's own just-built 4-variant copy exactly.
                        let groupHint = group.required
                            ? (group.multiSelect ? "· choose any" : "· choose 1")
                            : (group.multiSelect ? "· optional, choose any" : "· optional")
                        VStack(alignment: .leading, spacing: 6) {
                            Text("\(group.name)").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                + Text(" \(groupHint)").font(.caption).foregroundColor(IDS.Colors.textTertiary)
                            VStack(alignment: .leading, spacing: 8) {
                                ForEach(group.choices) { choice in
                                    let selected = (pendingChoices[group.id] ?? []).contains(choice.id)
                                    Button(action: { toggleChoice(group, choice, selected: selected) }) {
                                        HStack(spacing: 8) {
                                            Image(systemName: group.multiSelect
                                                ? (selected ? "checkmark.square.fill" : "square")
                                                : (selected ? "largecircle.fill.circle" : "circle"))
                                                .foregroundColor(selected ? IDS.Colors.brand : IDS.Colors.textTertiary)
                                            Text(choice.name + (choice.priceDelta > 0 ? " (+\(formatAmount(Int(choice.priceDelta))) RWF)" : ""))
                                                .font(.caption)
                                                .foregroundColor(IDS.Colors.textPrimary)
                                        }
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                    }
                    Button(action: { addConfiguredToCart(item) }) {
                        Text("Add to cart")
                            .font(IDS.Typography.bodyBold).foregroundColor(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(allGroupsChosen ? IDS.Colors.brand : IDS.Colors.textTertiary)
                            .cornerRadius(12)
                    }
                    .disabled(!allGroupsChosen)
                }
                .padding(.top, 14)
            }
        }
        .padding(16)
        .background(IDS.Colors.card)
        .cornerRadius(IDS.Layout.cardCornerRadius).idsCardBorder(cornerRadius: IDS.Layout.cardCornerRadius)
    }

    // For a no-option item only -- the original single-stepper interaction, completely
    // unchanged for the overwhelming majority of menu items that have no option groups.
    private func setSimpleQty(_ productId: String, _ qty: Int) {
        let key = eatsCartKey(productId, [])
        cart[key] = EatsCartLine(productId: productId, quantity: max(0, qty), choiceIds: [])
        if qty > 0 { lastAddedProductId = productId }
    }

    private func toggleExpand(_ productId: String) {
        pendingChoices = [:]
        expandedProductId = (expandedProductId == productId) ? nil : productId
    }

    // Real toggle logic (itunda Eats redesign, 2026-08-28, ports web/Android's own
    // just-built rework 1:1) -- a multiSelect group adds/removes from the array; a
    // single-select group replaces the array, or clears it entirely on re-tap IF the
    // group is optional (a required single-select group can't be cleared back to
    // nothing, same as before this rework).
    private func toggleChoice(_ group: MenuOptionGroupDto, _ choice: MenuOptionChoiceDto, selected: Bool) {
        if group.multiSelect {
            var current = pendingChoices[group.id] ?? []
            if selected { current.removeAll { $0 == choice.id } } else { current.append(choice.id) }
            pendingChoices[group.id] = current
        } else if selected, !group.required {
            pendingChoices[group.id] = []
        } else {
            pendingChoices[group.id] = [choice.id]
        }
    }

    private func addConfiguredToCart(_ item: MerchantProductDto) {
        let groups = item.optionGroups ?? []
        guard groups.allSatisfy({ !$0.required || !(pendingChoices[$0.id] ?? []).isEmpty }) else { return }
        let choiceIds = groups.flatMap { pendingChoices[$0.id] ?? [] }
        let key = eatsCartKey(item.id, choiceIds)
        cart[key] = EatsCartLine(productId: item.id, quantity: (cart[key]?.quantity ?? 0) + 1, choiceIds: choiceIds)
        lastAddedProductId = item.id
        pendingChoices = [:]
        expandedProductId = nil
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

/// Real self-hosted address-search autocomplete (2026-07-18) -- itunda's own Nominatim
/// geocoder, not a third-party Maps API. Mirrors bank-mfe's AddressAutocomplete/Android's
/// AddressAutocompleteField: debounced real search-as-you-type, a real suggestion list,
/// and on selection the real resolved coordinates are handed back to the caller so they
/// can be submitted explicitly (taking priority over EatsOrderService's own automatic
/// single-best-match fallback). Typing without selecting still places a real order via
/// that fallback.
struct AddressAutocompleteField: View {
    let address: String
    let onAddressChange: (String) -> Void
    let onSuggestionSelected: (AddressSuggestionDto) -> Void

    @State private var suggestions: [AddressSuggestionDto] = []
    @State private var searchTask: Task<Void, Never>?
    // Real Uber/Kakao T-style saved-places quick-select (2026-08-23) -- same real gap
    // already closed for ride booking (RideScreenView's RidePassengerContent) and
    // Android's AddressAutocompleteField: itunda's own "map bookmarks" feature was
    // never surfaced here either, despite a delivery address being an even more
    // universal need than a ride destination. No new backend work -- the same
    // existing GET /api/v1/maps/bookmarks this field's own search suggestions already
    // sit alongside.
    @State private var bookmarks: [MapBookmarkDto] = []

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
            } else if address.isEmpty && !bookmarks.isEmpty {
                VStack(alignment: .leading, spacing: 0) {
                    Text("Saved places").font(.caption2).bold().foregroundColor(IDS.Colors.textSecondary)
                        .padding(.horizontal, 12).padding(.top, 8)
                    ForEach(bookmarks) { bookmark in
                        Button(action: { selectSuggestion(AddressSuggestionDto(displayName: bookmark.displayName, latitude: bookmark.latitude, longitude: bookmark.longitude)) }) {
                            HStack(spacing: 8) {
                                Circle().fill(colorFromHex(bookmark.color)).frame(width: 8, height: 8)
                                Text(bookmark.displayName).font(.caption).foregroundColor(IDS.Colors.textPrimary)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(12)
                        }
                    }
                }
                .background(IDS.Colors.chipBackground)
                .cornerRadius(12)
            }
        }
        .task { await loadBookmarks() }
    }

    private func loadBookmarks() async {
        bookmarks = (try? await NetworkClient.shared.getMyMapBookmarks().bookmarks) ?? bookmarks
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

