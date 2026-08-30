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

// Real fix (2026-08-26): split out of OrderServiceTest.kt once that file grew
// past its file-size-lint baseline. This Given block was already fully self-
// contained (its own fresh mocks + its own OrderService instance, no shared
// spec-level state) -- a clean, zero-risk test-file split by concern.
class OrderDeliveryServiceTest : BehaviorSpec({

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
        val webhookDeliveryService = mockk<rw.itunda.merchant.WebhookDeliveryService>(relaxed = true)
        val service = OrderService(
            merchantRepository, merchantProductRepository, orderRepository, orderItemRepository,
            accountRepository, ledgerService, transactionRepository, fraudRuleEngine, ledgerEntryRepository,
            notificationRepository, priceTierRepository, riderRepository, pushNotificationService,
            timeDealRepository, affiliateService, autoTopUpService, webhookDeliveryService,
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
