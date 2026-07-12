package rw.itunda.core.creditscore

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.LoanStatus
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.repository.LoanAccountRepository
import rw.itunda.core.repository.SavingsGoalRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.UserRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.min

class CreditScoreUserNotFoundException(message: String) : RuntimeException(message)

data class CreditScoreFactor(val name: String, val points: Int, val description: String)
data class CreditScoreResult(val score: Int, val factors: List<CreditScoreFactor>, val computedAt: Instant)

/**
 * Real "alternative data" scoring, matching Toss's own described model
 * (docs/TOSS_PARITY_MATRIX.md's Credit score row: "Free credit check | Alternative data with
 * consent") -- not a real credit bureau score, which needs regulatory access this system
 * doesn't have. Computed live from itunda's own real transaction/loan/savings/KYC history on
 * every call, not cached beyond writing the result back onto User.creditScore for profile
 * display -- there is no separate "run a check" action to model, unlike a real bureau pull.
 *
 * Moved from the creditscore module into :core (2026-07-13) so LoansService's real
 * affordability/risk-model governance (docs/TOSS_PARITY_MATRIX.md's Credit row) can share
 * this exact computation rather than reading a possibly-stale User.creditScore or
 * duplicating the scoring logic -- same reasoning LedgerService lives in :core: more than
 * one feature module needs it, so it can't live inside just one of them.
 */
@Service
class CreditScoreService(
    private val userRepository: UserRepository,
    private val transactionRepository: TransactionRepository,
    private val loanAccountRepository: LoanAccountRepository,
    private val savingsGoalRepository: SavingsGoalRepository,
) {

    @Transactional
    fun computeScore(userId: String): CreditScoreResult {
        val user = userRepository.findById(userId).orElseThrow { CreditScoreUserNotFoundException("User not found") }
        val factors = mutableListOf<CreditScoreFactor>()

        factors += CreditScoreFactor("Base score", 300, "Starting point for every account")

        if (user.kycVerified) {
            factors += CreditScoreFactor("Identity verified", 100, "KYC submission reviewed and approved")
        }

        val accountAgeDays = ChronoUnit.DAYS.between(user.createdAt, Instant.now())
        val agePoints = min(100, (accountAgeDays / 7).toInt())
        if (agePoints > 0) {
            factors += CreditScoreFactor("Account history", agePoints, "$accountAgeDays days since account creation")
        }

        val completedTransactions = transactionRepository
            .findBySenderIdOrRecipientIdOrderByCreatedAtDesc(userId, userId)
            .count { it.status == TransactionStatus.COMPLETED }
        val transactionPoints = min(100, completedTransactions * 2)
        if (transactionPoints > 0) {
            factors += CreditScoreFactor("Transaction activity", transactionPoints, "$completedTransactions completed transactions")
        }

        val loans = loanAccountRepository.findByUserId(userId)
        when {
            loans.any { it.status == LoanStatus.PAID } ->
                factors += CreditScoreFactor("Loan repayment history", 100, "At least one loan fully repaid")
            loans.any { it.status == LoanStatus.ACTIVE } ->
                factors += CreditScoreFactor("Active credit usage", 20, "Currently repaying a loan, not yet fully paid")
        }

        val hasSavingsActivity = savingsGoalRepository.findByUserId(userId).any { it.currentAmount.signum() > 0 }
        if (hasSavingsActivity) {
            factors += CreditScoreFactor("Savings activity", 50, "At least one savings goal with real contributions")
        }

        val score = min(850, factors.sumOf { it.points })
        user.creditScore = score
        userRepository.save(user)

        return CreditScoreResult(score, factors, Instant.now())
    }
}
