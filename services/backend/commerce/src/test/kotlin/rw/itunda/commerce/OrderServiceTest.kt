package rw.itunda.commerce

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantProduct
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.Order
import rw.itunda.core.domain.OrderStatus
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.OrderItemRepository
import rw.itunda.core.repository.OrderRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.util.Optional

class OrderServiceTest : BehaviorSpec({

    fun wallet(id: String, userId: String) = Wallet(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test wallet",
        type = WalletType.MAIN, balance = BigDecimal("100000"), availableBalance = BigDecimal("100000"),
    )

    Given("a real merchant with a real product catalog") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val orderRepository = mockk<OrderRepository>()
        val orderItemRepository = mockk<OrderItemRepository>(relaxed = true)
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        // Not relaxed for save() specifically: mockk's relaxed default can't correctly
        // infer JpaRepository's generic `<S extends T> S save(S)` signature, returning a
        // raw mock Object that then fails a real ClassCastException back in the caller --
        // same reason MerchantServiceTest/WalletServiceTest explicitly stub this too.
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        every { transactionRepository.save(any()) } answers { firstArg() }
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val service = OrderService(
            merchantRepository, merchantProductRepository, orderRepository, orderItemRepository,
            walletRepository, ledgerService, transactionRepository, fraudRuleEngine,
        )

        val merchant = Merchant(id = "merchant_1", ownerUserId = "seller_1", walletId = "wallet_merchant", businessName = "Kigali Store", status = MerchantStatus.ACTIVE)
        val merchantWallet = wallet("wallet_merchant", "seller_1")
        val buyerWallet = wallet("wallet_buyer", "buyer_1")
        val product = MerchantProduct(id = "product_1", merchantId = "merchant_1", name = "Coffee beans", price = BigDecimal("2000"))

        When("a real buyer places a real order for 3 units") {
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            every { walletRepository.findById("wallet_merchant") } returns Optional.of(merchantWallet)
            every { walletRepository.findByUserIdAndType("buyer_1", WalletType.MAIN) } returns buyerWallet
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
                val buyerLeg = legs.first { it.accountId == "wallet_buyer" }
                val merchantLeg = legs.first { it.accountId == "wallet_merchant" }
                val feeLeg = legs.first { it.accountId == "fee_revenue" }
                buyerLeg.amount shouldBe BigDecimal("6000")
                merchantLeg.amount shouldBe BigDecimal("5910.00")
                feeLeg.amount shouldBe BigDecimal("90.00")
                feeLeg.accountType shouldBe LedgerAccountType.FEE_REVENUE
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
            every { walletRepository.findById("wallet_merchant") } returns Optional.of(merchantWallet)
            every { walletRepository.findByUserIdAndType("buyer_1", WalletType.MAIN) } returns buyerWallet
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
            every { walletRepository.findById("wallet_merchant") } returns Optional.of(merchantWallet)
            every { walletRepository.findByUserIdAndType("buyer_1", WalletType.MAIN) } returns buyerWallet

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
    }

    Given("a real placed order") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val orderRepository = mockk<OrderRepository>()
        val orderItemRepository = mockk<OrderItemRepository>(relaxed = true)
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val service = OrderService(
            merchantRepository, merchantProductRepository, orderRepository, orderItemRepository,
            walletRepository, ledgerService, transactionRepository, fraudRuleEngine,
        )
        val merchant = Merchant(id = "merchant_1", ownerUserId = "seller_1", walletId = "wallet_merchant", businessName = "Kigali Store", status = MerchantStatus.ACTIVE)
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
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
