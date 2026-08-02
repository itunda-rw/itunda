package rw.itunda.support

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.SupportTicket
import rw.itunda.core.domain.SupportTicketCategory
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.support.SupportService
import java.time.Duration
import java.time.Instant

/**
 * First test coverage for SupportController.
 *
 * Real bug found live (2026-08-02): POST /api/v1/support/tickets had no real rate
 * limit at all -- every other real content/request-creation endpoint in this codebase
 * already gates on `rateLimiter.checkLimit` (VehicleValuationService.registerVehicle,
 * IkiminaService.createIkimina, ...). SupportService itself lives in :core, which
 * :auth depends ON, so the limiter can't live there without a circular module
 * dependency -- the check is applied here in the controller instead.
 */
class SupportControllerTest : BehaviorSpec({

    fun currentUser(userId: String) = CurrentUser(userId)

    Given("a real authenticated user filing a support ticket") {
        val supportService = mockk<SupportService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val controller = SupportController(supportService, rateLimiter)

        val ticket = SupportTicket(
            id = "ticket_1", userId = "user_1", transactionId = "ledgertxn_1",
            category = SupportTicketCategory.GENERAL, description = "Wrong amount",
            dueBy = Instant.now().plusSeconds(3600),
        )
        every { supportService.createTicket("user_1", "ledgertxn_1", SupportTicketCategory.GENERAL, "Wrong amount") } returns ticket

        When("creating a ticket within the real rate limit") {
            controller.createTicket(CreateTicketRequest("ledgertxn_1", SupportTicketCategory.GENERAL, "Wrong amount"), currentUser("user_1"))

            Then("it real-checks the rate limit, keyed per user, before creating the ticket") {
                verify(exactly = 1) { rateLimiter.checkLimit("support:create-ticket:user_1", limit = 10, window = Duration.ofHours(1)) }
                verify(exactly = 1) { supportService.createTicket("user_1", "ledgertxn_1", SupportTicketCategory.GENERAL, "Wrong amount") }
            }
        }
    }

    Given("a real caller who has already exceeded the real ticket-creation rate limit") {
        val supportService = mockk<SupportService>()
        val rateLimiter = mockk<RateLimiter>()
        val controller = SupportController(supportService, rateLimiter)

        every { rateLimiter.checkLimit("support:create-ticket:user_spammer", limit = 10, window = Duration.ofHours(1)) } throws
            RateLimitExceededException("Too many attempts, please try again later")

        When("trying to create yet another ticket") {
            Then("it throws RateLimitExceededException and never touches SupportService at all") {
                try {
                    controller.createTicket(CreateTicketRequest("ledgertxn_1", SupportTicketCategory.GENERAL, "spam"), currentUser("user_spammer"))
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { supportService.createTicket(any(), any(), any(), any()) }
                }
            }
        }
    }
})
