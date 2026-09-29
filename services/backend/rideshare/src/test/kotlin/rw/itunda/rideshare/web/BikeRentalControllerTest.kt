package rw.itunda.rideshare.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.core.domain.BikeRentalSession
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser
import rw.itunda.rideshare.BikeRentalService

/**
 * Real gap found and fixed 2026-09-05 (feedback_idempotency_key_sweep re-audit, same
 * "claim" bug shape as RideController.acceptTrip): this whole controller had NO
 * Idempotency-Key infrastructure at all. `startRental` marks the bike unavailable
 * and creates the ACTIVE session BEFORE returning, so after a successful start a
 * lost-response retry from the SAME rider used to hit
 * BikeNotAvailableException("This bike is already rented") for a start that
 * actually just succeeded. `endRental` is real money movement (settleRental
 * charges a real fare) guarded by BikeRentalAlreadyEndedException with the same
 * gap. This file exists to make sure that wiring can't silently regress.
 */
class BikeRentalControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    Given("a first-time rental start") {
        val service = mockk<BikeRentalService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = BikeRentalController(service, idempotencyService)

        val session = mockk<BikeRentalSession>(relaxed = true)
        every { service.startRental("user_1", "bike_1", 1.0, 2.0) } returns session

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute("POST /api/v1/bikeshare/rentals", "key-1", any(), capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("starting the rental") {
            val response = controller.startRental(StartBikeRentalRequest("bike_1", 1.0, 2.0), "key-1", currentUser)

            Then("it routes through the real idempotency service, keyed to this exact route") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/bikeshare/rentals", "key-1", any(), any()) }
                verify(exactly = 1) { service.startRental("user_1", "bike_1", 1.0, 2.0) }
                response.statusCode shouldBe HttpStatus.CREATED
                response.body?.get("rental") shouldBe session
            }
        }
    }

    Given("a retried rental start using the same Idempotency-Key as a completed one") {
        val service = mockk<BikeRentalService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = BikeRentalController(service, idempotencyService)

        every {
            idempotencyService.replayOrExecute("POST /api/v1/bikeshare/rentals", "key-1", any(), any())
        } returns (201 to mapOf("success" to true, "rental" to "cached-result"))

        When("retrying with the same key") {
            val response = controller.startRental(StartBikeRentalRequest("bike_1", 1.0, 2.0), "key-1", currentUser)

            Then("the cached response is returned and the rental is never started again") {
                response.body?.get("rental") shouldBe "cached-result"
                verify(exactly = 0) { service.startRental(any(), any(), any(), any()) }
            }
        }
    }

    Given("a first-time rental end") {
        val service = mockk<BikeRentalService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = BikeRentalController(service, idempotencyService)

        val session = mockk<BikeRentalSession>(relaxed = true)
        every { service.endRental("user_1", "session_1", 3.0, 4.0) } returns session

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute("POST /api/v1/bikeshare/rentals/session_1/end", "key-1", any(), capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("ending the rental") {
            val response = controller.endRental("session_1", EndBikeRentalRequest(3.0, 4.0), "key-1", currentUser)

            Then("it routes through the real idempotency service, keyed to this exact route") {
                verify(exactly = 1) {
                    idempotencyService.replayOrExecute("POST /api/v1/bikeshare/rentals/session_1/end", "key-1", any(), any())
                }
                verify(exactly = 1) { service.endRental("user_1", "session_1", 3.0, 4.0) }
                response.statusCode shouldBe HttpStatus.OK
                response.body?.get("rental") shouldBe session
            }
        }
    }

    Given("a retried rental end using the same Idempotency-Key as a completed one") {
        val service = mockk<BikeRentalService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = BikeRentalController(service, idempotencyService)

        every {
            idempotencyService.replayOrExecute("POST /api/v1/bikeshare/rentals/session_1/end", "key-1", any(), any())
        } returns (200 to mapOf("success" to true, "rental" to "cached-result"))

        When("retrying with the same key") {
            val response = controller.endRental("session_1", EndBikeRentalRequest(3.0, 4.0), "key-1", currentUser)

            Then("the cached response is returned and the rental is never ended again") {
                response.body?.get("rental") shouldBe "cached-result"
                verify(exactly = 0) { service.endRental(any(), any(), any(), any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
