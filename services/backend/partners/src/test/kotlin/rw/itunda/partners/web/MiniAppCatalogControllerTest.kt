package rw.itunda.partners.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import rw.itunda.core.domain.PartnerMiniApp
import rw.itunda.partners.PartnerService

/**
 * First test coverage for MiniAppCatalogController's REST layer -- the real
 * published app-store surface every itunda client fetches, requiring only the
 * default itunda-user JWT (not partner-authenticated, not ADMIN-gated).
 */
class MiniAppCatalogControllerTest : BehaviorSpec({

    Given("a real published catalog of approved mini-apps") {
        val service = mockk<PartnerService>()
        val controller = MiniAppCatalogController(service)
        val miniApp = mockk<PartnerMiniApp>(relaxed = true)
        val page = PageImpl(listOf(miniApp), PageRequest.of(0, 20), 1)
        every { service.getCatalog(any()) } returns page

        When("fetching it") {
            val response = controller.catalog(PageRequest.of(0, 20))

            Then("it real-delegates and reports the real catalog") {
                verify(exactly = 1) { service.getCatalog(any()) }
                response.body?.get("miniApps") shouldBe listOf(miniApp)
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
