import SwiftUI
import UIKit
import CoreDesignSystem
import CoreNetwork
import CoreLocation


// Shop tab entry point + browse content. Sub-views live in
// ShopBrowseComponents.swift / ShopMerchantDetail.swift / ShopBooking.swift /
// ShopProductDetail.swift / ShopOrders.swift / ShopMerchantOrders.swift /
// ShopReturns.swift / ShopReviews.swift / ShopPay.swift (split 2026-08-19
// for real file-size decomposition).

/// Real "silent" one-shot location fetch for the nearby-ads rail below -- no UI, no
/// error surfaced to the user (a customer who denies/lacks location just never sees
/// the rail), same discipline RideScreenView's own RideLocationFetcher establishes for
/// an interactive fetch.
final class SilentLocationFetcher: NSObject, ObservableObject, CLLocationManagerDelegate {
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
/// CommerceShopContent (:features:shop:impl), replacing the old Toss-Shopping-cashback
/// DiscoverScreen entirely. See NetworkClient.swift's Commerce extension and
/// rw.itunda.commerce.OrderService's own doc comment for the full backend account,
/// including the honest "self-declared fulfillment, no real courier network" scope.
///
/// ShopScreen's own Shop/Eats segmented control was retired 2026-08-10: real user
/// correction, same fix applied to HoodScreen's Marketplace/Community/Jobs/Property
/// Picker and to Android's identical ShopTab/HoodTab chip rows -- nesting Shop and
/// Eats behind one Explore row with an internal switcher is a tab bar inside a tab,
/// noise a flat catalog shouldn't have. CommerceShopContent/EatsContent are each
/// their own flat ContentView.swift destination now (see ContentView.swift's
/// showShop/showEats).

enum CommerceView { case browse, orders, wishlist, subscriptions }

// Real cross-merchant cart (2026-07-20) -- closes the "real Coupang splits a
// multi-seller cart into per-seller orders, not attempted here" simplification the
// matrix named. Flattened (keyed by "merchantId:productId") rather than nested
// dictionaries, mirroring Android's own identical CommerceCartLine shape exactly.
struct CommerceCartLine {
    let merchantId: String
    let businessName: String
    let product: MerchantProductDto
    var quantity: Int
}

struct CommerceCheckoutResult: Identifiable {
    var id: String { merchantId }
    let merchantId: String
    let businessName: String
    let order: OrderDto?
    let error: String?
}

struct CommerceShopContent: View {
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
    @State private var reorderingId: String?
    @State private var reorderError: String?

    // Real Shop product wishlist (2026-07-24) -- lifted here same as Marketplace's own
    // favoriteIds (HoodScreen.swift), so the heart on a product card (grid or detail)
    // stays correct whichever screen toggled it. See NetworkClient's FavoriteProductDto
    // doc comment.
    @State private var favoriteProductIds: Set<String> = []
    @State private var favoritingProductId: String?
    // Real "recently viewed products" rail (2026-08-23) -- see
    // RecentlyViewedStores.swift's own doc comment.
    @State private var recentlyViewedProducts: [RecentlyViewedProduct] = RecentlyViewedProductsStore.shared.getAll()

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
            productSearchResults = try await NetworkClient.shared.searchProducts(q, businessType: "SHOP").products
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
                    onOpenProduct: { openProduct($0, businessName: merchant.businessName) },
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
                // businessType added 2026-08-25 (direct user directive: "we need
                // everything separated to avoid confusion, that's toss style, clear
                // isolation") -- matches Android's identical ShopScreen.kt fix.
                do { categories = try await NetworkClient.shared.getMerchantCategories(businessType: "SHOP").categories } catch {}
            }
            await loadFavoriteProductIds()
            await loadFollowedMerchantIds()
            if deals == nil {
                do { deals = try await NetworkClient.shared.getShopDeals(businessType: "SHOP").products } catch {}
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
                        Text("Wishlist").tag(CommerceView.wishlist)
                        Text("Subscriptions").tag(CommerceView.subscriptions)
                    }
                    .pickerStyle(.segmented)

                    // Real merchant-side Commerce order fulfillment queue (item 234) --
                    // see NetworkClient.getMerchantOrders's own doc comment. Shown above
                    // every sub-tab, same placement as bank-mfe/Android.
                    MerchantOrdersView()
                    MerchantReturnQueueView()

