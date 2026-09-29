package rw.itunda.commerce

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.ProductSubscription
import java.time.Instant

/**
 * Real gap found and fixed 2026-09-05 (concurrency-audit continuation, same class as
 * MerchantBillingScheduler's identical fix): `executeOne`'s FINAL
 * `productSubscriptionRepository.save(subscription)` call sits outside its own
 * try/catch (which only wraps the real order-placement call), and this loop had no
 * per-subscription try/catch of its own either -- a genuine failure there used to
 * propagate uncaught and silently stop delivering every OTHER due subscription in
 * the same tick.
 */
class ProductSubscriptionSchedulerTest : BehaviorSpec({

    fun subscription(id: String) = ProductSubscription(
        id = id, customerId = "customer_$id", merchantId = "merchant_1", productId = "product_1",
        quantity = 1, intervalDays = 30, deliveryAddress = "123 Main St", nextDeliveryAt = Instant.now(),
    )

    Given("3 due subscriptions, where delivering the middle one fails") {
        val productSubscriptionService = mockk<ProductSubscriptionService>()
        val s1 = subscription("s1")
        val s2 = subscription("s2")
        val s3 = subscription("s3")
        every { productSubscriptionService.getDueForExecution() } returns listOf(s1, s2, s3)
        every { productSubscriptionService.executeOne(s1) } returns true
        every { productSubscriptionService.executeOne(s2) } throws RuntimeException("optimistic lock failure")
        every { productSubscriptionService.executeOne(s3) } returns true
        val scheduler = ProductSubscriptionScheduler(productSubscriptionService)

        When("the sweep runs") {
            scheduler.run()

            Then("all 3 are still attempted -- the middle failure doesn't stop delivering the rest") {
                verify(exactly = 1) { productSubscriptionService.executeOne(s1) }
                verify(exactly = 1) { productSubscriptionService.executeOne(s2) }
                verify(exactly = 1) { productSubscriptionService.executeOne(s3) }
            }
        }
    }

    Given("no due subscriptions") {
        val productSubscriptionService = mockk<ProductSubscriptionService>()
        every { productSubscriptionService.getDueForExecution() } returns emptyList()
        val scheduler = ProductSubscriptionScheduler(productSubscriptionService)

        When("the sweep runs") {
            scheduler.run()

            Then("nothing is delivered, no exception is thrown") {
                verify(exactly = 0) { productSubscriptionService.executeOne(any()) }
            }
        }
    }
})
