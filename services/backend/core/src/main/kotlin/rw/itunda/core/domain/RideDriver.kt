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
 * wallet as their payout destination, same real WALLET-to-WALLET disbursement precedent
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

    @Column(name = "wallet_id", nullable = false, length = 64)
    val walletId: String,

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

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", walletId = "")
}
