package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant

/**
 * Real Kigali public-transit stored-value balance (2026-08-27, direct user follow-up
 * after the card-design-picker feature: "after this we will build transit features").
 * Kigali's real public buses (Kigali Bus Services, Royal Express) run on a real,
 * sourced contactless fare system called Tap&Go, built and operated by AC Group Ltd --
 * riders buy a physical card, top it up, and tap it on a reader when boarding; fares are
 * real, distance-based, and fall in a real 200-500 RWF range (acgroup.rw, kigalibusservices.rw/smartcards,
 * newtimes.co.rw's distance-based-fare-system reporting). Over 670,000 riders/day use it
 * across Kigali as of 2025, and it covers 100% of the city.
 *
 * itunda has no real partnership with AC Group, Kigali Bus Services, Royal Express, or
 * Tap&Go itself -- there is no real card reader on any real bus that recognizes an
 * itunda account. This is itunda's own honest, ledger-backed simulation of a transit
 * stored-value balance, the same "real money moves, real limits apply, it just isn't
 * carried by the real external rail" boundary [[DebitCard]] already establishes for
 * card purchases -- this entity is deliberately never named "Tap&Go" anywhere in this
 * codebase or any client, since that is AC Group's own real, trademarked product name,
 * not itunda's to reuse.
 *
 * One row per user (like DebitCard), topped up from the user's real MAIN account
 * (TransitService.topUp posts a real WALLET debit / TRANSIT_BALANCE_PAYABLE credit),
 * spent by picking a real operator + a real fare within the sourced 200-500 RWF range
 * (TransitService.tapFare posts the reverse leg against TRANSIT_FARE_EXPENSE, the same
 * "external, unmodeled counterparty" shape CARD_SPEND_EXPENSE already establishes).
 */
@Entity
@Table(name = "transit_balances")
class TransitBalance(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, unique = true, length = 64)
    val userId: String,

    @Column(nullable = false, precision = 18, scale = 2)
    var balance: BigDecimal = BigDecimal.ZERO,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    // A top-up and a fare tap both racing the same balance must not silently lose one
    // of their effects -- same reasoning DebitCard.version already establishes.
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(id = "", userId = "")
}

/**
 * The 2 real Kigali bus operators Tap&Go actually covers (acgroup.rw: "AC Group
 * launched the Tap&Go project with Kigali Bus Services and Royal Express"). A plain
 * whitelist, same reasoning [[DebitCardDesign]] already establishes for a client-facing
 * string field: a client can never desync from a strict enum ordinal.
 */
object TransitOperator {
    const val KIGALI_BUS_SERVICES = "Kigali Bus Services"
    const val ROYAL_EXPRESS = "Royal Express"
    val ALL: Set<String> = setOf(KIGALI_BUS_SERVICES, ROYAL_EXPRESS)
}

/**
 * One real row per tap-to-pay fare (TransitService.tapFare) -- the same real audit-trail
 * shape DebitCardTransaction already establishes for card purchases.
 */
@Entity
@Table(name = "transit_trips")
class TransitTrip(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(nullable = false, length = 64)
    val operator: String,

    @Column(nullable = false, precision = 18, scale = 2)
    val fare: BigDecimal,

    @Column(name = "ledger_transaction_id", nullable = false, length = 64)
    val ledgerTransactionId: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", operator = "", fare = BigDecimal.ZERO, ledgerTransactionId = "")

    companion object {
        // Real sourced Kigali fare range (kigalibusservices.rw/smartcards, distance-based
        // fare reporting): journeys run roughly 200-500 RWF depending on distance.
        // itunda has no real GPS-derived distance to compute an exact fare from, so a
        // rider picks a real fare within this sourced range rather than itunda
        // fabricating a precise distance calculation it can't actually back.
        val MIN_FARE: BigDecimal = BigDecimal("200")
        val MAX_FARE: BigDecimal = BigDecimal("500")
    }
}
