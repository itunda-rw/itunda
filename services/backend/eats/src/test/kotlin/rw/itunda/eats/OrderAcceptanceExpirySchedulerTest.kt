package rw.itunda.eats

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.EatsOrder
import rw.itunda.core.domain.EatsOrderStatus
import java.math.BigDecimal

/**
 * First test coverage for OrderAcceptanceExpiryScheduler -- and a real,
 * previously-live bug fix, not just a missing test. `expireUnacceptedOrder` calls the
 * private `refundAndCancel` helper, which calls `ledgerService.postLedgerTransaction`
 * with no try/catch anywhere in that chain, and this sweep's `.forEach` had none
 * either -- a single bad order would throw uncaught and silently stop refunding for
 * every OTHER real expired order in the same tick. Fixed alongside this test (same
 * commit) with a per-order try/catch.
 */
class OrderAcceptanceExpirySchedulerTest : BehaviorSpec({

    fun order(id: String) = EatsOrder(
        id = id, buyerId = "buyer_$id", restaurantId = "restaurant_$id",
        deliveryAddress = "addr", itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"),
        platformFee = BigDecimal("90"), totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_$id",
        status = EatsOrderStatus.PLACED,
    )

    Given("3 expired unaccepted orders, where cancelling the middle one fails") {
        val eatsOrderService = mockk<EatsOrderService>()
        val o1 = order("o1")
        val o2 = order("o2")
        val o3 = order("o3")
        every { eatsOrderService.getExpiredUnacceptedOrders() } returns listOf(o1, o2, o3)
        every { eatsOrderService.expireUnacceptedOrder(o1) } returns Unit
        every { eatsOrderService.expireUnacceptedOrder(o2) } throws RuntimeException("unexpected ledger error")
        every { eatsOrderService.expireUnacceptedOrder(o3) } returns Unit
        val scheduler = OrderAcceptanceExpiryScheduler(eatsOrderService)

        When("the sweep runs") {
            scheduler.run()

            Then("all 3 are still attempted -- the middle failure doesn't stop cancellation for the rest") {
                verify(exactly = 1) { eatsOrderService.expireUnacceptedOrder(o1) }
                verify(exactly = 1) { eatsOrderService.expireUnacceptedOrder(o2) }
                verify(exactly = 1) { eatsOrderService.expireUnacceptedOrder(o3) }
            }
        }
    }

    Given("no expired unaccepted orders") {
        val eatsOrderService = mockk<EatsOrderService>()
        every { eatsOrderService.getExpiredUnacceptedOrders() } returns emptyList()
        val scheduler = OrderAcceptanceExpiryScheduler(eatsOrderService)

        When("the sweep runs") {
            scheduler.run()

            Then("nothing is cancelled, no exception is thrown") {
                verify(exactly = 0) { eatsOrderService.expireUnacceptedOrder(any()) }
            }
        }
    }
})
