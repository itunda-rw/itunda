import CoreNetwork

// Real fix (2026-08-26): split out of ShopScreen.swift once that file grew past its
// file-size-lint baseline. Pure async network handlers extracted from
// CommerceShopContent's own methods -- explicit params/return values instead of
// `self.` state mutation, same technique used for Android's ShopDetailDispatch.kt.

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
