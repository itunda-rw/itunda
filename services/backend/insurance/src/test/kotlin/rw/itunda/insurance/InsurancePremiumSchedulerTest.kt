package rw.itunda.insurance

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.InsurancePolicy
import rw.itunda.core.domain.InsurancePremiumFund
import java.math.BigDecimal
import java.time.LocalDate

/**
 * First test coverage for InsurancePremiumScheduler -- and a real, previously-live
 * bug fix, not just a missing test. Both `collectPremium` and `autoContributeToFund`
 * call `ledgerService.postLedgerTransaction` with no try/catch of their own, and
 * neither loop here had one either -- a single bad policy/fund would throw uncaught
 * and silently stop the rest of that loop (and, for the first loop, the second loop
 * would never even run). Fixed alongside this test (same commit) with per-item
 * try/catch in both loops.
 */
class InsurancePremiumSchedulerTest : BehaviorSpec({

    fun policy(id: String) = InsurancePolicy(
        id = id, userId = "user_$id", planId = "ins_1", planName = "Health Shield", category = "health",
        status = "active", startDate = LocalDate.now(), endDate = LocalDate.now().plusYears(1),
        monthlyPremium = BigDecimal("15000"), nextPaymentDate = LocalDate.now(), policyNumber = "POL-$id",
    )

    fun fund(id: String) = InsurancePremiumFund(
        id = id, userId = "user_$id", policyId = "ins_$id", targetAmount = BigDecimal("50000"),
        currentAmount = BigDecimal("10000"), dailyContribution = BigDecimal("1000"),
    )

    Given("3 policies due for premium collection, where the middle one fails") {
        val insuranceService = mockk<InsuranceService>()
        val p1 = policy("p1")
        val p2 = policy("p2")
        val p3 = policy("p3")
        every { insuranceService.getPoliciesDueForPremiumCollection() } returns listOf(p1, p2, p3)
        every { insuranceService.collectPremium(p1) } returns true
        every { insuranceService.collectPremium(p2) } throws RuntimeException("unexpected ledger error")
        every { insuranceService.collectPremium(p3) } returns true
        every { insuranceService.getFundsDueForAutoContribution() } returns emptyList()
        val scheduler = InsurancePremiumScheduler(insuranceService)

        When("the sweep runs") {
            scheduler.run()

            Then("all 3 premium collections are still attempted -- the middle failure doesn't stop the rest") {
                verify(exactly = 1) { insuranceService.collectPremium(p1) }
                verify(exactly = 1) { insuranceService.collectPremium(p2) }
                verify(exactly = 1) { insuranceService.collectPremium(p3) }
            }
        }
    }

    Given("no policies due, but 3 funds due for auto-contribution, where the middle one fails") {
        val insuranceService = mockk<InsuranceService>()
        val f1 = fund("f1")
        val f2 = fund("f2")
        val f3 = fund("f3")
        every { insuranceService.getPoliciesDueForPremiumCollection() } returns emptyList()
        every { insuranceService.getFundsDueForAutoContribution() } returns listOf(f1, f2, f3)
        every { insuranceService.autoContributeToFund(f1) } returns true
        every { insuranceService.autoContributeToFund(f2) } throws RuntimeException("unexpected ledger error")
        every { insuranceService.autoContributeToFund(f3) } returns true
        val scheduler = InsurancePremiumScheduler(insuranceService)

        When("the sweep runs") {
            scheduler.run()

            Then("all 3 fund contributions are still attempted -- the middle failure doesn't stop the rest") {
                verify(exactly = 1) { insuranceService.autoContributeToFund(f1) }
                verify(exactly = 1) { insuranceService.autoContributeToFund(f2) }
                verify(exactly = 1) { insuranceService.autoContributeToFund(f3) }
            }
        }
    }

    Given("nothing due at all") {
        val insuranceService = mockk<InsuranceService>()
        every { insuranceService.getPoliciesDueForPremiumCollection() } returns emptyList()
        every { insuranceService.getFundsDueForAutoContribution() } returns emptyList()
        val scheduler = InsurancePremiumScheduler(insuranceService)

        When("the sweep runs") {
            scheduler.run()

            Then("nothing happens, no exception is thrown") {
                verify(exactly = 0) { insuranceService.collectPremium(any()) }
                verify(exactly = 0) { insuranceService.autoContributeToFund(any()) }
            }
        }
    }
})
