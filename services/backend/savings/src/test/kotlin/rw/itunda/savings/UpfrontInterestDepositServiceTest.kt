package rw.itunda.savings

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.UpfrontInterestDepositRepository
import rw.itunda.core.account.AccountNumberGenerator

/**
 * First test coverage for UpfrontInterestDepositService -- previously only its own
 * scheduler had a dedicated test (UpfrontInterestDepositSchedulerTest.kt). Covers the
 * real anti-spam/cost limit newly wired into withdraw -- see the service's own doc
 * comment.
 */
class UpfrontInterestDepositServiceTest : BehaviorSpec({

    fun service(
        depositRepository: UpfrontInterestDepositRepository = mockk(),
        accountRepository: AccountRepository = mockk(),
        ledgerService: LedgerService = mockk(),
        rateLimiter: RateLimiter = mockk(relaxed = true),
        accountNumberGenerator: AccountNumberGenerator = mockk(relaxed = true),
        ledgerEntryRepository: LedgerEntryRepository = mockk(relaxed = true),
    ) = UpfrontInterestDepositService(depositRepository, accountRepository, ledgerService, rateLimiter, accountNumberGenerator, ledgerEntryRepository)

    Given("a real caller who has already exceeded a real upfront-deposit rate limit") {
        val depositRepository = mockk<UpfrontInterestDepositRepository>()
        val rateLimiter = mockk<RateLimiter>()
        val svc = service(depositRepository = depositRepository, rateLimiter = rateLimiter)
        every { rateLimiter.checkLimit("upfront-deposit:withdraw:user_1", limit = 10, window = any()) } throws RateLimitExceededException("Too many requests")

        When("withdrawing a matured deposit") {
            Then("a real RateLimitExceededException fires before ever touching the real deposit row") {
                try {
                    svc.withdraw("user_1", "upfront_1")
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { depositRepository.findById(any()) }
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
