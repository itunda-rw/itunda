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
import rw.itunda.core.domain.OrderReturnRequest
import rw.itunda.core.domain.OrderReturnType
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

    // Real gap found and fixed 2026-09-05 (feedback_idempotency_key_sweep re-audit):
    // requestReturn was guarded by ReturnAlreadyRequestedException with no
    // Idempotency-Key protection -- a lost-response retry after a successful request
    // used to hit a confusing conflict for a return that actually already got
    // requested.
    Given("a first-time return request") {
        val orderReturnService = mockk<OrderReturnService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = OrderController(
            mockk(relaxed = true), idempotencyService, mockk(relaxed = true), mockk(relaxed = true), orderReturnService, mockk(relaxed = true),
        )

        val returnRequest = mockk<OrderReturnRequest>(relaxed = true)
        every { orderReturnService.requestReturn("user_1", "order_1", OrderReturnType.RETURN, "damaged", "It arrived broken") } returns returnRequest

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute("POST /api/v1/orders/order_1/return", "key-1", any(), capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("requesting the return") {
            val response = controller.requestReturn(
                "order_1",
                RequestReturnRequest(OrderReturnType.RETURN, "damaged", "It arrived broken"),
                "key-1",
                CurrentUser(userId = "user_1"),
            )

            Then("it routes through the real idempotency service, keyed to this exact route") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/orders/order_1/return", "key-1", any(), any()) }
                verify(exactly = 1) { orderReturnService.requestReturn("user_1", "order_1", OrderReturnType.RETURN, "damaged", "It arrived broken") }
                response.statusCode shouldBe HttpStatus.CREATED
                response.body?.get("returnRequest") shouldBe returnRequest
            }
        }
    }

    Given("a retried return request using the same Idempotency-Key as a completed one") {
        val orderReturnService = mockk<OrderReturnService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = OrderController(
            mockk(relaxed = true), idempotencyService, mockk(relaxed = true), mockk(relaxed = true), orderReturnService, mockk(relaxed = true),
        )

        every {
            idempotencyService.replayOrExecute("POST /api/v1/orders/order_1/return", "key-1", any(), any())
        } returns (201 to mapOf("success" to true, "returnRequest" to "cached-result"))

        When("retrying with the same key") {
            val response = controller.requestReturn(
                "order_1",
                RequestReturnRequest(OrderReturnType.RETURN, "damaged", "It arrived broken"),
                "key-1",
                CurrentUser(userId = "user_1"),
            )

            Then("the cached response is returned and the return is never requested again") {
                response.body?.get("returnRequest") shouldBe "cached-result"
                verify(exactly = 0) { orderReturnService.requestReturn(any(), any(), any(), any(), any()) }
            }
        }
    }

    // Real gap found and fixed 2026-09-05 (feedback_idempotency_key_sweep re-audit):
    // decideReturnRequest is real money movement on approve (a refund reversal of
    // the original order's ledger entries) guarded by
    // ReturnRequestAlreadyDecidedException, with no Idempotency-Key protection.
    Given("a first-time return decision") {
        val orderReturnService = mockk<OrderReturnService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = OrderController(
            mockk(relaxed = true), idempotencyService, mockk(relaxed = true), mockk(relaxed = true), orderReturnService, mockk(relaxed = true),
        )

        val returnRequest = mockk<OrderReturnRequest>(relaxed = true)
        every { orderReturnService.decide("merchant_1", "return_1", true) } returns returnRequest

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute("POST /api/v1/orders/returns/return_1/decide", "key-1", any(), capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("deciding the return") {
            val response = controller.decideReturnRequest("return_1", DecideReturnRequest(true), "key-1", CurrentUser(userId = "merchant_1"))

            Then("it routes through the real idempotency service, keyed to this exact route") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/orders/returns/return_1/decide", "key-1", any(), any()) }
                verify(exactly = 1) { orderReturnService.decide("merchant_1", "return_1", true) }
                response.statusCode shouldBe HttpStatus.OK
                response.body?.get("returnRequest") shouldBe returnRequest
            }
        }
    }

    Given("a retried return decision using the same Idempotency-Key as a completed one") {
        val orderReturnService = mockk<OrderReturnService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = OrderController(
            mockk(relaxed = true), idempotencyService, mockk(relaxed = true), mockk(relaxed = true), orderReturnService, mockk(relaxed = true),
        )

        every {
            idempotencyService.replayOrExecute("POST /api/v1/orders/returns/return_1/decide", "key-1", any(), any())
        } returns (200 to mapOf("success" to true, "returnRequest" to "cached-result"))

        When("retrying with the same key") {
            val response = controller.decideReturnRequest("return_1", DecideReturnRequest(true), "key-1", CurrentUser(userId = "merchant_1"))

            Then("the cached response is returned and the return is never decided again") {
                response.body?.get("returnRequest") shouldBe "cached-result"
                verify(exactly = 0) { orderReturnService.decide(any(), any(), any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
