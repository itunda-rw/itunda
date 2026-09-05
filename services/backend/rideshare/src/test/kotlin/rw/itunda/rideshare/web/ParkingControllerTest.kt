package rw.itunda.rideshare.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.core.domain.ParkingSession
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser
import rw.itunda.rideshare.ParkingService

/**
 * Real gap found and fixed 2026-09-05 (feedback_idempotency_key_sweep re-audit, same
 * "claim" bug shape as RideController.acceptTrip/BikeRentalController.startRental):
 * this whole controller had NO Idempotency-Key infrastructure at all. `startSession`
 * marks the spot unavailable and creates the ACTIVE session BEFORE returning, so
 * after a successful start a lost-response retry from the SAME renter used to hit
 * ParkingSpotNotAvailableException("already occupied") for a start that actually
 * just succeeded. `endSession` is real money movement (checkout charges a real fee)
 * guarded by ParkingSessionAlreadyEndedException with the same gap. This file exists
 * to make sure that wiring can't silently regress.
 */
class ParkingControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    Given("a first-time session start") {
        val service = mockk<ParkingService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = ParkingController(service, idempotencyService)

        val session = mockk<ParkingSession>(relaxed = true)
        every { service.startSession("user_1", "spot_1") } returns session

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute("POST /api/v1/parking/sessions", "key-1", any(), capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("starting the session") {
            val response = controller.startSession(StartParkingSessionRequest("spot_1"), "key-1", currentUser)

            Then("it routes through the real idempotency service, keyed to this exact route") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/parking/sessions", "key-1", any(), any()) }
                verify(exactly = 1) { service.startSession("user_1", "spot_1") }
                response.statusCode shouldBe HttpStatus.CREATED
                response.body?.get("session") shouldBe session
            }
        }
    }

    Given("a retried session start using the same Idempotency-Key as a completed one") {
        val service = mockk<ParkingService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = ParkingController(service, idempotencyService)

        every {
            idempotencyService.replayOrExecute("POST /api/v1/parking/sessions", "key-1", any(), any())
        } returns (201 to mapOf("success" to true, "session" to "cached-result"))

        When("retrying with the same key") {
            val response = controller.startSession(StartParkingSessionRequest("spot_1"), "key-1", currentUser)

            Then("the cached response is returned and the session is never started again") {
                response.body?.get("session") shouldBe "cached-result"
                verify(exactly = 0) { service.startSession(any(), any()) }
            }
        }
    }

    Given("a first-time session end") {
        val service = mockk<ParkingService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = ParkingController(service, idempotencyService)

        val session = mockk<ParkingSession>(relaxed = true)
        every { service.endSession("user_1", "session_1") } returns session

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute("POST /api/v1/parking/sessions/session_1/end", "key-1", any(), capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("ending the session") {
            val response = controller.endSession("session_1", "key-1", currentUser)

            Then("it routes through the real idempotency service, keyed to this exact route") {
                verify(exactly = 1) {
                    idempotencyService.replayOrExecute("POST /api/v1/parking/sessions/session_1/end", "key-1", any(), any())
                }
                verify(exactly = 1) { service.endSession("user_1", "session_1") }
                response.statusCode shouldBe HttpStatus.OK
                response.body?.get("session") shouldBe session
            }
        }
    }

    Given("a retried session end using the same Idempotency-Key as a completed one") {
        val service = mockk<ParkingService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = ParkingController(service, idempotencyService)

        every {
            idempotencyService.replayOrExecute("POST /api/v1/parking/sessions/session_1/end", "key-1", any(), any())
        } returns (200 to mapOf("success" to true, "session" to "cached-result"))

        When("retrying with the same key") {
            val response = controller.endSession("session_1", "key-1", currentUser)

            Then("the cached response is returned and the session is never ended again") {
                response.body?.get("session") shouldBe "cached-result"
                verify(exactly = 0) { service.endSession(any(), any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
