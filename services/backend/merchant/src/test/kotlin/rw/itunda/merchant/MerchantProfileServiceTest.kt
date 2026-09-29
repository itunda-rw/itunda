package rw.itunda.merchant

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.repository.MerchantRepository

// Extracted from MerchantServiceTest.kt (itunda Maps redesign, 2026-08-28) -- moved
// alongside the real MerchantProfileService source extraction (see that class's own
// doc comment) so tests stay colocated with the code they test.
class MerchantProfileServiceTest : BehaviorSpec({

    Given("a registered merchant") {
        val merchantRepository = mockk<MerchantRepository>()
        val service = MerchantProfileService(merchantRepository)

        val merchant = Merchant(
            id = "merchant_1", ownerUserId = "owner_1", accountId = "account_merchant",
            businessName = "Kigali Coffee", status = MerchantStatus.ACTIVE,
        )

        When("a real merchant opts into 배달의민족 예약주문 (scheduled orders)") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
            every { merchantRepository.save(any()) } answers { firstArg() }

            val result = service.setAcceptsScheduledOrders("owner_1", true)

            Then("it real-flips the flag") {
                result.acceptsScheduledOrders shouldBe true
            }
        }

        When("a real merchant temporarily pauses accepting orders (Baemin CEO app 영업일시중지)") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
            every { merchantRepository.save(any()) } answers { firstArg() }

            val result = service.setAcceptingOrders("owner_1", false)

            Then("it real-flips the flag") {
                result.isAcceptingOrders shouldBe false
            }
        }

        When("a real merchant sets a recurring weekly closed-day schedule (Baemin CEO app 휴무일 설정)") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
            every { merchantRepository.save(any()) } answers { firstArg() }

            val result = service.setClosedWeekdays("owner_1", setOf(7, 1))

            Then("it stores the real weekdays sorted, comma-separated") {
                result.closedWeekdays shouldBe "1,7"
            }
        }

        When("a real merchant clears their closed-day schedule") {
            merchant.closedWeekdays = "6,7"
            every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
            every { merchantRepository.save(any()) } answers { firstArg() }

            val result = service.setClosedWeekdays("owner_1", emptySet())

            Then("it real-clears the field back to null, not an empty string") {
                result.closedWeekdays shouldBe null
            }
        }

        When("setting an out-of-range weekday") {
            Then("it rejects the request before touching the merchant") {
                try {
                    service.setClosedWeekdays("owner_1", setOf(8))
                    error("expected InvalidClosedWeekdaysException")
                } catch (e: InvalidClosedWeekdaysException) {
                    verify(exactly = 0) { merchantRepository.save(any()) }
                }
            }
        }

        When("setting a real valid location") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
            every { merchantRepository.save(any()) } answers { firstArg() }

            val result = service.setLocation("owner_1", -1.9441, 30.0619)

            Then("it saves the real coordinates") {
                result.latitude shouldBe -1.9441
                result.longitude shouldBe 30.0619
            }
        }

        When("setting an out-of-range location") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant

            Then("it throws InvalidCoordinatesException") {
                try {
                    service.setLocation("owner_1", 999.0, 30.0)
                    error("expected InvalidCoordinatesException")
                } catch (e: InvalidCoordinatesException) {
                    // expected
                }
            }
        }

        When("setting a real valid category") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
            every { merchantRepository.save(any()) } answers { firstArg() }

            val result = service.setCategory("owner_1", "  Rwandan  ")

            Then("it saves the trimmed category") {
                result.category shouldBe "Rwandan"
            }
        }

        When("setting a blank category") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant

            Then("it throws InvalidCategoryException") {
                try {
                    service.setCategory("owner_1", "   ")
                    error("expected InvalidCategoryException")
                } catch (e: InvalidCategoryException) {
                    // expected
                }
            }
        }

        When("setting a category longer than 64 characters") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant

            Then("it throws InvalidCategoryException") {
                try {
                    service.setCategory("owner_1", "x".repeat(65))
                    error("expected InvalidCategoryException")
                } catch (e: InvalidCategoryException) {
                    // expected
                }
            }
        }

        When("setting real photo gallery URLs, including a duplicate and a blank entry") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
            every { merchantRepository.save(any()) } answers { firstArg() }

            val result = service.setPhotoUrls("owner_1", listOf(" https://a.jpg ", "https://b.jpg", "  "))

            Then("it trims, drops the blank entry, and stores the real URLs comma-joined") {
                result.photoUrls shouldBe "https://a.jpg,https://b.jpg"
                result.photoUrlList() shouldBe listOf("https://a.jpg", "https://b.jpg")
            }
        }

        When("setting more than 20 real photo URLs") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant

            Then("it throws InvalidPhotoUrlException before ever touching the merchant") {
                try {
                    service.setPhotoUrls("owner_1", (1..21).map { "https://photo$it.jpg" })
                    error("expected InvalidPhotoUrlException")
                } catch (e: InvalidPhotoUrlException) {
                    verify(exactly = 0) { merchantRepository.save(any()) }
                }
            }
        }

        When("clearing the real photo gallery back to empty") {
            merchant.photoUrls = "https://a.jpg"
            every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
            every { merchantRepository.save(any()) } answers { firstArg() }

            val result = service.setPhotoUrls("owner_1", emptyList())

            Then("it real-clears the field back to null, not an empty string") {
                result.photoUrls shouldBe null
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
