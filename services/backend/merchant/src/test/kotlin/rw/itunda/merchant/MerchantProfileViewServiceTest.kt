package rw.itunda.merchant

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantProfileView
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.repository.MerchantProfileViewRepository
import rw.itunda.core.repository.MerchantRepository
import java.time.LocalDate

class MerchantProfileViewServiceTest : BehaviorSpec({

    Given("a real consumer opening a merchant's place-detail") {
        val merchantProfileViewRepository = mockk<MerchantProfileViewRepository>(relaxed = true)
        val merchantRepository = mockk<MerchantRepository>()
        val service = MerchantProfileViewService(merchantProfileViewRepository, merchantRepository)

        When("recording the real view") {
            service.recordView("merchant_1")

            Then("it real-upserts today's row for that merchant with a deterministic id") {
                val today = LocalDate.now()
                verify(exactly = 1) { merchantProfileViewRepository.upsertView("merchant_profile_view_merchant_1_$today", "merchant_1", today) }
            }
        }
    }

    Given("a real merchant owner with a real 7-day visit trend") {
        val merchantProfileViewRepository = mockk<MerchantProfileViewRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val service = MerchantProfileViewService(merchantProfileViewRepository, merchantRepository)

        val merchant = Merchant(id = "merchant_1", ownerUserId = "owner_1", accountId = "account_1", businessName = "Kigali Diner", status = MerchantStatus.ACTIVE)
        val today = LocalDate.now()
        val rows = listOf(MerchantProfileView(id = "v1", merchantId = "merchant_1", viewDate = today, viewCount = 5))

        every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
        every { merchantProfileViewRepository.findByMerchantIdAndViewDateBetweenOrderByViewDateAsc("merchant_1", today.minusDays(6), today) } returns rows

        When("fetching the real trend for the default 7-day window") {
            Then("it resolves the caller's own merchant and returns its real daily rows") {
                service.getTrend("owner_1") shouldBe rows
            }
        }
    }

    Given("a caller who does not own a real merchant") {
        val merchantProfileViewRepository = mockk<MerchantProfileViewRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val service = MerchantProfileViewService(merchantProfileViewRepository, merchantRepository)

        every { merchantRepository.findByOwnerUserId("not_a_merchant") } returns null

        When("fetching the real trend") {
            Then("it throws MerchantNotFoundException instead of a fabricated empty trend") {
                try {
                    service.getTrend("not_a_merchant")
                    error("expected MerchantNotFoundException")
                } catch (e: MerchantNotFoundException) {
                    // expected
                }
            }
        }
    }
})
