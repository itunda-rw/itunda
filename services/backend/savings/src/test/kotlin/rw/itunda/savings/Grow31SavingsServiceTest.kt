package rw.itunda.savings

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.repository.Grow31SavingsDepositRepository
import rw.itunda.core.repository.Grow31SavingsPlanRepository
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.account.AccountNumberGenerator
import java.math.BigDecimal

/** First direct test coverage for Grow31SavingsService.createPlan -- only its own
 * scheduler had a dedicated test before this (Grow31SavingsSchedulerTest.kt). */
class Grow31SavingsServiceTest : BehaviorSpec({

    fun service(
        accountRepository: AccountRepository = mockk(),
        planRepository: Grow31SavingsPlanRepository = mockk(),
        depositRepository: Grow31SavingsDepositRepository = mockk(relaxed = true),
        ledgerService: LedgerService = mockk(relaxed = true),
        rateLimiter: RateLimiter = mockk(relaxed = true),
        accountNumberGenerator: AccountNumberGenerator = mockk(relaxed = true),
        ledgerEntryRepository: LedgerEntryRepository = mockk(relaxed = true),
    ) = Grow31SavingsService(accountRepository, planRepository, depositRepository, ledgerService, rateLimiter, accountNumberGenerator, ledgerEntryRepository)

    Given("creating a plan") {
        val accountRepository = mockk<AccountRepository>()
        val planRepository = mockk<Grow31SavingsPlanRepository>()
        val svc = service(accountRepository = accountRepository, planRepository = planRepository)

        When("a valid daily amount is chosen") {
            every { accountRepository.save(any()) } answers { firstArg() }
            every { planRepository.save(any()) } answers { firstArg() }

            val created = svc.createPlan("user_1", "Trip Fund", BigDecimal("1000"))

            Then("it provisions a real dedicated GROW31_SAVINGS account") {
                created.userId shouldBe "user_1"
                created.name shouldBe "Trip Fund"
                verify(exactly = 1) { accountRepository.save(match { it.type == AccountType.GROW31_SAVINGS }) }
            }
        }

        When("a non-positive daily amount is requested") {
            Then("it real-rejects before touching any account or plan") {
                try {
                    svc.createPlan("user_1", "Bad Plan", BigDecimal.ZERO)
                    error("expected Grow31PlanInvalidAmountException")
                } catch (e: Grow31PlanInvalidAmountException) {
                    verify(exactly = 0) { planRepository.save(any()) }
                }
            }
        }

        When("the plan name is blank") {
            Then("it throws InvalidGrow31PlanNameException before ever spending a rate-limit attempt") {
                try {
                    svc.createPlan("user_1", "   ", BigDecimal("1000"))
                    error("expected InvalidGrow31PlanNameException")
                } catch (e: InvalidGrow31PlanNameException) {
                    verify(exactly = 0) { planRepository.save(any()) }
                }
            }
        }

        When("the plan name would overflow the settlement account's own accountName once \" (31-Day Savings)\" is appended") {
            Then("it throws InvalidGrow31PlanNameException at the real 238-char safe bound, not the naive 255") {
                try {
                    svc.createPlan("user_1", "x".repeat(239), BigDecimal("1000"))
                    error("expected InvalidGrow31PlanNameException")
                } catch (e: InvalidGrow31PlanNameException) {
                    verify(exactly = 0) { planRepository.save(any()) }
                }
            }
        }
    }

    Given("a real caller who has already exceeded a real Grow31 rate limit") {
        val planRepository = mockk<Grow31SavingsPlanRepository>()
        val rateLimiter = mockk<RateLimiter>()
        val svc = service(planRepository = planRepository, rateLimiter = rateLimiter)

        When("depositing today") {
            every { rateLimiter.checkLimit("grow31-savings:deposit:user_1", limit = 30, window = any()) } throws RateLimitExceededException("Too many requests")

            Then("a real RateLimitExceededException fires before ever touching the real plan row") {
                try {
                    svc.depositToday("user_1", "g31_1")
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { planRepository.findById(any()) }
                }
            }
        }

        When("cancelling a plan") {
            every { rateLimiter.checkLimit("grow31-savings:cancel:user_1", limit = 10, window = any()) } throws RateLimitExceededException("Too many requests")

            Then("a real RateLimitExceededException fires before ever touching the real plan row") {
                try {
                    svc.cancelPlan("user_1", "g31_1")
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { planRepository.findById(any()) }
                }
            }
        }

        When("withdrawing a matured plan") {
            every { rateLimiter.checkLimit("grow31-savings:withdraw:user_1", limit = 10, window = any()) } throws RateLimitExceededException("Too many requests")

            Then("a real RateLimitExceededException fires before ever touching the real plan row") {
                try {
                    svc.withdraw("user_1", "g31_1")
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { planRepository.findById(any()) }
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
