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

// REQUESTED: customer paid, fare held in escrow, visible to every online driver in the
// real open-list. ACCEPTED: a driver claimed it and is en route to the pickup.
// DRIVING: the driver arrived and is now driving the customer's own car. COMPLETED:
// real payout net of itunda's platform fee. CANCELLED: withdrawn by the customer,
// either before a driver commits (REQUESTED, always a full refund) or after
// (ACCEPTED, real Uber cancellation-fee policy applies -- see
// `DesignatedDriverService.CANCELLATION_FEE_GRACE_PERIOD`'s own doc comment). Real gap
// closed 2026-08-18: before this, an ACCEPTED trip had NO cancel path at all -- if a
// driver accepted and then simply never started driving, the customer's fare was
// stuck in `designated_driver_holding` with zero recourse, unlike this feature's own
// documented template `RideTrip`, which has always let the passenger cancel a
// DRIVER_ASSIGNED trip.
enum class DesignatedDriverTripStatus { REQUESTED, ACCEPTED, DRIVING, COMPLETED, CANCELLED }

/**
 * Real Kakao T 대리운전 (designated driver) trip -- see `DesignatedDriver.kt`'s own
 * doc comment for the full sourced account. `vehicleMake`/`vehicleModel`/
 * `vehiclePlate` describe the CUSTOMER'S OWN CAR the driver will drive -- purely
 * informational text the driver sees before arriving, not a real vehicle-registry
 * lookup this backend has no path to perform.
 *
 * Fare escrow/payout/refund mirrors `RideTrip`'s own fare-holding pattern exactly (a
 * real, already-proven shape, not reinvented): the customer's real fare leaves their
 * wallet at request time, held in `designated_driver_holding` until the trip
 * completes, paid to the driver net of the platform fee, refunded in full if
 * cancelled while still `REQUESTED`.
 */
@Entity
@Table(name = "designated_driver_trips")
class DesignatedDriverTrip(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "customer_id", nullable = false, length = 64)
    val customerId: String,

    @Column(name = "driver_id", length = 64)
    var driverId: String? = null,

    // Null until a driver accepts. Starts the real cancellation-fee grace-period clock
    // -- same real role `RideTrip.driverAssignedAt` already plays for its own
    // structurally-identical cancellation-fee policy.
    @Column(name = "driver_accepted_at")
    var driverAcceptedAt: Instant? = null,

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

    @Column(name = "vehicle_make", nullable = false, length = 100)
    val vehicleMake: String,

    @Column(name = "vehicle_model", nullable = false, length = 100)
    val vehicleModel: String,

    @Column(name = "vehicle_plate", nullable = false, length = 20)
    val vehiclePlate: String,

    @Column(name = "distance_km", nullable = false, precision = 10, scale = 3)
    val distanceKm: BigDecimal,

    @Column(nullable = false, precision = 18, scale = 2)
    val fare: BigDecimal,

    @Column(name = "platform_fee", nullable = false, precision = 18, scale = 2)
    val platformFee: BigDecimal,

    @Column(name = "hold_transaction_id", nullable = false, length = 64)
    val holdTransactionId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: DesignatedDriverTripStatus = DesignatedDriverTripStatus.REQUESTED,

    @Column(name = "payout_transaction_id", length = 64)
    var payoutTransactionId: String? = null,

    @Column(name = "refund_transaction_id", length = 64)
    var refundTransactionId: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    // An accept and a customer cancel could arrive at nearly the same time.
    // Versioning makes only one state transition win, same discipline
    // `VehicleInspectionBooking`'s own doc comment already establishes.
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(
        id = "", customerId = "", pickupAddress = "", pickupLatitude = 0.0, pickupLongitude = 0.0,
        dropoffAddress = "", dropoffLatitude = 0.0, dropoffLongitude = 0.0, vehicleMake = "", vehicleModel = "",
        vehiclePlate = "", distanceKm = BigDecimal.ZERO, fare = BigDecimal.ZERO, platformFee = BigDecimal.ZERO,
        holdTransactionId = "",
    )
}
