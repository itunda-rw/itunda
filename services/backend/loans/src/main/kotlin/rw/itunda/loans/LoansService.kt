package rw.itunda.loans

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.core.creditscore.CreditScoreService
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.LoanAccount
import rw.itunda.core.domain.LoanStatus
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.LoanAccountRepository
import rw.itunda.core.repository.NotificationRepository
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
class NoBetterRateAvailableException(message: String) : RuntimeException(message)

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
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
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

        // Real Toss-style 자산 보호 알림 (Asset Protection Alert, launched May 2025) equivalent,
        // honestly scoped to itunda's own system boundary (no MyData/cross-institution access):
        // alert the real account owner whenever a new real financial product -- here, a loan --
        // is disbursed under their identity, so a hijacked session/stolen credentials can't take
        // out a loan with zero alert to the real owner. Follows DeviceService's own real
        // NEW_DEVICE_LOGIN notification convention (clear title + explanatory body + dataJson).
        //
        // Real push wired in (2026-07-28), same real security-alert urgency
        // DeviceService.recordLoginDevice's own push already established: a hijacked
        // account taking out a real loan needs the real owner to know the instant it
        // happens, not whenever they next open the app.
        val title = "New loan opened in your name"
        val body = "${offer.name} for ${amount} was just disbursed to your wallet. If this wasn't you, secure your account immediately."
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = userId, type = "NEW_LOAN_DISBURSED",
                title = title, body = body,
                isRead = false, createdAt = Instant.now(), dataJson = "{\"loanId\":\"${loan.id}\"}",
            ),
        )
        sendLoanDisbursedPushAfterCommit(userId, title, body, loan.id)

        return mapOf(
            "id" to loan.id, "type" to offer.name, "amount" to loan.principal,
            "status" to "approved", "disbursedAt" to loan.disbursedAt.toString(), "creditScore" to score,
        )
    }

    /** The security push is irreversible; send it only after the loan and ledger commit. */
    private fun sendLoanDisbursedPushAfterCommit(userId: String, title: String, body: String, loanId: String) {
        val send = { pushNotificationService.sendToUser(userId, title, body, mapOf("loanId" to loanId)) }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = send()
        })
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

    /**
     * Real 대환대출 (loan refinancing) -- see toss.im/tossfeed/article/toss-refinancing and
     * tossbank.com/product-service/loans/switching-platform: pay off a loan carrying a
     * higher rate by taking out a new one at a lower rate, with the new loan's
     * disbursement settling the old loan directly rather than the borrower having to
     * find the payoff amount out of pocket first ("기존 대출을 갚으면서, 새로 대출이
     * 실행" -- the existing loan is repaid as the new one executes).
     *
     * Honestly scoped to itunda's own book only, not a real cross-institution
     * comparison: `LenderCatalog`'s own doc comment already establishes that the partner
     * banks (BK/Equity/Urwego) have no real live loan-origination integration behind
     * them, so there is nothing genuine to "switch into" at another institution --
     * itunda IS both the source and destination lender here, the same real role Toss
     * Bank itself plays in its own real 대환대출 flow when a user refinances INTO Toss
     * Bank from elsewhere. Eligibility reuses the exact same real underwriting gate
     * `applyForLoan` already enforces (current credit score, not the score at original
     * disbursement -- a real reason to refinance is that your score has genuinely
     * improved since then), picking the single lowest real rate the borrower currently
     * qualifies for that's strictly better than what they're already paying and whose
     * cap can cover the real remaining balance.
     *
     * Posts two real, separate ledger transactions (new-loan disbursement, then old-loan
     * payoff) rather than one that nets to a zero wallet delta -- the real audit trail
     * should show both events actually happened, not be silently collapsed into a field
     * mutation just because the user's own wallet balance doesn't move.
     */
    @Transactional
    fun refinanceLoan(userId: String, loanId: String): Map<String, Any?> {
        val loan = loanAccountRepository.findById(loanId).orElseThrow { LoanNotFoundException("Loan not found") }
        if (loan.userId != userId) throw LoanNotOwnedException("That loan does not belong to you")
        if (loan.status != LoanStatus.ACTIVE) throw LoanAlreadyPaidException("Only an active loan can be refinanced")

        val score = creditScoreService.computeScore(userId).score
        val eligibleOffer = LoanCatalog.offers
            .filter { it.lenderId == "lender_itunda" }
            .filter { it.interestRate < loan.interestRate }
            .filter { it.maxAmount >= loan.outstanding }
            .filter { offer ->
                if (score < MIN_SCORE_TO_QUALIFY) return@filter false
                val isHighAmount = loan.outstanding > offer.maxAmount.multiply(HIGH_AMOUNT_FRACTION)
                !isHighAmount || score >= HIGH_AMOUNT_SCORE_THRESHOLD
            }
            .minByOrNull { it.interestRate }
            ?: throw NoBetterRateAvailableException("No itunda offer currently beats this loan's ${loan.interestRate}% rate for your credit profile")

        val wallet = walletRepository.findById(loan.walletId).orElseThrow { NoWalletException("Wallet not found") }
        val refinanceAmount = loan.outstanding
        val oldInterestRate = loan.interestRate

        ledgerService.postLedgerTransaction(
            wallet.currency,
            listOf(
                LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, refinanceAmount, "${eligibleOffer.name} refinance disbursement"),
                LedgerLeg("loan_payable", LedgerAccountType.LOAN_PAYABLE, LedgerDirection.DEBIT, refinanceAmount, "${eligibleOffer.name} refinance principal owed"),
            ),
        )
        val newLoan = loanAccountRepository.save(
            LoanAccount(
                id = "loan_${UUID.randomUUID()}", userId = userId, walletId = wallet.id, offerId = eligibleOffer.id,
                principal = refinanceAmount, outstanding = refinanceAmount, interestRate = eligibleOffer.interestRate,
                status = LoanStatus.ACTIVE, disbursedAt = Instant.now(),
            ),
        )

        ledgerService.postLedgerTransaction(
            wallet.currency,
            listOf(
                LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, refinanceAmount, "Refinance payoff of loan ${loan.id}"),
                LedgerLeg("loan_payable", LedgerAccountType.LOAN_PAYABLE, LedgerDirection.CREDIT, refinanceAmount, "Refinance payoff of loan ${loan.id}"),
            ),
        )
        loan.outstanding = BigDecimal.ZERO
        loan.status = LoanStatus.PAID
        loanAccountRepository.save(loan)

        return mapOf(
            "oldLoanId" to loan.id, "oldInterestRate" to oldInterestRate,
            "newLoanId" to newLoan.id, "newInterestRate" to newLoan.interestRate, "newLoanName" to eligibleOffer.name,
            "amount" to refinanceAmount, "creditScore" to score,
        )
    }
}
