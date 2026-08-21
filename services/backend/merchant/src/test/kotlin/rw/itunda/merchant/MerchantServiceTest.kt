package rw.itunda.merchant

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.persistence.Version
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.LedgerEntry
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.PaymentIntent
import rw.itunda.core.domain.PaymentIntentStatus
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.CustomerPaymentCodeRepository
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.PaymentIntentRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.AccountRepository
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
    Given("the payment-intent state machine") {
        Then("it is versioned so stale concurrent transitions cannot silently overwrite one another") {
            PaymentIntent::class.java.getDeclaredField("version").getAnnotation(Version::class.java) shouldNotBe null
        }
    }


    fun account(id: String, userId: String) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = AccountType.MAIN, balance = BigDecimal("100000"), availableBalance = BigDecimal("100000"),
    )

    Given("a registered merchant with a settlement account") {
        val merchantRepository = mockk<MerchantRepository>()
        val paymentIntentRepository = mockk<PaymentIntentRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val webhookDeliveryService = mockk<WebhookDeliveryService>(relaxed = true)
        // Not relaxed for save() specifically: mockk's relaxed default can't correctly
        // infer JpaRepository's generic `<S extends T> S save(S)` signature, returning a
        // raw mock Object that then fails a real ClassCastException back in the caller
        // (confirmed live) -- same reason AccountServiceTest explicitly stubs this too.
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        every { transactionRepository.save(any()) } answers { firstArg() }
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val demoCardAuthorizationService = DemoCardAuthorizationService()
        val shoppingCashbackService = mockk<ShoppingCashbackService>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val merchantCouponService = mockk<MerchantCouponService>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val customerPaymentCodeRepository = mockk<CustomerPaymentCodeRepository>(relaxed = true)
        val orderRepository = mockk<rw.itunda.core.repository.OrderRepository>(relaxed = true)
        val orderItemRepository = mockk<rw.itunda.core.repository.OrderItemRepository>(relaxed = true)
        val merchantLoyaltyPointsService = mockk<MerchantLoyaltyPointsService>(relaxed = true)
        val autoTopUpService = mockk<rw.itunda.account.AutoTopUpService>(relaxed = true)
        val service = MerchantService(merchantRepository, paymentIntentRepository, accountRepository, ledgerService, webhookDeliveryService, transactionRepository, fraudRuleEngine, demoCardAuthorizationService, shoppingCashbackService, rateLimiter, ledgerEntryRepository, notificationRepository, merchantCouponService, pushNotificationService, customerPaymentCodeRepository, orderRepository, orderItemRepository, merchantLoyaltyPointsService, autoTopUpService)

        val ownerAccount = account("account_merchant", "owner_1")
        val merchant = Merchant(
            id = "merchant_1", ownerUserId = "owner_1", accountId = "account_merchant",
            businessName = "Kigali Coffee", status = MerchantStatus.ACTIVE,
        )

        When("registering a new merchant") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns null
            every { accountRepository.findByUserIdAndType("owner_1", AccountType.MAIN) } returns ownerAccount
            every { merchantRepository.save(any()) } answers { firstArg() }
            every { notificationRepository.save(any()) } answers { firstArg() }

            val result = service.register("owner_1", "Kigali Coffee")

            Then("it reuses the owner's existing account as the settlement account") {
                result.accountId shouldBe "account_merchant"
                result.ownerUserId shouldBe "owner_1"
                result.status shouldBe MerchantStatus.ACTIVE
            }

            Then("it real-alerts the account owner, a Toss 자산 보호 알림-style security notification") {
                verify(exactly = 1) {
                    notificationRepository.save(match { it.userId == "owner_1" && it.type == "NEW_MERCHANT_REGISTERED" })
                }
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

        When("registering an account with no account") {
            every { merchantRepository.findByOwnerUserId("owner_2") } returns null
            every { accountRepository.findByUserIdAndType("owner_2", AccountType.MAIN) } returns null

            Then("it throws MerchantNoAccountException") {
                try {
                    service.register("owner_2", "No Account Shop")
                    error("expected MerchantNoAccountException")
                } catch (e: MerchantNoAccountException) {
                    // expected
                }
            }
        }

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

        When("generating a QR payment intent") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
            every { paymentIntentRepository.save(any()) } answers { firstArg() }
            every { paymentIntentRepository.existsByUssdCode(any()) } returns false

            val intent = service.generateQr("owner_1", BigDecimal("5000"), "2 espresso")

            Then("it's pending, tied to the merchant, and expires 15 minutes out") {
                intent.merchantId shouldBe "merchant_1"
                intent.amount shouldBe BigDecimal("5000")
                intent.status shouldBe PaymentIntentStatus.PENDING
                (intent.expiresAt.epochSecond - intent.createdAt.epochSecond) shouldBe 900L
            }
        }

        When("collecting a valid, pending payment intent") {
            val payerAccount = account("account_payer", "payer_1")
            val intent = PaymentIntent(
                id = "pi_1", merchantId = "merchant_1", amount = BigDecimal("5000"),
                description = "2 espresso", expiresAt = Instant.now().plusSeconds(600),
            )
            val legsSlot = slot<List<LedgerLeg>>()

            every { paymentIntentRepository.findById("pi_1") } returns Optional.of(intent)
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            every { accountRepository.findByUserIdAndType("payer_1", AccountType.PAY) } returns payerAccount
            every { accountRepository.findById("account_merchant") } returns Optional.of(ownerAccount)
            every { notificationRepository.save(any()) } answers { firstArg() }
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns
                LedgerPostResult("ledgertxn_test", emptyList())
            every { paymentIntentRepository.save(any()) } answers { firstArg() }

            val result = service.collect("payer_1", "pi_1")

            Then("it splits a 1.5% fee to fee_revenue and the rest to the merchant") {
                val legs = legsSlot.captured
                legs.size shouldBe 3
                val payerLeg = legs.first { it.accountId == "account_payer" }
                val merchantLeg = legs.first { it.accountId == "account_merchant" }
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
            Then("real Toss Shopping cashback is awarded for the real payer account and purchase amount") {
                verify(exactly = 1) { shoppingCashbackService.awardCashback(payerAccount, BigDecimal("5000"), "Kigali Coffee", any()) }
                result.containsKey("cashbackEarned") shouldBe true
            }
            // Real-time "money received" notification for the merchant owner (2026-07-22)
            // -- same real gap and fix as rw.itunda.p2p.P2pService.notifyMoneyReceived
            // (see that method's own doc comment for the full account).
            Then("the merchant owner gets a real, immediate notification that a payment arrived") {
                verify(exactly = 1) {
                    notificationRepository.save(
                        match { it.userId == "owner_1" && it.type == "MONEY_RECEIVED" && it.body.contains("5000") },
                    )
                }
            }
            Then("the merchant owner also gets a real push notification, not just the in-app one") {
                verify(exactly = 1) { pushNotificationService.sendToUser("owner_1", "Payment received", match { it.contains("5000") }, any()) }
            }
            // Real Toss Place-style 자동 적립 (2026-08-18) -- see
            // MerchantLoyaltyPointsService's own doc comment.
            Then("real Toss Place-style loyalty points are accrued on the real final charge amount") {
                verify(exactly = 1) { merchantLoyaltyPointsService.accrue(merchant, "payer_1", BigDecimal("5000")) }
            }
        }

        // Real Naver Pay Money "결제 시 부족분 자동 충전" (auto-charge the shortfall at
        // payment time) wired into merchant payment collection -- same real mechanic
        // P2pService.sendDirect already uses for P2P transfers, previously missing from
        // this app's actual highest-traffic real money-moving path.
        When("collecting a payment where the payer's balance is short, but auto top-up covers it") {
            val shortAccount = account("account_short", "payer_short").also { it.availableBalance = BigDecimal("2000") }
            val toppedUpAccount = account("account_short", "payer_short").also { it.availableBalance = BigDecimal("10000") }
            val intent = PaymentIntent(
                id = "pi_short", merchantId = "merchant_1", amount = BigDecimal("5000"),
                description = "2 espresso", expiresAt = Instant.now().plusSeconds(600),
            )
            val legsSlot = slot<List<LedgerLeg>>()
            every { paymentIntentRepository.findById("pi_short") } returns Optional.of(intent)
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            every { accountRepository.findByUserIdAndType("payer_short", AccountType.PAY) } returns shortAccount
            every { accountRepository.findById("account_merchant") } returns Optional.of(ownerAccount)
            every { accountRepository.findById("account_short") } returns Optional.of(toppedUpAccount)
            every { notificationRepository.save(any()) } answers { firstArg() }
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns
                LedgerPostResult("ledgertxn_short", emptyList())
            every { paymentIntentRepository.save(any()) } answers { firstArg() }
            every { autoTopUpService.topUpShortfall("payer_short", "account_short", BigDecimal("3000")) } returns
                rw.itunda.account.AutoTopUpTriggerResult(true, "Topped up 3000 RWF to cover the real shortfall")

            val result = service.collect("payer_short", "pi_short")

            Then("it calls topUpShortfall for exactly the real gap, re-reads the account, and completes the payment") {
                verify(exactly = 1) { autoTopUpService.topUpShortfall("payer_short", "account_short", BigDecimal("3000")) }
                result["status"] shouldBe "COMPLETED"
                val payerLeg = legsSlot.captured.first { it.accountId == "account_short" }
                payerLeg.amount shouldBe BigDecimal("5000")
            }
        }

        When("collecting a payment where the payer's balance is short and auto top-up isn't configured") {
            val shortAccount = account("account_short2", "payer_short2").also { it.availableBalance = BigDecimal("2000") }
            val intent = PaymentIntent(
                id = "pi_short2", merchantId = "merchant_1", amount = BigDecimal("5000"),
                description = "2 espresso", expiresAt = Instant.now().plusSeconds(600),
            )
            every { paymentIntentRepository.findById("pi_short2") } returns Optional.of(intent)
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            every { accountRepository.findByUserIdAndType("payer_short2", AccountType.PAY) } returns shortAccount
            every { accountRepository.findById("account_merchant") } returns Optional.of(ownerAccount)
            every { autoTopUpService.topUpShortfall("payer_short2", "account_short2", BigDecimal("3000")) } returns
                rw.itunda.account.AutoTopUpTriggerResult(false, "No auto top-up setting configured for this account")
            every { ledgerService.postLedgerTransaction(any(), any()) } throws
                rw.itunda.core.ledger.InsufficientFundsException("Insufficient available balance for this transfer")

            Then("it real-propagates the same InsufficientFundsException a normal shortfall would, not a dead end") {
                try {
                    service.collect("payer_short2", "pi_short2")
                    error("expected InsufficientFundsException")
                } catch (e: rw.itunda.core.ledger.InsufficientFundsException) {
                    // expected
                }
            }
        }

        When("collecting a payment while redeeming real existing loyalty points") {
            val payerAccount = account("account_payer_points", "payer_points")
            val intent = PaymentIntent(
                id = "pi_points", merchantId = "merchant_1", amount = BigDecimal("5000"),
                description = "2 espresso", expiresAt = Instant.now().plusSeconds(600),
            )
            val legsSlot = slot<List<LedgerLeg>>()
            every { paymentIntentRepository.findById("pi_points") } returns Optional.of(intent)
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            every { accountRepository.findByUserIdAndType("payer_points", AccountType.PAY) } returns payerAccount
            every { accountRepository.findById("account_merchant") } returns Optional.of(ownerAccount)
            every { notificationRepository.save(any()) } answers { firstArg() }
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns
                LedgerPostResult("ledgertxn_points", emptyList())
            every { paymentIntentRepository.save(any()) } answers { firstArg() }
            every { merchantLoyaltyPointsService.validateAndComputeRedemption("merchant_1", "payer_points", BigDecimal("1000"), BigDecimal("5000")) } returns BigDecimal("1000")

            val result = service.collect("payer_points", "pi_points", pointsToRedeem = BigDecimal("1000"))

            Then("the real charge amount is reduced by the real redeemed points before any ledger leg is posted") {
                val legs = legsSlot.captured
                val payerLeg = legs.first { it.accountId == "account_payer_points" }
                payerLeg.amount shouldBe BigDecimal("4000")
                result["pointsRedeemed"] shouldBe BigDecimal("1000")
            }
            Then("the real redemption is recorded only after the real payment succeeded") {
                verify(exactly = 1) { merchantLoyaltyPointsService.recordRedemption("merchant_1", "payer_points", BigDecimal("1000")) }
            }
            Then("new real points are still accrued, on the real POST-redemption charge amount, not the original") {
                verify(exactly = 1) { merchantLoyaltyPointsService.accrue(merchant, "payer_points", BigDecimal("4000")) }
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

            Then("it throws SelfPaymentException before touching any account") {
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

        // Real read-only intent preview (item 149) -- see MerchantService.previewIntent's
        // own doc comment.
        When("previewing a valid, pending payment intent") {
            val intent = PaymentIntent(
                id = "pi_preview", merchantId = "merchant_1", amount = BigDecimal("5000"),
                description = "2 espresso", expiresAt = Instant.now().plusSeconds(600),
            )
            val eligibleCoupons = listOf(mockk<CouponView>())
            every { paymentIntentRepository.findById("pi_preview") } returns Optional.of(intent)
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            every { merchantCouponService.getCouponsForCustomer("merchant_1", "payer_1") } returns eligibleCoupons

            val result = service.previewIntent("payer_1", "pi_preview")

            Then("it reports the real merchant, amount, and this payer's own real coupon eligibility -- with zero side effects") {
                result["merchantId"] shouldBe "merchant_1"
                result["businessName"] shouldBe "Kigali Coffee"
                result["amount"] shouldBe BigDecimal("5000")
                result["coupons"] shouldBe eligibleCoupons
                intent.status shouldBe PaymentIntentStatus.PENDING
                verify(exactly = 0) { paymentIntentRepository.save(any()) }
            }
        }

        When("previewing an expired payment intent") {
            val expiredIntent = PaymentIntent(
                id = "pi_preview_expired", merchantId = "merchant_1", amount = BigDecimal("1000"), description = "x",
                expiresAt = Instant.now().minusSeconds(60),
            )
            every { paymentIntentRepository.findById("pi_preview_expired") } returns Optional.of(expiredIntent)

            Then("it throws PaymentIntentNotPayableException without mutating the intent (unlike collect())") {
                try {
                    service.previewIntent("payer_1", "pi_preview_expired")
                    error("expected PaymentIntentNotPayableException")
                } catch (e: PaymentIntentNotPayableException) {
                    expiredIntent.status shouldBe PaymentIntentStatus.PENDING
                    verify(exactly = 0) { paymentIntentRepository.save(any()) }
                }
            }
        }

        When("previewing a payment code that doesn't exist") {
            every { paymentIntentRepository.findById("nope_preview") } returns Optional.empty()

            Then("it throws PaymentIntentNotFoundException") {
                try {
                    service.previewIntent("payer_1", "nope_preview")
                    error("expected PaymentIntentNotFoundException")
                } catch (e: PaymentIntentNotFoundException) {
                    // expected
                }
            }
        }

        When("the merchant has a real webhook URL registered and a payment is collected") {
            val hookedMerchant = Merchant(
                id = "merchant_2", ownerUserId = "owner_2", accountId = "account_merchant2",
                businessName = "Hooked Cafe", status = MerchantStatus.ACTIVE, webhookUrl = "https://merchant.example/hooks",
            )
            val payerAccount = account("account_payer2", "payer_2")
            val intent = PaymentIntent(
                id = "pi_hook", merchantId = "merchant_2", amount = BigDecimal("1000"),
                description = "coffee", expiresAt = Instant.now().plusSeconds(600),
            )
            every { paymentIntentRepository.findById("pi_hook") } returns Optional.of(intent)
            every { merchantRepository.findById("merchant_2") } returns Optional.of(hookedMerchant)
            every { accountRepository.findByUserIdAndType("payer_2", AccountType.PAY) } returns payerAccount
            every { accountRepository.findById("account_merchant2") } returns Optional.of(account("account_merchant2", "owner_2"))
            every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("ledgertxn_hook", emptyList())
            every { paymentIntentRepository.save(any()) } answers { firstArg() }

            service.collect("payer_2", "pi_hook")

            Then("real webhook delivery is attempted with the merchant's registered URL") {
                verify(exactly = 1) { webhookDeliveryService.deliverPaymentStatusChanged("merchant_2", "https://merchant.example/hooks", any()) }
            }
        }

        When("the real cashback service itself fails during a collection") {
            val payerAccount = account("account_payer3", "payer_3")
            val intent = PaymentIntent(
                id = "pi_cashback_fail", merchantId = "merchant_1", amount = BigDecimal("2000"),
                description = "tea", expiresAt = Instant.now().plusSeconds(600),
            )
            every { paymentIntentRepository.findById("pi_cashback_fail") } returns Optional.of(intent)
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            every { accountRepository.findByUserIdAndType("payer_3", AccountType.PAY) } returns payerAccount
            every { accountRepository.findById("account_merchant") } returns Optional.of(ownerAccount)
            every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("ledgertxn_cb_fail", emptyList())
            every { paymentIntentRepository.save(any()) } answers { firstArg() }
            every { shoppingCashbackService.awardCashback(any(), any(), any(), any()) } throws RuntimeException("simulated cashback outage")

            val result = service.collect("payer_3", "pi_cashback_fail")

            Then("the real payment still succeeds -- an auxiliary cashback failure must never roll back real money already moved") {
                result["status"] shouldBe "COMPLETED"
                intent.status shouldBe PaymentIntentStatus.COMPLETED
                result["cashbackEarned"] shouldBe BigDecimal.ZERO
            }
        }

        When("charging a real Luhn-valid demo test card that authorizes") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
            every { accountRepository.findById("account_merchant") } returns Optional.of(ownerAccount)
            val legsSlot = slot<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_card_1", emptyList())

            val result = service.chargeCard(
                "owner_1", BigDecimal("8000"), "2x Coffee",
                DemoCardAuthorizationService.TEST_CARD_APPROVE, 12, 2030, "123",
            )

            Then("it posts real ledger legs from a real clearing account, not a fake payer account") {
                val clearingLeg = legsSlot.captured.first { it.accountType == LedgerAccountType.RAIL_SUSPENSE }
                clearingLeg.accountId shouldBe "card_network_clearing"
                clearingLeg.direction shouldBe LedgerDirection.DEBIT
                clearingLeg.amount shouldBe BigDecimal("8000")

                val merchantLeg = legsSlot.captured.first { it.accountType == LedgerAccountType.WALLET }
                merchantLeg.accountId shouldBe "account_merchant"
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
            every { accountRepository.findById("account_merchant") } returns Optional.of(ownerAccount)

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
            every { accountRepository.findById("account_merchant") } returns Optional.of(ownerAccount)

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

            Then("regenerating produces a genuinely different key, moving the OLD hash into a real 7-day grace period rather than a hard cutover") {
                val firstHash = savedKeySlot.captured.apiKeyHash
                val rawKey2 = service.generateApiKey("owner_1")
                (rawKey1 == rawKey2) shouldBe false
                savedKeySlot.captured.previousApiKeyHash shouldBe firstHash
                savedKeySlot.captured.previousApiKeyExpiresAt shouldNotBe null
            }
        }

        When("resolving a merchant by a real, valid API key") {
            val keyedMerchant = Merchant(
                id = "merchant_5", ownerUserId = "owner_5", accountId = "account_5",
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
            every { merchantRepository.findByPreviousApiKeyHash(any()) } returns null

            Then("it real-throws InvalidApiKeyException rather than a null merchant slipping through") {
                try {
                    service.resolveMerchantByApiKey("sk_test_bogus")
                    error("expected InvalidApiKeyException")
                } catch (e: InvalidApiKeyException) {
                    // expected
                }
            }
        }

        // Real Toss Payments-style grace-period key reissue (2026-07-28) -- see
        // MerchantService.generateApiKey's own doc comment.
        When("resolving by a real PREVIOUS key still inside its real 7-day grace period") {
            every { merchantRepository.findByApiKeyHash(any()) } returns null
            val graceMerchant = Merchant(
                id = "merchant_7", ownerUserId = "owner_7", accountId = "account_7",
                businessName = "Rotated Key Shop", status = MerchantStatus.ACTIVE,
                apiKeyHash = "current_hash_7", previousApiKeyHash = "previous_hash_7",
                previousApiKeyExpiresAt = Instant.now().plusSeconds(3600),
            )
            every { merchantRepository.findByPreviousApiKeyHash(any()) } returns graceMerchant

            Then("it still real-resolves the merchant via the previous key -- a real in-flight rotation isn't a hard cutover") {
                service.resolveMerchantByApiKey("sk_test_previous").id shouldBe "merchant_7"
            }
        }

        When("resolving by a real PREVIOUS key whose real grace period has already elapsed") {
            every { merchantRepository.findByApiKeyHash(any()) } returns null
            val expiredMerchant = Merchant(
                id = "merchant_8", ownerUserId = "owner_8", accountId = "account_8",
                businessName = "Expired Key Shop", status = MerchantStatus.ACTIVE,
                apiKeyHash = "current_hash_8", previousApiKeyHash = "previous_hash_8",
                previousApiKeyExpiresAt = Instant.now().minusSeconds(3600),
            )
            every { merchantRepository.findByPreviousApiKeyHash(any()) } returns expiredMerchant

            Then("it real-throws InvalidApiKeyException -- the grace period is real, not indefinite") {
                try {
                    service.resolveMerchantByApiKey("sk_test_expired_previous")
                    error("expected InvalidApiKeyException")
                } catch (e: InvalidApiKeyException) {
                    // expected
                }
            }
        }

        When("resolving by a real key belonging to a suspended merchant") {
            val suspendedMerchant = Merchant(
                id = "merchant_6", ownerUserId = "owner_6", accountId = "account_6",
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
            every { paymentIntentRepository.existsByUssdCode(any()) } returns false

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

        When("a checkout callback is not an absolute HTTPS URL") {
            Then("it rejects unsafe browser-navigation schemes and relative URLs") {
                listOf(
                    "javascript:alert(1)",
                    "http://shop.example/success",
                    "/checkout/success",
                    "https://merchant:password@shop.example/success",
                ).forEach { unsafeUrl ->
                    try {
                        service.createExternalPayment(merchant, BigDecimal("5000"), "Order", null, unsafeUrl, null)
                        error("expected InvalidCheckoutRequestException for $unsafeUrl")
                    } catch (_: InvalidCheckoutRequestException) {
                        // expected
                    }
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

        When("a customer's browser polls an expired external checkout") {
            val expiredIntent = PaymentIntent(
                id = "pi_checkout_expired", merchantId = "merchant_1", amount = BigDecimal("2500"),
                description = "Expired order", expiresAt = Instant.now().minusSeconds(1),
                successUrl = "https://shop.example/ok", failUrl = "https://shop.example/no",
            )
            every { paymentIntentRepository.findById("pi_checkout_expired") } returns Optional.of(expiredIntent)
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            every { paymentIntentRepository.save(expiredIntent) } returns expiredIntent

            val info = service.getCheckoutInfo("pi_checkout_expired")

            Then("it persists and returns EXPIRED so the hosted page can stop polling") {
                info.status shouldBe PaymentIntentStatus.EXPIRED
                verify(exactly = 1) { paymentIntentRepository.save(expiredIntent) }
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

        // Real cancel/refund (2026-07-21) -- see MerchantService.cancelPayment's own doc
        // comment for the full account of the real Toss Payments cancel API this mirrors.
        fun completedIntent(id: String, amount: BigDecimal, refundedAmount: BigDecimal = BigDecimal.ZERO) = PaymentIntent(
            id = id, merchantId = "merchant_1", amount = amount, description = "Order #C1",
            status = PaymentIntentStatus.COMPLETED, expiresAt = Instant.now().plusSeconds(900),
            completedTransactionId = "txn_c1", orderId = "order_C1", refundedAmount = refundedAmount,
        )

        fun originalLegs(txnId: String) = listOf(
            LedgerEntry(id = "le_1", transactionId = txnId, accountId = "payer_account", accountType = LedgerAccountType.WALLET, direction = LedgerDirection.DEBIT, amount = BigDecimal("10000"), currency = "RWF", balanceAfter = BigDecimal("90000"), memo = "QR payment"),
            LedgerEntry(id = "le_2", transactionId = txnId, accountId = "account_merchant", accountType = LedgerAccountType.WALLET, direction = LedgerDirection.CREDIT, amount = BigDecimal("9850"), currency = "RWF", balanceAfter = BigDecimal("9850"), memo = "QR collection"),
            LedgerEntry(id = "le_3", transactionId = txnId, accountId = "fee_revenue", accountType = LedgerAccountType.FEE_REVENUE, direction = LedgerDirection.CREDIT, amount = BigDecimal("150"), currency = "RWF", balanceAfter = BigDecimal("150"), memo = "QR fee"),
        )

        When("fully cancelling a real completed payment") {
            val intent = completedIntent("pi_c1", BigDecimal("10000"))
            every { paymentIntentRepository.findById("pi_c1") } returns Optional.of(intent)
            every { ledgerEntryRepository.findByTransactionId("txn_c1") } returns originalLegs("txn_c1")
            val legsSlot = slot<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction(any(), capture(legsSlot)) } returns LedgerPostResult("txn_refund_1", emptyList())
            every { paymentIntentRepository.save(any()) } answers { firstArg() }

            val result = service.cancelPayment(merchant, "pi_c1", "Customer requested refund", null)

            Then("it reverses every original leg's direction at full amount, matching rw.itunda.commerce.OrderService.cancelOrder's exact real reversal pattern") {
                legsSlot.captured.size shouldBe 3
                legsSlot.captured.find { it.accountId == "payer_account" }!!.direction shouldBe LedgerDirection.CREDIT
                legsSlot.captured.find { it.accountId == "payer_account" }!!.amount shouldBe BigDecimal("10000.00")
                legsSlot.captured.find { it.accountId == "account_merchant" }!!.direction shouldBe LedgerDirection.DEBIT
                legsSlot.captured.find { it.accountId == "fee_revenue" }!!.direction shouldBe LedgerDirection.DEBIT
            }

            Then("the real result reports a full cancellation, including the merchant's own orderId for correlation") {
                result["orderId"] shouldBe "order_C1"
                result["fullyCancelled"] shouldBe true
                result["cancelledAmount"] shouldBe BigDecimal("10000")
                intent.refundedAmount shouldBe BigDecimal("10000")
            }
        }

        When("partially cancelling a real completed payment") {
            val intent = completedIntent("pi_c2", BigDecimal("10000"))
            every { paymentIntentRepository.findById("pi_c2") } returns Optional.of(intent)
            every { ledgerEntryRepository.findByTransactionId("txn_c1") } returns originalLegs("txn_c1")
            val legsSlot = slot<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction(any(), capture(legsSlot)) } returns LedgerPostResult("txn_refund_2", emptyList())
            every { paymentIntentRepository.save(any()) } answers { firstArg() }

            val result = service.cancelPayment(merchant, "pi_c2", "Partial return", BigDecimal("2000"))

            Then("only 20% of each original leg is reversed -- an itunda-specific proportional choice, not a fabricated claim about Toss's own internal partial-cancel math") {
                legsSlot.captured.find { it.accountId == "payer_account" }!!.amount shouldBe BigDecimal("2000.00")
                legsSlot.captured.find { it.accountId == "fee_revenue" }!!.amount shouldBe BigDecimal("30.00")
            }

            Then("the real payment stays partially, not fully, cancelled") {
                result["fullyCancelled"] shouldBe false
                result["remainingAmount"] shouldBe BigDecimal("8000")
                intent.refundedAmount shouldBe BigDecimal("2000")
            }
        }

        When("trying to cancel a payment that hasn't been completed yet") {
            val pendingIntent = PaymentIntent(
                id = "pi_pending", merchantId = "merchant_1", amount = BigDecimal("5000"),
                description = "Not paid yet", expiresAt = Instant.now().plusSeconds(900),
            )
            every { paymentIntentRepository.findById("pi_pending") } returns Optional.of(pendingIntent)

            Then("it real-throws PaymentIntentNotRefundableException rather than reversing a payment that never happened") {
                try {
                    service.cancelPayment(merchant, "pi_pending", "Too early", null)
                    error("expected PaymentIntentNotRefundableException")
                } catch (e: PaymentIntentNotRefundableException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("trying to cancel more than the real remaining refundable amount") {
            val intent = completedIntent("pi_c3", BigDecimal("10000"), refundedAmount = BigDecimal("8000"))
            every { paymentIntentRepository.findById("pi_c3") } returns Optional.of(intent)

            Then("it real-throws InvalidCancelRequestException -- only 2000 is left to refund, not the full 10000") {
                try {
                    service.cancelPayment(merchant, "pi_c3", "Too much", BigDecimal("5000"))
                    error("expected InvalidCancelRequestException")
                } catch (e: InvalidCancelRequestException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("trying to cancel with a blank cancelReason") {
            val intent = completedIntent("pi_c4", BigDecimal("5000"))
            every { paymentIntentRepository.findById("pi_c4") } returns Optional.of(intent)

            Then("it real-throws InvalidCancelRequestException before ever touching the ledger") {
                try {
                    service.cancelPayment(merchant, "pi_c4", "   ", null)
                    error("expected InvalidCancelRequestException")
                } catch (e: InvalidCancelRequestException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("one merchant tries to cancel a payment real-belonging to a different merchant") {
            val otherMerchantsIntent = PaymentIntent(
                id = "pi_c5", merchantId = "merchant_999", amount = BigDecimal("5000"),
                description = "Not yours", status = PaymentIntentStatus.COMPLETED,
                expiresAt = Instant.now().plusSeconds(900), completedTransactionId = "txn_x",
            )
            every { paymentIntentRepository.findById("pi_c5") } returns Optional.of(otherMerchantsIntent)

            Then("it real-404s rather than letting a merchant refund a payment they never received") {
                try {
                    service.cancelPayment(merchant, "pi_c5", "Not mine to cancel", null)
                    error("expected PaymentIntentNotFoundException")
                } catch (e: PaymentIntentNotFoundException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    Given("a registered merchant with no webhook configured yet") {
        val merchantRepository = mockk<MerchantRepository>()
        val paymentIntentRepository = mockk<PaymentIntentRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val webhookDeliveryService = mockk<WebhookDeliveryService>(relaxed = true)
        // Not relaxed for save() specifically: mockk's relaxed default can't correctly
        // infer JpaRepository's generic `<S extends T> S save(S)` signature, returning a
        // raw mock Object that then fails a real ClassCastException back in the caller
        // (confirmed live) -- same reason AccountServiceTest explicitly stubs this too.
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        every { transactionRepository.save(any()) } answers { firstArg() }
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val demoCardAuthorizationService = DemoCardAuthorizationService()
        val shoppingCashbackService = mockk<ShoppingCashbackService>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val merchantCouponService = mockk<MerchantCouponService>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val customerPaymentCodeRepository = mockk<CustomerPaymentCodeRepository>(relaxed = true)
        val orderRepository = mockk<rw.itunda.core.repository.OrderRepository>(relaxed = true)
        val orderItemRepository = mockk<rw.itunda.core.repository.OrderItemRepository>(relaxed = true)
        val merchantLoyaltyPointsService = mockk<MerchantLoyaltyPointsService>(relaxed = true)
        val service = MerchantService(merchantRepository, paymentIntentRepository, accountRepository, ledgerService, webhookDeliveryService, transactionRepository, fraudRuleEngine, demoCardAuthorizationService, shoppingCashbackService, rateLimiter, ledgerEntryRepository, notificationRepository, merchantCouponService, pushNotificationService, customerPaymentCodeRepository, orderRepository, orderItemRepository, merchantLoyaltyPointsService, mockk(relaxed = true))

        val merchant = Merchant(id = "merchant_3", ownerUserId = "owner_3", accountId = "account_3", businessName = "Test Shop")
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
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val webhookDeliveryService = mockk<WebhookDeliveryService>(relaxed = true)
        val transactionRepository = mockk<TransactionRepository>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val demoCardAuthorizationService = DemoCardAuthorizationService()
        val shoppingCashbackService = mockk<ShoppingCashbackService>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val merchantCouponService = mockk<MerchantCouponService>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val customerPaymentCodeRepository = mockk<CustomerPaymentCodeRepository>(relaxed = true)
        val orderRepository = mockk<rw.itunda.core.repository.OrderRepository>()
        val orderItemRepository = mockk<rw.itunda.core.repository.OrderItemRepository>()
        val merchantLoyaltyPointsService = mockk<MerchantLoyaltyPointsService>(relaxed = true)
        val service = MerchantService(merchantRepository, paymentIntentRepository, accountRepository, ledgerService, webhookDeliveryService, transactionRepository, fraudRuleEngine, demoCardAuthorizationService, shoppingCashbackService, rateLimiter, ledgerEntryRepository, notificationRepository, merchantCouponService, pushNotificationService, customerPaymentCodeRepository, orderRepository, orderItemRepository, merchantLoyaltyPointsService, mockk(relaxed = true))

        val merchant = Merchant(id = "merchant_4", ownerUserId = "owner_4", accountId = "account_4", businessName = "Report Cafe")
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

        When("requesting a report with an inverted date range") {
            Then("it rejects the request before querying transactions") {
                try {
                    service.getReport("owner_4", day2, day1)
                    error("expected InvalidReportRangeException")
                } catch (e: InvalidReportRangeException) {
                    verify(exactly = 0) { transactionRepository.findByRecipientIdAndTypeAndCreatedAtBetween(any(), any(), any(), any()) }
                }
            }
        }

        When("requesting more than the supported 31-day report window") {
            Then("it rejects the request before querying transactions") {
                try {
                    service.getReport("owner_4", day1, day1.plusDays(31))
                    error("expected InvalidReportRangeException")
                } catch (e: InvalidReportRangeException) {
                    verify(exactly = 0) { transactionRepository.findByRecipientIdAndTypeAndCreatedAtBetween(any(), any(), any(), any()) }
                }
            }
        }

        fun order(id: String, day: LocalDate) = rw.itunda.core.domain.Order(
            id = id, buyerId = "buyer_x", merchantId = "merchant_4", deliveryAddress = "Kigali",
            totalAmount = BigDecimal("1000"), fee = BigDecimal("15"), transactionId = "ledgertxn_$id",
            createdAt = day.atTime(9, 0).toInstant(ZoneOffset.UTC),
        )

        fun item(id: String, orderId: String, productId: String, name: String, price: String, qty: Int) =
            rw.itunda.core.domain.OrderItem(id = id, orderId = orderId, productId = productId, productName = name, unitPrice = BigDecimal(price), quantity = qty)

        When("requesting the top-selling-products report for a range with two products sold across two orders") {
            every {
                orderRepository.findByMerchantIdAndCreatedAtBetween("merchant_4", any(), any())
            } returns listOf(order("o1", day1), order("o2", day2))
            every {
                orderItemRepository.findByOrderIdIn(listOf("o1", "o2"))
            } returns listOf(
                item("i1", "o1", "prod_a", "Rwandan Coffee 1kg", "5000", 2),
                item("i2", "o1", "prod_b", "Sugar 1kg", "1000", 1),
                item("i3", "o2", "prod_a", "Rwandan Coffee 1kg", "5000", 3),
            )

            val products = service.getTopSellingProducts("owner_4", day1, day2)

            Then("it aggregates units and revenue per real product, ranked by revenue descending") {
                products.size shouldBe 2
                products[0].productId shouldBe "prod_a"
                products[0].unitsSold shouldBe 5
                products[0].revenue shouldBe BigDecimal("25000")
                products[1].productId shouldBe "prod_b"
                products[1].unitsSold shouldBe 1
                products[1].revenue shouldBe BigDecimal("1000")
            }
        }

        When("requesting the top-selling-products report for a range with no orders") {
            every { orderRepository.findByMerchantIdAndCreatedAtBetween("owner_4", any(), any()) } returns emptyList()
            every { orderRepository.findByMerchantIdAndCreatedAtBetween("merchant_4", any(), any()) } returns emptyList()

            Then("it returns an empty list without ever querying order items") {
                service.getTopSellingProducts("owner_4", day1, day1).size shouldBe 0
                verify(exactly = 0) { orderItemRepository.findByOrderIdIn(any()) }
            }
        }

        When("requesting the top-selling-products report with an inverted date range") {
            Then("it rejects the request before querying orders") {
                try {
                    service.getTopSellingProducts("owner_4", day2, day1)
                    error("expected InvalidReportRangeException")
                } catch (e: InvalidReportRangeException) {
                    verify(exactly = 0) { orderRepository.findByMerchantIdAndCreatedAtBetween(any(), any(), any()) }
                }
            }
        }
    }
}) {
    // Same reasoning as LedgerServiceTest.kt: fresh fixtures per leaf test so mutation
    // in one When (e.g. marking an intent EXPIRED) can't leak into a sibling test.
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
