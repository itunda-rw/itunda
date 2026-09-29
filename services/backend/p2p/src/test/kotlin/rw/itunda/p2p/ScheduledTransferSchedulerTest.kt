package rw.itunda.p2p

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.ScheduledTransfer
import java.math.BigDecimal
import java.time.LocalDate

class ScheduledTransferSchedulerTest : BehaviorSpec({

    fun scheduledTransfer(id: String) = ScheduledTransfer(
        id = id, userId = "user_$id", accountId = "account_$id", recipientIdentifier = "+250788111222",
        recipientName = "Eric", amount = BigDecimal("1000"), scheduledDate = LocalDate.now(),
    )

    Given("3 due scheduled transfers, where executing the middle one fails") {
        val scheduledTransferService = mockk<ScheduledTransferService>()
        val t1 = scheduledTransfer("t1")
        val t2 = scheduledTransfer("t2")
        val t3 = scheduledTransfer("t3")
        every { scheduledTransferService.getDueForExecution() } returns listOf(t1, t2, t3)
        every { scheduledTransferService.executeOne(t1) } returns true
        every { scheduledTransferService.executeOne(t2) } throws RuntimeException("optimistic lock failure")
        every { scheduledTransferService.executeOne(t3) } returns true
        val scheduler = ScheduledTransferScheduler(scheduledTransferService)

        When("the sweep runs") {
            scheduler.run()

            Then("all 3 are still attempted -- the middle failure doesn't stop executing the rest") {
                verify(exactly = 1) { scheduledTransferService.executeOne(t1) }
                verify(exactly = 1) { scheduledTransferService.executeOne(t2) }
                verify(exactly = 1) { scheduledTransferService.executeOne(t3) }
            }
        }
    }

    Given("no due scheduled transfers") {
        val scheduledTransferService = mockk<ScheduledTransferService>()
        every { scheduledTransferService.getDueForExecution() } returns emptyList()
        val scheduler = ScheduledTransferScheduler(scheduledTransferService)

        When("the sweep runs") {
            scheduler.run()

            Then("nothing is executed, no exception is thrown") {
                verify(exactly = 0) { scheduledTransferService.executeOne(any()) }
            }
        }
    }
})
