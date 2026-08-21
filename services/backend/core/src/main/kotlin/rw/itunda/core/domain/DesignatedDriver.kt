package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Real Kakao T 대리운전 (designated driver) -- a professional driver comes to the
 * customer's location and drives the CUSTOMER'S OWN CAR home for them (for when
 * someone is too intoxicated or tired to drive), sourced from Kakao Mobility's own
 * real, currently-live service lineup alongside Kakao T Taxi. Structurally a sibling
 * of `RideDriver`, not a reuse of it -- a real 대리운전 driver operates a genuinely
 * distinct role from a taxi/ride-hailing driver (drives someone else's car, not
 * their own), matching how Kakao T Driver (대리) and Kakao T Taxi are separate real
 * registrations too. Same real light self-service opt-in `RideDriverService.register`
 * already establishes: any itunda user can register, no admin approval gate --
 * `licenseNumber` is an honest, self-declared informational text field, not a real
 * KYB/license-verification gate this backend has no path to check.
 *
 * Deliberately no acceptance-rate/ranked-dispatch fields (unlike `RideDriver`) -- v1
 * scope is a real open-list claim (a customer's trip request is visible to every
 * online driver, first to accept gets it), the same simpler shape
 * `VehicleInspectionMechanic`'s own doc comment already establishes for why not every
 * driver-role feature needs ranked auto-dispatch.
 */
@Entity
@Table(name = "designated_drivers")
class DesignatedDriver(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, unique = true, length = 64)
    val userId: String,

    @Column(name = "account_id", nullable = false, length = 64)
    val accountId: String,

    @Column(name = "license_number", nullable = false, length = 100)
    val licenseNumber: String,

    @Column(nullable = false)
    var available: Boolean = false,

    @Column(name = "current_latitude")
    var currentLatitude: Double? = null,

    @Column(name = "current_longitude")
    var currentLongitude: Double? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", accountId = "", licenseNumber = "")
}
