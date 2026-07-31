package rw.itunda.merchant

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.PaymentIntent
import rw.itunda.core.repository.MerchantRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional

/**
 * First test coverage for the real Kakao Pay 정액 QR (static/fixed merchant QR) -- see
 * MerchantStaticQrService's own doc comment for the full sourced account. Mirrors
 * FacePayServiceTest's own established "mock MerchantService directly, verify
 * delegation" pattern for this codebase's thin-wrapper-around-collect shape.
 */
class MerchantStaticQrServiceTest : BehaviorSpec({

    Given("a real active merchant with a real static QR code") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantService = mockk<MerchantService>()
        val service = MerchantStaticQrService(merchantRepository, merchantService)

        val merchant = Merchant(id = "merchant_1", ownerUserId = "owner_1", walletId = "wallet_1", businessName = "Kigali Diner", status = MerchantStatus.ACTIVE)
        every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)

        When("a real customer scans it and pays their own real chosen amount") {
            val intent = PaymentIntent(id = "pi_1", merchantId = "merchant_1", amount = BigDecimal("3000"), description = "Static QR payment", expiresAt = Instant.now().plusSeconds(900))
            every { merchantService.createIntent("merchant_1", BigDecimal("3000"), "Static QR payment") } returns intent
            every { merchantService.collect("payer_1", "pi_1", "STATIC_QR") } returns
                mapOf("transactionId" to "ledgertxn_1", "channel" to "STATIC_QR", "amount" to BigDecimal("3000"))

            val result = service.payByStaticQr("payer_1", "merchant_1", BigDecimal("3000"), null)

            Then("it real-creates an intent from the real customer-chosen amount, then delegates to collect with the real STATIC_QR channel") {
                result["channel"] shouldBe "STATIC_QR"
                verify(exactly = 1) { merchantService.createIntent("merchant_1", BigDecimal("3000"), "Static QR payment") }
                verify(exactly = 1) { merchantService.collect("payer_1", "pi_1", "STATIC_QR") }
            }
        }

        When("a real customer provides their own real description") {
            val intent = PaymentIntent(id = "pi_2", merchantId = "merchant_1", amount = BigDecimal("1500"), description = "Lunch", expiresAt = Instant.now().plusSeconds(900))
            every { merchantService.createIntent("merchant_1", BigDecimal("1500"), "Lunch") } returns intent
            every { merchantService.collect("payer_1", "pi_2", "STATIC_QR") } returns mapOf("channel" to "STATIC_QR")

            service.payByStaticQr("payer_1", "merchant_1", BigDecimal("1500"), "  Lunch  ")

            Then("it real-trims the real customer-provided description before creating the intent") {
                verify(exactly = 1) { merchantService.createIntent("merchant_1", BigDecimal("1500"), "Lunch") }
            }
        }

        When("paying a zero or negative amount") {
            Then("it throws InvalidStaticQrAmountException before ever looking up the merchant") {
                try {
                    service.payByStaticQr("payer_1", "merchant_1", BigDecimal.ZERO, null)
                    error("expected InvalidStaticQrAmountException")
                } catch (e: InvalidStaticQrAmountException) {
                    verify(exactly = 0) { merchantRepository.findById(any()) }
                }
            }
        }
    }

    Given("a real SUSPENDED merchant") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantService = mockk<MerchantService>()
        val service = MerchantStaticQrService(merchantRepository, merchantService)

        val merchant = Merchant(id = "merchant_2", ownerUserId = "owner_2", walletId = "wallet_2", businessName = "Suspended Shop", status = MerchantStatus.SUSPENDED)
        every { merchantRepository.findById("merchant_2") } returns Optional.of(merchant)

        When("a real customer tries to pay via its real static QR anyway") {
            Then("it throws MerchantNotFoundException, not a status-revealing error") {
                try {
                    service.payByStaticQr("payer_1", "merchant_2", BigDecimal("1000"), null)
                    error("expected MerchantNotFoundException")
                } catch (e: MerchantNotFoundException) {
                    verify(exactly = 0) { merchantService.createIntent(any(), any(), any()) }
                }
            }
        }
    }

    Given("a real nonexistent merchantId") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantService = mockk<MerchantService>()
        val service = MerchantStaticQrService(merchantRepository, merchantService)

        every { merchantRepository.findById("nope") } returns Optional.empty()

        When("attempting to pay it") {
            Then("it throws MerchantNotFoundException") {
                try {
                    service.payByStaticQr("payer_1", "nope", BigDecimal("1000"), null)
                    error("expected MerchantNotFoundException")
                } catch (e: MerchantNotFoundException) {
                    // expected
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
