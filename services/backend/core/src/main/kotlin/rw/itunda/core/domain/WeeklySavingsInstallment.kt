package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

/**
 * One real successful weekly auto-debit against a `WeeklySavingsPlan` -- kept as its
 * own append-only row, not folded into the plan's own running total, specifically so
 * `WeeklySavingsService.computeInterest` can compute interest **per installment** on
 * that installment's own remaining term to maturity/cancellation (a week-1 deposit
 * earns interest on ~26 weeks, a week-25 deposit earns interest on ~1 week) and sum,
 * rather than applying one flat rate to the ending balance the way `InterestJar`'s
 * SafeBox-style daily accrual does. Missed/skipped weeks (insufficient funds) never
 * get a row here at all -- `WeeklySavingsPlan.weeksElapsed` advances regardless,
 * `installmentsCollected` and this table only count real successful debits.
 */
@Entity
@Table(name = "weekly_savings_installments")
class WeeklySavingsInstallment(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "plan_id", nullable = false, length = 64)
    val planId: String,

    @Column(name = "week_number", nullable = false)
    val weekNumber: Int,

    @Column(nullable = false, precision = 18, scale = 2)
    val amount: BigDecimal,

    @Column(name = "deposited_at", nullable = false)
    val depositedAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", planId = "", weekNumber = 0, amount = BigDecimal.ZERO)
}
