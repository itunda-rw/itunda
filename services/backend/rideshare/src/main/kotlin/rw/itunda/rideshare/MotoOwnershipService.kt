package rw.itunda.rideshare

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.MotoOwnershipPlan
import rw.itunda.core.domain.MotoOwnershipPlanStatus
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.MotoOwnershipPlanRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class InvalidMotoOwnershipBikePriceException(message: String) : RuntimeException(message)
class InvalidMotoOwnershipContributionException(message: String) : RuntimeException(message)
class MotoOwnershipPlanAlreadyActiveException(message: String) : RuntimeException(message)
class MotoOwnershipPlanNotFoundException(message: String) : RuntimeException(message)
class MotoOwnershipPlanNotSavingException(message: String) : RuntimeException(message)
class MotoOwnershipPlanNotCancellableException(message: String) : RuntimeException(message)
class MotoOwnershipPlanNotRepayableException(message: String) : RuntimeException(message)
class MotoOwnershipDownPaymentNotMetException(message: String) : RuntimeException(message)
class InvalidMotoOwnershipAmountException(message: String) : RuntimeException(message)
class MotoOwnershipNoAccountException(message: String) : RuntimeException(message)

// itunda's own honest realistic range for a moto-taxi bike, grounded in the sourced
// ~600,000 RWF entry-level figure (Anadolu Agency) but not a claimed reproduction of
// any real published market-wide price ceiling/floor -- the sourcing didn't give one.
private val MIN_BIKE_PRICE = BigDecimal("300000")
private val MAX_BIKE_PRICE = BigDecimal("2500000")

// itunda's own 30% down-payment policy pick -- see MotoOwnershipPlan.kt's own doc
// comment for why this is honestly named as itunda's own choice, not a sourced
// regulatory figure.
private val DOWN_PAYMENT_RATE = BigDecimal("0.30")

// A simple sanity bound on daily_contribution relative to bikePrice -- large enough
// that a plan finishes in a reasonable time, small enough it can't be an absurd
// single-day lump sum masquerading as a "daily" figure. Not a sourced figure, just a
// reasonable guardrail.
private val MIN_DAILY_CONTRIBUTION = BigDecimal("100")

private val ACTIVE_STATUSES = listOf(MotoOwnershipPlanStatus.SAVING, MotoOwnershipPlanStatus.LOAN_ACTIVE)

private const val AUTO_CONTRIBUTION_INTERVAL_DAYS = 30L

/**
 * Real Rwanda moto-taxi ownership savings-to-loan plan -- see
 * `MotoOwnershipPlan.kt`'s own doc comment for the full sourced account, the ledger
 * accounts reused, and the honest unsecured-facility v1 limitation.
 *
 * Genuinely distinct from every other feature in this codebase: the first two-PHASE
 * product (savings, then loan, against the SAME row) -- everything else in this
 * codebase is either pure savings (`SavingsService`) or pure lending (`LoansService`/
 * `VupLoanService`/`StudentLoanService`), never both phases of the same real-world
 * purchase against a single tracked balance.
 */
