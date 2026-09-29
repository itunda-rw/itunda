package rw.itunda.merchant

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.repository.MerchantRepository
import java.math.BigDecimal

/**
 * Real zero-test-coverage gap found live (2026-09-14, test-coverage sweep): this
 * whole class -- the real, live route MerchantDiscoveryController's nearby-merchants
 * endpoint calls -- had no test file at all.
 */
class MerchantDiscoveryServiceTest : BehaviorSpec({

    fun merchant(id: String, name: String, status: MerchantStatus, lat: Double?, lng: Double?, cashbackRate: BigDecimal? = null) = Merchant(
        id = id, ownerUserId = "owner_$id", accountId = "account_$id", businessName = name,
        status = status, latitude = lat, longitude = lng, cashbackRate = cashbackRate,
    )

    Given("a mix of active/inactive and geo-located/non-geo-located merchants") {
        val merchantRepository = mockk<MerchantRepository>()
        val service = MerchantDiscoveryService(merchantRepository)

        val nearActive = merchant("merchant_near", "Near Store", MerchantStatus.ACTIVE, -1.9536, 30.0605)
        val farActive = merchant("merchant_far", "Far Store", MerchantStatus.ACTIVE, -2.6, 29.7)
        val suspended = merchant("merchant_suspended", "Suspended Store", MerchantStatus.SUSPENDED, -1.9537, 30.0606)
        val noLocation = merchant("merchant_no_location", "No Location Store", MerchantStatus.ACTIVE, null, null)
        every { merchantRepository.findAll() } returns listOf(nearActive, farActive, suspended, noLocation)

        When("searching within a real 5km radius of central Kigali") {
            val result = service.nearby(-1.9536, 30.0605, 5.0)

            Then("only the real active, geo-located, in-radius merchant is returned") {
                result.size shouldBe 1
                result[0].id shouldBe "merchant_near"
            }
        }

        When("searching with an out-of-range radius") {
            Then("it throws IllegalArgumentException before ever querying merchants") {
                try {
                    service.nearby(-1.9536, 30.0605, 150.0)
                    error("expected IllegalArgumentException")
                } catch (e: IllegalArgumentException) {
                    // expected
                }
            }
        }

        When("searching from a coordinate outside Rwanda") {
            Then("it throws IllegalArgumentException before ever querying merchants") {
                try {
                    service.nearby(51.5072, -0.1276, 5.0)
                    error("expected IllegalArgumentException")
                } catch (e: IllegalArgumentException) {
                    // expected
                }
            }
        }
    }

    Given("a real merchant with no boosted cashback rate set") {
        val merchantRepository = mockk<MerchantRepository>()
        val service = MerchantDiscoveryService(merchantRepository)
        val plain = merchant("merchant_plain", "Plain Store", MerchantStatus.ACTIVE, -1.9536, 30.0605, cashbackRate = null)
        every { merchantRepository.findAll() } returns listOf(plain)

        When("searching nearby") {
            val result = service.nearby(-1.9536, 30.0605, 5.0)

            Then("it falls back to itunda's own real default cashback rate, not a fabricated zero") {
                result[0].cashbackRate shouldBe ShoppingCashbackService.DEFAULT_CASHBACK_RATE.toDouble()
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
