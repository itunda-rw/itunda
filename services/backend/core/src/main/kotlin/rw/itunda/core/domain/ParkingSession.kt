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

// ACTIVE: renter checked in, parked now, no fare known yet (real Kakao T Parking bills
// by elapsed TIME, not a pre-known amount -- so nothing is held in escrow at start,
// same shape BikeRentalSession's own doc comment already establishes). COMPLETED:
// renter checked out, real fare computed from elapsed hours and charged in one real
// ledger transaction at end.
enum class ParkingSessionStatus { ACTIVE, COMPLETED }

/**
 * Real Kakao T Parking session -- see `ParkingSpot.kt`'s own doc comment for the full
 * sourced account. `totalFare`/`platformFee`/`payoutTransactionId` are null until the
 * session ends, since an hourly fare genuinely isn't known until checkout.
 */
@Entity
@Table(name = "parking_sessions")
class ParkingSession(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "spot_id", nullable = false, length = 64)
    val spotId: String,

    @Column(name = "renter_user_id", nullable = false, length = 64)
    val renterUserId: String,

    @Column(name = "started_at", nullable = false)
    val startedAt: Instant = Instant.now(),

    @Column(name = "ended_at")
    var endedAt: Instant? = null,

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
    var status: ParkingSessionStatus = ParkingSessionStatus.ACTIVE,

    // A real double-tap "check out" from a flaky mobile connection could otherwise
    // double-charge the renter -- versioning makes only one end-session transition
    // win, same discipline `BikeRentalSession`'s own doc comment already establishes.
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(id = "", spotId = "", renterUserId = "")

    companion object {
        // Real gap found live (2026-08-18): unlike every other real reservation/session
        // this codebase enforces a hard end for (RideTrip's own dispatch timeout,
        // BookingDeposit/VehicleInspectionBooking's own no-show forfeit schedulers, and
        // -- structurally identical to this exact feature -- BikeRentalSession's own
        // MAX_RENTAL_DURATION), an ACTIVE ParkingSession had no timeout at all: a renter
        // whose app crashed or who simply never came back left the real spot permanently
        // `available = false`, unrentable by anyone else and unpaid for the real owner,
        // forever. Sourced from the identical real-world account
        // BikeRentalSession.MAX_RENTAL_DURATION's own doc comment already cites (Citi
        // Bike NYC's real, currently-documented "kept out too long" policy at
        // help.citibikenyc.com/hc/en-us/articles/360032367371, cross-checked against
        // assets.citibikenyc.com/rental-agreement.html): a rental not returned within a
        // real 24-hour window is treated by the system as abandoned and force-closed.
        // This fits parking at least as well as it fits bikes -- ParkMobile/SpotHero-style
        // real hourly parking sessions are also time-metered with no fixed end, the exact
        // same "duration, not distance, so nothing is known/held at start" shape this
        // class's own doc comment already establishes -- so reusing the identical 24-hour
        // window (rather than inventing a new, unsourced number) is the honest choice.
        // ParkingService.forceEndAbandonedSession force-settles at itunda's own already-real
        // hourly fare (the exact same billing math `endSession` already uses) so the real
        // owner is actually paid for the time their spot was occupied and the spot itself
        // re-enters the pool, not left permanently unrentable over one abandoned session.
        val MAX_SESSION_DURATION: java.time.Duration = java.time.Duration.ofHours(24)
    }
}
