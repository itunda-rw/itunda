package rw.itunda.savings

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.domain.WeeklySavingsInstallment
import rw.itunda.core.domain.WeeklySavingsPlan
import rw.itunda.core.domain.WeeklySavingsPlanStatus
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.WalletRepository
import rw.itunda.core.repository.WeeklySavingsInstallmentRepository
import rw.itunda.core.repository.WeeklySavingsPlanRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

private const val TERM_WEEKS = 26
private const val ESCALATION_STEP_WEEKS = 4
private const val BASE_RATE = 5.0
private const val BONUS_RATE = 3.0

class WeeklyPlanNotFoundException(message: String) : RuntimeException(message)
class WeeklyPlanInvalidEscalationException(message: String) : RuntimeException(message)
class WeeklyPlanInvalidAmountException(message: String) : RuntimeException(message)
class WeeklyPlanNotActiveException(message: String) : RuntimeException(message)
class WeeklyPlanNotMaturedException(message: String) : RuntimeException(message)
class WeeklyPlanAlreadyWithdrawnException(message: String) : RuntimeException(message)

data class WeeklySavingsPlanView(val plan: WeeklySavingsPlan, val walletBalance: BigDecimal, val installments: List<WeeklySavingsInstallment>)

/**
 * Real KakaoBank 26주적금 (26-week savings) equivalent -- see `WeeklySavingsPlan`'s own
 * doc comment for the full sourced mechanics and `docs/DESIGN_REFERENCES.md` Section 6
 * for the source citation. Mirrors this module's own established conventions rather
 * than inventing new ones: rate-limited creation like `SavingsService.createGoal`/
 * `GroupAccountService.createGroupAccount`, a dedicated per-plan `Wallet` like
 * `GroupAccountService` (not a shared clearing account like `SavingsGoal`), and a
 * scheduler-polls-a-due-list shape like `AutoSaveScheduler`/`InterestAccrualScheduler`
 * (real business cadence -- 7 real days per installment -- with a demo-speed poll).
 */
