package rw.itunda.identity.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import rw.itunda.core.domain.KycSubmission
import rw.itunda.core.security.CurrentUser
import rw.itunda.identity.DuplicateDocumentNumberException
import rw.itunda.identity.IdentityService
import rw.itunda.identity.IdentityUserNotFoundException
import rw.itunda.identity.InvalidDecisionReasonException
import rw.itunda.identity.SubmissionNotFoundException
import rw.itunda.identity.SubmissionNotPendingException

/**
 * First test coverage for ComplianceController's REST layer -- the real
 * admin KYC/KYB review queue. Mapped under /api/v1/system/compliance so
 * SecurityConfig's existing hasRole("ADMIN") rule applies (not independently
 * testable at this plain-object unit-test tier -- same established limit
 * CertificateControllerTest's own doc comment already names).
 */
class ComplianceControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "admin_1")

    Given("a real pending-review queue") {
        val service = mockk<IdentityService>()
        val controller = ComplianceController(service)
        val submission = mockk<KycSubmission>(relaxed = true)
        val page = PageImpl(listOf(submission), PageRequest.of(0, 20), 1)
        every { service.getQueue(any()) } returns page

        When("fetching it") {
            val response = controller.queue(PageRequest.of(0, 20))

            Then("it real-delegates and reports the real queue") {
                verify(exactly = 1) { service.getQueue(any()) }
                response.body?.get("queue") shouldBe listOf(submission)
            }
        }
    }

    Given("an admin approving a real pending submission") {
        val service = mockk<IdentityService>()
        val controller = ComplianceController(service)
        val approved = mockk<KycSubmission>(relaxed = true)
        every { service.decide("kyc_1", "admin_1", true, null) } returns approved

        When("deciding") {
            val response = controller.decide("kyc_1", DecideSubmissionRequest(approve = true), currentUser)

            Then("it real-delegates with the real reviewer's own userId, never a client-supplied one") {
                verify(exactly = 1) { service.decide("kyc_1", "admin_1", true, null) }
                response.body?.get("submission") shouldBe approved
            }
        }
    }

    listOf(
        Triple(SubmissionNotFoundException("Not found") as RuntimeException, HttpStatus.NOT_FOUND, "KYC_SUBMISSION_NOT_FOUND"),
        Triple(SubmissionNotPendingException("Conflict"), HttpStatus.CONFLICT, "KYC_SUBMISSION_NOT_PENDING"),
        Triple(IdentityUserNotFoundException("Not found"), HttpStatus.NOT_FOUND, "USER_NOT_FOUND"),
        Triple(InvalidDecisionReasonException("Invalid decision reason"), HttpStatus.BAD_REQUEST, "INVALID_DECISION_REASON"),
        Triple(DuplicateDocumentNumberException("Conflict"), HttpStatus.CONFLICT, "DUPLICATE_DOCUMENT_NUMBER"),
    ).forEach { (exception, expectedStatus, expectedCode) ->
        Given("a real ${exception::class.simpleName}") {
            val service = mockk<IdentityService>()
            val controller = ComplianceController(service)

            When("its exception handler maps it to a real HTTP response") {
                val response = when (exception) {
                    is SubmissionNotFoundException -> controller.handleNotFound(exception)
                    is SubmissionNotPendingException -> controller.handleNotPending(exception)
                    is IdentityUserNotFoundException -> controller.handleUserNotFound(exception)
                    is InvalidDecisionReasonException -> controller.handleInvalidDecisionReason(exception)
                    is DuplicateDocumentNumberException -> controller.handleDuplicateDocumentNumber(exception)
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
