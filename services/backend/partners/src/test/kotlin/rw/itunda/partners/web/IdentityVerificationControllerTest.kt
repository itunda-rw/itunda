package rw.itunda.partners.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.core.domain.IdentityVerificationRequest
import rw.itunda.core.domain.IdentityVerificationStatus
import rw.itunda.core.security.CurrentUser
import rw.itunda.partners.IdentityVerificationRequestNotFoundException
import rw.itunda.partners.IdentityVerificationRequestNotPendingException
import rw.itunda.partners.IdentityVerificationService
import rw.itunda.partners.IdentityVerificationUserNotFoundException
import java.time.Instant

/**
 * First test coverage for IdentityVerificationController's REST layer -- the
 * itunda-user-facing "verify with itunda" consent side, reached via the
 * itunda://verify/{requestId} deep link a partner's own site shows.
 */
class IdentityVerificationControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    Given("a real pending verification request") {
        val service = mockk<IdentityVerificationService>()
        val controller = IdentityVerificationController(service)
        val request = IdentityVerificationRequest(
            id = "idverify_1", partnerId = "partner_1", status = IdentityVerificationStatus.PENDING,
            expiresAt = Instant.now().plusSeconds(300),
        )
        every { service.getForUser("idverify_1") } returns (request to "Acme Ltd")

        When("fetching it for the real consent screen") {
            val response = controller.getRequest("idverify_1")

            Then("it real-delegates and reports the real partner name, never a client-supplied one") {
                verify(exactly = 1) { service.getForUser("idverify_1") }
                response.body?.get("partnerName") shouldBe "Acme Ltd"
                response.body?.get("status") shouldBe IdentityVerificationStatus.PENDING
            }
        }
    }

    Given("a real user approving a real pending request") {
        val service = mockk<IdentityVerificationService>()
        val controller = IdentityVerificationController(service)
        val approved = mockk<IdentityVerificationRequest>(relaxed = true)
        every { service.approve("idverify_1", "user_1") } returns approved

        When("approving") {
            val response = controller.approve("idverify_1", currentUser)

            Then("it real-delegates with the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { service.approve("idverify_1", "user_1") }
                response.body?.get("success") shouldBe true
            }
        }
    }

    Given("a real user declining a real pending request") {
        val service = mockk<IdentityVerificationService>()
        val controller = IdentityVerificationController(service)
        val declined = mockk<IdentityVerificationRequest>(relaxed = true)
        every { service.decline("idverify_1", "user_1") } returns declined

        When("declining") {
            val response = controller.decline("idverify_1", currentUser)

            Then("it real-delegates with the caller's own userId") {
                verify(exactly = 1) { service.decline("idverify_1", "user_1") }
                response.body?.get("success") shouldBe true
            }
        }
    }

    listOf(
        Triple(IdentityVerificationRequestNotFoundException("Not found") as RuntimeException, HttpStatus.NOT_FOUND, "VERIFICATION_REQUEST_NOT_FOUND"),
        Triple(IdentityVerificationRequestNotPendingException("Conflict"), HttpStatus.CONFLICT, "VERIFICATION_REQUEST_NOT_PENDING"),
        Triple(IdentityVerificationUserNotFoundException("Not found"), HttpStatus.NOT_FOUND, "USER_NOT_FOUND"),
    ).forEach { (exception, expectedStatus, expectedCode) ->
        Given("a real ${exception::class.simpleName}") {
            val service = mockk<IdentityVerificationService>()
            val controller = IdentityVerificationController(service)

            When("its exception handler maps it to a real HTTP response") {
                val response = when (exception) {
                    is IdentityVerificationRequestNotFoundException -> controller.handleNotFound(exception)
                    is IdentityVerificationRequestNotPendingException -> controller.handleNotPending(exception)
                    is IdentityVerificationUserNotFoundException -> controller.handleUserNotFound(exception)
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
