import SwiftUI
import CoreDesignSystem
import CoreNetwork

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

private enum CommerceView { case browse, orders, wishlist }

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
    @State private var categories: [String] = []
    @State private var selectedCategory: String?
    @State private var searchInput: String = ""
    @State private var filterTask: Task<Void, Never>?
    @State private var error: String?
    @State private var selectedMerchant: ShoppingMerchantDto?
    @State private var products: [MerchantProductDto]?
    @State private var selectedProduct: MerchantProductDto?
    @State private var cart: [String: CommerceCartLine] = [:]
    @State private var showCart = false
    @State private var results: [CommerceCheckoutResult]?

    // Real Shop product wishlist (2026-07-24) -- lifted here same as Marketplace's own
    // favoriteIds (HoodScreen.swift), so the heart on a product card (grid or detail)
    // stays correct whichever screen toggled it. See NetworkClient's FavoriteProductDto
    // doc comment.
    @State private var favoriteProductIds: Set<String> = []
    @State private var favoritingProductId: String?

    // Real Naver Smart Store-style "알림받기" (follow a store) -- first iOS client
    // for this feature (item 117, found via a content-grep sweep: bank-mfe has it,
    // Android/iOS didn't; Android ported the same day). Lifted here same as
    // favoriteProductIds above.
    @State private var followedMerchantIds: Set<String> = []
    @State private var followBusyMerchantId: String?

    // Real "Deals" rail (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 5
    // recommendation #8. Every entry is a real merchant-set discount, never a
    // fabricated promo -- see backend MerchantProductRepository.findDeals's own doc
    // comment.
    @State private var deals: [DealProductDto]?

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
            } else if let merchant = selectedMerchant, let product = selectedProduct {
                ProductDetailView(
                    merchant: merchant,
                    product: product,
                    cart: $cart,
                    onBack: { selectedProduct = nil },
                    onViewCart: { selectedProduct = nil; showCart = true },
                    favorited: favoriteProductIds.contains(product.id),
                    favoriteBusy: favoritingProductId == product.id,
                    onToggleFavorite: { Task { await toggleProductFavorite(product.id) } }
                )
            } else if let merchant = selectedMerchant {
                MerchantDetailView(
                    merchant: merchant,
                    products: products,
                    cart: $cart,
                    onBack: { selectedMerchant = nil },
                    onViewCart: { showCart = true },
                    onOpenProduct: { selectedProduct = $0 },
                    favoriteProductIds: favoriteProductIds,
                    favoritingProductId: favoritingProductId,
                    onToggleFavorite: { productId in Task { await toggleProductFavorite(productId) } },
                    following: followedMerchantIds.contains(merchant.merchantId),
                    followBusy: followBusyMerchantId == merchant.merchantId,
                    onToggleFollow: { Task { await toggleFollow(merchant.merchantId) } }
                )
            } else {
                browseBody
            }
        }
        .task {
            if merchants == nil { await loadMerchants() }
            if categories.isEmpty {
                do { categories = try await NetworkClient.shared.getMerchantCategories().categories } catch {}
            }
            await loadFavoriteProductIds()
            await loadFollowedMerchantIds()
            if deals == nil {
                do { deals = try await NetworkClient.shared.getShopDeals().products } catch {}
            }
        }
    }

    private func loadFollowedMerchantIds() async {
        do {
            let res = try await NetworkClient.shared.getMyFollowedMerchants()
            followedMerchantIds = Set(res.follows.map { $0.merchantId })
        } catch {
            // Best-effort -- see loadFavoriteProductIds's own doc comment above.
        }
    }

    private func toggleFollow(_ merchantId: String) async {
        followBusyMerchantId = merchantId
        defer { followBusyMerchantId = nil }
        do {
            if followedMerchantIds.contains(merchantId) {
                _ = try await NetworkClient.shared.unfollowMerchant(merchantId: merchantId)
                followedMerchantIds.remove(merchantId)
            } else {
                _ = try await NetworkClient.shared.followMerchant(merchantId: merchantId)
                followedMerchantIds.insert(merchantId)
            }
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func loadFavoriteProductIds() async {
        do {
            let res = try await NetworkClient.shared.getMyFavoriteProducts()
            favoriteProductIds = Set(res.favorites.map { $0.productId })
        } catch {
            // Best-effort -- hearts just won't render as filled if this fails.
        }
    }

    private func toggleProductFavorite(_ productId: String) async {
        favoritingProductId = productId
        defer { favoritingProductId = nil }
        do {
            if favoriteProductIds.contains(productId) {
                _ = try await NetworkClient.shared.removeProductFavorite(productId)
                favoriteProductIds.remove(productId)
            } else {
                _ = try await NetworkClient.shared.addProductFavorite(productId)
                favoriteProductIds.insert(productId)
            }
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    // Same debounced category/search filter as Eats' OrderFoodContent -- see
    // SearchAndCategoryChips's own doc comment for why this is now shared.
    private func scheduleFilterReload() {
        filterTask?.cancel()
        filterTask = Task {
            try? await Task.sleep(nanoseconds: 300_000_000)
            guard !Task.isCancelled else { return }
            await loadMerchants()
        }
    }

    private func selectCategory(_ category: String?) {
        selectedCategory = (category == selectedCategory) ? nil : category
        scheduleFilterReload()
    }

    private var browseBody: some View {
        ZStack(alignment: .bottom) {
            ScrollView {
                VStack(spacing: IDS.Layout.cardGap) {
                    // No IdsPlainTopBar("Shop") here (2026-07-24), same fix as
                    // HoodScreen's own "Hood" title -- this screen's own Shop/Eats
                    // picker one level up already establishes where the user is.
                    Picker("", selection: $view) {
                        Text("Merchants").tag(CommerceView.browse)
                        Text("My orders").tag(CommerceView.orders)
                        Text("♡ Wishlist").tag(CommerceView.wishlist)
                    }
                    .pickerStyle(.segmented)

                    if view == .orders {
                        MyCommerceOrdersView()
                    } else if view == .wishlist {
                        ProductWishlistView(onRemoved: { Task { await loadFavoriteProductIds() } })
                    } else {
                        // Real "Deals" rail (2026-07-25) -- only shown on the
                        // unfiltered landing state, same "merchandising above the raw
                        // list, hidden once the user starts filtering" discipline a
                        // real Coupang/Naver home surface follows. Tapping a deal
                        // jumps straight to that real merchant via the existing
                        // search-by-name flow.
                        if selectedCategory == nil, searchInput.trimmingCharacters(in: .whitespaces).isEmpty, let deals, !deals.isEmpty {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("🔥 Deals").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(spacing: 10) {
                                        ForEach(deals) { d in
                                            VStack(alignment: .leading, spacing: 6) {
                                                ProductImageThumb(imageUrl: d.imageUrl, side: 96)
                                                Text(d.name).font(.caption).bold().foregroundColor(IDS.Colors.textPrimary).lineLimit(2)
                                                if let discountPercent = d.discountPercent, discountPercent > 0 {
                                                    Text("\(discountPercent)% off").font(.caption2).bold().foregroundColor(.red)
                                                }
                                                Text("\(Int(d.price)) RWF").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                            }
                                            .frame(width: 120, alignment: .leading)
                                            .padding(10)
                                            .background(IDS.Colors.card)
                                            .cornerRadius(IDS.Layout.cardCornerRadius)
                                            .onTapGesture { searchInput = d.merchantName; scheduleFilterReload() }
                                        }
                                    }
                                }
                            }
                        }
                        SearchAndCategoryChips(
                            searchText: searchInput,
                            onSearchChange: { searchInput = $0; scheduleFilterReload() },
                            placeholder: "Search merchants",
                            categories: categories,
                            selectedCategory: selectedCategory,
                            onSelectCategory: selectCategory
                        )

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
                            Text(selectedCategory != nil || !searchInput.trimmingCharacters(in: .whitespaces).isEmpty ? "No merchants match your search." : "No stores registered yet.")
                                .foregroundColor(IDS.Colors.textSecondary)
                        } else {
                        ForEach(merchants!) { merchant in
                            Button(action: { Task { await openMerchant(merchant) } }) {
                                HStack(spacing: 14) {
                                    // Real photo-forward store thumb (2026-07-24), same
                                    // pattern already shipped for EatsScreen's own
                                    // RestaurantPhotoThumb -- ShoppingMerchantDto.photoUrl
                                    // is the same field both screens share, previously only
                                    // rendered on the Eats side.
                                    StorePhotoThumb(imageUrl: merchant.photoUrl)
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
            let q = searchInput.trimmingCharacters(in: .whitespaces)
            let res = try await NetworkClient.shared.getShoppingMerchants(category: selectedCategory, q: q.isEmpty ? nil : q)
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

private struct StorePhotoThumb: View {
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
            Image(systemName: "storefront.fill").foregroundColor(IDS.Colors.brand)
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
    let onOpenProduct: (MerchantProductDto) -> Void
    var favoriteProductIds: Set<String> = []
    var favoritingProductId: String?
    var onToggleFavorite: (String) -> Void = { _ in }
    var following: Bool = false
    var followBusy: Bool = false
    var onToggleFollow: () -> Void = {}

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
                // Real Naver Smart Store-style "알림받기" follow toggle -- first iOS
                // client for this feature (item 117, found via a content-grep sweep:
                // bank-mfe has it, Android/iOS didn't; Android ported the same day).
                Button(action: onToggleFollow) {
                    Text(following ? "Following" : "Follow")
                        .font(.caption).bold()
                        .foregroundColor(following ? IDS.Colors.textPrimary : .white)
                        .padding(.horizontal, 14).padding(.vertical, 8)
                        .background(following ? IDS.Colors.chipBackground : IDS.Colors.brand)
                        .cornerRadius(10)
                }
                .disabled(followBusy)
            }
            .padding(.horizontal, 8)

            ScrollView {
                if let products {
                    if products.isEmpty {
                        Text("No products yet.").foregroundColor(IDS.Colors.textSecondary).padding(.top, 20)
                    } else {
                        // Real 2-column image-led grid (2026-07-21), replacing the
                        // previous single-column text-only row -- closes
                        // docs/DESIGN_REFERENCES.md Section 5 recommendation #5
                        // (Chloe Youn's Coupang case study: real cards are image-led).
                        LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                            ForEach(products) { product in
                                VStack(alignment: .leading, spacing: 6) {
                                    // Real tap-through to the new product-detail screen
                                    // (2026-07-21) -- see ProductDetailView's own doc
                                    // comment. Only image/name/price is tappable so the
                                    // qty stepper below stays independently tappable.
                                    // Real Shop product wishlist heart (2026-07-24) -- a
                                    // sibling overlay button, not nested inside the
                                    // tap-through Button below (SwiftUI doesn't route
                                    // nested-Button taps reliably), same top-trailing
                                    // placement as Marketplace's ListingCard heart. Closes
                                    // docs/DESIGN_REFERENCES.md Section 5 recommendation #3.
                                    ZStack(alignment: .topTrailing) {
                                        Button(action: { onOpenProduct(product) }) {
                                            VStack(alignment: .leading, spacing: 6) {
                                                ProductImageThumb(imageUrl: product.imageUrl, side: 96)
                                                Text(product.name).font(IDS.Typography.bodyMedium).foregroundColor(IDS.Colors.textPrimary).lineLimit(2)
                                                ProductPriceRow(product: product)
                                            }
                                        }
                                        .buttonStyle(.plain)
                                        Button(action: { onToggleFavorite(product.id) }) {
                                            Image(systemName: favoriteProductIds.contains(product.id) ? "heart.fill" : "heart")
                                                .foregroundColor(favoriteProductIds.contains(product.id) ? .red : .white)
                                                .padding(4)
                                        }
                                        .disabled(favoritingProductId == product.id)
                                    }
                                    ProductRatingBadge(productId: product.id)
                                    HStack(spacing: 10) {
                                        Spacer()
                                        qtyButton("minus") { setQty(product, qty(product.id) - 1) }
                                        Text("\(qty(product.id))").frame(width: 24).font(.subheadline).bold()
                                        qtyButton("plus") { setQty(product, qty(product.id) + 1) }
                                        Spacer()
                                    }
                                }
                                .padding(12)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(IDS.Colors.card)
                                .cornerRadius(IDS.Layout.cardCornerRadius)
                            }
                        }
                    }
                } else {
                    ProgressView().padding(.top, 20)
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
    }
}

/// Real product-detail screen (2026-07-21), mirroring Android's
/// ProductDetailScreen in features/shop/impl/ShopScreen.kt: full-size image,
/// name, price row, rating badge, description, quantity stepper, and an
/// add/update-cart action -- reached by tapping a product card in
/// MerchantDetailView's grid (see that grid's own doc comment).
private struct ProductDetailView: View {
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
    // Real device binding step-up (2026-07-21) -- every order in this batch shares
    // the same device/session, so hitting this once means every remaining order
    // would fail identically -- the loop below stops at the first one rather than
    // collecting N duplicate failures, same fix already applied to bank-mfe's
    // MultiCartView/Android's MultiCartView.
    @State private var needsDeviceVerification = false

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
                onVerified: { needsDeviceVerification = false }
            )
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }

    private func placeOrders() async {
        submitting = true
        error = nil
        needsDeviceVerification = false
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
            } catch NetworkError.deviceNotVerified {
                needsDeviceVerification = true
                return
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

// Real Shop product wishlist view (2026-07-24) -- iOS port of bank-mfe's
// ProductCatalogView wishlist tab, mirroring HoodScreen's own ListingWishlistView
// field-for-field. Closes docs/DESIGN_REFERENCES.md Section 5 recommendation #3.
private struct ProductWishlistView: View {
    let onRemoved: () -> Void

    @State private var favorites: [FavoriteProductDto]?
    @State private var error: String?
    @State private var removingId: String?

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
            } else if favorites == nil {
                ProgressView().frame(maxWidth: .infinity, minHeight: 120)
            } else if favorites!.isEmpty {
                Text("No saved products yet -- tap ♡ on any product to save it here.")
                    .foregroundColor(IDS.Colors.textSecondary)
            } else {
                ForEach(favorites!) { f in
                    HStack(spacing: 12) {
                        ProductImageThumb(imageUrl: f.imageUrl, side: 48)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(f.name).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                            Text("\(f.businessName) · \(Int(f.price)) RWF").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                        Spacer()
                        Button(action: { Task { await remove(f.productId) } }) {
                            Text(removingId == f.productId ? "Removing…" : "Remove")
                                .font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                .padding(.horizontal, 12).padding(.vertical, 8)
                                .background(IDS.Colors.chipBackground).cornerRadius(10)
                        }
                        .disabled(removingId == f.productId)
                    }
                    .padding(16)
                    .background(IDS.Colors.card)
                    .cornerRadius(IDS.Layout.cardCornerRadius)
                }
            }
        }
        .task { await load() }
    }

    private func load() async {
        do {
            let res = try await NetworkClient.shared.getMyFavoriteProducts()
            favorites = res.favorites
            error = nil
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func remove(_ productId: String) async {
        removingId = productId
        defer { removingId = nil }
        do {
            _ = try await NetworkClient.shared.removeProductFavorite(productId)
            favorites = favorites?.filter { $0.productId != productId }
            onRemoved()
        } catch {
            self.error = "Couldn't remove this item. Check your connection and try again."
        }
    }
}

private struct MyCommerceOrdersView: View {
    @State private var orders: [OrderDto]?
    @State private var error: String?
    @State private var cancellingId: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            MyReturnRequestsView()
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
                                } else if order.status == "DELIVERED" {
                                    VStack(alignment: .leading, spacing: 8) {
                                        OrderItemReviews(order: order)
                                        ReturnExchangeAction(orderId: order.id)
                                    }
                                }
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

// Real post-delivery product reviews (2026-07-20), mirroring EatsScreen's own
// RestaurantRatingBadge/ReviewOrderCard pattern -- see ProductReviewService's own doc
// comment for the full backend account. One real review per real delivered line item.
// StarRatingRow is duplicated here rather than shared, matching EatsScreen.swift's own
// `private` (file-scoped) declaration -- each screen file in this codebase is self-contained.
private struct StarRatingRow: View {
    let value: Int
    let onChange: (Int) -> Void

    var body: some View {
        HStack(spacing: 4) {
            ForEach(1...5, id: \.self) { n in
                Button(action: { onChange(n) }) {
                    Image(systemName: n <= value ? "star.fill" : "star")
                        .foregroundColor(n <= value ? .yellow : IDS.Colors.textTertiary)
                }
            }
        }
    }
}

// Real product-image thumbnail (2026-07-21) -- imageUrl is a merchant-supplied external
// URL (see backend MerchantProduct.kt's own doc comment: no upload/storage layer exists
// in this backend, so this is a real "bring your own URL" v1, not a fake pipeline).
// AsyncImage (native SwiftUI, no third-party dependency) handles the nil/broken-URL case
// itself via its placeholder closure -- same fallback icon for "no image set" and "image
// failed to load," both real, valid states.
private struct ProductImageThumb: View {
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
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .background(IDS.Colors.chipBackground)
    }

    private var placeholder: some View {
        ZStack {
            IDS.Colors.chipBackground
            Image(systemName: "bag").foregroundColor(IDS.Colors.brand).font(.system(size: side / 2.5))
        }
    }
}

// Real discount-price display (2026-07-21) -- Baymard Institute's own placement
// research (docs/DESIGN_REFERENCES.md Section 5): the discount % must sit immediately
// next to the struck-through original price. discountPercent is always server-computed
// (see backend doc comment), never trusted from the client -- purely a rendering of
// numbers the server already validated.
private struct ProductPriceRow: View {
    let product: MerchantProductDto

    var body: some View {
        if let originalPrice = product.originalPrice, let discountPercent = product.discountPercent, discountPercent > 0 {
            VStack(alignment: .leading, spacing: 1) {
                HStack(spacing: 4) {
                    Text("\(discountPercent)%").font(.subheadline).bold().foregroundColor(.red)
                    Text("\(Int(product.price)) RWF").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                }
                Text("\(Int(originalPrice)) RWF").font(.caption2).foregroundColor(IDS.Colors.textSecondary).strikethrough()
            }
        } else {
            Text("\(Int(product.price)) RWF").font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
        }
    }
}

private struct ProductRatingBadge: View {
    let productId: String
    @State private var rating: ProductRatingResponse?
    @State private var open = false
    @State private var reviews: [ProductReviewDto]?

    var body: some View {
        Group {
            if let rating, rating.count > 0 {
                VStack(alignment: .leading, spacing: 4) {
                    Button(action: toggle) {
                        HStack(spacing: 4) {
                            Image(systemName: "star.fill").font(.caption2).foregroundColor(.yellow)
                            Text(String(format: "%.1f (%d)", rating.average ?? 0.0, rating.count))
                                .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                        }
                    }
                    if open {
                        if let reviews {
                            if reviews.isEmpty {
                                Text("No written reviews yet.").font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                            } else {
                                ForEach(reviews, id: \.id) { r in
                                    let stars = String(repeating: "★", count: r.rating) + String(repeating: "☆", count: 5 - r.rating)
                                    Text(r.comment.map { "\(stars) — \($0)" } ?? stars)
                                        .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
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
                rating = try await NetworkClient.shared.getProductRating(productId)
            } catch {
                // Real, non-critical -- a rating fetch failure shouldn't block browsing the catalog.
            }
        }
    }

    private func toggle() {
        open.toggle()
        guard open, reviews == nil else { return }
        Task {
            do {
                reviews = try await NetworkClient.shared.getProductReviews(productId).reviews
            } catch {
                reviews = []
            }
        }
    }
}

private struct ProductReviewRow: View {
    let item: OrderItemDto

    @State private var open = false
    @State private var done = false
    @State private var rating = 0
    @State private var comment = ""
    @State private var submitting = false
    @State private var error: String?

    var body: some View {
        if done {
            Text("\(item.productName): thanks for your review!").font(.caption).foregroundColor(IDS.Colors.textSecondary)
        } else if !open {
            Button(action: { open = true }) {
                Text("Rate \(item.productName)").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    .background(IDS.Colors.chipBackground).cornerRadius(12)
            }
        } else {
            VStack(alignment: .leading, spacing: 8) {
                Text(item.productName).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                StarRatingRow(value: rating) { rating = $0 }
                TextField("How was it? (optional)", text: $comment)
                    .padding(10).background(IDS.Colors.chipBackground).cornerRadius(10)
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
        guard rating > 0 else {
            error = "Pick a star rating."
            return
        }
        submitting = true
        error = nil
        defer { submitting = false }
        do {
            _ = try await NetworkClient.shared.submitProductReview(
                orderItemId: item.id,
                rating: rating,
                comment: comment.trimmingCharacters(in: .whitespaces).isEmpty ? nil : comment
            )
            done = true
        } catch let NetworkError.httpError(statusCode) {
            // A 409 here is the real PRODUCT_ALREADY_REVIEWED case in practice -- this
            // row only ever renders for a real DELIVERED order.
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

private struct OrderItemReviews: View {
    let order: OrderDto
    @State private var items: [OrderItemDto]?

    var body: some View {
        Group {
            if let items, !items.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(items) { item in ProductReviewRow(item: item) }
                }
            }
        }
        .task {
            do {
                let res = try await NetworkClient.shared.getOrder(order.id)
                items = res.items
            } catch {
                // Real, non-critical -- if item fetch fails, the order row itself still renders fine.
            }
        }
    }
}

// Real Coupang-style post-delivery Return & Exchange request (반품/교환 신청) (item 166/175)
// -- see OrderReturnService's own doc comment for the full account: a real 7-day window
// from delivery, an approved RETURN triggers a real refund via reversed ledger legs, an
// approved EXCHANGE moves no money. Merchant-side approve/reject queue has zero iOS
// client anywhere (Shop has no merchant order-management screen on this platform at all,
// same gap as Android) -- a real, separate, not-yet-started gap; this is the buyer-side
// request form only, mirroring bank-mfe's (item 166) and Android's (item 174) own shape.
private struct ReturnExchangeAction: View {
    let orderId: String

    @State private var open = false
    @State private var done = false
    @State private var type = "RETURN"
    @State private var reasonCode = orderReturnReasonCodes[0]
    @State private var note = ""
    @State private var submitting = false
    @State private var error: String?

    var body: some View {
        if done {
            Text("Return/exchange requested -- the seller will review it.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
        } else if !open {
            Button(action: { open = true }) {
                Text("Return or exchange").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    .background(IDS.Colors.chipBackground).cornerRadius(12)
            }
        } else {
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 8) {
                    ForEach([("RETURN", "Return"), ("EXCHANGE", "Exchange")], id: \.0) { value, label in
                        Button(action: { type = value }) {
                            Text(label).font(.caption).bold().foregroundColor(type == value ? .white : IDS.Colors.textPrimary)
                                .padding(.horizontal, 14).padding(.vertical, 8)
                                .background(type == value ? IDS.Colors.brand : IDS.Colors.chipBackground).cornerRadius(12)
                        }
                    }
                }
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(orderReturnReasonCodes, id: \.self) { code in
                            Button(action: { reasonCode = code }) {
                                Text(code).font(.system(size: 11)).foregroundColor(reasonCode == code ? .white : IDS.Colors.textPrimary)
                                    .padding(.horizontal, 12).padding(.vertical, 8)
                                    .background(reasonCode == code ? IDS.Colors.brand : IDS.Colors.chipBackground).cornerRadius(12)
                            }
                        }
                    }
                }
                TextField("Add a note (optional)", text: $note)
                    .padding(10).background(IDS.Colors.chipBackground).cornerRadius(10)
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
                        Text(submitting ? "Submitting…" : "Submit request").font(.subheadline).bold().foregroundColor(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(submitting ? IDS.Colors.textTertiary : IDS.Colors.brand).cornerRadius(12)
                    }
                    .disabled(submitting)
                }
            }
        }
    }

    private func submit() async {
        submitting = true
        error = nil
        defer { submitting = false }
        do {
            _ = try await NetworkClient.shared.requestOrderReturn(
                orderId: orderId, type: type, reasonCode: reasonCode,
                reasonNote: note.trimmingCharacters(in: .whitespaces).isEmpty ? nil : note
            )
            done = true
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

private struct MyReturnRequestsView: View {
    @State private var requests: [OrderReturnRequestDto]?

    private static let statusLabel = ["REQUESTED": "Pending review", "APPROVED": "Approved", "REJECTED": "Rejected"]

    var body: some View {
        Group {
            if let requests, !requests.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    Text("My return/exchange requests").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    ForEach(requests) { r in
                        HStack {
                            Text(r.type == "RETURN" ? "Return" : "Exchange").font(.subheadline).bold()
                            Spacer()
                            Text(Self.statusLabel[r.status] ?? r.status)
                                .font(.caption).bold()
                                .foregroundColor(r.status == "APPROVED" ? IDS.Colors.brand : r.status == "REJECTED" ? .red : IDS.Colors.textSecondary)
                        }
                        .padding(14).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
                    }
                }
            }
        }
        .task {
            do {
                requests = try await NetworkClient.shared.getMyReturnRequests().returnRequests
            } catch {
                requests = []
            }
        }
    }
}
