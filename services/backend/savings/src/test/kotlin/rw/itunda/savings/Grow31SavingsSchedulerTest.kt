package rw.itunda.savings

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.Grow31SavingsPlan
import java.math.BigDecimal
import java.time.LocalDate

/**
 * First test coverage for Grow31SavingsScheduler -- and a real, previously-live bug
 * fix, not just a missing test. `maturePlan` calls `ledgerService.postLedgerTransaction`
 * (the real maturity-interest payout) with no try/catch of its own, and this loop had
 * none either -- a single bad plan (a since-deleted account, a transient ledger
 * error) would throw uncaught and silently stop maturity processing for every OTHER
 * real due plan the same day. Fixed alongside this test (same commit) with a per-plan
 * try/catch.
 */
class Grow31SavingsSchedulerTest : BehaviorSpec({

    fun plan(id: String) = Grow31SavingsPlan(
        id = id, userId = "user_$id", accountId = "account_$id", name = "31-day plan",
        dailyAmount = BigDecimal("1000"), startDate = LocalDate.now().minusDays(31), baseRate = 5.0,
    )

    Given("3 plans due for maturity, where maturing the middle one fails") {
        val grow31SavingsService = mockk<Grow31SavingsService>()
        val p1 = plan("p1")
        val p2 = plan("p2")
        val p3 = plan("p3")
        every { grow31SavingsService.getPlansDueForMaturity() } returns listOf(p1, p2, p3)
        every { grow31SavingsService.maturePlan(p1) } returns Unit
        every { grow31SavingsService.maturePlan(p2) } throws RuntimeException("unexpected ledger error")
        every { grow31SavingsService.maturePlan(p3) } returns Unit
        val scheduler = Grow31SavingsScheduler(grow31SavingsService)

        When("the sweep runs") {
            val processed = scheduler.processDue()

            Then("all 3 are still attempted -- the middle failure doesn't stop maturity for the rest") {
                verify(exactly = 1) { grow31SavingsService.maturePlan(p1) }
                verify(exactly = 1) { grow31SavingsService.maturePlan(p2) }
                verify(exactly = 1) { grow31SavingsService.maturePlan(p3) }
            }
            Then("the real due count still reflects all 3, regardless of the middle failure") {
                processed shouldBe 3
            }
        }
    }

    Given("no plans due for maturity") {
        val grow31SavingsService = mockk<Grow31SavingsService>()
        every { grow31SavingsService.getPlansDueForMaturity() } returns emptyList()
        val scheduler = Grow31SavingsScheduler(grow31SavingsService)

        When("the sweep runs") {
            scheduler.run()

            Then("nothing is matured, no exception is thrown") {
                verify(exactly = 0) { grow31SavingsService.maturePlan(any()) }
            }
        }
    }
})