@Service
class WeeklySavingsService(
    private val walletRepository: WalletRepository,
    private val planRepository: WeeklySavingsPlanRepository,
    private val installmentRepository: WeeklySavingsInstallmentRepository,
    private val ledgerService: LedgerService,
    private val rateLimiter: RateLimiter,
) {
    private val log = LoggerFactory.getLogger(WeeklySavingsService::class.java)

    private fun generateAccountNumber(): String = (2025200000L + (Math.random() * 900000).toLong()).toString()

    // Real KakaoBank step-up presets (10/20/30/50/100%). The exact cadence the step
    // applies on (every 4 installments) is itunda's own scoping choice -- the sourced
    // design doc named the escalating-amount *behavior* but not its exact schedule.
    private val allowedEscalationRates = setOf(
        BigDecimal("0.00"), BigDecimal("0.10"), BigDecimal("0.20"),
        BigDecimal("0.30"), BigDecimal("0.50"), BigDecimal("1.00"),
    )

    /** Real per-week auto-debit amount: a compounding step-up every ESCALATION_STEP_WEEKS
     * weeks, e.g. weeks 1-4 = base, weeks 5-8 = base*(1+rate), weeks 9-12 = base*(1+rate)^2. */
    fun amountForWeek(baseWeeklyAmount: BigDecimal, escalationRate: BigDecimal, weekNumber: Int): BigDecimal {
        val step = (weekNumber - 1) / ESCALATION_STEP_WEEKS
        var amount = baseWeeklyAmount
        repeat(step) { amount = amount.multiply(BigDecimal.ONE.add(escalationRate)) }
        return amount.setScale(2, RoundingMode.HALF_UP)
    }

    @Transactional
    fun createPlan(userId: String, name: String, baseWeeklyAmount: BigDecimal, escalationRate: BigDecimal): WeeklySavingsPlan {
        // Real anti-spam limit, added from day one -- same class of free-row-creation
        // endpoint the 2026-07-19 sweep found missing across P2P/Savings/Marketplace.
        rateLimiter.checkLimit("weekly-savings:create:$userId", limit = 10, window = Duration.ofHours(1))
        if (baseWeeklyAmount <= BigDecimal.ZERO) throw WeeklyPlanInvalidAmountException("Weekly amount must be greater than zero")
        if (escalationRate.setScale(2, RoundingMode.HALF_UP) !in allowedEscalationRates) {
            throw WeeklyPlanInvalidEscalationException("Escalation rate must be one of 0%, 10%, 20%, 30%, 50%, 100%")
        }

        val now = Instant.now()
        val wallet = walletRepository.save(
            Wallet(
                id = "wallet_${UUID.randomUUID()}",
                userId = userId,
                accountNumber = generateAccountNumber(),
                accountName = "$name (26-Week Savings)",
                type = WalletType.WEEKLY_SAVINGS,
                balance = BigDecimal.ZERO,
                availableBalance = BigDecimal.ZERO,
            ),
        )
        val openingWeekday = now.atZone(ZoneOffset.UTC).dayOfWeek.value
        val plan = planRepository.save(
            WeeklySavingsPlan(
                id = "wsp_${UUID.randomUUID()}", userId = userId, walletId = wallet.id, name = name,
                baseWeeklyAmount = baseWeeklyAmount, escalationRate = escalationRate,
                openingWeekday = openingWeekday, baseRate = BASE_RATE, bonusRate = BONUS_RATE,
                nextInstallmentDueAt = now, createdAt = now,
            ),
        )
        log.info("Created 26-week savings plan {} for user {} (base {}/wk, escalation {}%, opening weekday {})", plan.id, userId, baseWeeklyAmount, escalationRate, openingWeekday)
        return plan
    }

    fun getPlans(userId: String) = planRepository.findByUserId(userId)

    private fun findOwned(userId: String, planId: String) =
        planRepository.findById(planId).filter { it.userId == userId }.orElseThrow { WeeklyPlanNotFoundException("Plan not found") }

    fun getPlan(userId: String, planId: String): WeeklySavingsPlanView {
        val plan = findOwned(userId, planId)
        val wallet = walletRepository.findById(plan.walletId).orElseThrow { NoWalletException("Wallet not found") }
        val installments = installmentRepository.findByPlanIdOrderByWeekNumberAsc(planId)
        return WeeklySavingsPlanView(plan, wallet.balance, installments)
    }

    // Real findAll()-then-filter honesty, same convention as
    // SavingsService.getGoalsDueForAutoContribution/getJarsDueForAccrual -- this
    // system's actual data scale doesn't yet justify an indexed query, and the new
    // (status, next_installment_due_at) index above is there for when it does.
    fun getPlansDueForProcessing(): List<WeeklySavingsPlan> {
        val now = Instant.now()
        return planRepository.findAll().filter { it.status == WeeklySavingsPlanStatus.ACTIVE && !it.nextInstallmentDueAt.isAfter(now) }
    }

    /**
     * Processes exactly one due weekly installment for [plan]. Returns false (not an
     * exception) on insufficient funds -- same "skip gracefully, retry next cycle"
     * contract as SavingsService.autoContribute -- but unlike a generic auto-save, a
     * skip here permanently sets streakBroken (the real "don't break the streak"
     * mechanic) and the fixed 26-week schedule still advances regardless: the weekday
     * lock does not pause for a missed payment, only the bonus rate is lost.
     */
    @Transactional
    fun processDueInstallment(plan: WeeklySavingsPlan): Boolean {
        val weekNumber = plan.weeksElapsed + 1
        val amount = amountForWeek(plan.baseWeeklyAmount, plan.escalationRate, weekNumber)
        val sourceWallet = walletRepository.findByUserIdAndType(plan.userId, WalletType.MAIN)

        val succeeded = if (sourceWallet == null || sourceWallet.availableBalance < amount) {
            log.info("Skipping week {} installment for plan {} -- insufficient funds or no MAIN wallet; streak broken", weekNumber, plan.id)
            plan.streakBroken = true
            false
        } else {
            val planWallet = walletRepository.findById(plan.walletId).orElseThrow { NoWalletException("Wallet not found") }
            ledgerService.postLedgerTransaction(
                sourceWallet.currency,
                listOf(
                    LedgerLeg(sourceWallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, amount, "26-week savings week $weekNumber: ${plan.name}"),
                    LedgerLeg(planWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, amount, "26-week savings week $weekNumber: ${plan.name}"),
                ),
            )
            installmentRepository.save(
                WeeklySavingsInstallment(id = "wspi_${UUID.randomUUID()}", planId = plan.id, weekNumber = weekNumber, amount = amount),
            )
            plan.installmentsCollected += 1
            plan.currentAmount = plan.currentAmount.add(amount)
            true
        }

        plan.weeksElapsed += 1
        plan.nextInstallmentDueAt = plan.nextInstallmentDueAt.plus(7, ChronoUnit.DAYS)
        planRepository.save(plan)

        if (plan.weeksElapsed >= TERM_WEEKS) {
            maturePlan(plan)
        }
        return succeeded
    }

    /** Real per-installment interest: each successful deposit accrues interest on its
     * own remaining term to [asOf] (not one flat rate on the ending balance), then
     * summed -- the real 26주적금 mechanic. [rate] is the annual percentage (already
     * resolved to base-only or base+bonus by the caller). */
    fun computeInterest(installments: List<WeeklySavingsInstallment>, asOf: Instant, rate: Double): BigDecimal {
        val annualRate = BigDecimal.valueOf(rate).divide(BigDecimal(100), 10, RoundingMode.HALF_UP)
        return installments.fold(BigDecimal.ZERO) { total, installment ->
            val remainingDays = ChronoUnit.DAYS.between(installment.depositedAt, asOf).coerceAtLeast(0)
            val interest = installment.amount.multiply(annualRate).multiply(BigDecimal(remainingDays)).divide(BigDecimal(365), 10, RoundingMode.HALF_UP)
            total.add(interest)
        }.setScale(2, RoundingMode.HALF_UP)
    }

    // Real streak-gated preferential rate: the bonus only survives a full unbroken run
    // to real maturity. Interest is credited straight into the plan's own wallet
    // (principal + interest sit together, matching how a real matured term account
    // looks) -- a separate withdraw() call is required to move it out to MAIN, mirroring
    // the real distinction between "the term ended" and "the customer took the money".
    @Transactional
    fun maturePlan(plan: WeeklySavingsPlan) {
        val installments = installmentRepository.findByPlanIdOrderByWeekNumberAsc(plan.id)
        val now = Instant.now()
        val rate = plan.baseRate + (if (plan.streakBroken) 0.0 else plan.bonusRate)
        val interest = computeInterest(installments, now, rate)

        if (interest > BigDecimal.ZERO) {
            ledgerService.postLedgerTransaction(
                "RWF",
                listOf(
                    LedgerLeg("interest_expense", LedgerAccountType.INTEREST_EXPENSE, LedgerDirection.DEBIT, interest, "26-week savings maturity interest: ${plan.name}"),
                    LedgerLeg(plan.walletId, LedgerAccountType.WALLET, LedgerDirection.CREDIT, interest, "26-week savings maturity interest: ${plan.name}"),
                ),
            )
        }
        plan.status = WeeklySavingsPlanStatus.MATURED
        plan.maturedAt = now
        plan.totalInterestPaid = interest
        planRepository.save(plan)
        log.info("Matured 26-week savings plan {} (streakBroken={}, rate={}%, interest={})", plan.id, plan.streakBroken, rate, interest)
    }

    /** Real early withdrawal: always forfeits the streak bonus (the plan never reached
     * its real unbroken 26-week maturity), computes interest on installments-so-far as
     * of right now at base rate only, credits it, then immediately pays out the full
     * principal + interest to the user's MAIN wallet in the same action -- unlike a
     * matured plan's separate withdraw() step, cancellation *is* the exit. */
    @Transactional
    fun cancelPlan(userId: String, planId: String): WeeklySavingsPlanView {
        val plan = findOwned(userId, planId)
        if (plan.status != WeeklySavingsPlanStatus.ACTIVE) throw WeeklyPlanNotActiveException("This plan is not active")

        val installments = installmentRepository.findByPlanIdOrderByWeekNumberAsc(planId)
        val now = Instant.now()
        val interest = computeInterest(installments, now, plan.baseRate)
        val planWallet = walletRepository.findById(plan.walletId).orElseThrow { NoWalletException("Wallet not found") }

        if (interest > BigDecimal.ZERO) {
            ledgerService.postLedgerTransaction(
                "RWF",
                listOf(
                    LedgerLeg("interest_expense", LedgerAccountType.INTEREST_EXPENSE, LedgerDirection.DEBIT, interest, "26-week savings early-withdrawal interest: ${plan.name}"),
                    LedgerLeg(plan.walletId, LedgerAccountType.WALLET, LedgerDirection.CREDIT, interest, "26-week savings early-withdrawal interest: ${plan.name}"),
                ),
            )
        }

        val mainWallet = walletRepository.findByUserIdAndType(userId, WalletType.MAIN) ?: throw NoWalletException("No wallet found for this account")
        val payout = planWallet.balance.add(interest)
        if (payout > BigDecimal.ZERO) {
            ledgerService.postLedgerTransaction(
                planWallet.currency,
                listOf(
                    LedgerLeg(plan.walletId, LedgerAccountType.WALLET, LedgerDirection.DEBIT, payout, "Early withdrawal: ${plan.name}"),
                    LedgerLeg(mainWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, payout, "Early withdrawal: ${plan.name}"),
                ),
            )
        }

        plan.streakBroken = true
        plan.status = WeeklySavingsPlanStatus.CANCELLED
        plan.cancelledAt = now
        plan.withdrawnAt = now
        plan.totalInterestPaid = interest
        planRepository.save(plan)
        log.info("Cancelled (early-withdrew) 26-week savings plan {} for user {} (payout {})", plan.id, userId, payout)
        return getPlan(userId, planId)
    }

    /** Moves a matured plan's full wallet balance (principal + already-credited
     * interest) to the user's MAIN wallet. Separate from maturePlan() on purpose --
     * maturity is a real scheduled/system event, withdrawal is a real explicit user
     * action, matching how a real matured term-deposit account behaves. */
    @Transactional
    fun withdraw(userId: String, planId: String): WeeklySavingsPlanView {
        val plan = findOwned(userId, planId)
        if (plan.status != WeeklySavingsPlanStatus.MATURED) throw WeeklyPlanNotMaturedException("This plan has not matured yet")
        if (plan.withdrawnAt != null) throw WeeklyPlanAlreadyWithdrawnException("This plan has already been withdrawn")

        val planWallet = walletRepository.findById(plan.walletId).orElseThrow { NoWalletException("Wallet not found") }
        val mainWallet = walletRepository.findByUserIdAndType(userId, WalletType.MAIN) ?: throw NoWalletException("No wallet found for this account")
        val payout = planWallet.balance
        if (payout > BigDecimal.ZERO) {
            ledgerService.postLedgerTransaction(
                planWallet.currency,
                listOf(
                    LedgerLeg(plan.walletId, LedgerAccountType.WALLET, LedgerDirection.DEBIT, payout, "Matured 26-week savings withdrawal: ${plan.name}"),
                    LedgerLeg(mainWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, payout, "Matured 26-week savings withdrawal: ${plan.name}"),
                ),
            )
        }
        plan.withdrawnAt = Instant.now()
        planRepository.save(plan)
        log.info("Withdrew matured 26-week savings plan {} for user {} (payout {})", plan.id, userId, payout)
        return getPlan(userId, planId)
    }
}
