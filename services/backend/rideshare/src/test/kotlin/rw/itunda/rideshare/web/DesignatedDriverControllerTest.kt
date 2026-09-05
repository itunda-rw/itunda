package rw.itunda.rideshare.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.core.domain.DesignatedDriver
import rw.itunda.core.domain.DesignatedDriverTrip
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser
import rw.itunda.rideshare.DesignatedDriverService

/**
 * Real gap found and fixed 2026-09-05 (feedback_idempotency_key_sweep): before the
 * fix, `registerDriver` called DesignatedDriverService.register directly with no
 * Idempotency-Key protection -- a lost response after a successful register would
 * resubmit here and hit DesignatedDriverAlreadyRegisteredException on the retry, a
 * confusing conflict for a registration that actually already succeeded. This file
 * exists to make sure that wiring can't silently regress.
 */
class DesignatedDriverControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    Given("a first-time driver registration request") {
        val service = mockk<DesignatedDriverService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = DesignatedDriverController(service, idempotencyService)

        val driver = mockk<DesignatedDriver>(relaxed = true)
        every { service.register("user_1", "DL-123456") } returns driver

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute("POST /api/v1/designated-driver/drivers/register", "key-1", any(), capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("registering") {
            val response = controller.registerDriver(RegisterDesignatedDriverRequest("DL-123456"), "key-1", currentUser)

            Then("it routes through the real idempotency service, keyed to this exact route") {
                verify(exactly = 1) {
                    idempotencyService.replayOrExecute("POST /api/v1/designated-driver/drivers/register", "key-1", any(), any())
                }
                verify(exactly = 1) { service.register("user_1", "DL-123456") }
                response.statusCode shouldBe HttpStatus.CREATED
                response.body?.get("driver") shouldBe driver
            }
        }
    }

    Given("a retried driver registration request using the same Idempotency-Key as a completed one") {
        val service = mockk<DesignatedDriverService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = DesignatedDriverController(service, idempotencyService)

        every {
            idempotencyService.replayOrExecute("POST /api/v1/designated-driver/drivers/register", "key-1", any(), any())
        } returns (201 to mapOf("success" to true, "driver" to "cached-result"))

        When("retrying with the same key") {
            val response = controller.registerDriver(RegisterDesignatedDriverRequest("DL-123456"), "key-1", currentUser)

            Then("the cached response is returned and the account is never registered again") {
                response.body?.get("driver") shouldBe "cached-result"
                verify(exactly = 0) { service.register(any(), any()) }
            }
        }
    }

    // Real gap found 2026-09-05 (feedback_idempotency_key_sweep re-audit, same class
    // as RideController.acceptTrip/EatsController.claimDelivery's identical fixes) --
    // DesignatedDriverService.acceptTrip guards on trip.status != REQUESTED. After a
    // successful accept, the trip's own status is now ACCEPTED, so a lost-response
    // retry from the SAME driver used to hit this exact guard with a confusing
    // "no longer available" message for an accept that actually just succeeded.
    Given("a first-time trip accept") {
        val service = mockk<DesignatedDriverService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = DesignatedDriverController(service, idempotencyService)

        val trip = mockk<DesignatedDriverTrip>(relaxed = true)
        every { service.acceptTrip("user_1", "trip_1") } returns trip

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute("POST /api/v1/designated-driver/trips/trip_1/accept", "key-1", any(), capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("accepting the trip") {
            val response = controller.acceptTrip("trip_1", "key-1", currentUser)

            Then("it routes through the real idempotency service, keyed to this exact route") {
                verify(exactly = 1) {
                    idempotencyService.replayOrExecute("POST /api/v1/designated-driver/trips/trip_1/accept", "key-1", any(), any())
                }
                verify(exactly = 1) { service.acceptTrip("user_1", "trip_1") }
                response.statusCode shouldBe HttpStatus.OK
                response.body?.get("trip") shouldBe trip
            }
        }
    }

    Given("a retried trip accept using the same Idempotency-Key as a completed one") {
        val service = mockk<DesignatedDriverService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = DesignatedDriverController(service, idempotencyService)

        every {
            idempotencyService.replayOrExecute("POST /api/v1/designated-driver/trips/trip_1/accept", "key-1", any(), any())
        } returns (200 to mapOf("success" to true, "trip" to "cached-result"))

        When("retrying with the same key") {
            val response = controller.acceptTrip("trip_1", "key-1", currentUser)

            Then("the cached response is returned and the trip is never accepted again") {
                response.body?.get("trip") shouldBe "cached-result"
                verify(exactly = 0) { service.acceptTrip(any(), any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
