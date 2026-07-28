package rw.itunda.commerce

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantProduct
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.ProductInquiry
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.ProductInquiryRepository
import java.math.BigDecimal
import java.util.Optional

class ProductInquiryServiceTest : BehaviorSpec({

    Given("a real shopper asking a real pre-purchase question about a product") {
        val productInquiryRepository = mockk<ProductInquiryRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = ProductInquiryService(productInquiryRepository, merchantProductRepository, merchantRepository, notificationRepository, pushNotificationService)

        val product = MerchantProduct(id = "product_1", merchantId = "merchant_1", name = "Widget", price = BigDecimal("2000"))
        val merchant = Merchant(id = "merchant_1", ownerUserId = "owner_1", walletId = "wallet_1", businessName = "Kigali Shop", status = MerchantStatus.ACTIVE)

        When("asking a real question -- no order or purchase involved at all") {
            every { merchantProductRepository.findById("product_1") } returns Optional.of(product)
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            every { productInquiryRepository.save(any()) } answers { firstArg() }
            every { notificationRepository.save(any()) } answers { firstArg() }

            val inquiry = service.askQuestion("shopper_1", "product_1", "  Is this available in blue?  ")

            Then("it real-trims the question and resolves the real merchant from the product") {
                inquiry.buyerId shouldBe "shopper_1"
                inquiry.merchantId shouldBe "merchant_1"
                inquiry.question shouldBe "Is this available in blue?"
                inquiry.answer shouldBe null
            }

            Then("it real-alerts the real merchant owner of the new question") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "owner_1" && it.type == "NEW_PRODUCT_INQUIRY" }) }
            }
        }

        When("asking about a product that doesn't exist") {
            every { merchantProductRepository.findById("ghost") } returns Optional.empty()

            Then("it throws OrderProductNotFoundException") {
                try {
                    service.askQuestion("shopper_1", "ghost", "hi")
                    error("expected OrderProductNotFoundException")
                } catch (e: OrderProductNotFoundException) {
                    // expected
                }
            }
        }

        When("asking an empty question") {
            Then("it throws InvalidProductInquiryException before ever touching the product") {
                try {
                    service.askQuestion("shopper_1", "product_1", "   ")
                    error("expected InvalidProductInquiryException")
                } catch (e: InvalidProductInquiryException) {
                    verify(exactly = 0) { merchantProductRepository.findById(any()) }
                }
            }
        }
    }

    Given("a real merchant owner answering a real question about their own product") {
        val productInquiryRepository = mockk<ProductInquiryRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = ProductInquiryService(productInquiryRepository, merchantProductRepository, merchantRepository, notificationRepository, pushNotificationService)

        val merchant = Merchant(id = "merchant_1", ownerUserId = "owner_1", walletId = "wallet_1", businessName = "Kigali Shop", status = MerchantStatus.ACTIVE)
        val inquiry = ProductInquiry(id = "product_inquiry_1", productId = "product_1", merchantId = "merchant_1", buyerId = "shopper_1", question = "Is this in blue?")

        When("the real owner answers") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
            every { productInquiryRepository.findById("product_inquiry_1") } returns Optional.of(inquiry)
            every { productInquiryRepository.save(any()) } answers { firstArg() }
            every { notificationRepository.save(any()) } answers { firstArg() }

            val result = service.answerQuestion("owner_1", "product_inquiry_1", "  Yes, blue is in stock!  ")

            Then("it real-trims and saves the answer with a timestamp") {
                result.answer shouldBe "Yes, blue is in stock!"
                (result.answeredAt != null) shouldBe true
            }

            Then("it real-notifies the real asking shopper") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "shopper_1" && it.type == "PRODUCT_INQUIRY_ANSWERED" }) }
            }
        }

        When("someone who isn't the real merchant owner tries to answer") {
            every { merchantRepository.findByOwnerUserId("stranger") } returns null

            Then("it throws MerchantNotFoundException") {
                try {
                    service.answerQuestion("stranger", "product_inquiry_1", "hi")
                    error("expected MerchantNotFoundException")
                } catch (e: MerchantNotFoundException) {
                    // expected
                }
            }
        }

        When("a different merchant's owner tries to answer this question") {
            val otherMerchant = Merchant(id = "merchant_2", ownerUserId = "owner_2", walletId = "wallet_2", businessName = "Other Shop", status = MerchantStatus.ACTIVE)
            every { merchantRepository.findByOwnerUserId("owner_2") } returns otherMerchant
            every { productInquiryRepository.findById("product_inquiry_1") } returns Optional.of(inquiry)

            Then("it throws ProductInquiryNotFoundException, not a 403 that would confirm the question exists") {
                try {
                    service.answerQuestion("owner_2", "product_inquiry_1", "hi")
                    error("expected ProductInquiryNotFoundException")
                } catch (e: ProductInquiryNotFoundException) {
                    // expected
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
