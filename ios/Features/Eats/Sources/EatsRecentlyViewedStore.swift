import Foundation

/// Real "recently viewed restaurants" convenience store (2026-08-23) -- ported from
/// Android's RecentlyViewedRestaurantsStore.kt (sourced from real Baemin/Coupang
/// Eats). Purely client-side, per-device UserDefaults -- no account-wide sync, no
/// backend needed, same real scope the Android store already established. Split out
/// of App/Sources/RecentlyViewedStores.swift (2026-09-06, Eats product-completeness
/// pass) when Eats moved into its own Feature module -- RecentlyViewedProduct/
/// RecentlyViewedProductsStore in that file are Shop-only and stay in App/Sources.

struct RecentlyViewedRestaurant: Codable, Identifiable {
    var id: String { merchantId }
    let merchantId: String
    let businessName: String
    let category: String?
    let photoUrl: String?
}

final class RecentlyViewedRestaurantsStore {
    static let shared = RecentlyViewedRestaurantsStore()
    private let defaults = UserDefaults.standard
    private let key = "itunda_eats_recently_viewed"
    private let cap = 12

    private init() {}

    func getAll() -> [RecentlyViewedRestaurant] {
        guard let data = defaults.data(forKey: key) else { return [] }
        return (try? JSONDecoder().decode([RecentlyViewedRestaurant].self, from: data)) ?? []
    }

    @discardableResult
    func add(_ restaurant: RecentlyViewedRestaurant) -> [RecentlyViewedRestaurant] {
        var next = [restaurant] + getAll().filter { $0.merchantId != restaurant.merchantId }
        next = Array(next.prefix(cap))
        if let data = try? JSONEncoder().encode(next) {
            defaults.set(data, forKey: key)
        }
        return next
    }
}
