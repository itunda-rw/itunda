import Foundation
import CoreNetwork

/// Real recent-searches list (2026-07-22) -- the other half of the same
/// "no autocomplete/recent-searches" gap bank-mfe already closed on the web, ported here
/// to match (mirrors Android's RecentMapSearchesStore.kt). Naver/Kakao Maps' own real
/// recent-searches list is a purely client-side, per-device convenience (no account-wide
/// sync), so this is plain local persistence, not a fabricated backend feature.
///
/// Backed by plain UserDefaults + Codable (not Keychain like KeychainTokenStore.swift --
/// this holds place names/coordinates, not credentials), same convention
/// OfflineActionQueue.swift already established for exactly this kind of small local
/// JSON blob rather than pulling in Core Data/SwiftData for one small list.
final class RecentMapSearchesStore {
    static let shared = RecentMapSearchesStore()

    private let storageKey = "itunda_map_recent_searches"
    private let defaults = UserDefaults.standard

    private init() {}

    func getAll() -> [PlaceSearchResultDto] {
        guard let data = defaults.data(forKey: storageKey) else { return [] }
        return (try? JSONDecoder().decode([PlaceSearchResultDto].self, from: data)) ?? []
    }

    @discardableResult
    func add(_ place: PlaceSearchResultDto) -> [PlaceSearchResultDto] {
        let deduped = getAll().filter { $0.latitude != place.latitude || $0.longitude != place.longitude }
        let next = Array(([place] + deduped).prefix(8))
        persist(next)
        return next
    }

    func clear() {
        defaults.removeObject(forKey: storageKey)
    }

    private func persist(_ places: [PlaceSearchResultDto]) {
        if let data = try? JSONEncoder().encode(places) {
            defaults.set(data, forKey: storageKey)
        }
    }
}
