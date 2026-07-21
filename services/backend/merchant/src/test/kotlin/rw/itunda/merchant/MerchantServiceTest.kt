package rw.itunda.merchant

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.PaymentIntent
import rw.itunda.core.domain.PaymentIntentStatus
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.PaymentIntentRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
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
        // Not relaxed for save() specifically: mockk's relaxed default can't correctly
        // infer JpaRepository's generic `<S extends T> S save(S)` signature, returning a
        // raw mock Object that then fails a real ClassCastException back in the caller
        // (confirmed live) -- same reason WalletServiceTest explicitly stubs this too.
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        every { transactionRepository.save(any()) } answers { firstArg() }
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val demoCardAuthorizationService = DemoCardAuthorizationService()
        val shoppingCashbackService = mockk<ShoppingCashbackService>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = MerchantService(merchantRepository, paymentIntentRepository, walletRepository, ledgerService, webhookDeliveryService, transactionRepository, fraudRuleEngine, demoCardAuthorizationService, shoppingCashbackService, rateLimiter)

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
            Then("real Toss Shopping cashback is awarded for the real payer wallet and purchase amount") {
                verify(exactly = 1) { shoppingCashbackService.awardCashback(payerWallet, BigDecimal("5000"), "Kigali Coffee") }
                result.containsKey("cashbackEarned") shouldBe true
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

        When("the real cashback service itself fails during a collection") {
            val payerWallet = wallet("wallet_payer3", "payer_3")
            val intent = PaymentIntent(
                id = "pi_cashback_fail", merchantId = "merchant_1", amount = BigDecimal("2000"),
                description = "tea", expiresAt = Instant.now().plusSeconds(600),
            )
            every { paymentIntentRepository.findById("pi_cashback_fail") } returns Optional.of(intent)
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            every { walletRepository.findByUserIdAndType("payer_3", WalletType.MAIN) } returns payerWallet
            every { walletRepository.findById("wallet_merchant") } returns Optional.of(ownerWallet)
            every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("ledgertxn_cb_fail", emptyList())
            every { paymentIntentRepository.save(any()) } answers { firstArg() }
            every { shoppingCashbackService.awardCashback(any(), any(), any()) } throws RuntimeException("simulated cashback outage")

            val result = service.collect("payer_3", "pi_cashback_fail")

            Then("the real payment still succeeds -- an auxiliary cashback failure must never roll back real money already moved") {
                result["status"] shouldBe "COMPLETED"
                intent.status shouldBe PaymentIntentStatus.COMPLETED
                result["cashbackEarned"] shouldBe BigDecimal.ZERO
            }
        }

        When("charging a real Luhn-valid demo test card that authorizes") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
            every { walletRepository.findById("wallet_merchant") } returns Optional.of(ownerWallet)
            val legsSlot = slot<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_card_1", emptyList())

            val result = service.chargeCard(
                "owner_1", BigDecimal("8000"), "2x Coffee",
                DemoCardAuthorizationService.TEST_CARD_APPROVE, 12, 2030, "123",
            )

            Then("it posts real ledger legs from a real clearing account, not a fake payer wallet") {
                val clearingLeg = legsSlot.captured.first { it.accountType == LedgerAccountType.RAIL_SUSPENSE }
                clearingLeg.accountId shouldBe "card_network_clearing"
                clearingLeg.direction shouldBe LedgerDirection.DEBIT
                clearingLeg.amount shouldBe BigDecimal("8000")

                val merchantLeg = legsSlot.captured.first { it.accountType == LedgerAccountType.WALLET }
                merchantLeg.accountId shouldBe "wallet_merchant"
                merchantLeg.direction shouldBe LedgerDirection.CREDIT
                merchantLeg.amount shouldBe BigDecimal("7880.00")
            }
            Then("it saves a real CARD-channel Transaction and returns a real result") {
                result["channel"] shouldBe "CARD"
                result["status"] shouldBe "COMPLETED"
                verify(exactly = 1) { transactionRepository.save(any()) }
            }
        }

        When("charging a demo test card that declines") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
            every { walletRepository.findById("wallet_merchant") } returns Optional.of(ownerWallet)

            Then("it throws CardDeclinedException before ever touching the ledger -- a real decline must not move money") {
                try {
                    service.chargeCard(
                        "owner_1", BigDecimal("5000"), "Declined test",
                        DemoCardAuthorizationService.TEST_CARD_DECLINE_GENERIC, 12, 2030, "123",
                    )
                    error("expected CardDeclinedException")
                } catch (e: CardDeclinedException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                    verify(exactly = 0) { transactionRepository.save(any()) }
                }
            }
        }

        When("charging a card number that fails the real Luhn checksum") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
            every { walletRepository.findById("wallet_merchant") } returns Optional.of(ownerWallet)

            Then("it throws CardDeclinedException as an invalid card, never touching the ledger") {
                try {
                    service.chargeCard("owner_1", BigDecimal("5000"), "Bad card", "1234567812345678", 12, 2030, "123")
                    error("expected CardDeclinedException")
                } catch (e: CardDeclinedException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        // Real "Pay with itunda" external checkout API (2026-07-21) -- see
        // PaymentsApiController's own doc comment for the full account of the real Toss
        // Payments feature this mirrors.
        When("a merchant generates a real external-checkout API key") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
            val savedKeySlot = slot<Merchant>()
            every { merchantRepository.save(capture(savedKeySlot)) } answers { firstArg() }

            val rawKey1 = service.generateApiKey("owner_1")

            Then("it returns a real raw key and persists only its hash, never the raw value") {
                rawKey1.startsWith("sk_test_") shouldBe true
                savedKeySlot.captured.apiKeyHash shouldNotBe null
                savedKeySlot.captured.apiKeyHash shouldNotBe rawKey1
            }

            Then("regenerating produces a genuinely different key, rotating out the old one") {
                val rawKey2 = service.generateApiKey("owner_1")
                (rawKey1 == rawKey2) shouldBe false
            }
        }

        When("resolving a merchant by a real, valid API key") {
            val keyedMerchant = Merchant(
                id = "merchant_5", ownerUserId = "owner_5", walletId = "wallet_5",
                businessName = "External Shop", status = MerchantStatus.ACTIVE,
                apiKeyHash = "d1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
            )
            every { merchantRepository.findByApiKeyHash(any()) } returns keyedMerchant

            Then("it resolves the correct merchant") {
                service.resolveMerchantByApiKey("sk_test_anything").id shouldBe "merchant_5"
            }
        }

        When("resolving by an unknown API key") {
            every { merchantRepository.findByApiKeyHash(any()) } returns null

            Then("it real-throws InvalidApiKeyException rather than a null merchant slipping through") {
                try {
                    service.resolveMerchantByApiKey("sk_test_bogus")
                    error("expected InvalidApiKeyException")
                } catch (e: InvalidApiKeyException) {
                    // expected
                }
            }
        }

        When("resolving by a real key belonging to a suspended merchant") {
            val suspendedMerchant = Merchant(
                id = "merchant_6", ownerUserId = "owner_6", walletId = "wallet_6",
                businessName = "Suspended Shop", status = MerchantStatus.SUSPENDED,
                apiKeyHash = "hash6",
            )
            every { merchantRepository.findByApiKeyHash(any()) } returns suspendedMerchant

            Then("a valid key on a suspended account still real-fails, not silently succeeds") {
                try {
                    service.resolveMerchantByApiKey("sk_test_suspended")
                    error("expected InvalidApiKeyException")
                } catch (e: InvalidApiKeyException) {
                    // expected
                }
            }
        }

        When("creating a real external payment with successUrl/failUrl/orderId") {
            every { paymentIntentRepository.save(any()) } answers { firstArg() }

            val intent = service.createExternalPayment(
                merchant, BigDecimal("5000"), "Order #A1", "order_A1",
                "https://shop.example/success", "https://shop.example/fail",
            )

            Then("the real PaymentIntent carries every external field, unlike the in-app QR path which leaves them null") {
                intent.merchantId shouldBe "merchant_1"
                intent.orderId shouldBe "order_A1"
                intent.successUrl shouldBe "https://shop.example/success"
                intent.failUrl shouldBe "https://shop.example/fail"
                intent.status shouldBe PaymentIntentStatus.PENDING
            }
        }

        When("creating an external payment with a real invalid (non-positive) amount") {
            Then("it real-throws InvalidCheckoutRequestException before ever touching the repository") {
                try {
                    service.createExternalPayment(merchant, BigDecimal.ZERO, "Bad order", null, null, null)
                    error("expected InvalidCheckoutRequestException")
                } catch (e: InvalidCheckoutRequestException) {
                    verify(exactly = 0) { paymentIntentRepository.save(any()) }
                }
            }
        }

        When("a customer's browser requests the real public checkout info for a paymentKey") {
            val intent = PaymentIntent(
                id = "pi_checkout_1", merchantId = "merchant_1", amount = BigDecimal("2500"),
                description = "Order #B2", expiresAt = Instant.now().plusSeconds(900),
                successUrl = "https://shop.example/ok", failUrl = "https://shop.example/no",
            )
            every { paymentIntentRepository.findById("pi_checkout_1") } returns Optional.of(intent)
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)

            val info = service.getCheckoutInfo("pi_checkout_1")

            Then("it returns only real, safe-to-show fields -- the merchant's business name, never its internal id or webhook URL") {
                info.merchantName shouldBe "Kigali Coffee"
                info.amount shouldBe BigDecimal("2500")
                info.successUrl shouldBe "https://shop.example/ok"
                info.failUrl shouldBe "https://shop.example/no"
            }
        }

        When("one merchant's API key tries to read a payment that real-belongs to a different merchant") {
            val otherMerchantsIntent = PaymentIntent(
                id = "pi_other", merchantId = "merchant_999", amount = BigDecimal("1000"),
                description = "Not yours", expiresAt = Instant.now().plusSeconds(900),
            )
            every { paymentIntentRepository.findById("pi_other") } returns Optional.of(otherMerchantsIntent)

            Then("it real-404s rather than leaking another merchant's payment data") {
                try {
                    service.getPaymentStatusForMerchant(merchant, "pi_other")
                    error("expected PaymentIntentNotFoundException")
                } catch (e: PaymentIntentNotFoundException) {
                    // expected -- ownership check, not just "does this id exist"
                }
            }
        }
    }

    Given("a registered merchant with no webhook configured yet") {
        val merchantRepository = mockk<MerchantRepository>()
        val paymentIntentRepository = mockk<PaymentIntentRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val webhookDeliveryService = mockk<WebhookDeliveryService>(relaxed = true)
        // Not relaxed for save() specifically: mockk's relaxed default can't correctly
        // infer JpaRepository's generic `<S extends T> S save(S)` signature, returning a
        // raw mock Object that then fails a real ClassCastException back in the caller
        // (confirmed live) -- same reason WalletServiceTest explicitly stubs this too.
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        every { transactionRepository.save(any()) } answers { firstArg() }
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val demoCardAuthorizationService = DemoCardAuthorizationService()
        val shoppingCashbackService = mockk<ShoppingCashbackService>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = MerchantService(merchantRepository, paymentIntentRepository, walletRepository, ledgerService, webhookDeliveryService, transactionRepository, fraudRuleEngine, demoCardAuthorizationService, shoppingCashbackService, rateLimiter)

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

        When("registering a webhook URL longer than the real 500-char DB column bound") {
            Then("it throws InvalidWebhookUrlException rather than risking a raw DB insert failure") {
                try {
                    service.setWebhookUrl("owner_3", "https://example.com/" + "x".repeat(500))
                    error("expected InvalidWebhookUrlException")
                } catch (e: InvalidWebhookUrlException) {
                    // expected
                }
            }
        }
    }

    Given("a merchant with real collections spread across two days and two channels") {
        val merchantRepository = mockk<MerchantRepository>()
        val paymentIntentRepository = mockk<PaymentIntentRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val webhookDeliveryService = mockk<WebhookDeliveryService>(relaxed = true)
        val transactionRepository = mockk<TransactionRepository>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val demoCardAuthorizationService = DemoCardAuthorizationService()
        val shoppingCashbackService = mockk<ShoppingCashbackService>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = MerchantService(merchantRepository, paymentIntentRepository, walletRepository, ledgerService, webhookDeliveryService, transactionRepository, fraudRuleEngine, demoCardAuthorizationService, shoppingCashbackService, rateLimiter)

        val merchant = Merchant(id = "merchant_4", ownerUserId = "owner_4", walletId = "wallet_4", businessName = "Report Cafe")
        every { merchantRepository.findByOwnerUserId("owner_4") } returns merchant

        fun txn(id: String, day: LocalDate, amount: String, fee: String, channel: String) = Transaction(
            id = id, referenceNumber = "REF$id", senderId = "payer_x", recipientId = "owner_4",
            amount = BigDecimal(amount), fee = BigDecimal(fee), currency = "RWF",
            type = TransactionType.PAYMENT, status = TransactionStatus.COMPLETED,
            description = "QR collection", channel = channel,
            createdAt = day.atTime(10, 0).toInstant(ZoneOffset.UTC),
        )

        val day1 = LocalDate.of(2026, 7, 10)
        val day2 = LocalDate.of(2026, 7, 11)
        every {
            transactionRepository.findByRecipientIdAndTypeAndCreatedAtBetween("owner_4", TransactionType.PAYMENT, any(), any())
        } returns listOf(
            txn("t1", day1, "10000", "150.00", "QR"),
            txn("t2", day1, "5000", "75.00", "FACE_PAY"),
            txn("t3", day2, "20000", "300.00", "QR"),
        )

        When("requesting the report for that range") {
            val report = service.getReport("owner_4", day1, day2)

            Then("it groups by day with correct per-day totals and channel breakdown") {
                report.size shouldBe 2

                val reportDay1 = report.first { it.date == day1 }
                reportDay1.collectionCount shouldBe 2
                reportDay1.grossAmount shouldBe BigDecimal("15000")
                reportDay1.fees shouldBe BigDecimal("225.00")
                reportDay1.netAmount shouldBe BigDecimal("14775.00")
                reportDay1.byChannel shouldBe mapOf("QR" to 1, "FACE_PAY" to 1)

                val reportDay2 = report.first { it.date == day2 }
                reportDay2.collectionCount shouldBe 1
                reportDay2.grossAmount shouldBe BigDecimal("20000")
                reportDay2.fees shouldBe BigDecimal("300.00")
                reportDay2.netAmount shouldBe BigDecimal("19700.00")
                reportDay2.byChannel shouldBe mapOf("QR" to 1)
            }
        }

        When("requesting a report for an account that isn't a merchant") {
            every { merchantRepository.findByOwnerUserId("not_a_merchant") } returns null

            Then("it throws MerchantNotFoundException before ever querying transactions") {
                try {
                    service.getReport("not_a_merchant", day1, day2)
                    error("expected MerchantNotFoundException")
                } catch (e: MerchantNotFoundException) {
                    verify(exactly = 0) { transactionRepository.findByRecipientIdAndTypeAndCreatedAtBetween(any(), any(), any(), any()) }
                }
            }
        }

        When("requesting a report for a range with no collections") {
            every {
                transactionRepository.findByRecipientIdAndTypeAndCreatedAtBetween("owner_4", TransactionType.PAYMENT, any(), any())
            } returns emptyList()

            Then("it returns an empty list, not an error") {
                service.getReport("owner_4", day1, day1).size shouldBe 0
            }
        }
    }
}) {
    // Same reasoning as LedgerServiceTest.kt: fresh fixtures per leaf test so mutation
    // in one When (e.g. marking an intent EXPIRED) can't leak into a sibling test.
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
