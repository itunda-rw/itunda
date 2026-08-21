package rw.itunda.savings

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Grow31SavingsDeposit
import rw.itunda.core.domain.Grow31SavingsPlan
import rw.itunda.core.domain.Grow31SavingsPlanStatus
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.Grow31SavingsDepositRepository
import rw.itunda.core.repository.Grow31SavingsPlanRepository
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.account.AccountNumberGenerator
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

private const val TERM_DAYS = 31
private const val BASE_RATE = 1.0

class Grow31PlanNotFoundException(message: String) : RuntimeException(message)
class Grow31PlanInvalidAmountException(message: String) : RuntimeException(message)
class Grow31PlanNotActiveException(message: String) : RuntimeException(message)
class Grow31PlanNotMaturedException(message: String) : RuntimeException(message)
class Grow31PlanAlreadyWithdrawnException(message: String) : RuntimeException(message)
class Grow31AlreadyDepositedTodayException(message: String) : RuntimeException(message)

data class Grow31SavingsPlanView(val plan: Grow31SavingsPlan, val accountBalance: BigDecimal, val deposits: List<Grow31SavingsDeposit>)

/**
 * Real Toss Bank 키워봐요 31일적금 (Grow-it 31-day savings) equivalent -- see
 * `Grow31SavingsPlan`'s own doc comment for the full sourced mechanics and what's
 * deliberately NOT reproduced (the cosmetic character-growth minigame). Mirrors
 * `WeeklySavingsService`'s own established conventions: rate-limited creation, a
 * dedicated per-plan `Account`, a scheduler-polls-a-due-list shape for maturity. The
 * real structural difference: deposits here are an explicit daily USER action
 * ([depositToday]), not a scheduler-driven auto-debit -- the streak IS the product.
 */
