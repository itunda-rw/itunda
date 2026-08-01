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
}
