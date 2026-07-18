package rw.itunda.eats

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.core.domain.EatsOrder
import rw.itunda.core.domain.EatsOrderStatus
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.LedgerEntry
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantProduct
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.Rider
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.EatsOrderItemRepository
import rw.itunda.core.repository.EatsOrderRepository
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.RiderRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.util.Optional

class EatsOrderServiceTest : BehaviorSpec({

    fun wallet(id: String, userId: String) = Wallet(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test wallet",
        type = WalletType.MAIN, balance = BigDecimal("100000"), availableBalance = BigDecimal("100000"),
    )

    Given("a real restaurant with a real menu") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val riderRepository = mockk<RiderRepository>()
        val eatsOrderRepository = mockk<EatsOrderRepository>()
        val eatsOrderItemRepository = mockk<EatsOrderItemRepository>(relaxed = true)
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        // Not relaxed for save() specifically -- same real mockk-generic-inference
        // workaround OrderServiceTest/MerchantServiceTest/WalletServiceTest already
        // document.
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        every { transactionRepository.save(any()) } answers { firstArg() }
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val service = EatsOrderService(
            merchantRepository, merchantProductRepository, riderRepository, eatsOrderRepository,
            eatsOrderItemRepository, walletRepository, ledgerService, transactionRepository, fraudRuleEngine,
            ledgerEntryRepository,
        )

        val restaurant = Merchant(id = "restaurant_1", ownerUserId = "owner_1", walletId = "wallet_restaurant", businessName = "Kigali Grill", status = MerchantStatus.ACTIVE)
        val restaurantWallet = wallet("wallet_restaurant", "owner_1")
        val buyerWallet = wallet("wallet_buyer", "buyer_1")
        val menuItem = MerchantProduct(id = "item_1", merchantId = "restaurant_1", name = "Grilled chicken", price = BigDecimal("3000"))

        When("a real buyer places a real order for 2 units") {
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            every { walletRepository.findById("wallet_restaurant") } returns Optional.of(restaurantWallet)
            every { walletRepository.findByUserIdAndType("buyer_1", WalletType.MAIN) } returns buyerWallet
            every { merchantProductRepository.findById("item_1") } returns Optional.of(menuItem)
            val legsSlot = slot<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_1", emptyList())
            every { eatsOrderRepository.save(any()) } answers { firstArg() }

            val detail = service.placeOrder("buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_1", 2)), "  KG 9 Ave  ")

            Then("it prices from the real live menu (3000 x 2 = 6000), splits a 1.5% platform fee, adds a real flat 1500 delivery fee, and trims the address") {
                detail.order.itemsSubtotal shouldBe BigDecimal("6000")
                detail.order.platformFee shouldBe BigDecimal("90.00")
                detail.order.deliveryFee shouldBe BigDecimal("1500")
                detail.order.totalAmount shouldBe BigDecimal("7500")
                detail.order.deliveryAddress shouldBe "KG 9 Ave"
                detail.items.first().productName shouldBe "Grilled chicken"

                val legs = legsSlot.captured
                legs.first { it.accountId == "wallet_buyer" }.amount shouldBe BigDecimal("7500")
                legs.first { it.accountId == "wallet_restaurant" }.amount shouldBe BigDecimal("5910.00")
                legs.first { it.accountId == "fee_revenue" }.amount shouldBe BigDecimal("90.00")
                val holdingLeg = legs.first { it.accountId == "eats_delivery_holding" }
                holdingLeg.amount shouldBe BigDecimal("1500")
                holdingLeg.accountType shouldBe LedgerAccountType.EATS_DELIVERY_HOLDING
            }
        }

        When("ordering from your own restaurant") {
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)

            Then("it throws SelfEatsOrderException") {
                try {
                    service.placeOrder("owner_1", "restaurant_1", listOf(EatsOrderItemRequest("item_1", 1)), "addr")
                    error("expected SelfEatsOrderException")
                } catch (e: SelfEatsOrderException) {
                    // expected
                }
            }
        }

        When("ordering a menu item that belongs to a DIFFERENT restaurant") {
            val otherItem = MerchantProduct(id = "item_2", merchantId = "restaurant_OTHER", name = "Not this restaurant's item", price = BigDecimal("500"))
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            every { walletRepository.findById("wallet_restaurant") } returns Optional.of(restaurantWallet)
            every { walletRepository.findByUserIdAndType("buyer_1", WalletType.MAIN) } returns buyerWallet
            every { merchantProductRepository.findById("item_2") } returns Optional.of(otherItem)

            Then("it throws MenuItemNotFoundException, not silently mixing restaurants into one order") {
                try {
                    service.placeOrder("buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_2", 1)), "addr")
                    error("expected MenuItemNotFoundException")
                } catch (e: MenuItemNotFoundException) {
                    // expected
                }
            }
        }

        When("ordering with zero quantity") {
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            every { walletRepository.findById("wallet_restaurant") } returns Optional.of(restaurantWallet)
            every { walletRepository.findByUserIdAndType("buyer_1", WalletType.MAIN) } returns buyerWallet

            Then("it throws InvalidEatsQuantityException") {
                try {
                    service.placeOrder("buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_1", 0)), "addr")
                    error("expected InvalidEatsQuantityException")
                } catch (e: InvalidEatsQuantityException) {
                    // expected
                }
            }
        }

        When("placing an order with an empty item list") {
            Then("it throws EmptyEatsOrderException before even looking up the restaurant") {
                try {
                    service.placeOrder("buyer_1", "restaurant_1", emptyList(), "addr")
                    error("expected EmptyEatsOrderException")
                } catch (e: EmptyEatsOrderException) {
                    // expected
                }
            }
        }
    }

    Given("a real restaurant advancing a real placed order") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val riderRepository = mockk<RiderRepository>()
        val eatsOrderRepository = mockk<EatsOrderRepository>()
        val eatsOrderItemRepository = mockk<EatsOrderItemRepository>(relaxed = true)
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val service = EatsOrderService(
            merchantRepository, merchantProductRepository, riderRepository, eatsOrderRepository,
            eatsOrderItemRepository, walletRepository, ledgerService, transactionRepository, fraudRuleEngine,
            ledgerEntryRepository,
        )
        val restaurant = Merchant(id = "restaurant_1", ownerUserId = "owner_1", walletId = "wallet_restaurant", businessName = "Kigali Grill", status = MerchantStatus.ACTIVE)
        val order = EatsOrder(
            id = "eats_order_1", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "addr",
            itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
            totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_1", status = EatsOrderStatus.PLACED,
        )

        When("the real restaurant owner advances status PLACED -> ACCEPTED") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns restaurant
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(order)
            every { eatsOrderRepository.save(any()) } answers { firstArg() }

            val result = service.updateRestaurantStatus("owner_1", "eats_order_1", EatsOrderStatus.ACCEPTED)

            Then("it advances exactly one step") {
                result.status shouldBe EatsOrderStatus.ACCEPTED
            }
        }

        When("the real restaurant owner tries to skip PLACED straight to READY_FOR_PICKUP") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns restaurant
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(order)

            Then("it throws InvalidEatsOrderStatusTransitionException rather than silently skipping steps") {
                try {
                    service.updateRestaurantStatus("owner_1", "eats_order_1", EatsOrderStatus.READY_FOR_PICKUP)
                    error("expected InvalidEatsOrderStatusTransitionException")
                } catch (e: InvalidEatsOrderStatusTransitionException) {
                    // expected
                }
            }
        }

        When("the real restaurant owner tries to advance past DELIVERED into rider-only states") {
            val readyOrder = EatsOrder(
                id = "eats_order_1", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "addr",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_1", status = EatsOrderStatus.READY_FOR_PICKUP,
            )
            every { merchantRepository.findByOwnerUserId("owner_1") } returns restaurant
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(readyOrder)

            Then("it throws InvalidEatsOrderStatusTransitionException -- RIDER_ASSIGNED is not in the restaurant's own status chain") {
                try {
                    service.updateRestaurantStatus("owner_1", "eats_order_1", EatsOrderStatus.RIDER_ASSIGNED)
                    error("expected InvalidEatsOrderStatusTransitionException")
                } catch (e: InvalidEatsOrderStatusTransitionException) {
                    // expected
                }
            }
        }

        When("a stranger who isn't the real restaurant owner tries to update status") {
            every { merchantRepository.findByOwnerUserId("stranger") } returns null

            Then("it throws RestaurantNotFoundException") {
                try {
                    service.updateRestaurantStatus("stranger", "eats_order_1", EatsOrderStatus.ACCEPTED)
                    error("expected RestaurantNotFoundException")
                } catch (e: RestaurantNotFoundException) {
                    // expected
                }
            }
        }

        When("a real buyer views their own order detail") {
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(order)
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            every { eatsOrderItemRepository.findByOrderId("eats_order_1") } returns emptyList()

            val detail = service.getOrderDetail("buyer_1", "eats_order_1")

            Then("it succeeds") {
                detail.order.id shouldBe "eats_order_1"
            }
        }

        When("a stranger (neither buyer, restaurant, nor rider) tries to view the order detail") {
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(order)
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)

            Then("it throws EatsOrderNotFoundException, not a 403 that would confirm the order exists") {
                try {
                    service.getOrderDetail("stranger", "eats_order_1")
                    error("expected EatsOrderNotFoundException")
                } catch (e: EatsOrderNotFoundException) {
                    // expected
                }
            }
        }

        When("the real restaurant cancels a real PLACED order") {
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(order)
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            val originalEntries = listOf(
                LedgerEntry(id = "le_1", transactionId = "ledgertxn_1", accountId = "wallet_buyer", accountType = LedgerAccountType.WALLET, direction = LedgerDirection.DEBIT, amount = BigDecimal("7500"), currency = "RWF", balanceAfter = BigDecimal("92500"), memo = "Eats order - Kigali Grill"),
                LedgerEntry(id = "le_2", transactionId = "ledgertxn_1", accountId = "wallet_restaurant", accountType = LedgerAccountType.WALLET, direction = LedgerDirection.CREDIT, amount = BigDecimal("5910.00"), currency = "RWF", balanceAfter = BigDecimal("5910.00"), memo = "Eats order collection - Kigali Grill"),
                LedgerEntry(id = "le_3", transactionId = "ledgertxn_1", accountId = "fee_revenue", accountType = LedgerAccountType.FEE_REVENUE, direction = LedgerDirection.CREDIT, amount = BigDecimal("90.00"), currency = "RWF", balanceAfter = BigDecimal("90.00"), memo = "Eats platform fee - Kigali Grill"),
                LedgerEntry(id = "le_4", transactionId = "ledgertxn_1", accountId = "eats_delivery_holding", accountType = LedgerAccountType.EATS_DELIVERY_HOLDING, direction = LedgerDirection.CREDIT, amount = BigDecimal("1500"), currency = "RWF", balanceAfter = BigDecimal("1500"), memo = "Eats delivery fee held - Kigali Grill"),
            )
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(order)
            every { ledgerEntryRepository.findByTransactionId("ledgertxn_1") } returns originalEntries
            val legsSlot = slot<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("refund_txn_1", emptyList())
            every { eatsOrderRepository.save(any()) } answers { firstArg() }

            val result = service.cancelOrder("owner_1", "eats_order_1")

            Then("it flips every original leg -- including the delivery-fee holding leg -- and marks the order CANCELLED") {
                result.status shouldBe EatsOrderStatus.CANCELLED
                result.refundTransactionId shouldBe "refund_txn_1"

                val legs = legsSlot.captured
                legs.first { it.accountId == "wallet_buyer" }.direction shouldBe LedgerDirection.CREDIT
                legs.first { it.accountId == "wallet_restaurant" }.direction shouldBe LedgerDirection.DEBIT
                legs.first { it.accountId == "fee_revenue" }.direction shouldBe LedgerDirection.DEBIT
                legs.first { it.accountId == "eats_delivery_holding" }.direction shouldBe LedgerDirection.DEBIT
            }
        }

        When("someone tries to cancel an order that's already ACCEPTED") {
            val acceptedOrder = EatsOrder(
                id = "eats_order_1", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "addr",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_1", status = EatsOrderStatus.ACCEPTED,
            )
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(acceptedOrder)
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)

            Then("it throws InvalidEatsOrderStatusTransitionException rather than cancelling mid-fulfillment") {
                try {
                    service.cancelOrder("buyer_1", "eats_order_1")
                    error("expected InvalidEatsOrderStatusTransitionException")
                } catch (e: InvalidEatsOrderStatusTransitionException) {
                    // expected
                }
            }
        }

        When("a stranger tries to cancel someone else's order") {
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(order)
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)

            Then("it throws EatsOrderNotFoundException, not a 403 that would confirm the order exists") {
                try {
                    service.cancelOrder("stranger", "eats_order_1")
                    error("expected EatsOrderNotFoundException")
                } catch (e: EatsOrderNotFoundException) {
                    // expected
                }
            }
        }
    }

    Given("a real available rider and a real order ready for pickup") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val riderRepository = mockk<RiderRepository>()
        val eatsOrderRepository = mockk<EatsOrderRepository>()
        val eatsOrderItemRepository = mockk<EatsOrderItemRepository>(relaxed = true)
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val service = EatsOrderService(
            merchantRepository, merchantProductRepository, riderRepository, eatsOrderRepository,
            eatsOrderItemRepository, walletRepository, ledgerService, transactionRepository, fraudRuleEngine,
            ledgerEntryRepository,
        )
        val rider = Rider(id = "rider_1", userId = "rider_user_1", walletId = "wallet_rider", available = true)
        val readyOrder = EatsOrder(
            id = "eats_order_1", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "addr",
            itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
            totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_1", status = EatsOrderStatus.READY_FOR_PICKUP,
        )

        When("the real available rider claims it") {
            every { riderRepository.findByUserId("rider_user_1") } returns rider
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(readyOrder)
            every { eatsOrderRepository.save(any()) } answers { firstArg() }

            val result = service.claimDelivery("rider_user_1", "eats_order_1")

            Then("it real-assigns the rider and moves to RIDER_ASSIGNED") {
                result.riderId shouldBe "rider_1"
                result.status shouldBe EatsOrderStatus.RIDER_ASSIGNED
            }
        }

        When("an OFFLINE rider tries to claim it") {
            val offlineRider = Rider(id = "rider_2", userId = "rider_user_2", walletId = "wallet_rider_2", available = false)
            every { riderRepository.findByUserId("rider_user_2") } returns offlineRider

            Then("it throws RiderNotAvailableException") {
                try {
                    service.claimDelivery("rider_user_2", "eats_order_1")
                    error("expected RiderNotAvailableException")
                } catch (e: RiderNotAvailableException) {
                    // expected
                }
            }
        }

        When("a second real rider tries to claim an already-claimed order") {
            val alreadyClaimed = EatsOrder(
                id = "eats_order_1", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "addr",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_1", riderId = "rider_1", status = EatsOrderStatus.RIDER_ASSIGNED,
            )
            every { riderRepository.findByUserId("rider_user_1") } returns rider
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(alreadyClaimed)

            Then("it throws DeliveryAlreadyClaimedException") {
                try {
                    service.claimDelivery("rider_user_1", "eats_order_1")
                    error("expected DeliveryAlreadyClaimedException")
                } catch (e: DeliveryAlreadyClaimedException) {
                    // expected
                }
            }
        }
    }

    Given("a real rider assigned to a real delivery") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val riderRepository = mockk<RiderRepository>()
        val eatsOrderRepository = mockk<EatsOrderRepository>()
        val eatsOrderItemRepository = mockk<EatsOrderItemRepository>(relaxed = true)
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val service = EatsOrderService(
            merchantRepository, merchantProductRepository, riderRepository, eatsOrderRepository,
            eatsOrderItemRepository, walletRepository, ledgerService, transactionRepository, fraudRuleEngine,
            ledgerEntryRepository,
        )
        val rider = Rider(id = "rider_1", userId = "rider_user_1", walletId = "wallet_rider", available = true)
        val riderWallet = wallet("wallet_rider", "rider_user_1")
        val assignedOrder = EatsOrder(
            id = "eats_order_1", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "addr",
            itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
            totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_1", riderId = "rider_1", status = EatsOrderStatus.RIDER_ASSIGNED,
        )

        When("the assigned rider advances RIDER_ASSIGNED -> PICKED_UP") {
            every { riderRepository.findByUserId("rider_user_1") } returns rider
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(assignedOrder)
            every { eatsOrderRepository.save(any()) } answers { firstArg() }

            val result = service.updateRiderStatus("rider_user_1", "eats_order_1", EatsOrderStatus.PICKED_UP)

            Then("it advances without touching the ledger yet") {
                result.status shouldBe EatsOrderStatus.PICKED_UP
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }

        When("the assigned rider marks it DELIVERED") {
            val pickedUpOrder = EatsOrder(
                id = "eats_order_1", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "addr",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_1", riderId = "rider_1", status = EatsOrderStatus.PICKED_UP,
            )
            every { riderRepository.findByUserId("rider_user_1") } returns rider
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(pickedUpOrder)
            every { eatsOrderRepository.save(any()) } answers { firstArg() }
            every { walletRepository.findById("wallet_rider") } returns Optional.of(riderWallet)
            val legsSlot = slot<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("payout_txn_1", emptyList())

            val result = service.updateRiderStatus("rider_user_1", "eats_order_1", EatsOrderStatus.DELIVERED)

            Then("it real-pays the delivery fee out of eats_delivery_holding straight into the rider's own wallet") {
                result.status shouldBe EatsOrderStatus.DELIVERED
                result.deliveryPayoutTransactionId shouldBe "payout_txn_1"

                val legs = legsSlot.captured
                val holdingLeg = legs.first { it.accountId == "eats_delivery_holding" }
                val riderLeg = legs.first { it.accountId == "wallet_rider" }
                holdingLeg.amount shouldBe BigDecimal("1500")
                riderLeg.amount shouldBe BigDecimal("1500")
            }
        }

        When("someone who isn't the assigned rider tries to advance the delivery") {
            every { riderRepository.findByUserId("someone_else") } returns Rider(id = "rider_2", userId = "someone_else", walletId = "wallet_2")
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(assignedOrder)

            Then("it throws NotAssignedRiderException") {
                try {
                    service.updateRiderStatus("someone_else", "eats_order_1", EatsOrderStatus.PICKED_UP)
                    error("expected NotAssignedRiderException")
                } catch (e: NotAssignedRiderException) {
                    // expected
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
