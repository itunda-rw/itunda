package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * One real successful daily deposit against a `Grow31SavingsPlan` -- an append-only
 * history/audit row, same "one row per successful movement, never folded into a
 * running total alone" convention `WeeklySavingsInstallment` already establishes.
 * [depositDate] is the real calendar day the deposit counted for (used to detect a
 * same-day double-deposit and a missed-day streak break), distinct from
 * [depositedAt]'s own real wall-clock instant.
 */
@Entity
@Table(name = "grow31_savings_deposits")
class Grow31SavingsDeposit(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "plan_id", nullable = false, length = 64)
    val planId: String,

    @Column(name = "day_number", nullable = false)
    val dayNumber: Int,

    @Column(name = "deposit_date", nullable = false)
    val depositDate: LocalDate,

    @Column(nullable = false, precision = 18, scale = 2)
    val amount: BigDecimal,

    @Column(name = "streak_at_deposit", nullable = false)
    val streakAtDeposit: Int,

    @Column(name = "deposited_at", nullable = false)
    val depositedAt: Instant = Instant.now(),
) {
    protected constructor() : this(
        id = "", planId = "", dayNumber = 0, depositDate = LocalDate.now(),
        amount = BigDecimal.ZERO, streakAtDeposit = 0,
    )
}
