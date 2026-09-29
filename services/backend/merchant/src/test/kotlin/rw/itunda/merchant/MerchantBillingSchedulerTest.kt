package rw.itunda.merchant

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.MerchantBillingSubscription
import java.time.Instant

/**
 * Real gap found and fixed 2026-09-05 (concurrency-audit continuation): unlike the
 * earlier scheduler audit's "does the ledger call have its own try/catch" check
 * (which correctly found `chargeOne` safe for that specific failure mode),
 * `MerchantBillingScheduler.run()`'s loop had no per-subscription try/catch of its
 * own -- if `chargeOne`'s FINAL `merchantBillingSubscriptionRepository.save(subscription)`
 * call throws (e.g. a genuine ObjectOptimisticLockingFailureException from a real
 * concurrent mutation, sitting outside chargeOne's own try/catch which only wraps the
 * ledger charge), that exception used to propagate uncaught out of the whole `run()`
 * tick, silently stopping every OTHER due subscription in the same batch from being
 * charged this cycle -- the exact "scheduler transaction-poisoning" bug class this
 * codebase has already found and fixed on several other schedulers.
 */
class MerchantBillingSchedulerTest : BehaviorSpec({

    fun subscription(id: String) = MerchantBillingSubscription(
        id = id, planId = "plan_1", merchantId = "merchant_1", customerId = "customer_$id",
        nextChargeAt = Instant.now(),
    )

    Given("3 due subscriptions, where charging the middle one fails") {
        val merchantBillingService = mockk<MerchantBillingService>()
        val s1 = subscription("s1")
        val s2 = subscription("s2")
        val s3 = subscription("s3")
        every { merchantBillingService.getDueForExecution() } returns listOf(s1, s2, s3)
        every { merchantBillingService.chargeOne(s1) } returns true
        every { merchantBillingService.chargeOne(s2) } throws RuntimeException("optimistic lock failure")
        every { merchantBillingService.chargeOne(s3) } returns true
        val scheduler = MerchantBillingScheduler(merchantBillingService)

        When("the sweep runs") {
            scheduler.run()

            Then("all 3 are still attempted -- the middle failure doesn't stop charging the rest") {
                verify(exactly = 1) { merchantBillingService.chargeOne(s1) }
                verify(exactly = 1) { merchantBillingService.chargeOne(s2) }
                verify(exactly = 1) { merchantBillingService.chargeOne(s3) }
            }
        }
    }

    Given("no due subscriptions") {
        val merchantBillingService = mockk<MerchantBillingService>()
        every { merchantBillingService.getDueForExecution() } returns emptyList()
        val scheduler = MerchantBillingScheduler(merchantBillingService)

        When("the sweep runs") {
            scheduler.run()

            Then("nothing is charged, no exception is thrown") {
                verify(exactly = 0) { merchantBillingService.chargeOne(any()) }
            }
        }
    }
})
