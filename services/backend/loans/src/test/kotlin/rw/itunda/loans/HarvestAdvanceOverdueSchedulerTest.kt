package rw.itunda.loans

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.HarvestAdvance
import rw.itunda.core.domain.HarvestAdvanceStatus
import java.math.BigDecimal
import java.time.Instant

/**
 * First test coverage for HarvestAdvanceOverdueScheduler -- mirrors
 * VupLoanOverdueSchedulerTest exactly, including the per-item try/catch it proved
 * out (Bank product-completeness pass, cycle 2, 2026-09-08).
 */
class HarvestAdvanceOverdueSchedulerTest : BehaviorSpec({

    fun advance(id: String) = HarvestAdvance(
        id = id, membershipId = "coopmem_$id", accountId = "account_$id", principalAmount = BigDecimal("50000"),
        purpose = "INPUT_FINANCING", expectedHarvestDate = Instant.now(), repaymentDueDate = Instant.now().minusSeconds(3600),
        status = HarvestAdvanceStatus.DISBURSED,
    )

    Given("3 overdue harvest advances, where flagging the middle one fails") {
        val cooperativeService = mockk<CooperativeService>()
        val a1 = advance("a1")
        val a2 = advance("a2")
        val a3 = advance("a3")
        every { cooperativeService.getAdvancesDueForOverdueCheck() } returns listOf(a1, a2, a3)
        every { cooperativeService.markOverdue(a1) } returns Unit
        every { cooperativeService.markOverdue(a2) } throws RuntimeException("unexpected database error")
        every { cooperativeService.markOverdue(a3) } returns Unit
        val scheduler = HarvestAdvanceOverdueScheduler(cooperativeService)

        When("the sweep runs") {
            scheduler.run()

            Then("all 3 are still attempted -- the middle failure doesn't stop flagging the rest") {
                verify(exactly = 1) { cooperativeService.markOverdue(a1) }
                verify(exactly = 1) { cooperativeService.markOverdue(a2) }
                verify(exactly = 1) { cooperativeService.markOverdue(a3) }
            }
        }
    }

    Given("no overdue harvest advances") {
        val cooperativeService = mockk<CooperativeService>()
        every { cooperativeService.getAdvancesDueForOverdueCheck() } returns emptyList()
        val scheduler = HarvestAdvanceOverdueScheduler(cooperativeService)

        When("the sweep runs") {
            scheduler.run()

            Then("nothing is flagged, no exception is thrown") {
                verify(exactly = 0) { cooperativeService.markOverdue(any()) }
            }
        }
    }
})