                    if view == .orders {
                        MyCommerceOrdersView(
                            onReorder: { order in Task { await handleReorder(order) } },
                            reorderingId: reorderingId
                        )
                        if let reorderError {
                            Text(reorderError).foregroundColor(.red).font(.caption)
                        }
                        MyBookingsView()
                    } else if view == .wishlist {
                        ProductWishlistView(onRemoved: { Task { await loadFavoriteProductIds() } })
                    } else if view == .subscriptions {
                        MyProductSubscriptionsView()
                    } else {
                        // Real fix (2026-08-25, direct user follow-up: "why do we
                        // have pay in there?" -- matches Android's identical
                        // ShopScreen.kt fix). PayAMerchantSection is real, in-person
                        // merchant payment -- it already has its own real home, the
                        // Pay tab (PayHomeExtras.swift's own PayScreen). This call
                        // rendered the exact same section a second time,
                        // unconditionally, at the top of Shop's own online-catalog
                        // browse screen.
                        if let membershipDay, membershipDay.isMembershipDay {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("🎉 Membership Day — \(Int(membershipDay.multiplier))x cashback today")
                                    .font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.brand)
                                Text("Every purchase you make today earns \(Int(membershipDay.multiplier))x the usual cashback.")
                                    .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            }
                            .padding(.vertical, 10).frame(maxWidth: .infinity, alignment: .leading)
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
                                        .padding(.vertical, 10)
                                    }
                                }
                            }
                        } else {
                        // Real "recently viewed products" rail (2026-08-23) -- see
                        // RecentlyViewedStores.swift's own doc comment. Same "merchandising
                        // above the raw list, hidden once filtering starts" discipline the
                        // Nearby/Deals rails below already establish. Reopens the merchant
                        // (same shortcut those rails use), not a possibly-stale cached
                        // product snapshot.
                        if selectedCategory == nil, searchInput.trimmingCharacters(in: .whitespaces).isEmpty, !recentlyViewedProducts.isEmpty {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("🕒 Recently viewed").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(spacing: 10) {
                                        ForEach(recentlyViewedProducts) { rv in
                                            Button(action: {
                                                Task { await openMerchant(ShoppingMerchantDto(merchantId: rv.merchantId, businessName: rv.businessName, category: nil, cashbackRate: "1%")) }
                                            }) {
                                                VStack(alignment: .leading, spacing: 6) {
                                                    ProductImageThumb(imageUrl: rv.imageUrl, side: 96)
                                                    Text(rv.name).font(.caption).bold().foregroundColor(IDS.Colors.textPrimary).lineLimit(2)
                                                    if let discountPercent = rv.discountPercent, discountPercent > 0 {
                                                        Text("\(discountPercent)% off").font(.caption2).bold().foregroundColor(.red)
                                                    }
                                                    Text("\(Int(rv.price)) RWF").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                                }
                                            }
                                            .buttonStyle(.plain)
                                        }
                                    }
                                }
                            }
                        }
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
                                                .background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius).idsCardBorder(cornerRadius: IDS.Layout.cardCornerRadius)
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
                                HStack(spacing: 6) {
                                    FlameGlyph(size: 17)
                                    Text("Deals")
                                }
                                .font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(spacing: 10) {
                                        ForEach(deals) { d in
                                            VStack(alignment: .leading, spacing: 6) {
                                                ProductImageThumb(imageUrl: d.imageUrl, side: 96)
                                                Text(d.name).font(.caption).bold().foregroundColor(IDS.Colors.textPrimary).lineLimit(2)
                                                if let discountPercent = d.discountPercent, discountPercent > 0 {
                                                    Text("\(discountPercent)% off").font(.caption2).bold().foregroundColor(.red)
                                                }
                                                HStack(alignment: .lastTextBaseline, spacing: 4) {
                                                    Text("\(Int(d.price)) RWF").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                                    // Real strikethrough original price (2026-08-25, matches the
                                                    // Toss Shopping reference) -- same real originalPrice field the
                                                    // discount badge above already derives from.
                                                    if let originalPrice = d.originalPrice, originalPrice > d.price {
                                                        Text("\(Int(originalPrice)) RWF").font(.caption2).foregroundColor(IDS.Colors.textTertiary).strikethrough()
                                                    }
                                                }
                                                // rating/reviewCount (2026-08-25) -- real batched ProductReview
                                                // data, same real "no review yet -> no stars" honesty as this
                                                // app's Android client.
                                                if let rating = d.rating, let reviewCount = d.reviewCount, reviewCount > 0 {
                                                    HStack(spacing: 2) {
                                                        Image(systemName: "star.fill").font(.system(size: 9)).foregroundColor(Color(red: 0.96, green: 0.65, blue: 0.14))
                                                        Text(String(format: "%.1f (%d)", rating, reviewCount)).font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                                                    }
                                                }
                                                Text(d.stockQuantity.map { $0 == 0 ? "Out of stock" : "\($0) available" } ?? "Available")
                                                    .font(.caption2).foregroundColor(d.stockQuantity == 0 ? .red : IDS.Colors.textSecondary)
                                            }
                                            .frame(width: 120, alignment: .leading)
                                            .padding(10)
                                            .background(IDS.Colors.card)
                                            .cornerRadius(IDS.Layout.cardCornerRadius).idsCardBorder(cornerRadius: IDS.Layout.cardCornerRadius)
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
                                                // Real Coupang badge system (2026-08-05) -- see IdsBadge's own doc
                                                // comment. Matches Android ShopScreen.kt's own identical StatusBadge
                                                // treatment (this was plain Text on iOS until now).
                                                // Real live HH:MM:SS countdown (2026-08-25, direct Toss Shopping
                                                // reference screenshot) -- TimelineView ticks this to the second off
                                                // the same real v.deal.endsAt, no manual Timer/@State plumbing needed.
                                                TimelineView(.periodic(from: .now, by: 1)) { _ in
                                                    Text("⏰ \(formatTimeDealCountdownHms(v.deal.endsAt))").font(.caption2).bold().foregroundColor(IDS.Colors.danger)
                                                }
                                                IdsBadge("\(v.deal.remainingQuantity) left", tint: IDS.Colors.danger)
                                            }
                                            .frame(width: 120, alignment: .leading)
                                            .padding(10)
                                            .background(IDS.Colors.card)
                                            .cornerRadius(IDS.Layout.cardCornerRadius).idsCardBorder(cornerRadius: IDS.Layout.cardCornerRadius)
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
                            .padding(.vertical, 10)
                        } else if merchants == nil {
                            SkeletonBlock(height: 120)
                        } else if merchants!.isEmpty {
                            // Real copy-voice fix (item 244, round 5 of the empty-state
                            // pass, ported from the same-day Android fix): "registered
                            // yet" is honest about whose gap this is -- no store has
                            // joined yet, not something the reader is missing a step on.
                            Text(selectedCategory != nil || !searchInput.trimmingCharacters(in: .whitespaces).isEmpty ? "No merchants match your search — try a different category or search term." : "No stores registered yet — check back once merchants in your area join itunda Shop.")
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
                                .cornerRadius(IDS.Layout.cardCornerRadius).idsCardBorder(cornerRadius: IDS.Layout.cardCornerRadius)
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

    // businessType added 2026-08-25 (direct user directive: "we need everything
    // separated to avoid confusion, that's toss style, clear isolation") -- matches
    // Android's identical ShopScreen.kt fix and EatsScreen.swift's own existing
    // businessType: "RESTAURANT" call, which this mirrors.
    private func loadMerchants() async {
        do {
            let q = searchInput.trimmingCharacters(in: .whitespaces)
            let res = try await NetworkClient.shared.getShoppingMerchants(category: selectedCategory, businessType: "SHOP", q: q.isEmpty ? nil : q)
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

    // Real "recently viewed products" rail (2026-08-23) -- see
    // RecentlyViewedStores.swift's own doc comment.
    private func openProduct(_ product: MerchantProductDto, businessName: String) {
        selectedProduct = product
        recentlyViewedProducts = RecentlyViewedProductsStore.shared.add(
            RecentlyViewedProduct(id: product.id, merchantId: product.merchantId, businessName: businessName, name: product.name, price: product.price, imageUrl: product.imageUrl, discountPercent: product.discountPercent)
        )
    }

    // Real Coupang/Amazon-style "Buy it again" (2026-08-23) -- direct port of this
    // app's own real Eats "Reorder" (see EatsScreen.swift's handleReorder). Re-populates
    // the cross-merchant `cart` from a past order's still-active products and opens the
    // cart for review, same "review before a real-money action, not an instant one-tap
    // purchase" precedent Eats already established (a delivery address could be stale, a
    // price could have changed since). Commerce products never carry option groups
    // (only Eats' menu items do), so unlike Eats this needs no "drop items that now
    // require an option selection" sanitization -- only "drop items that are no longer
    // active."
    private func handleReorder(_ order: OrderDto) async {
        reorderingId = order.id
        reorderError = nil
        defer { reorderingId = nil }
        do {
            let orderDetail = try await NetworkClient.shared.getOrder(order.id)
            let menuRes = try await NetworkClient.shared.getMerchantProducts(merchantId: order.merchantId)
            guard orderDetail.success, menuRes.success else {
                reorderError = "Could not reorder."
                return
            }
            let activeProducts = Dictionary(uniqueKeysWithValues: menuRes.products.filter(\.active).map { ($0.id, $0) })
            var addedAny = false
            for item in orderDetail.items {
                guard let product = activeProducts[item.productId] else { continue }
                let key = "\(order.merchantId):\(product.id)"
                let existingQuantity = cart[key]?.quantity ?? 0
                cart[key] = CommerceCartLine(merchantId: order.merchantId, businessName: menuRes.merchant.businessName, product: product, quantity: existingQuantity + item.quantity)
                addedAny = true
            }
            guard addedAny else {
                reorderError = "None of the items from that order are available anymore."
                return
            }
            showCart = true
        } catch {
            reorderError = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

