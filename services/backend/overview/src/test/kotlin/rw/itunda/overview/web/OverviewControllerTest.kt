package rw.itunda.overview.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.security.CurrentUser
import rw.itunda.overview.OverviewResult
import rw.itunda.overview.OverviewService
import java.math.BigDecimal

/**
 * First test coverage for OverviewController's REST layer -- same gap class the
 * Certificate/Vehicle/Partners/Identity passes already found and fixed once each.
 * A single-endpoint controller with no exception handlers of its own; covers the
 * controller-to-service delegation and the real caller-scoped userId.
 */
class OverviewControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    Given("a real request for the caller's own overview") {
        val service = mockk<OverviewService>()
        val controller = OverviewController(service)
        val result = mockk<OverviewResult>(relaxed = true)
        every { result.netWorth } returns BigDecimal("1000000")
        every { service.getOverview("user_1") } returns result

        When("fetching it") {
            val response = controller.getOverview(currentUser)

            Then("it real-delegates and queries only by the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { service.getOverview("user_1") }
                response.body?.get("netWorth") shouldBe BigDecimal("1000000")
                response.body?.get("success") shouldBe true
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
