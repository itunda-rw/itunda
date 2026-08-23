import SwiftUI
import CoreDesignSystem
import CoreNetwork


// Real post-delivery ratings & reviews (2026-07-18) -- itunda's own self-hosted rating
// system, ported from bank-mfe's own review UI (the template for this iOS version).
struct StarRatingRow: View {
    let value: Int
    let onChange: (Int) -> Void

    var body: some View {
        HStack(spacing: 4) {
            ForEach(1...5, id: \.self) { n in
                Button(action: { onChange(n) }) {
                    Image(systemName: n <= value ? "star.fill" : "star")
                        .foregroundColor(n <= value ? .yellow : IDS.Colors.textTertiary)
                }
                .accessibilityLabel("Rate \(n) star\(n == 1 ? "" : "s")")
            }
        }
    }
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

// Real written-review list + owner-reply display (item 184/185/186) -- bank-mfe (item
// 184) and Android (item 185) already have this; this is the first iOS client. Mirrors
// ProductRatingBadge's own expand-on-tap pattern exactly (ShopScreen.swift, this app's
// Commerce equivalent).
struct RestaurantRatingBadge: View {
    let restaurantId: String
    @State private var rating: EatsRatingResponse?
    @State private var open = false
    @State private var reviews: [EatsReviewDto]?

    var body: some View {
        Group {
            if let rating, rating.count > 0 {
                VStack(alignment: .leading, spacing: 4) {
                    Button(action: toggle) {
                        HStack(spacing: 4) {
                            Image(systemName: "star.fill").font(.caption).foregroundColor(.yellow)
                            Text(String(format: "%.1f (%d)", rating.average ?? 0.0, rating.count))
                                .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                    }
                    if open {
                        if let reviews {
                            if reviews.isEmpty {
                                EmptyStateView("No written reviews yet — be the first to share how it went.")
                            } else {
                                ForEach(reviews, id: \.id) { r in
                                    let stars = String(repeating: "★", count: r.restaurantRating) + String(repeating: "☆", count: 5 - r.restaurantRating)
                                    Text(r.restaurantComment.map { "\(stars) — \($0)" } ?? stars)
                                        .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                                    if let reply = r.ownerReply, !reply.isEmpty {
                                        Text("↳ Restaurant: \(reply)").font(.caption2).foregroundColor(IDS.Colors.textTertiary).padding(.leading, 12)
                                    }
                                }
                            }
                        } else {
                            Text("Loading reviews…").font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                        }
                    }
                }
            }
        }
        .task {
            do {
                rating = try await NetworkClient.shared.getRestaurantRating(restaurantId)
            } catch {
                // Real, non-critical -- a rating fetch failure shouldn't block browsing
                // the menu.
            }
        }
    }

    private func toggle() {
        open.toggle()
        guard open, reviews == nil else { return }
        Task {
            do {
                reviews = try await NetworkClient.shared.getRestaurantReviews(restaurantId).reviews
            } catch {
                reviews = []
            }
        }
    }
}

struct ReviewOrderCard: View {
    let order: EatsOrderDto

    @State private var open = false
    @State private var done = false
    @State private var restaurantRating = 0
    @State private var restaurantComment = ""
    @State private var riderRating = 0
    @State private var riderComment = ""
    @State private var submitting = false
    @State private var error: String?

    var body: some View {
        if done {
            Text("Thanks for your review!").font(.caption).foregroundColor(IDS.Colors.textSecondary)
        } else if !open {
            Button(action: { open = true }) {
                Text("Rate this order").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    .background(IDS.Colors.chipBackground).cornerRadius(12)
            }
        } else {
            VStack(alignment: .leading, spacing: 10) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Restaurant").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    StarRatingRow(value: restaurantRating) { restaurantRating = $0 }
                    TextField("How was the food? (optional)", text: $restaurantComment)
                        .padding(10).background(IDS.Colors.chipBackground).cornerRadius(10)
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text("Rider").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    StarRatingRow(value: riderRating) { riderRating = $0 }
                    TextField("How was the delivery? (optional)", text: $riderComment)
                        .padding(10).background(IDS.Colors.chipBackground).cornerRadius(10)
                }
                if let error {
                    Text(error).font(.caption).foregroundColor(.red)
                }
                HStack(spacing: 10) {
                    Button(action: { open = false }) {
                        Text("Cancel").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(IDS.Colors.chipBackground).cornerRadius(12)
                    }
                    Button(action: { Task { await submit() } }) {
                        Text(submitting ? "Submitting…" : "Submit review").font(.subheadline).bold().foregroundColor(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(submitting ? IDS.Colors.textTertiary : IDS.Colors.brand).cornerRadius(12)
                    }
                    .disabled(submitting)
                }
            }
        }
    }

