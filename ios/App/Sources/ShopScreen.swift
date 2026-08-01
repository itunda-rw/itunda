import SwiftUI
import CoreDesignSystem
import CoreNetwork
import CoreLocation

/// Real "silent" one-shot location fetch for the nearby-ads rail below -- no UI, no
/// error surfaced to the user (a customer who denies/lacks location just never sees
/// the rail), same discipline RideScreenView's own RideLocationFetcher establishes for
/// an interactive fetch.
private final class SilentLocationFetcher: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var coordinate: CLLocationCoordinate2D?
    private let manager = CLLocationManager()

    override init() {
        super.init()
        manager.delegate = self
    }

    func requestLocation() {
        let status = manager.authorizationStatus
        if status == .notDetermined {
            manager.requestWhenInUseAuthorization()
        } else if status == .authorizedWhenInUse || status == .authorizedAlways {
            manager.requestLocation()
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        if manager.authorizationStatus == .authorizedWhenInUse || manager.authorizationStatus == .authorizedAlways {
            manager.requestLocation()
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        coordinate = locations.last?.coordinate
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // Real, non-critical -- the rail just won't render if this fails.
    }
}

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

private enum CommerceView { case browse, orders, wishlist, subscriptions }

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
    // Real local-business appointment booking, customer side -- merchant-mfe/Android
    // already have this; this is the first iOS client. See BookingFlowView's own doc
    // comment.
    @State private var bookingService: MerchantProductDto?
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

    // Real Coupang 타임특가 (Time Deal, item 226) -- see TimeDealDto's own doc comment
    // on the backend. A time-boxed, quantity-capped event, distinct from the
    // always-on Deals rail above. Re-fetched every 30s so a deal that just sold out
    // or expired stops showing without a manual refresh, matching bank-mfe/Android's
    // own established re-fetch interval for this exact feature.
    @State private var timeDeals: [TimeDealViewDto]?

    // Real 당근(Karrot) 반경 타기팅-style nearby ads rail -- see lib/shopping.ts's own
    // NearbyMerchantAd doc comment. bank-mfe/Android already have this; this is the
    // first iOS client.
    @State private var nearbyAds: [NearbyMerchantAdDto] = []
    @StateObject private var nearbyAdsLocationFetcher = SilentLocationFetcher()

    // Real Naver Pay 멤버십 데이 (Membership Day) cashback boost -- bank-mfe/Android
    // already have this; this is the first iOS client. See the backend's
    // ShoppingCashbackService doc comment for the real "first Monday of the month"
    // eligibility rule.
    @State private var membershipDay: MembershipDayStatusResponse?

    // Real "pay a merchant" -- the manual-code-entry alternative to camera QR scanning
    // (this app has no scanner), mirrors bank-mfe's PayByCodeCard/PayByStaticQrCard and
    // Android's PayAMerchantSection exactly. This is the first iOS client for either.
    @State private var paymentResult: CollectPaymentResultDto?

    // Real cross-merchant product search (item 191) -- closes
    // docs/DESIGN_REFERENCES.md Section 5 recommendation #1: bank-mfe has had "search
    // across every merchant" since 2026-07-20 (lib/shopping.ts's own doc comment), and
    // Android got its own port the same session (item 190), but iOS's Shop tab never
    // called this real, pre-existing endpoint at all. Opening a result constructs a
    // minimal ShoppingMerchantDto from the search row (merchantId/businessName only),
    // matching bank-mfe/Android's own openSearchResult shortcut rather than a second
    // real merchant fetch.
    @State private var productSearchInput = ""
    @State private var productSearchResults: [ProductSearchResultDto]?
    @State private var productSearching = false

    private var totalItems: Int { cart.values.reduce(0) { $0 + $1.quantity } }

    private func searchProducts() async {
        let q = productSearchInput.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return }
        productSearching = true
        do {
            productSearchResults = try await NetworkClient.shared.searchProducts(q).products
        } catch {
            productSearchResults = []
        }
        productSearching = false
    }

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
            } else if let merchant = selectedMerchant, let service = bookingService {
                BookingFlowView(
                    merchant: merchant,
                    service: service,
                    onBack: { bookingService = nil },
                    onBooked: { bookingService = nil }
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
                    onBookService: { bookingService = $0 },
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
            if membershipDay == nil {
                do { membershipDay = try await NetworkClient.shared.getMembershipDayStatus() } catch {}
            }
            nearbyAdsLocationFetcher.requestLocation()
        }
        .task {
            while true {
                do { timeDeals = try await NetworkClient.shared.getActiveTimeDeals().deals } catch {}
                try? await Task.sleep(nanoseconds: 30_000_000_000)
            }
        }
        .onChange(of: nearbyAdsLocationFetcher.coordinate?.latitude) { _ in
            guard let coordinate = nearbyAdsLocationFetcher.coordinate else { return }
            Task {
                nearbyAds = (try? await NetworkClient.shared.getNearbyMerchantAds(latitude: coordinate.latitude, longitude: coordinate.longitude))?.ads ?? []
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
                        Text("Subscriptions").tag(CommerceView.subscriptions)
                    }
                    .pickerStyle(.segmented)

                    if view == .orders {
                        MyCommerceOrdersView()
                        MyBookingsView()
                    } else if view == .wishlist {
                        ProductWishlistView(onRemoved: { Task { await loadFavoriteProductIds() } })
                    } else if view == .subscriptions {
                        MyProductSubscriptionsView()
                    } else {
                        PayAMerchantSection(paymentResult: $paymentResult)
                        if let membershipDay, membershipDay.isMembershipDay {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("🎉 Membership Day — \(Int(membershipDay.multiplier))x cashback today")
                                    .font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.brand)
                                Text("Every purchase you make today earns \(Int(membershipDay.multiplier))x the usual cashback.")
                                    .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            }
                            .padding(14).frame(maxWidth: .infinity, alignment: .leading)
                            .background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
                        }
                        HStack(spacing: 8) {
                            TextField("Search products across every merchant", text: $productSearchInput)
                                .padding(12).background(IDS.Colors.backgroundPrimary).cornerRadius(10)
                            Button(action: { Task { await searchProducts() } }) {
                                Text(productSearching ? "…" : "Search").bold().foregroundColor(.white)
                                    .padding(.horizontal, 16).padding(.vertical, 14)
                                    .background(productSearching || productSearchInput.trimmingCharacters(in: .whitespaces).isEmpty ? IDS.Colors.textTertiary : IDS.Colors.brand)
                                    .cornerRadius(10)
                            }
                            .disabled(productSearching || productSearchInput.trimmingCharacters(in: .whitespaces).isEmpty)
                            if productSearchResults != nil {
                                Button(action: { productSearchResults = nil; productSearchInput = "" }) {
                                    Text("Clear").bold().foregroundColor(IDS.Colors.textPrimary)
                                        .padding(.horizontal, 16).padding(.vertical, 14)
                                        .background(IDS.Colors.textTertiary).cornerRadius(10)
                                }
                            }
                        }

                        if let productSearchResults {
                            if productSearchResults.isEmpty {
                                Text("No products matched \"\(productSearchInput)\".").foregroundColor(IDS.Colors.textSecondary)
                            } else {
                                ForEach(productSearchResults) { r in
                                    Button(action: {
                                        Task {
                                            await openMerchant(ShoppingMerchantDto(merchantId: r.merchantId, businessName: r.merchantName, category: nil, cashbackRate: "1%"))
                                        }
                                    }) {
                                        HStack(spacing: 12) {
                                            ProductImageThumb(imageUrl: r.imageUrl, side: 48)
                                            VStack(alignment: .leading, spacing: 2) {
                                                Text(r.name).font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                                                Text(r.merchantName).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                                Text(r.stockQuantity.map { $0 == 0 ? "Out of stock" : "\($0) available" } ?? "Available")
                                                    .font(.caption).foregroundColor(r.stockQuantity == 0 ? .red : IDS.Colors.textSecondary)
                                            }
                                            Spacer()
                                            Text("\(Int(r.price)) RWF").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                                        }
                                        .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
                                    }
                                }
                            }
                        } else {
                        // Real 당근(Karrot) 반경 타기팅-style nearby ads rail -- only shown
                        // on the unfiltered landing state, same discipline the Deals rail
                        // below follows. Tapping one opens that merchant's real catalog,
                        // same minimal-ShoppingMerchantDto shortcut the Deals rail uses.
                        if selectedCategory == nil, searchInput.trimmingCharacters(in: .whitespaces).isEmpty, !nearbyAds.isEmpty {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("📍 Near you").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(spacing: 10) {
                                        ForEach(nearbyAds) { a in
                                            Button(action: {
                                                Task { await openMerchant(ShoppingMerchantDto(merchantId: a.ad.merchantId, businessName: a.businessName, category: nil, cashbackRate: "1%")) }
                                            }) {
                                                VStack(alignment: .leading, spacing: 4) {
                                                    Text(a.ad.title).font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                                    Text(a.businessName).font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                                                    if let description = a.ad.description, !description.isEmpty {
                                                        Text(description).font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                                                    }
                                                    Text(String(format: "%.1f km away", a.distanceKm)).font(.caption2).bold().foregroundColor(IDS.Colors.brand)
                                                }
                                                .padding(10).frame(width: 160, alignment: .leading)
                                                .background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
                                            }
                                            .buttonStyle(.plain)
                                        }
                                    }
                                }
                            }
                        }
                        // Real "Deals" rail (2026-07-25) -- only shown on the
                        // unfiltered landing state, same "merchandising above the raw
                        // list, hidden once the user starts filtering" discipline a
                        // real Coupang/Naver home surface follows. Tapping a deal jumps
                        // straight to that real merchant via the same minimal-
                        // ShoppingMerchantDto shortcut the product search results above
                        // now use (previously fell back to a merchant-name text search
                        // -- this closes that same gap for the same reason).
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
                                                Text(d.stockQuantity.map { $0 == 0 ? "Out of stock" : "\($0) available" } ?? "Available")
                                                    .font(.caption2).foregroundColor(d.stockQuantity == 0 ? .red : IDS.Colors.textSecondary)
                                            }
                                            .frame(width: 120, alignment: .leading)
                                            .padding(10)
                                            .background(IDS.Colors.card)
                                            .cornerRadius(IDS.Layout.cardCornerRadius)
                                            .onTapGesture {
                                                Task { await openMerchant(ShoppingMerchantDto(merchantId: d.merchantId, businessName: d.merchantName, category: nil, cashbackRate: "1%")) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // Real Coupang 타임특가 (Time Deal, item 226) -- see
                        // TimeDealDto's own doc comment. Tapping a deal jumps
                        // straight to that real merchant, same minimal-
                        // ShoppingMerchantDto shortcut the Deals rail above uses.
                        if selectedCategory == nil, searchInput.trimmingCharacters(in: .whitespaces).isEmpty, let timeDeals, !timeDeals.isEmpty {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("⏰ Time Deals").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(spacing: 10) {
                                        ForEach(timeDeals) { v in
                                            VStack(alignment: .leading, spacing: 6) {
                                                ProductImageThumb(imageUrl: v.productImageUrl, side: 96)
                                                Text(v.productName).font(.caption).bold().foregroundColor(IDS.Colors.textPrimary).lineLimit(2)
                                                Text("\(Int(v.deal.dealPrice)) RWF").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                                Text(formatTimeDealCountdown(v.deal.endsAt)).font(.caption2).bold().foregroundColor(IDS.Colors.brand)
                                                Text("\(v.deal.remainingQuantity) left")
                                                    .font(.caption2).foregroundColor(v.deal.remainingQuantity <= 3 ? .red : IDS.Colors.textSecondary)
                                            }
                                            .frame(width: 120, alignment: .leading)
                                            .padding(10)
                                            .background(IDS.Colors.card)
                                            .cornerRadius(IDS.Layout.cardCornerRadius)
                                            .onTapGesture {
                                                Task { await openMerchant(ShoppingMerchantDto(merchantId: v.deal.merchantId, businessName: v.businessName, category: nil, cashbackRate: "1%")) }
                                            }
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
    var onBookService: (MerchantProductDto) -> Void = { _ in }
    var favoriteProductIds: Set<String> = []
    var favoritingProductId: String?
    var onToggleFavorite: (String) -> Void = { _ in }
    var following: Bool = false
    var followBusy: Bool = false
    var onToggleFollow: () -> Void = {}

    // Real Kakao Pay 정기결제/Toss 빌링키-style recurring billing plans this merchant
    // itself has published -- see MerchantBillingService's own doc comment. bank-mfe/
    // Android already have this; this is the first iOS client.
    @State private var billingPlans: [MerchantBillingPlanDto] = []
    @State private var mySubscriptions: [MerchantBillingSubscriptionDto] = []

    private var totalItems: Int { cart.values.reduce(0) { $0 + $1.quantity } }
    private func qty(_ productId: String) -> Int { cart["\(merchant.merchantId):\(productId)"]?.quantity ?? 0 }
    private func setQty(_ product: MerchantProductDto, _ quantity: Int) {
        let key = "\(merchant.merchantId):\(product.id)"
        if quantity <= 0 { cart.removeValue(forKey: key) }
        else if product.stockQuantity == nil || quantity <= product.stockQuantity! {
            cart[key] = CommerceCartLine(merchantId: merchant.merchantId, businessName: merchant.businessName, product: product, quantity: quantity)
        }
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
                if !billingPlans.isEmpty {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Subscription plans").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                        ForEach(billingPlans) { plan in
                            BillingPlanRow(
                                plan: plan,
                                subscription: mySubscriptions.first { $0.planId == plan.id && $0.status == "ACTIVE" },
                                onChanged: { Task { await loadBilling() } }
                            )
                        }
                    }
                    .padding(14).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
                    .padding(.bottom, 10)
                }
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
                                    Text(product.stockQuantity.map { $0 == 0 ? "Out of stock" : "\($0) available" } ?? "Available")
                                        .font(.caption)
                                        .foregroundColor(product.stockQuantity == 0 ? .red : IDS.Colors.textSecondary)
                                    // Real bookable-service entry point -- a product with
                                    // a real durationMinutes set is an appointment, not a
                                    // cart-able good, so it gets a "Book" action instead
                                    // of the qty stepper. See BookingFlowView's own doc
                                    // comment. merchant-mfe/Android already have this;
                                    // this is the first iOS client.
                                    if product.durationMinutes != nil {
                                        Button(action: { onBookService(product) }) {
                                            Text("Book").font(.caption).bold().foregroundColor(.white)
                                                .frame(maxWidth: .infinity).padding(.vertical, 8)
                                                .background(IDS.Colors.brand).cornerRadius(10)
                                        }
                                    } else {
                                        HStack(spacing: 10) {
                                            Spacer()
                                            qtyButton("minus") { setQty(product, qty(product.id) - 1) }
                                            Text("\(qty(product.id))").frame(width: 24).font(.subheadline).bold()
                                            qtyButton("plus") { setQty(product, qty(product.id) + 1) }
                                                .disabled(product.stockQuantity != nil && qty(product.id) >= product.stockQuantity!)
                                            Spacer()
                                        }
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
        .task { await loadBilling() }
    }

    private func loadBilling() async {
        billingPlans = (try? await NetworkClient.shared.getMerchantBillingPlans(merchant.merchantId))?.plans ?? []
        let subs = (try? await NetworkClient.shared.getMyBillingSubscriptions())?.subscriptions ?? []
        mySubscriptions = subs.filter { $0.merchantId == merchant.merchantId }
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

/// Real Kakao Pay 정기결제/Toss 빌링키-style subscribe/cancel -- subscribing charges the
/// first cycle immediately (real "인증 + 첫결제"), same as
/// MerchantBillingService.subscribe's own doc comment. One real active subscription per
/// plan; cancelling stops future charges but doesn't refund the current cycle already
/// paid for. bank-mfe/Android already have this; this is the first iOS client.
private struct BillingPlanRow: View {
    let plan: MerchantBillingPlanDto
    let subscription: MerchantBillingSubscriptionDto?
    let onChanged: () -> Void

    @State private var busy = false
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(plan.name).font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    Text("\(Int(plan.amount)) RWF every \(plan.intervalDays) days").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    if let description = plan.description, !description.isEmpty {
                        Text(description).font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                    }
                }
                Spacer()
                if let subscription {
                    Button(action: { Task { await cancel(subscription.id) } }) {
                        Text(busy ? "…" : "Cancel").bold().font(.caption)
                            .padding(.horizontal, 12).padding(.vertical, 8)
                            .background(Color(.tertiarySystemBackground)).cornerRadius(8)
                    }
                    .disabled(busy)
                } else {
                    Button(action: { Task { await subscribe() } }) {
                        Text(busy ? "…" : "Subscribe").bold().font(.caption).foregroundColor(.white)
                            .padding(.horizontal, 12).padding(.vertical, 8)
                            .background(IDS.Colors.brand).cornerRadius(8)
                    }
                    .disabled(busy)
                }
            }
            if let subscription {
                Text(subscription.status == "ACTIVE" ? "Next charge \(String(subscription.nextChargeAt.prefix(10)))" : "Cancelled")
                    .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
            }
            if let error {
                Text(error).font(.caption2).foregroundColor(.red)
            }
        }
        .padding(12).background(Color(.secondarySystemBackground)).cornerRadius(10)
    }

    private func subscribe() async {
        busy = true
        error = nil
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.subscribeToBillingPlan(plan.id)
            onChanged()
        } catch {
            self.error = "Could not subscribe to this plan."
        }
    }

    private func cancel(_ subscriptionId: String) async {
        busy = true
        error = nil
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.cancelBillingSubscription(subscriptionId)
            onChanged()
        } catch {
            self.error = "Could not cancel this subscription."
        }
    }
}

