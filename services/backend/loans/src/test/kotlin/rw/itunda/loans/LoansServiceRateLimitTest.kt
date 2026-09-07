package rw.itunda.loans

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.creditscore.CreditScoreService
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.repository.LoanAccountRepository
import rw.itunda.core.repository.NotificationRepository
import java.math.BigDecimal

/**
 * Extracted from LoansServiceTest.kt (2026-09-07, Loans product-completeness pass)
 * once that file crossed the file-size-lint 500-line guideline -- same
 * "code that changes together lives together" extraction this repo's own
 * MyProfileCards.tsx precedent already establishes. Real anti-spam/cost limit newly
 * wired into applyForLoan/repayLoan/refinanceLoan -- see LoansService.kt's own doc
 * comments.
 */
class LoansServiceRateLimitTest : BehaviorSpec({

    Given("a real caller who has already exceeded a real loan rate limit") {
        val accountRepository = mockk<AccountRepository>()
        val loanAccountRepository = mockk<LoanAccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val creditScoreService = mockk<CreditScoreService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>()
        val service = LoansService(accountRepository, loanAccountRepository, ledgerService, creditScoreService, notificationRepository, pushNotificationService, rateLimiter)

        When("applying for a real loan") {
            every { rateLimiter.checkLimit("loans:apply:user_1", limit = 5, window = any()) } throws RateLimitExceededException("Too many requests")

            Then("a real RateLimitExceededException fires before ever touching the real credit score") {
                try {
                    service.applyForLoan("user_1", "loan_1", BigDecimal("100000"))
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { creditScoreService.computeScore(any()) }
                }
            }
        }

        When("repaying a real loan") {
            every { rateLimiter.checkLimit("loans:repay:user_1", limit = 30, window = any()) } throws RateLimitExceededException("Too many requests")

            Then("a real RateLimitExceededException fires before ever touching the real loan row") {
                try {
                    service.repayLoan("user_1", "loan_1", BigDecimal("1000"))
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { loanAccountRepository.findByIdForUpdate(any()) }
                }
            }
        }
    }
})
