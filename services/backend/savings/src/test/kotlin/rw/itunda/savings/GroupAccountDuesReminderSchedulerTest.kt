package rw.itunda.savings

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.GroupAccount
import java.math.BigDecimal

/**
 * Same loop-resilience contract every other per-item scheduler in this codebase
 * proves -- see GroupAccountDuesReminderScheduler's own doc comment for the real
 * batch-transaction-poisoning bug this shape closed.
 */
class GroupAccountDuesReminderSchedulerTest : BehaviorSpec({
    fun account(id: String) = GroupAccount(id = id, name = "Family fund", ownerId = "owner_1", accountId = "acct_$id", monthlyDuesAmount = BigDecimal("5000"))

    Given("3 group accounts with dues configured, where the middle one's reminder sweep fails") {
        val service = mockk<GroupAccountService>()
        every { service.getAccountsWithDuesConfigured() } returns listOf(account("ga_1"), account("ga_2"), account("ga_3"))
        every { service.sendAutomaticDuesRemindersFor("ga_1") } returns 2
        every { service.sendAutomaticDuesRemindersFor("ga_2") } throws IllegalStateException("dedupe table write failed")
        every { service.sendAutomaticDuesRemindersFor("ga_3") } returns 1
        val scheduler = GroupAccountDuesReminderScheduler(service)

        When("the scheduler sweeps due accounts") {
            scheduler.run()

            Then("all 3 accounts are still attempted, not just the ones before the failure") {
                verify(exactly = 1) { service.sendAutomaticDuesRemindersFor("ga_1") }
                verify(exactly = 1) { service.sendAutomaticDuesRemindersFor("ga_2") }
                verify(exactly = 1) { service.sendAutomaticDuesRemindersFor("ga_3") }
            }
        }
    }

    Given("no group accounts with dues configured") {
        val service = mockk<GroupAccountService>()
        every { service.getAccountsWithDuesConfigured() } returns emptyList()
        val scheduler = GroupAccountDuesReminderScheduler(service)

        When("the scheduler sweeps due accounts") {
            scheduler.run()

            Then("it does nothing") {
                verify(exactly = 0) { service.sendAutomaticDuesRemindersFor(any()) }
            }
        }
    }
})