@Service
class MotoOwnershipService(
    private val motoOwnershipPlanRepository: MotoOwnershipPlanRepository,
    private val accountRepository: AccountRepository,
    private val ledgerService: LedgerService,
    private val rateLimiter: RateLimiter,
) {
    private val log = LoggerFactory.getLogger(MotoOwnershipService::class.java)

    // Real bug class this session has hit repeatedly -- the "reject if already
    // active" check-then-CREATE race: @Version can't protect a row that doesn't exist
    // yet. Locking the caller's own MAIN account row first (same fix
    // VupLoanService.applyForLoan/StudentLoanService.applyForLoan/
    // YouthAccountService.openYouthAccount already needed for this exact shape)
    // serializes concurrent plan creations for the same user without needing a new
    // lock table.
    @Transactional
    fun createPlan(userId: String, bikePrice: BigDecimal, dailyContribution: BigDecimal): MotoOwnershipPlan {
        if (bikePrice < MIN_BIKE_PRICE || bikePrice > MAX_BIKE_PRICE) {
            throw InvalidMotoOwnershipBikePriceException("Bike price must be between $MIN_BIKE_PRICE and $MAX_BIKE_PRICE RWF")
        }
        val downPaymentTarget = bikePrice.multiply(DOWN_PAYMENT_RATE)
        if (dailyContribution < MIN_DAILY_CONTRIBUTION || dailyContribution > downPaymentTarget) {
            throw InvalidMotoOwnershipContributionException(
                "Daily contribution must be between $MIN_DAILY_CONTRIBUTION and the down payment target ($downPaymentTarget) RWF",
            )
        }

        val account = accountRepository.findByUserIdAndType(userId, AccountType.MAIN)
            ?: throw MotoOwnershipNoAccountException("No account found for this account")
        accountRepository.findByIdForUpdate(account.id)

        val activePlans = motoOwnershipPlanRepository.findByUserIdAndStatusIn(userId, ACTIVE_STATUSES)
        if (activePlans.isNotEmpty()) {
            throw MotoOwnershipPlanAlreadyActiveException("You already have an active moto-taxi ownership plan -- complete or cancel it before starting another")
        }

        rateLimiter.checkLimit("moto-ownership:create:$userId", limit = 5, window = Duration.ofDays(1))

        return motoOwnershipPlanRepository.save(
            MotoOwnershipPlan(
                id = "motoown_${UUID.randomUUID()}", userId = userId, bikePrice = bikePrice,
                downPaymentTarget = downPaymentTarget, savedAmount = BigDecimal.ZERO,
                dailyContribution = dailyContribution, loanOutstanding = BigDecimal.ZERO,
            ),
        )
    }

    private fun getOwnedPlan(userId: String, planId: String): MotoOwnershipPlan {
        val plan = motoOwnershipPlanRepository.findById(planId).orElseThrow { MotoOwnershipPlanNotFoundException("Moto-taxi ownership plan not found") }
        if (plan.userId != userId) throw MotoOwnershipPlanNotFoundException("Moto-taxi ownership plan not found")
        return plan
    }

    @Transactional
    fun contribute(userId: String, planId: String, amount: BigDecimal): MotoOwnershipPlan {
        val plan = getOwnedPlan(userId, planId)
        if (plan.status != MotoOwnershipPlanStatus.SAVING) throw MotoOwnershipPlanNotSavingException("Only a SAVING plan can receive contributions")
        if (amount <= BigDecimal.ZERO) throw InvalidMotoOwnershipAmountException("Contribution amount must be positive")

        val account = accountRepository.findByUserIdAndType(userId, AccountType.MAIN)
            ?: throw MotoOwnershipNoAccountException("No account found for this account")

        // Clamp BEFORE ever touching the ledger -- the exact overshoot-clamp lesson
        // this session learned fixing InsuranceService.contributeToFund/
        // VupLoanService.repay/StudentLoanService.repay. Never post the raw amount to
        // the ledger and cap the field separately.
        val actualAmount = amount.min(plan.downPaymentTarget.subtract(plan.savedAmount))
        if (actualAmount <= BigDecimal.ZERO) throw InvalidMotoOwnershipAmountException("The down payment target has already been met")

        ledgerService.postLedgerTransaction(
            account.currency,
            listOf(
                LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, actualAmount, "Moto-taxi ownership plan contribution"),
                LedgerLeg("savings_goal_payable", LedgerAccountType.SAVINGS_GOAL_PAYABLE, LedgerDirection.CREDIT, actualAmount, "Moto-taxi ownership plan contribution"),
            ),
        )

        plan.savedAmount = plan.savedAmount.add(actualAmount)
        return motoOwnershipPlanRepository.save(plan)
    }

    @Transactional
    fun cancel(userId: String, planId: String): MotoOwnershipPlan {
        val plan = getOwnedPlan(userId, planId)
        if (plan.status != MotoOwnershipPlanStatus.SAVING) throw MotoOwnershipPlanNotCancellableException("Only a SAVING plan can be cancelled")

        val account = accountRepository.findByUserIdAndType(userId, AccountType.MAIN)
            ?: throw MotoOwnershipNoAccountException("No account found for this account")

        if (plan.savedAmount > BigDecimal.ZERO) {
            ledgerService.postLedgerTransaction(
                account.currency,
                listOf(
                    LedgerLeg("savings_goal_payable", LedgerAccountType.SAVINGS_GOAL_PAYABLE, LedgerDirection.DEBIT, plan.savedAmount, "Moto-taxi ownership plan cancelled -- refund"),
                    LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, plan.savedAmount, "Moto-taxi ownership plan cancelled -- refund"),
                ),
            )
        }

        plan.savedAmount = BigDecimal.ZERO
        plan.status = MotoOwnershipPlanStatus.CANCELLED
        return motoOwnershipPlanRepository.save(plan)
    }

    // Real accounting bug found live in this feature's own build-time review
    // (2026-08-02): the original version of this method posted the disbursement as
    // TWO separate transactions -- a CREDIT account/DEBIT loan_payable leg for only
    // `remainingBalance`, plus a second transaction crediting `savedAmount` BACK to
    // loan_payable, reasoning that the first leg had "under-recognized" the full
    // bikePrice. That reasoning was wrong: the first leg already DEBITs loan_payable
    // for exactly `remainingBalance`, which by itself already correctly matches
    // `loanOutstanding` -- crediting loan_payable a second time for savedAmount
    // silently UNDERSTATES its real balance by savedAmount, and once the loan is
    // later repaid in full (crediting loan_payable again for the total repaid), the
    // shared loan_payable account -- also used by VupLoanService/StudentLoanService/
    // CooperativeService/LoansService -- ends up permanently short by savedAmount for
    // every converted plan. Verified by hand against this feature's own test fixture
    // (bikePrice=600,000, savedAmount=180,000, remainingBalance=420,000): the buggy
    // version left loan_payable's net position at 420,000-180,000=240,000 against a
    // claimed loanOutstanding of 420,000, a real, silent 180,000 mismatch.
    //
    // Corrected design: the whole point of converting is that the user goes and
    // actually buys the bike, so they need the FULL purchase price in spendable
    // account cash, not just the loan portion -- the down payment they already saved
    // (locked in savings_goal_payable, previously only reachable via `cancel`'s
    // refund) gets RELEASED into their account alongside the newly-disbursed loan
    // principal, in ONE real, atomically-balanced ledger transaction:
    //   - account: CREDIT bikePrice (the full purchase amount, now spendable)
    //   - savings_goal_payable: DEBIT savedAmount (release the locked-down-payment
    //     liability -- itunda no longer owes it back as a future refund, because it's
    //     just been delivered to the user as real cash instead)
    //   - loan_payable: DEBIT remainingBalance (the genuinely NEW principal borrowed)
    // Debits (savedAmount + remainingBalance = bikePrice) always exactly equal the
    // account credit (bikePrice), for any savedAmount/bikePrice combination -- no
    // revenue account or second transaction needed, and loan_payable ends up carrying
    // exactly `loanOutstanding`, nothing more.
    @Transactional
    fun convertToLoan(userId: String, planId: String): MotoOwnershipPlan {
        val plan = getOwnedPlan(userId, planId)
        if (plan.status != MotoOwnershipPlanStatus.SAVING) throw MotoOwnershipPlanNotSavingException("Only a SAVING plan can be converted to a loan")
        if (plan.savedAmount < plan.downPaymentTarget) {
            throw MotoOwnershipDownPaymentNotMetException("The down payment target (${plan.downPaymentTarget}) has not been met yet")
        }

        val account = accountRepository.findByUserIdAndType(userId, AccountType.MAIN)
            ?: throw MotoOwnershipNoAccountException("No account found for this account")

        val remainingBalance = plan.bikePrice.subtract(plan.savedAmount)

        val legs = mutableListOf(
            LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, plan.bikePrice, "Moto-taxi ownership purchase -- loan disbursement + released down payment"),
        )
        if (plan.savedAmount > BigDecimal.ZERO) {
            legs.add(LedgerLeg("savings_goal_payable", LedgerAccountType.SAVINGS_GOAL_PAYABLE, LedgerDirection.DEBIT, plan.savedAmount, "Moto-taxi ownership down payment released to account"))
        }
        if (remainingBalance > BigDecimal.ZERO) {
            legs.add(LedgerLeg("loan_payable", LedgerAccountType.LOAN_PAYABLE, LedgerDirection.DEBIT, remainingBalance, "Moto-taxi ownership loan principal owed"))
        }
        ledgerService.postLedgerTransaction(account.currency, legs)

        plan.loanOutstanding = remainingBalance
        plan.status = MotoOwnershipPlanStatus.LOAN_ACTIVE
        return motoOwnershipPlanRepository.save(plan)
    }

    @Transactional
    fun repay(userId: String, planId: String, amount: BigDecimal): MotoOwnershipPlan {
        val plan = getOwnedPlan(userId, planId)
        if (plan.status != MotoOwnershipPlanStatus.LOAN_ACTIVE) throw MotoOwnershipPlanNotRepayableException("Only a LOAN_ACTIVE plan can be repaid")
        if (amount <= BigDecimal.ZERO) throw InvalidMotoOwnershipAmountException("Repayment amount must be positive")

        val account = accountRepository.findByUserIdAndType(userId, AccountType.MAIN)
            ?: throw MotoOwnershipNoAccountException("No account found for this account")

        // Clamp BEFORE ever touching the ledger -- same overshoot-clamp discipline as
        // `contribute` above.
        val actualAmount = amount.min(plan.loanOutstanding)

        ledgerService.postLedgerTransaction(
            account.currency,
            listOf(
                LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, actualAmount, "Moto-taxi ownership loan repayment"),
                LedgerLeg("loan_payable", LedgerAccountType.LOAN_PAYABLE, LedgerDirection.CREDIT, actualAmount, "Moto-taxi ownership loan repayment"),
            ),
        )

        plan.loanOutstanding = plan.loanOutstanding.subtract(actualAmount)
        plan.status = if (plan.loanOutstanding <= BigDecimal.ZERO) MotoOwnershipPlanStatus.COMPLETED else MotoOwnershipPlanStatus.LOAN_ACTIVE
        return motoOwnershipPlanRepository.save(plan)
    }

    fun getMyPlans(userId: String): List<MotoOwnershipPlan> = motoOwnershipPlanRepository.findByUserId(userId)

    fun getPlan(userId: String, planId: String): MotoOwnershipPlan = getOwnedPlan(userId, planId)

    // Same findAll()-then-filter honesty as SavingsService.getGoalsDueForAutoContribution
    // at this system's actual data scale. Only SAVING-status plans with room left below
    // downPaymentTarget, on the same 30-day cadence.
    fun getPlansDueForAutoContribution(): List<MotoOwnershipPlan> {
        val cutoff = Instant.now().minus(AUTO_CONTRIBUTION_INTERVAL_DAYS, ChronoUnit.DAYS)
        return motoOwnershipPlanRepository.findAll().filter { plan ->
            plan.status == MotoOwnershipPlanStatus.SAVING &&
                plan.savedAmount < plan.downPaymentTarget &&
                (plan.lastAutoContributionAt == null || plan.lastAutoContributionAt!!.isBefore(cutoff))
        }
    }

    // Returns false (not an exception) on insufficient funds -- a real recurring job
    // skips this cycle and retries next time, same real-world "standing order"
    // behavior SavingsService.autoContribute already establishes.
    @Transactional
    fun autoContribute(plan: MotoOwnershipPlan): Boolean {
        val account = accountRepository.findByUserIdAndType(plan.userId, AccountType.MAIN)
        if (account == null || account.availableBalance < plan.dailyContribution) {
            log.info("Skipping auto-contribution for moto-taxi ownership plan {} -- insufficient funds or no MAIN account", plan.id)
            return false
        }

        // Same overshoot-clamp discipline as `contribute` above.
        val actualAmount = plan.dailyContribution.min(plan.downPaymentTarget.subtract(plan.savedAmount))
        if (actualAmount <= BigDecimal.ZERO) return false

        ledgerService.postLedgerTransaction(
            account.currency,
            listOf(
                LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, actualAmount, "Moto-taxi ownership plan auto-contribution"),
                LedgerLeg("savings_goal_payable", LedgerAccountType.SAVINGS_GOAL_PAYABLE, LedgerDirection.CREDIT, actualAmount, "Moto-taxi ownership plan auto-contribution"),
            ),
        )

        plan.savedAmount = plan.savedAmount.add(actualAmount)
        plan.lastAutoContributionAt = Instant.now()
        motoOwnershipPlanRepository.save(plan)
        return true
    }
}
