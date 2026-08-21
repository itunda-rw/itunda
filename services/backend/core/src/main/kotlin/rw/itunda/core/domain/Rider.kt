package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

enum class RiderStatus { ACTIVE, SUSPENDED }

/**
 * A real itunda user who has opted into delivering Eats orders -- reuses the user's own
 * existing MAIN account as the payout destination (same real WALLET-to-WALLET disbursement
 * precedent `PayrollService` already established for employee pay), no new account type or
 * external payout rail needed. `available` is a real self-reported online/offline toggle.
 * `currentLatitude`/`currentLongitude` (2026-07-19) close the "no real GPS/location
 * tracking" limitation this doc comment used to name -- `Merchant`/`EatsOrder` already
 * carried real coordinates (see `GeoUtils`'s own doc comment, which named "proximity
 * ranking" as a real intended use from the start), the rider's own position was the one
 * missing piece. See `rw.itunda.eats.RiderService`/`EatsOrderService` for the full account.
 */
@Entity
@Table(name = "riders")
class Rider(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, unique = true, length = 64)
    val userId: String,

    @Column(name = "account_id", nullable = false, length = 64)
    val accountId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: RiderStatus = RiderStatus.ACTIVE,

    @Column(nullable = false)
    var available: Boolean = false,

    @Column(name = "current_latitude")
    var currentLatitude: Double? = null,

    @Column(name = "current_longitude")
    var currentLongitude: Double? = null,

    @Column(name = "location_updated_at")
    var locationUpdatedAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", accountId = "")
}
