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
import rw.itunda.core.domain.HarvestAdvance
import rw.itunda.core.security.CurrentUser

/**
 * First test coverage for HarvestAdvanceAdminController -- mirrors
 * VupLoanAdminControllerTest exactly (Bank product-completeness pass, cycle 2,
 * 2026-09-08).
 */
class HarvestAdvanceAdminControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "admin_1")

    Given("a real overdue-harvest-advance review queue") {
        val service = mockk<CooperativeService>()
        val controller = HarvestAdvanceAdminController(service)
        val pageable = PageRequest.of(0, 20)
        val page = PageImpl(listOf(mockk<HarvestAdvance>(relaxed = true)))
        every { service.getDefaultReviewQueue(pageable) } returns page

        When("fetching it") {
            val response = controller.queue(pageable)

            Then("it delegates and returns the real queue page") {
                verify(exactly = 1) { service.getDefaultReviewQueue(pageable) }
                response.body?.get("queue") shouldBe page.content
            }
        }
    }

    Given("a real reviewer decision on an overdue harvest advance") {
        val service = mockk<CooperativeService>()
        val controller = HarvestAdvanceAdminController(service)
        val advance = mockk<HarvestAdvance>(relaxed = true)
        every { service.decide("advance_1", "admin_1", true, "hardship") } returns advance

        When("deciding") {
            val response = controller.decide("advance_1", DecideHarvestAdvanceReviewRequest(true, "hardship"), currentUser)

            Then("it real-delegates scoped to the real reviewer's own userId, never a client-supplied one") {
                verify(exactly = 1) { service.decide("advance_1", "admin_1", true, "hardship") }
                response.body?.get("advance") shouldBe advance
            }
        }
    }

    listOf(
        Triple(HarvestAdvanceNotFoundException("Not found") as RuntimeException, HttpStatus.NOT_FOUND, "HARVEST_ADVANCE_NOT_FOUND"),
        Triple(HarvestAdvanceNotOverdueException("Conflict"), HttpStatus.CONFLICT, "HARVEST_ADVANCE_NOT_OVERDUE"),
        Triple(InvalidHarvestAdvanceReviewNoteException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_REVIEW_NOTE"),
    ).forEach { (exception, expectedStatus, expectedCode) ->
        Given("a real ${exception::class.simpleName}") {
            val controller = HarvestAdvanceAdminController(mockk())

            When("its exception handler maps it to a real HTTP response") {
                val response = when (exception) {
                    is HarvestAdvanceNotFoundException -> controller.handleNotFound(exception)
                    is HarvestAdvanceNotOverdueException -> controller.handleNotOverdue(exception)
                    is InvalidHarvestAdvanceReviewNoteException -> controller.handleInvalidReviewNote(exception)
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
