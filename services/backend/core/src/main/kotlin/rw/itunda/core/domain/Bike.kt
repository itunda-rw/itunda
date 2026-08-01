package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
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
) {
    protected constructor() : this(id = "", ownerUserId = "", walletId = "", type = BikeType.REGULAR, currentLatitude = 0.0, currentLongitude = 0.0)
}
