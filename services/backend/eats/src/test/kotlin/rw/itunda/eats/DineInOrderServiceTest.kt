package rw.itunda.eats

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.core.domain.DineInOrderStatus
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantProduct
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.DineInOrderItemRepository
import rw.itunda.core.repository.DineInOrderRepository
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.MenuOptionChoiceRepository
import rw.itunda.core.repository.MenuOptionGroupRepository
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.util.Optional

/**
 * First-ever test coverage for [DineInOrderService] -- a real, pre-existing gap found
 * while wiring its "new table order" restaurant push (item 88 of the push-notification
 * rollout, sibling of `OrderService`/`EatsOrderService`'s own "new order" pushes).
 */
class DineInOrderServiceTest : BehaviorSpec({

    fun wallet(id: String, userId: String) = Wallet(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test wallet",
        type = WalletType.MAIN, balance = BigDecimal("100000"), availableBalance = BigDecimal("100000"),
    )

    Given("a real restaurant with a real dine-in menu") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val dineInOrderRepository = mockk<DineInOrderRepository>(relaxed = true)
        // Same known "relaxed mockk can't correctly infer JpaRepository's generic
        // save() signature" gotcha this project's own tests already document
        // repeatedly (MerchantServiceTest/EatsOrderServiceTest/etc) -- explicit stub.
        every { dineInOrderRepository.save(any()) } answers { firstArg() }
        val dineInOrderItemRepository = mockk<DineInOrderItemRepository>(relaxed = true)
        val menuOptionGroupRepository = mockk<MenuOptionGroupRepository>(relaxed = true)
        every { menuOptionGroupRepository.findByProductIdInOrderByDisplayOrderAsc(any()) } returns emptyList()
        val menuOptionChoiceRepository = mockk<MenuOptionChoiceRepository>(relaxed = true)
        every { menuOptionChoiceRepository.findByGroupIdInOrderByDisplayOrderAsc(any()) } returns emptyList()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        every { transactionRepository.save(any()) } answers { firstArg() }
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = DineInOrderService(
            merchantRepository, merchantProductRepository, dineInOrderRepository, dineInOrderItemRepository,
            menuOptionGroupRepository, menuOptionChoiceRepository, walletRepository, ledgerService,
            transactionRepository, fraudRuleEngine, ledgerEntryRepository, notificationRepository,
            pushNotificationService,
        )

        val restaurant = Merchant(id = "restaurant_1", ownerUserId = "owner_1", walletId = "wallet_restaurant", businessName = "Kigali Grill", status = MerchantStatus.ACTIVE)
        val restaurantWallet = wallet("wallet_restaurant", "owner_1")
        val buyerWallet = wallet("wallet_buyer", "buyer_1")
        val menuItem = MerchantProduct(id = "item_1", merchantId = "restaurant_1", name = "Grilled chicken", price = BigDecimal("3000"))

        When("a real buyer places a real table order for 2 units") {
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            every { walletRepository.findById("wallet_restaurant") } returns Optional.of(restaurantWallet)
            every { walletRepository.findByUserIdAndType("buyer_1", WalletType.MAIN) } returns buyerWallet
            every { merchantProductRepository.findById("item_1") } returns Optional.of(menuItem)
            val legsSlot = slot<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_1", emptyList())

            val detail = service.placeOrder("buyer_1", "restaurant_1", "  Table 12  ", listOf(DineInOrderItemRequest("item_1", 2)))

            Then("it prices from the live menu (3000 x 2 = 6000), splits a 1.5% platform fee, and settles straight to the restaurant wallet with no delivery leg at all") {
                detail.order.itemsSubtotal shouldBe BigDecimal("6000")
                detail.order.platformFee shouldBe BigDecimal("90.00")
                detail.order.totalAmount shouldBe BigDecimal("6000")
                detail.order.tableNumber shouldBe "Table 12"
                detail.items.first().productName shouldBe "Grilled chicken"

                val legs = legsSlot.captured
                legs.size shouldBe 3
                legs.first { it.accountId == "wallet_buyer" }.amount shouldBe BigDecimal("6000")
                legs.first { it.accountId == "wallet_restaurant" }.amount shouldBe BigDecimal("5910.00")
                legs.first { it.accountType == LedgerAccountType.FEE_REVENUE }.amount shouldBe BigDecimal("90.00")
            }

            Then("it real-alerts the real restaurant owner of the new table order") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "owner_1" && it.type == "DINE_IN_ORDER_PLACED" }) }
            }

            Then("the restaurant owner also gets a real push notification, not just the in-app one") {
                verify(exactly = 1) { pushNotificationService.sendToUser("owner_1", "New table order", any(), any()) }
            }
        }

        When("ordering a menu item whose closing/surplus deal has already expired") {
            // Real 마감할인 (closing/surplus discount) expiry enforcement -- see
            // MerchantProduct.isSurplusDeal/surplusExpiresAt's own doc comment and
            // DineInOrderService.placeOrder's own new comment above the check this test
            // covers. Same shared MerchantProduct catalog used as a menu item here.
            val expiredDealItem = MerchantProduct(
                id = "item_expired_surplus", merchantId = "restaurant_1", name = "Closing-time soup",
                price = BigDecimal("1000"), isSurplusDeal = true,
                surplusExpiresAt = java.time.Instant.now().minusSeconds(3600),
            )
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            every { walletRepository.findById("wallet_restaurant") } returns Optional.of(restaurantWallet)
            every { walletRepository.findByUserIdAndType("buyer_1", WalletType.MAIN) } returns buyerWallet
            every { merchantProductRepository.findById("item_expired_surplus") } returns Optional.of(expiredDealItem)

            Then("it throws DineInMenuItemSurplusDealExpiredException before debiting a wallet") {
                try {
                    service.placeOrder("buyer_1", "restaurant_1", "Table 12", listOf(DineInOrderItemRequest("item_expired_surplus", 1)))
                    error("expected DineInMenuItemSurplusDealExpiredException")
                } catch (e: DineInMenuItemSurplusDealExpiredException) {
                    // expected
                }
            }
        }

        When("a table order is still inside its payment transaction") {
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            every { walletRepository.findById("wallet_restaurant") } returns Optional.of(restaurantWallet)
            every { walletRepository.findByUserIdAndType("buyer_1", WalletType.MAIN) } returns buyerWallet
            every { merchantProductRepository.findById("item_1") } returns Optional.of(menuItem)
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_after_commit", emptyList())

            TransactionSynchronizationManager.initSynchronization()
            try {
                service.placeOrder("buyer_1", "restaurant_1", "Table 12", listOf(DineInOrderItemRequest("item_1", 2)))

                Then("the restaurant notification is durable while its external push is withheld") {
                    verify(exactly = 1) { notificationRepository.save(match { it.userId == "owner_1" && it.type == "DINE_IN_ORDER_PLACED" }) }
                    verify(exactly = 0) { pushNotificationService.sendToUser(any(), any(), any(), any()) }
                }

                Then("the restaurant receives exactly one order push after commit") {
                    TransactionSynchronizationManager.getSynchronizations().single().afterCommit()
                    verify(exactly = 1) { pushNotificationService.sendToUser("owner_1", "New table order", any(), any()) }
                }
            } finally {
                TransactionSynchronizationManager.clearSynchronization()
            }
        }

        When("a merchant owner tries to order from their own restaurant") {
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)

            Then("it real-rejects the self-order") {
                io.kotest.assertions.throwables.shouldThrow<SelfDineInOrderException> {
                    service.placeOrder("owner_1", "restaurant_1", "Table 3", listOf(DineInOrderItemRequest("item_1", 1)))
                }
            }
        }

        When("a buyer submits an order with zero items") {
            Then("it real-rejects the empty order") {
                io.kotest.assertions.throwables.shouldThrow<EmptyDineInOrderException> {
                    service.placeOrder("buyer_1", "restaurant_1", "Table 3", emptyList())
                }
            }
        }

        When("a buyer submits a blank table number") {
            Then("it real-rejects the blank table") {
                io.kotest.assertions.throwables.shouldThrow<InvalidDineInTableException> {
                    service.placeOrder("buyer_1", "restaurant_1", "   ", listOf(DineInOrderItemRequest("item_1", 1)))
                }
            }
        }
    }

    Given("a real placed dine-in order") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>(relaxed = true)
        val dineInOrderRepository = mockk<DineInOrderRepository>()
        val dineInOrderItemRepository = mockk<DineInOrderItemRepository>(relaxed = true)
        val menuOptionGroupRepository = mockk<MenuOptionGroupRepository>(relaxed = true)
        val menuOptionChoiceRepository = mockk<MenuOptionChoiceRepository>(relaxed = true)
        val walletRepository = mockk<WalletRepository>(relaxed = true)
        val ledgerService = mockk<LedgerService>(relaxed = true)
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = DineInOrderService(
            merchantRepository, merchantProductRepository, dineInOrderRepository, dineInOrderItemRepository,
            menuOptionGroupRepository, menuOptionChoiceRepository, walletRepository, ledgerService,
            transactionRepository, fraudRuleEngine, ledgerEntryRepository, notificationRepository,
            pushNotificationService,
        )

        val restaurant = Merchant(id = "restaurant_1", ownerUserId = "owner_1", walletId = "wallet_restaurant", businessName = "Kigali Grill", status = MerchantStatus.ACTIVE)
        // Fresh instance per When block -- DineInOrder.status is a mutable var, and
        // updateStatus/cancelOrder mutate it in place, so a single shared instance
        // would leak PLACED -> ACCEPTED state from one test into the next.
        fun freshPlacedOrder() = rw.itunda.core.domain.DineInOrder(
            id = "dine_in_order_1", buyerId = "buyer_1", restaurantId = "restaurant_1", tableNumber = "12",
            itemsSubtotal = BigDecimal("6000"), platformFee = BigDecimal("90.00"), totalAmount = BigDecimal("6000"),
            transactionId = "ledgertxn_1", status = DineInOrderStatus.PLACED,
        )

        When("the restaurant advances the order PLACED -> ACCEPTED") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns restaurant
            every { dineInOrderRepository.findById("dine_in_order_1") } returns Optional.of(freshPlacedOrder())
            every { dineInOrderRepository.save(any()) } answers { firstArg() }

            val updated = service.updateStatus("owner_1", "dine_in_order_1", DineInOrderStatus.ACCEPTED)

            Then("it real-advances the status one step") {
                updated.status shouldBe DineInOrderStatus.ACCEPTED
            }
        }

        When("the restaurant tries to skip straight to SERVED") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns restaurant
            every { dineInOrderRepository.findById("dine_in_order_1") } returns Optional.of(freshPlacedOrder())

            Then("it real-rejects the skipped transition") {
                io.kotest.assertions.throwables.shouldThrow<InvalidDineInStatusTransitionException> {
                    service.updateStatus("owner_1", "dine_in_order_1", DineInOrderStatus.SERVED)
                }
            }
        }

        When("the restaurant cancels a real PLACED order") {
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            every { dineInOrderRepository.findById("dine_in_order_1") } returns Optional.of(freshPlacedOrder())
            every { dineInOrderRepository.save(any()) } answers { firstArg() }
            every { ledgerEntryRepository.findByTransactionId("ledgertxn_1") } returns listOf(
                rw.itunda.core.domain.LedgerEntry(
                    id = "entry_1", transactionId = "ledgertxn_1", accountId = "wallet_buyer",
                    accountType = LedgerAccountType.WALLET, direction = rw.itunda.core.domain.LedgerDirection.DEBIT,
                    amount = BigDecimal("6000"), currency = "RWF", balanceAfter = BigDecimal("94000"), memo = "Dine-in order",
                ),
            )
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("refundtxn_1", emptyList())

            val cancelled = service.cancelOrder("owner_1", "dine_in_order_1")

            Then("it real-refunds and real-notifies (in-app and push) the buyer, but never re-alerts the restaurant that just cancelled its own order") {
                cancelled.status shouldBe DineInOrderStatus.CANCELLED
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "buyer_1" && it.type == "DINE_IN_ORDER_UPDATE" }) }
                // Real push (item 124) -- the buyer genuinely should get this, same
                // urgency as every other real order-status push; what this test's own
                // name actually guards against is the RESTAURANT owner (who took the
                // cancel action themselves) getting a redundant push about their own
                // action, not the buyer being silent.
                verify(exactly = 1) { pushNotificationService.sendToUser("buyer_1", any(), any(), any()) }
                verify(exactly = 0) { pushNotificationService.sendToUser("owner_1", any(), any(), any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
