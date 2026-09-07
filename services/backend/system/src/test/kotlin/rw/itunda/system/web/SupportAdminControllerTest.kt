package rw.itunda.system.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import rw.itunda.core.domain.SupportTicketResolution
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.support.SupportService
import rw.itunda.core.support.SupportTicketAlreadyResolvedException
import rw.itunda.core.support.SupportTicketNotFoundException
import rw.itunda.core.support.SupportTransactionNotFoundException

/**
 * First test coverage for SupportAdminController -- SupportServiceTest.kt already
 * covers the underlying business logic in full, but the admin controller's own
 * delegation (reviewer-scoped, never client-supplied) and exception-handler mapping
 * had zero coverage, unlike every other controller this sweep has covered. Matches
 * this sweep's established BehaviorSpec/MockK shape (see MapsControllerTest.kt).
 */
class SupportAdminControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "admin_1")
    val pageable = PageRequest.of(0, 20)

    fun controller(supportService: SupportService = mockk()) = SupportAdminController(supportService)

    Given("a real support-ticket-queue request") {
        val supportService = mockk<SupportService>()
        val ctl = controller(supportService = supportService)
        every { supportService.getQueue(pageable) } returns PageImpl(emptyList())

        When("fetching it") {
            ctl.queue(pageable)
            Then("it real-delegates to the shared queue, not scoped to any one reviewer") {
                verify(exactly = 1) { supportService.getQueue(pageable) }
            }
        }
    }

    Given("a real ticket resolution") {
        val supportService = mockk<SupportService>()
        val ctl = controller(supportService = supportService)
        every {
            supportService.resolve("ticket_1", "admin_1", SupportTicketResolution.REFUNDED, "Confirmed")
        } returns mockk(relaxed = true)

        When("resolving it") {
            ctl.resolve("ticket_1", ResolveTicketRequest(SupportTicketResolution.REFUNDED, "Confirmed"), currentUser)
            Then("it real-attributes the resolution to the caller's own userId, never a client-supplied reviewer") {
                verify(exactly = 1) { supportService.resolve("ticket_1", "admin_1", SupportTicketResolution.REFUNDED, "Confirmed") }
            }
        }
    }

    listOf(
        Triple(SupportTicketNotFoundException("Support ticket not found") as RuntimeException, HttpStatus.NOT_FOUND, "SUPPORT_TICKET_NOT_FOUND"),
        Triple(SupportTicketAlreadyResolvedException("This ticket has already been resolved"), HttpStatus.CONFLICT, "SUPPORT_TICKET_ALREADY_RESOLVED"),
        Triple(SupportTransactionNotFoundException("No ledger entries found for transaction txn_1"), HttpStatus.UNPROCESSABLE_ENTITY, "REFUND_SOURCE_TRANSACTION_MISSING"),
    ).forEach { (exception, expectedStatus, expectedCode) ->
        Given("a real ${exception::class.simpleName}") {
            val ctl = controller()

            When("its exception handler maps it to a real HTTP response") {
                val response = when (exception) {
                    is SupportTicketNotFoundException -> ctl.handleNotFound(exception)
                    is SupportTicketAlreadyResolvedException -> ctl.handleAlreadyResolved(exception)
                    is SupportTransactionNotFoundException -> ctl.handleTransactionNotFound(exception)
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
