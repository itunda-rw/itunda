package rw.itunda.eats.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.core.domain.EatsOrder
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser
import rw.itunda.eats.EatsOrderService

/**
 * Real gap found and fixed 2026-09-05 (feedback_idempotency_key_sweep re-audit, same
 * class as RideController.acceptTrip's identical fix): EatsOrderService.claimDelivery
 * checks BOTH "rider already has an active delivery" (RiderAlreadyOnDeliveryException)
 * AND "delivery already claimed" (DeliveryAlreadyClaimedException). After a successful
 * claim, the rider legitimately now has an active delivery, so a lost-response retry
 * from the SAME rider used to hit the first guard with a scary, confusing "finish your
 * current delivery" message for a claim that actually just succeeded. This file exists
 * to make sure that wiring can't silently regress.
 */
class EatsControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "rider_1")

    fun controller(eatsOrderService: EatsOrderService, idempotencyService: IdempotencyService) = EatsController(
        mockk(relaxed = true), eatsOrderService, mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), idempotencyService,
        mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
    )

    Given("a first-time delivery claim") {
        val eatsOrderService = mockk<EatsOrderService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = controller(eatsOrderService, idempotencyService)

        val order = mockk<EatsOrder>(relaxed = true)
        every { eatsOrderService.claimDelivery("rider_1", "order_1") } returns order

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute("POST /api/v1/eats/orders/order_1/claim", "key-1", any(), capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("claiming the delivery") {
            val response = controller.claimDelivery("order_1", "key-1", currentUser)

            Then("it routes through the real idempotency service, keyed to this exact route") {
                verify(exactly = 1) {
                    idempotencyService.replayOrExecute("POST /api/v1/eats/orders/order_1/claim", "key-1", any(), any())
                }
                verify(exactly = 1) { eatsOrderService.claimDelivery("rider_1", "order_1") }
                response.statusCode shouldBe HttpStatus.OK
                response.body?.get("order") shouldBe order
            }
        }
    }

    Given("a retried delivery claim using the same Idempotency-Key as a completed one") {
        val eatsOrderService = mockk<EatsOrderService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = controller(eatsOrderService, idempotencyService)

        every {
            idempotencyService.replayOrExecute("POST /api/v1/eats/orders/order_1/claim", "key-1", any(), any())
        } returns (200 to mapOf("success" to true, "order" to "cached-result"))

        When("retrying with the same key") {
            val response = controller.claimDelivery("order_1", "key-1", currentUser)

            Then("the cached response is returned and the delivery is never claimed again") {
                response.body?.get("order") shouldBe "cached-result"
                verify(exactly = 0) { eatsOrderService.claimDelivery(any(), any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
