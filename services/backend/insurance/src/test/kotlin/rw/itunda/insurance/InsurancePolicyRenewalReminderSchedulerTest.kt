package rw.itunda.insurance

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.InsurancePolicy
import java.math.BigDecimal
import java.time.LocalDate

class InsurancePolicyRenewalReminderSchedulerTest : BehaviorSpec({

    fun policy(id: String) = InsurancePolicy(
        id = id, userId = "user_$id", planId = "plan_1", planName = "Basic Health", category = "health",
        status = "ACTIVE", startDate = LocalDate.now().minusMonths(11), endDate = LocalDate.now().plusDays(20),
        monthlyPremium = BigDecimal("5000"), nextPaymentDate = LocalDate.now().plusDays(5), policyNumber = "POL-$id",
    )

    Given("3 due policies, where sending the middle reminder fails") {
        val insuranceService = mockk<InsuranceService>()
        val p1 = policy("p1")
        val p2 = policy("p2")
        val p3 = policy("p3")
        every { insuranceService.getPoliciesDueForRenewalReminder() } returns listOf(p1, p2, p3)
        every { insuranceService.sendRenewalReminder(p1.id) } returns Unit
        every { insuranceService.sendRenewalReminder(p2.id) } throws RuntimeException("push provider down")
        every { insuranceService.sendRenewalReminder(p3.id) } returns Unit
        val scheduler = InsurancePolicyRenewalReminderScheduler(insuranceService)

        When("the sweep runs") {
            val sentCount = scheduler.processDue()

            Then("all 3 are still attempted -- the middle failure doesn't stop the rest") {
                verify(exactly = 1) { insuranceService.sendRenewalReminder(p1.id) }
                verify(exactly = 1) { insuranceService.sendRenewalReminder(p2.id) }
                verify(exactly = 1) { insuranceService.sendRenewalReminder(p3.id) }
            }

            Then("only the real successes are counted") {
                sentCount shouldBe 2
            }
        }
    }

    Given("no due policies") {
        val insuranceService = mockk<InsuranceService>()
        every { insuranceService.getPoliciesDueForRenewalReminder() } returns emptyList()
        val scheduler = InsurancePolicyRenewalReminderScheduler(insuranceService)

        When("the sweep runs") {
            val sentCount = scheduler.processDue()

            Then("nothing is sent, no exception is thrown") {
                sentCount shouldBe 0
            }
        }
    }
})