/// Real product-detail screen (2026-07-21), mirroring Android's
/// ProductDetailScreen in features/shop/impl/ShopScreen.kt: full-size image,
/// name, price row, rating badge, description, quantity stepper, and an
/// add/update-cart action -- reached by tapping a product card in
/// MerchantDetailView's grid (see that grid's own doc comment).
// Real Coupang 정기배송 (subscribe & save) -- see NetworkClient's ProductSubscriptionDto
// doc comment. A minimal delivery-address prompt via .alert rather than a full address
// form, matching bank-mfe's own compact-card scope (fixed qty=1, every 30d).
private struct SubscribeAndSaveButton: View {
    let merchantId: String
    let productId: String

    @State private var showAlert = false
    @State private var address = ""
    @State private var busy = false
    @State private var done = false
    @State private var error: String?

    var body: some View {
        if done {
            Text("✓ Subscribed -- delivered every 30 days").font(.caption).foregroundColor(IDS.Colors.brand)
        } else {
            VStack(alignment: .leading, spacing: 4) {
                Button(action: { showAlert = true }) {
                    Text("Subscribe & save (every 30 days)").font(.caption).bold().foregroundColor(IDS.Colors.brand)
                }
                if let error { Text(error).font(.caption2).foregroundColor(.red) }
            }
            .alert("Subscribe & save", isPresented: $showAlert) {
                TextField("Delivery address", text: $address)
                Button("Subscribe") { Task { await subscribe() } }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("Delivered every 30 days. Cancel anytime.")
            }
        }
    }

