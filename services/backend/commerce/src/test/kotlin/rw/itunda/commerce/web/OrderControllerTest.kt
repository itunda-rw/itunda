package rw.itunda.commerce.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.commerce.OrderService
import rw.itunda.commerce.ProductFavoriteService
import rw.itunda.commerce.ProductInquiryService
import rw.itunda.commerce.ProductReviewService
import rw.itunda.commerce.OrderReturnService
import rw.itunda.core.domain.Order
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser

/**
 * Real gap found and fixed 2026-09-05 (feedback_idempotency_key_sweep re-audit, same
 * "claim" bug shape as RideController.acceptTrip/EatsController.claimDelivery/
 * DesignatedDriverController.acceptTrip's identical fixes): OrderService.claimDelivery
 * checks BOTH "rider already has an active delivery" (RiderAlreadyOnDeliveryException)
 * AND "delivery already claimed" (DeliveryAlreadyClaimedException). After a successful
 * claim, the rider legitimately now has an active delivery, so a lost-response retry
 * from the SAME rider used to hit the first guard with a scary, confusing "finish your
 * current delivery" message for a claim that actually just succeeded. This file exists
 * to make sure that wiring can't silently regress.
 */
class OrderControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "rider_1")

    fun controller(orderService: OrderService, idempotencyService: IdempotencyService) = OrderController(
        orderService,
        idempotencyService,
        mockk<ProductReviewService>(relaxed = true),
        mockk<ProductFavoriteService>(relaxed = true),
        mockk<OrderReturnService>(relaxed = true),
        mockk<ProductInquiryService>(relaxed = true),
    )

    Given("a first-time delivery claim") {
        val orderService = mockk<OrderService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = controller(orderService, idempotencyService)

        val order = mockk<Order>(relaxed = true)
        every { orderService.claimDelivery("rider_1", "order_1") } returns order

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute("POST /api/v1/orders/order_1/claim-delivery", "key-1", any(), capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("claiming the delivery") {
            val response = controller.claimDelivery("order_1", "key-1", currentUser)

            Then("it routes through the real idempotency service, keyed to this exact route") {
                verify(exactly = 1) {
                    idempotencyService.replayOrExecute("POST /api/v1/orders/order_1/claim-delivery", "key-1", any(), any())
                }
                verify(exactly = 1) { orderService.claimDelivery("rider_1", "order_1") }
                response.statusCode shouldBe HttpStatus.OK
                response.body?.get("order") shouldBe order
            }
        }
    }

    Given("a retried delivery claim using the same Idempotency-Key as a completed one") {
        val orderService = mockk<OrderService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = controller(orderService, idempotencyService)

        every {
            idempotencyService.replayOrExecute("POST /api/v1/orders/order_1/claim-delivery", "key-1", any(), any())
        } returns (200 to mapOf("success" to true, "order" to "cached-result"))

        When("retrying with the same key") {
            val response = controller.claimDelivery("order_1", "key-1", currentUser)

            Then("the cached response is returned and the delivery is never claimed again") {
                response.body?.get("order") shouldBe "cached-result"
                verify(exactly = 0) { orderService.claimDelivery(any(), any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
