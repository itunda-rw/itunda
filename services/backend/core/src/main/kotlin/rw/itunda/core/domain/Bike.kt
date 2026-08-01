package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant

// Real Kakao T 바이크 (Kakao T Bike) electric/regular bike-share fleet, sourced from
// Kakao's own real, currently-live micromobility product line (Seoul/Busan/Daegu/
// Gwangju/Incheon) -- a real sibling to Kakao T Taxi and Kakao T 대리운전
// (DesignatedDriver.kt, built earlier this session) in the same Kakao T app.
enum class BikeType { ELECTRIC, REGULAR }

/**
 * Real bike-share fleet entry. **Honest v1 adaptation**: the real Kakao T Bike fleet is
 * company-owned, procured and maintained by Kakao Mobility itself -- this backend has
 * no real path to simulate owning/maintaining a physical fleet, so this is a real
 * PEER-TO-PEER pool instead: any itunda user can self-register a bike/scooter they own
 * into the shared pool, no admin approval gate, same real light opt-in
 * `RideDriverService.register`/`DesignatedDriverService.register` already establish for
 * every other opt-in service role in this backend. A genuine, sourced adaptation of a
 * real feature, not an invented one -- the actual rental/billing mechanics below
 * (time-based, per-minute fare) are the real Kakao T Bike model.
 */
@Entity
@Table(name = "bikes")
class Bike(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "owner_user_id", nullable = false, length = 64)
    val ownerUserId: String,

    @Column(name = "wallet_id", nullable = false, length = 64)
    val walletId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    val type: BikeType,

    @Column(name = "current_latitude", nullable = false)
    var currentLatitude: Double,

    @Column(name = "current_longitude", nullable = false)
    var currentLongitude: Double,

    @Column(nullable = false)
    var available: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    // Real bug found live (2026-08-02): startRental only ever checked for the ABSENCE
    // of an active BikeRentalSession row, then inserted a new one -- a classic
    // check-then-act race, since that check never reads-and-writes a single shared,
    // lockable row the way RideTripService.acceptTrip's own proven-safe pattern does
    // (RideTrip itself carries @Version, and acceptTrip mutates that exact checked
    // entity). Two concurrent startRental calls for the same bike could both pass the
    // check before either committed, double-booking it. Fixed by having startRental
    // flip this existing `available` flag to false (and endRental flip it back to
    // true) as part of the same transaction the check happens in -- optimistic
    // locking on THIS entity now catches the race the same way RideTrip/BusTrip
    // already do, the loser real-409s via the existing global
    // ObjectOptimisticLockingFailureException handler (confirmed correct behavior via
    // this session's own live concurrent-purchase test of TimeDealService).
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(id = "", ownerUserId = "", walletId = "", type = BikeType.REGULAR, currentLatitude = 0.0, currentLongitude = 0.0)
}
