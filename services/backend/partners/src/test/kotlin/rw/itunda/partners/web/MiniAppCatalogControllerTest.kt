package rw.itunda.partners.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import rw.itunda.core.domain.PartnerMiniAppCategory
import rw.itunda.core.domain.PartnerMiniApp
import rw.itunda.partners.InvalidMiniAppCategoryException
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
        every { service.getCatalog(any(), null) } returns page

        When("fetching it with no category filter") {
            val response = controller.catalog(PageRequest.of(0, 20))

            Then("it real-delegates and reports the real catalog") {
                verify(exactly = 1) { service.getCatalog(any(), null) }
                response.body?.get("miniApps") shouldBe listOf(miniApp)
            }
        }
    }

    Given("a real request for the catalog filtered to one category") {
        val service = mockk<PartnerService>()
        val controller = MiniAppCatalogController(service)
        val miniApp = mockk<PartnerMiniApp>(relaxed = true)
        val page = PageImpl(listOf(miniApp), PageRequest.of(0, 20), 1)
        every { service.getCatalog(any(), PartnerMiniAppCategory.FINANCE) } returns page

        When("fetching it with category=finance") {
            val response = controller.catalog(PageRequest.of(0, 20), "finance")

            Then("the parsed category is real-passed through") {
                verify(exactly = 1) { service.getCatalog(any(), PartnerMiniAppCategory.FINANCE) }
                response.body?.get("miniApps") shouldBe listOf(miniApp)
            }
        }
    }

    Given("a real InvalidMiniAppCategoryException from a garbage ?category= value") {
        val service = mockk<PartnerService>()
        val controller = MiniAppCatalogController(service)

        When("its exception handler maps it to a real HTTP response") {
            val response = controller.handleInvalidCategory(InvalidMiniAppCategoryException("Invalid category"))

            Then("it maps to 400 with code INVALID_MINI_APP_CATEGORY, not a generic 500") {
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
                response.body?.code shouldBe "INVALID_MINI_APP_CATEGORY"
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