    private func subscribe() async {
        guard !address.trimmingCharacters(in: .whitespaces).isEmpty else {
            error = "Enter a delivery address."
            return
        }
        busy = true
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.subscribeToProduct(merchantId: merchantId, productId: productId, quantity: 1, intervalDays: 30, deliveryAddress: address.trimmingCharacters(in: .whitespaces))
            done = true
        } catch {
            self.error = "Could not set up this subscription."
        }
    }
}

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

// Real Coupang 정기배송 (subscribe & save)-style recurring product delivery -- see
// NetworkClient's ProductSubscriptionDto doc comment. bank-mfe already had this;
// this is the first iOS client, mirroring bank-mfe's MyProductSubscriptionsCard.
private struct MyProductSubscriptionsView: View {
    @State private var subscriptions: [ProductSubscriptionDto]?
    @State private var error: String?
    @State private var busyId: String?

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
            } else if subscriptions == nil {
                ProgressView().frame(maxWidth: .infinity, minHeight: 120)
            } else if subscriptions!.isEmpty {
                Text("No recurring deliveries yet -- subscribe from any product's detail page.")
                    .foregroundColor(IDS.Colors.textSecondary)
            } else {
                ForEach(subscriptions!) { s in
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Qty \(s.quantity) · every \(s.intervalDays)d").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                        if s.status == "CANCELLED", let cancelledAt = s.cancelledAt {
                            Text("Cancelled \(String(cancelledAt.prefix(10)))").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        } else {
                            Text("\(s.status) · \(s.deliveryCount) delivered").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                        if let reason = s.lastFailureReason, s.status == "ACTIVE" {
                            Text("Last delivery failed: \(reason)").font(.caption).foregroundColor(.red)
                        }
                        if s.status != "CANCELLED" {
                            HStack(spacing: 8) {
                                Button(action: { Task { await toggle(s) } }) {
                                    Text(busyId == s.id ? "…" : (s.status == "ACTIVE" ? "Pause" : "Resume"))
                                        .font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                        .padding(.horizontal, 12).padding(.vertical, 8)
                                        .background(IDS.Colors.chipBackground).cornerRadius(10)
                                }
                                .disabled(busyId == s.id)
                                Button(action: { Task { await cancel(s.id) } }) {
                                    Text("Cancel")
                                        .font(.caption).bold().foregroundColor(IDS.Colors.textSecondary)
                                        .padding(.horizontal, 12).padding(.vertical, 8)
                                        .background(IDS.Colors.chipBackground).cornerRadius(10)
                                }
                                .disabled(busyId == s.id)
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
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
            let res = try await NetworkClient.shared.getMyProductSubscriptions()
            subscriptions = res.subscriptions
            error = nil
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func toggle(_ s: ProductSubscriptionDto) async {
        busyId = s.id
        defer { busyId = nil }
        do {
            if s.status == "ACTIVE" { _ = try await NetworkClient.shared.pauseProductSubscription(s.id) }
            else { _ = try await NetworkClient.shared.resumeProductSubscription(s.id) }
            await load()
        } catch {
            self.error = "Could not update this subscription."
        }
    }

    private func cancel(_ id: String) async {
        busyId = id
        defer { busyId = nil }
        do {
            _ = try await NetworkClient.shared.cancelProductSubscription(id)
            await load()
        } catch {
            self.error = "Could not cancel this subscription."
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

private let BOOKING_STATUS_LABEL: [String: String] = [
    "REQUESTED": "Requested", "CONFIRMED": "Confirmed", "DECLINED": "Declined",
    "CANCELLED": "Cancelled", "COMPLETED": "Completed",
]

// Real customer-side view of merchant bookings requested via BookingFlowView -- see
// its own doc comment. Cancel is the only customer action here (confirm/decline/
// complete are owner-side, already real on merchant-mfe/Android's own merchant apps).
private struct MyBookingsView: View {
    @State private var bookings: [MerchantBookingDto]?
    @State private var error: String?
    @State private var cancellingId: String?

    var body: some View {
        Group {
            if let bookings, !bookings.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Bookings").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                    if let error {
                        Text(error).font(.caption).foregroundColor(.red)
                    }
                    ForEach(bookings) { b in
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(b.serviceName).font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                                Spacer()
                                Text(BOOKING_STATUS_LABEL[b.status] ?? b.status).font(.caption).bold().foregroundColor(IDS.Colors.brand)
                            }
                            Text("\(b.bookingDate) at \(String(b.startTime.prefix(5)))").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            if b.status == "REQUESTED" || b.status == "CONFIRMED" {
                                Button(action: { Task { await cancel(b.id) } }) {
                                    Text(cancellingId == b.id ? "Cancelling…" : "Cancel booking")
                                        .font(.caption).bold().foregroundColor(.white)
                                        .padding(.horizontal, 14).padding(.vertical, 8)
                                        .background(.red).cornerRadius(10)
                                }
                                .disabled(cancellingId == b.id)
                            }
                            if b.status == "COMPLETED" {
                                BookingReviewButton(bookingId: b.id)
                            }
                        }
                        .padding(14).frame(maxWidth: .infinity, alignment: .leading)
                        .background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
                    }
                }
                .padding(.top, 16)
            }
        }
        .task { await load() }
    }

    private func load() async {
        bookings = (try? await NetworkClient.shared.getMyBookings())?.bookings ?? []
    }

    private func cancel(_ bookingId: String) async {
        cancellingId = bookingId
        error = nil
        do {
            _ = try await NetworkClient.shared.cancelBooking(bookingId)
            await load()
        } catch {
            self.error = "Couldn't cancel this booking."
        }
        cancellingId = nil
    }
}

// Real customer-side post-appointment review (item 143) -- see NetworkClient.swift's
// own doc comment on submitBookingReview. Mirrors ProductReviewRow's exact shape (star
// rating + optional comment, a real 409 BOOKING_ALREADY_REVIEWED is treated as
// already-done, not an error).
private struct BookingReviewButton: View {
    let bookingId: String

    @State private var open = false
    @State private var done = false
    @State private var rating = 0
    @State private var comment = ""
    @State private var submitting = false
    @State private var error: String?

    var body: some View {
        if done {
            Text("Thanks for your review!").font(.caption).foregroundColor(IDS.Colors.textSecondary)
        } else if !open {
            Button(action: { open = true }) {
                Text("Rate this visit").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    .background(IDS.Colors.chipBackground).cornerRadius(12)
            }
        } else {
            VStack(alignment: .leading, spacing: 8) {
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
            _ = try await NetworkClient.shared.submitBookingReview(
                bookingId, rating: rating, comment: comment.trimmingCharacters(in: .whitespaces).isEmpty ? nil : comment
            )
            done = true
        } catch let NetworkError.httpError(statusCode) {
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

// Real local-business appointment booking (customer side) -- closes the "business
// profile + real booking" gap independently converged on by Naver Smart Place, Kakao
// Hair Shop, and Karrot's Business Profile research (docs/DESIGN_REFERENCES.md). Date
// picker is a plain next-14-days strip (no calendar widget); slots come straight from
// the real backend-computed availability (MerchantBookingService.getAvailableSlots),
// never client-guessed. merchant-mfe/Android already have this; this is the first
// iOS client.
private struct BookingFlowView: View {
    let merchant: ShoppingMerchantDto
    let service: MerchantProductDto
    let onBack: () -> Void
    let onBooked: () -> Void

    @State private var selectedDate = Date()
    @State private var slots: [BookingSlotDto]?
    @State private var selectedSlot: BookingSlotDto?
    @State private var notes = ""
    @State private var submitting = false
    @State private var error: String?
    @State private var booked = false

    private var dateFormatter: DateFormatter {
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; return f
    }
    private var next14Days: [Date] {
        (0..<14).compactMap { Calendar.current.date(byAdding: .day, value: $0, to: Calendar.current.startOfDay(for: Date())) }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left").font(.system(size: 18, weight: .medium)).frame(width: 44, height: 44)
                }
                .accessibilityLabel("Back")
                Text("Book \(service.name)").font(IDS.Typography.title).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
            }
            .padding(.horizontal, 8)

            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    Text("\(service.name) · \(service.durationMinutes ?? 0) min · \(Int(service.price)) RWF")
                        .font(.footnote).foregroundColor(IDS.Colors.textSecondary)

                    Text("Choose a date").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(next14Days, id: \.self) { date in
                                let selected = Calendar.current.isDate(date, inSameDayAs: selectedDate)
                                VStack {
                                    Text(date.formatted(.dateTime.weekday(.abbreviated))).font(.caption2)
                                    Text(date.formatted(.dateTime.day())).font(.subheadline).bold()
                                }
                                .foregroundColor(selected ? .white : IDS.Colors.textPrimary)
                                .padding(.horizontal, 12).padding(.vertical, 8)
                                .background(selected ? IDS.Colors.brand : IDS.Colors.card)
                                .cornerRadius(10)
                                .onTapGesture { selectedDate = date; selectedSlot = nil }
                            }
                        }
                    }

                    Text("Choose a time").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                    if let slots {
                        if slots.isEmpty {
                            Text("No open times on this date.").font(.footnote).foregroundColor(IDS.Colors.textSecondary)
                        } else {
                            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                                ForEach(slots, id: \.startTime) { slot in
                                    let selected = slot == selectedSlot
                                    Text(String(slot.startTime.prefix(5)))
                                        .font(.subheadline).bold()
                                        .foregroundColor(selected ? .white : IDS.Colors.textPrimary)
                                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                                        .background(selected ? IDS.Colors.brand : IDS.Colors.card)
                                        .cornerRadius(8)
                                        .onTapGesture { selectedSlot = slot }
                                }
                            }
                        }
                    } else {
                        ProgressView()
                    }

                    TextField("Notes (optional)", text: $notes)
                        .padding(12).background(IDS.Colors.card).cornerRadius(10)

                    if let error {
                        Text(error).font(.caption).foregroundColor(.red)
                    }

                    Button(action: { Task { await book() } }) {
                        Text(submitting ? "Requesting…" : "Request booking")
                            .font(IDS.Typography.bodyBold).foregroundColor(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 16)
                            .background(submitting || selectedSlot == nil ? IDS.Colors.textTertiary : IDS.Colors.brand)
                            .cornerRadius(16)
                    }
                    .disabled(submitting || selectedSlot == nil)
                }
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.top, 12)
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await loadSlots() }
        .onChange(of: selectedDate) { _ in Task { await loadSlots() } }
        .alert("Booking requested", isPresented: $booked) {
            Button("Done", action: onBooked)
        } message: {
            Text("\(service.name) on \(dateFormatter.string(from: selectedDate)) at \(selectedSlot.map { String($0.startTime.prefix(5)) } ?? "") -- \(merchant.businessName) will confirm shortly.")
        }
    }

    private func loadSlots() async {
        selectedSlot = nil
        slots = nil
        do {
            slots = try await NetworkClient.shared.getBookingSlots(merchantId: merchant.merchantId, serviceId: service.id, date: dateFormatter.string(from: selectedDate)).slots
        } catch {
            self.error = "Couldn't load available times."
            slots = []
        }
    }

    private func book() async {
        guard let slot = selectedSlot else { return }
        submitting = true
        error = nil
        do {
            let res = try await NetworkClient.shared.createBooking(CreateBookingRequest(
                merchantId: merchant.merchantId, serviceId: service.id,
                date: dateFormatter.string(from: selectedDate), startTime: slot.startTime,
                notes: notes.trimmingCharacters(in: .whitespaces).isEmpty ? nil : notes.trimmingCharacters(in: .whitespaces)
            ))
            if res.success { booked = true }
        } catch {
            self.error = "Could not request this booking."
        }
        submitting = false
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

    // Real bulk/wholesale pricing -- closes the gap named in Baemin's own real
    // 배민상회 B2B supplies marketplace research. Shows the best (highest-quantity)
    // real tier as a hint; the actual price used at checkout is always resolved
    // server-side from the real ordered quantity, never trusted from this display.
    // Android already has this; this is the first iOS client.
    private var bestTier: PriceTierDto? { product.priceTiers?.max { $0.minQuantity < $1.minQuantity } }

    var body: some View {
        VStack(alignment: .leading, spacing: 1) {
            if let originalPrice = product.originalPrice, let discountPercent = product.discountPercent, discountPercent > 0 {
                HStack(spacing: 4) {
                    Text("\(discountPercent)%").font(.subheadline).bold().foregroundColor(.red)
                    Text("\(Int(product.price)) RWF").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                }
                Text("\(Int(originalPrice)) RWF").font(.caption2).foregroundColor(IDS.Colors.textSecondary).strikethrough()
            } else {
                Text("\(Int(product.price)) RWF").font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
            }
            if let bestTier {
                Text("Buy \(bestTier.minQuantity)+ for \(Int(bestTier.unitPrice)) RWF each")
                    .font(.caption2).bold().foregroundColor(.green)
            }
        }
    }
}

