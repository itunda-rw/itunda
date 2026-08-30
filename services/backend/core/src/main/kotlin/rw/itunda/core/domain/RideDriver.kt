package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

enum class RideDriverStatus { ACTIVE, SUSPENDED }

/**
 * Real Kakao T-style ride-hailing driver -- see `RideTripService`'s own doc comment for
 * the full sourced account (kakaomobility.com/contents/taxi-dispatch). Structurally a
 * sibling of `Rider` (Eats delivery), not a reuse of it -- a driver and a food-delivery
 * rider are genuinely distinct real roles in this backend (an account can register as
 * either or both), matching how Kakao T Driver and Kakao T (delivery isn't even a Kakao
 * T product) are separate real registrations too. Reuses the driver's own existing MAIN
 * account as their payout destination, same real ACCOUNT-to-ACCOUNT disbursement precedent
 * `PayrollService`/`RiderService` already established.
 *
 * `totalOffers`/`totalAccepted` back a real, computed acceptance rate -- Kakao's own
 * sourced dispatch algorithm weighs a driver's real "배차 요청 수락 수/콜 카드 발송 수"
 * (acceptance requests ÷ cards sent) ratio. This backend has no real ML acceptance-
 * prediction model (Kakao's own sourced >90% ROC-AUC figure describes a trained model
 * this repo has no data or infrastructure to build) -- these two real counters are the
 * honest, rule-based v1: an exact, real ratio computed from this driver's own actual
 * accept/decline history, not a fabricated prediction.
 */
@Entity
@Table(name = "ride_drivers")
class RideDriver(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, unique = true, length = 64)
    val userId: String,

    @Column(name = "account_id", nullable = false, length = 64)
    val accountId: String,

    // Real gap found live (2026-08-31, market-readiness audit) -- this driver-facing
    // registration had zero identity/license info at all, unlike the smaller, adjacent
    // DesignatedDriver.licenseNumber (rw.itunda.core.domain), which already required
    // one. An honest, self-declared informational text field, not a real
    // license-verification gate this backend has no path to check -- same discipline
    // DesignatedDriver.licenseNumber's own doc comment already establishes.
    @Column(name = "license_number", nullable = false, length = 100)
    val licenseNumber: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: RideDriverStatus = RideDriverStatus.ACTIVE,

    @Column(nullable = false)
    var available: Boolean = false,

    @Column(name = "current_latitude")
    var currentLatitude: Double? = null,

    @Column(name = "current_longitude")
    var currentLongitude: Double? = null,

    @Column(name = "location_updated_at")
    var locationUpdatedAt: Instant? = null,

    @Column(name = "total_offers", nullable = false)
    var totalOffers: Int = 0,

    @Column(name = "total_accepted", nullable = false)
    var totalAccepted: Int = 0,

    // Real Uber "Destination Filter" (2026-08-16, help.uber.com/en-GB/driving-and-
    // delivering/article/driver-destination-filter) -- a driver nearing the end of
    // their shift sets a real destination and gets preferentially matched with trips
    // whose dropoff genuinely moves them closer to it, "up to twice a day" (Uber's own
    // real published limit, resets at midnight local -- see
    // RideDriverService.setDestination's own doc comment for the exact enforcement).
    // Null means no active destination filter, every existing driver's dispatch
    // behavior completely unchanged.
    @Column(name = "destination_latitude")
    var destinationLatitude: Double? = null,

    @Column(name = "destination_longitude")
    var destinationLongitude: Double? = null,

    @Column(name = "destination_uses_today", nullable = false)
    var destinationUsesToday: Int = 0,

    @Column(name = "destination_uses_reset_date")
    var destinationUsesResetDate: java.time.LocalDate? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", accountId = "", licenseNumber = "")
}
