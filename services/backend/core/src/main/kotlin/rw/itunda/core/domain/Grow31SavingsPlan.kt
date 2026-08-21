package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

enum class Grow31SavingsPlanStatus { ACTIVE, MATURED, CANCELLED }

/**
 * Real Toss Bank 키워봐요 31일적금 (Grow-it 31-day savings) equivalent -- launched
 * 2026-08 (tossbank.com/articles/savings-account, g-enews.com 2026-08-06 coverage: 10만+
 * customers pre-registered before launch). Genuinely distinct from `WeeklySavingsPlan`
 * (26주적금, already shipped): DAILY cadence over a short fixed 31-day window (not
 * weekly over 26 weeks), a small user-chosen [dailyAmount] that stays flat (no
 * escalation), and -- the real, sourced, distinguishing mechanic -- a TIERED bonus rate
 * keyed on the longest unbroken daily-deposit streak reached during the 31 days (base
 * 1%, +3% at a 3-day streak, +4% at 7, +6% at 14, +8% at 21, +10% at the full 31), not
 * WeeklySavingsPlan's binary all-or-nothing bonus. Each deposit is a real, explicit,
 * once-per-real-calendar-day USER ACTION (`Grow31SavingsService.depositToday`), not an
 * auto-debit -- the real product's own daily engagement loop, mirrored honestly:
 * itunda has no path to Toss's own cosmetic "grow a character" minigame (its own
 * per-day missions/character stats have no sourced mechanical rules beyond flavor
 * text), so this ships the real, buildable financial mechanic -- daily deposit +
 * streak-tiered bonus interest -- without inventing gameplay Toss's own material never
 * specified precisely enough to reproduce honestly.
 *
 * [daysElapsed] advances by real calendar days since [startDate] regardless of whether
 * a given day's deposit happened, the same "fixed schedule doesn't pause for a miss"
 * discipline `WeeklySavingsPlan.weeksElapsed` already established -- maturity is
 * reached at day 31 of the calendar, not the 31st successful deposit.
 * [currentStreak]/[longestStreak] track consecutive daily deposits; a missed day is
 * detected lazily on the next real deposit (comparing it against
 * [lastDepositDate]) rather than needing a separate scheduler sweep just to notice a
 * gap, since the bonus tier that matters at maturity is [longestStreak], already
 * locked in by the time any gap happens.
 */
@Entity
@Table(name = "grow31_savings_plans")
class Grow31SavingsPlan(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "account_id", nullable = false, length = 64)
    val accountId: String,

    @Column(nullable = false, length = 255)
    var name: String,

    @Column(name = "daily_amount", nullable = false, precision = 18, scale = 2)
    val dailyAmount: BigDecimal,

    @Column(name = "start_date", nullable = false)
    val startDate: LocalDate,

    @Column(name = "days_elapsed", nullable = false)
    var daysElapsed: Int = 0,

    @Column(name = "current_streak", nullable = false)
    var currentStreak: Int = 0,

    @Column(name = "longest_streak", nullable = false)
    var longestStreak: Int = 0,

    @Column(name = "last_deposit_date")
    var lastDepositDate: LocalDate? = null,

    @Column(name = "total_saved", nullable = false, precision = 18, scale = 2)
    var totalSaved: BigDecimal = BigDecimal.ZERO,

    @Column(name = "base_rate", nullable = false)
    val baseRate: Double,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: Grow31SavingsPlanStatus = Grow31SavingsPlanStatus.ACTIVE,

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

    // The daily deposit, the maturity scheduler, and a user withdrawal all mutate the
    // same plan and can move money -- one transition must win at a time, same reasoning
    // every other real money-moving entity here already carries a @Version for.
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(
        id = "", userId = "", accountId = "", name = "", dailyAmount = BigDecimal.ZERO,
        startDate = LocalDate.now(), baseRate = 0.0,
    )
}
