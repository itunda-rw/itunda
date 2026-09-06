import CoreNetwork

// Real fix (2026-08-26): split out of ShopScreen.swift once that file grew past its
// file-size-lint baseline. Pure async network handlers extracted from
// CommerceShopContent's own methods -- explicit params/return values instead of
// `self.` state mutation, same technique used for Android's ShopDetailDispatch.kt.

// TalkScreen.errorMessage (App-only, 23 other real callers) isn't reachable from a
// Feature module, so this Feature keeps its own local copy -- same real precedent
// FeatureMy's/FeatureEats'/FeatureRide's own copies already established. Internal
// (not private), since 4 files across this module call it.
func errorMessage(_ statusCode: Int) -> String {
    switch statusCode {
    case 400: return "Please check what you entered and try again."
    case 401, 403: return "You don't have access to do that."
    case 404: return "That couldn't be found."
    case 409: return "That's already been done, or is being processed."
    case 422: return "Insufficient funds for this order."
    case 429: return "Too many attempts -- please wait a moment and try again."
    default: return "Something went wrong. Please try again."
    }
}

func loadShopFollowedMerchantIds() async -> Set<String>? {
    do {
        let res = try await NetworkClient.shared.getMyFollowedMerchants()
        return Set(res.follows.map { $0.merchantId })
    } catch {
        return nil
    }
}

func toggleShopMerchantFollow(_ merchantId: String, followedMerchantIds: Set<String>) async -> (newIds: Set<String>?, error: String?) {
    do {
        if followedMerchantIds.contains(merchantId) {
            _ = try await NetworkClient.shared.unfollowMerchant(merchantId: merchantId)
            var ids = followedMerchantIds; ids.remove(merchantId)
            return (ids, nil)
        } else {
            _ = try await NetworkClient.shared.followMerchant(merchantId: merchantId)
            var ids = followedMerchantIds; ids.insert(merchantId)
            return (ids, nil)
        }
    } catch {
        return (nil, "Couldn't reach itunda. Check your connection and try again.")
    }
}

func loadShopFavoriteProductIds() async -> Set<String>? {
    do {
        let res = try await NetworkClient.shared.getMyFavoriteProducts()
        return Set(res.favorites.map { $0.productId })
    } catch {
        return nil
    }
}

func toggleShopProductFavoriteIds(_ productId: String, favoriteProductIds: Set<String>) async -> (newIds: Set<String>?, error: String?) {
    do {
        if favoriteProductIds.contains(productId) {
            _ = try await NetworkClient.shared.removeProductFavorite(productId)
            var ids = favoriteProductIds; ids.remove(productId)
            return (ids, nil)
        } else {
            _ = try await NetworkClient.shared.addProductFavorite(productId)
            var ids = favoriteProductIds; ids.insert(productId)
            return (ids, nil)
        }
    } catch {
        return (nil, "Couldn't reach itunda. Check your connection and try again.")
    }
}

struct ShopMerchantsLoadResult {
    let merchants: [ShoppingMerchantDto]?
    let error: String?
}

// Extracted out of CommerceShopContent.loadMerchants (2026-09-07, Shop/Commerce
// product-completeness pass) once ShopScreen.swift crossed the file-size-lint
// 500-line guideline -- same real "explicit params/return values" technique this
// file's own doc comment already established.
func loadShopMerchants(selectedCategory: String?, searchInput: String) async -> ShopMerchantsLoadResult {
    do {
        let q = searchInput.trimmingCharacters(in: .whitespaces)
        let res = try await NetworkClient.shared.getShoppingMerchants(category: selectedCategory, businessType: "SHOP", q: q.isEmpty ? nil : q)
        return ShopMerchantsLoadResult(merchants: res.merchants, error: nil)
    } catch {
        return ShopMerchantsLoadResult(merchants: nil, error: "Couldn't reach itunda. Check your connection and try again.")
    }
}

struct ShopMerchantProductsLoadResult {
    let products: [MerchantProductDto]?
    let error: String?
}

// Extracted out of CommerceShopContent.openMerchant (2026-09-07, Shop/Commerce
// product-completeness pass) -- same real extraction reasoning as
// loadShopMerchants above.
func loadShopMerchantProducts(merchantId: String) async -> ShopMerchantProductsLoadResult {
    do {
        let res = try await NetworkClient.shared.getMerchantProducts(merchantId: merchantId)
        // Real filter (2026-08-25, direct user feedback: "booking... supposed to be
        // in itunda place not in itunda shopping") -- a product with a real
        // durationMinutes set is a real-time appointment at this merchant's
        // physical location, not a cart-able online good, so it no longer shows in
        // Shop's own catalog at all. Booking now lives in itunda Place
        // (Features/Maps/Sources/MapsBooking.swift), reachable from the same real
        // merchant pinned on the map.
        return ShopMerchantProductsLoadResult(products: res.products.filter { $0.durationMinutes == nil }, error: nil)
    } catch {
        return ShopMerchantProductsLoadResult(products: nil, error: "Couldn't reach itunda. Check your connection and try again.")
    }
}

// Extracted out of CommerceShopContent.searchProducts (2026-09-07, Shop/Commerce
// product-completeness pass) alongside ShopProductSearchSection in
// ShopBrowseComponents.swift -- same real extraction reasoning as loadShopMerchants
// above. Matches the original's own "empty array, not nil, on error" behavior so a
// failed search still clears the loading state into a real (empty) "no results"
// view rather than reverting to the pre-search state.
func searchShopProducts(_ query: String) async -> [ProductSearchResultDto]? {
    do {
        return try await NetworkClient.shared.searchProducts(query, businessType: "SHOP").products
    } catch {
        return []
    }
}

struct ShopReorderResult {
    let newCart: [String: CommerceCartLine]?
    let error: String?
    let opened: Bool
}

// Real Coupang/Amazon-style "Buy it again" -- direct port of Eats' own real "Reorder".
func performShopReorder(_ order: OrderDto, cart: [String: CommerceCartLine]) async -> ShopReorderResult {
    do {
        let orderDetail = try await NetworkClient.shared.getOrder(order.id)
        let menuRes = try await NetworkClient.shared.getMerchantProducts(merchantId: order.merchantId)
        guard orderDetail.success, menuRes.success else {
            return ShopReorderResult(newCart: nil, error: "Could not reorder.", opened: false)
        }
        let activeProducts = Dictionary(uniqueKeysWithValues: menuRes.products.filter(\.active).map { ($0.id, $0) })
        var newCart = cart
        var addedAny = false
        for item in orderDetail.items {
            guard let product = activeProducts[item.productId] else { continue }
            let key = "\(order.merchantId):\(product.id)"
            let existingQuantity = newCart[key]?.quantity ?? 0
            newCart[key] = CommerceCartLine(merchantId: order.merchantId, businessName: menuRes.merchant.businessName, product: product, quantity: existingQuantity + item.quantity)
            addedAny = true
        }
        guard addedAny else {
            return ShopReorderResult(newCart: nil, error: "None of the items from that order are available anymore.", opened: false)
        }
        return ShopReorderResult(newCart: newCart, error: nil, opened: true)
    } catch {
        return ShopReorderResult(newCart: nil, error: "Couldn't reach itunda. Check your connection and try again.", opened: false)
    }
}
