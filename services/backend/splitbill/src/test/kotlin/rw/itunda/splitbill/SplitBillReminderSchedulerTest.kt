package rw.itunda.splitbill

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

/**
 * SplitBillService.sendDueReminders already guards its own per-participant loop (see
 * that method's own try/catch); this scheduler's own try/catch is a different, simpler
 * shape -- a whole-call guard, so an unexpected failure from the single sendDueReminders
 * call (e.g. the repository read itself failing) never crashes the @Scheduled thread and
 * silently kills every future tick, the same way any other scheduler in this codebase
 * without its own guard would.
 */
class SplitBillReminderSchedulerTest : BehaviorSpec({
    Given("sendDueReminders fails unexpectedly") {
        val service = mockk<SplitBillService>()
        every { service.sendDueReminders() } throws IllegalStateException("database connection lost")
        val scheduler = SplitBillReminderScheduler(service)

        When("the scheduler runs its tick") {
            Then("the failure is contained, not propagated to the @Scheduled thread") {
                shouldNotThrowAny { scheduler.run() }
            }
        }
    }

    Given("sendDueReminders succeeds") {
        val service = mockk<SplitBillService>()
        every { service.sendDueReminders() } returns 3
        val scheduler = SplitBillReminderScheduler(service)

        When("the scheduler runs its tick") {
            scheduler.run()

            Then("it delegates the real reminder sweep exactly once") {
                verify(exactly = 1) { service.sendDueReminders() }
            }
        }
    }
})
