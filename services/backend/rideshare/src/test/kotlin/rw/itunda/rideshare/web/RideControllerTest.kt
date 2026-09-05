package rw.itunda.rideshare.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.core.domain.RideTrip
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser
import rw.itunda.rideshare.RideDriverService
import rw.itunda.rideshare.RideTripReviewService
import rw.itunda.rideshare.RideTripService
import rw.itunda.rideshare.RideTrustedContactService

/**
 * Real gap found and fixed 2026-09-05 (feedback_idempotency_key_sweep re-audit): a
 * different shape than that sweep's usual "create" endpoints, but the same
 * underlying risk -- RideTripService.acceptTrip checks BOTH "driver already has an
 * active trip" (RideDriverAlreadyOnTripException) AND "trip already claimed"
 * (RideTripAlreadyClaimedException). After a successful accept, the driver
 * legitimately now has an active trip, so a lost-response retry from the SAME
 * driver used to hit the first guard with a scary, confusing "finish your current
 * trip" message for an accept that actually just succeeded. This file exists to
 * make sure that wiring can't silently regress.
 */
class RideControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "driver_1")

    fun controller(rideTripService: RideTripService, idempotencyService: IdempotencyService) = RideController(
        mockk<RideDriverService>(relaxed = true),
        rideTripService,
        mockk<RideTripReviewService>(relaxed = true),
        mockk<RideTrustedContactService>(relaxed = true),
        idempotencyService,
    )

    Given("a first-time trip accept") {
        val rideTripService = mockk<RideTripService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = controller(rideTripService, idempotencyService)

        val trip = mockk<RideTrip>(relaxed = true)
        every { rideTripService.acceptTrip("driver_1", "trip_1") } returns trip

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute("POST /api/v1/rides/trips/trip_1/accept", "key-1", any(), capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("accepting the trip") {
            val response = controller.acceptTrip("trip_1", "key-1", currentUser)

            Then("it routes through the real idempotency service, keyed to this exact route") {
                verify(exactly = 1) {
                    idempotencyService.replayOrExecute("POST /api/v1/rides/trips/trip_1/accept", "key-1", any(), any())
                }
                verify(exactly = 1) { rideTripService.acceptTrip("driver_1", "trip_1") }
                response.statusCode shouldBe HttpStatus.OK
                response.body?.get("trip") shouldBe trip
            }
        }
    }

    Given("a retried trip accept using the same Idempotency-Key as a completed one") {
        val rideTripService = mockk<RideTripService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = controller(rideTripService, idempotencyService)

        every {
            idempotencyService.replayOrExecute("POST /api/v1/rides/trips/trip_1/accept", "key-1", any(), any())
        } returns (200 to mapOf("success" to true, "trip" to "cached-result"))

        When("retrying with the same key") {
            val response = controller.acceptTrip("trip_1", "key-1", currentUser)

            Then("the cached response is returned and the trip is never accepted again") {
                response.body?.get("trip") shouldBe "cached-result"
                verify(exactly = 0) { rideTripService.acceptTrip(any(), any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
