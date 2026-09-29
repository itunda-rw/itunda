package rw.itunda.merchant

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.MerchantLoyaltyAccount
import java.math.BigDecimal

/**
 * Same loop-resilience contract every other per-item scheduler in this codebase
 * proves -- see MerchantLoyaltyPointsExpiryScheduler's own doc comment.
 */
class MerchantLoyaltyPointsExpiryTest : BehaviorSpec({
    fun account(id: String) = MerchantLoyaltyAccount(id = id, merchantId = "merchant_1", customerId = "cust_$id", pointBalance = BigDecimal("500"))

    Given("3 dormant loyalty accounts due for expiry, where the middle one's expiry fails") {
        val service = mockk<MerchantLoyaltyPointsService>()
        every { service.getExpirableAccounts() } returns listOf(account("lp_1"), account("lp_2"), account("lp_3"))
        every { service.expireIfDue("lp_1") } returns Unit
        every { service.expireIfDue("lp_2") } throws IllegalStateException("optimistic lock conflict")
        every { service.expireIfDue("lp_3") } returns Unit
        val scheduler = MerchantLoyaltyPointsExpiryScheduler(service)

        When("the scheduler sweeps due accounts") {
            scheduler.run()

            Then("all 3 accounts are still attempted, not just the ones before the failure") {
                verify(exactly = 1) { service.expireIfDue("lp_1") }
                verify(exactly = 1) { service.expireIfDue("lp_2") }
                verify(exactly = 1) { service.expireIfDue("lp_3") }
            }
        }
    }

    Given("no dormant accounts") {
        val service = mockk<MerchantLoyaltyPointsService>()
        every { service.getExpirableAccounts() } returns emptyList()
        val scheduler = MerchantLoyaltyPointsExpiryScheduler(service)

        When("the scheduler sweeps due accounts") {
            scheduler.run()

            Then("it does nothing") {
                verify(exactly = 0) { service.expireIfDue(any()) }
            }
        }
    }
})
