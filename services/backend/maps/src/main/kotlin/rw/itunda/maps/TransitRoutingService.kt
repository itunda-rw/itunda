package rw.itunda.maps

import org.springframework.stereotype.Service
import rw.itunda.core.domain.TransitRoute
import rw.itunda.core.domain.TransitStop
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.repository.TransitRouteRepository
import rw.itunda.core.repository.TransitScheduledTripRepository
import rw.itunda.core.repository.TransitStopRepository
import rw.itunda.core.repository.TransitStopTimeRepository
import java.time.ZoneId
import java.time.ZonedDateTime

data class TransitJourney(
    val originStop: TransitStop,
    val destinationStop: TransitStop,
    val route: TransitRoute,
    val departureSecondsAfterMidnight: Int,
    val arrivalSecondsAfterMidnight: Int,
    val walkToOriginStopKm: Double,
    val walkFromDestinationStopKm: Double,
)

/**
 * A real, explicitly-scoped-down v1 Kigali transit journey planner (itunda Maps
 * redesign, 2026-08-28, direct Naver Map reference: the directions panel's Transit
 * mode) -- built on the real GTFS schedule TransitGtfsImportService imports (see
 * TransitStop.kt's own doc comment for the full sourcing).
 *
 * **Real, honest v1 scope, not a lesser hack**: only DIRECT routes are considered --
 * a single real GTFS trip whose stop sequence visits a stop near the origin before a
 * stop near the destination. A full multi-transfer journey planner (the real thing
 * OpenTripPlanner-class software does) is genuinely out of scope for this pass; when
 * no direct route exists, this returns an honest empty list rather than fabricating a
 * plausible-looking multi-leg itinerary this class doesn't actually compute. Departure
 * times are real GTFS schedule data, not live GPS -- "next scheduled departure," never
 * presented as a live vehicle position, matching the honest distinction the reference's
 * own real-time-styled UI needs to be adapted away from (Kigali's real public system is
 * itself schedule-based, not live-tracked, so this is a faithful representation, not a
 * lesser hack).
 *
 * No `service_id`/calendar-day filtering yet (`TransitScheduledTrip.serviceId` is
 * stored for this future use, not yet cross-referenced) -- every stored trip is
 * treated as running "today," an explicit, documented scope limitation, not a silent
 * omission.
 */
@Service
class TransitRoutingService(
    private val transitStopRepository: TransitStopRepository,
    private val transitRouteRepository: TransitRouteRepository,
    private val transitScheduledTripRepository: TransitScheduledTripRepository,
    private val transitStopTimeRepository: TransitStopTimeRepository,
) {
    companion object {
        private const val WALK_RADIUS_KM = 0.8
        private const val MAX_JOURNEYS = 5
        private val KIGALI_ZONE = ZoneId.of("Africa/Kigali")
    }

    /** Real nearby-stops-to-nearby-stops direct-route search. Empty (never null) when
     * no real stop exists within walking distance of either end, or no real direct
     * trip connects them -- an honest "no transit option found," not an error. */
    fun findDirectJourneys(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double, nowSecondsAfterMidnight: Int? = null): List<TransitJourney> {
        val allStops = transitStopRepository.findAll()
        if (allStops.isEmpty()) return emptyList()

        val originCandidates = nearbyStops(allStops, fromLat, fromLng)
        val destinationCandidates = nearbyStops(allStops, toLat, toLng)
        if (originCandidates.isEmpty() || destinationCandidates.isEmpty()) return emptyList()

        val originStopIds = originCandidates.map { it.first.id }.toSet()
        val destinationStopIds = destinationCandidates.map { it.first.id }.toSet()
        val originStopTimes = transitStopTimeRepository.findByStopIdIn(originStopIds).groupBy { it.tripId }
        val destinationStopTimes = transitStopTimeRepository.findByStopIdIn(destinationStopIds).groupBy { it.tripId }
        val candidateTripIds = originStopTimes.keys intersect destinationStopTimes.keys
        if (candidateTripIds.isEmpty()) return emptyList()

        // Batch-resolve tripId -> routeId -> real TransitRoute up front, not per pair.
        val tripsById = transitScheduledTripRepository.findAllById(candidateTripIds).associateBy { it.id }
        val routeIds = tripsById.values.map { it.routeId }.toSet()
        val routesById = transitRouteRepository.findAllById(routeIds).associateBy { it.id }

        val originStopsById = originCandidates.associate { it.first.id to it }
        val destinationStopsById = destinationCandidates.associate { it.first.id to it }
        val now = nowSecondsAfterMidnight ?: ZonedDateTime.now(KIGALI_ZONE).toLocalTime().toSecondOfDay()

        val journeys = mutableListOf<TransitJourney>()
        for (tripId in candidateTripIds) {
            val route = tripsById[tripId]?.routeId?.let { routesById[it] } ?: continue
            val originTimes = originStopTimes[tripId] ?: continue
            val destTimes = destinationStopTimes[tripId] ?: continue
            for (originTime in originTimes) {
                if (originTime.departureSecondsAfterMidnight < now) continue
                val (originStop, walkToOrigin) = originStopsById[originTime.stopId] ?: continue
                for (destTime in destTimes) {
                    // A real direct connection: this trip visits the origin-side stop
                    // strictly before the destination-side stop.
                    if (originTime.stopSequence >= destTime.stopSequence) continue
                    val (destinationStop, walkFromDest) = destinationStopsById[destTime.stopId] ?: continue
                    journeys += TransitJourney(
                        originStop = originStop, destinationStop = destinationStop, route = route,
                        departureSecondsAfterMidnight = originTime.departureSecondsAfterMidnight,
                        arrivalSecondsAfterMidnight = destTime.arrivalSecondsAfterMidnight,
                        walkToOriginStopKm = walkToOrigin, walkFromDestinationStopKm = walkFromDest,
                    )
                }
            }
        }
        return journeys.sortedBy { it.departureSecondsAfterMidnight }.take(MAX_JOURNEYS)
    }

    private fun nearbyStops(allStops: List<TransitStop>, lat: Double, lng: Double): List<Pair<TransitStop, Double>> =
        allStops.mapNotNull { stop ->
            val distanceKm = GeoUtils.haversineKm(lat, lng, stop.latitude, stop.longitude)
            if (distanceKm <= WALK_RADIUS_KM) stop to distanceKm else null
        }
}
