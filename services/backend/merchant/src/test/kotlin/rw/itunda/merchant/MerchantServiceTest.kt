package rw.itunda.merchant

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.PaymentIntent
import rw.itunda.core.domain.PaymentIntentStatus
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.PaymentIntentRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional

/**
 * First test coverage for the merchant module (previously zero, like the ledger
 * before LedgerServiceTest.kt). Same Kotest + MockK convention. LedgerService itself
 * is mocked, not exercised for real -- its own balance/imbalance math is already
 * covered by LedgerServiceTest; this file is about merchant-specific business logic:
 * fee calculation, ownership checks, expiry, and double-collection prevention.
 */
class MerchantServiceTest : BehaviorSpec({

    fun wallet(id: String, userId: String) = Wallet(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test wallet",
        type = WalletType.MAIN, balance = BigDecimal("100000"), availableBalance = BigDecimal("100000"),
    )

    Given("a registered merchant with a settlement wallet") {
        val merchantRepository = mockk<MerchantRepository>()
        val paymentIntentRepository = mockk<PaymentIntentRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val webhookDeliveryService = mockk<WebhookDeliveryService>(relaxed = true)
        val service = MerchantService(merchantRepository, paymentIntentRepository, walletRepository, ledgerService, webhookDeliveryService)

        val ownerWallet = wallet("wallet_merchant", "owner_1")
        val merchant = Merchant(
            id = "merchant_1", ownerUserId = "owner_1", walletId = "wallet_merchant",
            businessName = "Kigali Coffee", status = MerchantStatus.ACTIVE,
        )

        When("registering a new merchant") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns null
            every { walletRepository.findByUserIdAndType("owner_1", WalletType.MAIN) } returns ownerWallet
            every { merchantRepository.save(any()) } answers { firstArg() }

            val result = service.register("owner_1", "Kigali Coffee")

            Then("it reuses the owner's existing wallet as the settlement wallet") {
                result.walletId shouldBe "wallet_merchant"
                result.ownerUserId shouldBe "owner_1"
                result.status shouldBe MerchantStatus.ACTIVE
            }
        }

        When("registering an account that's already a merchant") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant

            Then("it throws MerchantAlreadyRegisteredException rather than creating a duplicate") {
                try {
                    service.register("owner_1", "Kigali Coffee 2")
                    error("expected MerchantAlreadyRegisteredException")
                } catch (e: MerchantAlreadyRegisteredException) {
                    // expected
                }
            }
        }

        When("registering an account with no wallet") {
            every { merchantRepository.findByOwnerUserId("owner_2") } returns null
            every { walletRepository.findByUserIdAndType("owner_2", WalletType.MAIN) } returns null

            Then("it throws MerchantNoWalletException") {
                try {
                    service.register("owner_2", "No Wallet Shop")
                    error("expected MerchantNoWalletException")
                } catch (e: MerchantNoWalletException) {
                    // expected
                }
            }
        }

        When("generating a QR payment intent") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
            every { paymentIntentRepository.save(any()) } answers { firstArg() }

            val intent = service.generateQr("owner_1", BigDecimal("5000"), "2 espresso")

            Then("it's pending, tied to the merchant, and expires 15 minutes out") {
                intent.merchantId shouldBe "merchant_1"
                intent.amount shouldBe BigDecimal("5000")
                intent.status shouldBe PaymentIntentStatus.PENDING
                (intent.expiresAt.epochSecond - intent.createdAt.epochSecond) shouldBe 900L
            }
        }

        When("collecting a valid, pending payment intent") {
            val payerWallet = wallet("wallet_payer", "payer_1")
            val intent = PaymentIntent(
                id = "pi_1", merchantId = "merchant_1", amount = BigDecimal("5000"),
                description = "2 espresso", expiresAt = Instant.now().plusSeconds(600),
            )
            val legsSlot = slot<List<LedgerLeg>>()

            every { paymentIntentRepository.findById("pi_1") } returns Optional.of(intent)
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            every { walletRepository.findByUserIdAndType("payer_1", WalletType.MAIN) } returns payerWallet
            every { walletRepository.findById("wallet_merchant") } returns Optional.of(ownerWallet)
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns
                LedgerPostResult("ledgertxn_test", emptyList())
            every { paymentIntentRepository.save(any()) } answers { firstArg() }

            val result = service.collect("payer_1", "pi_1")

            Then("it splits a 1.5% fee to fee_revenue and the rest to the merchant") {
                val legs = legsSlot.captured
                legs.size shouldBe 3
                val payerLeg = legs.first { it.accountId == "wallet_payer" }
                val merchantLeg = legs.first { it.accountId == "wallet_merchant" }
                val feeLeg = legs.first { it.accountId == "fee_revenue" }

                payerLeg.direction shouldBe LedgerDirection.DEBIT
                payerLeg.amount shouldBe BigDecimal("5000")
                feeLeg.direction shouldBe LedgerDirection.CREDIT
                feeLeg.accountType shouldBe LedgerAccountType.FEE_REVENUE
                feeLeg.amount shouldBe BigDecimal("75.00")
                merchantLeg.direction shouldBe LedgerDirection.CREDIT
                merchantLeg.amount shouldBe BigDecimal("4925.00")
                // Balanced: one 5000 debit == 4925.00 + 75.00 credits.
                (merchantLeg.amount + feeLeg.amount) shouldBe payerLeg.amount.setScale(2)
            }
            Then("the intent is marked completed and tied to the real ledger transaction") {
                result["status"] shouldBe "COMPLETED"
                result["transactionId"] shouldBe "ledgertxn_test"
                intent.status shouldBe PaymentIntentStatus.COMPLETED
                intent.paidByUserId shouldBe "payer_1"
                intent.completedTransactionId shouldBe "ledgertxn_test"
            }
        }

        When("collecting an already-completed payment intent") {
            val paidIntent = PaymentIntent(
                id = "pi_2", merchantId = "merchant_1", amount = BigDecimal("1000"), description = "x",
                expiresAt = Instant.now().plusSeconds(600), status = PaymentIntentStatus.COMPLETED,
            )
            every { paymentIntentRepository.findById("pi_2") } returns Optional.of(paidIntent)

            Then("it throws PaymentIntentNotPayableException, not a second collection") {
                try {
                    service.collect("payer_1", "pi_2")
                    error("expected PaymentIntentNotPayableException")
                } catch (e: PaymentIntentNotPayableException) {
                    // expected -- no ledger call should have happened
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("collecting an expired payment intent") {
            val expiredIntent = PaymentIntent(
                id = "pi_3", merchantId = "merchant_1", amount = BigDecimal("1000"), description = "x",
                expiresAt = Instant.now().minusSeconds(60),
            )
            every { paymentIntentRepository.findById("pi_3") } returns Optional.of(expiredIntent)
            every { paymentIntentRepository.save(any()) } answers { firstArg() }

            Then("it throws PaymentIntentNotPayableException and marks the intent EXPIRED") {
                try {
                    service.collect("payer_1", "pi_3")
                    error("expected PaymentIntentNotPayableException")
                } catch (e: PaymentIntentNotPayableException) {
                    expiredIntent.status shouldBe PaymentIntentStatus.EXPIRED
                }
            }
        }

        When("the merchant's own owner tries to pay their own QR code") {
            val selfIntent = PaymentIntent(
                id = "pi_4", merchantId = "merchant_1", amount = BigDecimal("1000"), description = "x",
                expiresAt = Instant.now().plusSeconds(600),
            )
            every { paymentIntentRepository.findById("pi_4") } returns Optional.of(selfIntent)
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)

            Then("it throws SelfPaymentException before touching any wallet") {
                try {
                    service.collect("owner_1", "pi_4")
                    error("expected SelfPaymentException")
                } catch (e: SelfPaymentException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("collecting a payment code that doesn't exist") {
            every { paymentIntentRepository.findById("nope") } returns Optional.empty()

            Then("it throws PaymentIntentNotFoundException") {
                try {
                    service.collect("payer_1", "nope")
                    error("expected PaymentIntentNotFoundException")
                } catch (e: PaymentIntentNotFoundException) {
                    // expected
                }
            }
        }

        When("the merchant has a real webhook URL registered and a payment is collected") {
            val hookedMerchant = Merchant(
                id = "merchant_2", ownerUserId = "owner_2", walletId = "wallet_merchant2",
                businessName = "Hooked Cafe", status = MerchantStatus.ACTIVE, webhookUrl = "https://merchant.example/hooks",
            )
            val payerWallet = wallet("wallet_payer2", "payer_2")
            val intent = PaymentIntent(
                id = "pi_hook", merchantId = "merchant_2", amount = BigDecimal("1000"),
                description = "coffee", expiresAt = Instant.now().plusSeconds(600),
            )
            every { paymentIntentRepository.findById("pi_hook") } returns Optional.of(intent)
            every { merchantRepository.findById("merchant_2") } returns Optional.of(hookedMerchant)
            every { walletRepository.findByUserIdAndType("payer_2", WalletType.MAIN) } returns payerWallet
            every { walletRepository.findById("wallet_merchant2") } returns Optional.of(wallet("wallet_merchant2", "owner_2"))
            every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("ledgertxn_hook", emptyList())
            every { paymentIntentRepository.save(any()) } answers { firstArg() }

            service.collect("payer_2", "pi_hook")

            Then("real webhook delivery is attempted with the merchant's registered URL") {
                verify(exactly = 1) { webhookDeliveryService.deliverPaymentStatusChanged("https://merchant.example/hooks", any()) }
            }
        }
    }

    Given("a registered merchant with no webhook configured yet") {
        val merchantRepository = mockk<MerchantRepository>()
        val paymentIntentRepository = mockk<PaymentIntentRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val webhookDeliveryService = mockk<WebhookDeliveryService>(relaxed = true)
        val service = MerchantService(merchantRepository, paymentIntentRepository, walletRepository, ledgerService, webhookDeliveryService)

        val merchant = Merchant(id = "merchant_3", ownerUserId = "owner_3", walletId = "wallet_3", businessName = "Test Shop")
        every { merchantRepository.findByOwnerUserId("owner_3") } returns merchant
        every { merchantRepository.save(any()) } answers { firstArg() }

        When("registering a webhook URL") {
            val updated = service.setWebhookUrl("owner_3", "https://myshop.example/webhooks/itunda")

            Then("it's saved onto the real merchant record") {
                updated.webhookUrl shouldBe "https://myshop.example/webhooks/itunda"
                verify(exactly = 1) { merchantRepository.save(merchant) }
            }
        }
    }
}) {
    // Same reasoning as LedgerServiceTest.kt: fresh fixtures per leaf test so mutation
    // in one When (e.g. marking an intent EXPIRED) can't leak into a sibling test.
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
