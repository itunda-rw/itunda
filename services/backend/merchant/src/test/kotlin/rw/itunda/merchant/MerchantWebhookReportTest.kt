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
import rw.itunda.core.domain.CustomerPaymentCode
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

// Real fix (2026-08-26): split out of MerchantServiceTest.kt once that file grew
// past its file-size-lint baseline. Both Given blocks were already fully self-
// contained (own fresh mocks + own MerchantService instance, no shared spec-level
// state) -- a clean, zero-risk test-file split by concern.
class MerchantWebhookReportTest : BehaviorSpec({

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
        // setWebhookUrl now lives on MerchantProfileService (itunda Maps redesign,
        // 2026-08-28), see that class's own doc comment -- this Given block no longer
        // needs a real MerchantService instance at all.
        val profileService = MerchantProfileService(merchantRepository)

        val merchant = Merchant(id = "merchant_3", ownerUserId = "owner_3", accountId = "account_3", businessName = "Test Shop")
        every { merchantRepository.findByOwnerUserId("owner_3") } returns merchant
        every { merchantRepository.save(any()) } answers { firstArg() }

        When("registering a webhook URL") {
            val updated = profileService.setWebhookUrl("owner_3", "https://myshop.example/webhooks/itunda")

            Then("it's saved onto the real merchant record") {
                updated.webhookUrl shouldBe "https://myshop.example/webhooks/itunda"
                verify(exactly = 1) { merchantRepository.save(merchant) }
            }
        }

        When("registering a webhook URL longer than the real 500-char DB column bound") {
            Then("it throws InvalidWebhookUrlException rather than risking a raw DB insert failure") {
                try {
                    profileService.setWebhookUrl("owner_3", "https://example.com/" + "x".repeat(500))
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
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
