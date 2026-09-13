package rw.itunda.splitbill

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.SplitBillParticipant
import java.math.BigDecimal

/**
 * Real fix (2026-09-13, push-before-commit ordering sweep): the reminder loop used to
 * live entirely inside SplitBillService.sendDueReminders, one non-@Transactional method
 * with its own internal per-participant try/catch. Moved here (a different bean than
 * SplitBillService) so SplitBillService.sendReminderForParticipant's own @Transactional
 * boundary is a real, separately-proxied per-row transaction -- same shape
 * P2pDelayedTransferReminderScheduler already establishes.
 */
class SplitBillReminderSchedulerTest : BehaviorSpec({

    fun participant(id: String) = SplitBillParticipant(id = id, splitBillId = "splitbill_1", userId = "user_$id", shareAmount = BigDecimal("1000"))

    Given("two real due participants, one whose reminder throws") {
        val service = mockk<SplitBillService>()
        every { service.getParticipantsDueForReminder() } returns listOf(participant("p1"), participant("p2"))
        every { service.sendReminderForParticipant("p1") } throws IllegalStateException("database connection lost")
        every { service.sendReminderForParticipant("p2") } returns true
        val scheduler = SplitBillReminderScheduler(service)

        When("the scheduler runs its tick") {
            Then("the failing participant never blocks the other real due participant") {
                shouldNotThrowAny { scheduler.processDue() }
                verify(exactly = 1) { service.sendReminderForParticipant("p1") }
                verify(exactly = 1) { service.sendReminderForParticipant("p2") }
            }

            Then("it counts only the real successful sends") {
                val remindedCount = scheduler.processDue()
                remindedCount shouldBe 1
            }
        }
    }

    Given("getParticipantsDueForReminder itself fails unexpectedly") {
        val service = mockk<SplitBillService>()
        every { service.getParticipantsDueForReminder() } throws IllegalStateException("database connection lost")
        val scheduler = SplitBillReminderScheduler(service)

        When("the scheduler runs its tick") {
            Then("the failure is contained, not propagated to the @Scheduled thread") {
                shouldNotThrowAny { scheduler.run() }
            }
        }
    }

    Given("no participants are currently due") {
        val service = mockk<SplitBillService>()
        every { service.getParticipantsDueForReminder() } returns emptyList()
        val scheduler = SplitBillReminderScheduler(service)

        When("the scheduler runs its tick") {
            val remindedCount = scheduler.processDue()

            Then("it real-no-ops rather than calling the per-participant send at all") {
                remindedCount shouldBe 0
                verify(exactly = 0) { service.sendReminderForParticipant(any()) }
            }
        }
    }
})
