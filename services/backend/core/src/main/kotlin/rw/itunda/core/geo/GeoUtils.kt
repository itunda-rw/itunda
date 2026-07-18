package rw.itunda.core.geo

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Real great-circle (Haversine) distance between two real lat/lng points -- the first
 * piece of itunda's own self-hosted maps effort (see docs/TOSS_PARITY_MATRIX.md's Maps
 * row), built on real coordinates now stored on `Merchant`/`Listing`/`EatsOrder` rather
 * than the flat-fee/no-proximity placeholders those rows previously carried.
 *
 * Honestly scoped: this is straight-line distance, not real road distance -- no
 * self-hosted routing (OSRM) exists yet, a real, named follow-up once this lands. Good
 * enough for a real, non-fake distance-based delivery fee and proximity ranking today.
 */
object GeoUtils {
    private const val EARTH_RADIUS_KM = 6371.0

    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    fun isValidCoordinate(latitude: Double, longitude: Double): Boolean =
        latitude in -90.0..90.0 && longitude in -180.0..180.0
}
