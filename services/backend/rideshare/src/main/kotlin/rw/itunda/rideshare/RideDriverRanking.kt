package rw.itunda.rideshare

import rw.itunda.core.domain.RideDriver
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.geo.OsrmRoutingClient
import rw.itunda.core.geo.TravelMode
import java.math.BigDecimal
import java.math.RoundingMode

// Real fix (2026-08-26): split out of RideTripService.kt once that file grew past its
// file-size-lint baseline. Pure candidate-filtering/ranking logic, extracted from
// RideTripService.rankNearbyDrivers -- no DB/ledger access, same "structurally the
// same real 'coarse filter, real rank' shape EatsOrderService.rankNearbyRiders
// already established" this codebase's own doc comment already names. The repo reads
// (actuallyBusy/candidatePool resolution) stay in RideTripService itself; this is just
// the real Kakao Mobility-sourced ranking rule applied to already-fetched data.
internal fun rankRideDriverCandidates(
    pickupLat: Double,
    pickupLng: Double,
    dropoffLat: Double,
    dropoffLng: Double,
    excludedUserIds: Set<String>,
    candidates: List<RideDriver>,
    busyDriverIds: Set<String>,
    minOffersForAcceptanceFilter: Int,
    minAcceptanceRate: BigDecimal,
): List<Pair<RideDriver, Double>> {
    return candidates
        .filterNot { it.userId in excludedUserIds }
        .filterNot { it.id in busyDriverIds }
        .filterNot { driver ->
            driver.totalOffers >= minOffersForAcceptanceFilter &&
                BigDecimal(driver.totalAccepted).divide(BigDecimal(driver.totalOffers), 4, RoundingMode.HALF_UP) < minAcceptanceRate
        }
        .filterNot { driver ->
            val destLat = driver.destinationLatitude
            val destLng = driver.destinationLongitude
            if (destLat == null || destLng == null) {
                false
            } else {
                val distanceFromCurrent = GeoUtils.haversineKm(driver.currentLatitude!!, driver.currentLongitude!!, destLat, destLng)
                val distanceFromDropoff = GeoUtils.haversineKm(dropoffLat, dropoffLng, destLat, destLng)
                distanceFromDropoff >= distanceFromCurrent
            }
        }
        .map { driver -> driver to GeoUtils.haversineKm(pickupLat, pickupLng, driver.currentLatitude!!, driver.currentLongitude!!) }
        .sortedBy { (_, distanceKm) -> distanceKm }
}

// Real distance+fare calculation, extracted from RideTripService.requestTrip
// (2026-08-24) so a real fare preview (estimateFare) can never drift from what a real
// request actually charges -- same OSRM routeThrough call with the identical never-
// fail haversine fallback, same formula. Pure computation, no DB/ledger access.
internal fun calculateRideFare(
    osrmRoutingClient: OsrmRoutingClient,
    routePoints: List<Pair<Double, Double>>,
    baseFare: BigDecimal,
    perKmRate: BigDecimal,
    minFare: BigDecimal,
): Pair<BigDecimal, BigDecimal> {
    val totalDistanceKm = osrmRoutingClient.routeThrough(routePoints, TravelMode.DRIVING)?.distanceKm
        ?: routePoints.zipWithNext().sumOf { (a, b) -> GeoUtils.haversineKm(a.first, a.second, b.first, b.second) }
    val distanceKm = BigDecimal(totalDistanceKm).setScale(3, RoundingMode.HALF_UP)
    val fare = baseFare.add(perKmRate.multiply(distanceKm)).setScale(2, RoundingMode.HALF_UP).max(minFare)
    return distanceKm to fare
}
