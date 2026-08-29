import Foundation

// Real Naver Map-style transit/place-detail/weather additions (itunda Maps redesign,
// 2026-08-28) -- split into its own extension file from the start, since
// NetworkClient.swift is already at its frozen file-size-lint baseline with ~13 lines
// of slack (same pattern already established by NetworkClient+CoreServices.swift /
// NetworkClient+RecurringSavings.swift). Mirrors Android's MapsDtos.kt field-for-field.

public struct TransitStopDto: Decodable {
    public let id: String
    public let name: String
    public let latitude: Double
    public let longitude: Double
}
public struct TransitRouteDto: Decodable {
    public let id: String
    public let shortName: String?
    public let longName: String?
}
public struct TransitJourneyDto: Decodable {
    public let originStop: TransitStopDto
    public let destinationStop: TransitStopDto
    public let route: TransitRouteDto
    public let departureSecondsAfterMidnight: Int
    public let arrivalSecondsAfterMidnight: Int
    public let walkToOriginStopKm: Double
    public let walkFromDestinationStopKm: Double
}
public struct MapsTransitDirectionsResponse: Decodable { public let success: Bool; public let journeys: [TransitJourneyDto] }

public struct MapPlaceRatingDto: Decodable { public let average: Double?; public let count: Int }
public struct MapPlaceMenuItemDto: Decodable { public let id: String; public let name: String; public let price: Double; public let imageUrl: String?; public let active: Bool }
public struct MapPlaceUpdateDto: Decodable {
    public let id: String
    public let label: String
    public let title: String
    public let body: String
    public let periodStart: String?
    public let periodEnd: String?
    public let likeCount: Int
    public let createdAt: String
}
public struct MapPlaceDetailDto: Decodable {
    public let merchantId: String
    public let businessName: String
    public let category: String?
    public let photoUrl: String?
    public let photoUrls: [String]
    public let openingHours: String?
    public let phoneNumber: String?
    public let aiSummary: String?
    public let rating: MapPlaceRatingDto
    public let goodPointCounts: [String: Int]
    public let menu: [MapPlaceMenuItemDto]
    public let updates: [MapPlaceUpdateDto]
}
public struct MapPlaceDetailResponse: Decodable { public let success: Bool; public let place: MapPlaceDetailDto }

// Real gap found live (uncalled-endpoint sweep, 2026-08-29): the News tab already
// renders each update's real likeCount, but the like toggle itself
// (MerchantUpdateController.toggleLike) had zero caller anywhere -- the count was
// static text, not tappable.
public struct ToggleMerchantUpdateLikeResponse: Decodable { public let success: Bool; public let liked: Bool }

// Real, free, keyless Kigali weather (2026-08-28) -- see KigaliWeatherClient's own doc
// comment on the backend. `weather` is null when the real upstream is unreachable and
// there's no still-fresh cache -- never fabricated.
public struct KigaliWeatherDto: Decodable { public let temperatureCelsius: Double; public let condition: String; public let pm2_5: Double? }
public struct MapsWeatherResponse: Decodable { public let success: Bool; public let weather: KigaliWeatherDto? }

public struct EatsGoodPointsResponse: Decodable { public let success: Bool; public let counts: [String: Int]; public let goodPointOptions: [String] }

extension NetworkClient {
    public func getTransitDirections(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double) async throws -> MapsTransitDirectionsResponse {
        try await get("api/v1/maps/directions/transit", query: [
            URLQueryItem(name: "fromLat", value: String(fromLat)),
            URLQueryItem(name: "fromLng", value: String(fromLng)),
            URLQueryItem(name: "toLat", value: String(toLat)),
            URLQueryItem(name: "toLng", value: String(toLng)),
        ])
    }

    public func getMapPlaceDetail(merchantId: String) async throws -> MapPlaceDetailResponse {
        try await get("api/v1/maps/places/\(merchantId)")
    }

    public func toggleMerchantUpdateLike(updateId: String) async throws -> ToggleMerchantUpdateLikeResponse {
        try await authenticatedPost("api/v1/merchant/updates/\(updateId)/like", body: EmptyRequest())
    }

    public func getKigaliWeather() async throws -> MapsWeatherResponse {
        try await get("api/v1/maps/weather")
    }

    public func getRestaurantGoodPoints(_ restaurantId: String) async throws -> EatsGoodPointsResponse {
        try await get("api/v1/eats/restaurants/\(restaurantId)/good-points")
    }
}
