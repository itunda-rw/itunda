package rw.itunda.eats

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.EatsFulfillmentType
import rw.itunda.core.domain.EatsOrder
import rw.itunda.core.domain.EatsOrderItem
import rw.itunda.core.domain.EatsOrderStatus
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.LedgerEntry
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantProduct
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.Rider
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.geo.GeocodeResult
import rw.itunda.core.geo.GeocodeSuggestion
import rw.itunda.core.geo.NominatimGeocodingClient
import rw.itunda.core.geo.OsrmRoutingClient
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.EatsOrderItemRepository
import rw.itunda.core.repository.EatsOrderRepository
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.MenuOptionChoiceRepository
import rw.itunda.core.repository.MenuOptionGroupRepository
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.RiderRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.Optional

class EatsOrderServiceTest : BehaviorSpec({

    fun account(id: String, userId: String) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = AccountType.MAIN, balance = BigDecimal("100000"), availableBalance = BigDecimal("100000"),
    )

    Given("a real restaurant with a real menu") {
        val merchantRepository = mockk<MerchantRepository>()
        // Real Eats order-status webhook (2026-08-30) -- notifyRestaurantWebhook's own
        // merchantRepository.findById lookup needs a safe default across every test in
        // this file, most of which never cared about that call before this feature
        // existed. A more specific findById stub registered later in a given When block
        // still correctly takes precedence for its own exact id (MockK checks
        // most-recently-registered stubs first) -- this is purely a fallback for every
        // other id/test that never stubbed it at all.
        every { merchantRepository.findById(any<String>()) } returns Optional.empty()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val riderRepository = mockk<RiderRepository>()
        val eatsOrderRepository = mockk<EatsOrderRepository>()
        // Real 단건배달 (single-order delivery) guarantee (2026-07-26) -- default "no
        // rider is currently busy" stub for every pre-existing test in this suite, none
        // of which predate or specifically exercise this new behavior. See
        // EatsOrderService.claimDelivery's own doc comment.
        every { eatsOrderRepository.existsByRiderIdAndStatusIn(any(), any()) } returns false
        every { eatsOrderRepository.findDistinctRiderIdsByStatusIn(any()) } returns emptyList()
        val eatsOrderItemRepository = mockk<EatsOrderItemRepository>(relaxed = true)
        // Real menu-options resolution (2026-07-21) -- relaxed + explicit empty-list
        // stubs, matching this suite's own existing "no option groups defined" default
        // for every pre-existing test (see MenuOptionGroup.kt's own doc comment: purely
        // additive, a product with zero groups behaves exactly as before).
        val menuOptionGroupRepository = mockk<MenuOptionGroupRepository>(relaxed = true)
        every { menuOptionGroupRepository.findByProductIdInOrderByDisplayOrderAsc(any()) } returns emptyList()
        val menuOptionChoiceRepository = mockk<MenuOptionChoiceRepository>(relaxed = true)
        every { menuOptionChoiceRepository.findByGroupIdInOrderByDisplayOrderAsc(any()) } returns emptyList()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        // Not relaxed for save() specifically -- same real mockk-generic-inference
        // workaround OrderServiceTest/MerchantServiceTest/AccountServiceTest already
        // document.
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        every { transactionRepository.save(any()) } answers { firstArg() }
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>()
        every { osrmRoutingClient.routeDistanceKm(any(), any(), any(), any()) } returns null
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>()
        every { nominatimGeocodingClient.geocode(any()) } returns null
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        // Real buyer order-status notifications (2026-07-20) -- explicit stub, same
        // known "relaxed mockk can't correctly infer JpaRepository's generic save()
        // signature" gotcha this project's own tests already document repeatedly
        // (MerchantServiceTest/OrderServiceTest/EatsOrderServiceTest/GroupMessagingServiceTest, etc).
        every { notificationRepository.save(any()) } answers { firstArg() }
        // Real Baemin Club-style free-delivery membership (2026-07-26) -- relaxed, no
        // active member by default, matching this suite's own "no pre-existing test
        // predates this feature" convention already used for the single-order delivery
        // guard's own default stubs.
        val eatsMembershipService = mockk<EatsMembershipService>(relaxed = true).also { every { it.hasActiveMembership(any()) } returns false }
        // Real Coupang Wow-style unconditional waiver (2026-07-31) -- same relaxed,
        // no-active-member-by-default convention as EatsMembershipService above. Named
        // (not inline) so the platform-membership test below can override it per-test.
        val platformMembershipService = mockk<PlatformMembershipService>(relaxed = true).also { every { it.hasActiveMembership(any()) } returns false }
        val autoTopUpService = mockk<rw.itunda.account.AutoTopUpService>(relaxed = true)
        every { autoTopUpService.ensureSufficientPayBalance(any(), any(), any()) } answers { secondArg() }
        val webhookDeliveryService = mockk<rw.itunda.merchant.WebhookDeliveryService>(relaxed = true)
        val service = EatsOrderService(
            merchantRepository, merchantProductRepository, riderRepository, eatsOrderRepository,
            eatsOrderItemRepository, menuOptionGroupRepository, menuOptionChoiceRepository, accountRepository, ledgerService, transactionRepository, fraudRuleEngine,
            ledgerEntryRepository, osrmRoutingClient, nominatimGeocodingClient, rateLimiter, notificationRepository,
            eatsMembershipService, platformMembershipService, pushNotificationService, autoTopUpService, webhookDeliveryService,
        )

        val restaurant = Merchant(id = "restaurant_1", ownerUserId = "owner_1", accountId = "account_restaurant", businessName = "Kigali Grill", status = MerchantStatus.ACTIVE)
        val restaurantAccount = account("account_restaurant", "owner_1")
        val buyerAccount = account("account_buyer", "buyer_1")
        val menuItem = MerchantProduct(id = "item_1", merchantId = "restaurant_1", name = "Grilled chicken", price = BigDecimal("3000"))

        When("a real buyer places a real order for 2 units") {
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            every { accountRepository.findById("account_restaurant") } returns Optional.of(restaurantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
            every { merchantProductRepository.findAllById(listOf("item_1")) } returns listOf(menuItem)
            val legsSlot = slot<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_1", emptyList())
            every { eatsOrderRepository.save(any()) } answers { firstArg() }

            val detail = service.placeOrder("buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_1", 2)), "  KG 9 Ave  ")

            Then("it prices from the real live menu (3000 x 2 = 6000), splits a 1.5% platform fee, adds a real flat 1500 delivery fee, applies the real >=5000 promotion tier (1000 off), and trims the address") {
                detail.order.itemsSubtotal shouldBe BigDecimal("6000")
                detail.order.platformFee shouldBe BigDecimal("90.00")
                detail.order.deliveryFee shouldBe BigDecimal("1500")
                detail.order.promotionDiscount shouldBe BigDecimal("1000")
                // 6000 + 1500 delivery - 1000 promotion = 6500, the real amount the buyer is charged
                detail.order.totalAmount shouldBe BigDecimal("6500")
                detail.order.deliveryAddress shouldBe "KG 9 Ave"
                detail.items.first().productName shouldBe "Grilled chicken"

                val legs = legsSlot.captured
                legs.first { it.accountId == "account_buyer" }.amount shouldBe BigDecimal("6500")
                // Restaurant payout and platform fee are both untouched by the promotion --
                // itunda alone absorbs the discount, matching Baemin's own real mechanic.
                legs.first { it.accountId == "account_restaurant" }.amount shouldBe BigDecimal("5910.00")
                legs.first { it.accountId == "fee_revenue" }.amount shouldBe BigDecimal("90.00")
                legs.first { it.accountId == "promotion_expense" }.amount shouldBe BigDecimal("1000")
                val holdingLeg = legs.first { it.accountId == "eats_delivery_holding" }
                holdingLeg.amount shouldBe BigDecimal("1500")
                holdingLeg.accountType shouldBe LedgerAccountType.EATS_DELIVERY_HOLDING
            }

            Then("it real-alerts the real restaurant owner -- the real gap where a new order arrived with zero notification") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "owner_1" && it.type == "NEW_EATS_ORDER" }) }
            }

            Then("the restaurant owner also gets a real push notification, not just the in-app one") {
                verify(exactly = 1) { pushNotificationService.sendToUser("owner_1", "New order received", any(), any()) }
            }

            // Real gap found live (repo-wide fraud-engine-verification sweep,
            // 2026-09-08): fraudRuleEngine was relaxed = true with zero verify{}
            // anywhere in this file, so a future accidental removal of the real
            // fraudRuleEngine.evaluate call would have compiled and passed silently.
            Then("the real fraud engine is actually consulted, not just mocked away") {
                verify(exactly = 1) { fraudRuleEngine.evaluate("buyer_1", "owner_1", BigDecimal("6500"), "ledgertxn_1") }
            }
        }

        // Real Toss Bank/Toss Pay separation (2026-08-21) -- an Eats order is real
        // merchant collection, same as MerchantService.collect()'s own QR path, so it
        // gets the same auto-topup-from-Bank-if-short treatment.
        When("a real buyer's itunda Pay money is short but auto top-up from Bank covers it") {
            val shortAccount = account("account_buyer_short", "buyer_short").also { it.availableBalance = BigDecimal("1000") }
            val toppedUpAccount = account("account_buyer_short", "buyer_short").also { it.availableBalance = BigDecimal("10000") }
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            every { accountRepository.findById("account_restaurant") } returns Optional.of(restaurantAccount)
            every { accountRepository.findByUserIdAndType("buyer_short", AccountType.PAY) } returns shortAccount
            every { autoTopUpService.ensureSufficientPayBalance("buyer_short", shortAccount, BigDecimal("6500")) } returns toppedUpAccount
            every { merchantProductRepository.findAllById(listOf("item_1")) } returns listOf(menuItem)
            val legsSlot = slot<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_topup", emptyList())
            every { eatsOrderRepository.save(any()) } answers { firstArg() }

            service.placeOrder("buyer_short", "restaurant_1", listOf(EatsOrderItemRequest("item_1", 2)), "KG 9 Ave")

            Then("it calls the shared top-up helper for exactly this order's real charge, then completes the order") {
                verify(exactly = 1) { autoTopUpService.ensureSufficientPayBalance("buyer_short", shortAccount, BigDecimal("6500")) }
                legsSlot.captured.first { it.accountId == "account_buyer_short" }.amount shouldBe BigDecimal("6500")
            }
        }

        When("a real order's itemsSubtotal is below the lowest real promotion tier") {
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            every { accountRepository.findById("account_restaurant") } returns Optional.of(restaurantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
            every { merchantProductRepository.findAllById(listOf("item_1")) } returns listOf(menuItem)
            val legsSlot = slot<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_no_promo", emptyList())
            every { eatsOrderRepository.save(any()) } answers { firstArg() }

            // 1 unit at 3000 = itemsSubtotal 3000, under the real 5000 floor -- no
            // unearned discount for a small order.
            val detail = service.placeOrder("buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_1", 1)), "KG 9 Ave")

            Then("no real promotion discount applies, and the zero-amount promotion_expense leg is left for the real LedgerService to filter") {
                detail.order.promotionDiscount shouldBe BigDecimal.ZERO
                detail.order.totalAmount shouldBe BigDecimal("4500")
                // A zero-amount leg is still constructed here -- postLedgerTransaction's own
                // real implementation filters zero-amount legs before posting, this mock
                // doesn't run that filter, so the leg is present but correctly zero.
                legsSlot.captured.first { it.accountId == "promotion_expense" }.amount shouldBe BigDecimal.ZERO
            }
        }

        When("a real Coupang Wow-style platform member orders at a restaurant that has NOT opted into Eats Club") {
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            every { accountRepository.findById("account_restaurant") } returns Optional.of(restaurantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
            every { merchantProductRepository.findAllById(listOf("item_1")) } returns listOf(menuItem)
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_platform_member", emptyList())
            every { eatsOrderRepository.save(any()) } answers { firstArg() }
            every { platformMembershipService.hasActiveMembership("buyer_1") } returns true

            val detail = service.placeOrder("buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_1", 2)), "KG 9 Ave")

            Then("delivery is real-free even though this specific restaurant never opted into Eats Club -- the unconditional, broader guarantee") {
                restaurant.participatesInEatsMembership shouldBe false
                detail.order.deliveryFee shouldBe BigDecimal.ZERO
                // itemsSubtotal (6000) crosses the real >=5000 promotion tier (1000 off),
                // same as the base case above -- totalAmount is the real post-discount charge.
                detail.order.totalAmount shouldBe detail.order.itemsSubtotal.subtract(BigDecimal("1000"))
            }
        }

        When("a restaurant order is still inside its payment transaction") {
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            every { accountRepository.findById("account_restaurant") } returns Optional.of(restaurantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
            every { merchantProductRepository.findAllById(listOf("item_1")) } returns listOf(menuItem)
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_after_commit", emptyList())
            every { eatsOrderRepository.save(any()) } answers { firstArg() }

            TransactionSynchronizationManager.initSynchronization()
            try {
                service.placeOrder("buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_1", 2)), "KG 9 Ave")

                Then("the order notification is durable, but the restaurant push is withheld") {
                    verify(exactly = 1) { notificationRepository.save(match { it.userId == "owner_1" && it.type == "NEW_EATS_ORDER" }) }
                    verify(exactly = 0) { pushNotificationService.sendToUser(any(), any(), any(), any()) }
                }

                Then("the restaurant receives the push only after commit") {
                    TransactionSynchronizationManager.getSynchronizations().single().afterCommit()
                    verify(exactly = 1) {
                        pushNotificationService.sendToUser(
                            "owner_1",
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

        When("a real buyer places a real Baemin-style 포장주문 (Pickup) order, no delivery address given") {
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            every { accountRepository.findById("account_restaurant") } returns Optional.of(restaurantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
            every { merchantProductRepository.findAllById(listOf("item_1")) } returns listOf(menuItem)
            val legsSlot = slot<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_pickup", emptyList())
            every { eatsOrderRepository.save(any()) } answers { firstArg() }

            val detail = service.placeOrder(
                "buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_1", 2)), deliveryAddress = "",
                fulfillmentType = EatsFulfillmentType.PICKUP,
            )

            Then("it real-charges zero delivery fee unconditionally and stores a real, honest display address") {
                detail.order.fulfillmentType shouldBe EatsFulfillmentType.PICKUP
                detail.order.deliveryFee shouldBe BigDecimal.ZERO
                // itemsSubtotal (6000) crosses the real >=5000 promotion tier (1000 off):
                // 6000 - 1000 = 5000, the real amount the buyer is charged.
                detail.order.totalAmount shouldBe BigDecimal("5000")
                detail.order.deliveryAddress shouldBe "Pickup at Kigali Grill"
                val holdingLeg = legsSlot.captured.first { it.accountId == "eats_delivery_holding" }
                holdingLeg.amount shouldBe BigDecimal.ZERO
            }
            Then("it never calls real geocoding at all for a PICKUP order") {
                verify(exactly = 0) { nominatimGeocodingClient.geocode(any()) }
            }
        }

        When("a real buyer places a PICKUP order at a restaurant with a real 10% Baemin-style 포장할인 set") {
            val restaurantWithPickupDiscount = Merchant(
                id = "restaurant_1", ownerUserId = "owner_1", accountId = "account_restaurant", businessName = "Kigali Grill",
                status = MerchantStatus.ACTIVE, pickupDiscountPercent = 10,
            )
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurantWithPickupDiscount)
            every { accountRepository.findById("account_restaurant") } returns Optional.of(restaurantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
            every { merchantProductRepository.findAllById(listOf("item_1")) } returns listOf(menuItem)
            val legsSlot = slot<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_pickup_discount", emptyList())
            every { eatsOrderRepository.save(any()) } answers { firstArg() }

            // itemsSubtotal = 1 x 3000 = 3000, below the lowest real promotion tier
            // (5000) so promotionDiscount stays a clean zero -- isolates the real
            // pickupDiscount math from EatsPromotionCalculator's own tiers.
            val detail = service.placeOrder(
                "buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_1", 1)), deliveryAddress = "",
                fulfillmentType = EatsFulfillmentType.PICKUP,
            )

            Then("the restaurant absorbs the real 10% discount, the buyer pays less, and itunda's own platform fee is untouched") {
                // pickupDiscount = 3000 * 10% = 300. platformFee = 3000 * 1.5% = 45.
                // buyerCharge = 3000 - 0 (promotion) - 300 (pickup) = 2700.
                // netToRestaurant = 3000 - 45 (fee) - 300 (pickup) = 2655.
                detail.order.pickupDiscount shouldBe BigDecimal("300.00")
                detail.order.totalAmount shouldBe BigDecimal("2700.00")
                val restaurantLeg = legsSlot.captured.first { it.accountId == "account_restaurant" }
                restaurantLeg.direction shouldBe LedgerDirection.CREDIT
                restaurantLeg.amount shouldBe BigDecimal("2655.00")
                val feeLeg = legsSlot.captured.first { it.accountId == "fee_revenue" }
                feeLeg.amount shouldBe BigDecimal("45.00")
                val buyerLeg = legsSlot.captured.first { it.accountId == "account_buyer" }
                buyerLeg.amount shouldBe BigDecimal("2700.00")
            }
        }

        // Real fund-safety fix (2026-09-04): pickupDiscountPercent (restaurant-set, up
        // to 100) and promotionDiscount (platform-computed) both subtract from
        // buyerCharge with no check that they don't together consume the whole order.
        // At 100% pickup discount and an itemsSubtotal below the lowest promotion
        // tier (so promotionDiscount stays a clean zero), buyerCharge lands at EXACTLY
        // zero -- and LedgerService.postLedgerTransaction's own `amount >
        // BigDecimal.ZERO` filter would silently drop that zero-valued WALLET debit
        // leg without breaking the debit/credit balance check (removing an
        // already-zero leg can never unbalance a balanced transaction), letting the
        // whole order commit with the restaurant paid in full and the buyer charged
        // nothing. This test's Then block never reaches the ledger call at all --
        // deliberately verified by commenting out this test's own guarding
        // `if (buyerCharge <= BigDecimal.ZERO) throw ...` line in EatsOrderService.kt
        // and confirming the test fails (no exception thrown) before restoring it.
        When("a real buyer's pickup-discount-and-promotion stacking would charge them exactly zero") {
            val restaurantWithFullPickupDiscount = Merchant(
                id = "restaurant_1", ownerUserId = "owner_1", accountId = "account_restaurant", businessName = "Kigali Grill",
                status = MerchantStatus.ACTIVE, pickupDiscountPercent = 100,
            )
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurantWithFullPickupDiscount)
            every { accountRepository.findById("account_restaurant") } returns Optional.of(restaurantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
            every { merchantProductRepository.findAllById(listOf("item_1")) } returns listOf(menuItem)

            Then("it's rejected before ever touching the ledger, not silently committed as a free order") {
                try {
                    // itemsSubtotal = 1 x 3000 = 3000, below the 5000 promotion tier, so
                    // promotionDiscount = 0; pickupDiscount = 3000 x 100% = 3000;
                    // buyerCharge = 3000 - 0 - 3000 = 0.
                    service.placeOrder(
                        "buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_1", 1)), deliveryAddress = "",
                        fulfillmentType = EatsFulfillmentType.PICKUP,
                    )
                    throw AssertionError("expected InvalidEatsOrderChargeException")
                } catch (e: InvalidEatsOrderChargeException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("a real buyer places a DELIVERY order (not PICKUP) at a restaurant with a real 포장할인 set") {
            val restaurantWithPickupDiscount = Merchant(
                id = "restaurant_1", ownerUserId = "owner_1", accountId = "account_restaurant", businessName = "Kigali Grill",
                status = MerchantStatus.ACTIVE, pickupDiscountPercent = 10,
            )
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurantWithPickupDiscount)
            every { accountRepository.findById("account_restaurant") } returns Optional.of(restaurantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
            every { merchantProductRepository.findAllById(listOf("item_1")) } returns listOf(menuItem)
            every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("ledgertxn_delivery_no_pickup_discount", emptyList())
            every { eatsOrderRepository.save(any()) } answers { firstArg() }

            val detail = service.placeOrder("buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_1", 1)), "KG 9 Ave")

            Then("the real pickup discount is never applied -- it's a PICKUP-only real Baemin mechanic") {
                detail.order.pickupDiscount shouldBe BigDecimal.ZERO
            }
        }

        When("a real buyer's order falls below the restaurant's real minimum order amount") {
            val restaurantWithMin = Merchant(
                id = "restaurant_1", ownerUserId = "owner_1", accountId = "account_restaurant", businessName = "Kigali Grill",
                status = MerchantStatus.ACTIVE, minOrderAmount = BigDecimal("10000"),
            )
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurantWithMin)
            every { accountRepository.findById("account_restaurant") } returns Optional.of(restaurantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
            every { merchantProductRepository.findAllById(listOf("item_1")) } returns listOf(menuItem)

            Then("it's honestly rejected -- this real, already-shipped field was never actually enforced anywhere before") {
                try {
                    service.placeOrder("buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_1", 2)), "KG 9 Ave")
                    throw AssertionError("expected MinOrderAmountNotMetException")
                } catch (e: MinOrderAmountNotMetException) {
                    // expected -- 2 x 3000 = 6000, below the real 10000 minimum
                }
            }
        }

        When("a real buyer's order meets the restaurant's real minimum order amount exactly") {
            val restaurantWithMin = Merchant(
                id = "restaurant_1", ownerUserId = "owner_1", accountId = "account_restaurant", businessName = "Kigali Grill",
                status = MerchantStatus.ACTIVE, minOrderAmount = BigDecimal("6000"),
            )
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurantWithMin)
            every { accountRepository.findById("account_restaurant") } returns Optional.of(restaurantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
            every { merchantProductRepository.findAllById(listOf("item_1")) } returns listOf(menuItem)
            every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("ledgertxn_minexact", emptyList())
            every { eatsOrderRepository.save(any()) } answers { firstArg() }

            val detail = service.placeOrder("buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_1", 2)), "KG 9 Ave")

            Then("it succeeds -- meeting the minimum exactly is not the same as falling below it") {
                detail.order.itemsSubtotal shouldBe BigDecimal("6000")
            }
        }

        When("a real buyer places a real 배달의민족 예약주문 (scheduled order) at a restaurant that opted in") {
            val schedulingRestaurant = Merchant(
                id = "restaurant_1", ownerUserId = "owner_1", accountId = "account_restaurant", businessName = "Kigali Grill",
                status = MerchantStatus.ACTIVE, acceptsScheduledOrders = true,
            )
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(schedulingRestaurant)
            every { accountRepository.findById("account_restaurant") } returns Optional.of(restaurantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
            every { merchantProductRepository.findAllById(listOf("item_1")) } returns listOf(menuItem)
            every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("ledgertxn_sched", emptyList())
            every { eatsOrderRepository.save(any()) } answers { firstArg() }
            val scheduledFor = Instant.now().plus(1, java.time.temporal.ChronoUnit.HOURS)

            val detail = service.placeOrder(
                "buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_1", 1)), "addr",
                scheduledFor = scheduledFor,
            )

            Then("it real-stores the buyer's requested time") {
                detail.order.scheduledFor shouldBe scheduledFor
            }
        }

        When("a real buyer tries to schedule an order at a restaurant that hasn't opted in") {
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)

            Then("it throws ScheduledOrdersNotSupportedException before ever moving real money") {
                try {
                    service.placeOrder(
                        "buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_1", 1)), "addr",
                        scheduledFor = Instant.now().plus(1, java.time.temporal.ChronoUnit.HOURS),
                    )
                    error("expected ScheduledOrdersNotSupportedException")
                } catch (e: ScheduledOrdersNotSupportedException) {
                    // expected
                }
            }
        }

        When("a real buyer tries to schedule an order in the past") {
            val schedulingRestaurant = Merchant(
                id = "restaurant_1", ownerUserId = "owner_1", accountId = "account_restaurant", businessName = "Kigali Grill",
                status = MerchantStatus.ACTIVE, acceptsScheduledOrders = true,
            )
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(schedulingRestaurant)

            Then("it throws InvalidScheduledOrderTimeException") {
                try {
                    service.placeOrder(
                        "buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_1", 1)), "addr",
                        scheduledFor = Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS),
                    )
                    error("expected InvalidScheduledOrderTimeException")
                } catch (e: InvalidScheduledOrderTimeException) {
                    // expected
                }
            }
        }

        When("a real buyer tries to schedule an order beyond the real 2-day window") {
            val schedulingRestaurant = Merchant(
                id = "restaurant_1", ownerUserId = "owner_1", accountId = "account_restaurant", businessName = "Kigali Grill",
                status = MerchantStatus.ACTIVE, acceptsScheduledOrders = true,
            )
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(schedulingRestaurant)

            Then("it throws InvalidScheduledOrderTimeException") {
                try {
                    service.placeOrder(
                        "buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_1", 1)), "addr",
                        scheduledFor = Instant.now().plus(5, java.time.temporal.ChronoUnit.DAYS),
                    )
                    error("expected InvalidScheduledOrderTimeException")
                } catch (e: InvalidScheduledOrderTimeException) {
                    // expected
                }
            }
        }

        When("a real buyer places an order with real delivery notes") {
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            every { accountRepository.findById("account_restaurant") } returns Optional.of(restaurantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
            every { merchantProductRepository.findAllById(listOf("item_1")) } returns listOf(menuItem)
            every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("ledgertxn_2", emptyList())
            every { eatsOrderRepository.save(any()) } answers { firstArg() }

            val detail = service.placeOrder(
                "buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_1", 1)), "addr",
                deliveryNotes = "  Leave at the gate, dog is friendly  ",
            )

            Then("it trims and stores the real notes") {
                detail.order.deliveryNotes shouldBe "Leave at the gate, dog is friendly"
            }
        }

        When("delivery notes exceed 500 characters") {
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)

            Then("it throws InvalidEatsDeliveryNotesException before even resolving the restaurant's account") {
                try {
                    service.placeOrder(
                        "buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_1", 1)), "addr",
                        deliveryNotes = "x".repeat(501),
                    )
                    error("expected InvalidEatsDeliveryNotesException")
                } catch (e: InvalidEatsDeliveryNotesException) {
                    // expected
                }
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

        When("ordering from a restaurant that has temporarily paused orders") {
            val pausedRestaurant = Merchant(
                id = "restaurant_1", ownerUserId = "owner_1", accountId = "account_restaurant", businessName = "Kigali Grill",
                status = MerchantStatus.ACTIVE, isAcceptingOrders = false,
            )
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(pausedRestaurant)

            Then("it throws RestaurantNotAcceptingOrdersException before ever resolving the restaurant's account") {
                try {
                    service.placeOrder("buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_1", 1)), "addr")
                    error("expected RestaurantNotAcceptingOrdersException")
                } catch (e: RestaurantNotAcceptingOrdersException) {
                    verify(exactly = 0) { accountRepository.findById("account_restaurant") }
                }
            }
        }

        When("ordering a menu item that belongs to a DIFFERENT restaurant") {
            val otherItem = MerchantProduct(id = "item_2", merchantId = "restaurant_OTHER", name = "Not this restaurant's item", price = BigDecimal("500"))
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            every { accountRepository.findById("account_restaurant") } returns Optional.of(restaurantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
            every { merchantProductRepository.findAllById(listOf("item_2")) } returns listOf(otherItem)

            Then("it throws MenuItemNotFoundException, not silently mixing restaurants into one order") {
                try {
                    service.placeOrder("buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_2", 1)), "addr")
                    error("expected MenuItemNotFoundException")
                } catch (e: MenuItemNotFoundException) {
                    // expected
                }
            }
        }

        When("ordering a menu item the merchant has marked temporarily sold out") {
            val soldOutItem = MerchantProduct(id = "item_3", merchantId = "restaurant_1", name = "Out of stock special", price = BigDecimal("1500"), soldOut = true)
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            every { accountRepository.findById("account_restaurant") } returns Optional.of(restaurantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
            every { merchantProductRepository.findAllById(listOf("item_3")) } returns listOf(soldOutItem)

            Then("it throws MenuItemSoldOutException, distinct from MenuItemNotFoundException -- the item is real and still shown") {
                try {
                    service.placeOrder("buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_3", 1)), "addr")
                    error("expected MenuItemSoldOutException")
                } catch (e: MenuItemSoldOutException) {
                    // expected
                }
            }
        }

        When("ordering a menu item whose closing/surplus deal has already expired") {
            // Real 마감할인 (closing/surplus discount) expiry enforcement -- see
            // MerchantProduct.isSurplusDeal/surplusExpiresAt's own doc comment and
            // EatsOrderService.placeOrder's own new comment above the check this test
            // covers. Same shared MerchantProduct catalog used as a menu item here.
            val expiredDealItem = MerchantProduct(
                id = "item_expired_surplus", merchantId = "restaurant_1", name = "Closing-time samosas",
                price = BigDecimal("500"), isSurplusDeal = true,
                surplusExpiresAt = java.time.Instant.now().minusSeconds(3600),
            )
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            every { accountRepository.findById("account_restaurant") } returns Optional.of(restaurantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
            every { merchantProductRepository.findAllById(listOf("item_expired_surplus")) } returns listOf(expiredDealItem)

            Then("it throws MenuItemSurplusDealExpiredException, distinct from MenuItemNotFoundException") {
                try {
                    service.placeOrder("buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_expired_surplus", 1)), "addr")
                    error("expected MenuItemSurplusDealExpiredException")
                } catch (e: MenuItemSurplusDealExpiredException) {
                    // expected
                }
            }
        }

        When("ordering with zero quantity") {
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            every { accountRepository.findById("account_restaurant") } returns Optional.of(restaurantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount

            Then("it throws InvalidEatsQuantityException") {
                try {
                    service.placeOrder("buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_1", 0)), "addr")
                    error("expected InvalidEatsQuantityException")
                } catch (e: InvalidEatsQuantityException) {
                    // expected
                }
            }
        }

        When("a real menu item has a real required 'Size' option group and the buyer selects a real priced choice") {
            val group = rw.itunda.core.domain.MenuOptionGroup(id = "group_1", productId = "item_1", name = "Size")
            val choices = listOf(
                rw.itunda.core.domain.MenuOptionChoice(id = "choice_small", groupId = "group_1", name = "Small", priceDelta = BigDecimal.ZERO),
                rw.itunda.core.domain.MenuOptionChoice(id = "choice_large", groupId = "group_1", name = "Large", priceDelta = BigDecimal("1000")),
            )
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            every { accountRepository.findById("account_restaurant") } returns Optional.of(restaurantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
            every { merchantProductRepository.findAllById(listOf("item_1")) } returns listOf(menuItem)
            every { menuOptionGroupRepository.findByProductIdInOrderByDisplayOrderAsc(listOf("item_1")) } returns listOf(group)
            every { menuOptionChoiceRepository.findByGroupIdInOrderByDisplayOrderAsc(listOf("group_1")) } returns choices
            every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("ledgertxn_opt1", emptyList())
            every { eatsOrderRepository.save(any()) } answers { firstArg() }

            val detail = service.placeOrder(
                "buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_1", 2, listOf("choice_large"))), "addr",
            )

            Then("it real-prices the line at base+delta (3000+1000=4000 x 2 = 8000), never the bare base price") {
                detail.order.itemsSubtotal shouldBe BigDecimal("8000")
                detail.items.first().unitPrice shouldBe BigDecimal("4000")
                detail.items.first().selectedOptionsJson shouldBe """[{"groupName":"Size","choiceName":"Large","priceDelta":1000}]"""
            }
        }

        When("a real menu item has a real required option group and the buyer selects NOTHING for it") {
            val group = rw.itunda.core.domain.MenuOptionGroup(id = "group_1", productId = "item_1", name = "Size")
            val choices = listOf(rw.itunda.core.domain.MenuOptionChoice(id = "choice_small", groupId = "group_1", name = "Small", priceDelta = BigDecimal.ZERO))
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            every { accountRepository.findById("account_restaurant") } returns Optional.of(restaurantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
            every { merchantProductRepository.findAllById(listOf("item_1")) } returns listOf(menuItem)
            every { menuOptionGroupRepository.findByProductIdInOrderByDisplayOrderAsc(listOf("item_1")) } returns listOf(group)
            every { menuOptionChoiceRepository.findByGroupIdInOrderByDisplayOrderAsc(listOf("group_1")) } returns choices

            Then("it real-422s with MissingRequiredMenuOptionException -- never silently defaults to a choice the buyer didn't pick") {
                try {
                    service.placeOrder("buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_1", 1)), "addr")
                    error("expected MissingRequiredMenuOptionException")
                } catch (e: MissingRequiredMenuOptionException) {
                    // expected
                }
            }
        }

        When("a real menu item has a real required option group and the buyer selects a choice that belongs to a DIFFERENT group entirely") {
            val group = rw.itunda.core.domain.MenuOptionGroup(id = "group_1", productId = "item_1", name = "Size")
            val choices = listOf(rw.itunda.core.domain.MenuOptionChoice(id = "choice_small", groupId = "group_1", name = "Small", priceDelta = BigDecimal.ZERO))
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            every { accountRepository.findById("account_restaurant") } returns Optional.of(restaurantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
            every { merchantProductRepository.findAllById(listOf("item_1")) } returns listOf(menuItem)
            every { menuOptionGroupRepository.findByProductIdInOrderByDisplayOrderAsc(listOf("item_1")) } returns listOf(group)
            every { menuOptionChoiceRepository.findByGroupIdInOrderByDisplayOrderAsc(listOf("group_1")) } returns choices

            Then("it real-400s with InvalidMenuOptionSelectionException for a choice id that doesn't belong to this product at all") {
                try {
                    service.placeOrder("buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_1", 1, listOf("choice_from_elsewhere"))), "addr")
                    error("expected InvalidMenuOptionSelectionException")
                } catch (e: InvalidMenuOptionSelectionException) {
                    // expected
                }
            }
        }

        When("a menu item has NO option groups at all and the buyer sends no selections") {
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            every { accountRepository.findById("account_restaurant") } returns Optional.of(restaurantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
            every { merchantProductRepository.findAllById(listOf("item_1")) } returns listOf(menuItem)
            every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("ledgertxn_opt2", emptyList())
            every { eatsOrderRepository.save(any()) } answers { firstArg() }

            val detail = service.placeOrder("buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_1", 1)), "addr")

            Then("it behaves exactly as before this feature existed -- purely additive, never a regression for pre-existing menu items") {
                detail.items.first().unitPrice shouldBe BigDecimal("3000")
                detail.items.first().selectedOptionsJson shouldBe null
            }
        }

        When("a real buyer places a real order with real delivery coordinates and the restaurant has a real location") {
            val restaurantWithLocation = Merchant(
                id = "restaurant_1", ownerUserId = "owner_1", accountId = "account_restaurant", businessName = "Kigali Grill",
                status = MerchantStatus.ACTIVE, latitude = -1.9441, longitude = 30.0619,
            )
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurantWithLocation)
            every { accountRepository.findById("account_restaurant") } returns Optional.of(restaurantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
            every { merchantProductRepository.findAllById(listOf("item_1")) } returns listOf(menuItem)
            val legsSlot = slot<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_2", emptyList())
            every { eatsOrderRepository.save(any()) } answers { firstArg() }

            // Same longitude, 0.05 degrees south -- a real Haversine distance along a
            // meridian is exact: 6371km * (0.05deg in radians) = ~5.560km.
            val detail = service.placeOrder(
                "buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_1", 2)), "KG 9 Ave",
                deliveryLatitude = -1.9941, deliveryLongitude = 30.0619,
            )

            Then("it computes a real distance-based fee (base 500 + 250/km) instead of the flat amount") {
                detail.order.distanceKm shouldBe BigDecimal("5.560")
                detail.order.deliveryFee shouldBe BigDecimal("1890.00")
                // itemsSubtotal (6000) crosses the real >=5000 promotion tier (1000 off):
                // 6000 + 1890 delivery - 1000 promotion = 6890.00.
                detail.order.totalAmount shouldBe BigDecimal("6890.00")

                val holdingLeg = legsSlot.captured.first { it.accountId == "eats_delivery_holding" }
                holdingLeg.amount shouldBe BigDecimal("1890.00")
            }
        }

        When("itunda's own self-hosted OSRM has a real route between the restaurant and the buyer") {
            val restaurantWithLocation = Merchant(
                id = "restaurant_1", ownerUserId = "owner_1", accountId = "account_restaurant", businessName = "Kigali Grill",
                status = MerchantStatus.ACTIVE, latitude = -1.9441, longitude = 30.0619,
            )
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurantWithLocation)
            every { accountRepository.findById("account_restaurant") } returns Optional.of(restaurantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
            every { merchantProductRepository.findAllById(listOf("item_1")) } returns listOf(menuItem)
            every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("ledgertxn_3", emptyList())
            every { eatsOrderRepository.save(any()) } answers { firstArg() }
            // A real road distance is longer than the straight line between the same two
            // points (the road detours), proving the service used OSRM's real number, not
            // silently falling back to Haversine's straight-line approximation.
            every { osrmRoutingClient.routeDistanceKm(-1.9441, 30.0619, -1.9941, 30.0619) } returns 7.234

            val detail = service.placeOrder(
                "buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_1", 2)), "KG 9 Ave",
                deliveryLatitude = -1.9941, deliveryLongitude = 30.0619,
            )

            Then("it uses the real OSRM road distance, not the Haversine straight-line one") {
                detail.order.distanceKm shouldBe BigDecimal("7.234")
                // base 500 + 250 * 7.234 = 2308.50
                detail.order.deliveryFee shouldBe BigDecimal("2308.50")
            }
        }

        When("no delivery coordinates are given but itunda's own self-hosted Nominatim can geocode the real address") {
            val restaurantWithLocation = Merchant(
                id = "restaurant_1", ownerUserId = "owner_1", accountId = "account_restaurant", businessName = "Kigali Grill",
                status = MerchantStatus.ACTIVE, latitude = -1.9441, longitude = 30.0619,
            )
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurantWithLocation)
            every { accountRepository.findById("account_restaurant") } returns Optional.of(restaurantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
            every { merchantProductRepository.findAllById(listOf("item_1")) } returns listOf(menuItem)
            every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("ledgertxn_5", emptyList())
            every { eatsOrderRepository.save(any()) } answers { firstArg() }
            every { nominatimGeocodingClient.geocode("KG 9 Ave") } returns GeocodeResult(-1.9941, 30.0619)

            val detail = service.placeOrder("buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_1", 1)), "KG 9 Ave")

            Then("it resolves the address to real coordinates and computes a real distance-based fee") {
                detail.order.deliveryLatitude shouldBe -1.9941
                detail.order.deliveryLongitude shouldBe 30.0619
                detail.order.distanceKm shouldBe BigDecimal("5.560")
                detail.order.deliveryFee shouldBe BigDecimal("1890.00")
            }
        }

        When("no delivery coordinates are given and Nominatim finds no match") {
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            every { accountRepository.findById("account_restaurant") } returns Optional.of(restaurantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
            every { merchantProductRepository.findAllById(listOf("item_1")) } returns listOf(menuItem)
            every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("ledgertxn_6", emptyList())
            every { eatsOrderRepository.save(any()) } answers { firstArg() }
            every { nominatimGeocodingClient.geocode("some unrecognizable scribble") } returns null

            val detail = service.placeOrder("buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_1", 1)), "some unrecognizable scribble")

            Then("it falls back to the flat delivery fee, exactly as if geocoding didn't exist") {
                detail.order.deliveryLatitude shouldBe null
                detail.order.distanceKm shouldBe null
                detail.order.deliveryFee shouldBe BigDecimal("1500")
            }
        }

        When("the delivery point is real but far outside Rwanda") {
            val restaurantWithLocation = Merchant(
                id = "restaurant_1", ownerUserId = "owner_1", accountId = "account_restaurant", businessName = "Kigali Grill",
                status = MerchantStatus.ACTIVE, latitude = -1.9441, longitude = 30.0619,
            )
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurantWithLocation)
            every { accountRepository.findById("account_restaurant") } returns Optional.of(restaurantAccount)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
            every { merchantProductRepository.findAllById(listOf("item_1")) } returns listOf(menuItem)
            every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("ledgertxn_4", emptyList())
            every { eatsOrderRepository.save(any()) } answers { firstArg() }

            // (0,0) -- real coordinates, but nowhere near Rwanda's own road network.
            val detail = service.placeOrder(
                "buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_1", 1)), "Somewhere unrouteable",
                deliveryLatitude = 0.0, deliveryLongitude = 0.0,
            )

            Then("it never calls OSRM -- itunda's Rwanda-only router has no configured max-matching-radius and would silently snap to the nearest network node instead of correctly finding no route -- and uses Haversine directly") {
                io.mockk.verify(exactly = 0) { osrmRoutingClient.routeDistanceKm(any(), any(), any(), any()) }
                detail.order.deliveryFee shouldBe BigDecimal("5000") // bounded at the real max, not a runaway number
            }
        }

        When("submitting only one of deliveryLatitude/deliveryLongitude") {
            Then("it throws InvalidEatsCoordinatesException before even looking up the restaurant") {
                try {
                    service.placeOrder("buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_1", 1)), "addr", deliveryLatitude = -1.9441)
                    error("expected InvalidEatsCoordinatesException")
                } catch (e: InvalidEatsCoordinatesException) {
                    // expected
                }
            }
        }

        When("submitting an out-of-range delivery coordinate") {
            Then("it throws InvalidEatsCoordinatesException") {
                try {
                    service.placeOrder(
                        "buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_1", 1)), "addr",
                        deliveryLatitude = 999.0, deliveryLongitude = 30.0,
                    )
                    error("expected InvalidEatsCoordinatesException")
                } catch (e: InvalidEatsCoordinatesException) {
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

        When("placing an order with a delivery address longer than the real 500-char DB column bound") {
            Then("it throws InvalidEatsDeliveryAddressException rather than risking a raw DB insert failure") {
                try {
                    service.placeOrder("buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_1", 1)), "x".repeat(501))
                    error("expected InvalidEatsDeliveryAddressException")
                } catch (e: InvalidEatsDeliveryAddressException) {
                    // expected
                }
            }
        }
    }

    Given("a real restaurant advancing a real placed order") {
        val merchantRepository = mockk<MerchantRepository>()
        // Real Eats order-status webhook (2026-08-30) -- notifyRestaurantWebhook's own
        // merchantRepository.findById lookup needs a safe default across every test in
        // this file, most of which never cared about that call before this feature
        // existed. A more specific findById stub registered later in a given When block
        // still correctly takes precedence for its own exact id (MockK checks
        // most-recently-registered stubs first) -- this is purely a fallback for every
        // other id/test that never stubbed it at all.
        every { merchantRepository.findById(any<String>()) } returns Optional.empty()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val riderRepository = mockk<RiderRepository>()
        val eatsOrderRepository = mockk<EatsOrderRepository>()
        // Real 단건배달 (single-order delivery) guarantee (2026-07-26) -- default "no
        // rider is currently busy" stub for every pre-existing test in this suite, none
        // of which predate or specifically exercise this new behavior. See
        // EatsOrderService.claimDelivery's own doc comment.
        every { eatsOrderRepository.existsByRiderIdAndStatusIn(any(), any()) } returns false
        every { eatsOrderRepository.findDistinctRiderIdsByStatusIn(any()) } returns emptyList()
        val eatsOrderItemRepository = mockk<EatsOrderItemRepository>(relaxed = true)
        // Real menu-options resolution (2026-07-21) -- relaxed + explicit empty-list
        // stubs, matching this suite's own existing "no option groups defined" default
        // for every pre-existing test (see MenuOptionGroup.kt's own doc comment: purely
        // additive, a product with zero groups behaves exactly as before).
        val menuOptionGroupRepository = mockk<MenuOptionGroupRepository>(relaxed = true)
        every { menuOptionGroupRepository.findByProductIdInOrderByDisplayOrderAsc(any()) } returns emptyList()
        val menuOptionChoiceRepository = mockk<MenuOptionChoiceRepository>(relaxed = true)
        every { menuOptionChoiceRepository.findByGroupIdInOrderByDisplayOrderAsc(any()) } returns emptyList()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>()
        every { osrmRoutingClient.routeDistanceKm(any(), any(), any(), any()) } returns null
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>()
        every { nominatimGeocodingClient.geocode(any()) } returns null
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        // Real buyer order-status notifications (2026-07-20) -- explicit stub, same
        // known "relaxed mockk can't correctly infer JpaRepository's generic save()
        // signature" gotcha this project's own tests already document repeatedly
        // (MerchantServiceTest/OrderServiceTest/EatsOrderServiceTest/GroupMessagingServiceTest, etc).
        every { notificationRepository.save(any()) } answers { firstArg() }
        val autoTopUpService = mockk<rw.itunda.account.AutoTopUpService>(relaxed = true)
        every { autoTopUpService.ensureSufficientPayBalance(any(), any(), any()) } answers { secondArg() }
        val webhookDeliveryService = mockk<rw.itunda.merchant.WebhookDeliveryService>(relaxed = true)
        val service = EatsOrderService(
            merchantRepository, merchantProductRepository, riderRepository, eatsOrderRepository,
            eatsOrderItemRepository, menuOptionGroupRepository, menuOptionChoiceRepository, accountRepository, ledgerService, transactionRepository, fraudRuleEngine,
            ledgerEntryRepository, osrmRoutingClient, nominatimGeocodingClient, rateLimiter, notificationRepository,
            // Real Baemin Club-style free-delivery membership (2026-07-26) -- relaxed,
            // no active member by default, matching this suite's own "no pre-existing
            // test predates this feature" convention already used for the single-order
            // delivery guard's own default stubs.
            mockk<EatsMembershipService>(relaxed = true).also { every { it.hasActiveMembership(any()) } returns false },
            // Real Coupang Wow-style unconditional waiver (2026-07-31) -- same
            // relaxed, no-active-member-by-default convention as EatsMembershipService above.
            mockk<PlatformMembershipService>(relaxed = true).also { every { it.hasActiveMembership(any()) } returns false },
            pushNotificationService,
            autoTopUpService, webhookDeliveryService,
        )
        val restaurant = Merchant(id = "restaurant_1", ownerUserId = "owner_1", accountId = "account_restaurant", businessName = "Kigali Grill", status = MerchantStatus.ACTIVE)
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

            Then("it real-notifies the buyer, not the restaurant owner") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "buyer_1" && it.type == "EATS_ORDER_UPDATE" }) }
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

        When("the real restaurant marks an order READY_FOR_PICKUP with real nearby riders online") {
            val locatedRestaurant = Merchant(id = "restaurant_1", ownerUserId = "owner_1", accountId = "account_restaurant", businessName = "Kigali Grill", status = MerchantStatus.ACTIVE, latitude = -1.9536, longitude = 30.0605)
            val preparingOrder = EatsOrder(
                id = "eats_order_2", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "addr",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_2", status = EatsOrderStatus.PREPARING,
            )
            val closeRider = Rider(id = "rider_close", userId = "rider_user_close", accountId = "account_close", available = true, currentLatitude = -1.9536, currentLongitude = 30.0620)
            val farRider = Rider(id = "rider_far", userId = "rider_user_far", accountId = "account_far", available = true, currentLatitude = -2.5967, currentLongitude = 29.7392)
            val savedSlot = slot<EatsOrder>()
            every { merchantRepository.findByOwnerUserId("owner_1") } returns locatedRestaurant
            every { eatsOrderRepository.findById("eats_order_2") } returns Optional.of(preparingOrder)
            every { eatsOrderRepository.save(capture(savedSlot)) } answers { firstArg() }
            every { riderRepository.findByAvailableTrueAndCurrentLatitudeIsNotNullAndCurrentLongitudeIsNotNull() } returns listOf(farRider, closeRider)

            service.updateRestaurantStatus("owner_1", "eats_order_2", EatsOrderStatus.READY_FOR_PICKUP)

            Then("real automatic dispatch offers it exclusively to the real closest rider only, not every nearby rider") {
                savedSlot.captured.offeredRiderId shouldBe "rider_close"
                (savedSlot.captured.offerExpiresAt != null) shouldBe true
                verify {
                    notificationRepository.save(match<Notification> { it.type == "DELIVERY_OFFER" && it.userId == "rider_user_close" })
                }
                verify(exactly = 0) { notificationRepository.saveAll(any<List<Notification>>()) }
            }
        }

        When("the closest rider is real ONLINE but already carrying another real active delivery") {
            val locatedRestaurant = Merchant(id = "restaurant_1", ownerUserId = "owner_1", accountId = "account_restaurant", businessName = "Kigali Grill", status = MerchantStatus.ACTIVE, latitude = -1.9536, longitude = 30.0605)
            val preparingOrder = EatsOrder(
                id = "eats_order_11", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "addr",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_11", status = EatsOrderStatus.PREPARING,
            )
            val busyCloseRider = Rider(id = "rider_busy_close", userId = "rider_user_busy_close", accountId = "account_busy_close", available = true, currentLatitude = -1.9536, currentLongitude = 30.0620)
            val freeFarRider = Rider(id = "rider_free_far", userId = "rider_user_free_far", accountId = "account_free_far", available = true, currentLatitude = -1.9600, currentLongitude = 30.0700)
            val savedSlot = slot<EatsOrder>()
            every { merchantRepository.findByOwnerUserId("owner_1") } returns locatedRestaurant
            every { eatsOrderRepository.findById("eats_order_11") } returns Optional.of(preparingOrder)
            every { eatsOrderRepository.save(capture(savedSlot)) } answers { firstArg() }
            every { riderRepository.findByAvailableTrueAndCurrentLatitudeIsNotNullAndCurrentLongitudeIsNotNull() } returns listOf(busyCloseRider, freeFarRider)
            // Real 단건배달 (single-order delivery) exclusion (2026-07-26) -- the
            // closest rider is real busy, so real dispatch must skip them for the next
            // real candidate, never offering a delivery claimDelivery would reject anyway.
            every { eatsOrderRepository.findDistinctRiderIdsByStatusIn(listOf(EatsOrderStatus.RIDER_ASSIGNED, EatsOrderStatus.PICKED_UP)) } returns listOf("rider_busy_close")

            service.updateRestaurantStatus("owner_1", "eats_order_11", EatsOrderStatus.READY_FOR_PICKUP)

            Then("real dispatch skips the real busy closest rider and offers the next real free candidate instead") {
                savedSlot.captured.offeredRiderId shouldBe "rider_free_far"
            }
        }

        When("the real restaurant marks an order READY_FOR_PICKUP but has no real online riders at all") {
            val locatedRestaurant = Merchant(id = "restaurant_1", ownerUserId = "owner_1", accountId = "account_restaurant", businessName = "Kigali Grill", status = MerchantStatus.ACTIVE, latitude = -1.9536, longitude = 30.0605)
            val preparingOrder = EatsOrder(
                id = "eats_order_5", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "addr",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_5", status = EatsOrderStatus.PREPARING,
            )
            val savedSlot = slot<EatsOrder>()
            every { merchantRepository.findByOwnerUserId("owner_1") } returns locatedRestaurant
            every { eatsOrderRepository.findById("eats_order_5") } returns Optional.of(preparingOrder)
            every { eatsOrderRepository.save(capture(savedSlot)) } answers { firstArg() }
            every { riderRepository.findByAvailableTrueAndCurrentLatitudeIsNotNullAndCurrentLongitudeIsNotNull() } returns emptyList()

            service.updateRestaurantStatus("owner_1", "eats_order_5", EatsOrderStatus.READY_FOR_PICKUP)

            Then("automatic dispatch real-degrades to the open-browse fallback, not a stuck order") {
                savedSlot.captured.offeredRiderId shouldBe null
                verify(exactly = 0) { notificationRepository.save(any<Notification>()) }
                verify(exactly = 0) { notificationRepository.saveAll(any<List<Notification>>()) }
            }
        }

        When("the real restaurant marks an order READY_FOR_PICKUP but has no real coordinates set") {
            val order2 = EatsOrder(
                id = "eats_order_3", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "addr",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_3", status = EatsOrderStatus.PREPARING,
            )
            every { merchantRepository.findByOwnerUserId("owner_1") } returns restaurant
            every { eatsOrderRepository.findById("eats_order_3") } returns Optional.of(order2)
            every { eatsOrderRepository.save(any()) } answers { firstArg() }

            val result = service.updateRestaurantStatus("owner_1", "eats_order_3", EatsOrderStatus.READY_FOR_PICKUP)

            Then("the real status transition still succeeds -- notification is best-effort, never blocking") {
                result.status shouldBe EatsOrderStatus.READY_FOR_PICKUP
                verify(exactly = 0) { notificationRepository.save(any<Notification>()) }
                verify(exactly = 0) { notificationRepository.saveAll(any<List<Notification>>()) }
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
                LedgerEntry(id = "le_1", transactionId = "ledgertxn_1", accountId = "account_buyer", accountType = LedgerAccountType.WALLET, direction = LedgerDirection.DEBIT, amount = BigDecimal("7500"), currency = "RWF", balanceAfter = BigDecimal("92500"), memo = "Eats order - Kigali Grill"),
                LedgerEntry(id = "le_2", transactionId = "ledgertxn_1", accountId = "account_restaurant", accountType = LedgerAccountType.WALLET, direction = LedgerDirection.CREDIT, amount = BigDecimal("5910.00"), currency = "RWF", balanceAfter = BigDecimal("5910.00"), memo = "Eats order collection - Kigali Grill"),
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
                legs.first { it.accountId == "account_buyer" }.direction shouldBe LedgerDirection.CREDIT
                legs.first { it.accountId == "account_restaurant" }.direction shouldBe LedgerDirection.DEBIT
                legs.first { it.accountId == "fee_revenue" }.direction shouldBe LedgerDirection.DEBIT
                legs.first { it.accountId == "eats_delivery_holding" }.direction shouldBe LedgerDirection.DEBIT
            }

            Then("it real-notifies the buyer, since the RESTAURANT was the one who cancelled") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "buyer_1" && it.type == "EATS_ORDER_UPDATE" }) }
            }
        }

        When("the real buyer cancels their own real PLACED order") {
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            val originalEntries = listOf(
                LedgerEntry(id = "le_1", transactionId = "ledgertxn_1", accountId = "account_buyer", accountType = LedgerAccountType.WALLET, direction = LedgerDirection.DEBIT, amount = BigDecimal("7500"), currency = "RWF", balanceAfter = BigDecimal("92500"), memo = "Eats order - Kigali Grill"),
                LedgerEntry(id = "le_2", transactionId = "ledgertxn_1", accountId = "account_restaurant", accountType = LedgerAccountType.WALLET, direction = LedgerDirection.CREDIT, amount = BigDecimal("5910.00"), currency = "RWF", balanceAfter = BigDecimal("5910.00"), memo = "Eats order collection - Kigali Grill"),
            )
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(order)
            every { ledgerEntryRepository.findByTransactionId("ledgertxn_1") } returns originalEntries
            every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("refund_txn_2", emptyList())
            every { eatsOrderRepository.save(any()) } answers { firstArg() }

            service.cancelOrder("buyer_1", "eats_order_1")

            Then("it does NOT notify the buyer about their own action") {
                verify(exactly = 0) { notificationRepository.save(any()) }
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

        When("a real order times out unaccepted, below the real consecutive-miss threshold") {
            val originalEntries = listOf(
                LedgerEntry(id = "le_1", transactionId = "ledgertxn_1", accountId = "account_buyer", accountType = LedgerAccountType.WALLET, direction = LedgerDirection.DEBIT, amount = BigDecimal("7500"), currency = "RWF", balanceAfter = BigDecimal("92500"), memo = "Eats order - Kigali Grill"),
                LedgerEntry(id = "le_2", transactionId = "ledgertxn_1", accountId = "account_restaurant", accountType = LedgerAccountType.WALLET, direction = LedgerDirection.CREDIT, amount = BigDecimal("5910.00"), currency = "RWF", balanceAfter = BigDecimal("5910.00"), memo = "Eats order collection - Kigali Grill"),
            )
            val restaurantOneMiss = Merchant(
                id = "restaurant_1", ownerUserId = "owner_1", accountId = "account_restaurant", businessName = "Kigali Grill",
                status = MerchantStatus.ACTIVE, consecutiveMissedOrders = 1,
            )
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(order)
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurantOneMiss)
            every { ledgerEntryRepository.findByTransactionId("ledgertxn_1") } returns originalEntries
            every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("refund_txn_3", emptyList())
            every { eatsOrderRepository.save(any()) } answers { firstArg() }
            val merchantSaveSlot = slot<Merchant>()
            every { merchantRepository.save(capture(merchantSaveSlot)) } answers { firstArg() }

            service.expireUnacceptedOrder(order)

            Then("it real-cancels and refunds the order, and increments the streak without pausing the restaurant") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "buyer_1" && it.title == "Order cancelled" }) }
                merchantSaveSlot.captured.consecutiveMissedOrders shouldBe 2
                merchantSaveSlot.captured.isAcceptingOrders shouldBe true
            }
        }

        When("a real order times out unaccepted, reaching the real consecutive-miss threshold") {
            val originalEntries = listOf(
                LedgerEntry(id = "le_1", transactionId = "ledgertxn_1", accountId = "account_buyer", accountType = LedgerAccountType.WALLET, direction = LedgerDirection.DEBIT, amount = BigDecimal("7500"), currency = "RWF", balanceAfter = BigDecimal("92500"), memo = "Eats order - Kigali Grill"),
                LedgerEntry(id = "le_2", transactionId = "ledgertxn_1", accountId = "account_restaurant", accountType = LedgerAccountType.WALLET, direction = LedgerDirection.CREDIT, amount = BigDecimal("5910.00"), currency = "RWF", balanceAfter = BigDecimal("5910.00"), memo = "Eats order collection - Kigali Grill"),
            )
            val restaurantAtThreshold = Merchant(
                id = "restaurant_1", ownerUserId = "owner_1", accountId = "account_restaurant", businessName = "Kigali Grill",
                status = MerchantStatus.ACTIVE, consecutiveMissedOrders = 2,
            )
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(order)
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurantAtThreshold)
            every { ledgerEntryRepository.findByTransactionId("ledgertxn_1") } returns originalEntries
            every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("refund_txn_4", emptyList())
            every { eatsOrderRepository.save(any()) } answers { firstArg() }
            val merchantSaveSlot = slot<Merchant>()
            every { merchantRepository.save(capture(merchantSaveSlot)) } answers { firstArg() }

            service.expireUnacceptedOrder(order)

            Then("it real-auto-pauses the restaurant (same isAcceptingOrders field the manual toggle uses) and resets the streak") {
                merchantSaveSlot.captured.isAcceptingOrders shouldBe false
                merchantSaveSlot.captured.consecutiveMissedOrders shouldBe 0
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "owner_1" && it.type == "RESTAURANT_AUTO_PAUSED" }) }
                verify(exactly = 1) { pushNotificationService.sendToUser("owner_1", any(), any(), any()) }
            }
        }

        When("the real restaurant owner accepts a real order after a nonzero real miss streak") {
            val restaurantWithMisses = Merchant(
                id = "restaurant_1", ownerUserId = "owner_1", accountId = "account_restaurant", businessName = "Kigali Grill",
                status = MerchantStatus.ACTIVE, consecutiveMissedOrders = 2,
            )
            every { merchantRepository.findByOwnerUserId("owner_1") } returns restaurantWithMisses
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(order)
            every { eatsOrderRepository.save(any()) } answers { firstArg() }
            val merchantSaveSlot = slot<Merchant>()
            every { merchantRepository.save(capture(merchantSaveSlot)) } answers { firstArg() }

            service.updateRestaurantStatus("owner_1", "eats_order_1", EatsOrderStatus.ACCEPTED)

            Then("it real-resets the miss streak back to zero -- a prompt real accept clears past misses") {
                merchantSaveSlot.captured.consecutiveMissedOrders shouldBe 0
            }
        }

        When("the real restaurant marks a real Baemin-style PICKUP order READY_FOR_PICKUP") {
            val pickupOrder = EatsOrder(
                id = "eats_order_pickup", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "Pickup at Kigali Grill",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal.ZERO, platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("6000"), transactionId = "ledgertxn_pickup", status = EatsOrderStatus.PREPARING,
                fulfillmentType = EatsFulfillmentType.PICKUP,
            )
            every { merchantRepository.findByOwnerUserId("owner_1") } returns restaurant
            every { eatsOrderRepository.findById("eats_order_pickup") } returns Optional.of(pickupOrder)
            every { eatsOrderRepository.save(any()) } answers { firstArg() }

            val result = service.updateRestaurantStatus("owner_1", "eats_order_pickup", EatsOrderStatus.READY_FOR_PICKUP)

            Then("it real-notifies the buyer to come collect it, rather than ever dispatching a real rider") {
                result.status shouldBe EatsOrderStatus.READY_FOR_PICKUP
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "buyer_1" && it.title == "Order ready" }) }
                // riderRepository is a strict (non-relaxed) mock with zero stubs in this
                // fixture -- if dispatchToNextCandidate had incorrectly run, any real
                // call into it would throw, so a clean pass here IS the real proof no
                // rider dispatch happened.
            }
        }

        When("the real restaurant completes a real Baemin-style PICKUP order") {
            val readyPickupOrder = EatsOrder(
                id = "eats_order_pickup2", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "Pickup at Kigali Grill",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal.ZERO, platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("6000"), transactionId = "ledgertxn_pickup2", status = EatsOrderStatus.READY_FOR_PICKUP,
                fulfillmentType = EatsFulfillmentType.PICKUP,
            )
            every { merchantRepository.findByOwnerUserId("owner_1") } returns restaurant
            every { eatsOrderRepository.findById("eats_order_pickup2") } returns Optional.of(readyPickupOrder)
            every { eatsOrderRepository.save(any()) } answers { firstArg() }

            val result = service.completePickup("owner_1", "eats_order_pickup2")

            Then("it real-transitions directly to DELIVERED, no rider hop, and notifies the buyer") {
                result.status shouldBe EatsOrderStatus.DELIVERED
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "buyer_1" && it.title == "Order completed" }) }
            }
        }

        When("the real restaurant tries to complete-pickup a real DELIVERY-type order") {
            val deliveryOrder = EatsOrder(
                id = "eats_order_delivery", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "addr",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_delivery", status = EatsOrderStatus.READY_FOR_PICKUP,
            )
            every { merchantRepository.findByOwnerUserId("owner_1") } returns restaurant
            every { eatsOrderRepository.findById("eats_order_delivery") } returns Optional.of(deliveryOrder)

            Then("it throws InvalidEatsOrderStatusTransitionException -- only a real PICKUP order can be completed this way") {
                try {
                    service.completePickup("owner_1", "eats_order_delivery")
                    error("expected InvalidEatsOrderStatusTransitionException")
                } catch (e: InvalidEatsOrderStatusTransitionException) {
                    // expected
                }
            }
        }

        When("the real restaurant tries to complete-pickup a PICKUP order that isn't READY_FOR_PICKUP yet") {
            val preparingPickupOrder = EatsOrder(
                id = "eats_order_pickup3", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "Pickup at Kigali Grill",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal.ZERO, platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("6000"), transactionId = "ledgertxn_pickup3", status = EatsOrderStatus.PREPARING,
                fulfillmentType = EatsFulfillmentType.PICKUP,
            )
            every { merchantRepository.findByOwnerUserId("owner_1") } returns restaurant
            every { eatsOrderRepository.findById("eats_order_pickup3") } returns Optional.of(preparingPickupOrder)

            Then("it throws InvalidEatsOrderStatusTransitionException") {
                try {
                    service.completePickup("owner_1", "eats_order_pickup3")
                    error("expected InvalidEatsOrderStatusTransitionException")
                } catch (e: InvalidEatsOrderStatusTransitionException) {
                    // expected
                }
            }
        }

        // Real abandoned-delivery force-cancel (2026-08-18) -- see
        // EatsOrderService.forceCancelAbandonedDelivery's own doc comment for the full
        // account of the gap this closes: a rider who goes dark after being assigned
        // previously left the order (and the buyer's real money) stuck forever.
        When("a real delivery has been RIDER_ASSIGNED well past the real abandonment timeout") {
            val staleOrder = EatsOrder(
                id = "eats_order_stale", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "addr",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_stale", status = EatsOrderStatus.RIDER_ASSIGNED,
                riderId = "rider_1",
            )
            staleOrder.updatedAt = Instant.now().minus(EatsOrderService.DELIVERY_ABANDONMENT_TIMEOUT).minus(Duration.ofMinutes(1))
            val abandoningRider = Rider(id = "rider_1", userId = "rider_user_1", accountId = "account_rider_1")
            every { eatsOrderRepository.findById("eats_order_stale") } returns Optional.of(staleOrder)
            every { riderRepository.findById("rider_1") } returns Optional.of(abandoningRider)
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns account("account_buyer", "buyer_1")
            every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("refund_txn_stale", emptyList())
            every { eatsOrderRepository.save(any()) } answers { firstArg() }

            val result = service.forceCancelAbandonedDelivery("eats_order_stale")

            Then("it real-cancels the order, refunds only the delivery fee, and frees the abandoning rider") {
                result?.status shouldBe EatsOrderStatus.CANCELLED
                result?.refundTransactionId shouldBe "refund_txn_stale"
                verify(exactly = 1) {
                    ledgerService.postLedgerTransaction(
                        "RWF",
                        match { legs ->
                            legs.size == 2 && legs.sumOf { if (it.direction == LedgerDirection.CREDIT) it.amount else -it.amount } == BigDecimal.ZERO &&
                                legs.all { it.amount.compareTo(BigDecimal("1500")) == 0 }
                        },
                    )
                }
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "buyer_1" && it.title == "Order cancelled" }) }
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "rider_user_1" && it.type == "EATS_DELIVERY_ABANDONED" }) }
            }
        }

        When("a real delivery is RIDER_ASSIGNED but still well within the real abandonment window") {
            val freshOrder = EatsOrder(
                id = "eats_order_fresh", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "addr",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_fresh", status = EatsOrderStatus.RIDER_ASSIGNED,
                riderId = "rider_1",
            )
            freshOrder.updatedAt = Instant.now().minus(Duration.ofMinutes(5))
            every { eatsOrderRepository.findById("eats_order_fresh") } returns Optional.of(freshOrder)

            val result = service.forceCancelAbandonedDelivery("eats_order_fresh")

            Then("it real-re-checks elapsed time and leaves the still-genuinely-in-flight delivery untouched") {
                result?.status shouldBe EatsOrderStatus.RIDER_ASSIGNED
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }

        When("a real delivery already reached DELIVERED before the scheduler got to it") {
            val deliveredOrder = EatsOrder(
                id = "eats_order_done", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "addr",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_done", status = EatsOrderStatus.DELIVERED,
                riderId = "rider_1",
            )
            every { eatsOrderRepository.findById("eats_order_done") } returns Optional.of(deliveredOrder)

            val result = service.forceCancelAbandonedDelivery("eats_order_done")

            Then("it real-no-ops rather than clawing back a real rider's already-earned, already-paid-out delivery") {
                result?.status shouldBe EatsOrderStatus.DELIVERED
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }
    }

    Given("a real restaurant marking a real item unavailable on a real accepted order") {
        val merchantRepository = mockk<MerchantRepository>()
        // Real Eats order-status webhook (2026-08-30) -- notifyRestaurantWebhook's own
        // merchantRepository.findById lookup needs a safe default across every test in
        // this file, most of which never cared about that call before this feature
        // existed. A more specific findById stub registered later in a given When block
        // still correctly takes precedence for its own exact id (MockK checks
        // most-recently-registered stubs first) -- this is purely a fallback for every
        // other id/test that never stubbed it at all.
        every { merchantRepository.findById(any<String>()) } returns Optional.empty()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val riderRepository = mockk<RiderRepository>()
        val eatsOrderRepository = mockk<EatsOrderRepository>()
        every { eatsOrderRepository.existsByRiderIdAndStatusIn(any(), any()) } returns false
        every { eatsOrderRepository.findDistinctRiderIdsByStatusIn(any()) } returns emptyList()
        val eatsOrderItemRepository = mockk<EatsOrderItemRepository>(relaxed = true)
        val menuOptionGroupRepository = mockk<MenuOptionGroupRepository>(relaxed = true)
        every { menuOptionGroupRepository.findByProductIdInOrderByDisplayOrderAsc(any()) } returns emptyList()
        val menuOptionChoiceRepository = mockk<MenuOptionChoiceRepository>(relaxed = true)
        every { menuOptionChoiceRepository.findByGroupIdInOrderByDisplayOrderAsc(any()) } returns emptyList()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>()
        every { osrmRoutingClient.routeDistanceKm(any(), any(), any(), any()) } returns null
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>()
        every { nominatimGeocodingClient.geocode(any()) } returns null
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val autoTopUpService = mockk<rw.itunda.account.AutoTopUpService>(relaxed = true)
        every { autoTopUpService.ensureSufficientPayBalance(any(), any(), any()) } answers { secondArg() }
        val webhookDeliveryService = mockk<rw.itunda.merchant.WebhookDeliveryService>(relaxed = true)
        val service = EatsOrderService(
            merchantRepository, merchantProductRepository, riderRepository, eatsOrderRepository,
            eatsOrderItemRepository, menuOptionGroupRepository, menuOptionChoiceRepository, accountRepository, ledgerService, transactionRepository, fraudRuleEngine,
            ledgerEntryRepository, osrmRoutingClient, nominatimGeocodingClient, rateLimiter, notificationRepository,
            mockk<EatsMembershipService>(relaxed = true).also { every { it.hasActiveMembership(any()) } returns false },
            mockk<PlatformMembershipService>(relaxed = true).also { every { it.hasActiveMembership(any()) } returns false },
            pushNotificationService,
            autoTopUpService, webhookDeliveryService,
        )

        val restaurant = Merchant(id = "restaurant_1", ownerUserId = "owner_1", accountId = "account_restaurant", businessName = "Kigali Grill", status = MerchantStatus.ACTIVE)
        val buyerAccount = Account(id = "account_buyer", userId = "buyer_1", accountNumber = "ACC-B", accountName = "Buyer", type = AccountType.PAY, balance = BigDecimal("50000"), availableBalance = BigDecimal("50000"))
        val restaurantAccount = Account(id = "account_restaurant", userId = "owner_1", accountNumber = "ACC-R", accountName = "Restaurant", type = AccountType.MAIN, balance = BigDecimal("50000"), availableBalance = BigDecimal("50000"))
        every { accountRepository.findByUserIdAndType("buyer_1", AccountType.PAY) } returns buyerAccount
        every { accountRepository.findById("account_restaurant") } returns Optional.of(restaurantAccount)

        fun acceptedOrder(status: EatsOrderStatus = EatsOrderStatus.ACCEPTED) = EatsOrder(
            id = "eats_order_iu1", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "addr",
            itemsSubtotal = BigDecimal("9000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("135"),
            totalAmount = BigDecimal("10500"), transactionId = "ledgertxn_iu1", status = status,
        )
        fun items() = listOf(
            EatsOrderItem(id = "item_1", orderId = "eats_order_iu1", productId = "product_1", productName = "Grilled Chicken", unitPrice = BigDecimal("4000"), quantity = 1),
            EatsOrderItem(id = "item_2", orderId = "eats_order_iu1", productId = "product_2", productName = "Fries", unitPrice = BigDecimal("2500"), quantity = 2),
        )

        When("the restaurant marks one of two items unavailable") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns restaurant
            every { eatsOrderRepository.findByIdForUpdate("eats_order_iu1") } returns Optional.of(acceptedOrder())
            every { eatsOrderItemRepository.findByOrderId("eats_order_iu1") } returns items()
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_refund_1", emptyList())
            val savedItem = slot<EatsOrderItem>()
            every { eatsOrderItemRepository.save(capture(savedItem)) } answers { firstArg() }

            service.markItemUnavailable("owner_1", "eats_order_iu1", "item_1")

            Then("it posts a real 2-leg refund crediting the buyer and clawing back the restaurant for exactly that item's amount") {
                verify(exactly = 1) {
                    ledgerService.postLedgerTransaction(
                        "RWF",
                        match { legs ->
                            legs.size == 2 &&
                                legs.any { it.accountId == "account_buyer" && it.direction == LedgerDirection.CREDIT && it.amount == BigDecimal("4000") } &&
                                legs.any { it.accountId == "account_restaurant" && it.direction == LedgerDirection.DEBIT && it.amount == BigDecimal("4000") }
                        },
                    )
                }
            }

            Then("it marks the item unavailable with the real refund transaction id, never touches the other item") {
                savedItem.captured.id shouldBe "item_1"
                savedItem.captured.unavailable shouldBe true
                savedItem.captured.refundTransactionId shouldBe "ledgertxn_refund_1"
            }

            Then("it real-notifies the buyer") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "buyer_1" && it.type == "EATS_ORDER_UPDATE" }) }
            }
        }

        When("the order is still PLACED, before real fulfillment has started") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns restaurant
            every { eatsOrderRepository.findByIdForUpdate("eats_order_iu1") } returns Optional.of(acceptedOrder(EatsOrderStatus.PLACED))

            Then("it throws InvalidEatsOrderStatusTransitionException rather than letting a not-yet-accepted order be partially refunded") {
                try {
                    service.markItemUnavailable("owner_1", "eats_order_iu1", "item_1")
                    error("expected InvalidEatsOrderStatusTransitionException")
                } catch (e: InvalidEatsOrderStatusTransitionException) {
                    // expected
                }
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }

        When("the same item is already marked unavailable") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns restaurant
            every { eatsOrderRepository.findByIdForUpdate("eats_order_iu1") } returns Optional.of(acceptedOrder())
            val alreadyUnavailable = listOf(
                EatsOrderItem(id = "item_1", orderId = "eats_order_iu1", productId = "product_1", productName = "Grilled Chicken", unitPrice = BigDecimal("4000"), quantity = 1, unavailable = true, refundTransactionId = "ledgertxn_prior"),
                items()[1],
            )
            every { eatsOrderItemRepository.findByOrderId("eats_order_iu1") } returns alreadyUnavailable

            Then("it throws EatsOrderItemAlreadyUnavailableException, never double-refunds") {
                try {
                    service.markItemUnavailable("owner_1", "eats_order_iu1", "item_1")
                    error("expected EatsOrderItemAlreadyUnavailableException")
                } catch (e: EatsOrderItemAlreadyUnavailableException) {
                    // expected
                }
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }

        When("marking the item would leave zero available items in the order") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns restaurant
            every { eatsOrderRepository.findByIdForUpdate("eats_order_iu1") } returns Optional.of(acceptedOrder())
            every { eatsOrderItemRepository.findByOrderId("eats_order_iu1") } returns listOf(items()[0])

            Then("it throws EatsOrderAllItemsUnavailableException rather than silently emptying the order") {
                try {
                    service.markItemUnavailable("owner_1", "eats_order_iu1", "item_1")
                    error("expected EatsOrderAllItemsUnavailableException")
                } catch (e: EatsOrderAllItemsUnavailableException) {
                    // expected
                }
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }

        When("a different restaurant owner (not this order's own) tries to mark an item unavailable") {
            val otherRestaurant = Merchant(id = "restaurant_2", ownerUserId = "owner_2", accountId = "account_other", businessName = "Other Diner", status = MerchantStatus.ACTIVE)
            every { merchantRepository.findByOwnerUserId("owner_2") } returns otherRestaurant
            every { eatsOrderRepository.findByIdForUpdate("eats_order_iu1") } returns Optional.of(acceptedOrder())

            Then("it throws EatsOrderNotFoundException -- same IDOR-safe 404 as every other order lookup, not a leak of existence") {
                try {
                    service.markItemUnavailable("owner_2", "eats_order_iu1", "item_1")
                    error("expected EatsOrderNotFoundException")
                } catch (e: EatsOrderNotFoundException) {
                    // expected
                }
            }
        }
    }

    Given("a real available rider and a real order ready for pickup") {
        val merchantRepository = mockk<MerchantRepository>()
        // Real Eats order-status webhook (2026-08-30) -- notifyRestaurantWebhook's own
        // merchantRepository.findById lookup needs a safe default across every test in
        // this file, most of which never cared about that call before this feature
        // existed. A more specific findById stub registered later in a given When block
        // still correctly takes precedence for its own exact id (MockK checks
        // most-recently-registered stubs first) -- this is purely a fallback for every
        // other id/test that never stubbed it at all.
        every { merchantRepository.findById(any<String>()) } returns Optional.empty()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val riderRepository = mockk<RiderRepository>()
        val eatsOrderRepository = mockk<EatsOrderRepository>()
        // Real 단건배달 (single-order delivery) guarantee (2026-07-26) -- default "no
        // rider is currently busy" stub for every pre-existing test in this suite, none
        // of which predate or specifically exercise this new behavior. See
        // EatsOrderService.claimDelivery's own doc comment.
        every { eatsOrderRepository.existsByRiderIdAndStatusIn(any(), any()) } returns false
        every { eatsOrderRepository.findDistinctRiderIdsByStatusIn(any()) } returns emptyList()
        val eatsOrderItemRepository = mockk<EatsOrderItemRepository>(relaxed = true)
        // Real menu-options resolution (2026-07-21) -- relaxed + explicit empty-list
        // stubs, matching this suite's own existing "no option groups defined" default
        // for every pre-existing test (see MenuOptionGroup.kt's own doc comment: purely
        // additive, a product with zero groups behaves exactly as before).
        val menuOptionGroupRepository = mockk<MenuOptionGroupRepository>(relaxed = true)
        every { menuOptionGroupRepository.findByProductIdInOrderByDisplayOrderAsc(any()) } returns emptyList()
        val menuOptionChoiceRepository = mockk<MenuOptionChoiceRepository>(relaxed = true)
        every { menuOptionChoiceRepository.findByGroupIdInOrderByDisplayOrderAsc(any()) } returns emptyList()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>()
        every { osrmRoutingClient.routeDistanceKm(any(), any(), any(), any()) } returns null
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>()
        every { nominatimGeocodingClient.geocode(any()) } returns null
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        // Real buyer order-status notifications (2026-07-20) -- explicit stub, same
        // known "relaxed mockk can't correctly infer JpaRepository's generic save()
        // signature" gotcha this project's own tests already document repeatedly
        // (MerchantServiceTest/OrderServiceTest/EatsOrderServiceTest/GroupMessagingServiceTest, etc).
        every { notificationRepository.save(any()) } answers { firstArg() }
        val autoTopUpService = mockk<rw.itunda.account.AutoTopUpService>(relaxed = true)
        every { autoTopUpService.ensureSufficientPayBalance(any(), any(), any()) } answers { secondArg() }
        val webhookDeliveryService = mockk<rw.itunda.merchant.WebhookDeliveryService>(relaxed = true)
        val service = EatsOrderService(
            merchantRepository, merchantProductRepository, riderRepository, eatsOrderRepository,
            eatsOrderItemRepository, menuOptionGroupRepository, menuOptionChoiceRepository, accountRepository, ledgerService, transactionRepository, fraudRuleEngine,
            ledgerEntryRepository, osrmRoutingClient, nominatimGeocodingClient, rateLimiter, notificationRepository,
            // Real Baemin Club-style free-delivery membership (2026-07-26) -- relaxed,
            // no active member by default, matching this suite's own "no pre-existing
            // test predates this feature" convention already used for the single-order
            // delivery guard's own default stubs.
            mockk<EatsMembershipService>(relaxed = true).also { every { it.hasActiveMembership(any()) } returns false },
            // Real Coupang Wow-style unconditional waiver (2026-07-31) -- same
            // relaxed, no-active-member-by-default convention as EatsMembershipService above.
            mockk<PlatformMembershipService>(relaxed = true).also { every { it.hasActiveMembership(any()) } returns false },
            pushNotificationService,
            autoTopUpService, webhookDeliveryService,
        )
        val rider = Rider(id = "rider_1", userId = "rider_user_1", accountId = "account_rider", available = true)
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

            Then("it real-notifies the buyer that a rider was assigned") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "buyer_1" && it.type == "EATS_ORDER_UPDATE" }) }
            }
        }

        When("a real rider tries to claim a real Baemin-style PICKUP order directly by id") {
            val pickupReadyOrder = EatsOrder(
                id = "eats_order_pickup_claim", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "Pickup at Kigali Grill",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal.ZERO, platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("6000"), transactionId = "ledgertxn_pickup_claim", status = EatsOrderStatus.READY_FOR_PICKUP,
                fulfillmentType = EatsFulfillmentType.PICKUP,
            )
            every { riderRepository.findByUserId("rider_user_1") } returns rider
            every { eatsOrderRepository.findById("eats_order_pickup_claim") } returns Optional.of(pickupReadyOrder)

            Then("it throws DeliveryAlreadyClaimedException -- real defense-in-depth, a PICKUP order has no rider to assign") {
                try {
                    service.claimDelivery("rider_user_1", "eats_order_pickup_claim")
                    error("expected DeliveryAlreadyClaimedException")
                } catch (e: DeliveryAlreadyClaimedException) {
                    verify(exactly = 0) { eatsOrderRepository.save(any()) }
                }
            }
        }

        When("an OFFLINE rider tries to claim it") {
            val offlineRider = Rider(id = "rider_2", userId = "rider_user_2", accountId = "account_rider_2", available = false)
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

        When("a rider who's already carrying a real active delivery tries to claim a second one") {
            every { riderRepository.findByUserId("rider_user_1") } returns rider
            every { eatsOrderRepository.existsByRiderIdAndStatusIn("rider_1", listOf(EatsOrderStatus.RIDER_ASSIGNED, EatsOrderStatus.PICKED_UP)) } returns true

            Then("it throws RiderAlreadyOnDeliveryException -- real 단건배달 (single-order delivery), the same real Coupang Eats/배민1 guarantee") {
                try {
                    service.claimDelivery("rider_user_1", "eats_order_1")
                    error("expected RiderAlreadyOnDeliveryException")
                } catch (e: RiderAlreadyOnDeliveryException) {
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

        When("a real rider WITHOUT the real active dispatch offer tries to claim it") {
            val offeredOrder = EatsOrder(
                id = "eats_order_6", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "addr",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_6", status = EatsOrderStatus.READY_FOR_PICKUP,
                offeredRiderId = "rider_other", offerExpiresAt = Instant.now().plusSeconds(60),
            )
            every { riderRepository.findByUserId("rider_user_1") } returns rider
            every { eatsOrderRepository.findById("eats_order_6") } returns Optional.of(offeredOrder)

            Then("it real-409s the same way an already-claimed delivery does -- never leaking that an active offer exists") {
                try {
                    service.claimDelivery("rider_user_1", "eats_order_6")
                    error("expected DeliveryAlreadyClaimedException")
                } catch (e: DeliveryAlreadyClaimedException) {
                    // expected
                }
            }
        }

        When("the real rider WITH the real active dispatch offer claims it") {
            val offeredOrder = EatsOrder(
                id = "eats_order_7", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "addr",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_7", status = EatsOrderStatus.READY_FOR_PICKUP,
                offeredRiderId = "rider_1", offerExpiresAt = Instant.now().plusSeconds(60),
            )
            every { riderRepository.findByUserId("rider_user_1") } returns rider
            every { eatsOrderRepository.findById("eats_order_7") } returns Optional.of(offeredOrder)
            every { eatsOrderRepository.save(any()) } answers { firstArg() }

            val result = service.claimDelivery("rider_user_1", "eats_order_7")

            Then("it real-succeeds and real-clears the offer fields") {
                result.status shouldBe EatsOrderStatus.RIDER_ASSIGNED
                result.offeredRiderId shouldBe null
                result.offerExpiresAt shouldBe null
            }
        }

        When("a real rider claims it after their own real dispatch offer already expired") {
            val expiredOfferOrder = EatsOrder(
                id = "eats_order_8", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "addr",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_8", status = EatsOrderStatus.READY_FOR_PICKUP,
                offeredRiderId = "rider_other", offerExpiresAt = Instant.now().minusSeconds(5),
            )
            every { riderRepository.findByUserId("rider_user_1") } returns rider
            every { eatsOrderRepository.findById("eats_order_8") } returns Optional.of(expiredOfferOrder)
            every { eatsOrderRepository.save(any()) } answers { firstArg() }

            val result = service.claimDelivery("rider_user_1", "eats_order_8")

            Then("a real expired offer no longer blocks anyone -- it's genuinely open again, matching the honest open-browse fallback") {
                result.status shouldBe EatsOrderStatus.RIDER_ASSIGNED
                result.riderId shouldBe "rider_1"
            }
        }
    }

    Given("a real rider with an active dispatch offer, deciding whether to accept it") {
        val merchantRepository = mockk<MerchantRepository>()
        // Real Eats order-status webhook (2026-08-30) -- notifyRestaurantWebhook's own
        // merchantRepository.findById lookup needs a safe default across every test in
        // this file, most of which never cared about that call before this feature
        // existed. A more specific findById stub registered later in a given When block
        // still correctly takes precedence for its own exact id (MockK checks
        // most-recently-registered stubs first) -- this is purely a fallback for every
        // other id/test that never stubbed it at all.
        every { merchantRepository.findById(any<String>()) } returns Optional.empty()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val riderRepository = mockk<RiderRepository>()
        val eatsOrderRepository = mockk<EatsOrderRepository>()
        // Real 단건배달 (single-order delivery) guarantee (2026-07-26) -- default "no
        // rider is currently busy" stub for every pre-existing test in this suite, none
        // of which predate or specifically exercise this new behavior. See
        // EatsOrderService.claimDelivery's own doc comment.
        every { eatsOrderRepository.existsByRiderIdAndStatusIn(any(), any()) } returns false
        every { eatsOrderRepository.findDistinctRiderIdsByStatusIn(any()) } returns emptyList()
        val eatsOrderItemRepository = mockk<EatsOrderItemRepository>(relaxed = true)
        // Real menu-options resolution (2026-07-21) -- relaxed + explicit empty-list
        // stubs, matching this suite's own existing "no option groups defined" default
        // for every pre-existing test (see MenuOptionGroup.kt's own doc comment: purely
        // additive, a product with zero groups behaves exactly as before).
        val menuOptionGroupRepository = mockk<MenuOptionGroupRepository>(relaxed = true)
        every { menuOptionGroupRepository.findByProductIdInOrderByDisplayOrderAsc(any()) } returns emptyList()
        val menuOptionChoiceRepository = mockk<MenuOptionChoiceRepository>(relaxed = true)
        every { menuOptionChoiceRepository.findByGroupIdInOrderByDisplayOrderAsc(any()) } returns emptyList()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>()
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        // Real buyer order-status notifications (2026-07-20) -- explicit stub, same
        // known "relaxed mockk can't correctly infer JpaRepository's generic save()
        // signature" gotcha this project's own tests already document repeatedly
        // (MerchantServiceTest/OrderServiceTest/EatsOrderServiceTest/GroupMessagingServiceTest, etc).
        every { notificationRepository.save(any()) } answers { firstArg() }
        val autoTopUpService = mockk<rw.itunda.account.AutoTopUpService>(relaxed = true)
        every { autoTopUpService.ensureSufficientPayBalance(any(), any(), any()) } answers { secondArg() }
        val webhookDeliveryService = mockk<rw.itunda.merchant.WebhookDeliveryService>(relaxed = true)
        val service = EatsOrderService(
            merchantRepository, merchantProductRepository, riderRepository, eatsOrderRepository,
            eatsOrderItemRepository, menuOptionGroupRepository, menuOptionChoiceRepository, accountRepository, ledgerService, transactionRepository, fraudRuleEngine,
            ledgerEntryRepository, osrmRoutingClient, nominatimGeocodingClient, rateLimiter, notificationRepository,
            // Real Baemin Club-style free-delivery membership (2026-07-26) -- relaxed,
            // no active member by default, matching this suite's own "no pre-existing
            // test predates this feature" convention already used for the single-order
            // delivery guard's own default stubs.
            mockk<EatsMembershipService>(relaxed = true).also { every { it.hasActiveMembership(any()) } returns false },
            // Real Coupang Wow-style unconditional waiver (2026-07-31) -- same
            // relaxed, no-active-member-by-default convention as EatsMembershipService above.
            mockk<PlatformMembershipService>(relaxed = true).also { every { it.hasActiveMembership(any()) } returns false },
            pushNotificationService,
            autoTopUpService, webhookDeliveryService,
        )
        val restaurant = Merchant(id = "restaurant_1", ownerUserId = "owner_1", accountId = "account_restaurant", businessName = "Kigali Grill", status = MerchantStatus.ACTIVE, latitude = -1.9536, longitude = 30.0605)
        val rider = Rider(id = "rider_1", userId = "rider_user_1", accountId = "account_rider", available = true)

        When("they real-decline their real active offer") {
            val offeredOrder = EatsOrder(
                id = "eats_order_9", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "addr",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_9", status = EatsOrderStatus.READY_FOR_PICKUP,
                offeredRiderId = "rider_1", offerExpiresAt = Instant.now().plusSeconds(60),
            )
            val nextRider = Rider(id = "rider_next", userId = "rider_user_next", accountId = "account_next", available = true, currentLatitude = -1.9536, currentLongitude = 30.0605)
            every { riderRepository.findByUserId("rider_user_1") } returns rider
            every { eatsOrderRepository.findById("eats_order_9") } returns Optional.of(offeredOrder)
            every { eatsOrderRepository.save(any()) } answers { firstArg() }
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            every { riderRepository.findByAvailableTrueAndCurrentLatitudeIsNotNullAndCurrentLongitudeIsNotNull() } returns listOf(nextRider)

            val result = service.declineDelivery("rider_user_1", "eats_order_9")

            Then("it real-excludes them and real-reassigns to the next real candidate immediately, not waiting for the timeout") {
                result.offeredRiderId shouldBe "rider_next"
                result.excludedRiderUserIds shouldBe "rider_user_1"
                verify { notificationRepository.save(match<Notification> { it.type == "DELIVERY_OFFER" && it.userId == "rider_user_next" }) }
            }
        }

        When("a real rider tries to decline a delivery they were never offered") {
            val order = EatsOrder(
                id = "eats_order_10", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "addr",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_10", status = EatsOrderStatus.READY_FOR_PICKUP,
                offeredRiderId = "rider_someone_else", offerExpiresAt = Instant.now().plusSeconds(60),
            )
            every { riderRepository.findByUserId("rider_user_1") } returns rider
            every { eatsOrderRepository.findById("eats_order_10") } returns Optional.of(order)

            Then("it throws NoActiveOfferException") {
                try {
                    service.declineDelivery("rider_user_1", "eats_order_10")
                    error("expected NoActiveOfferException")
                } catch (e: NoActiveOfferException) {
                    // expected
                }
            }
        }
    }

    Given("real dispatch offers that expired without a response") {
        val merchantRepository = mockk<MerchantRepository>()
        // Real Eats order-status webhook (2026-08-30) -- notifyRestaurantWebhook's own
        // merchantRepository.findById lookup needs a safe default across every test in
        // this file, most of which never cared about that call before this feature
        // existed. A more specific findById stub registered later in a given When block
        // still correctly takes precedence for its own exact id (MockK checks
        // most-recently-registered stubs first) -- this is purely a fallback for every
        // other id/test that never stubbed it at all.
        every { merchantRepository.findById(any<String>()) } returns Optional.empty()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val riderRepository = mockk<RiderRepository>()
        val eatsOrderRepository = mockk<EatsOrderRepository>()
        // Real 단건배달 (single-order delivery) guarantee (2026-07-26) -- default "no
        // rider is currently busy" stub for every pre-existing test in this suite, none
        // of which predate or specifically exercise this new behavior. See
        // EatsOrderService.claimDelivery's own doc comment.
        every { eatsOrderRepository.existsByRiderIdAndStatusIn(any(), any()) } returns false
        every { eatsOrderRepository.findDistinctRiderIdsByStatusIn(any()) } returns emptyList()
        val eatsOrderItemRepository = mockk<EatsOrderItemRepository>(relaxed = true)
        // Real menu-options resolution (2026-07-21) -- relaxed + explicit empty-list
        // stubs, matching this suite's own existing "no option groups defined" default
        // for every pre-existing test (see MenuOptionGroup.kt's own doc comment: purely
        // additive, a product with zero groups behaves exactly as before).
        val menuOptionGroupRepository = mockk<MenuOptionGroupRepository>(relaxed = true)
        every { menuOptionGroupRepository.findByProductIdInOrderByDisplayOrderAsc(any()) } returns emptyList()
        val menuOptionChoiceRepository = mockk<MenuOptionChoiceRepository>(relaxed = true)
        every { menuOptionChoiceRepository.findByGroupIdInOrderByDisplayOrderAsc(any()) } returns emptyList()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>()
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        // Real buyer order-status notifications (2026-07-20) -- explicit stub, same
        // known "relaxed mockk can't correctly infer JpaRepository's generic save()
        // signature" gotcha this project's own tests already document repeatedly
        // (MerchantServiceTest/OrderServiceTest/EatsOrderServiceTest/GroupMessagingServiceTest, etc).
        every { notificationRepository.save(any()) } answers { firstArg() }
        val autoTopUpService = mockk<rw.itunda.account.AutoTopUpService>(relaxed = true)
        every { autoTopUpService.ensureSufficientPayBalance(any(), any(), any()) } answers { secondArg() }
        val webhookDeliveryService = mockk<rw.itunda.merchant.WebhookDeliveryService>(relaxed = true)
        val service = EatsOrderService(
            merchantRepository, merchantProductRepository, riderRepository, eatsOrderRepository,
            eatsOrderItemRepository, menuOptionGroupRepository, menuOptionChoiceRepository, accountRepository, ledgerService, transactionRepository, fraudRuleEngine,
            ledgerEntryRepository, osrmRoutingClient, nominatimGeocodingClient, rateLimiter, notificationRepository,
            // Real Baemin Club-style free-delivery membership (2026-07-26) -- relaxed,
            // no active member by default, matching this suite's own "no pre-existing
            // test predates this feature" convention already used for the single-order
            // delivery guard's own default stubs.
            mockk<EatsMembershipService>(relaxed = true).also { every { it.hasActiveMembership(any()) } returns false },
            // Real Coupang Wow-style unconditional waiver (2026-07-31) -- same
            // relaxed, no-active-member-by-default convention as EatsMembershipService above.
            mockk<PlatformMembershipService>(relaxed = true).also { every { it.hasActiveMembership(any()) } returns false },
            pushNotificationService,
            autoTopUpService, webhookDeliveryService,
        )
        val restaurant = Merchant(id = "restaurant_1", ownerUserId = "owner_1", accountId = "account_restaurant", businessName = "Kigali Grill", status = MerchantStatus.ACTIVE, latitude = -1.9536, longitude = 30.0605)

        When("the real scheduler finds and reassigns one") {
            val expiredOrder = EatsOrder(
                id = "eats_order_11", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "addr",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_11", status = EatsOrderStatus.READY_FOR_PICKUP,
                offeredRiderId = "rider_timed_out", offerExpiresAt = Instant.now().minusSeconds(5),
            )
            val timedOutRider = Rider(id = "rider_timed_out", userId = "rider_user_timed_out", accountId = "account_timedout")
            val nextRider = Rider(id = "rider_next", userId = "rider_user_next", accountId = "account_next", available = true, currentLatitude = -1.9536, currentLongitude = 30.0605)
            every { eatsOrderRepository.findByOfferExpiresAtBeforeAndRiderIdIsNull(any()) } returns listOf(expiredOrder)
            every { eatsOrderRepository.findById("eats_order_11") } returns Optional.of(expiredOrder)
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            every { riderRepository.findById("rider_timed_out") } returns Optional.of(timedOutRider)
            every { eatsOrderRepository.save(any()) } answers { firstArg() }
            every { riderRepository.findByAvailableTrueAndCurrentLatitudeIsNotNullAndCurrentLongitudeIsNotNull() } returns listOf(nextRider)

            val expired = service.getExpiredOffers()
            val pools = service.computeDispatchPools()
            service.reassignExpiredOffer(expired.first().id, pools)

            Then("it real-excludes the timed-out rider and real-offers to the next real candidate") {
                expired shouldBe listOf(expiredOrder)
                expiredOrder.excludedRiderUserIds shouldBe "rider_user_timed_out"
                expiredOrder.offeredRiderId shouldBe "rider_next"
                verify { notificationRepository.save(match<Notification> { it.type == "DELIVERY_OFFER" && it.userId == "rider_user_next" }) }
            }
        }

        When("the real scheduler reassigns two real expired offers for two different real restaurants in one tick") {
            val restaurant2 = Merchant(id = "restaurant_2", ownerUserId = "owner_2", accountId = "account_restaurant_2", businessName = "Huye Grill", status = MerchantStatus.ACTIVE, latitude = -2.5967, longitude = 29.7392)
            val expiredOrder1 = EatsOrder(
                id = "eats_order_12", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "addr",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_12", status = EatsOrderStatus.READY_FOR_PICKUP,
                offeredRiderId = "rider_timed_out_1", offerExpiresAt = Instant.now().minusSeconds(5),
            )
            val expiredOrder2 = EatsOrder(
                id = "eats_order_13", buyerId = "buyer_2", restaurantId = "restaurant_2", deliveryAddress = "addr",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_13", status = EatsOrderStatus.READY_FOR_PICKUP,
                offeredRiderId = "rider_timed_out_2", offerExpiresAt = Instant.now().minusSeconds(5),
            )
            val timedOutRider1 = Rider(id = "rider_timed_out_1", userId = "rider_user_timed_out_1", accountId = "account_to1")
            val timedOutRider2 = Rider(id = "rider_timed_out_2", userId = "rider_user_timed_out_2", accountId = "account_to2")
            val nextRider = Rider(id = "rider_next", userId = "rider_user_next", accountId = "account_next", available = true, currentLatitude = -1.9536, currentLongitude = 30.0605)
            every { eatsOrderRepository.findById("eats_order_12") } returns Optional.of(expiredOrder1)
            every { eatsOrderRepository.findById("eats_order_13") } returns Optional.of(expiredOrder2)
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            every { merchantRepository.findById("restaurant_2") } returns Optional.of(restaurant2)
            every { riderRepository.findById("rider_timed_out_1") } returns Optional.of(timedOutRider1)
            every { riderRepository.findById("rider_timed_out_2") } returns Optional.of(timedOutRider2)
            every { eatsOrderRepository.save(any()) } answers { firstArg() }
            every { riderRepository.findByAvailableTrueAndCurrentLatitudeIsNotNullAndCurrentLongitudeIsNotNull() } returns listOf(nextRider)

            val pools = service.computeDispatchPools()
            service.reassignExpiredOffer(expiredOrder1.id, pools)
            service.reassignExpiredOffer(expiredOrder2.id, pools)

            Then("the real candidate pool is fetched exactly once for the whole tick, shared across both orders") {
                verify(exactly = 1) { riderRepository.findByAvailableTrueAndCurrentLatitudeIsNotNullAndCurrentLongitudeIsNotNull() }
                expiredOrder1.excludedRiderUserIds shouldBe "rider_user_timed_out_1"
                expiredOrder2.excludedRiderUserIds shouldBe "rider_user_timed_out_2"
            }
        }

        // Section 180: DispatchOfferScheduler used to call a single batch-@Transactional
        // reassignExpiredOffers(orders: List<EatsOrder>) that looped every expired offer
        // network-wide in one shared transaction -- see
        // EatsOrderService.reassignExpiredOffer's own doc comment for the full account
        // of the real transaction-poisoning bug this closed. This case covers the
        // re-check-before-act guard the per-item method that replaced it now has.
        When("a real rider already claimed the order in the gap before the scheduler's per-item call ran") {
            val claimedOrder = EatsOrder(
                id = "eats_order_14", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "addr",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_14", status = EatsOrderStatus.RIDER_ASSIGNED,
                riderId = "rider_who_claimed_it", offeredRiderId = "rider_who_claimed_it", offerExpiresAt = Instant.now().minusSeconds(5),
            )
            every { eatsOrderRepository.findById("eats_order_14") } returns Optional.of(claimedOrder)

            val pools = EatsDispatchPools(candidatePool = emptyList(), busyRiderIds = emptySet())
            service.reassignExpiredOffer(claimedOrder.id, pools)

            Then("the real re-check-before-act guard safely no-ops instead of clobbering the rider's real claim") {
                verify(exactly = 0) { eatsOrderRepository.save(any()) }
                claimedOrder.offeredRiderId shouldBe "rider_who_claimed_it"
            }
        }
    }

    Given("a real rider assigned to a real delivery") {
        val merchantRepository = mockk<MerchantRepository>()
        // Real Eats order-status webhook (2026-08-30) -- notifyRestaurantWebhook's own
        // merchantRepository.findById lookup needs a safe default across every test in
        // this file, most of which never cared about that call before this feature
        // existed. A more specific findById stub registered later in a given When block
        // still correctly takes precedence for its own exact id (MockK checks
        // most-recently-registered stubs first) -- this is purely a fallback for every
        // other id/test that never stubbed it at all.
        every { merchantRepository.findById(any<String>()) } returns Optional.empty()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val riderRepository = mockk<RiderRepository>()
        val eatsOrderRepository = mockk<EatsOrderRepository>()
        // Real 단건배달 (single-order delivery) guarantee (2026-07-26) -- default "no
        // rider is currently busy" stub for every pre-existing test in this suite, none
        // of which predate or specifically exercise this new behavior. See
        // EatsOrderService.claimDelivery's own doc comment.
        every { eatsOrderRepository.existsByRiderIdAndStatusIn(any(), any()) } returns false
        every { eatsOrderRepository.findDistinctRiderIdsByStatusIn(any()) } returns emptyList()
        val eatsOrderItemRepository = mockk<EatsOrderItemRepository>(relaxed = true)
        // Real menu-options resolution (2026-07-21) -- relaxed + explicit empty-list
        // stubs, matching this suite's own existing "no option groups defined" default
        // for every pre-existing test (see MenuOptionGroup.kt's own doc comment: purely
        // additive, a product with zero groups behaves exactly as before).
        val menuOptionGroupRepository = mockk<MenuOptionGroupRepository>(relaxed = true)
        every { menuOptionGroupRepository.findByProductIdInOrderByDisplayOrderAsc(any()) } returns emptyList()
        val menuOptionChoiceRepository = mockk<MenuOptionChoiceRepository>(relaxed = true)
        every { menuOptionChoiceRepository.findByGroupIdInOrderByDisplayOrderAsc(any()) } returns emptyList()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>()
        every { osrmRoutingClient.routeDistanceKm(any(), any(), any(), any()) } returns null
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>()
        every { nominatimGeocodingClient.geocode(any()) } returns null
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        // Real buyer order-status notifications (2026-07-20) -- explicit stub, same
        // known "relaxed mockk can't correctly infer JpaRepository's generic save()
        // signature" gotcha this project's own tests already document repeatedly
        // (MerchantServiceTest/OrderServiceTest/EatsOrderServiceTest/GroupMessagingServiceTest, etc).
        every { notificationRepository.save(any()) } answers { firstArg() }
        val autoTopUpService = mockk<rw.itunda.account.AutoTopUpService>(relaxed = true)
        every { autoTopUpService.ensureSufficientPayBalance(any(), any(), any()) } answers { secondArg() }
        val webhookDeliveryService = mockk<rw.itunda.merchant.WebhookDeliveryService>(relaxed = true)
        val service = EatsOrderService(
            merchantRepository, merchantProductRepository, riderRepository, eatsOrderRepository,
            eatsOrderItemRepository, menuOptionGroupRepository, menuOptionChoiceRepository, accountRepository, ledgerService, transactionRepository, fraudRuleEngine,
            ledgerEntryRepository, osrmRoutingClient, nominatimGeocodingClient, rateLimiter, notificationRepository,
            // Real Baemin Club-style free-delivery membership (2026-07-26) -- relaxed,
            // no active member by default, matching this suite's own "no pre-existing
            // test predates this feature" convention already used for the single-order
            // delivery guard's own default stubs.
            mockk<EatsMembershipService>(relaxed = true).also { every { it.hasActiveMembership(any()) } returns false },
            // Real Coupang Wow-style unconditional waiver (2026-07-31) -- same
            // relaxed, no-active-member-by-default convention as EatsMembershipService above.
            mockk<PlatformMembershipService>(relaxed = true).also { every { it.hasActiveMembership(any()) } returns false },
            pushNotificationService,
            autoTopUpService, webhookDeliveryService,
        )
        val rider = Rider(id = "rider_1", userId = "rider_user_1", accountId = "account_rider", available = true)
        val riderAccount = account("account_rider", "rider_user_1")
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

            Then("it real-notifies the buyer that their order was picked up") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "buyer_1" && it.type == "EATS_ORDER_UPDATE" }) }
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
            every { accountRepository.findById("account_rider") } returns Optional.of(riderAccount)
            val legsSlot = slot<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("payout_txn_1", emptyList())

            val result = service.updateRiderStatus("rider_user_1", "eats_order_1", EatsOrderStatus.DELIVERED)

            Then("it real-pays the delivery fee out of eats_delivery_holding straight into the rider's own account") {
                result.status shouldBe EatsOrderStatus.DELIVERED
                result.deliveryPayoutTransactionId shouldBe "payout_txn_1"

                val legs = legsSlot.captured
                val holdingLeg = legs.first { it.accountId == "eats_delivery_holding" }
                val riderLeg = legs.first { it.accountId == "account_rider" }
                holdingLeg.amount shouldBe BigDecimal("1500")
                riderLeg.amount shouldBe BigDecimal("1500")
            }

            Then("it real-notifies the buyer that their order was delivered") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "buyer_1" && it.type == "EATS_ORDER_UPDATE" }) }
            }

            // Real gap found live (2026-09-14, FraudRuleEngine per-call-site sweep):
            // placeOrder's own evaluate only covers buyerId -> restaurant.ownerUserId --
            // no rider is assigned at order-placement time, so the rider who actually
            // receives this delivery fee here was never evaluated as a fraud
            // counterparty anywhere in the order's lifecycle.
            Then("the real fraud engine is evaluated against the buyer and the real rider receiving the delivery fee") {
                verify(exactly = 1) { fraudRuleEngine.evaluate("buyer_1", "rider_user_1", BigDecimal("1500"), "payout_txn_1") }
            }
        }

        When("the assigned rider marks it DELIVERED with a real 안심배달 proof photo") {
            val pickedUpOrder = EatsOrder(
                id = "eats_order_1", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "addr",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_1", riderId = "rider_1", status = EatsOrderStatus.PICKED_UP,
            )
            every { riderRepository.findByUserId("rider_user_1") } returns rider
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(pickedUpOrder)
            every { eatsOrderRepository.save(any()) } answers { firstArg() }
            every { accountRepository.findById("account_rider") } returns Optional.of(riderAccount)
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("payout_txn_1", emptyList())

            val result = service.updateRiderStatus("rider_user_1", "eats_order_1", EatsOrderStatus.DELIVERED, "https://example.com/proof.jpg")

            Then("it real-persists the proof photo on the order") {
                result.deliveryProofPhotoUrl shouldBe "https://example.com/proof.jpg"
            }
        }

        When("a photo is submitted on a PICKED_UP transition, not DELIVERED") {
            every { riderRepository.findByUserId("rider_user_1") } returns rider
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(assignedOrder)
            every { eatsOrderRepository.save(any()) } answers { firstArg() }

            val result = service.updateRiderStatus("rider_user_1", "eats_order_1", EatsOrderStatus.PICKED_UP, "https://example.com/proof.jpg")

            Then("it is silently ignored -- the real product only captures a proof photo at drop-off") {
                result.deliveryProofPhotoUrl shouldBe null
            }
        }

        When("someone who isn't the assigned rider tries to advance the delivery") {
            every { riderRepository.findByUserId("someone_else") } returns Rider(id = "rider_2", userId = "someone_else", accountId = "account_2")
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(assignedOrder)

            // Real IDOR fix (2026-09-04): this used to throw NotAssignedRiderException,
            // a distinguishable exception/code from EatsOrderNotFoundException even
            // though both map to the same 404 status -- letting any registered rider
            // probe order ids and learn "exists, assigned to someone else" vs "doesn't
            // exist." Now reuses the exact same exception as the not-found case.
            Then("it throws the same EatsOrderNotFoundException as a genuinely missing order, never a distinguishable one") {
                try {
                    service.updateRiderStatus("someone_else", "eats_order_1", EatsOrderStatus.PICKED_UP)
                    error("expected EatsOrderNotFoundException")
                } catch (e: EatsOrderNotFoundException) {
                    // expected
                }
            }
        }

        // Real gap found live (2026-09-14, FraudRuleEngine-verify sweep): tipRider had
        // zero FraudRuleEngine coverage -- placeOrder's own evaluate call only ever
        // assesses the restaurant as counterparty (no rider is assigned yet at
        // order-placement time), so a tip is real new money to a real counterparty (the
        // rider) never once evaluated anywhere in this order's whole lifecycle.
        When("a real buyer tips the real rider who delivered their order") {
            val deliveredOrder = EatsOrder(
                id = "eats_order_tip_1", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "addr",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_1", riderId = "rider_1", status = EatsOrderStatus.DELIVERED,
            )
            every { eatsOrderRepository.findByIdForUpdate("eats_order_tip_1") } returns Optional.of(deliveredOrder)
            every { riderRepository.findById("rider_1") } returns Optional.of(rider)
            val buyerAccount = account("account_buyer_tip", "buyer_1")
            every { accountRepository.findByUserIdAndType("buyer_1", AccountType.MAIN) } returns buyerAccount
            every { accountRepository.findById("account_rider") } returns Optional.of(riderAccount)
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("tiptxn_1", emptyList())
            every { eatsOrderRepository.save(any()) } answers { firstArg() }

            val result = service.tipRider("buyer_1", "eats_order_tip_1", BigDecimal("500"))

            Then("it real-records the tip amount and transaction id") {
                result.tipAmount shouldBe BigDecimal("500")
                result.tipTransactionId shouldBe "tiptxn_1"
            }

            Then("the real fraud engine is evaluated against the buyer and the rider, a counterparty never assessed before now") {
                verify(exactly = 1) { fraudRuleEngine.evaluate("buyer_1", "rider_user_1", BigDecimal("500"), "tiptxn_1") }
            }
        }
    }

    Given("a real buyer checking their rider's real live location") {
        val merchantRepository = mockk<MerchantRepository>()
        // Real Eats order-status webhook (2026-08-30) -- notifyRestaurantWebhook's own
        // merchantRepository.findById lookup needs a safe default across every test in
        // this file, most of which never cared about that call before this feature
        // existed. A more specific findById stub registered later in a given When block
        // still correctly takes precedence for its own exact id (MockK checks
        // most-recently-registered stubs first) -- this is purely a fallback for every
        // other id/test that never stubbed it at all.
        every { merchantRepository.findById(any<String>()) } returns Optional.empty()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val riderRepository = mockk<RiderRepository>()
        val eatsOrderRepository = mockk<EatsOrderRepository>()
        // Real 단건배달 (single-order delivery) guarantee (2026-07-26) -- default "no
        // rider is currently busy" stub for every pre-existing test in this suite, none
        // of which predate or specifically exercise this new behavior. See
        // EatsOrderService.claimDelivery's own doc comment.
        every { eatsOrderRepository.existsByRiderIdAndStatusIn(any(), any()) } returns false
        every { eatsOrderRepository.findDistinctRiderIdsByStatusIn(any()) } returns emptyList()
        val eatsOrderItemRepository = mockk<EatsOrderItemRepository>(relaxed = true)
        // Real menu-options resolution (2026-07-21) -- relaxed + explicit empty-list
        // stubs, matching this suite's own existing "no option groups defined" default
        // for every pre-existing test (see MenuOptionGroup.kt's own doc comment: purely
        // additive, a product with zero groups behaves exactly as before).
        val menuOptionGroupRepository = mockk<MenuOptionGroupRepository>(relaxed = true)
        every { menuOptionGroupRepository.findByProductIdInOrderByDisplayOrderAsc(any()) } returns emptyList()
        val menuOptionChoiceRepository = mockk<MenuOptionChoiceRepository>(relaxed = true)
        every { menuOptionChoiceRepository.findByGroupIdInOrderByDisplayOrderAsc(any()) } returns emptyList()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>()
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        // Real buyer order-status notifications (2026-07-20) -- explicit stub, same
        // known "relaxed mockk can't correctly infer JpaRepository's generic save()
        // signature" gotcha this project's own tests already document repeatedly
        // (MerchantServiceTest/OrderServiceTest/EatsOrderServiceTest/GroupMessagingServiceTest, etc).
        every { notificationRepository.save(any()) } answers { firstArg() }
        val autoTopUpService = mockk<rw.itunda.account.AutoTopUpService>(relaxed = true)
        every { autoTopUpService.ensureSufficientPayBalance(any(), any(), any()) } answers { secondArg() }
        val webhookDeliveryService = mockk<rw.itunda.merchant.WebhookDeliveryService>(relaxed = true)
        val service = EatsOrderService(
            merchantRepository, merchantProductRepository, riderRepository, eatsOrderRepository,
            eatsOrderItemRepository, menuOptionGroupRepository, menuOptionChoiceRepository, accountRepository, ledgerService, transactionRepository, fraudRuleEngine,
            ledgerEntryRepository, osrmRoutingClient, nominatimGeocodingClient, rateLimiter, notificationRepository,
            // Real Baemin Club-style free-delivery membership (2026-07-26) -- relaxed,
            // no active member by default, matching this suite's own "no pre-existing
            // test predates this feature" convention already used for the single-order
            // delivery guard's own default stubs.
            mockk<EatsMembershipService>(relaxed = true).also { every { it.hasActiveMembership(any()) } returns false },
            // Real Coupang Wow-style unconditional waiver (2026-07-31) -- same
            // relaxed, no-active-member-by-default convention as EatsMembershipService above.
            mockk<PlatformMembershipService>(relaxed = true).also { every { it.hasActiveMembership(any()) } returns false },
            pushNotificationService,
            autoTopUpService, webhookDeliveryService,
        )
        val riderWithLocation = Rider(id = "rider_1", userId = "rider_user_1", accountId = "account_rider", currentLatitude = -1.95, currentLongitude = 30.06, locationUpdatedAt = java.time.Instant.parse("2026-07-19T12:00:00Z"))
        val restaurant = Merchant(id = "restaurant_1", ownerUserId = "owner_1", accountId = "account_restaurant", businessName = "Test Spot", status = MerchantStatus.ACTIVE)

        When("the order is RIDER_ASSIGNED and the rider has shared a real location") {
            val order = EatsOrder(
                id = "eats_order_1", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "addr",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_1", riderId = "rider_1", status = EatsOrderStatus.RIDER_ASSIGNED,
            )
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(order)
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            every { riderRepository.findById("rider_1") } returns Optional.of(riderWithLocation)

            val location = service.getRiderLocation("buyer_1", "eats_order_1")

            Then("it returns the real live coordinates") {
                location?.latitude shouldBe -1.95
                location?.longitude shouldBe 30.06
            }
        }

        When("the order hasn't been claimed by a rider yet") {
            val order = EatsOrder(
                id = "eats_order_2", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "addr",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_2", status = EatsOrderStatus.READY_FOR_PICKUP,
            )
            every { eatsOrderRepository.findById("eats_order_2") } returns Optional.of(order)
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)

            val location = service.getRiderLocation("buyer_1", "eats_order_2")

            Then("it honestly returns null -- there's no rider to show yet") {
                location shouldBe null
            }
        }

        When("the order has already been DELIVERED") {
            val order = EatsOrder(
                id = "eats_order_3", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "addr",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_3", riderId = "rider_1", status = EatsOrderStatus.DELIVERED,
            )
            every { eatsOrderRepository.findById("eats_order_3") } returns Optional.of(order)
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            every { riderRepository.findById("rider_1") } returns Optional.of(riderWithLocation)

            val location = service.getRiderLocation("buyer_1", "eats_order_3")

            Then("it honestly returns null -- a stale post-delivery position isn't shown") {
                location shouldBe null
            }
        }

        When("the assigned rider hasn't shared a real location yet") {
            val riderWithNoLocation = Rider(id = "rider_2", userId = "rider_user_2", accountId = "account_rider_2")
            val order = EatsOrder(
                id = "eats_order_4", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "addr",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_4", riderId = "rider_2", status = EatsOrderStatus.PICKED_UP,
            )
            every { eatsOrderRepository.findById("eats_order_4") } returns Optional.of(order)
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            every { riderRepository.findById("rider_2") } returns Optional.of(riderWithNoLocation)

            val location = service.getRiderLocation("buyer_1", "eats_order_4")

            Then("it honestly returns null rather than a fabricated position") {
                location shouldBe null
            }
        }

        When("a real stranger tries to check someone else's delivery") {
            val order = EatsOrder(
                id = "eats_order_1", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "addr",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_1", riderId = "rider_1", status = EatsOrderStatus.RIDER_ASSIGNED,
            )
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(order)
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            every { riderRepository.findById("rider_1") } returns Optional.of(riderWithLocation)

            Then("it throws EatsOrderNotFoundException, not a 403 that would confirm the order exists") {
                try {
                    service.getRiderLocation("stranger", "eats_order_1")
                    error("expected EatsOrderNotFoundException")
                } catch (e: EatsOrderNotFoundException) {
                    // expected
                }
            }
        }
    }

    Given("a real buyer searching for a real delivery address") {
        val merchantRepository = mockk<MerchantRepository>()
        // Real Eats order-status webhook (2026-08-30) -- notifyRestaurantWebhook's own
        // merchantRepository.findById lookup needs a safe default across every test in
        // this file, most of which never cared about that call before this feature
        // existed. A more specific findById stub registered later in a given When block
        // still correctly takes precedence for its own exact id (MockK checks
        // most-recently-registered stubs first) -- this is purely a fallback for every
        // other id/test that never stubbed it at all.
        every { merchantRepository.findById(any<String>()) } returns Optional.empty()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val riderRepository = mockk<RiderRepository>()
        val eatsOrderRepository = mockk<EatsOrderRepository>()
        // Real 단건배달 (single-order delivery) guarantee (2026-07-26) -- default "no
        // rider is currently busy" stub for every pre-existing test in this suite, none
        // of which predate or specifically exercise this new behavior. See
        // EatsOrderService.claimDelivery's own doc comment.
        every { eatsOrderRepository.existsByRiderIdAndStatusIn(any(), any()) } returns false
        every { eatsOrderRepository.findDistinctRiderIdsByStatusIn(any()) } returns emptyList()
        val eatsOrderItemRepository = mockk<EatsOrderItemRepository>(relaxed = true)
        // Real menu-options resolution (2026-07-21) -- relaxed + explicit empty-list
        // stubs, matching this suite's own existing "no option groups defined" default
        // for every pre-existing test (see MenuOptionGroup.kt's own doc comment: purely
        // additive, a product with zero groups behaves exactly as before).
        val menuOptionGroupRepository = mockk<MenuOptionGroupRepository>(relaxed = true)
        every { menuOptionGroupRepository.findByProductIdInOrderByDisplayOrderAsc(any()) } returns emptyList()
        val menuOptionChoiceRepository = mockk<MenuOptionChoiceRepository>(relaxed = true)
        every { menuOptionChoiceRepository.findByGroupIdInOrderByDisplayOrderAsc(any()) } returns emptyList()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>()
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>()
        val rateLimiter = mockk<RateLimiter>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        // Real buyer order-status notifications (2026-07-20) -- explicit stub, same
        // known "relaxed mockk can't correctly infer JpaRepository's generic save()
        // signature" gotcha this project's own tests already document repeatedly
        // (MerchantServiceTest/OrderServiceTest/EatsOrderServiceTest/GroupMessagingServiceTest, etc).
        every { notificationRepository.save(any()) } answers { firstArg() }
        val autoTopUpService = mockk<rw.itunda.account.AutoTopUpService>(relaxed = true)
        every { autoTopUpService.ensureSufficientPayBalance(any(), any(), any()) } answers { secondArg() }
        val webhookDeliveryService = mockk<rw.itunda.merchant.WebhookDeliveryService>(relaxed = true)
        val service = EatsOrderService(
            merchantRepository, merchantProductRepository, riderRepository, eatsOrderRepository,
            eatsOrderItemRepository, menuOptionGroupRepository, menuOptionChoiceRepository, accountRepository, ledgerService, transactionRepository, fraudRuleEngine,
            ledgerEntryRepository, osrmRoutingClient, nominatimGeocodingClient, rateLimiter, notificationRepository,
            // Real Baemin Club-style free-delivery membership (2026-07-26) -- relaxed,
            // no active member by default, matching this suite's own "no pre-existing
            // test predates this feature" convention already used for the single-order
            // delivery guard's own default stubs.
            mockk<EatsMembershipService>(relaxed = true).also { every { it.hasActiveMembership(any()) } returns false },
            // Real Coupang Wow-style unconditional waiver (2026-07-31) -- same
            // relaxed, no-active-member-by-default convention as EatsMembershipService above.
            mockk<PlatformMembershipService>(relaxed = true).also { every { it.hasActiveMembership(any()) } returns false },
            pushNotificationService,
            autoTopUpService, webhookDeliveryService,
        )

        When("a real query matches real Nominatim suggestions") {
            every { rateLimiter.checkLimit("eats:geocode-search:buyer_1", limit = 60, window = Duration.ofMinutes(1)) } just Runs
            every { nominatimGeocodingClient.search("Kigali Air") } returns listOf(
                GeocodeSuggestion("Kigali International Airport, Rwanda", -1.9686, 30.1394),
            )

            val results = service.searchDeliveryAddress("buyer_1", "Kigali Air")

            Then("it real-rate-limits per buyer and returns the real suggestions") {
                results shouldBe listOf(GeocodeSuggestion("Kigali International Airport, Rwanda", -1.9686, 30.1394))
                verify(exactly = 1) { rateLimiter.checkLimit("eats:geocode-search:buyer_1", limit = 60, window = Duration.ofMinutes(1)) }
            }
        }

        When("the real per-buyer rate limit is exceeded") {
            every { rateLimiter.checkLimit("eats:geocode-search:buyer_1", limit = 60, window = Duration.ofMinutes(1)) } throws
                RateLimitExceededException("Too many requests")

            Then("it throws RateLimitExceededException before ever calling Nominatim") {
                try {
                    service.searchDeliveryAddress("buyer_1", "Kigali Air")
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    // expected
                }
                verify(exactly = 0) { nominatimGeocodingClient.search(any()) }
            }
        }
    }

    // Real bug found live (2026-09-06, Eats product-completeness pass): placeOrder
    // had shipped with zero rate limiting despite every other real content/order-
    // creation endpoint in this module already having one. See placeOrder's own doc
    // comment for the full account.
    Given("a real buyer who has already hit the real place-order rate limit") {
        val merchantRepository = mockk<MerchantRepository>()
        every { merchantRepository.findById(any<String>()) } returns Optional.empty()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val riderRepository = mockk<RiderRepository>()
        val eatsOrderRepository = mockk<EatsOrderRepository>()
        every { eatsOrderRepository.existsByRiderIdAndStatusIn(any(), any()) } returns false
        every { eatsOrderRepository.findDistinctRiderIdsByStatusIn(any()) } returns emptyList()
        val eatsOrderItemRepository = mockk<EatsOrderItemRepository>(relaxed = true)
        val menuOptionGroupRepository = mockk<MenuOptionGroupRepository>(relaxed = true)
        every { menuOptionGroupRepository.findByProductIdInOrderByDisplayOrderAsc(any()) } returns emptyList()
        val menuOptionChoiceRepository = mockk<MenuOptionChoiceRepository>(relaxed = true)
        every { menuOptionChoiceRepository.findByGroupIdInOrderByDisplayOrderAsc(any()) } returns emptyList()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>()
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>()
        val rateLimiter = mockk<RateLimiter>()
        every { rateLimiter.checkLimit("eats:place-order:buyer_1", limit = 20, window = Duration.ofHours(1)) } throws
            RateLimitExceededException("Too many requests")
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val autoTopUpService = mockk<rw.itunda.account.AutoTopUpService>(relaxed = true)
        val webhookDeliveryService = mockk<rw.itunda.merchant.WebhookDeliveryService>(relaxed = true)
        val service = EatsOrderService(
            merchantRepository, merchantProductRepository, riderRepository, eatsOrderRepository,
            eatsOrderItemRepository, menuOptionGroupRepository, menuOptionChoiceRepository, accountRepository, ledgerService, transactionRepository, fraudRuleEngine,
            ledgerEntryRepository, osrmRoutingClient, nominatimGeocodingClient, rateLimiter, notificationRepository,
            mockk<EatsMembershipService>(relaxed = true).also { every { it.hasActiveMembership(any()) } returns false },
            mockk<PlatformMembershipService>(relaxed = true).also { every { it.hasActiveMembership(any()) } returns false },
            pushNotificationService, autoTopUpService, webhookDeliveryService,
        )

        When("they try to place another order") {
            Then("it real-429s before ever looking up the restaurant or touching the repository") {
                try {
                    service.placeOrder("buyer_1", "restaurant_1", listOf(EatsOrderItemRequest("item_1", 2)), "KG 9 Ave")
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    // expected
                }
                verify(exactly = 0) { merchantRepository.findById("restaurant_1") }
                verify(exactly = 0) { eatsOrderRepository.save(any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
