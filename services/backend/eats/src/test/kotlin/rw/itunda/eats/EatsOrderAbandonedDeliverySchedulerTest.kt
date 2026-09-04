package rw.itunda.eats

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.EatsOrder
import rw.itunda.core.domain.EatsOrderStatus
import java.math.BigDecimal

/**
 * First test coverage for EatsOrderAbandonedDeliveryScheduler -- same real "one bad
 * row can't poison the sweep" resilience contract as every other scheduler test in
 * this sweep.
 */
class EatsOrderAbandonedDeliverySchedulerTest : BehaviorSpec({

    fun order(id: String) = EatsOrder(
        id = id, buyerId = "buyer_$id", restaurantId = "restaurant_$id", riderId = "rider_$id",
        deliveryAddress = "addr", itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"),
        platformFee = BigDecimal("90"), totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_$id",
        status = EatsOrderStatus.CANCELLED,
    )

    Given("3 abandoned deliveries, where force-cancelling the middle one fails") {
        val eatsOrderService = mockk<EatsOrderService>()
        every { eatsOrderService.getAbandonedDeliveries() } returns listOf(order("o1"), order("o2"), order("o3"))
        every { eatsOrderService.forceCancelAbandonedDelivery("o1") } returns order("o1")
        every { eatsOrderService.forceCancelAbandonedDelivery("o2") } throws RuntimeException("ledger error")
        every { eatsOrderService.forceCancelAbandonedDelivery("o3") } returns order("o3")
        val scheduler = EatsOrderAbandonedDeliveryScheduler(eatsOrderService)

        When("the sweep runs") {
            scheduler.run()

            Then("all 3 are still attempted -- the middle failure doesn't stop the sweep") {
                verify(exactly = 1) { eatsOrderService.forceCancelAbandonedDelivery("o1") }
                verify(exactly = 1) { eatsOrderService.forceCancelAbandonedDelivery("o2") }
                verify(exactly = 1) { eatsOrderService.forceCancelAbandonedDelivery("o3") }
            }
        }
    }

    Given("no abandoned deliveries") {
        val eatsOrderService = mockk<EatsOrderService>()
        every { eatsOrderService.getAbandonedDeliveries() } returns emptyList()
        val scheduler = EatsOrderAbandonedDeliveryScheduler(eatsOrderService)

        When("the sweep runs") {
            scheduler.run()

            Then("nothing is cancelled, no exception is thrown") {
                verify(exactly = 0) { eatsOrderService.forceCancelAbandonedDelivery(any()) }
            }
        }
    }
})
