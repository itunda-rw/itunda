package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

enum class WeeklySavingsPlanStatus { ACTIVE, MATURED, CANCELLED }

/**
 * Real KakaoBank 26주적금 (26-week savings) equivalent -- see
 * docs/DESIGN_REFERENCES.md Section 6 ("Bank / Pay") for the sourced mechanics this
 * mirrors, distinct from the generic `SavingsGoal`/`InterestJar` products and from the
 * already-shipped `GroupAccount`: (1) the weekly auto-debit amount **escalates**
 * automatically from [baseWeeklyAmount] rather than staying flat -- itunda's own
 * scoping choice (the source material named the escalation behavior but not its exact
 * cadence) is a step every [ESCALATION_STEP_WEEKS] weeks, by [escalationRate]
 * compounding -- see `WeeklySavingsService.amountForWeek`; (2) [openingWeekday] is
 * captured once at plan creation from the real day-of-week and never changes --
 * `nextInstallmentDueAt` only ever advances by exactly 7 real days, the same
 * no-mid-plan-changes lock the real product enforces; (3) interest is computed
 * **per weekly installment** on that installment's own remaining term to maturity/
 * cancellation, then summed -- see `WeeklySavingsInstallment` and
 * `WeeklySavingsService.computeInterest`, not one flat balance-at-maturity calc; (4)
 * [bonusRate] is only ever paid on top of [baseRate] if [streakBroken] stays false
 * through a real, full unbroken run to maturity -- a single missed installment (an
 * insufficient-funds skip) sets it permanently, the real "don't break the streak"
 * gamification mechanic. Backed by a real dedicated `Wallet` (WalletType.WEEKLY_SAVINGS)
 * per plan, same "no new balance concept" discipline `GroupAccount` already
 * established -- deposits/interest/withdrawals are the same real ledger-backed
 * WALLET-to-WALLET or clearing-account movement every other money-moving feature here
 * already uses.
 */
@Entity
@Table(name = "weekly_savings_plans")
class WeeklySavingsPlan(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "wallet_id", nullable = false, length = 64)
    val walletId: String,

    @Column(nullable = false, length = 255)
    var name: String,

    @Column(name = "base_weekly_amount", nullable = false, precision = 18, scale = 2)
    val baseWeeklyAmount: BigDecimal,

    // e.g. 0.10 == a real 10% step-up every ESCALATION_STEP_WEEKS weeks. Locked at
    // creation, same as everything else about this plan's schedule.
    @Column(name = "escalation_rate", nullable = false, precision = 6, scale = 4)
    val escalationRate: BigDecimal,

    // ISO day-of-week (1=Monday..7=Sunday), captured once from the real creation
    // instant. Never read as "today's weekday" again after creation -- the whole point
    // of the lock is that this stays fixed even if the scheduler processes a given
    // week's installment late.
    @Column(name = "opening_weekday", nullable = false)
    val openingWeekday: Int,

    @Column(name = "base_rate", nullable = false)
    val baseRate: Double,

    @Column(name = "bonus_rate", nullable = false)
    val bonusRate: Double,

    @Column(name = "installments_collected", nullable = false)
    var installmentsCollected: Int = 0,

    // Real scheduled due-dates that have elapsed, whether or not that week's debit
    // actually succeeded -- the fixed 26-week schedule does not pause for a missed
    // payment, only the streak bonus is lost. Maturity is reached at
    // WeeklySavingsService.TERM_WEEKS regardless of installmentsCollected.
    @Column(name = "weeks_elapsed", nullable = false)
    var weeksElapsed: Int = 0,

    @Column(name = "current_amount", nullable = false, precision = 18, scale = 2)
    var currentAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "streak_broken", nullable = false)
    var streakBroken: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: WeeklySavingsPlanStatus = WeeklySavingsPlanStatus.ACTIVE,

    @Column(name = "next_installment_due_at", nullable = false)
    var nextInstallmentDueAt: Instant = Instant.now(),

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "matured_at")
    var maturedAt: Instant? = null,

    @Column(name = "cancelled_at")
    var cancelledAt: Instant? = null,

    @Column(name = "withdrawn_at")
    var withdrawnAt: Instant? = null,

    @Column(name = "total_interest_paid", precision = 18, scale = 2)
    var totalInterestPaid: BigDecimal? = null,
) {
    protected constructor() : this(
        id = "", userId = "", walletId = "", name = "", baseWeeklyAmount = BigDecimal.ZERO,
        escalationRate = BigDecimal.ZERO, openingWeekday = 1, baseRate = 0.0, bonusRate = 0.0,
    )
}
