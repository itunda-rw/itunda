package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

/** Mirrors backend/src/types/index.ts InterestJar — one row per user (userId is the PK). */
@Entity
@Table(name = "interest_jars")
class InterestJar(
    @Id
    @Column(name = "user_id", length = 64)
    val userId: String,

    @Column(name = "wallet_id", nullable = false, length = 64)
    val walletId: String,

    @Column(nullable = false, precision = 18, scale = 2)
    var balance: BigDecimal,

    @Column(nullable = false)
    val rate: Double,

    @Column(name = "earned_this_month", nullable = false, precision = 18, scale = 2)
    var earnedThisMonth: BigDecimal,

    @Column(name = "earned_total", nullable = false, precision = 18, scale = 2)
    var earnedTotal: BigDecimal,

    @Column(name = "last_paid_at", nullable = false)
    var lastPaidAt: Instant = Instant.now(),

    @Column(name = "next_payout_at", nullable = false)
    var nextPayoutAt: Instant = Instant.now().plusSeconds(86400),

    // Real "hidden money" nudge (2026-07-21) -- see SavingsService.maybeNudgeUnclaimed's
    // own doc comment. Tracks when this jar last got a real Notification about unclaimed
    // interest, so accrual (which runs far more often than any user should be pinged)
    // doesn't spam a new notification every cycle.
    @Column(name = "last_nudged_at")
    var lastNudgedAt: Instant? = null,
) {
    protected constructor() : this(userId = "", walletId = "", balance = BigDecimal.ZERO, rate = 0.0, earnedThisMonth = BigDecimal.ZERO, earnedTotal = BigDecimal.ZERO)
}
