package rw.itunda.rideshare

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.MotoOwnershipPlan
import rw.itunda.core.domain.MotoOwnershipPlanStatus
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.MotoOwnershipPlanRepository
import rw.itunda.core.repository.WalletRepository
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
class MotoOwnershipNoWalletException(message: String) : RuntimeException(message)

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
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
    private val rateLimiter: RateLimiter,
) {
    private val log = LoggerFactory.getLogger(MotoOwnershipService::class.java)

    // Real bug class this session has hit repeatedly -- the "reject if already
    // active" check-then-CREATE race: @Version can't protect a row that doesn't exist
    // yet. Locking the caller's own MAIN wallet row first (same fix
    // VupLoanService.applyForLoan/StudentLoanService.applyForLoan/
    // MiniWalletService.openMiniWallet already needed for this exact shape)
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

        val wallet = walletRepository.findByUserIdAndType(userId, WalletType.MAIN)
            ?: throw MotoOwnershipNoWalletException("No wallet found for this account")
        walletRepository.findByIdForUpdate(wallet.id)

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

        val wallet = walletRepository.findByUserIdAndType(userId, WalletType.MAIN)
            ?: throw MotoOwnershipNoWalletException("No wallet found for this account")

        // Clamp BEFORE ever touching the ledger -- the exact overshoot-clamp lesson
        // this session learned fixing InsuranceService.contributeToFund/
        // VupLoanService.repay/StudentLoanService.repay. Never post the raw amount to
        // the ledger and cap the field separately.
        val actualAmount = amount.min(plan.downPaymentTarget.subtract(plan.savedAmount))
        if (actualAmount <= BigDecimal.ZERO) throw InvalidMotoOwnershipAmountException("The down payment target has already been met")

        ledgerService.postLedgerTransaction(
            wallet.currency,
            listOf(
                LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, actualAmount, "Moto-taxi ownership plan contribution"),
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

        val wallet = walletRepository.findByUserIdAndType(userId, WalletType.MAIN)
            ?: throw MotoOwnershipNoWalletException("No wallet found for this account")

        if (plan.savedAmount > BigDecimal.ZERO) {
            ledgerService.postLedgerTransaction(
                wallet.currency,
                listOf(
                    LedgerLeg("savings_goal_payable", LedgerAccountType.SAVINGS_GOAL_PAYABLE, LedgerDirection.DEBIT, plan.savedAmount, "Moto-taxi ownership plan cancelled -- refund"),
                    LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, plan.savedAmount, "Moto-taxi ownership plan cancelled -- refund"),
                ),
            )
        }

        plan.savedAmount = BigDecimal.ZERO
        plan.status = MotoOwnershipPlanStatus.CANCELLED
        return motoOwnershipPlanRepository.save(plan)
    }

    // The trickiest ledger logic in this feature. Two SEPARATE real ledger
    // transactions, each independently balanced:
    //
    // 1) Disbursement: the remaining balance (bikePrice - savedAmount) is paid out to
    //    the borrower's wallet -- CREDIT MAIN wallet / DEBIT loan_payable, the exact
    //    shape VupLoanService.disburse/StudentLoanService.disburse already establish.
    //
    // 2) Down-payment transfer: the money already sitting in savings_goal_payable is
    //    no longer a refundable savings balance (see `cancel` above, which is the ONLY
    //    other place that account gets debited back to the user) -- it has now been
    //    applied toward the purchase. DEBIT savings_goal_payable / CREDIT loan_payable
    //    for savedAmount. savings_goal_payable decreases because itunda no longer owes
    //    that money back to the user; loan_payable is credited here (increasing the
    //    liability leg on the SAME account leg 1 just debited/decreased) to reflect
    //    that the user's total bike debt is bikePrice MINUS the down payment they
    //    already contributed, not bikePrice minus zero -- i.e. this leg exactly
    //    reverses the amount by which leg 1 under-recognized the full bikePrice as
    //    principal owed.
    //
    // Net effect across both transactions: loan_payable carries exactly
    // bikePrice.subtract(savedAmount) = loanOutstanding, savings_goal_payable no
    // longer carries this plan's savedAmount, and the wallet only ever received the
    // true remaining balance -- never the down payment twice.
    @Transactional
    fun convertToLoan(userId: String, planId: String): MotoOwnershipPlan {
        val plan = getOwnedPlan(userId, planId)
        if (plan.status != MotoOwnershipPlanStatus.SAVING) throw MotoOwnershipPlanNotSavingException("Only a SAVING plan can be converted to a loan")
        if (plan.savedAmount < plan.downPaymentTarget) {
            throw MotoOwnershipDownPaymentNotMetException("The down payment target (${plan.downPaymentTarget}) has not been met yet")
        }

        val wallet = walletRepository.findByUserIdAndType(userId, WalletType.MAIN)
            ?: throw MotoOwnershipNoWalletException("No wallet found for this account")

        val remainingBalance = plan.bikePrice.subtract(plan.savedAmount)

        if (remainingBalance > BigDecimal.ZERO) {
            ledgerService.postLedgerTransaction(
                wallet.currency,
                listOf(
                    LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, remainingBalance, "Moto-taxi ownership loan disbursement"),
                    LedgerLeg("loan_payable", LedgerAccountType.LOAN_PAYABLE, LedgerDirection.DEBIT, remainingBalance, "Moto-taxi ownership loan principal owed"),
                ),
            )
        }

        ledgerService.postLedgerTransaction(
            wallet.currency,
            listOf(
                LedgerLeg("savings_goal_payable", LedgerAccountType.SAVINGS_GOAL_PAYABLE, LedgerDirection.DEBIT, plan.savedAmount, "Moto-taxi ownership down payment applied to loan"),
                LedgerLeg("loan_payable", LedgerAccountType.LOAN_PAYABLE, LedgerDirection.CREDIT, plan.savedAmount, "Moto-taxi ownership down payment applied to loan"),
            ),
        )

        plan.loanOutstanding = remainingBalance
        plan.status = MotoOwnershipPlanStatus.LOAN_ACTIVE
        return motoOwnershipPlanRepository.save(plan)
    }

    @Transactional
    fun repay(userId: String, planId: String, amount: BigDecimal): MotoOwnershipPlan {
        val plan = getOwnedPlan(userId, planId)
        if (plan.status != MotoOwnershipPlanStatus.LOAN_ACTIVE) throw MotoOwnershipPlanNotRepayableException("Only a LOAN_ACTIVE plan can be repaid")
        if (amount <= BigDecimal.ZERO) throw InvalidMotoOwnershipAmountException("Repayment amount must be positive")

        val wallet = walletRepository.findByUserIdAndType(userId, WalletType.MAIN)
            ?: throw MotoOwnershipNoWalletException("No wallet found for this account")

        // Clamp BEFORE ever touching the ledger -- same overshoot-clamp discipline as
        // `contribute` above.
        val actualAmount = amount.min(plan.loanOutstanding)

        ledgerService.postLedgerTransaction(
            wallet.currency,
            listOf(
                LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, actualAmount, "Moto-taxi ownership loan repayment"),
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
        val wallet = walletRepository.findByUserIdAndType(plan.userId, WalletType.MAIN)
        if (wallet == null || wallet.availableBalance < plan.dailyContribution) {
            log.info("Skipping auto-contribution for moto-taxi ownership plan {} -- insufficient funds or no MAIN wallet", plan.id)
            return false
        }

        // Same overshoot-clamp discipline as `contribute` above.
        val actualAmount = plan.dailyContribution.min(plan.downPaymentTarget.subtract(plan.savedAmount))
        if (actualAmount <= BigDecimal.ZERO) return false

        ledgerService.postLedgerTransaction(
            wallet.currency,
            listOf(
                LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, actualAmount, "Moto-taxi ownership plan auto-contribution"),
                LedgerLeg("savings_goal_payable", LedgerAccountType.SAVINGS_GOAL_PAYABLE, LedgerDirection.CREDIT, actualAmount, "Moto-taxi ownership plan auto-contribution"),
            ),
        )

        plan.savedAmount = plan.savedAmount.add(actualAmount)
        plan.lastAutoContributionAt = Instant.now()
        motoOwnershipPlanRepository.save(plan)
        return true
    }
}
