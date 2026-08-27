package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

/**
 * Real "tap to pay your moto-taxi fare" (2026-08-27, direct user follow-up: "now we
 * can make pay for tax and moto as well", after confirming via AskUserQuestion this
 * means moto-taxi fare tap-collection). Kigali's real, mandatory smart-metered
 * moto-taxi fares (government-set, newtimes.co.rw's own "New taxi-moto fares set amid
 * rising fuel prices" reporting): RWF 400 for the first 2km, then RWF 117/km, real
 * observed door-to-door prices ranging roughly 400 RWF (shortest hop) to 6,000 RWF
 * (cross-city). itunda has no real distance-based meter integration -- same honest
 * "rider/driver picks a real fare within a real sourced range" boundary
 * [[TransitTrip]] already establishes for bus fares, not a fabricated GPS-derived
 * calculation. A real, existing app-based alternative, Yego Moto, already exists in
 * Kigali -- itunda has no partnership with it and this feature is never named after
 * it, same boundary [[TransitBalance]] draws against Tap&Go.
 *
 * Structurally simpler than Transit's own tap-collection: a Kigali moto-taxi driver is
 * an individual, not a company running fixed routes the way Kigali Bus Services/Royal
 * Express do, so there's no real "operator" to pick, and -- unlike a bus operator,
 * which itunda has no real settlement relationship with -- the driver here IS a real
 * itunda user tapping/scanning the rider's own presented [[CustomerPaymentCode]], so
 * this is a real, direct WALLET-to-WALLET payment (MotoFareService.collectFare),
 * exactly like a peer-to-peer transfer, not a simulated expense against an itunda-owned
 * clearing account -- no new LedgerAccountType needed at all.
 */
@Entity
@Table(name = "moto_fare_trips")
class MotoFareTrip(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "rider_user_id", nullable = false, length = 64)
    val riderUserId: String,

    @Column(name = "driver_user_id", nullable = false, length = 64)
    val driverUserId: String,

    @Column(nullable = false, precision = 18, scale = 2)
    val fare: BigDecimal,

    @Column(name = "ledger_transaction_id", nullable = false, length = 64)
    val ledgerTransactionId: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", riderUserId = "", driverUserId = "", fare = BigDecimal.ZERO, ledgerTransactionId = "")

    companion object {
        // Real sourced Kigali moto-taxi fare range (newtimes.co.rw's own smart-meter
        // fare-schedule reporting: RWF 400 base for the first 2km + RWF 117/km
        // thereafter): observed real door-to-door fares run roughly 400 RWF (a short
        // hop) to 6,000 RWF (a long cross-city ride).
        val MIN_FARE: BigDecimal = BigDecimal("400")
        val MAX_FARE: BigDecimal = BigDecimal("6000")
    }
}