    private func submit() async {
        guard restaurantRating > 0, riderRating > 0 else {
            error = "Rate both the restaurant and the rider."
            return
        }
        submitting = true
        error = nil
        defer { submitting = false }
        do {
            _ = try await NetworkClient.shared.submitEatsReview(
                orderId: order.id,
                restaurantRating: restaurantRating,
                restaurantComment: restaurantComment.trimmingCharacters(in: .whitespaces).isEmpty ? nil : restaurantComment,
                riderRating: riderRating,
                riderComment: riderComment.trimmingCharacters(in: .whitespaces).isEmpty ? nil : riderComment
            )
            done = true
        } catch let NetworkError.httpError(statusCode) {
            // A 409 here is the real ORDER_ALREADY_REVIEWED case in practice -- this
            // card only ever renders for a real DELIVERED order, so the sibling "not
            // yet delivered" 409 can't actually occur through this UI path.
            if statusCode == 409 {
                done = true
            } else {
                error = TalkScreen.errorMessage(statusCode)
            }
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

struct RestaurantMenuView: View {
    let restaurant: ShoppingMerchantDto
    let menu: [MerchantProductDto]?
    @Binding var cart: [String: EatsCartLine]
    let onBack: () -> Void
    let onCheckout: () -> Void

    // Real menu-options selection UI (2026-07-21, v1: required single-select only) --
    // ports bank-mfe's own MenuView 1:1. Only one item's option panel is expanded at a
    // time, matching this file's own established "inline-card-replaces-trigger"
    // convention (no modal-overlay pattern exists anywhere in this app).
    @State private var expandedProductId: String?
    @State private var pendingChoices: [String: String] = [:]

    private var cartCount: Int { cart.values.reduce(0) { $0 + $1.quantity } }

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

    private func menuItemCard(_ item: MerchantProductDto) -> some View {
        let groups = item.optionGroups ?? []
        let hasOptions = !groups.isEmpty
        let simpleKey = eatsCartKey(item.id, [])
        let simpleQty = hasOptions ? 0 : (cart[simpleKey]?.quantity ?? 0)
        let isExpanded = expandedProductId == item.id
        let allGroupsChosen = groups.allSatisfy { pendingChoices[$0.id] != nil }

        return VStack(alignment: .leading, spacing: 0) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.name).font(IDS.Typography.bodyMedium).foregroundColor(IDS.Colors.textPrimary)
                    Text("\(Int(item.price)) RWF\(hasOptions ? " · options required" : "")").font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
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
                        VStack(alignment: .leading, spacing: 6) {
                            Text("\(group.name)").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                + Text(" · choose 1").font(.caption).foregroundColor(IDS.Colors.textTertiary)
                            VStack(alignment: .leading, spacing: 8) {
                                ForEach(group.choices) { choice in
                                    Button(action: { pendingChoices[group.id] = choice.id }) {
                                        HStack(spacing: 8) {
                                            Image(systemName: pendingChoices[group.id] == choice.id ? "largecircle.fill.circle" : "circle")
                                                .foregroundColor(pendingChoices[group.id] == choice.id ? IDS.Colors.brand : IDS.Colors.textTertiary)
                                            Text(choice.name + (choice.priceDelta > 0 ? " (+\(Int(choice.priceDelta)) RWF)" : ""))
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
        .cornerRadius(IDS.Layout.cardCornerRadius)
    }

    // For a no-option item only -- the original single-stepper interaction, completely
    // unchanged for the overwhelming majority of menu items that have no option groups.
    private func setSimpleQty(_ productId: String, _ qty: Int) {
        let key = eatsCartKey(productId, [])
        cart[key] = EatsCartLine(productId: productId, quantity: max(0, qty), choiceIds: [])
    }

    private func toggleExpand(_ productId: String) {
        pendingChoices = [:]
        expandedProductId = (expandedProductId == productId) ? nil : productId
    }

    private func addConfiguredToCart(_ item: MerchantProductDto) {
        let groups = item.optionGroups ?? []
        let choiceIds = groups.compactMap { pendingChoices[$0.id] }
        guard choiceIds.count == groups.count else { return } // one real required choice per group, enforced client-side too
        let key = eatsCartKey(item.id, choiceIds)
        cart[key] = EatsCartLine(productId: item.id, quantity: (cart[key]?.quantity ?? 0) + 1, choiceIds: choiceIds)
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

