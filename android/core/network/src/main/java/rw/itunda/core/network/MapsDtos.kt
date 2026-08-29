package rw.itunda.core.network

// Extracted from ApiService.kt (itunda Maps redesign, 2026-08-28) -- that file crossed
// its frozen file-size-lint baseline once the real transit/place-detail/weather DTOs
// below were added. All real Maps-domain response/DTO shapes live here now; the
// Retrofit endpoint declarations themselves stay in ApiService.kt (an interface can't
// span files, only its supporting data classes can be moved out).

// Real "search this map" + "directions" (2026-07-19) -- see rw.itunda.maps.MapsService's
// own doc comment on the backend for why these are a new, general-purpose front door
// onto itunda's already-deployed self-hosted Nominatim/OSRM.
data class PlaceSearchResultDto(val displayName: String, val latitude: Double, val longitude: Double)
data class MapsSearchResponse(val success: Boolean, val results: List<PlaceSearchResultDto>)
data class MapsReverseGeocodeResponse(val success: Boolean, val placeName: String?)
data class RouteStepDto(val instruction: String, val distanceMeters: Double, val streetName: String?)
data class RouteResultDto(val distanceKm: Double, val durationMinutes: Double, val geometry: List<List<Double>>, val steps: List<RouteStepDto> = emptyList())
data class MapsDirectionsResponse(val success: Boolean, val route: RouteResultDto)
// Real alternative routes (2026-07-22) -- see OsrmRoutingClient.routeAlternatives' own
// doc comment on the backend. Often just a single-element list -- OSRM itself decides
// whether a real alternative exists for a given trip.
data class MapsDirectionsAlternativesResponse(val success: Boolean, val routes: List<RouteResultDto>)
// Ordered multi-stop directions (2026-07-22). The backend deliberately accepts only
// 2–5 Rwanda waypoints so the self-hosted OSRM request and the mobile itinerary stay
// legible. The returned RouteResultDto is one continuous road route through that order.
data class ItineraryWaypointRequest(val latitude: Double, val longitude: Double)
data class ItineraryDirectionsRequest(val waypoints: List<ItineraryWaypointRequest>, val mode: String = "DRIVING")
data class MapsItineraryResponse(val success: Boolean, val route: RouteResultDto)

// Real Kigali GTFS-based transit journeys (2026-08-28, itunda Maps redesign) -- see
// TransitRoutingService's own doc comment on the backend for the honest, explicitly
// scoped v1 (direct routes only, real schedule-based departure times, never live GPS).
// A real empty list means no real direct transit option was found, not an error.
data class TransitStopDto(val id: String, val name: String, val latitude: Double, val longitude: Double)
data class TransitRouteDto(val id: String, val shortName: String?, val longName: String?)
data class TransitJourneyDto(
    val originStop: TransitStopDto,
    val destinationStop: TransitStopDto,
    val route: TransitRouteDto,
    val departureSecondsAfterMidnight: Int,
    val arrivalSecondsAfterMidnight: Int,
    val walkToOriginStopKm: Double,
    val walkFromDestinationStopKm: Double,
)
data class MapsTransitDirectionsResponse(val success: Boolean, val journeys: List<TransitJourneyDto>)

// Real consolidated place-detail (2026-08-28, itunda Maps redesign) -- see
// MapsPlaceDetailService's own doc comment on the backend. The one real source the
// tabbed place-detail panel reads from, replacing the old per-field ad hoc lookups.
data class MapPlaceRatingDto(val average: Double?, val count: Long)
data class MapPlaceMenuItemDto(val id: String, val name: String, val price: Double, val imageUrl: String?, val active: Boolean)
data class MapPlaceUpdateDto(
    val id: String,
    val label: String,
    val title: String,
    val body: String,
    val periodStart: String?,
    val periodEnd: String?,
    val likeCount: Long,
    val createdAt: String,
)
data class MapPlaceDetailDto(
    val merchantId: String,
    val businessName: String,
    val category: String?,
    val photoUrl: String?,
    val photoUrls: List<String>,
    val openingHours: String?,
    val phoneNumber: String?,
    val aiSummary: String?,
    val rating: MapPlaceRatingDto,
    val goodPointCounts: Map<String, Int>,
    val menu: List<MapPlaceMenuItemDto>,
    val updates: List<MapPlaceUpdateDto>,
)
data class MapPlaceDetailResponse(val success: Boolean, val place: MapPlaceDetailDto)

// Real gap found live (uncalled-endpoint sweep, 2026-08-29): the News tab above
// already renders each update's real likeCount, but the like toggle itself
// (MerchantUpdateController.toggleLike) had zero caller anywhere -- the count was
// static text, not tappable.
data class ToggleMerchantUpdateLikeResponse(val success: Boolean, val liked: Boolean)

// Real, free, keyless Kigali weather (2026-08-28, itunda Maps redesign) -- see
// KigaliWeatherClient's own doc comment on the backend. `weather` is null when the
// real upstream is unreachable and there's no still-fresh cache -- never fabricated.
data class KigaliWeatherDto(val temperatureCelsius: Double, val condition: String, val pm2_5: Double?)
data class MapsWeatherResponse(val success: Boolean, val weather: KigaliWeatherDto?)
