import Foundation

/// Real "recently viewed" convenience store (2026-08-23) -- ported from Android's
/// RecentlyViewedProductsStore.kt (2026-08-10, itself sourced from real Coupang/Naver/
/// Toss/Kakao Shopping), which iOS never got -- a real, confirmed gap: this feature
/// existed only on Android before now, not iOS, not web. Purely client-side,
/// per-device UserDefaults -- no account-wide sync, no backend needed, same real scope
/// the Android store already established. Plain JSON, not the Keychain, same
/// reasoning DeviceStore.swift's own doc comment already gives for a non-secret local
/// convenience value.
///
/// The Eats sibling this file originally also carried (RecentlyViewedRestaurant/
/// RecentlyViewedRestaurantsStore, sourced from RecentlyViewedRestaurantsStore.kt) moved
/// to Features/Eats/Sources/EatsRecentlyViewedStore.swift (2026-09-06, Eats
/// product-completeness pass) when Eats moved into its own Feature module.

struct RecentlyViewedProduct: Codable, Identifiable {
    var id: String
    let merchantId: String
    let businessName: String
    let name: String
    let price: Double
    let imageUrl: String?
    let discountPercent: Int?
}

final class RecentlyViewedProductsStore {
    static let shared = RecentlyViewedProductsStore()
    private let defaults = UserDefaults.standard
    private let key = "itunda_shop_recently_viewed"
    private let cap = 12

    private init() {}

    func getAll() -> [RecentlyViewedProduct] {
        guard let data = defaults.data(forKey: key) else { return [] }
        return (try? JSONDecoder().decode([RecentlyViewedProduct].self, from: data)) ?? []
    }

    @discardableResult
    func add(_ product: RecentlyViewedProduct) -> [RecentlyViewedProduct] {
        var next = [product] + getAll().filter { $0.id != product.id }
        next = Array(next.prefix(cap))
        if let data = try? JSONEncoder().encode(next) {
            defaults.set(data, forKey: key)
        }
        return next
    }
}
