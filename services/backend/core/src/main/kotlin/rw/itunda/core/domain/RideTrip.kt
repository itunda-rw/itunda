package rw.itunda.core.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant

// Forward-only, same discipline as EatsOrderStatus. REQUESTED -> (real dispatch offers
// it to one real candidate driver at a time) -> DRIVER_ASSIGNED (a driver accepted) ->
// IN_PROGRESS (driver started the trip) -> COMPLETED (real fare payout to the driver).
// CANCELLED is reachable only from REQUESTED -- before any driver has committed to the
// trip, the same safest, simplest real scope `EatsOrderStatus.CANCELLED`'s own doc
// comment already chose (no sourced cancellation-fee policy exists to build against).
enum class RideTripStatus { REQUESTED, DRIVER_ASSIGNED, IN_PROGRESS, COMPLETED, CANCELLED }

/**
 * A real Kakao T-style ride-hailing trip -- see `RideTripService`'s own doc comment for
 * the full sourced account (kakaomobility.com/contents/taxi-dispatch: real dispatch
 * ranks candidate drivers by acceptance-prediction, daily completions, rating, real
 * acceptance rate, and ETA; cancellation rate is real-documented to jump from 9.8% to
 * 28.8% once a wait crosses 15-18 seconds).
 *
 * `fare` = `baseFare` + `perKmRate` × `distanceKm`, computed once at request time from
 * `GeoUtils.haversineKm` between pickup and dropoff and never recomputed later -- same
 * snapshot-at-request-time discipline `EatsOrder.deliveryFee`/`itemsSubtotal` already
 * establish, so a later rate change never retroactively alters an already-requested
 * trip. Held in a real `ride_holding` escrow clearing account from request time (the
 * passenger's money leaves their wallet immediately, matching `EatsOrder`'s own
 * delivery-fee-holding pattern) until the trip reaches `COMPLETED`, at which point it's
 * paid straight into the driver's own wallet net of `platformFee` -- or refunded in
 * full if the trip is `CANCELLED` while still `REQUESTED`.
 */
@Entity
@Table(name = "ride_trips")
class RideTrip(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "passenger_id", nullable = false, length = 64)
    val passengerId: String,

    @Column(name = "driver_id", length = 64)
    var driverId: String? = null,

    @Column(name = "pickup_address", nullable = false, length = 500)
    val pickupAddress: String,

    @Column(name = "pickup_latitude", nullable = false)
    val pickupLatitude: Double,

    @Column(name = "pickup_longitude", nullable = false)
    val pickupLongitude: Double,

    @Column(name = "dropoff_address", nullable = false, length = 500)
    val dropoffAddress: String,

    @Column(name = "dropoff_latitude", nullable = false)
    val dropoffLatitude: Double,

    @Column(name = "dropoff_longitude", nullable = false)
    val dropoffLongitude: Double,

    @Column(name = "distance_km", nullable = false, precision = 10, scale = 3)
    val distanceKm: BigDecimal,

    @Column(nullable = false, precision = 18, scale = 2)
    val fare: BigDecimal,

    @Column(name = "platform_fee", nullable = false, precision = 18, scale = 2)
    val platformFee: BigDecimal,

    @Column(name = "transaction_id", nullable = false, length = 64)
    val transactionId: String,

    @Column(name = "payout_transaction_id", length = 64)
    var payoutTransactionId: String? = null,

    @Column(name = "refund_transaction_id", length = 64)
    var refundTransactionId: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: RideTripStatus = RideTripStatus.REQUESTED,

    // Real exclusive dispatch offer window -- same shape EatsOrder.offeredRiderId/
    // offerExpiresAt already established. A real 15-second window, directly matching
    // Kakao's own sourced finding (cancellation rate jumps sharply right around a
    // 15-18 second wait), not an arbitrary number.
    @Column(name = "offered_driver_id", length = 64)
    var offeredDriverId: String? = null,

    @Column(name = "offer_expires_at")
    var offerExpiresAt: Instant? = null,

    @Column(name = "excluded_driver_user_ids", length = 2000)
    var excludedDriverUserIds: String? = null,

    // Real Kakao T 예약 호출 (scheduled ride booking) -- kakaomobility's own real,
    // currently-live feature: request a ride for a future date/time instead of
    // immediate dispatch. `null` (the default) means ASAP, every existing caller's
    // behavior completely unchanged. Bounded to SCHEDULED_RIDE_MAX_WINDOW ahead, the
    // same real "record the preference, bound the window" pattern
    // EatsOrder.scheduledFor already established for Baemin-style 예약주문 -- itunda's
    // own honest choice, since Kakao's own real booking-window length isn't published.
    @Column(name = "scheduled_for")
    var scheduledFor: Instant? = null,

    // Set exactly once, by RideDispatchScheduler, the moment a scheduled trip's real
    // dispatch attempt actually starts (see RideTripService.activateScheduledDispatch's
    // own doc comment) -- distinguishes "not due yet" from "due, dispatch already
    // attempted" so the scheduler never re-triggers the same trip every poll, and so
    // getAvailableTrips knows a scheduled trip has genuinely become live rather than
    // showing it to any driver hours or days early.
    @Column(name = "scheduled_dispatch_started_at")
    var scheduledDispatchStartedAt: Instant? = null,

    // Real Uber "Verify Your Ride" PIN (uber.com/pl/en/blog/pin-number) -- generated once
    // at request time, told to the driver by the passenger in person right before
    // pickup, confirmed via RideTripService.startTrip before the trip (and the fare
    // clock) actually begins. @JsonIgnore by default so this NEVER leaks through any of
    // the driver-facing endpoints that serialize a raw RideTrip (getMyDriverTrips,
    // acceptTrip's own response, etc.) -- only the dedicated passenger-only
    // GET /trips/{id}/pin endpoint explicitly re-includes it. Nullable: trips created
    // before this migration have no PIN, and startTrip skips the check entirely for
    // those (see its own doc comment) rather than permanently locking out an in-flight
    // trip that predates this feature.
    @JsonIgnore
    @Column(name = "pin", length = 4)
    var pin: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    // A driver completion, passenger cancellation, and dispatch reassignment can be
    // concurrent.  This prevents two callers from applying separate terminal state
    // transitions to the same fare-holding balance.
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(
        id = "", passengerId = "", pickupAddress = "", pickupLatitude = 0.0, pickupLongitude = 0.0,
        dropoffAddress = "", dropoffLatitude = 0.0, dropoffLongitude = 0.0, distanceKm = BigDecimal.ZERO,
        fare = BigDecimal.ZERO, platformFee = BigDecimal.ZERO, transactionId = "",
    )

    companion object {
        // Directly sourced from Kakao Mobility's own real dispatch-system finding:
        // "15초에서 18초로 넘어가는 구간에서는... 취소율이 9.8%에서 28.8%로 급증" (cancellation
        // rate surges from 9.8% to 28.8% between 15-18 second wait times) --
        // kakaomobility.com/contents/taxi-dispatch.
        val OFFER_WINDOW: java.time.Duration = java.time.Duration.ofSeconds(15)
    }
}
