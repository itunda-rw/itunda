package rw.itunda.core.creditscore

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.LoanStatus
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.repository.DebitCardRepository
import rw.itunda.core.repository.DebitCardTransactionRepository
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
data class CreditScoreSuggestion(val action: String, val pointsGain: Int, val description: String)

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
    private val debitCardRepository: DebitCardRepository,
    private val debitCardTransactionRepository: DebitCardTransactionRepository,
) {
    companion object {
        // Named, not inline literals (2026-07-26) -- extracted so
        // getImprovementSuggestions can reuse the exact real point values computeScore
        // itself awards, rather than a second, independently-maintained copy that could
        // silently drift out of sync. Behavior-preserving: same values as before.
        const val BASE_SCORE_POINTS = 300
        const val KYC_VERIFIED_POINTS = 100
        const val MAX_ACCOUNT_AGE_POINTS = 100
        const val MAX_TRANSACTION_POINTS = 100
        const val POINTS_PER_TRANSACTION = 2
        const val LOAN_PAID_POINTS = 100
        const val LOAN_ACTIVE_POINTS = 20
        const val SAVINGS_ACTIVITY_POINTS = 50
        // Real Nubank NuScore-style factor (2026-08-16, sourced from Nu International's
        // own real launch coverage: "credit card usage data" is one of NuScore's named
        // real factors alongside spending behavior and savings habits) -- see this
        // class's own doc comment for the full account of why this was a genuine,
        // confirmed gap before this addition.
        const val MAX_CARD_USAGE_POINTS = 50
        const val POINTS_PER_CARD_TRANSACTION = 5
        const val MAX_SCORE = 850
    }

    @Transactional
    fun computeScore(userId: String): CreditScoreResult {
        val user = userRepository.findById(userId).orElseThrow { CreditScoreUserNotFoundException("User not found") }
        val factors = mutableListOf<CreditScoreFactor>()

        factors += CreditScoreFactor("Base score", BASE_SCORE_POINTS, "Starting point for every account")

        if (user.kycVerified) {
            factors += CreditScoreFactor("Identity verified", KYC_VERIFIED_POINTS, "KYC submission reviewed and approved")
        }

        val accountAgeDays = ChronoUnit.DAYS.between(user.createdAt, Instant.now())
        val agePoints = min(MAX_ACCOUNT_AGE_POINTS, (accountAgeDays / 7).toInt())
        if (agePoints > 0) {
            factors += CreditScoreFactor("Account history", agePoints, "$accountAgeDays days since account creation")
        }

        val completedTransactions = transactionRepository
            .findBySenderIdOrRecipientIdOrderByCreatedAtDesc(userId, userId)
            .count { it.status == TransactionStatus.COMPLETED }
        val transactionPoints = min(MAX_TRANSACTION_POINTS, completedTransactions * POINTS_PER_TRANSACTION)
        if (transactionPoints > 0) {
            factors += CreditScoreFactor("Transaction activity", transactionPoints, "$completedTransactions completed transactions")
        }

        val loans = loanAccountRepository.findByUserId(userId)
        when {
            loans.any { it.status == LoanStatus.PAID } ->
                factors += CreditScoreFactor("Loan repayment history", LOAN_PAID_POINTS, "At least one loan fully repaid")
            loans.any { it.status == LoanStatus.ACTIVE } ->
                factors += CreditScoreFactor("Active credit usage", LOAN_ACTIVE_POINTS, "Currently repaying a loan, not yet fully paid")
        }

        val hasSavingsActivity = savingsGoalRepository.findByUserId(userId).any { it.currentAmount.signum() > 0 }
        if (hasSavingsActivity) {
            factors += CreditScoreFactor("Savings activity", SAVINGS_ACTIVITY_POINTS, "At least one savings goal with real contributions")
        }

        val card = debitCardRepository.findByUserId(userId)
        if (card != null) {
            val cardTransactionCount = debitCardTransactionRepository.countByCardId(card.id)
            val cardPoints = min(MAX_CARD_USAGE_POINTS, (cardTransactionCount * POINTS_PER_CARD_TRANSACTION).toInt())
            if (cardPoints > 0) {
                factors += CreditScoreFactor("Card usage", cardPoints, "$cardTransactionCount real card purchase(s)")
            }
        }

        val score = min(MAX_SCORE, factors.sumOf { it.points })
        user.creditScore = score
        userRepository.save(user)

        return CreditScoreResult(score, factors, Instant.now())
    }

    /**
     * Real "what would move your score" suggestions -- closes Toss's own real 신용플러스
     * (Credit Plus) feature (tossbank.com/articles/raise-credit-score, asiae.co.kr):
     * a real, personalized breakdown of which action would raise a user's score and by
     * how much -- "여러 대출이 있다면 어느 대출을 얼마나 상환하면 신용점수가 어떻게 변하는지
     * 알려줍니다" (shows how repaying which loan changes your score), "신용카드나 체크카드
     * 사용법에 따라 신용점수가 몇 점이나 오르는지" (how card usage patterns raise your score).
     *
     * Every real point value here is the exact same real threshold `computeScore`
     * itself awards (the named constants above), computed read-only from the same real
     * repository queries -- never a second, separately-estimated number that could
     * disagree with what actually happens once the user takes the action. Purely
     * additive and read-only: doesn't call `computeScore` (which writes back onto
     * `User.creditScore`), so checking suggestions never has a side effect.
     */
    fun getImprovementSuggestions(userId: String): List<CreditScoreSuggestion> {
        val user = userRepository.findById(userId).orElseThrow { CreditScoreUserNotFoundException("User not found") }
        val suggestions = mutableListOf<CreditScoreSuggestion>()

        if (!user.kycVerified) {
            suggestions += CreditScoreSuggestion(
                "Verify your identity", KYC_VERIFIED_POINTS,
                "Submit KYC and get it reviewed and approved",
            )
        }

        val completedTransactions = transactionRepository
            .findBySenderIdOrRecipientIdOrderByCreatedAtDesc(userId, userId)
            .count { it.status == TransactionStatus.COMPLETED }
        val currentTransactionPoints = min(MAX_TRANSACTION_POINTS, completedTransactions * POINTS_PER_TRANSACTION)
        if (currentTransactionPoints < MAX_TRANSACTION_POINTS) {
            val txnsToMax = (MAX_TRANSACTION_POINTS - currentTransactionPoints + POINTS_PER_TRANSACTION - 1) / POINTS_PER_TRANSACTION
            suggestions += CreditScoreSuggestion(
                "Complete more real transactions", MAX_TRANSACTION_POINTS - currentTransactionPoints,
                "$txnsToMax more completed transaction(s) reaches the real cap for this factor",
            )
        }

        val loans = loanAccountRepository.findByUserId(userId)
        val hasActiveLoan = loans.any { it.status == LoanStatus.ACTIVE }
        val hasPaidLoan = loans.any { it.status == LoanStatus.PAID }
        if (hasActiveLoan && !hasPaidLoan) {
            suggestions += CreditScoreSuggestion(
                "Pay off your active loan in full", LOAN_PAID_POINTS - LOAN_ACTIVE_POINTS,
                "A fully repaid loan earns real repayment-history points, not just active-usage points",
            )
        }

        val hasSavingsActivity = savingsGoalRepository.findByUserId(userId).any { it.currentAmount.signum() > 0 }
        if (!hasSavingsActivity) {
            suggestions += CreditScoreSuggestion(
                "Start a savings goal", SAVINGS_ACTIVITY_POINTS,
                "Make a real contribution to any savings goal",
            )
        }

        val card = debitCardRepository.findByUserId(userId)
        if (card == null) {
            suggestions += CreditScoreSuggestion(
                "Get an itunda Card", MAX_CARD_USAGE_POINTS,
                "Issue a real itunda Card and use it -- card usage is its own real scoring factor",
            )
        } else {
            val cardTransactionCount = debitCardTransactionRepository.countByCardId(card.id)
            val currentCardPoints = min(MAX_CARD_USAGE_POINTS, (cardTransactionCount * POINTS_PER_CARD_TRANSACTION).toInt())
            if (currentCardPoints < MAX_CARD_USAGE_POINTS) {
                val txnsToMax = (MAX_CARD_USAGE_POINTS - currentCardPoints + POINTS_PER_CARD_TRANSACTION - 1) / POINTS_PER_CARD_TRANSACTION
                suggestions += CreditScoreSuggestion(
                    "Use your itunda Card more", MAX_CARD_USAGE_POINTS - currentCardPoints,
                    "$txnsToMax more real card purchase(s) reaches the real cap for this factor",
                )
            }
        }

        return suggestions.sortedByDescending { it.pointsGain }
    }
}
