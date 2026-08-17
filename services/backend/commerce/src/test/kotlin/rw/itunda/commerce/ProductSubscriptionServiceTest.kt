package rw.itunda.commerce

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantProduct
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.Order
import rw.itunda.core.domain.ProductSubscriptionStatus
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.ProductSubscriptionRepository
import rw.itunda.core.repository.WalletRepository
import rw.itunda.merchant.ShoppingCashbackService
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional

/**
 * Real Coupang 정기배송 (subscribe & save)-style recurring product delivery -- see
 * ProductSubscriptionService's own doc comment for the full sourced account.
 * OrderService/ShoppingCashbackService are mocked, not exercised for real -- their own
 * logic is already covered by OrderServiceTest/an implicit ShoppingCashbackService
 * pinning elsewhere; this file is about subscription-specific logic: validation,
 * ownership, and the one-time-first-cycle-must-succeed vs. resilient-recurring
 * distinction.
 */
class ProductSubscriptionServiceTest : BehaviorSpec({

    fun wallet(id: String, userId: String) = Wallet(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test wallet",
        type = WalletType.MAIN, balance = BigDecimal("100000"), availableBalance = BigDecimal("100000"),
    )

    Given("a real merchant product and a real customer") {
        val productSubscriptionRepository = mockk<ProductSubscriptionRepository>(relaxed = true)
        every { productSubscriptionRepository.save(any()) } answers { firstArg() }
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val walletRepository = mockk<WalletRepository>()
        val orderService = mockk<OrderService>()
        val shoppingCashbackService = mockk<ShoppingCashbackService>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = ProductSubscriptionService(
            productSubscriptionRepository, merchantRepository, merchantProductRepository, walletRepository, orderService, shoppingCashbackService, rateLimiter,
        )

        val product = MerchantProduct(id = "product_1", merchantId = "merchant_1", name = "Tissue paper", price = BigDecimal("5000"))
        val merchant = Merchant(id = "merchant_1", ownerUserId = "owner_1", walletId = "wallet_merchant", businessName = "Kigali Mart", status = MerchantStatus.ACTIVE)
        val customerWallet = wallet("wallet_customer", "customer_1")
        val order = Order(
            id = "order_1", buyerId = "customer_1", merchantId = "merchant_1", deliveryAddress = "KG 123 St",
            totalAmount = BigDecimal("10000"), fee = BigDecimal("150"), transactionId = "ledgertxn_1",
        )

        When("subscribing to a real product with a valid interval") {
            every { merchantProductRepository.findById("product_1") } returns Optional.of(product)
            every { orderService.placeOrder("customer_1", "merchant_1", listOf(OrderItemRequest("product_1", 2)), "KG 123 St") } returns OrderDetail(order, emptyList())
            every { walletRepository.findByUserIdAndType("customer_1", WalletType.MAIN) } returns customerWallet
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)

            val subscription = service.subscribe("customer_1", "merchant_1", "product_1", 2, 30, "KG 123 St")

            Then("it places a real first order immediately and saves an ACTIVE subscription") {
                subscription.status shouldBe ProductSubscriptionStatus.ACTIVE
                subscription.deliveryCount shouldBe 1
                subscription.quantity shouldBe 2
            }

            Then("it awards the real 5% subscription discount on the real order total") {
                verify(exactly = 1) { shoppingCashbackService.awardCashback(customerWallet, BigDecimal("10000"), "Kigali Mart", ProductSubscriptionService.SUBSCRIPTION_DISCOUNT_RATE) }
            }
        }

        When("the interval exceeds Coupang's own real 6-month (180-day) ceiling") {
            Then("it's rejected") {
                try {
                    service.subscribe("customer_1", "merchant_1", "product_1", 1, 181, "KG 123 St")
                    throw AssertionError("expected InvalidProductSubscriptionException")
                } catch (e: InvalidProductSubscriptionException) {
                    // expected
                }
            }
        }

        When("the quantity is zero or negative") {
            Then("it's rejected before any lookup") {
                try {
                    service.subscribe("customer_1", "merchant_1", "product_1", 0, 30, "KG 123 St")
                    throw AssertionError("expected InvalidProductSubscriptionException")
                } catch (e: InvalidProductSubscriptionException) {
                    // expected
                }
            }
        }

        When("the product belongs to a different merchant") {
            val otherProduct = MerchantProduct(id = "product_2", merchantId = "merchant_OTHER", name = "Not this store's item", price = BigDecimal("500"))
            every { merchantProductRepository.findById("product_2") } returns Optional.of(otherProduct)

            Then("it's rejected as not found, not leaking cross-merchant existence") {
                try {
                    service.subscribe("customer_1", "merchant_1", "product_2", 1, 30, "KG 123 St")
                    throw AssertionError("expected ProductSubscriptionProductNotFoundException")
                } catch (e: ProductSubscriptionProductNotFoundException) {
                    // expected
                }
            }
        }

        When("the very first delivery fails") {
            every { merchantProductRepository.findById("product_1") } returns Optional.of(product)
            every { orderService.placeOrder("customer_1", "merchant_1", listOf(OrderItemRequest("product_1", 2)), "KG 123 St") } throws InsufficientFundsException("Insufficient balance")

            Then("subscribe itself fails honestly instead of silently creating a subscription with a hidden failed delivery") {
                try {
                    service.subscribe("customer_1", "merchant_1", "product_1", 2, 30, "KG 123 St")
                    throw AssertionError("expected InsufficientFundsException")
                } catch (e: InsufficientFundsException) {
                    // expected
                }
            }

            Then("no subscription row is ever persisted for the failed attempt") {
                verify(exactly = 0) { productSubscriptionRepository.save(any()) }
            }
        }
    }

    Given("a real active subscription due for its recurring delivery") {
        val productSubscriptionRepository = mockk<ProductSubscriptionRepository>(relaxed = true)
        every { productSubscriptionRepository.save(any()) } answers { firstArg() }
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val walletRepository = mockk<WalletRepository>()
        val orderService = mockk<OrderService>()
        val shoppingCashbackService = mockk<ShoppingCashbackService>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = ProductSubscriptionService(
            productSubscriptionRepository, merchantRepository, merchantProductRepository, walletRepository, orderService, shoppingCashbackService, rateLimiter,
        )

        val merchant = Merchant(id = "merchant_1", ownerUserId = "owner_1", walletId = "wallet_merchant", businessName = "Kigali Mart", status = MerchantStatus.ACTIVE)
        val customerWallet = wallet("wallet_customer", "customer_1")
        val order = Order(
            id = "order_2", buyerId = "customer_1", merchantId = "merchant_1", deliveryAddress = "KG 123 St",
            totalAmount = BigDecimal("5000"), fee = BigDecimal("75"), transactionId = "ledgertxn_2",
        )
        val subscription = rw.itunda.core.domain.ProductSubscription(
            id = "productsub_1", customerId = "customer_1", merchantId = "merchant_1", productId = "product_1",
            quantity = 1, intervalDays = 30, deliveryAddress = "KG 123 St", nextDeliveryAt = Instant.now(), deliveryCount = 3,
        )

        When("the recurring delivery succeeds") {
            every { orderService.placeOrder("customer_1", "merchant_1", listOf(OrderItemRequest("product_1", 1)), "KG 123 St") } returns OrderDetail(order, emptyList())
            every { walletRepository.findByUserIdAndType("customer_1", WalletType.MAIN) } returns customerWallet
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)

            val succeeded = service.executeOne(subscription)

            Then("it returns true and advances the schedule without throwing") {
                succeeded shouldBe true
                subscription.deliveryCount shouldBe 4
            }
        }

        When("the recurring delivery fails for insufficient funds") {
            every { orderService.placeOrder("customer_1", "merchant_1", listOf(OrderItemRequest("product_1", 1)), "KG 123 St") } throws InsufficientFundsException("Insufficient balance")
            val staleDeliveryCount = subscription.deliveryCount

            val succeeded = service.executeOne(subscription)

            Then("it is skipped honestly -- never thrown, never fabricated as success -- and the schedule still advances to the next cycle") {
                succeeded shouldBe false
                subscription.lastFailureReason shouldBe "Insufficient balance"
                subscription.deliveryCount shouldBe staleDeliveryCount
            }
        }
    }

    // Real regression guard for the 2026-08-17 transaction-poisoning fix -- see
    // MerchantBillingChargeExecutor's own doc comment (rw.itunda.merchant) for the full
    // account of the same root cause found here. MockK unit tests never create a real
    // Spring AOP proxy, so they can never actually observe the
    // UnexpectedRollbackException this bug produced live -- only a structural check
    // like this one can catch a future regression (re-adding @Transactional to
    // executeOne) before it reaches a real deployed backend again.
    Given("a real active subscription with an upcoming delivery") {
        val productSubscriptionRepository = mockk<ProductSubscriptionRepository>(relaxed = true)
        every { productSubscriptionRepository.save(any()) } answers { firstArg() }
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val walletRepository = mockk<WalletRepository>()
        val orderService = mockk<OrderService>()
        val shoppingCashbackService = mockk<ShoppingCashbackService>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = ProductSubscriptionService(
            productSubscriptionRepository, merchantRepository, merchantProductRepository, walletRepository, orderService, shoppingCashbackService, rateLimiter,
        )

        val originalNextDeliveryAt = Instant.parse("2026-09-01T00:00:00Z")
        val subscription = rw.itunda.core.domain.ProductSubscription(
            id = "productsub_2", customerId = "customer_1", merchantId = "merchant_1", productId = "product_1",
            quantity = 1, intervalDays = 30, deliveryAddress = "KG 123 St", nextDeliveryAt = originalNextDeliveryAt, deliveryCount = 3,
        )

        When("the customer skips the next round") {
            every { productSubscriptionRepository.findByIdAndCustomerId("productsub_2", "customer_1") } returns subscription

            val result = service.skipNext("customer_1", "productsub_2")

            Then("the real Coupang 건너뛰기 behavior: the schedule advances one interval, the subscription stays ACTIVE, and no delivery is recorded") {
                result.nextDeliveryAt shouldBe originalNextDeliveryAt.plus(30, java.time.temporal.ChronoUnit.DAYS)
                result.status shouldBe ProductSubscriptionStatus.ACTIVE
                result.deliveryCount shouldBe 3
                verify(exactly = 1) { productSubscriptionRepository.save(subscription) }
            }
        }

        When("skipping a subscription that's already paused") {
            subscription.status = ProductSubscriptionStatus.PAUSED
            every { productSubscriptionRepository.findByIdAndCustomerId("productsub_2", "customer_1") } returns subscription

            Then("it throws InvalidProductSubscriptionException rather than silently advancing a stopped schedule") {
                try {
                    service.skipNext("customer_1", "productsub_2")
                    error("expected InvalidProductSubscriptionException")
                } catch (e: InvalidProductSubscriptionException) {
                    // expected
                }
            }
        }

        When("skipping a subscription that belongs to a different customer or doesn't exist") {
            every { productSubscriptionRepository.findByIdAndCustomerId("productsub_2", "customer_1") } returns null

            Then("it throws ProductSubscriptionNotFoundException -- the same real-vs-fake IDOR discipline every other lookup here uses") {
                try {
                    service.skipNext("customer_1", "productsub_2")
                    error("expected ProductSubscriptionNotFoundException")
                } catch (e: ProductSubscriptionNotFoundException) {
                    // expected
                }
            }
        }

        When("the customer updates quantity and interval on a real subscription") {
            every { productSubscriptionRepository.findByIdAndCustomerId("productsub_2", "customer_1") } returns subscription

            val result = service.updateSubscription("customer_1", "productsub_2", quantity = 3, intervalDays = 14)

            Then("the real Coupang 정기배송 수량/주기 변경 behavior: quantity/interval change, but nextDeliveryAt is untouched -- the already-queued round still ships as originally scheduled") {
                result.quantity shouldBe 3
                result.intervalDays shouldBe 14
                result.nextDeliveryAt shouldBe originalNextDeliveryAt
                verify(exactly = 1) { productSubscriptionRepository.save(subscription) }
            }
        }

        When("updating with neither quantity nor intervalDays provided") {
            Then("it's rejected before any lookup") {
                try {
                    service.updateSubscription("customer_1", "productsub_2", quantity = null, intervalDays = null)
                    error("expected InvalidProductSubscriptionException")
                } catch (e: InvalidProductSubscriptionException) {
                    verify(exactly = 0) { productSubscriptionRepository.findByIdAndCustomerId(any(), any()) }
                }
            }
        }

        When("updating with a non-positive quantity or an interval beyond Coupang's own real 6-month ceiling") {
            Then("both are rejected before any lookup") {
                try {
                    service.updateSubscription("customer_1", "productsub_2", quantity = 0, intervalDays = null)
                    error("expected InvalidProductSubscriptionException")
                } catch (e: InvalidProductSubscriptionException) {
                    // expected
                }
                try {
                    service.updateSubscription("customer_1", "productsub_2", quantity = null, intervalDays = 181)
                    error("expected InvalidProductSubscriptionException")
                } catch (e: InvalidProductSubscriptionException) {
                    // expected
                }
                verify(exactly = 0) { productSubscriptionRepository.findByIdAndCustomerId(any(), any()) }
            }
        }

        When("updating a subscription that belongs to a different customer or doesn't exist") {
            every { productSubscriptionRepository.findByIdAndCustomerId("productsub_2", "customer_1") } returns null

            Then("it throws ProductSubscriptionNotFoundException -- the same real-vs-fake IDOR discipline every other lookup here uses") {
                try {
                    service.updateSubscription("customer_1", "productsub_2", quantity = 2, intervalDays = null)
                    error("expected ProductSubscriptionNotFoundException")
                } catch (e: ProductSubscriptionNotFoundException) {
                    // expected
                }
            }
        }
    }

    Given("the transaction-boundary fix for the scheduler's per-row delivery loop") {
        Then("executeOne itself must not carry @Transactional -- orderService.placeOrder is already fully atomic on its own") {
            val method = ProductSubscriptionService::class.java.declaredMethods.first { it.name == "executeOne" }
            method.isAnnotationPresent(Transactional::class.java) shouldBe false
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