@Service
class Grow31SavingsService(
    private val accountRepository: AccountRepository,
    private val planRepository: Grow31SavingsPlanRepository,
    private val depositRepository: Grow31SavingsDepositRepository,
    private val ledgerService: LedgerService,
    private val rateLimiter: RateLimiter,
    private val accountNumberGenerator: AccountNumberGenerator,
) {
    private val log = LoggerFactory.getLogger(Grow31SavingsService::class.java)

    companion object {
        // Real, sourced tier table (tossbank.com/articles/savings-account,
        // g-enews.com 2026-08-06): base 1%, then +3/+4/+6/+8/+10 at a 3/7/14/21/31-day
        // unbroken streak respectively. Keyed on the longest streak actually reached,
        // not the streak at any single moment -- see Grow31SavingsPlan.longestStreak.
        fun bonusRateForStreak(streak: Int): Double = when {
            streak >= 31 -> 10.0
            streak >= 21 -> 8.0
            streak >= 14 -> 6.0
            streak >= 7 -> 4.0
            streak >= 3 -> 3.0
            else -> 0.0
        }
    }

    @Transactional
    fun createPlan(userId: String, name: String, dailyAmount: BigDecimal): Grow31SavingsPlan {
        // Real anti-spam limit, same convention as WeeklySavingsService.createPlan/
        // SavingsService.createGoal.
        rateLimiter.checkLimit("grow31-savings:create:$userId", limit = 10, window = Duration.ofHours(1))
        if (dailyAmount <= BigDecimal.ZERO) throw Grow31PlanInvalidAmountException("Daily amount must be greater than zero")

        val now = Instant.now()
        val account = accountRepository.save(
            Account(
                id = "account_${UUID.randomUUID()}",
                userId = userId,
                accountNumber = accountNumberGenerator.generate(2025300000L),
                accountName = "$name (31-Day Savings)",
                type = AccountType.GROW31_SAVINGS,
                balance = BigDecimal.ZERO,
                availableBalance = BigDecimal.ZERO,
            ),
        )
        val plan = planRepository.save(
            Grow31SavingsPlan(
                id = "g31_${UUID.randomUUID()}", userId = userId, accountId = account.id, name = name,
                dailyAmount = dailyAmount, startDate = LocalDate.now(), baseRate = BASE_RATE, createdAt = now,
            ),
        )
        log.info("Created 31-day savings plan {} for user {} (daily {})", plan.id, userId, dailyAmount)
        return plan
    }

    fun getPlans(userId: String) = planRepository.findByUserId(userId)

    private fun findOwned(userId: String, planId: String) =
        planRepository.findById(planId).filter { it.userId == userId }.orElseThrow { Grow31PlanNotFoundException("Plan not found") }

    fun getPlan(userId: String, planId: String): Grow31SavingsPlanView {
        val plan = findOwned(userId, planId)
        val account = accountRepository.findById(plan.accountId).orElseThrow { NoAccountException("Account not found") }
        val deposits = depositRepository.findByPlanIdOrderByDayNumberAsc(planId)
        return Grow31SavingsPlanView(plan, account.balance, deposits)
    }

    /**
     * Real once-per-real-calendar-day user-triggered deposit -- the actual product
     * mechanic, not an auto-debit. A missed day is detected lazily right here (the
     * gap between [Grow31SavingsPlan.lastDepositDate] and today), which is enough:
     * the bonus tier that matters at maturity is [Grow31SavingsPlan.longestStreak],
     * already locked in by the time any gap happens, so no separate scheduler sweep
     * is needed just to notice a miss.
     */
    @Transactional
    fun depositToday(userId: String, planId: String): Grow31SavingsPlanView {
        val plan = findOwned(userId, planId)
        if (plan.status != Grow31SavingsPlanStatus.ACTIVE) throw Grow31PlanNotActiveException("This plan is not active")

        val today = LocalDate.now()
        if (plan.lastDepositDate == today) {
            throw Grow31AlreadyDepositedTodayException("You've already saved today -- come back tomorrow")
        }

        val sourceAccount = accountRepository.findByUserIdAndType(userId, AccountType.MAIN)
            ?: throw NoAccountException("No account found for this account")
        val planAccount = accountRepository.findById(plan.accountId).orElseThrow { NoAccountException("Account not found") }

        ledgerService.postLedgerTransaction(
            sourceAccount.currency,
            listOf(
                LedgerLeg(sourceAccount.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, plan.dailyAmount, "31-day savings: ${plan.name}"),
                LedgerLeg(planAccount.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, plan.dailyAmount, "31-day savings: ${plan.name}"),
            ),
        )

        // Consecutive if the last deposit was exactly yesterday; any other gap
        // (including the very first deposit, lastDepositDate == null) starts a fresh
        // streak at 1 -- this deposit itself always counts as day one of its own run.
        plan.currentStreak = if (plan.lastDepositDate == today.minusDays(1)) plan.currentStreak + 1 else 1
        plan.longestStreak = maxOf(plan.longestStreak, plan.currentStreak)
        plan.lastDepositDate = today
        plan.totalSaved = plan.totalSaved.add(plan.dailyAmount)
        plan.daysElapsed = (ChronoUnit.DAYS.between(plan.startDate, today) + 1).toInt().coerceAtLeast(plan.daysElapsed)

        depositRepository.save(
            Grow31SavingsDeposit(
                id = "g31d_${UUID.randomUUID()}", planId = plan.id, dayNumber = plan.daysElapsed,
                depositDate = today, amount = plan.dailyAmount, streakAtDeposit = plan.currentStreak,
            ),
        )
        planRepository.save(plan)
        log.info("31-day savings deposit for plan {} (day {}, streak {})", plan.id, plan.daysElapsed, plan.currentStreak)

        if (plan.daysElapsed >= TERM_DAYS) {
            maturePlan(plan)
        }
        return getPlan(userId, planId)
    }

    // Real findAll()-then-filter honesty, same convention as
    // WeeklySavingsService.getPlansDueForProcessing -- catches a plan whose 31-day
    // calendar window elapsed without today's own deposit call ever pushing it past
    // TERM_DAYS (e.g. the user stopped depositing before day 31).
    fun getPlansDueForMaturity(): List<Grow31SavingsPlan> {
        val today = LocalDate.now()
        return planRepository.findAll().filter {
            it.status == Grow31SavingsPlanStatus.ACTIVE && ChronoUnit.DAYS.between(it.startDate, today) + 1 >= TERM_DAYS
        }
    }

    /** Real streak-tiered bonus, resolved from the longest run actually reached, then a
     * flat rate applied on the total real principal saved over the real 31-day term --
     * simpler than WeeklySavingsService's own per-installment remaining-term accrual
     * (itunda's own honest choice: the sourced product names a flat tiered APR, not a
     * per-installment accrual schedule). Interest is credited straight into the plan's
     * own account, matching the real distinction between "the term ended" and "the
     * customer took the money" -- a separate withdraw() call moves it to MAIN. */
    @Transactional
    fun maturePlan(plan: Grow31SavingsPlan) {
        val now = Instant.now()
        val rate = plan.baseRate + bonusRateForStreak(plan.longestStreak)
        val annualRate = BigDecimal.valueOf(rate).divide(BigDecimal(100), 10, RoundingMode.HALF_UP)
        val interest = plan.totalSaved.multiply(annualRate).multiply(BigDecimal(TERM_DAYS))
            .divide(BigDecimal(365), 10, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP)

        if (interest > BigDecimal.ZERO) {
            ledgerService.postLedgerTransaction(
                "RWF",
                listOf(
                    LedgerLeg("interest_expense", LedgerAccountType.INTEREST_EXPENSE, LedgerDirection.DEBIT, interest, "31-day savings maturity interest: ${plan.name}"),
                    LedgerLeg(plan.accountId, LedgerAccountType.WALLET, LedgerDirection.CREDIT, interest, "31-day savings maturity interest: ${plan.name}"),
                ),
            )
        }
        plan.status = Grow31SavingsPlanStatus.MATURED
        plan.maturedAt = now
        plan.totalInterestPaid = interest
        planRepository.save(plan)
        log.info("Matured 31-day savings plan {} (longestStreak={}, rate={}%, interest={})", plan.id, plan.longestStreak, rate, interest)
    }

    /** Real early exit before the real 31-day term elapses: forfeits the streak bonus
     * (base rate only, on whatever real principal was saved so far), computes interest,
     * credits it, then immediately pays out full principal + interest to MAIN --
     * mirrors WeeklySavingsService.cancelPlan's exact same early-withdrawal shape. */
    @Transactional
    fun cancelPlan(userId: String, planId: String): Grow31SavingsPlanView {
        val plan = findOwned(userId, planId)
        if (plan.status != Grow31SavingsPlanStatus.ACTIVE) throw Grow31PlanNotActiveException("This plan is not active")

        val now = Instant.now()
        val annualRate = BigDecimal.valueOf(plan.baseRate).divide(BigDecimal(100), 10, RoundingMode.HALF_UP)
        val daysHeld = ChronoUnit.DAYS.between(plan.createdAt, now).coerceAtLeast(0)
        val interest = plan.totalSaved.multiply(annualRate).multiply(BigDecimal(daysHeld))
            .divide(BigDecimal(365), 10, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP)
        val planAccount = accountRepository.findById(plan.accountId).orElseThrow { NoAccountException("Account not found") }

        if (interest > BigDecimal.ZERO) {
            ledgerService.postLedgerTransaction(
                "RWF",
                listOf(
                    LedgerLeg("interest_expense", LedgerAccountType.INTEREST_EXPENSE, LedgerDirection.DEBIT, interest, "31-day savings early-withdrawal interest: ${plan.name}"),
                    LedgerLeg(plan.accountId, LedgerAccountType.WALLET, LedgerDirection.CREDIT, interest, "31-day savings early-withdrawal interest: ${plan.name}"),
                ),
            )
        }

        val mainAccount = accountRepository.findByUserIdAndType(userId, AccountType.MAIN) ?: throw NoAccountException("No account found for this account")
        val payout = planAccount.balance.add(interest)
        if (payout > BigDecimal.ZERO) {
            ledgerService.postLedgerTransaction(
                planAccount.currency,
                listOf(
                    LedgerLeg(plan.accountId, LedgerAccountType.WALLET, LedgerDirection.DEBIT, payout, "Early withdrawal: ${plan.name}"),
                    LedgerLeg(mainAccount.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, payout, "Early withdrawal: ${plan.name}"),
                ),
            )
        }

        plan.status = Grow31SavingsPlanStatus.CANCELLED
        plan.cancelledAt = now
        plan.withdrawnAt = now
        plan.totalInterestPaid = interest
        planRepository.save(plan)
        log.info("Cancelled (early-withdrew) 31-day savings plan {} for user {} (payout {})", plan.id, userId, payout)
        return getPlan(userId, planId)
    }

    /** Moves a matured plan's full account balance (principal + already-credited
     * interest) to the user's MAIN account -- separate from maturePlan() on purpose,
     * same real "maturity is a system event, withdrawal is an explicit user action"
     * distinction WeeklySavingsService.withdraw already establishes. */
    @Transactional
    fun withdraw(userId: String, planId: String): Grow31SavingsPlanView {
        val plan = findOwned(userId, planId)
        if (plan.status != Grow31SavingsPlanStatus.MATURED) throw Grow31PlanNotMaturedException("This plan has not matured yet")
        if (plan.withdrawnAt != null) throw Grow31PlanAlreadyWithdrawnException("This plan has already been withdrawn")

        val planAccount = accountRepository.findById(plan.accountId).orElseThrow { NoAccountException("Account not found") }
        val mainAccount = accountRepository.findByUserIdAndType(userId, AccountType.MAIN) ?: throw NoAccountException("No account found for this account")
        val payout = planAccount.balance
        if (payout > BigDecimal.ZERO) {
            ledgerService.postLedgerTransaction(
                planAccount.currency,
                listOf(
                    LedgerLeg(plan.accountId, LedgerAccountType.WALLET, LedgerDirection.DEBIT, payout, "Matured 31-day savings withdrawal: ${plan.name}"),
                    LedgerLeg(mainAccount.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, payout, "Matured 31-day savings withdrawal: ${plan.name}"),
                ),
            )
        }
        plan.withdrawnAt = Instant.now()
        planRepository.save(plan)
        log.info("Withdrew matured 31-day savings plan {} for user {} (payout {})", plan.id, userId, payout)
        return getPlan(userId, planId)
    }
}
