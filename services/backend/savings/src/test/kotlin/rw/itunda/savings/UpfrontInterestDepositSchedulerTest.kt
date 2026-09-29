package rw.itunda.savings

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.UpfrontInterestDeposit
import java.math.BigDecimal
import java.time.Instant

/**
 * First test coverage for UpfrontInterestDepositScheduler -- and a real, previously-
 * live bug fix, not just a missing test. `matureDeposit` had no try/catch of its own
 * around its repository save, and this loop had none either. Lower real-world
 * likelihood than a ledger-touching scheduler (no money moves here, only a status
 * flip), but still a real instance of the same "one bad row can't poison the sweep"
 * gap. Fixed alongside this test (same commit) with a per-deposit try/catch.
 */
class UpfrontInterestDepositSchedulerTest : BehaviorSpec({

    fun deposit(id: String) = UpfrontInterestDeposit(
        id = id, userId = "user_$id", accountId = "account_$id", principal = BigDecimal("500000"),
        interestRate = 6.0, interestPaid = BigDecimal("30000"), maturesAt = Instant.now(),
    )

    Given("3 deposits due for maturity, where maturing the middle one fails") {
        val upfrontInterestDepositService = mockk<UpfrontInterestDepositService>()
        val d1 = deposit("d1")
        val d2 = deposit("d2")
        val d3 = deposit("d3")
        every { upfrontInterestDepositService.getDepositsDueForMaturity() } returns listOf(d1, d2, d3)
        every { upfrontInterestDepositService.matureDeposit(d1) } returns Unit
        every { upfrontInterestDepositService.matureDeposit(d2) } throws RuntimeException("unexpected database error")
        every { upfrontInterestDepositService.matureDeposit(d3) } returns Unit
        val scheduler = UpfrontInterestDepositScheduler(upfrontInterestDepositService)

        When("the sweep runs") {
            scheduler.run()

            Then("all 3 are still attempted -- the middle failure doesn't stop maturity for the rest") {
                verify(exactly = 1) { upfrontInterestDepositService.matureDeposit(d1) }
                verify(exactly = 1) { upfrontInterestDepositService.matureDeposit(d2) }
                verify(exactly = 1) { upfrontInterestDepositService.matureDeposit(d3) }
            }
        }
    }

    Given("no deposits due for maturity") {
        val upfrontInterestDepositService = mockk<UpfrontInterestDepositService>()
        every { upfrontInterestDepositService.getDepositsDueForMaturity() } returns emptyList()
        val scheduler = UpfrontInterestDepositScheduler(upfrontInterestDepositService)

        When("the sweep runs") {
            scheduler.run()

            Then("nothing is matured, no exception is thrown") {
                verify(exactly = 0) { upfrontInterestDepositService.matureDeposit(any()) }
            }
        }
    }
})
