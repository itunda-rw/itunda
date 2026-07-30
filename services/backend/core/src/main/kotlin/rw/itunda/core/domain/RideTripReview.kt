package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A real Kakao T-style post-trip driver rating (item 213) -- mirrors `EatsReview`'s own
 * proven shape exactly: one real review per real `RideTrip` (a real DB unique constraint
 * on `trip_id`, not just an application-level check), submitted by the real passenger
 * who took the trip, rating the driver who completed it.
 *
 * **Honest v1 scope**: passenger-rates-driver only, not the real bidirectional
 * driver-rates-passenger Kakao T also has -- deliberately deferred, same "purely
 * additive, one direction at a time" discipline this codebase already uses (see
 * `EatsReview`'s own restaurant+rider dual rating, itself built incrementally). The
 * aggregate driver rating is computed by a real `AVG`/`COUNT` query at read time
 * (`RideTripReviewRepository.getDriverRatingSummary`), not a running counter cached on
 * `RideDriver` -- avoids touching that already-tested entity's write path for a purely
 * additive feature. Not wired into dispatch ranking this pass (see
 * `RideTripService.rankNearbyDrivers`'s own acceptance-rate filter) -- a separate,
 * deliberately deferred follow-up, so this feature ships without touching already-proven
 * dispatch behavior.
 */
@Entity
@Table(name = "ride_trip_reviews")
class RideTripReview(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "trip_id", nullable = false, unique = true, length = 64)
    val tripId: String,

    @Column(name = "passenger_id", nullable = false, length = 64)
    val passengerId: String,

    @Column(name = "driver_id", nullable = false, length = 64)
    val driverId: String,

    @Column(name = "rating", nullable = false)
    val rating: Int,

    @Column(name = "comment", length = 1000)
    val comment: String?,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", tripId = "", passengerId = "", driverId = "", rating = 0, comment = null)
}
