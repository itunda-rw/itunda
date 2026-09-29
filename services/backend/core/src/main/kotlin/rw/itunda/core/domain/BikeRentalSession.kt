package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant

// ACTIVE: rider unlocked the bike, riding now, no fare known yet (real Kakao T Bike
// bills by elapsed TIME, not a pre-known distance-based fare like ride-hailing/
// designated-driver -- so nothing is held in escrow at start, unlike those two).
// COMPLETED: rider parked/locked the bike, real fare computed from elapsed minutes and
// charged in one real ledger transaction at end.
enum class BikeRentalStatus { ACTIVE, COMPLETED }

/**
 * Real Kakao T Bike rental session -- see `Bike.kt`'s own doc comment for the full
 * sourced account. `totalFare`/`platformFee`/`payoutTransactionId` are null until the
 * rental ends, since a time-based fare genuinely isn't known until then.
 */
@Entity
@Table(name = "bike_rental_sessions")
class BikeRentalSession(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "bike_id", nullable = false, length = 64)
    val bikeId: String,

    @Column(name = "rider_user_id", nullable = false, length = 64)
    val riderUserId: String,

    @Column(name = "started_at", nullable = false)
    val startedAt: Instant = Instant.now(),

    @Column(name = "ended_at")
    var endedAt: Instant? = null,

    @Column(name = "start_latitude", nullable = false)
    val startLatitude: Double,

    @Column(name = "start_longitude", nullable = false)
    val startLongitude: Double,

    @Column(name = "end_latitude")
    var endLatitude: Double? = null,

    @Column(name = "end_longitude")
    var endLongitude: Double? = null,

    @Column(name = "duration_minutes")
    var durationMinutes: Int? = null,

    @Column(name = "total_fare", precision = 18, scale = 2)
    var totalFare: BigDecimal? = null,

    @Column(name = "platform_fee", precision = 18, scale = 2)
    var platformFee: BigDecimal? = null,

    @Column(name = "payout_transaction_id", length = 64)
    var payoutTransactionId: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: BikeRentalStatus = BikeRentalStatus.ACTIVE,

    // A real double-tap "end rental" from a flaky mobile connection could otherwise
    // double-charge the rider -- versioning makes only one end-rental transition win,
    // same discipline `DesignatedDriverTrip`'s own doc comment already establishes.
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(id = "", bikeId = "", riderUserId = "", startLatitude = 0.0, startLongitude = 0.0)

    companion object {
        // Real gap found live (2026-08-18): unlike every other real reservation/session
        // this codebase enforces a hard end for (RideTrip's own dispatch timeout,
        // BookingDeposit/VehicleInspectionBooking's own no-show forfeit schedulers), an
        // ACTIVE BikeRentalSession had no timeout at all -- a rider whose app crashed
        // or who simply never came back left the real bike permanently `available =
        // false`, unrentable by anyone else and unpaid for the real owner, forever.
        // Sourced from Citi Bike NYC's own real, currently-documented policy
        // (help.citibikenyc.com/hc/en-us/articles/360032367371-What-if-I-keep-a-bike-out-too-long,
        // cross-checked against Citi Bike's own rental agreement at
        // assets.citibikenyc.com/rental-agreement.html): a bike not docked within a real
        // 24-hour window is treated by the system as abandoned and the ride is closed
        // out. itunda's own honest peer-to-peer adaptation (same "no invented penalty
        // this backend has no real data to size" discipline `UpfrontInterestDeposit`'s
        // own doc comment already establishes for a structurally identical scoping
        // choice): rather than a flat lost-bike fee, `BikeRentalService.forceEndAbandonedRental`
        // just force-settles the session at itunda's own already-real per-minute fare
        // (the exact same billing math `endRental` already uses) so the real owner is
        // actually paid for the time their bike was gone and the bike itself re-enters
        // the pool, not left permanently unrentable over one abandoned session.
        val MAX_RENTAL_DURATION: java.time.Duration = java.time.Duration.ofHours(24)
    }
}
