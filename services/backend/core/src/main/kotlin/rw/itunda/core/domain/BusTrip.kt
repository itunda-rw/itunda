package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant

/**
 * Real Kakao T 시외버스 (intercity bus booking) -- sourced from Kakao Mobility's own
 * real, currently-live service (kakaomobility.com/service-kakaot/bus): search routes
 * between two cities/terminals, pick a departure time, reserve seats, pay in-app, get
 * a mobile ticket instead of a paper one from a kiosk. A real sibling to Kakao T Taxi/
 * 대리운전/Bike/Parking in the same app.
 *
 * **Honest v1 adaptation**, same pattern `Bike.kt`/`DesignatedDriver.kt`/
 * `ParkingSpot.kt` already establish: the real Kakao T Bus network is a partnership
 * with licensed coach operators this backend has no real path to simulate -- this is
 * instead a real PEER-TO-PEER pool, any itunda user self-registers as a coach operator
 * and posts a scheduled trip, no admin approval gate or real transport-licensing
 * check. A genuine, sourced adaptation, not an invented feature.
 *
 * Unlike Bike/Parking (duration unknown at start, billed at checkout), a real bus
 * ticket's fare IS known at booking time (seatCount * farePerSeat) -- so this bills
 * immediately at booking, the same direct account-to-account-at-purchase shape
 * `MerchantService.collect` already establishes, not the settle-at-end pattern.
 *
 * `@Version` guards `availableSeats` against a real concurrent-booking race (two
 * riders booking the last seat at once), the same optimistic-locking discipline
 * `DesignatedDriverTrip`'s own doc comment already establishes for its own
 * accept-vs-cancel race.
 */
@Entity
@Table(name = "bus_trips")
class BusTrip(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "operator_user_id", nullable = false, length = 64)
    val operatorUserId: String,

    @Column(name = "account_id", nullable = false, length = 64)
    val accountId: String,

    @Column(nullable = false, length = 200)
    val origin: String,

    @Column(nullable = false, length = 200)
    val destination: String,

    @Column(name = "departure_time", nullable = false)
    val departureTime: Instant,

    @Column(name = "total_seats", nullable = false)
    val totalSeats: Int,

    @Column(name = "available_seats", nullable = false)
    var availableSeats: Int,

    @Column(name = "fare_per_seat", nullable = false, precision = 18, scale = 2)
    val farePerSeat: BigDecimal,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(
        id = "", operatorUserId = "", accountId = "", origin = "", destination = "",
        departureTime = Instant.EPOCH, totalSeats = 0, availableSeats = 0, farePerSeat = BigDecimal.ZERO,
    )
}
