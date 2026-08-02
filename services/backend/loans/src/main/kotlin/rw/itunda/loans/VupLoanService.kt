package rw.itunda.loans

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.VupLoan
import rw.itunda.core.domain.VupLoanPurpose
import rw.itunda.core.domain.VupLoanStatus
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.VupLoanRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class IneligibleUbudeheCategoryException(message: String) : RuntimeException(message)
class InvalidVupLoanAmountException(message: String) : RuntimeException(message)
class VupLoanAlreadyActiveException(message: String) : RuntimeException(message)
class VupLoanNotFoundException(message: String) : RuntimeException(message)
class VupLoanNotRequestedException(message: String) : RuntimeException(message)
class VupLoanNotRepayableException(message: String) : RuntimeException(message)
class VupLoanNoWalletException(message: String) : RuntimeException(message)
class VupLoanInvalidRepayAmountException(message: String) : RuntimeException(message)

// itunda's own honest ceiling on a single VUP loan: 500,000 RWF, 5x the real sourced
// NISR EICV7 average (~100,000 RWF) -- a reasonable bound, not a claimed reproduction
// of any real published VUP/FS cap (the sourcing didn't specify one precisely).
private val MAX_VUP_LOAN_AMOUNT = BigDecimal("500000")

// VUP/FS targets households in poorer Ubudehe categories (1-3); category 4+ (the
// wealthiest bands) is deliberately out of scope for this means-tested product.
private const val MIN_ELIGIBLE_UBUDEHE_CATEGORY = 1
private const val MAX_ELIGIBLE_UBUDEHE_CATEGORY = 3

private val ACTIVE_STATUSES = listOf(VupLoanStatus.REQUESTED, VupLoanStatus.DISBURSED, VupLoanStatus.OVERDUE)

/**
 * Real Rwanda VUP (Vision 2020 Umurenge Programme) Financial Services micro-loan --
 * see `VupLoan.kt`'s own doc comment for the full sourced account and the correction
 * made from this feature's own earlier research proposal (disbursement/repayment
 * mirror `LoansService.applyForLoan`/`repayLoan`'s exact `loan_payable` ledger shape,
 * never `SaccoService`'s pooled wallet).
 *
 * Genuinely distinct from every other lending feature in this codebase: the first
 * MEANS-TESTED product, gated on a self-declared Ubudehe category rather than credit
 * score or collateral -- see `VupLoan.declaredUbudeheCategory`'s own doc comment for
 * why that field is honestly self-declared, not government-verified.
 */
