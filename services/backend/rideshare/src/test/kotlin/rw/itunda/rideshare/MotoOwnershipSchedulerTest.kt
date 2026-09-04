package rw.itunda.rideshare

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.MotoOwnershipPlan
import rw.itunda.core.domain.MotoOwnershipPlanStatus
import java.math.BigDecimal

/**
 * First test coverage for MotoOwnershipScheduler -- and a real, previously-live bug
 * fix, not just a missing test. `autoContribute`'s own `Boolean` return only covers
 * the deliberate insufficient-funds case -- it calls
 * `ledgerService.postLedgerTransaction` with no try/catch of its own, so a genuinely
 * unexpected failure throws uncaught. This loop had no try/catch either. Fixed
 * alongside this test (same commit) with a per-plan try/catch.
 */
class MotoOwnershipSchedulerTest : BehaviorSpec({

    fun plan(id: String) = MotoOwnershipPlan(
        id = id, userId = "user_$id", bikePrice = BigDecimal("600000"),
        downPaymentTarget = BigDecimal("180000"), savedAmount = BigDecimal("50000"),
        dailyContribution = BigDecimal("5000"), loanOutstanding = BigDecimal.ZERO,
        status = MotoOwnershipPlanStatus.SAVING,
    )

    Given("3 plans due for auto-contribution, where the middle one fails") {
        val motoOwnershipService = mockk<MotoOwnershipService>()
        val p1 = plan("p1")
        val p2 = plan("p2")
        val p3 = plan("p3")
        every { motoOwnershipService.getPlansDueForAutoContribution() } returns listOf(p1, p2, p3)
        every { motoOwnershipService.autoContribute(p1) } returns true
        every { motoOwnershipService.autoContribute(p2) } throws RuntimeException("unexpected ledger error")
        every { motoOwnershipService.autoContribute(p3) } returns true
        val scheduler = MotoOwnershipScheduler(motoOwnershipService)

        When("the sweep runs") {
            scheduler.run()

            Then("all 3 are still attempted -- the middle failure doesn't stop the rest") {
                verify(exactly = 1) { motoOwnershipService.autoContribute(p1) }
                verify(exactly = 1) { motoOwnershipService.autoContribute(p2) }
                verify(exactly = 1) { motoOwnershipService.autoContribute(p3) }
            }
        }
    }

    Given("no plans due for auto-contribution") {
        val motoOwnershipService = mockk<MotoOwnershipService>()
        every { motoOwnershipService.getPlansDueForAutoContribution() } returns emptyList()
        val scheduler = MotoOwnershipScheduler(motoOwnershipService)

        When("the sweep runs") {
            scheduler.run()

            Then("nothing is contributed, no exception is thrown") {
                verify(exactly = 0) { motoOwnershipService.autoContribute(any()) }
            }
        }
    }
})
