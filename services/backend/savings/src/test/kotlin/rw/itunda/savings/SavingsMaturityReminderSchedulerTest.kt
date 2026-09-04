package rw.itunda.savings

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.SavingsGoal
import java.math.BigDecimal

/**
 * First test coverage for SavingsMaturityReminderScheduler -- and a real,
 * previously-live bug fix, not just a missing test. `sendMaturityReminder` had no
 * try/catch of its own, and this loop had none either. Fixed alongside this test
 * (same commit) with a per-goal try/catch.
 */
class SavingsMaturityReminderSchedulerTest : BehaviorSpec({

    fun goal(id: String) = SavingsGoal(
        id = id, userId = "user_$id", accountId = "account_$id", name = "Trip",
        targetAmount = BigDecimal("100000"), currentAmount = BigDecimal("100000"),
        monthlyContribution = BigDecimal("5000"), interestRate = 0.0,
    )

    Given("3 goals due for a maturity reminder, where sending the middle one fails") {
        val savingsService = mockk<SavingsService>()
        val g1 = goal("g1")
        val g2 = goal("g2")
        val g3 = goal("g3")
        every { savingsService.getGoalsDueForMaturityReminder() } returns listOf(g1, g2, g3)
        every { savingsService.sendMaturityReminder("g1") } returns Unit
        every { savingsService.sendMaturityReminder("g2") } throws RuntimeException("messaging service unreachable")
        every { savingsService.sendMaturityReminder("g3") } returns Unit
        val scheduler = SavingsMaturityReminderScheduler(savingsService)

        When("the sweep runs") {
            val processed = scheduler.processDue()

            Then("all 3 are still attempted -- the middle failure doesn't stop the rest") {
                verify(exactly = 1) { savingsService.sendMaturityReminder("g1") }
                verify(exactly = 1) { savingsService.sendMaturityReminder("g2") }
                verify(exactly = 1) { savingsService.sendMaturityReminder("g3") }
            }
            Then("the real due count still reflects all 3, regardless of the middle failure") {
                processed shouldBe 3
            }
        }
    }

    Given("no goals due for a reminder") {
        val savingsService = mockk<SavingsService>()
        every { savingsService.getGoalsDueForMaturityReminder() } returns emptyList()
        val scheduler = SavingsMaturityReminderScheduler(savingsService)

        When("the sweep runs") {
            scheduler.run()

            Then("nothing is sent, no exception is thrown") {
                verify(exactly = 0) { savingsService.sendMaturityReminder(any()) }
            }
        }
    }
})
