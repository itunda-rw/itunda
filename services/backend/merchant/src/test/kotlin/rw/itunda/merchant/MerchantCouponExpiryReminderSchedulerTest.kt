package rw.itunda.merchant

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.CouponDiscountType
import rw.itunda.core.domain.MerchantCoupon
import java.math.BigDecimal
import java.time.Instant

/**
 * First test coverage for MerchantCouponExpiryReminderScheduler -- and a real,
 * previously-live bug fix, not just a missing test. This file's own doc comment
 * claimed to already avoid the "transaction-poisoning pitfall," but that claim
 * addressed a different concern (self-invocation) than the one that actually
 * applied: `sendExpiryReminder` had no try/catch of its own, and this loop had none
 * either. Fixed alongside this test (same commit) with a per-coupon try/catch.
 */
class MerchantCouponExpiryReminderSchedulerTest : BehaviorSpec({

    fun coupon(id: String) = MerchantCoupon(
        id = id, merchantId = "merchant_$id", title = "10% off", discountType = CouponDiscountType.PERCENT,
        discountValue = BigDecimal.TEN, active = true, expiresAt = Instant.now().plusSeconds(3600),
    )

    Given("3 coupons due for an expiry reminder, where sending the middle one fails") {
        val merchantCouponService = mockk<MerchantCouponService>()
        val c1 = coupon("c1")
        val c2 = coupon("c2")
        val c3 = coupon("c3")
        every { merchantCouponService.getCouponsDueForExpiryReminder() } returns listOf(c1, c2, c3)
        every { merchantCouponService.sendExpiryReminder("c1") } returns Unit
        every { merchantCouponService.sendExpiryReminder("c2") } throws RuntimeException("messaging service unreachable")
        every { merchantCouponService.sendExpiryReminder("c3") } returns Unit
        val scheduler = MerchantCouponExpiryReminderScheduler(merchantCouponService)

        When("the sweep runs") {
            val processed = scheduler.processDue()

            Then("all 3 are still attempted -- the middle failure doesn't stop the rest") {
                verify(exactly = 1) { merchantCouponService.sendExpiryReminder("c1") }
                verify(exactly = 1) { merchantCouponService.sendExpiryReminder("c2") }
                verify(exactly = 1) { merchantCouponService.sendExpiryReminder("c3") }
            }
            Then("the real due count still reflects all 3, regardless of the middle failure") {
                processed shouldBe 3
            }
        }
    }

    Given("no coupons due for a reminder") {
        val merchantCouponService = mockk<MerchantCouponService>()
        every { merchantCouponService.getCouponsDueForExpiryReminder() } returns emptyList()
        val scheduler = MerchantCouponExpiryReminderScheduler(merchantCouponService)

        When("the sweep runs") {
            scheduler.run()

            Then("nothing is sent, no exception is thrown") {
                verify(exactly = 0) { merchantCouponService.sendExpiryReminder(any()) }
            }
        }
    }
})
