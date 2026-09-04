package rw.itunda.eats

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.EatsOrder
import rw.itunda.core.domain.EatsOrderStatus
import java.math.BigDecimal

/**
 * First test coverage for DispatchOfferScheduler -- same real "one bad row can't
 * poison the sweep" resilience contract as every other scheduler test in this sweep,
 * plus the real once-per-tick `computeDispatchPools()` optimization this class's own
 * doc comment names explicitly (called once per tick, not once per order).
 */
class DispatchOfferSchedulerTest : BehaviorSpec({

    fun order(id: String) = EatsOrder(
        id = id, buyerId = "buyer_$id", restaurantId = "restaurant_$id",
        deliveryAddress = "addr", itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"),
        platformFee = BigDecimal("90"), totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_$id",
        status = EatsOrderStatus.PLACED,
    )

    Given("3 expired dispatch offers, where reassigning the middle one fails") {
        val eatsOrderService = mockk<EatsOrderService>()
        val pools = EatsDispatchPools(candidatePool = emptyList(), busyRiderIds = emptySet())
        every { eatsOrderService.getExpiredOffers() } returns listOf(order("o1"), order("o2"), order("o3"))
        every { eatsOrderService.computeDispatchPools() } returns pools
        every { eatsOrderService.reassignExpiredOffer("o1", pools) } returns order("o1")
        every { eatsOrderService.reassignExpiredOffer("o2", pools) } throws RuntimeException("no rider available")
        every { eatsOrderService.reassignExpiredOffer("o3", pools) } returns order("o3")
        val scheduler = DispatchOfferScheduler(eatsOrderService)

        When("the sweep runs") {
            scheduler.run()

            Then("all 3 are still attempted, sharing the SAME pools computed once for the whole tick") {
                verify(exactly = 1) { eatsOrderService.computeDispatchPools() }
                verify(exactly = 1) { eatsOrderService.reassignExpiredOffer("o1", pools) }
                verify(exactly = 1) { eatsOrderService.reassignExpiredOffer("o2", pools) }
                verify(exactly = 1) { eatsOrderService.reassignExpiredOffer("o3", pools) }
            }
        }
    }

    Given("no expired offers this tick") {
        val eatsOrderService = mockk<EatsOrderService>()
        every { eatsOrderService.getExpiredOffers() } returns emptyList()
        val scheduler = DispatchOfferScheduler(eatsOrderService)

        When("the sweep runs") {
            scheduler.run()

            Then("dispatch pools are never even computed -- a real cost avoided when there's nothing to dispatch") {
                verify(exactly = 0) { eatsOrderService.computeDispatchPools() }
                verify(exactly = 0) { eatsOrderService.reassignExpiredOffer(any(), any()) }
            }
        }
    }
})
