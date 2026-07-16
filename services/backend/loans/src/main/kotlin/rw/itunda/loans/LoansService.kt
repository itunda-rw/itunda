package rw.itunda.loans

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.creditscore.CreditScoreService
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.LoanAccount
import rw.itunda.core.domain.LoanStatus
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.LoanAccountRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class LoanOfferNotFoundException(message: String) : RuntimeException(message)
class LoanNotFoundException(message: String) : RuntimeException(message)
class LoanNotOwnedException(message: String) : RuntimeException(message)
class LoanAlreadyPaidException(message: String) : RuntimeException(message)
class LoanAmountInvalidException(message: String) : RuntimeException(message)
class LoanApplicationDeclinedException(message: String) : RuntimeException(message)
class NoWalletException(message: String) : RuntimeException(message)

// Real affordability/risk-model governance (2026-07-13) -- see
// docs/TOSS_PARITY_MATRIX.md's Credit row ("affordability/risk-model governance ... still
// target"). Before this, applyForLoan checked only that 0 < amount <= offer.maxAmount --
// any authenticated user could borrow the maximum of any offer regardless of creditworthiness
// or existing debt load. Thresholds are itunda's own simple underwriting model, not a real
// bureau-grade risk model (which needs data this system doesn't have, e.g. real income) --
// same "alternative data, not a bureau" honesty CreditScoreService itself already carries.
private const val MIN_SCORE_TO_QUALIFY = 400
private const val HIGH_AMOUNT_SCORE_THRESHOLD = 600
private val HIGH_AMOUNT_FRACTION = BigDecimal("0.5")
private const val MAX_CONCURRENT_ACTIVE_LOANS = 2

/**
 * Port of backend/src/controllers/loan.controller.ts. repayLoan carries the same
 * ownership check just added to the Express backend (SECURITY.md): without it, any
 * authenticated user could pay down — or fully clear — someone else's loan using that
 * loan's own wallet as the debit source.
 */
@Service
class LoansService(
    private val walletRepository: WalletRepository,
    private val loanAccountRepository: LoanAccountRepository,
    private val ledgerService: LedgerService,
    private val creditScoreService: CreditScoreService,
) {
    fun getOffers(lenderId: String? = null) = if (lenderId == null) LoanCatalog.offers else LoanCatalog.findByLender(lenderId)

    fun getLenders() = LenderCatalog.lenders

    fun getMyLoans(userId: String) = loanAccountRepository.findByUserId(userId).map {
        mapOf(
            "id" to it.id,
            "type" to (LoanCatalog.find(it.offerId)?.name ?: it.offerId),
            "amount" to it.principal,
            "remaining" to it.outstanding,
            "interestRate" to it.interestRate,
            "dueDate" to it.disbursedAt.toString(),
            "status" to it.status.name.lowercase(),
            "paid" to it.principal.subtract(it.outstanding),
        )
    }

    @Transactional
    fun applyForLoan(userId: String, loanId: String, amount: BigDecimal): Map<String, Any?> {
        val offer = LoanCatalog.find(loanId) ?: throw LoanOfferNotFoundException("Loan offer not found")
        if (amount <= BigDecimal.ZERO || amount > offer.maxAmount) {
            throw LoanAmountInvalidException("Amount must be between 1 and ${offer.maxAmount} for ${offer.name}")
        }

        val activeLoanCount = loanAccountRepository.findByUserId(userId).count { it.status == LoanStatus.ACTIVE }
        if (activeLoanCount >= MAX_CONCURRENT_ACTIVE_LOANS) {
            throw LoanApplicationDeclinedException("Already has $activeLoanCount active loans -- pay one down before applying for another")
        }

        // Real risk gate: reuses CreditScoreService's exact computation, the same score a
        // user sees at GET /api/v1/credit-score, not a duplicated/inline scoring copy.
        val score = creditScoreService.computeScore(userId).score
        if (score < MIN_SCORE_TO_QUALIFY) {
            throw LoanApplicationDeclinedException("Credit score $score is below the minimum $MIN_SCORE_TO_QUALIFY required for any loan")
        }
        val isHighAmount = amount > offer.maxAmount.multiply(HIGH_AMOUNT_FRACTION)
        if (isHighAmount && score < HIGH_AMOUNT_SCORE_THRESHOLD) {
            throw LoanApplicationDeclinedException("Credit score $score is below $HIGH_AMOUNT_SCORE_THRESHOLD, required for amounts over half of ${offer.name}'s limit")
        }

        val wallet = walletRepository.findByUserIdAndType(userId, WalletType.MAIN) ?: throw NoWalletException("No wallet found for this account")

        ledgerService.postLedgerTransaction(
            wallet.currency,
            listOf(
                LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, amount, "${offer.name} disbursement"),
                LedgerLeg("loan_payable", LedgerAccountType.LOAN_PAYABLE, LedgerDirection.DEBIT, amount, "${offer.name} principal owed"),
            ),
        )

        val loan = loanAccountRepository.save(
            LoanAccount(
                id = "loan_${UUID.randomUUID()}", userId = userId, walletId = wallet.id, offerId = loanId,
                principal = amount, outstanding = amount, interestRate = offer.interestRate, status = LoanStatus.ACTIVE,
                disbursedAt = Instant.now(),
            ),
        )

        return mapOf(
            "id" to loan.id, "type" to offer.name, "amount" to loan.principal,
            "status" to "approved", "disbursedAt" to loan.disbursedAt.toString(), "creditScore" to score,
        )
    }

    @Transactional
    fun repayLoan(userId: String, loanId: String, amount: BigDecimal): Map<String, Any?> {
        val loan = loanAccountRepository.findById(loanId).orElseThrow { LoanNotFoundException("Loan not found") }
        if (loan.userId != userId) throw LoanNotOwnedException("That loan does not belong to you")
        if (loan.status == LoanStatus.PAID) throw LoanAlreadyPaidException("Loan is already fully repaid")

        val repayAmount = amount.min(loan.outstanding)
        val result = ledgerService.postLedgerTransaction(
            "RWF",
            listOf(
                LedgerLeg(loan.walletId, LedgerAccountType.WALLET, LedgerDirection.DEBIT, repayAmount, "Loan repayment ${loan.id}"),
                LedgerLeg("loan_payable", LedgerAccountType.LOAN_PAYABLE, LedgerDirection.CREDIT, repayAmount, "Loan repayment ${loan.id}"),
            ),
        )

        loan.outstanding = loan.outstanding.subtract(repayAmount)
        if (loan.outstanding <= BigDecimal.ZERO) loan.status = LoanStatus.PAID
        val wallet = walletRepository.findById(loan.walletId).orElseThrow { NoWalletException("Wallet not found") }

        return mapOf(
            "transaction" to mapOf(
                "id" to result.transactionId, "amount" to repayAmount, "type" to "LOAN",
                "status" to "COMPLETED", "description" to "Loan repayment - $loanId", "completedAt" to Instant.now().toString(),
            ),
            "remaining" to loan.outstanding,
            "newBalance" to wallet.balance,
        )
    }
}
