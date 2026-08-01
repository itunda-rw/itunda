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
}
