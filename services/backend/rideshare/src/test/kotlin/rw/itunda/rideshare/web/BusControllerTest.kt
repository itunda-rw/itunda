package rw.itunda.rideshare.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.core.domain.BusBooking
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser
import rw.itunda.rideshare.BusService

/**
 * Real gap found and fixed 2026-09-05 (feedback_idempotency_key_sweep re-audit):
 * before the fix, `cancelBooking` called BusService.cancelBooking directly with no
 * Idempotency-Key protection, despite cancelling a booking being real money
 * movement (a real refund posted via ledgerService.postLedgerTransaction) guarded
 * by BusBookingAlreadyCancelledException. A lost-response retry after a successful
 * cancel used to hit a confusing conflict for a cancellation that already
 * succeeded. This file exists to make sure that wiring can't silently regress.
 */
class BusControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    Given("a first-time booking cancellation") {
        val service = mockk<BusService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = BusController(service, idempotencyService)

        val booking = mockk<BusBooking>(relaxed = true)
        every { service.cancelBooking("user_1", "booking_1") } returns booking

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute("POST /api/v1/bus/bookings/booking_1/cancel", "key-1", any(), capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("cancelling the booking") {
            val response = controller.cancelBooking("booking_1", "key-1", currentUser)

            Then("it routes through the real idempotency service, keyed to this exact route") {
                verify(exactly = 1) {
                    idempotencyService.replayOrExecute("POST /api/v1/bus/bookings/booking_1/cancel", "key-1", any(), any())
                }
                verify(exactly = 1) { service.cancelBooking("user_1", "booking_1") }
                response.statusCode shouldBe HttpStatus.OK
                response.body?.get("booking") shouldBe booking
            }
        }
    }

    Given("a retried booking cancellation using the same Idempotency-Key as a completed one") {
        val service = mockk<BusService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = BusController(service, idempotencyService)

        every {
            idempotencyService.replayOrExecute("POST /api/v1/bus/bookings/booking_1/cancel", "key-1", any(), any())
        } returns (200 to mapOf("success" to true, "booking" to "cached-result"))

        When("retrying with the same key") {
            val response = controller.cancelBooking("booking_1", "key-1", currentUser)

            Then("the cached response is returned and the booking is never cancelled again") {
                response.body?.get("booking") shouldBe "cached-result"
                verify(exactly = 0) { service.cancelBooking(any(), any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
