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
import rw.itunda.core.domain.PartnerMiniApp
import rw.itunda.core.security.CurrentUser
import rw.itunda.partners.PartnerMiniAppNotFoundException
import rw.itunda.partners.PartnerMiniAppNotPendingException
import rw.itunda.partners.PartnerService

/**
 * First test coverage for PartnerAdminController's REST layer. Mapped under
 * /api/v1/system/partners so SecurityConfig's existing hasRole("ADMIN") rule
 * applies (not independently testable at this plain-object unit-test tier --
 * same established limit CertificateControllerTest's own doc comment names).
 */
class PartnerAdminControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "admin_1")

    Given("a real pending-review queue") {
        val service = mockk<PartnerService>()
        val controller = PartnerAdminController(service)
        val miniApp = mockk<PartnerMiniApp>(relaxed = true)
        val page = PageImpl(listOf(miniApp), PageRequest.of(0, 20), 1)
        every { service.getQueue(any()) } returns page

        When("fetching it") {
            val response = controller.queue(PageRequest.of(0, 20))

            Then("it real-delegates and reports the real queue") {
                verify(exactly = 1) { service.getQueue(any()) }
                response.body?.get("queue") shouldBe listOf(miniApp)
            }
        }
    }

    Given("an admin approving a real pending submission") {
        val service = mockk<PartnerService>()
        val controller = PartnerAdminController(service)
        val approved = mockk<PartnerMiniApp>(relaxed = true)
        every { service.decide("app_1", "admin_1", true, null) } returns approved

        When("deciding") {
            val response = controller.decide("app_1", DecidePartnerMiniAppRequest(approve = true), currentUser)

            Then("it real-delegates with the real reviewer's own userId, never a client-supplied one") {
                verify(exactly = 1) { service.decide("app_1", "admin_1", true, null) }
                response.body?.get("miniApp") shouldBe approved
            }
        }
    }

    listOf(
        Pair(PartnerMiniAppNotFoundException("Not found") as RuntimeException, HttpStatus.NOT_FOUND to "PARTNER_MINI_APP_NOT_FOUND"),
        Pair(PartnerMiniAppNotPendingException("Conflict"), HttpStatus.CONFLICT to "PARTNER_MINI_APP_NOT_PENDING"),
    ).forEach { (exception, expected) ->
        val (expectedStatus, expectedCode) = expected
        Given("a real ${exception::class.simpleName}") {
            val service = mockk<PartnerService>()
            val controller = PartnerAdminController(service)

            When("its exception handler maps it to a real HTTP response") {
                val response = when (exception) {
                    is PartnerMiniAppNotFoundException -> controller.handleNotFound(exception)
                    is PartnerMiniAppNotPendingException -> controller.handleNotPending(exception)
                    else -> error("unexpected exception type")
                }

                Then("it maps to $expectedStatus with code $expectedCode, not a generic 500") {
                    response.statusCode shouldBe expectedStatus
                    response.body?.code shouldBe expectedCode
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
