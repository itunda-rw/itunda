package rw.itunda.loans

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.PostpaidCreditLine
import java.math.BigDecimal

/**
 * First test coverage for PostpaidCreditAccrualScheduler -- real late-fee accrual on
 * an overdue postpaid credit line. Same real "one bad row can't poison the sweep"
 * resilience contract every other scheduler test in this sweep verifies.
 */
class PostpaidCreditAccrualSchedulerTest : BehaviorSpec({

    fun line(id: String) = PostpaidCreditLine(
        id = id, userId = "user_$id", accountId = "account_$id", creditLimit = BigDecimal("100000"),
    )

    Given("3 postpaid credit lines overdue for a late fee, where the middle one fails") {
        val postpaidCreditService = mockk<PostpaidCreditService>()
        val l1 = line("l1")
        val l2 = line("l2")
        val l3 = line("l3")
        every { postpaidCreditService.getLinesOverdueForLateFee() } returns listOf(l1, l2, l3)
        every { postpaidCreditService.accrueLateFee(l1) } returns Unit
        every { postpaidCreditService.accrueLateFee(l2) } throws RuntimeException("unexpected ledger error")
        every { postpaidCreditService.accrueLateFee(l3) } returns Unit
        val scheduler = PostpaidCreditAccrualScheduler(postpaidCreditService)

        When("the sweep runs") {
            scheduler.run()

            Then("all 3 are still attempted -- the middle failure doesn't stop accrual for the rest") {
                verify(exactly = 1) { postpaidCreditService.accrueLateFee(l1) }
                verify(exactly = 1) { postpaidCreditService.accrueLateFee(l2) }
                verify(exactly = 1) { postpaidCreditService.accrueLateFee(l3) }
            }
        }
    }

    Given("no postpaid credit lines overdue") {
        val postpaidCreditService = mockk<PostpaidCreditService>()
        every { postpaidCreditService.getLinesOverdueForLateFee() } returns emptyList()
        val scheduler = PostpaidCreditAccrualScheduler(postpaidCreditService)

        When("the sweep runs") {
            scheduler.run()

            Then("nothing is accrued, no exception is thrown") {
                verify(exactly = 0) { postpaidCreditService.accrueLateFee(any()) }
            }
        }
    }
})
