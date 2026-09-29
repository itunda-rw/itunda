package rw.itunda.system.web

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import rw.itunda.core.domain.LinkedAccountStatus
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.health.ProviderHealthTracker
import rw.itunda.core.incident.IncidentDetector
import rw.itunda.core.provider.MtnMomoSandboxClient
import rw.itunda.core.reconciliation.ReconciliationService
import rw.itunda.core.repository.LinkedAccountRepository
import rw.itunda.core.repository.TransactionRepository
import java.math.BigDecimal

class SystemControllerTest : BehaviorSpec({
    Given("the operations dashboard") {
        val transactions = mockk<TransactionRepository>()
        val linkedAccounts = mockk<LinkedAccountRepository>()
        val controller = SystemController(
            mockk<ProviderHealthTracker>(), mockk<IncidentDetector>(), mockk<ReconciliationService>(),
            mockk<MtnMomoSandboxClient>(), transactions, linkedAccounts,
        )
        every { transactions.sumAmountByStatusAndCompletedAtBetween(TransactionStatus.COMPLETED, any(), any()) } returns BigDecimal("12500")
        every { transactions.countByStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(TransactionStatus.COMPLETED, any(), any()) } returns 3
        every { linkedAccounts.countByStatus(LinkedAccountStatus.LINKED) } returns 2

        When("it is requested") {
            val response = controller.getSystemDashboard()
            val dashboard = response.body!!["dashboard"] as Map<*, *>

            Then("it returns real settled volume and active-consent counts") {
                response.statusCode.value() shouldBe 200
                (dashboard["operations"] as Map<*, *>)["todayVolume"] shouldBe BigDecimal("12500")
                (dashboard["operations"] as Map<*, *>)["todayCompletedTransactionCount"] shouldBe 3L
                (dashboard["operatingLayer"] as Map<*, *>)["activeConsents"] shouldBe 2L
            }
        }

        Then("legacy placeholder endpoints are explicit about their unavailable contracts") {
            controller.getProductCapabilities().statusCode.value() shouldBe 501
            controller.getParityMatrix().statusCode.value() shouldBe 501
        }
    }
})
