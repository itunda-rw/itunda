package rw.itunda.loans

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.OverdraftAccount
import java.math.BigDecimal

/**
 * First test coverage for OverdraftInterestAccrualScheduler -- real daily interest
 * accrual on a real drawn overdraft balance. Same real "one bad row can't poison the
 * sweep" resilience contract every other scheduler test in this sweep verifies.
 */
class OverdraftInterestAccrualSchedulerTest : BehaviorSpec({

    fun account(id: String) = OverdraftAccount(
        id = id, userId = "user_$id", accountId = "account_$id", creditLimit = BigDecimal("100000"),
        interestRate = 8.0, drawnBalance = BigDecimal("10000"),
    )

    Given("3 overdraft accounts due for accrual, where accruing the middle one fails") {
        val overdraftService = mockk<OverdraftService>()
        val a1 = account("a1")
        val a2 = account("a2")
        val a3 = account("a3")
        every { overdraftService.getAccountsDueForAccrual() } returns listOf(a1, a2, a3)
        every { overdraftService.accrueInterest(a1) } returns Unit
        every { overdraftService.accrueInterest(a2) } throws RuntimeException("unexpected ledger error")
        every { overdraftService.accrueInterest(a3) } returns Unit
        val scheduler = OverdraftInterestAccrualScheduler(overdraftService)

        When("the sweep runs") {
            scheduler.run()

            Then("all 3 are still attempted -- the middle failure doesn't stop accrual for the rest") {
                verify(exactly = 1) { overdraftService.accrueInterest(a1) }
                verify(exactly = 1) { overdraftService.accrueInterest(a2) }
                verify(exactly = 1) { overdraftService.accrueInterest(a3) }
            }
        }
    }

    Given("no overdraft accounts due for accrual") {
        val overdraftService = mockk<OverdraftService>()
        every { overdraftService.getAccountsDueForAccrual() } returns emptyList()
        val scheduler = OverdraftInterestAccrualScheduler(overdraftService)

        When("the sweep runs") {
            scheduler.run()

            Then("nothing is accrued, no exception is thrown") {
                verify(exactly = 0) { overdraftService.accrueInterest(any()) }
            }
        }
    }
})
