package rw.itunda.identity.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.domain.KycSubmission
import rw.itunda.core.security.CurrentUser
import rw.itunda.identity.IdentityService
import rw.itunda.identity.SubmissionAlreadyPendingException
import java.time.Instant

/**
 * First test coverage for IdentityController's REST layer -- same gap class the
 * Certificate/Vehicle/Partners passes already found and fixed once each. Also
 * covers the real RateLimitExceededException handler added in this same pass
 * (this controller had no handler for it at all before, unlike nearly every
 * other rate-limited controller in this codebase).
 */
class IdentityControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    Given("a real identity submission request") {
        val service = mockk<IdentityService>()
        val controller = IdentityController(service)
        val submission = mockk<KycSubmission>(relaxed = true)
        every { service.submit("user_1", "NATIONAL_ID", "1199012345678901", "ref-123") } returns submission

        When("submitting") {
            val response = controller.submit(SubmitIdentityRequest("NATIONAL_ID", "1199012345678901", "ref-123"), currentUser)

            Then("it real-delegates with the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { service.submit("user_1", "NATIONAL_ID", "1199012345678901", "ref-123") }
                response.statusCode shouldBe HttpStatus.CREATED
                response.body?.get("submission") shouldBe submission
            }
        }
    }

    Given("a real request for the caller's own submission status") {
        val service = mockk<IdentityService>()
        val controller = IdentityController(service)
        val submissions = listOf(KycSubmission(id = "kyc_1", userId = "user_1", documentType = "NATIONAL_ID", documentNumber = "x", documentReference = "y", status = "PENDING", submittedAt = Instant.now()))
        every { service.getMySubmissions("user_1") } returns submissions

        When("fetching it") {
            val response = controller.status(currentUser)

            Then("it queries only by the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { service.getMySubmissions("user_1") }
                response.body?.get("submissions") shouldBe submissions
            }
        }
    }

    listOf(
        Triple(SubmissionAlreadyPendingException("Conflict") as RuntimeException, HttpStatus.CONFLICT, "KYC_SUBMISSION_ALREADY_PENDING"),
        Triple(IllegalArgumentException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_IDENTITY_SUBMISSION"),
        Triple(RateLimitExceededException("Too many requests"), HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED"),
    ).forEach { (exception, expectedStatus, expectedCode) ->
        Given("a real ${exception::class.simpleName}") {
            val service = mockk<IdentityService>()
            val controller = IdentityController(service)

            When("its exception handler maps it to a real HTTP response") {
                val response = when (exception) {
                    is SubmissionAlreadyPendingException -> controller.handleAlreadyPending(exception)
                    is IllegalArgumentException -> controller.handleInvalidSubmission(exception)
                    is RateLimitExceededException -> controller.handleRateLimit(exception)
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