/// Real Coupang-style pre-purchase product Q&A (상품문의) -- see
/// rw.itunda.commerce.ProductInquiryService's own doc comment. Genuinely distinct from
/// ProductRatingBadge's reviews below: no order/purchase required at all, so this is
/// always visible on a product's detail page, not gated behind having bought it.
/// bank-mfe/Android already have this; this is the first iOS client.
private struct ProductInquirySection: View {
    let productId: String
    @State private var inquiries: [ProductInquiryDto]?
    @State private var question = ""
    @State private var asking = false
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Questions & answers").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
            HStack(spacing: 8) {
                TextField("Ask the seller a question", text: $question)
                    .padding(10).background(IDS.Colors.backgroundPrimary).cornerRadius(10)
                Button(action: { Task { await ask() } }) {
                    Text("Ask").bold().font(.caption).foregroundColor(IDS.Colors.textPrimary)
                        .padding(.horizontal, 14).padding(.vertical, 10)
                        .background(Color(.tertiarySystemBackground)).cornerRadius(10)
                }
                .disabled(asking || question.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            if let error {
                Text(error).font(.caption2).foregroundColor(.red)
            }
            if let inquiries {
                if inquiries.isEmpty {
                    Text("No questions yet — be the first to ask.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                } else {
                    VStack(alignment: .leading, spacing: 10) {
                        ForEach(inquiries) { q in
                            VStack(alignment: .leading, spacing: 2) {
                                HStack(alignment: .top, spacing: 0) {
                                    Text("Q. ").font(.caption).bold().foregroundColor(IDS.Colors.textSecondary)
                                    Text(q.question).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                }
                                if let answer = q.answer {
                                    HStack(alignment: .top, spacing: 0) {
                                        Text("A. ").font(.caption).bold().foregroundColor(IDS.Colors.textSecondary)
                                        Text(answer).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                    }
                                    .padding(.leading, 12)
                                } else {
                                    Text("Awaiting seller response").font(.caption2).foregroundColor(IDS.Colors.textTertiary)
                                        .padding(.leading, 12)
                                }
                            }
                        }
                    }
                }
            } else {
                Text("Loading questions…").font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
        }
        .task { await load() }
    }

    private func load() async {
        inquiries = (try? await NetworkClient.shared.getProductInquiries(productId))?.inquiries ?? []
    }

    private func ask() async {
        let trimmed = question.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        asking = true
        error = nil
        defer { asking = false }
        do {
            _ = try await NetworkClient.shared.askProductInquiry(productId, question: trimmed)
            question = ""
            await load()
        } catch {
            self.error = "Could not submit your question."
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
                                    if let reply = r.ownerReply, !reply.isEmpty {
                                        Text("↳ Seller: \(reply)").font(.caption2).foregroundColor(IDS.Colors.textTertiary).padding(.leading, 12)
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

/// Real "pay a merchant" -- the manual-code-entry alternative to camera QR scanning
/// (this app has no scanner), mirrors bank-mfe's `PayByCodeCard`/`PayByStaticQrCard` and
/// Android's `PayAMerchantSection` exactly. bank-mfe/Android already have both; this is
/// the first iOS client for either -- previously neither the dynamic per-sale flow nor
/// the static QR flow existed anywhere on this native consumer app.
/// Coupon-preview-before-pay (bank-mfe's own `previewPaymentIntent` flow, item 149/146)
/// closed 2026-08-01 -- see PayByCodeCard's own doc comment.
private struct PayAMerchantSection: View {
    @Binding var paymentResult: CollectPaymentResultDto?
    // Real Face Pay -- see FacePaySettingsCard/PayByCodeCard's own doc comments. Lifted
    // here, same as bank-mfe's own ShoppingView, so this card and PayByCodeCard don't
    // each fetch enrollment status independently.
    @State private var facePayEnrolled: Bool?

    var body: some View {
        if let result = paymentResult {
            VStack(alignment: .leading, spacing: 6) {
                Text("Payment complete").font(.headline).bold().foregroundColor(IDS.Colors.textPrimary)
                Text(result.merchantName).font(.subheadline).foregroundColor(IDS.Colors.textPrimary)
                Text("\(Int(result.amount)) RWF").font(.title2).bold().foregroundColor(IDS.Colors.textPrimary)
                if result.cashbackEarned > 0 {
                    Text("+ \(Int(result.cashbackEarned)) RWF cashback").font(.footnote).foregroundColor(IDS.Colors.brand)
                }
                Button(action: { paymentResult = nil }) {
                    Text("Done").bold().foregroundColor(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(IDS.Colors.brand).cornerRadius(10)
                }
            }
            .padding(18).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
        } else {
            VStack(alignment: .leading, spacing: 10) {
                FacePaySettingsCard(enrolled: facePayEnrolled, onChanged: { Task { await loadFacePayStatus() } })
                PayByCodeCard(facePayEnrolled: facePayEnrolled ?? false, onPaid: { paymentResult = $0 })
                PayByStaticQrCard(onPaid: { paymentResult = $0 })
            }
            .task { await loadFacePayStatus() }
        }
    }

    private func loadFacePayStatus() async {
        facePayEnrolled = (try? await NetworkClient.shared.getFacePayStatus())?.enrolled
    }
}

/// Real Face Pay enroll/disable toggle -- see rw.itunda.merchant.FacePayService's own
/// doc comment. bank-mfe/Android already have this; this is the first iOS client.
/// Enrolling swaps Pay-by-code's own collect call to the Face Pay channel -- same manual
/// code entry, just a different real ledger channel label, matching bank-mfe's own
/// honest scope exactly (no device biometric prompt gates it on any client, itunda's own).
private struct FacePaySettingsCard: View {
    let enrolled: Bool?
    let onChanged: () -> Void

    @State private var busy = false
    @State private var error: String?

    var body: some View {
        if enrolled == nil {
            Color(.secondarySystemBackground).frame(height: 64).cornerRadius(IDS.Layout.cardCornerRadius)
        } else {
            VStack(alignment: .leading, spacing: 6) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("😊 Face Pay").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                        Text(enrolled == true ? "Enabled — authorize payment codes with your face, no code re-entry needed" : "Not enabled on this account")
                            .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    }
                    Spacer()
                    Button(action: { Task { await toggle() } }) {
                        Text(busy ? "…" : (enrolled == true ? "Disable" : "Enable"))
                            .bold().font(.caption).foregroundColor(enrolled == true ? IDS.Colors.textPrimary : .white)
                            .padding(.horizontal, 14).padding(.vertical, 8)
                            .background(enrolled == true ? Color(.tertiarySystemBackground) : IDS.Colors.brand).cornerRadius(8)
                    }
                    .disabled(busy)
                }
                if let error {
                    Text(error).font(.caption).foregroundColor(.red)
                }
            }
            .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
        }
    }

    private func toggle() async {
        busy = true
        error = nil
        defer { busy = false }
        do {
            if enrolled == true {
                _ = try await NetworkClient.shared.revokeFacePay()
            } else {
                _ = try await NetworkClient.shared.enrollFacePay()
            }
            onChanged()
        } catch {
            self.error = "Could not update Face Pay."
        }
    }
}

private func couponDiscountLabel(_ c: MerchantCouponPreviewDto) -> String {
    c.discountType == "PERCENT" ? "\(Int(c.discountValue))% off" : "\(Int(c.discountValue)) RWF off"
}

// Real Coupang 타임특가 (Time Deal, item 226) countdown -- mirrors bank-mfe/Android's
// own formatDealCountdown exactly.
private func formatTimeDealCountdown(_ endsAt: String) -> String {
    guard let end = ISO8601DateFormatter(withFractionalSeconds: true).date(from: endsAt) ?? ISO8601DateFormatter().date(from: endsAt) else { return "Ending soon" }
    let secondsLeft = end.timeIntervalSinceNow
    if secondsLeft <= 0 { return "Ending soon" }
    let totalMinutes = Int(secondsLeft / 60)
    let hours = totalMinutes / 60
    let minutes = totalMinutes % 60
    return hours > 0 ? "\(hours)h \(minutes)m left" : "\(minutes)m left"
}

/// Real coupon-preview-before-pay (item 149/146) -- closes the deliberate scope-down
/// this struct's own doc comment previously named. Mirrors bank-mfe's PayByCodeCard
/// exactly: a non-Face-Pay code with real eligible coupons stops at a preview step
/// (merchant/amount + coupon picker) before the actual collect() call; Face Pay and a
/// code with zero eligible coupons both skip straight to a direct pay.
private struct PayByCodeCard: View {
    let facePayEnrolled: Bool
    let onPaid: (CollectPaymentResultDto) -> Void

    @State private var code = ""
    @State private var submitting = false
    @State private var error: String?
    @State private var needsDeviceVerification = false
    @State private var preview: PaymentIntentPreviewResponse?
    @State private var eligibleCoupons: [MerchantCouponViewDto] = []
    @State private var selectedCouponId: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Pay by code").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
            Text(facePayEnrolled
                ? "Face Pay is on — enter the code the merchant shows you to authorize with your face."
                : "No scanner handy? Enter the payment code the merchant shows you to pay instantly and earn cashback.")
                .font(.caption).foregroundColor(IDS.Colors.textSecondary)
            if let preview {
                Text(preview.businessName).font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                Text("\(Int(preview.amount)) RWF").font(.title2).bold().foregroundColor(IDS.Colors.textPrimary)
                Text("Apply a coupon?").font(.footnote).bold().foregroundColor(IDS.Colors.textPrimary)
                Button(action: { selectedCouponId = nil }) {
                    HStack {
                        Image(systemName: selectedCouponId == nil ? "largecircle.fill.circle" : "circle")
                        Text("No coupon").font(.footnote)
                    }.foregroundColor(IDS.Colors.textPrimary)
                }
                ForEach(eligibleCoupons) { c in
                    Button(action: { selectedCouponId = c.coupon.id }) {
                        HStack {
                            Image(systemName: selectedCouponId == c.coupon.id ? "largecircle.fill.circle" : "circle")
                            Text("\(c.coupon.title) — \(couponDiscountLabel(c.coupon))").font(.footnote)
                        }.foregroundColor(IDS.Colors.textPrimary)
                    }
                }
                if let error {
                    Text(error).font(.caption).foregroundColor(.red)
                }
                HStack(spacing: 10) {
                    Button(action: { Task { await payDirect(couponId: selectedCouponId) } }) {
                        Text(submitting ? "Paying…" : "Pay").bold().foregroundColor(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(IDS.Colors.brand).cornerRadius(10)
                    }
                    .disabled(submitting)
                    Button(action: cancelPreview) {
                        Text("Cancel").bold().foregroundColor(IDS.Colors.textPrimary)
                            .padding(.horizontal, 16).padding(.vertical, 12)
                            .background(Color(.tertiarySystemBackground)).cornerRadius(10)
                    }
                    .disabled(submitting)
                }
            } else {
                HStack(spacing: 10) {
                    TextField("Payment code", text: $code)
                        .padding(12).background(IDS.Colors.backgroundPrimary).cornerRadius(10)
                    Button(action: { Task { await submit() } }) {
                        Text(submitting ? (facePayEnrolled ? "Authorizing…" : "Paying…") : (facePayEnrolled ? "😊 Pay" : "Pay"))
                            .bold().foregroundColor(.white)
                            .padding(.horizontal, 16).padding(.vertical, 14)
                            .background(submitting || code.isEmpty ? IDS.Colors.textTertiary : IDS.Colors.brand)
                            .cornerRadius(10)
                    }
                    .disabled(submitting || code.isEmpty)
                }
                if let error {
                    Text(error).font(.caption).foregroundColor(.red)
                }
            }
        }
        .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
        DeviceStepUpHost(visible: needsDeviceVerification, onDismiss: { needsDeviceVerification = false }, onVerified: { needsDeviceVerification = false })
    }

    private func payDirect(couponId: String? = nil) async {
        submitting = true
        error = nil
        needsDeviceVerification = false
        defer { submitting = false }
        do {
            let result = facePayEnrolled
                ? try await NetworkClient.shared.collectWithFacePay(intentId: code.trimmingCharacters(in: .whitespaces))
                : try await NetworkClient.shared.collectPayment(intentId: code.trimmingCharacters(in: .whitespaces), couponId: couponId)
            code = ""
            preview = nil
            eligibleCoupons = []
            selectedCouponId = nil
            onPaid(result)
        } catch NetworkError.deviceNotVerified {
            needsDeviceVerification = true
        } catch {
            self.error = "Could not complete this payment."
        }
    }

    private func submit() async {
        error = nil
        if facePayEnrolled {
            await payDirect()
            return
        }
        submitting = true
        do {
            let r = try await NetworkClient.shared.previewPaymentIntent(intentId: code.trimmingCharacters(in: .whitespaces))
            let eligible = r.coupons.filter { $0.eligible && !$0.alreadyRedeemed }
            if eligible.isEmpty {
                submitting = false
                await payDirect()
            } else {
                preview = r
                eligibleCoupons = eligible
                submitting = false
            }
        } catch {
            self.error = "Could not look up this payment code."
            submitting = false
        }
    }

    private func cancelPreview() {
        preview = nil
        eligibleCoupons = []
        selectedCouponId = nil
        error = nil
    }
}

private struct PayByStaticQrCard: View {
    let onPaid: (CollectPaymentResultDto) -> Void

    @State private var merchantId = ""
    @State private var amount = ""
    @State private var submitting = false
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Pay a merchant's static QR").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
            Text("For a merchant with one permanent code (like a market stall) — enter their merchant ID and how much you're paying.")
                .font(.caption).foregroundColor(IDS.Colors.textSecondary)
            TextField("Merchant ID", text: $merchantId)
                .padding(12).background(IDS.Colors.backgroundPrimary).cornerRadius(10)
            HStack(spacing: 10) {
                TextField("Amount (RWF)", text: $amount)
                    .keyboardType(.decimalPad)
                    .padding(12).background(IDS.Colors.backgroundPrimary).cornerRadius(10)
                Button(action: { Task { await pay() } }) {
                    Text(submitting ? "Paying…" : "Pay").bold().foregroundColor(.white)
                        .padding(.horizontal, 16).padding(.vertical, 14)
                        .background(submitting || merchantId.isEmpty || Double(amount) == nil ? IDS.Colors.textTertiary : IDS.Colors.brand)
                        .cornerRadius(10)
                }
                .disabled(submitting || merchantId.isEmpty || Double(amount) == nil)
            }
            if let error {
                Text(error).font(.caption).foregroundColor(.red)
            }
        }
        .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
    }

    private func pay() async {
        guard let numericAmount = Double(amount), numericAmount > 0 else {
            error = "Enter a valid amount."
            return
        }
        submitting = true
        error = nil
        defer { submitting = false }
        do {
            let result = try await NetworkClient.shared.payByStaticQr(merchantId: merchantId.trimmingCharacters(in: .whitespaces), amount: numericAmount)
            merchantId = ""
            amount = ""
            onPaid(result)
        } catch {
            self.error = "Could not complete this payment."
        }
    }
}
