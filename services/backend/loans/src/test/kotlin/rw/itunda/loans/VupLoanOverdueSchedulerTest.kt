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
 * First test coverage for VupLoanOverdueScheduler -- and a real, previously-live bug
 * fix, not just a missing test. `markOverdue` had no try/catch of its own around its
 * repository save/notification calls, and this loop had none either. Lower real-world
 * likelihood than a ledger-touching scheduler (no money moves here), but still a real
 * instance of the same gap. Fixed alongside this test (same commit) with a per-loan
 * try/catch.
 */
class VupLoanOverdueSchedulerTest : BehaviorSpec({

    fun loan(id: String) = VupLoan(
        id = id, userId = "user_$id", declaredUbudeheCategory = 2, purpose = VupLoanPurpose.FARMING,
        principalAmount = BigDecimal("50000"), outstandingPrincipal = BigDecimal("50000"), status = VupLoanStatus.DISBURSED,
    )

    Given("3 overdue VUP loans, where flagging the middle one fails") {
        val vupLoanService = mockk<VupLoanService>()
        val l1 = loan("l1")
        val l2 = loan("l2")
        val l3 = loan("l3")
        every { vupLoanService.getLoansDueForOverdueCheck() } returns listOf(l1, l2, l3)
        every { vupLoanService.markOverdue(l1) } returns Unit
        every { vupLoanService.markOverdue(l2) } throws RuntimeException("unexpected database error")
        every { vupLoanService.markOverdue(l3) } returns Unit
        val scheduler = VupLoanOverdueScheduler(vupLoanService)

        When("the sweep runs") {
            scheduler.run()

            Then("all 3 are still attempted -- the middle failure doesn't stop flagging the rest") {
                verify(exactly = 1) { vupLoanService.markOverdue(l1) }
                verify(exactly = 1) { vupLoanService.markOverdue(l2) }
                verify(exactly = 1) { vupLoanService.markOverdue(l3) }
            }
        }
    }

    Given("no overdue VUP loans") {
        val vupLoanService = mockk<VupLoanService>()
        every { vupLoanService.getLoansDueForOverdueCheck() } returns emptyList()
        val scheduler = VupLoanOverdueScheduler(vupLoanService)

        When("the sweep runs") {
            scheduler.run()

            Then("nothing is flagged, no exception is thrown") {
                verify(exactly = 0) { vupLoanService.markOverdue(any()) }
            }
        }
    }
})
