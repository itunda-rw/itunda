package rw.itunda.p2p

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.P2pPaymentRequest
import java.math.BigDecimal
import java.time.Instant

/**
 * First test coverage for P2pPaymentRequestExpiryScheduler -- mirrors
 * HarvestAdvanceOverdueSchedulerTest exactly, including the per-item try/catch it
 * proved out (Bank product-completeness pass, cycle 2, 2026-09-09).
 */
class P2pPaymentRequestExpirySchedulerTest : BehaviorSpec({

    fun request(id: String) = P2pPaymentRequest(
        id = id, requesterUserId = "requester_$id", amount = BigDecimal("2000"),
        description = "Lunch", expiresAt = Instant.now().minusSeconds(60),
    )

    Given("3 expired payment requests, where flagging the middle one fails") {
        val p2pService = mockk<P2pService>()
        val r1 = request("r1")
        val r2 = request("r2")
        val r3 = request("r3")
        every { p2pService.getRequestsDueForExpiryCheck() } returns listOf(r1, r2, r3)
        every { p2pService.markExpired(r1) } returns Unit
        every { p2pService.markExpired(r2) } throws RuntimeException("unexpected database error")
        every { p2pService.markExpired(r3) } returns Unit
        val scheduler = P2pPaymentRequestExpiryScheduler(p2pService)

        When("the sweep runs") {
            scheduler.run()

            Then("all 3 are still attempted -- the middle failure doesn't stop flagging the rest") {
                verify(exactly = 1) { p2pService.markExpired(r1) }
                verify(exactly = 1) { p2pService.markExpired(r2) }
                verify(exactly = 1) { p2pService.markExpired(r3) }
            }
        }
    }

    Given("no expired payment requests") {
        val p2pService = mockk<P2pService>()
        every { p2pService.getRequestsDueForExpiryCheck() } returns emptyList()
        val scheduler = P2pPaymentRequestExpiryScheduler(p2pService)

        When("the sweep runs") {
            scheduler.run()

            Then("nothing is flagged, no exception is thrown") {
                verify(exactly = 0) { p2pService.markExpired(any()) }
            }
        }
    }
})
