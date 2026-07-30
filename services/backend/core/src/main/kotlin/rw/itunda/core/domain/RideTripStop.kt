package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A real Kakao T-style multi-stop waypoint (item 214) -- Kakao T's own real, currently-
 * live feature: a passenger can add up to 3 extra stops between pickup and dropoff, the
 * driver must reach each one in order. `sequence` (0-indexed, 0 = first stop after
 * pickup) fixes the real visit order; `arrivedAt` is set exactly once, by
 * `RideTripService.arriveAtStop`, the moment the driver marks having reached it -- a
 * driver can't skip ahead to a later stop before an earlier one is real-marked arrived.
 *
 * `fare`/`distanceKm` on `RideTrip` itself already cover the *whole* real route through
 * every stop (pickup -> stop 1 -> ... -> dropoff, summed as consecutive
 * `GeoUtils.haversineKm` legs) -- this table only records the real waypoints and their
 * real arrival progress, not a second fare breakdown per leg.
 */
@Entity
@Table(name = "ride_trip_stops")
class RideTripStop(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "trip_id", nullable = false, length = 64)
    val tripId: String,

    @Column(name = "sequence", nullable = false)
    val sequence: Int,

    @Column(nullable = false, length = 500)
    val address: String,

    @Column(nullable = false)
    val latitude: Double,

    @Column(nullable = false)
    val longitude: Double,

    @Column(name = "arrived_at")
    var arrivedAt: Instant? = null,
) {
    protected constructor() : this(id = "", tripId = "", sequence = 0, address = "", latitude = 0.0, longitude = 0.0)
}
