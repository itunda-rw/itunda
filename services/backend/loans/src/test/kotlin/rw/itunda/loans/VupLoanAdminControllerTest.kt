package rw.itunda.loans

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import rw.itunda.core.domain.VupLoan
import rw.itunda.core.security.CurrentUser

/**
 * First test coverage for VupLoanAdminController -- the real backing for
 * LoanDefaultQueue.tsx (ops-mfe), despite every sibling controller in this module
 * already having one.
 */
class VupLoanAdminControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "admin_1")

    Given("a real overdue-VUP-loan review queue") {
        val service = mockk<VupLoanService>()
        val controller = VupLoanAdminController(service)
        val pageable = PageRequest.of(0, 20)
        val page = PageImpl(listOf(mockk<VupLoan>(relaxed = true)))
        every { service.getDefaultReviewQueue(pageable) } returns page

        When("fetching it") {
            val response = controller.queue(pageable)

            Then("it delegates and returns the real queue page") {
                verify(exactly = 1) { service.getDefaultReviewQueue(pageable) }
                response.body?.get("queue") shouldBe page.content
            }
        }
    }

    Given("a real reviewer decision on an overdue VUP loan") {
        val service = mockk<VupLoanService>()
        val controller = VupLoanAdminController(service)
        val loan = mockk<VupLoan>(relaxed = true)
        every { service.decide("loan_1", "admin_1", true, "hardship") } returns loan

        When("deciding") {
            val response = controller.decide("loan_1", DecideVupLoanReviewRequest(true, "hardship"), currentUser)

            Then("it real-delegates scoped to the real reviewer's own userId, never a client-supplied one") {
                verify(exactly = 1) { service.decide("loan_1", "admin_1", true, "hardship") }
                response.body?.get("loan") shouldBe loan
            }
        }
    }

    listOf(
        Triple(VupLoanNotFoundException("Not found") as RuntimeException, HttpStatus.NOT_FOUND, "VUP_LOAN_NOT_FOUND"),
        Triple(VupLoanNotOverdueException("Conflict"), HttpStatus.CONFLICT, "VUP_LOAN_NOT_OVERDUE"),
        Triple(InvalidVupLoanReviewNoteException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_REVIEW_NOTE"),
    ).forEach { (exception, expectedStatus, expectedCode) ->
        Given("a real ${exception::class.simpleName}") {
            val controller = VupLoanAdminController(mockk())

            When("its exception handler maps it to a real HTTP response") {
                val response = when (exception) {
                    is VupLoanNotFoundException -> controller.handleNotFound(exception)
                    is VupLoanNotOverdueException -> controller.handleNotOverdue(exception)
                    is InvalidVupLoanReviewNoteException -> controller.handleInvalidReviewNote(exception)
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
