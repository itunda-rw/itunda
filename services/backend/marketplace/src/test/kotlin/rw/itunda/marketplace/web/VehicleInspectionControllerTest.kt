package rw.itunda.marketplace.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.core.domain.VehicleInspectionMechanic
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser
import rw.itunda.marketplace.VehicleInspectionService

/**
 * Real gap found and fixed 2026-09-05 (feedback_idempotency_key_sweep): before the
 * fix, `registerAsMechanic` called VehicleInspectionService.registerAsMechanic
 * directly with no Idempotency-Key protection -- a lost response after a successful
 * register would resubmit here and hit MechanicAlreadyRegisteredException on the
 * retry, a confusing conflict for a registration that actually already succeeded.
 * Booking creation (`requestInspection`) was already protected; this register
 * endpoint was the outlier. This file exists to make sure that wiring can't
 * silently regress.
 */
class VehicleInspectionControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    Given("a first-time mechanic registration request") {
        val service = mockk<VehicleInspectionService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = VehicleInspectionController(service, idempotencyService)

        val mechanic = mockk<VehicleInspectionMechanic>(relaxed = true)
        every { service.registerAsMechanic("user_1", "Kigali Auto Care") } returns mechanic

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute(
                "POST /api/v1/marketplace/inspections/mechanics/register",
                "key-1",
                any(),
                capture(actionSlot),
            )
        } answers { actionSlot.captured.invoke() }

        When("registering") {
            val response = controller.registerAsMechanic(RegisterMechanicRequest("Kigali Auto Care"), "key-1", currentUser)

            Then("it routes through the real idempotency service, keyed to this exact route") {
                verify(exactly = 1) {
                    idempotencyService.replayOrExecute("POST /api/v1/marketplace/inspections/mechanics/register", "key-1", any(), any())
                }
                verify(exactly = 1) { service.registerAsMechanic("user_1", "Kigali Auto Care") }
                response.statusCode shouldBe HttpStatus.CREATED
                response.body?.get("mechanic") shouldBe mechanic
            }
        }
    }

    Given("a retried mechanic registration request using the same Idempotency-Key as a completed one") {
        val service = mockk<VehicleInspectionService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = VehicleInspectionController(service, idempotencyService)

        every {
            idempotencyService.replayOrExecute("POST /api/v1/marketplace/inspections/mechanics/register", "key-1", any(), any())
        } returns (201 to mapOf("success" to true, "mechanic" to "cached-result"))

        When("retrying with the same key") {
            val response = controller.registerAsMechanic(RegisterMechanicRequest("Kigali Auto Care"), "key-1", currentUser)

            Then("the cached response is returned and the account is never registered again") {
                response.body?.get("mechanic") shouldBe "cached-result"
                verify(exactly = 0) { service.registerAsMechanic(any(), any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
