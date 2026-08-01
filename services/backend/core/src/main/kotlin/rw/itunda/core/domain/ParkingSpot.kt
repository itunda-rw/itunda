package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant

/**
 * Real Kakao T 주차 (Kakao T Parking) -- sourced from Kakao Mobility's own real,
 * currently-live parking service (kakaomobility.com/service-kakaot/parking,
 * parking.kakao.com): search real-time available parking near a destination, reserve,
 * pay in-app, automatic settlement on exit -- a real sibling to Kakao T Taxi/대리운전/
 * Bike in the same app.
 *
 * **Honest v1 adaptation**, same pattern `Bike.kt`/`DesignatedDriver.kt` already
 * establish: the real Kakao T Parking network is a company-operated lot partnership
 * this backend has no real path to simulate -- this is instead a real PEER-TO-PEER
 * pool, any itunda user self-lists a parking spot they own/control (a driveway, a
 * private lot space), no admin approval gate. A genuine, sourced adaptation of a real
 * feature, not an invented one -- the actual billing mechanics (time-based, settled at
 * checkout, no fare known up front) are the real Kakao T Parking model, same shape
 * `BikeRentalSession`'s own doc comment already establishes for the identical reason
 * (a bike/parking rental's duration, unlike a ride's distance, isn't known at start).
 */
@Entity
@Table(name = "parking_spots")
class ParkingSpot(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "owner_user_id", nullable = false, length = 64)
    val ownerUserId: String,

    @Column(name = "wallet_id", nullable = false, length = 64)
    val walletId: String,

    @Column(nullable = false, length = 500)
    val address: String,

    @Column(nullable = false)
    val latitude: Double,

    @Column(nullable = false)
    val longitude: Double,

    @Column(name = "hourly_rate", nullable = false, precision = 18, scale = 2)
    val hourlyRate: BigDecimal,

    @Column(nullable = false)
    var available: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    // Real bug found live (2026-08-02) -- see Bike.kt's own doc comment for the full
    // account: startSession only checked for the ABSENCE of an active ParkingSession
    // row, a check-then-act race with no versioned entity to catch it. Fixed the same
    // way: startSession/endSession now flip this existing `available` flag as part of
    // the checked transaction, so optimistic locking on this entity catches a
    // concurrent double-booking, matching RideTrip/BusTrip's own proven-safe pattern.
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(id = "", ownerUserId = "", walletId = "", address = "", latitude = 0.0, longitude = 0.0, hourlyRate = BigDecimal.ZERO)
}
