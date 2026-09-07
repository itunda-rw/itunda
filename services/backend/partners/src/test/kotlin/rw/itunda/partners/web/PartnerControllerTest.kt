package rw.itunda.partners.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.domain.Partner
import rw.itunda.core.domain.PartnerMiniApp
import rw.itunda.partners.InvalidApiKeyException
import rw.itunda.partners.InvalidMiniAppDecisionReasonException
import rw.itunda.partners.InvalidMiniAppSubmissionException
import rw.itunda.partners.InvalidPartnerEmailException
import rw.itunda.partners.InvalidPermissionScopeException
import rw.itunda.partners.PartnerEmailAlreadyRegisteredException
import rw.itunda.partners.PartnerService
import rw.itunda.partners.PartnerSuspendedException

/**
 * First test coverage for PartnerController's REST layer -- same gap class the
 * Certificate and Vehicle passes already found and fixed once each. Covers
 * controller-to-service delegation and every real @ExceptionHandler mapping.
 */
class PartnerControllerTest : BehaviorSpec({

    Given("a first-time partner registration request") {
        val service = mockk<PartnerService>()
        val controller = PartnerController(service)
        val partner = mockk<Partner>(relaxed = true)
        every { service.register("Acme Ltd", "dev@acme.rw") } returns (partner to "sk_test_rawkey")

        When("registering") {
            val response = controller.register(RegisterPartnerRequest("Acme Ltd", "dev@acme.rw"))

            Then("it real-delegates and shows the real raw API key exactly once") {
                verify(exactly = 1) { service.register("Acme Ltd", "dev@acme.rw") }
                response.statusCode shouldBe HttpStatus.CREATED
                response.body?.get("apiKey") shouldBe "sk_test_rawkey"
            }
        }
    }

    Given("a real mini-app submission request") {
        val service = mockk<PartnerService>()
        val controller = PartnerController(service)
        val miniApp = mockk<PartnerMiniApp>(relaxed = true)
        every {
            service.submitMiniApp("sk_test_key", "My App", "Does things", null, "https://example.com/bundle.js", listOf("account:read"))
        } returns miniApp

        When("submitting") {
            val response = controller.submitMiniApp(
                SubmitMiniAppRequest("My App", "Does things", null, "https://example.com/bundle.js", listOf("account:read")),
                "sk_test_key",
            )

            Then("it real-delegates with the caller's own API key") {
                verify(exactly = 1) {
                    service.submitMiniApp("sk_test_key", "My App", "Does things", null, "https://example.com/bundle.js", listOf("account:read"))
                }
                response.statusCode shouldBe HttpStatus.CREATED
                response.body?.get("miniApp") shouldBe miniApp
            }
        }
    }

    Given("a real request for a partner's own mini-apps") {
        val service = mockk<PartnerService>()
        val controller = PartnerController(service)
        val miniApps = listOf(mockk<PartnerMiniApp>(relaxed = true))
        every { service.getMyMiniApps("sk_test_key") } returns miniApps

        When("listing them") {
            val response = controller.getMyMiniApps("sk_test_key")

            Then("it queries only by the caller's own API key") {
                verify(exactly = 1) { service.getMyMiniApps("sk_test_key") }
                response.body?.get("miniApps") shouldBe miniApps
            }
        }
    }

    Given("a request for the allowed permission scopes") {
        val service = mockk<PartnerService>()
        val controller = PartnerController(service)

        When("fetching them") {
            val response = controller.getAllowedPermissions()

            Then("it reports the real, static allow-list") {
                response.body?.get("permissions") shouldBe rw.itunda.partners.PartnerMiniAppPermissions.ALLOWED
            }
        }
    }

    listOf(
        Triple(PartnerEmailAlreadyRegisteredException("Conflict") as RuntimeException, HttpStatus.CONFLICT, "PARTNER_EMAIL_ALREADY_REGISTERED"),
        Triple(InvalidPartnerEmailException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_EMAIL"),
        Triple(InvalidApiKeyException("Unauthorized"), HttpStatus.UNAUTHORIZED, "INVALID_API_KEY"),
        Triple(PartnerSuspendedException("Forbidden"), HttpStatus.FORBIDDEN, "PARTNER_SUSPENDED"),
        Triple(InvalidPermissionScopeException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_PERMISSION_SCOPE"),
        Triple(InvalidMiniAppSubmissionException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_MINI_APP_SUBMISSION"),
        Triple(RateLimitExceededException("Too many requests"), HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED"),
        Triple(InvalidMiniAppDecisionReasonException("Invalid decision reason"), HttpStatus.BAD_REQUEST, "INVALID_DECISION_REASON"),
    ).forEach { (exception, expectedStatus, expectedCode) ->
        Given("a real ${exception::class.simpleName}") {
            val service = mockk<PartnerService>()
            val controller = PartnerController(service)

            When("its exception handler maps it to a real HTTP response") {
                val response = when (exception) {
                    is PartnerEmailAlreadyRegisteredException -> controller.handleAlreadyRegistered(exception)
                    is InvalidPartnerEmailException -> controller.handleInvalidEmail(exception)
                    is InvalidApiKeyException -> controller.handleInvalidApiKey(exception)
                    is PartnerSuspendedException -> controller.handleSuspended(exception)
                    is InvalidPermissionScopeException -> controller.handleInvalidScope(exception)
                    is InvalidMiniAppSubmissionException -> controller.handleInvalidSubmission(exception)
                    is RateLimitExceededException -> controller.handleRateLimit(exception)
                    is InvalidMiniAppDecisionReasonException -> controller.handleInvalidDecisionReason(exception)
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
