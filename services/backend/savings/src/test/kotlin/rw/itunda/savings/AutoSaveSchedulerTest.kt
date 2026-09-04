package rw.itunda.savings

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.SavingsGoal
import java.math.BigDecimal

/**
 * First test coverage for AutoSaveScheduler -- and a real, previously-live bug fix,
 * not just a missing test. `SavingsService.autoContribute`'s own `Boolean` return
 * only covers the deliberate insufficient-funds case -- it re-fetches the goal via
 * `findByIdForUpdate(...).orElseThrow { GoalNotFoundException(...) }` and calls
 * `ledgerService.postLedgerTransaction` with no try/catch of its own, so a genuinely
 * unexpected failure (a since-deleted goal, a transient ledger error) throws
 * uncaught. This loop had no try/catch either, so that exception would silently stop
 * auto-contribution for every OTHER real due goal in the same tick. Fixed alongside
 * this test (same commit) with a per-goal try/catch.
 */
class AutoSaveSchedulerTest : BehaviorSpec({

    fun goal(id: String) = SavingsGoal(
        id = id, userId = "user_$id", accountId = "account_$id", name = "Trip",
        targetAmount = BigDecimal("100000"), currentAmount = BigDecimal.ZERO,
        monthlyContribution = BigDecimal("5000"), interestRate = 0.0,
    )

    Given("3 savings goals due for auto-contribution, where the middle one's goal row was since deleted") {
        val savingsService = mockk<SavingsService>()
        val g1 = goal("g1")
        val g2 = goal("g2")
        val g3 = goal("g3")
        every { savingsService.getGoalsDueForAutoContribution() } returns listOf(g1, g2, g3)
        every { savingsService.autoContribute(g1) } returns true
        every { savingsService.autoContribute(g2) } throws GoalNotFoundException("Goal not found")
        every { savingsService.autoContribute(g3) } returns true
        val scheduler = AutoSaveScheduler(savingsService)

        When("the sweep runs") {
            scheduler.run()

            Then("all 3 are still attempted -- the middle failure doesn't stop contribution for the rest") {
                verify(exactly = 1) { savingsService.autoContribute(g1) }
                verify(exactly = 1) { savingsService.autoContribute(g2) }
                verify(exactly = 1) { savingsService.autoContribute(g3) }
            }
        }
    }

    Given("a goal correctly skipped for insufficient funds -- the existing expected-failure path") {
        val savingsService = mockk<SavingsService>()
        val g1 = goal("g1")
        every { savingsService.getGoalsDueForAutoContribution() } returns listOf(g1)
        every { savingsService.autoContribute(g1) } returns false
        val scheduler = AutoSaveScheduler(savingsService)

        When("the sweep runs") {
            scheduler.run()

            Then("it's attempted once and treated as a normal, non-exceptional outcome") {
                verify(exactly = 1) { savingsService.autoContribute(g1) }
            }
        }
    }

    Given("no savings goals due for auto-contribution") {
        val savingsService = mockk<SavingsService>()
        every { savingsService.getGoalsDueForAutoContribution() } returns emptyList()
        val scheduler = AutoSaveScheduler(savingsService)

        When("the sweep runs") {
            scheduler.run()

            Then("nothing is contributed, no exception is thrown") {
                verify(exactly = 0) { savingsService.autoContribute(any()) }
            }
        }
    }
})