@Service
class VupLoanService(
    private val vupLoanRepository: VupLoanRepository,
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
    private val rateLimiter: RateLimiter,
) {
    @Transactional
    fun applyForLoan(userId: String, declaredUbudeheCategory: Int, purpose: VupLoanPurpose, amount: BigDecimal): VupLoan {
        if (declaredUbudeheCategory < MIN_ELIGIBLE_UBUDEHE_CATEGORY || declaredUbudeheCategory > MAX_ELIGIBLE_UBUDEHE_CATEGORY) {
            throw IneligibleUbudeheCategoryException(
                "VUP Financial Services targets Ubudehe categories $MIN_ELIGIBLE_UBUDEHE_CATEGORY-$MAX_ELIGIBLE_UBUDEHE_CATEGORY; category $declaredUbudeheCategory is not eligible",
            )
        }
        if (amount <= BigDecimal.ZERO || amount > MAX_VUP_LOAN_AMOUNT) {
            throw InvalidVupLoanAmountException("Amount must be between 1 and $MAX_VUP_LOAN_AMOUNT RWF")
        }

        val activeLoans = vupLoanRepository.findByUserIdAndStatusIn(userId, ACTIVE_STATUSES)
        if (activeLoans.isNotEmpty()) {
            throw VupLoanAlreadyActiveException("You already have an active VUP loan -- repay it before applying for another")
        }

        rateLimiter.checkLimit("vup-loan:apply:$userId", limit = 5, window = Duration.ofDays(1))

        return vupLoanRepository.save(
            VupLoan(
                id = "vuploan_${UUID.randomUUID()}", userId = userId,
                declaredUbudeheCategory = declaredUbudeheCategory, purpose = purpose,
                principalAmount = amount, outstandingPrincipal = amount,
            ),
        )
    }

    @Transactional
    fun disburse(userId: String, loanId: String): VupLoan {
        val loan = vupLoanRepository.findById(loanId).orElseThrow { VupLoanNotFoundException("VUP loan not found") }
        if (loan.userId != userId) throw VupLoanNotFoundException("VUP loan not found")
        if (loan.status != VupLoanStatus.REQUESTED) throw VupLoanNotRequestedException("Only a REQUESTED loan can be disbursed")

        val wallet = walletRepository.findByUserIdAndType(userId, WalletType.MAIN)
            ?: throw VupLoanNoWalletException("No wallet found for this account")

        ledgerService.postLedgerTransaction(
            wallet.currency,
            listOf(
                LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, loan.principalAmount, "VUP Financial Services loan disbursement"),
                LedgerLeg("loan_payable", LedgerAccountType.LOAN_PAYABLE, LedgerDirection.DEBIT, loan.principalAmount, "VUP Financial Services principal owed"),
            ),
        )

        loan.status = VupLoanStatus.DISBURSED
        loan.disbursedAt = Instant.now()
        // A reasonable, honestly-named term chosen by itunda itself -- 12 months --
        // not a claimed reproduction of VUP/FS's real exact term, which the sourcing
        // didn't specify precisely.
        loan.dueDate = LocalDate.now().plusMonths(12)
        return vupLoanRepository.save(loan)
    }

    @Transactional
    fun repay(userId: String, loanId: String, amount: BigDecimal): VupLoan {
        val loan = vupLoanRepository.findById(loanId).orElseThrow { VupLoanNotFoundException("VUP loan not found") }
        if (loan.userId != userId) throw VupLoanNotFoundException("VUP loan not found")
        if (loan.status != VupLoanStatus.DISBURSED && loan.status != VupLoanStatus.OVERDUE) {
            throw VupLoanNotRepayableException("Only a DISBURSED or OVERDUE loan can be repaid")
        }
        if (amount <= BigDecimal.ZERO) throw VupLoanInvalidRepayAmountException("Repayment amount must be positive")

        val wallet = walletRepository.findByUserIdAndType(userId, WalletType.MAIN)
            ?: throw VupLoanNoWalletException("No wallet found for this account")

        // Clamp BEFORE ever touching the ledger -- the exact overshoot-clamp lesson
        // this session just learned fixing InsuranceService.contributeToFund. Never
        // post the raw amount to the ledger and cap the field separately.
        val actualAmount = amount.min(loan.outstandingPrincipal)

        ledgerService.postLedgerTransaction(
            wallet.currency,
            listOf(
                LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, actualAmount, "VUP Financial Services loan repayment"),
                LedgerLeg("loan_payable", LedgerAccountType.LOAN_PAYABLE, LedgerDirection.CREDIT, actualAmount, "VUP Financial Services loan repayment"),
            ),
        )

        loan.outstandingPrincipal = loan.outstandingPrincipal.subtract(actualAmount)
        loan.status = when {
            loan.outstandingPrincipal <= BigDecimal.ZERO -> VupLoanStatus.REPAID
            loan.dueDate != null && loan.dueDate!!.isBefore(LocalDate.now()) -> VupLoanStatus.OVERDUE
            else -> VupLoanStatus.DISBURSED
        }
        return vupLoanRepository.save(loan)
    }

    fun getMyLoans(userId: String): List<VupLoan> = vupLoanRepository.findByUserId(userId)

    fun getLoan(userId: String, loanId: String): VupLoan {
        val loan = vupLoanRepository.findById(loanId).orElseThrow { VupLoanNotFoundException("VUP loan not found") }
        if (loan.userId != userId) throw VupLoanNotFoundException("VUP loan not found")
        return loan
    }

    fun getEligibility(userId: String): Map<String, Any?> {
        val hasActiveLoan = vupLoanRepository.findByUserIdAndStatusIn(userId, ACTIVE_STATUSES).isNotEmpty()
        return mapOf(
            "hasActiveLoan" to hasActiveLoan,
            "canApply" to !hasActiveLoan,
            "minUbudeheCategory" to MIN_ELIGIBLE_UBUDEHE_CATEGORY,
            "maxUbudeheCategory" to MAX_ELIGIBLE_UBUDEHE_CATEGORY,
            "interestRate" to 0.11,
            "maxAmount" to MAX_VUP_LOAN_AMOUNT,
        )
    }

    // For the overdue scheduler: DISBURSED loans past their due date, still owing.
    fun getLoansDueForOverdueCheck(): List<VupLoan> {
        val today = LocalDate.now()
        return vupLoanRepository.findAll().filter {
            it.status == VupLoanStatus.DISBURSED && it.outstandingPrincipal > BigDecimal.ZERO &&
                it.dueDate != null && it.dueDate!!.isBefore(today)
        }
    }

    @Transactional
    fun markOverdue(loan: VupLoan) {
        loan.status = VupLoanStatus.OVERDUE
        vupLoanRepository.save(loan)
    }
}
