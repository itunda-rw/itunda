package rw.itunda.ussd.web

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import rw.itunda.ussd.UssdService

/**
 * Real bug caught during this feature's own build-time review, before it ever shipped:
 * POST /api/v1/ussd/session is deliberately permitAll() (the real caller is a USSD
 * gateway server, not a logged-in itunda user), but permitAll() alone authenticates
 * nothing about the CALLER -- without a real gateway-level check, any internet client
 * could hit this endpoint claiming to be any phone number, turning the PIN-rate-limited
 * flows into an open account-enumeration/PIN-guessing oracle. This file exists to make
 * sure that fix can't silently regress. Directly instantiates the controller with a
 * mocked service, no Spring context -- see ContactsControllerTest.kt's own doc comment
 * for why that's a valid, sufficient way to exercise this exact code.
 */
class UssdControllerTest : BehaviorSpec({

    Given("a real configured gateway secret") {
        val ussdService = mockk<UssdService>()
        val controller = UssdController(ussdService, gatewaySecret = "real-secret-123")

        When("the request carries no secret header at all") {
            Then("it real-401s before ever reaching UssdService") {
                shouldThrow<UssdGatewayUnauthorizedException> {
                    controller.session(sessionId = "sess_1", phoneNumber = "+250788111222", text = "", providedSecret = null)
                }
            }
        }

        When("the request carries the wrong secret") {
            Then("it real-401s before ever reaching UssdService") {
                shouldThrow<UssdGatewayUnauthorizedException> {
                    controller.session(sessionId = "sess_1", phoneNumber = "+250788111222", text = "", providedSecret = "wrong-secret")
                }
            }
        }

        When("the request carries the real, correct secret") {
            every { ussdService.handleUssdRequest("sess_1", "+250788111222", "") } returns "CON Welcome to itunda"

            Then("it proceeds to UssdService normally") {
                val response = controller.session(sessionId = "sess_1", phoneNumber = "+250788111222", text = "", providedSecret = "real-secret-123")
                response.body shouldBe "CON Welcome to itunda"
            }
        }
    }

    Given("no gateway secret configured (the honest dev/CI default)") {
        val ussdService = mockk<UssdService>()
        val controller = UssdController(ussdService, gatewaySecret = "")
        every { ussdService.handleUssdRequest("sess_1", "+250788111222", "") } returns "CON Welcome to itunda"

        When("a request arrives with no secret header at all") {
            Then("it proceeds normally -- matching mtn-momo's own established empty-by-default convention") {
                val response = controller.session(sessionId = "sess_1", phoneNumber = "+250788111222", text = "", providedSecret = null)
                response.body shouldBe "CON Welcome to itunda"
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
