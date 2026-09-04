package rw.itunda.loans

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.VupLoan
import rw.itunda.core.domain.VupLoanPurpose
import rw.itunda.core.domain.VupLoanStatus
import java.math.BigDecimal

/**
 * First test coverage for VupLoanReminderScheduler -- and a real, previously-live bug
 * fix, not just a missing test. `sendDueReminder` had no try/catch of its own, and
 * this loop had none either. Fixed alongside this test (same commit) with a per-loan
 * try/catch, matching VupLoanOverdueScheduler's own sibling fix.
 */
class VupLoanReminderSchedulerTest : BehaviorSpec({

    fun loan(id: String) = VupLoan(
        id = id, userId = "user_$id", declaredUbudeheCategory = 2, purpose = VupLoanPurpose.FARMING,
        principalAmount = BigDecimal("50000"), outstandingPrincipal = BigDecimal("50000"), status = VupLoanStatus.DISBURSED,
    )

    Given("3 loans due soon for a reminder, where sending the middle one fails") {
        val vupLoanService = mockk<VupLoanService>()
        val l1 = loan("l1")
        val l2 = loan("l2")
        val l3 = loan("l3")
        every { vupLoanService.getLoansDueSoonForReminder() } returns listOf(l1, l2, l3)
        every { vupLoanService.sendDueReminder(l1) } returns Unit
        every { vupLoanService.sendDueReminder(l2) } throws RuntimeException("messaging service unreachable")
        every { vupLoanService.sendDueReminder(l3) } returns Unit
        val scheduler = VupLoanReminderScheduler(vupLoanService)

        When("the sweep runs") {
            scheduler.run()

            Then("all 3 are still attempted -- the middle failure doesn't stop the rest") {
                verify(exactly = 1) { vupLoanService.sendDueReminder(l1) }
                verify(exactly = 1) { vupLoanService.sendDueReminder(l2) }
                verify(exactly = 1) { vupLoanService.sendDueReminder(l3) }
            }
        }
    }

    Given("no loans due soon") {
        val vupLoanService = mockk<VupLoanService>()
        every { vupLoanService.getLoansDueSoonForReminder() } returns emptyList()
        val scheduler = VupLoanReminderScheduler(vupLoanService)

        When("the sweep runs") {
            scheduler.run()

            Then("nothing is sent, no exception is thrown") {
                verify(exactly = 0) { vupLoanService.sendDueReminder(any()) }
            }
        }
    }
})
