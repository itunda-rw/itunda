package rw.itunda.commerce

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.LedgerEntry
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantProduct
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.Order
import rw.itunda.core.domain.OrderItem
import rw.itunda.core.domain.OrderStatus
import rw.itunda.core.domain.Rider
import rw.itunda.core.domain.TimeDeal
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.OrderItemRepository
import rw.itunda.core.repository.OrderRepository
import rw.itunda.core.repository.ProductPriceTierRepository
import rw.itunda.core.repository.RiderRepository
import rw.itunda.core.repository.TimeDealRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.util.Optional

class OrderServiceTest : BehaviorSpec({

    fun account(id: String, userId: String) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = AccountType.MAIN, balance = BigDecimal("100000"), availableBalance = BigDecimal("100000"),
    )

    Given("a real merchant with a real product catalog") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val orderRepository = mockk<OrderRepository>()
        val orderItemRepository = mockk<OrderItemRepository>(relaxed = true)
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        // Not relaxed for save() specifically: mockk's relaxed default can't correctly
        // infer JpaRepository's generic `<S extends T> S save(S)` signature, returning a
        // raw mock Object that then fails a real ClassCastException back in the caller --
        // same reason MerchantServiceTest/AccountServiceTest explicitly stub this too.
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        every { transactionRepository.save(any()) } answers { firstArg() }
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        // Real buyer order-status notifications (2026-07-20) -- explicit stub, same
        // known "relaxed mockk can't correctly infer JpaRepository's generic save()
        // signature" gotcha this project's own tests already document repeatedly.
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val priceTierRepository = mockk<ProductPriceTierRepository>(relaxed = true)
        val riderRepository = mockk<RiderRepository>()
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        // Real Coupang 타임특가 (Time Deal, item 226) -- no active deal in this test
        // group, same explicit-stub discipline this file already uses rather than
        // trusting a relaxed mock's default for a nullable return type.
        val timeDealRepository = mockk<TimeDealRepository>()
        every { timeDealRepository.findActiveDealForProduct(any(), any()) } returns null
        val affiliateService = mockk<AffiliateService>()
        every { affiliateService.payCommissionIfReferred(any(), any(), any(), any()) } returns Unit
        val autoTopUpService = mockk<rw.itunda.account.AutoTopUpService>(relaxed = true)
        every { autoTopUpService.ensureSufficientPayBalance(any(), any(), any()) } answers { secondArg() }
        val service = OrderService(
            merchantRepository, merchantProductRepository, orderRepository, orderItemRepository,
            accountRepository, ledgerService, transactionRepository, fraudRuleEngine, ledgerEntryRepository,
            notificationRepository, priceTierRepository, riderRepository, pushNotificationService,
            timeDealRepository, affiliateService, autoTopUpService,
        )

        val merchant = Merchant(id = "merchant_1", ownerUserId = "seller_1", accountId = "account_merchant", businessName = "Kigali Store", status = MerchantStatus.ACTIVE)
        val merchantAccount = account("account_merchant", "seller_1")
        val buyerAccount = account("account_buyer", "buyer_1").also { it.type = AccountType.PAY }
        val product = MerchantProduct(id = "product_1", merchantId = "merchant_1", name = "Coffee beans", price = BigDecimal("2000"))

        When("a buyer requests more than the finite catalog stock") {
            val stockedProduct = MerchantProduct(
                id = "product_stocked", merchantId = "merchant_1", name = "Limited beans",
                price = BigDecimal("2000"), stockQuantity = 2,
            )
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            every { accountRepository.findById("account_merchant") } returns Optional.of(merchantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
            every { merchantProductRepository.findById("product_stocked") } returns Optional.of(stockedProduct)

            Then("it rejects before debiting a account or mutating the available stock") {
                try {
                    service.placeOrder("buyer_1", "merchant_1", listOf(OrderItemRequest("product_stocked", 3)), "KG 123 St")
                    error("expected InsufficientProductStockException")
                } catch (e: InsufficientProductStockException) {
                    stockedProduct.stockQuantity shouldBe 2
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("ordering from a merchant that has temporarily paused orders") {
            // Real Baemin CEO app 영업일시중지 enforcement -- see Merchant.isAcceptingOrders's
            // own doc comment and OrderService.placeOrder's own new comment above the
            // check this test covers. Same real gap/fix EatsOrderServiceTest's own
            // "temporarily paused orders" test already proves for the delivery checkout;
            // this proves the Commerce checkout, which shares the same Merchant catalog,
            // now enforces it too.
            val pausedMerchant = Merchant(
                id = "merchant_1", ownerUserId = "seller_1", accountId = "account_merchant", businessName = "Kigali Store",
                status = MerchantStatus.ACTIVE, isAcceptingOrders = false,
            )
            every { merchantRepository.findById("merchant_1") } returns Optional.of(pausedMerchant)

            Then("it throws MerchantNotAcceptingOrdersException before ever resolving the merchant's account") {
                try {
                    service.placeOrder("buyer_1", "merchant_1", listOf(OrderItemRequest("product_1", 1)), "KG 123 St")
                    error("expected MerchantNotAcceptingOrdersException")
                } catch (e: MerchantNotAcceptingOrdersException) {
                    verify(exactly = 0) { accountRepository.findById("account_merchant") }
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("ordering from a merchant whose real recurring closed-weekday schedule includes today") {
            // Real Baemin CEO app 휴무일 설정 enforcement -- see Merchant.isClosedToday's own
            // doc comment. Same real weekday-computation discipline MerchantTest.kt's own
            // isClosedToday tests already establish -- no Clock abstraction exists in this
            // codebase, so this uses the real current Rwanda weekday rather than a fabricated
            // fake date.
            val todayWeekday = java.time.LocalDate.now(java.time.ZoneId.of("Africa/Kigali")).dayOfWeek.value
            val closedTodayMerchant = Merchant(
                id = "merchant_1", ownerUserId = "seller_1", accountId = "account_merchant", businessName = "Kigali Store",
                status = MerchantStatus.ACTIVE, closedWeekdays = "$todayWeekday",
            )
            every { merchantRepository.findById("merchant_1") } returns Optional.of(closedTodayMerchant)

            Then("it throws MerchantNotAcceptingOrdersException before ever resolving the merchant's account") {
                try {
                    service.placeOrder("buyer_1", "merchant_1", listOf(OrderItemRequest("product_1", 1)), "KG 123 St")
                    error("expected MerchantNotAcceptingOrdersException")
                } catch (e: MerchantNotAcceptingOrdersException) {
                    verify(exactly = 0) { accountRepository.findById("account_merchant") }
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("a buyer tries to order a product the merchant has marked sold out") {
            // Real Baemin CEO app/DoorDash-style "86" enforcement -- see
            // MerchantProduct.soldOut's own doc comment and OrderService.placeOrder's own
            // new comment above the check this test covers. stockQuantity is deliberately
            // left null (unlimited/untracked) so this failure can only be attributed to
            // the soldOut flag itself, not an incidental stock-count rejection.
            val soldOutProduct = MerchantProduct(
                id = "product_soldout", merchantId = "merchant_1", name = "86'd item",
                price = BigDecimal("2000"), soldOut = true,
            )
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            every { accountRepository.findById("account_merchant") } returns Optional.of(merchantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
            every { merchantProductRepository.findById("product_soldout") } returns Optional.of(soldOutProduct)

            Then("it rejects with ProductSoldOutException before debiting a account") {
                try {
                    service.placeOrder("buyer_1", "merchant_1", listOf(OrderItemRequest("product_soldout", 1)), "KG 123 St")
                    error("expected ProductSoldOutException")
                } catch (e: ProductSoldOutException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("a buyer tries to order a product whose closing/surplus deal has already expired") {
            // Real 마감할인 (closing/surplus discount) expiry enforcement -- see
            // MerchantProduct.isSurplusDeal/surplusExpiresAt's own doc comment and
            // OrderService.placeOrder's own new comment above the check this test
            // covers. MerchantProductRepository.findSurplusDeals already correctly
            // hides an expired deal from the browse rail, but checkout itself never
            // re-checked the expiry -- this proves it now does, before any money moves.
            val expiredDealProduct = MerchantProduct(
                id = "product_expired_surplus", merchantId = "merchant_1", name = "Closing-time bread",
                price = BigDecimal("500"), isSurplusDeal = true,
                surplusExpiresAt = java.time.Instant.now().minusSeconds(3600),
                stockQuantity = 5,
            )
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            every { accountRepository.findById("account_merchant") } returns Optional.of(merchantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
            every { merchantProductRepository.findById("product_expired_surplus") } returns Optional.of(expiredDealProduct)

            Then("it rejects with SurplusDealExpiredException before debiting a account or decrementing stock") {
                try {
                    service.placeOrder("buyer_1", "merchant_1", listOf(OrderItemRequest("product_expired_surplus", 1)), "KG 123 St")
                    error("expected SurplusDealExpiredException")
                } catch (e: SurplusDealExpiredException) {
                    expiredDealProduct.stockQuantity shouldBe 5
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("a buyer orders a product whose closing/surplus deal has NOT yet expired") {
            // Negative case: a still-valid surplus deal must keep working exactly like
            // any other product -- this feature is an expiry check, not a blanket ban
            // on isSurplusDeal products.
            val activeDealProduct = MerchantProduct(
                id = "product_active_surplus", merchantId = "merchant_1", name = "Still-fresh bread",
                price = BigDecimal("500"), isSurplusDeal = true,
                surplusExpiresAt = java.time.Instant.now().plusSeconds(3600),
                stockQuantity = 5,
            )
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            every { accountRepository.findById("account_merchant") } returns Optional.of(merchantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
            every { merchantProductRepository.findById("product_active_surplus") } returns Optional.of(activeDealProduct)
            every { merchantProductRepository.saveAll(any<List<MerchantProduct>>()) } answers { firstArg() }
            every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("ledgertxn_test2", emptyList())
            every { orderRepository.save(any()) } answers { firstArg() }

            Then("it succeeds like a normal order") {
                val detail = service.placeOrder("buyer_1", "merchant_1", listOf(OrderItemRequest("product_active_surplus", 1)), "KG 123 St")
                detail.order.totalAmount shouldBe BigDecimal("500")
                activeDealProduct.stockQuantity shouldBe 4
            }
        }

        When("a real buyer places a real order for 3 units") {
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            every { accountRepository.findById("account_merchant") } returns Optional.of(merchantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
            every { merchantProductRepository.findById("product_1") } returns Optional.of(product)
            val legsSlot = slot<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_test", emptyList())
            every { orderRepository.save(any()) } answers { firstArg() }

            val detail = service.placeOrder("buyer_1", "merchant_1", listOf(OrderItemRequest("product_1", 3)), "  KG 123 St  ")

            Then("it prices from the real live catalog (2000 x 3 = 6000), splits a 1.5% fee, and trims the address") {
                detail.order.totalAmount shouldBe BigDecimal("6000")
                detail.order.fee shouldBe BigDecimal("90.00")
                detail.order.deliveryAddress shouldBe "KG 123 St"
                detail.items.first().productName shouldBe "Coffee beans"
                detail.items.first().quantity shouldBe 3

                val legs = legsSlot.captured
                val buyerLeg = legs.first { it.accountId == "account_buyer" }
                val merchantLeg = legs.first { it.accountId == "account_merchant" }
                val feeLeg = legs.first { it.accountId == "fee_revenue" }
                buyerLeg.amount shouldBe BigDecimal("6000")
                merchantLeg.amount shouldBe BigDecimal("5910.00")
                feeLeg.amount shouldBe BigDecimal("90.00")
                feeLeg.accountType shouldBe LedgerAccountType.FEE_REVENUE
            }

            Then("it real-alerts the real merchant owner -- the real gap where a new order arrived with zero notification") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "seller_1" && it.type == "NEW_COMMERCE_ORDER" }) }
            }

            Then("the merchant owner also gets a real push notification, not just the in-app one") {
                verify(exactly = 1) { pushNotificationService.sendToUser("seller_1", "New order received", any(), any()) }
            }
        }

        // Real Toss Bank/Toss Pay separation (2026-08-21) -- a Commerce order is real
        // merchant collection, same as MerchantService.collect()'s own QR path, so it
        // gets the same auto-topup-from-Bank-if-short treatment.
        When("a real buyer's itunda Pay money is short but auto top-up from Bank covers it") {
            val shortAccount = account("account_buyer_short", "buyer_short").also { it.availableBalance = BigDecimal("1000") }
            val toppedUpAccount = account("account_buyer_short", "buyer_short").also { it.availableBalance = BigDecimal("10000") }
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            every { accountRepository.findById("account_merchant") } returns Optional.of(merchantAccount)
            every { accountRepository.findByUserIdAndType("buyer_short", AccountType.PAY) } returns shortAccount
            every { autoTopUpService.ensureSufficientPayBalance("buyer_short", shortAccount, BigDecimal("6000")) } returns toppedUpAccount
            every { merchantProductRepository.findById("product_1") } returns Optional.of(product)
            val legsSlot = slot<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_topup", emptyList())
            every { orderRepository.save(any()) } answers { firstArg() }

            service.placeOrder("buyer_short", "merchant_1", listOf(OrderItemRequest("product_1", 3)), "KG 123 St")

            Then("it calls the shared top-up helper for exactly this order's real total, then completes the order") {
                verify(exactly = 1) { autoTopUpService.ensureSufficientPayBalance("buyer_short", shortAccount, BigDecimal("6000")) }
                legsSlot.captured.first { it.accountId == "account_buyer_short" }.amount shouldBe BigDecimal("6000")
            }
        }

        When("a marketplace order is still inside its payment transaction") {
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            every { accountRepository.findById("account_merchant") } returns Optional.of(merchantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
            every { merchantProductRepository.findById("product_1") } returns Optional.of(product)
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_after_commit", emptyList())
            every { orderRepository.save(any()) } answers { firstArg() }

            TransactionSynchronizationManager.initSynchronization()
            try {
                service.placeOrder("buyer_1", "merchant_1", listOf(OrderItemRequest("product_1", 3)), "KG 123 St")

                Then("the durable merchant notification is saved, but no external push is sent") {
                    verify(exactly = 1) { notificationRepository.save(match { it.userId == "seller_1" && it.type == "NEW_COMMERCE_ORDER" }) }
                    verify(exactly = 0) { pushNotificationService.sendToUser(any(), any(), any(), any()) }
                }

                Then("the merchant receives exactly one push after commit") {
                    TransactionSynchronizationManager.getSynchronizations().single().afterCommit()
                    verify(exactly = 1) {
                        pushNotificationService.sendToUser(
                            "seller_1",
                            "New order received",
                            any(),
                            match { it.containsKey("orderId") },
                        )
                    }
                }
            } finally {
                TransactionSynchronizationManager.clearSynchronization()
            }
        }

        When("a real buyer's order falls below the merchant's real minimum order amount") {
            val merchantWithMin = Merchant(
                id = "merchant_1", ownerUserId = "seller_1", accountId = "account_merchant", businessName = "Kigali Store",
                status = MerchantStatus.ACTIVE, minOrderAmount = BigDecimal("10000"),
            )
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchantWithMin)
            every { accountRepository.findById("account_merchant") } returns Optional.of(merchantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
            every { merchantProductRepository.findById("product_1") } returns Optional.of(product)

            Then("it's honestly rejected -- this real, already-shipped field was never actually enforced anywhere before") {
                try {
                    service.placeOrder("buyer_1", "merchant_1", listOf(OrderItemRequest("product_1", 3)), "addr")
                    error("expected MinOrderAmountNotMetException")
                } catch (e: MinOrderAmountNotMetException) {
                    // expected -- 3 x 2000 = 6000, below the real 10000 minimum
                }
            }
        }

        When("a real buyer's order meets the merchant's real minimum order amount exactly") {
            val merchantWithMin = Merchant(
                id = "merchant_1", ownerUserId = "seller_1", accountId = "account_merchant", businessName = "Kigali Store",
                status = MerchantStatus.ACTIVE, minOrderAmount = BigDecimal("6000"),
            )
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchantWithMin)
            every { accountRepository.findById("account_merchant") } returns Optional.of(merchantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
            every { merchantProductRepository.findById("product_1") } returns Optional.of(product)
            every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("ledgertxn_minexact", emptyList())
            every { orderRepository.save(any()) } answers { firstArg() }

            val detail = service.placeOrder("buyer_1", "merchant_1", listOf(OrderItemRequest("product_1", 3)), "addr")

            Then("it succeeds -- meeting the minimum exactly is not the same as falling below it") {
                detail.order.totalAmount shouldBe BigDecimal("6000")
            }
        }

        When("a real active time deal exists for the product with enough remaining quantity") {
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            every { accountRepository.findById("account_merchant") } returns Optional.of(merchantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
            every { merchantProductRepository.findById("product_1") } returns Optional.of(product)
            every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("ledgertxn_deal", emptyList())
            every { orderRepository.save(any()) } answers { firstArg() }
            val deal = TimeDeal(
                id = "time_deal_1", merchantId = "merchant_1", productId = "product_1", dealPrice = BigDecimal("1500"),
                originalPrice = BigDecimal("2000"), totalQuantity = 10, remainingQuantity = 4,
                startsAt = java.time.Instant.now().minusSeconds(60), endsAt = java.time.Instant.now().plusSeconds(3600),
            )
            every { timeDealRepository.findActiveDealForProduct("product_1", any()) } returns deal
            every { timeDealRepository.save(any()) } answers { firstArg() }

            val detail = service.placeOrder("buyer_1", "merchant_1", listOf(OrderItemRequest("product_1", 3)), "addr")

            Then("the real deal price is charged instead of the normal price, and remaining quantity is decremented") {
                detail.items.single().unitPrice shouldBe BigDecimal("1500")
                detail.order.totalAmount shouldBe BigDecimal("4500")
                deal.remainingQuantity shouldBe 1
            }
        }

        When("a real time deal exists but doesn't have enough remaining quantity for the whole line") {
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            every { accountRepository.findById("account_merchant") } returns Optional.of(merchantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
            every { merchantProductRepository.findById("product_1") } returns Optional.of(product)
            every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("ledgertxn_falloff", emptyList())
            every { orderRepository.save(any()) } answers { firstArg() }
            val scarceDeal = TimeDeal(
                id = "time_deal_2", merchantId = "merchant_1", productId = "product_1", dealPrice = BigDecimal("1500"),
                originalPrice = BigDecimal("2000"), totalQuantity = 10, remainingQuantity = 2,
                startsAt = java.time.Instant.now().minusSeconds(60), endsAt = java.time.Instant.now().plusSeconds(3600),
            )
            every { timeDealRepository.findActiveDealForProduct("product_1", any()) } returns scarceDeal

            val detail = service.placeOrder("buyer_1", "merchant_1", listOf(OrderItemRequest("product_1", 3)), "addr")

            Then("it honestly falls through to the normal price rather than a fabricated partial-deal split") {
                detail.items.single().unitPrice shouldBe BigDecimal("2000")
                scarceDeal.remainingQuantity shouldBe 2
            }
        }

        When("ordering from your own store") {
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)

            Then("it throws SelfOrderException") {
                try {
                    service.placeOrder("seller_1", "merchant_1", listOf(OrderItemRequest("product_1", 1)), "addr")
                    error("expected SelfOrderException")
                } catch (e: SelfOrderException) {
                    // expected
                }
            }
        }

        When("ordering a product that belongs to a DIFFERENT merchant") {
            val otherProduct = MerchantProduct(id = "product_2", merchantId = "merchant_OTHER", name = "Not this store's item", price = BigDecimal("500"))
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            every { accountRepository.findById("account_merchant") } returns Optional.of(merchantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
            every { merchantProductRepository.findById("product_2") } returns Optional.of(otherProduct)

            Then("it throws OrderProductNotFoundException, not silently mixing merchants into one order") {
                try {
                    service.placeOrder("buyer_1", "merchant_1", listOf(OrderItemRequest("product_2", 1)), "addr")
                    error("expected OrderProductNotFoundException")
                } catch (e: OrderProductNotFoundException) {
                    // expected
                }
            }
        }

        When("ordering with zero quantity") {
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            every { accountRepository.findById("account_merchant") } returns Optional.of(merchantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount

            Then("it throws InvalidQuantityException") {
                try {
                    service.placeOrder("buyer_1", "merchant_1", listOf(OrderItemRequest("product_1", 0)), "addr")
                    error("expected InvalidQuantityException")
                } catch (e: InvalidQuantityException) {
                    // expected
                }
            }
        }

        When("placing an order with an empty item list") {
            Then("it throws EmptyOrderException before even looking up the merchant") {
                try {
                    service.placeOrder("buyer_1", "merchant_1", emptyList(), "addr")
                    error("expected EmptyOrderException")
                } catch (e: EmptyOrderException) {
                    // expected
                }
            }
        }

        When("placing an order with a delivery address longer than the real 500-char DB column bound") {
            Then("it throws InvalidDeliveryAddressException rather than risking a raw DB insert failure") {
                try {
                    service.placeOrder("buyer_1", "merchant_1", listOf(OrderItemRequest("product_1", 1)), "x".repeat(501))
                    error("expected InvalidDeliveryAddressException")
                } catch (e: InvalidDeliveryAddressException) {
                    // expected
                }
            }
        }
    }

    Given("a real placed order") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val orderRepository = mockk<OrderRepository>()
        val orderItemRepository = mockk<OrderItemRepository>(relaxed = true)
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        // Real buyer order-status notifications (2026-07-20) -- explicit stub, same
        // known "relaxed mockk can't correctly infer JpaRepository's generic save()
        // signature" gotcha this project's own tests already document repeatedly.
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val priceTierRepository = mockk<ProductPriceTierRepository>(relaxed = true)
        val riderRepository = mockk<RiderRepository>()
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val timeDealRepository = mockk<TimeDealRepository>()
        every { timeDealRepository.findActiveDealForProduct(any(), any()) } returns null
        val affiliateService = mockk<AffiliateService>()
        every { affiliateService.payCommissionIfReferred(any(), any(), any(), any()) } returns Unit
        val autoTopUpService = mockk<rw.itunda.account.AutoTopUpService>(relaxed = true)
        every { autoTopUpService.ensureSufficientPayBalance(any(), any(), any()) } answers { secondArg() }
        val service = OrderService(
            merchantRepository, merchantProductRepository, orderRepository, orderItemRepository,
            accountRepository, ledgerService, transactionRepository, fraudRuleEngine, ledgerEntryRepository,
            notificationRepository, priceTierRepository, riderRepository, pushNotificationService,
            timeDealRepository, affiliateService, autoTopUpService,
        )
        val merchant = Merchant(id = "merchant_1", ownerUserId = "seller_1", accountId = "account_merchant", businessName = "Kigali Store", status = MerchantStatus.ACTIVE)
        val order = Order(
            id = "order_1", buyerId = "buyer_1", merchantId = "merchant_1", deliveryAddress = "addr",
            totalAmount = BigDecimal("6000"), fee = BigDecimal("90"), transactionId = "ledgertxn_1", status = OrderStatus.PLACED,
        )

        When("the real seller advances status PLACED -> PACKED") {
            every { merchantRepository.findByOwnerUserId("seller_1") } returns merchant
            every { orderRepository.findById("order_1") } returns Optional.of(order)
            every { orderRepository.save(any()) } answers { firstArg() }

            val result = service.updateOrderStatus("seller_1", "order_1", OrderStatus.PACKED)

            Then("it advances exactly one step") {
                result.status shouldBe OrderStatus.PACKED
            }

            Then("it real-notifies the buyer, not the seller") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "buyer_1" && it.type == "COMMERCE_ORDER_UPDATE" }) }
            }
        }

        When("the real seller tries to skip PLACED straight to DELIVERED") {
            every { merchantRepository.findByOwnerUserId("seller_1") } returns merchant
            every { orderRepository.findById("order_1") } returns Optional.of(order)

            Then("it throws InvalidOrderStatusTransitionException rather than silently skipping steps") {
                try {
                    service.updateOrderStatus("seller_1", "order_1", OrderStatus.DELIVERED)
                    error("expected InvalidOrderStatusTransitionException")
                } catch (e: InvalidOrderStatusTransitionException) {
                    // expected
                }
            }
        }

        When("someone who isn't the real seller tries to update the order status") {
            every { merchantRepository.findByOwnerUserId("stranger") } returns null

            Then("it throws MerchantNotFoundException (not registered as a merchant at all)") {
                try {
                    service.updateOrderStatus("stranger", "order_1", OrderStatus.PACKED)
                    error("expected MerchantNotFoundException")
                } catch (e: MerchantNotFoundException) {
                    // expected
                }
            }
        }

        When("a real buyer views their own order detail") {
            every { orderRepository.findById("order_1") } returns Optional.of(order)
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            every { orderItemRepository.findByOrderId("order_1") } returns emptyList()

            val detail = service.getOrderDetail("buyer_1", "order_1")

            Then("it succeeds") {
                detail.order.id shouldBe "order_1"
            }
        }

        When("a stranger (neither buyer nor seller) tries to view the order detail") {
            every { orderRepository.findById("order_1") } returns Optional.of(order)
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)

            Then("it throws OrderNotFoundException, not a 403 that would confirm the order exists") {
                try {
                    service.getOrderDetail("stranger", "order_1")
                    error("expected OrderNotFoundException")
                } catch (e: OrderNotFoundException) {
                    // expected
                }
            }
        }

        When("the real buyer cancels a real PLACED order") {
            every { orderRepository.findById("order_1") } returns Optional.of(order)
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            val originalEntries = listOf(
                LedgerEntry(id = "le_1", transactionId = "ledgertxn_1", accountId = "account_buyer", accountType = LedgerAccountType.WALLET, direction = LedgerDirection.DEBIT, amount = BigDecimal("6000"), currency = "RWF", balanceAfter = BigDecimal("94000"), memo = "Order - Kigali Store"),
                LedgerEntry(id = "le_2", transactionId = "ledgertxn_1", accountId = "account_merchant", accountType = LedgerAccountType.WALLET, direction = LedgerDirection.CREDIT, amount = BigDecimal("5910.00"), currency = "RWF", balanceAfter = BigDecimal("5910.00"), memo = "Order collection - Kigali Store"),
                LedgerEntry(id = "le_3", transactionId = "ledgertxn_1", accountId = "fee_revenue", accountType = LedgerAccountType.FEE_REVENUE, direction = LedgerDirection.CREDIT, amount = BigDecimal("90.00"), currency = "RWF", balanceAfter = BigDecimal("90.00"), memo = "Order fee - Kigali Store"),
            )
            every { ledgerEntryRepository.findByTransactionId("ledgertxn_1") } returns originalEntries
            val legsSlot = slot<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("refund_txn_1", emptyList())
            every { orderRepository.save(any()) } answers { firstArg() }
            val stockedProduct = MerchantProduct(
                id = "product_limited", merchantId = "merchant_1", name = "Limited beans",
                price = BigDecimal("2000"), stockQuantity = 1,
            )
            every { orderItemRepository.findByOrderId("order_1") } returns listOf(
                OrderItem("line_1", "order_1", "product_limited", "Limited beans", BigDecimal("2000"), 2),
            )
            every { merchantProductRepository.findById("product_limited") } returns Optional.of(stockedProduct)
            every { merchantProductRepository.saveAll(any<List<MerchantProduct>>()) } answers { firstArg() }

            val result = service.cancelOrder("buyer_1", "order_1")

            Then("it flips every original leg's direction and marks the order CANCELLED with a real refund transaction id") {
                result.status shouldBe OrderStatus.CANCELLED
                result.refundTransactionId shouldBe "refund_txn_1"

                val legs = legsSlot.captured
                legs.first { it.accountId == "account_buyer" }.direction shouldBe LedgerDirection.CREDIT
                legs.first { it.accountId == "account_merchant" }.direction shouldBe LedgerDirection.DEBIT
                legs.first { it.accountId == "fee_revenue" }.direction shouldBe LedgerDirection.DEBIT
            }

            Then("it restores every finite item quantity to the live catalog") {
                stockedProduct.stockQuantity shouldBe 3
            }

            Then("it does NOT notify the buyer about their own action") {
                verify(exactly = 0) { notificationRepository.save(any()) }
            }
        }

        When("the real seller cancels a real PLACED order") {
            every { orderRepository.findById("order_1") } returns Optional.of(order)
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            val originalEntries = listOf(
                LedgerEntry(id = "le_1", transactionId = "ledgertxn_1", accountId = "account_buyer", accountType = LedgerAccountType.WALLET, direction = LedgerDirection.DEBIT, amount = BigDecimal("6000"), currency = "RWF", balanceAfter = BigDecimal("94000"), memo = "Order - Kigali Store"),
                LedgerEntry(id = "le_2", transactionId = "ledgertxn_1", accountId = "account_merchant", accountType = LedgerAccountType.WALLET, direction = LedgerDirection.CREDIT, amount = BigDecimal("5910.00"), currency = "RWF", balanceAfter = BigDecimal("5910.00"), memo = "Order collection - Kigali Store"),
            )
            every { ledgerEntryRepository.findByTransactionId("ledgertxn_1") } returns originalEntries
            every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("refund_txn_2", emptyList())
            every { orderRepository.save(any()) } answers { firstArg() }

            service.cancelOrder("seller_1", "order_1")

            Then("it real-notifies the buyer, since the SELLER was the one who cancelled") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "buyer_1" && it.type == "COMMERCE_ORDER_UPDATE" }) }
            }
        }

        When("someone tries to cancel an order that's already PACKED") {
            val packedOrder = Order(
                id = "order_1", buyerId = "buyer_1", merchantId = "merchant_1", deliveryAddress = "addr",
                totalAmount = BigDecimal("6000"), fee = BigDecimal("90"), transactionId = "ledgertxn_1", status = OrderStatus.PACKED,
            )
            every { orderRepository.findById("order_1") } returns Optional.of(packedOrder)
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)

            Then("it throws InvalidOrderStatusTransitionException rather than cancelling mid-fulfillment") {
                try {
                    service.cancelOrder("buyer_1", "order_1")
                    error("expected InvalidOrderStatusTransitionException")
                } catch (e: InvalidOrderStatusTransitionException) {
                    // expected
                }
            }
        }

        When("a stranger tries to cancel someone else's order") {
            every { orderRepository.findById("order_1") } returns Optional.of(order)
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)

            Then("it throws OrderNotFoundException, not a 403 that would confirm the order exists") {
                try {
                    service.cancelOrder("stranger", "order_1")
                    error("expected OrderNotFoundException")
                } catch (e: OrderNotFoundException) {
                    // expected
                }
            }
        }
    }

    Given("a real itunda rider fleet delivering a real PACKED Commerce order") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val orderRepository = mockk<OrderRepository>()
        val orderItemRepository = mockk<OrderItemRepository>(relaxed = true)
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val priceTierRepository = mockk<ProductPriceTierRepository>(relaxed = true)
        val riderRepository = mockk<RiderRepository>()
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val timeDealRepository = mockk<TimeDealRepository>()
        every { timeDealRepository.findActiveDealForProduct(any(), any()) } returns null
        val affiliateService = mockk<AffiliateService>()
        every { affiliateService.payCommissionIfReferred(any(), any(), any(), any()) } returns Unit
        val autoTopUpService = mockk<rw.itunda.account.AutoTopUpService>(relaxed = true)
        every { autoTopUpService.ensureSufficientPayBalance(any(), any(), any()) } answers { secondArg() }
        val service = OrderService(
            merchantRepository, merchantProductRepository, orderRepository, orderItemRepository,
            accountRepository, ledgerService, transactionRepository, fraudRuleEngine, ledgerEntryRepository,
            notificationRepository, priceTierRepository, riderRepository, pushNotificationService,
            timeDealRepository, affiliateService, autoTopUpService,
        )
        val merchant = Merchant(id = "merchant_1", ownerUserId = "seller_1", accountId = "account_merchant", businessName = "Kigali Store", status = MerchantStatus.ACTIVE)
        val rider = Rider(id = "rider_1", userId = "rider_user_1", accountId = "account_rider", available = true)
        val packedOrder = Order(
            id = "order_1", buyerId = "buyer_1", merchantId = "merchant_1", deliveryAddress = "addr",
            totalAmount = BigDecimal("6000"), fee = BigDecimal("90"), transactionId = "ledgertxn_1", status = OrderStatus.PACKED,
        )

        When("an available registered rider claims an unclaimed PACKED order") {
            every { riderRepository.findByUserId("rider_user_1") } returns rider
            every { orderRepository.existsByRiderIdAndStatusIn("rider_1", listOf(OrderStatus.SHIPPED)) } returns false
            every { orderRepository.findById("order_1") } returns Optional.of(packedOrder)
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            every { orderRepository.save(any()) } answers { firstArg() }

            val result = service.claimDelivery("rider_user_1", "order_1")

            Then("it assigns the rider and advances to SHIPPED") {
                result.riderId shouldBe "rider_1"
                result.status shouldBe OrderStatus.SHIPPED
            }
        }

        When("a rider tries to claim an order that's not yet PACKED") {
            val placedOrder = Order(
                id = "order_2", buyerId = "buyer_1", merchantId = "merchant_1", deliveryAddress = "addr",
                totalAmount = BigDecimal("6000"), fee = BigDecimal("90"), transactionId = "ledgertxn_1", status = OrderStatus.PLACED,
            )
            every { riderRepository.findByUserId("rider_user_1") } returns rider
            every { orderRepository.existsByRiderIdAndStatusIn("rider_1", listOf(OrderStatus.SHIPPED)) } returns false
            every { orderRepository.findById("order_2") } returns Optional.of(placedOrder)

            Then("it throws DeliveryAlreadyClaimedException, the same real 'not available' message an already-claimed order gives") {
                try {
                    service.claimDelivery("rider_user_1", "order_2")
                    error("expected DeliveryAlreadyClaimedException")
                } catch (e: DeliveryAlreadyClaimedException) {
                    // expected
                }
            }
        }

        When("an offline rider tries to claim a delivery") {
            val offlineRider = Rider(id = "rider_2", userId = "rider_user_2", accountId = "account_rider_2", available = false)
            every { riderRepository.findByUserId("rider_user_2") } returns offlineRider

            Then("it throws RiderNotAvailableException") {
                try {
                    service.claimDelivery("rider_user_2", "order_1")
                    error("expected RiderNotAvailableException")
                } catch (e: RiderNotAvailableException) {
                    // expected
                }
            }
        }

        When("a rider already carrying an active delivery tries to claim another") {
            every { riderRepository.findByUserId("rider_user_1") } returns rider
            every { orderRepository.existsByRiderIdAndStatusIn("rider_1", listOf(OrderStatus.SHIPPED)) } returns true

            Then("it throws RiderAlreadyOnDeliveryException -- one delivery at a time") {
                try {
                    service.claimDelivery("rider_user_1", "order_1")
                    error("expected RiderAlreadyOnDeliveryException")
                } catch (e: RiderAlreadyOnDeliveryException) {
                    // expected
                }
            }
        }

        When("the claiming rider completes a real SHIPPED delivery") {
            val shippedOrder = Order(
                id = "order_1", buyerId = "buyer_1", merchantId = "merchant_1", deliveryAddress = "addr",
                totalAmount = BigDecimal("6000"), fee = BigDecimal("90"), transactionId = "ledgertxn_1",
                status = OrderStatus.SHIPPED, riderId = "rider_1",
            )
            every { riderRepository.findByUserId("rider_user_1") } returns rider
            every { orderRepository.findById("order_1") } returns Optional.of(shippedOrder)
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            every { orderRepository.save(any()) } answers { firstArg() }

            val result = service.completeDelivery("rider_user_1", "order_1")

            Then("it marks the order DELIVERED") {
                result.status shouldBe OrderStatus.DELIVERED
            }
        }

        When("a different rider (not the one who claimed it) tries to complete the delivery") {
            val shippedOrder = Order(
                id = "order_1", buyerId = "buyer_1", merchantId = "merchant_1", deliveryAddress = "addr",
                totalAmount = BigDecimal("6000"), fee = BigDecimal("90"), transactionId = "ledgertxn_1",
                status = OrderStatus.SHIPPED, riderId = "rider_1",
            )
            val otherRider = Rider(id = "rider_2", userId = "rider_user_2", accountId = "account_rider_2", available = true)
            every { riderRepository.findByUserId("rider_user_2") } returns otherRider
            every { orderRepository.findById("order_1") } returns Optional.of(shippedOrder)

            Then("it throws InvalidOrderStatusTransitionException, not letting a different rider finish someone else's delivery") {
                try {
                    service.completeDelivery("rider_user_2", "order_1")
                    error("expected InvalidOrderStatusTransitionException")
                } catch (e: InvalidOrderStatusTransitionException) {
                    // expected
                }
            }
        }

        When("a merchant tries to self-declare SHIPPED once a rider has already claimed the delivery") {
            val riderClaimedOrder = Order(
                id = "order_1", buyerId = "buyer_1", merchantId = "merchant_1", deliveryAddress = "addr",
                totalAmount = BigDecimal("6000"), fee = BigDecimal("90"), transactionId = "ledgertxn_1",
                status = OrderStatus.PACKED, riderId = "rider_1",
            )
            every { merchantRepository.findByOwnerUserId("seller_1") } returns merchant
            every { orderRepository.findById("order_1") } returns Optional.of(riderClaimedOrder)

            Then("it throws InvalidOrderStatusTransitionException -- the rider owns this step now") {
                try {
                    service.updateOrderStatus("seller_1", "order_1", OrderStatus.SHIPPED)
                    error("expected InvalidOrderStatusTransitionException")
                } catch (e: InvalidOrderStatusTransitionException) {
                    // expected
                }
            }
        }

        When("a rider requests available deliveries with no known location") {
            val noLocationRider = Rider(id = "rider_3", userId = "rider_user_3", accountId = "account_rider_3", available = true)
            every { riderRepository.findByUserId("rider_user_3") } returns noLocationRider
            every {
                orderRepository.findByStatusAndRiderIdIsNullOrderByCreatedAtAsc(OrderStatus.PACKED, any())
            } returns org.springframework.data.domain.PageImpl(listOf(packedOrder))

            val page = service.getAvailableDeliveries("rider_user_3", org.springframework.data.domain.PageRequest.of(0, 20))

            Then("it real-falls back to createdAt order without distance ranking") {
                page.content shouldBe listOf(packedOrder)
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
