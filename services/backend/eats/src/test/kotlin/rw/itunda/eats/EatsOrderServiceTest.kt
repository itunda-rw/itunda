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
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.EatsOrder
import rw.itunda.core.domain.EatsOrderStatus
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.LedgerEntry
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantProduct
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.Rider
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.geo.GeocodeResult
import rw.itunda.core.geo.GeocodeSuggestion
import rw.itunda.core.geo.NominatimGeocodingClient
import rw.itunda.core.geo.OsrmRoutingClient
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.EatsOrderItemRepository
import rw.itunda.core.repository.EatsOrderRepository
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.RiderRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
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
        val osrmRoutingClient = mockk<OsrmRoutingClient>()
        every { osrmRoutingClient.routeDistanceKm(any(), any(), any(), any()) } returns null
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>()
        every { nominatimGeocodingClient.geocode(any()) } returns null
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        // Real buyer order-status notifications (2026-07-20) -- explicit stub, same
        // known "relaxed mockk can't correctly infer JpaRepository's generic save()
        // signature" gotcha this project's own tests already document repeatedly
        // (MerchantServiceTest/OrderServiceTest/EatsOrderServiceTest/GroupMessagingServiceTest, etc).
        every { notificationRepository.save(any()) } answers { firstArg() }
        val service = EatsOrderService(
            merchantRepository, merchantProductRepository, riderRepository, eatsOrderRepository,
            eatsOrderItemRepository, walletRepository, ledgerService, transactionRepository, fraudRuleEngine,
            ledgerEntryRepository, osrmRoutingClient, nominatimGeocodingClient, rateLimiter, notificationRepository,
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

        When("a real buyer places an order with real delivery notes") {
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            every { walletRepository.findById("wallet_restaurant") } returns Optional.of(restaurantWallet)
            every { walletRepository.findByUserIdAndType("buyer_1", WalletType.MAIN) } returns buyerWallet
            every { merchantProductRepository.findById("item_1") } returns Optional.of(menuItem)
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

            Then("it throws InvalidEatsDeliveryNotesException before even resolving the restaurant's wallet") {
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

        When("a real buyer places a real order with real delivery coordinates and the restaurant has a real location") {
            val restaurantWithLocation = Merchant(
                id = "restaurant_1", ownerUserId = "owner_1", walletId = "wallet_restaurant", businessName = "Kigali Grill",
                status = MerchantStatus.ACTIVE, latitude = -1.9441, longitude = 30.0619,
            )
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurantWithLocation)
            every { walletRepository.findById("wallet_restaurant") } returns Optional.of(restaurantWallet)
            every { walletRepository.findByUserIdAndType("buyer_1", WalletType.MAIN) } returns buyerWallet
            every { merchantProductRepository.findById("item_1") } returns Optional.of(menuItem)
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
                detail.order.totalAmount shouldBe BigDecimal("7890.00")

                val holdingLeg = legsSlot.captured.first { it.accountId == "eats_delivery_holding" }
                holdingLeg.amount shouldBe BigDecimal("1890.00")
            }
        }

        When("itunda's own self-hosted OSRM has a real route between the restaurant and the buyer") {
            val restaurantWithLocation = Merchant(
                id = "restaurant_1", ownerUserId = "owner_1", walletId = "wallet_restaurant", businessName = "Kigali Grill",
                status = MerchantStatus.ACTIVE, latitude = -1.9441, longitude = 30.0619,
            )
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurantWithLocation)
            every { walletRepository.findById("wallet_restaurant") } returns Optional.of(restaurantWallet)
            every { walletRepository.findByUserIdAndType("buyer_1", WalletType.MAIN) } returns buyerWallet
            every { merchantProductRepository.findById("item_1") } returns Optional.of(menuItem)
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
                id = "restaurant_1", ownerUserId = "owner_1", walletId = "wallet_restaurant", businessName = "Kigali Grill",
                status = MerchantStatus.ACTIVE, latitude = -1.9441, longitude = 30.0619,
            )
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurantWithLocation)
            every { walletRepository.findById("wallet_restaurant") } returns Optional.of(restaurantWallet)
            every { walletRepository.findByUserIdAndType("buyer_1", WalletType.MAIN) } returns buyerWallet
            every { merchantProductRepository.findById("item_1") } returns Optional.of(menuItem)
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
            every { walletRepository.findById("wallet_restaurant") } returns Optional.of(restaurantWallet)
            every { walletRepository.findByUserIdAndType("buyer_1", WalletType.MAIN) } returns buyerWallet
            every { merchantProductRepository.findById("item_1") } returns Optional.of(menuItem)
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
                id = "restaurant_1", ownerUserId = "owner_1", walletId = "wallet_restaurant", businessName = "Kigali Grill",
                status = MerchantStatus.ACTIVE, latitude = -1.9441, longitude = 30.0619,
            )
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurantWithLocation)
            every { walletRepository.findById("wallet_restaurant") } returns Optional.of(restaurantWallet)
            every { walletRepository.findByUserIdAndType("buyer_1", WalletType.MAIN) } returns buyerWallet
            every { merchantProductRepository.findById("item_1") } returns Optional.of(menuItem)
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
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val riderRepository = mockk<RiderRepository>()
        val eatsOrderRepository = mockk<EatsOrderRepository>()
        val eatsOrderItemRepository = mockk<EatsOrderItemRepository>(relaxed = true)
        val walletRepository = mockk<WalletRepository>()
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
        // Real buyer order-status notifications (2026-07-20) -- explicit stub, same
        // known "relaxed mockk can't correctly infer JpaRepository's generic save()
        // signature" gotcha this project's own tests already document repeatedly
        // (MerchantServiceTest/OrderServiceTest/EatsOrderServiceTest/GroupMessagingServiceTest, etc).
        every { notificationRepository.save(any()) } answers { firstArg() }
        val service = EatsOrderService(
            merchantRepository, merchantProductRepository, riderRepository, eatsOrderRepository,
            eatsOrderItemRepository, walletRepository, ledgerService, transactionRepository, fraudRuleEngine,
            ledgerEntryRepository, osrmRoutingClient, nominatimGeocodingClient, rateLimiter, notificationRepository,
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
            val locatedRestaurant = Merchant(id = "restaurant_1", ownerUserId = "owner_1", walletId = "wallet_restaurant", businessName = "Kigali Grill", status = MerchantStatus.ACTIVE, latitude = -1.9536, longitude = 30.0605)
            val preparingOrder = EatsOrder(
                id = "eats_order_2", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "addr",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_2", status = EatsOrderStatus.PREPARING,
            )
            val closeRider = Rider(id = "rider_close", userId = "rider_user_close", walletId = "wallet_close", available = true, currentLatitude = -1.9536, currentLongitude = 30.0620)
            val farRider = Rider(id = "rider_far", userId = "rider_user_far", walletId = "wallet_far", available = true, currentLatitude = -2.5967, currentLongitude = 29.7392)
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

        When("the real restaurant marks an order READY_FOR_PICKUP but has no real online riders at all") {
            val locatedRestaurant = Merchant(id = "restaurant_1", ownerUserId = "owner_1", walletId = "wallet_restaurant", businessName = "Kigali Grill", status = MerchantStatus.ACTIVE, latitude = -1.9536, longitude = 30.0605)
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

            Then("it real-notifies the buyer, since the RESTAURANT was the one who cancelled") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "buyer_1" && it.type == "EATS_ORDER_UPDATE" }) }
            }
        }

        When("the real buyer cancels their own real PLACED order") {
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            val originalEntries = listOf(
                LedgerEntry(id = "le_1", transactionId = "ledgertxn_1", accountId = "wallet_buyer", accountType = LedgerAccountType.WALLET, direction = LedgerDirection.DEBIT, amount = BigDecimal("7500"), currency = "RWF", balanceAfter = BigDecimal("92500"), memo = "Eats order - Kigali Grill"),
                LedgerEntry(id = "le_2", transactionId = "ledgertxn_1", accountId = "wallet_restaurant", accountType = LedgerAccountType.WALLET, direction = LedgerDirection.CREDIT, amount = BigDecimal("5910.00"), currency = "RWF", balanceAfter = BigDecimal("5910.00"), memo = "Eats order collection - Kigali Grill"),
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
        val osrmRoutingClient = mockk<OsrmRoutingClient>()
        every { osrmRoutingClient.routeDistanceKm(any(), any(), any(), any()) } returns null
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>()
        every { nominatimGeocodingClient.geocode(any()) } returns null
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        // Real buyer order-status notifications (2026-07-20) -- explicit stub, same
        // known "relaxed mockk can't correctly infer JpaRepository's generic save()
        // signature" gotcha this project's own tests already document repeatedly
        // (MerchantServiceTest/OrderServiceTest/EatsOrderServiceTest/GroupMessagingServiceTest, etc).
        every { notificationRepository.save(any()) } answers { firstArg() }
        val service = EatsOrderService(
            merchantRepository, merchantProductRepository, riderRepository, eatsOrderRepository,
            eatsOrderItemRepository, walletRepository, ledgerService, transactionRepository, fraudRuleEngine,
            ledgerEntryRepository, osrmRoutingClient, nominatimGeocodingClient, rateLimiter, notificationRepository,
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

            Then("it real-notifies the buyer that a rider was assigned") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "buyer_1" && it.type == "EATS_ORDER_UPDATE" }) }
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
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val riderRepository = mockk<RiderRepository>()
        val eatsOrderRepository = mockk<EatsOrderRepository>()
        val eatsOrderItemRepository = mockk<EatsOrderItemRepository>(relaxed = true)
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>()
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        // Real buyer order-status notifications (2026-07-20) -- explicit stub, same
        // known "relaxed mockk can't correctly infer JpaRepository's generic save()
        // signature" gotcha this project's own tests already document repeatedly
        // (MerchantServiceTest/OrderServiceTest/EatsOrderServiceTest/GroupMessagingServiceTest, etc).
        every { notificationRepository.save(any()) } answers { firstArg() }
        val service = EatsOrderService(
            merchantRepository, merchantProductRepository, riderRepository, eatsOrderRepository,
            eatsOrderItemRepository, walletRepository, ledgerService, transactionRepository, fraudRuleEngine,
            ledgerEntryRepository, osrmRoutingClient, nominatimGeocodingClient, rateLimiter, notificationRepository,
        )
        val restaurant = Merchant(id = "restaurant_1", ownerUserId = "owner_1", walletId = "wallet_restaurant", businessName = "Kigali Grill", status = MerchantStatus.ACTIVE, latitude = -1.9536, longitude = 30.0605)
        val rider = Rider(id = "rider_1", userId = "rider_user_1", walletId = "wallet_rider", available = true)

        When("they real-decline their real active offer") {
            val offeredOrder = EatsOrder(
                id = "eats_order_9", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "addr",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_9", status = EatsOrderStatus.READY_FOR_PICKUP,
                offeredRiderId = "rider_1", offerExpiresAt = Instant.now().plusSeconds(60),
            )
            val nextRider = Rider(id = "rider_next", userId = "rider_user_next", walletId = "wallet_next", available = true, currentLatitude = -1.9536, currentLongitude = 30.0605)
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
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val riderRepository = mockk<RiderRepository>()
        val eatsOrderRepository = mockk<EatsOrderRepository>()
        val eatsOrderItemRepository = mockk<EatsOrderItemRepository>(relaxed = true)
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>()
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        // Real buyer order-status notifications (2026-07-20) -- explicit stub, same
        // known "relaxed mockk can't correctly infer JpaRepository's generic save()
        // signature" gotcha this project's own tests already document repeatedly
        // (MerchantServiceTest/OrderServiceTest/EatsOrderServiceTest/GroupMessagingServiceTest, etc).
        every { notificationRepository.save(any()) } answers { firstArg() }
        val service = EatsOrderService(
            merchantRepository, merchantProductRepository, riderRepository, eatsOrderRepository,
            eatsOrderItemRepository, walletRepository, ledgerService, transactionRepository, fraudRuleEngine,
            ledgerEntryRepository, osrmRoutingClient, nominatimGeocodingClient, rateLimiter, notificationRepository,
        )
        val restaurant = Merchant(id = "restaurant_1", ownerUserId = "owner_1", walletId = "wallet_restaurant", businessName = "Kigali Grill", status = MerchantStatus.ACTIVE, latitude = -1.9536, longitude = 30.0605)

        When("the real scheduler finds and reassigns one") {
            val expiredOrder = EatsOrder(
                id = "eats_order_11", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "addr",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_11", status = EatsOrderStatus.READY_FOR_PICKUP,
                offeredRiderId = "rider_timed_out", offerExpiresAt = Instant.now().minusSeconds(5),
            )
            val timedOutRider = Rider(id = "rider_timed_out", userId = "rider_user_timed_out", walletId = "wallet_timedout")
            val nextRider = Rider(id = "rider_next", userId = "rider_user_next", walletId = "wallet_next", available = true, currentLatitude = -1.9536, currentLongitude = 30.0605)
            every { eatsOrderRepository.findByOfferExpiresAtBeforeAndRiderIdIsNull(any()) } returns listOf(expiredOrder)
            every { merchantRepository.findAllById(listOf("restaurant_1")) } returns listOf(restaurant)
            every { riderRepository.findAllById(listOf("rider_timed_out")) } returns listOf(timedOutRider)
            every { eatsOrderRepository.save(any()) } answers { firstArg() }
            every { riderRepository.findByAvailableTrueAndCurrentLatitudeIsNotNullAndCurrentLongitudeIsNotNull() } returns listOf(nextRider)

            val expired = service.getExpiredOffers()
            service.reassignExpiredOffer(expired.first())

            Then("it real-excludes the timed-out rider and real-offers to the next real candidate") {
                expired shouldBe listOf(expiredOrder)
                expiredOrder.excludedRiderUserIds shouldBe "rider_user_timed_out"
                expiredOrder.offeredRiderId shouldBe "rider_next"
                verify { notificationRepository.save(match<Notification> { it.type == "DELIVERY_OFFER" && it.userId == "rider_user_next" }) }
            }
        }

        When("the real scheduler reassigns two real expired offers for two different real restaurants in one tick") {
            val restaurant2 = Merchant(id = "restaurant_2", ownerUserId = "owner_2", walletId = "wallet_restaurant_2", businessName = "Huye Grill", status = MerchantStatus.ACTIVE, latitude = -2.5967, longitude = 29.7392)
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
            val timedOutRider1 = Rider(id = "rider_timed_out_1", userId = "rider_user_timed_out_1", walletId = "wallet_to1")
            val timedOutRider2 = Rider(id = "rider_timed_out_2", userId = "rider_user_timed_out_2", walletId = "wallet_to2")
            val nextRider = Rider(id = "rider_next", userId = "rider_user_next", walletId = "wallet_next", available = true, currentLatitude = -1.9536, currentLongitude = 30.0605)
            every { merchantRepository.findAllById(listOf("restaurant_1", "restaurant_2")) } returns listOf(restaurant, restaurant2)
            every { riderRepository.findAllById(listOf("rider_timed_out_1", "rider_timed_out_2")) } returns listOf(timedOutRider1, timedOutRider2)
            every { eatsOrderRepository.save(any()) } answers { firstArg() }
            every { riderRepository.findByAvailableTrueAndCurrentLatitudeIsNotNullAndCurrentLongitudeIsNotNull() } returns listOf(nextRider)

            service.reassignExpiredOffers(listOf(expiredOrder1, expiredOrder2))

            Then("the real candidate pool is fetched exactly once for the whole batch, not once per order") {
                verify(exactly = 1) { riderRepository.findByAvailableTrueAndCurrentLatitudeIsNotNullAndCurrentLongitudeIsNotNull() }
                verify(exactly = 1) { merchantRepository.findAllById(any<List<String>>()) }
                verify(exactly = 1) { riderRepository.findAllById(any<List<String>>()) }
                expiredOrder1.excludedRiderUserIds shouldBe "rider_user_timed_out_1"
                expiredOrder2.excludedRiderUserIds shouldBe "rider_user_timed_out_2"
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
        val osrmRoutingClient = mockk<OsrmRoutingClient>()
        every { osrmRoutingClient.routeDistanceKm(any(), any(), any(), any()) } returns null
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>()
        every { nominatimGeocodingClient.geocode(any()) } returns null
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        // Real buyer order-status notifications (2026-07-20) -- explicit stub, same
        // known "relaxed mockk can't correctly infer JpaRepository's generic save()
        // signature" gotcha this project's own tests already document repeatedly
        // (MerchantServiceTest/OrderServiceTest/EatsOrderServiceTest/GroupMessagingServiceTest, etc).
        every { notificationRepository.save(any()) } answers { firstArg() }
        val service = EatsOrderService(
            merchantRepository, merchantProductRepository, riderRepository, eatsOrderRepository,
            eatsOrderItemRepository, walletRepository, ledgerService, transactionRepository, fraudRuleEngine,
            ledgerEntryRepository, osrmRoutingClient, nominatimGeocodingClient, rateLimiter, notificationRepository,
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

            Then("it real-notifies the buyer that their order was delivered") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "buyer_1" && it.type == "EATS_ORDER_UPDATE" }) }
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

    Given("a real buyer checking their rider's real live location") {
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
        val osrmRoutingClient = mockk<OsrmRoutingClient>()
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        // Real buyer order-status notifications (2026-07-20) -- explicit stub, same
        // known "relaxed mockk can't correctly infer JpaRepository's generic save()
        // signature" gotcha this project's own tests already document repeatedly
        // (MerchantServiceTest/OrderServiceTest/EatsOrderServiceTest/GroupMessagingServiceTest, etc).
        every { notificationRepository.save(any()) } answers { firstArg() }
        val service = EatsOrderService(
            merchantRepository, merchantProductRepository, riderRepository, eatsOrderRepository,
            eatsOrderItemRepository, walletRepository, ledgerService, transactionRepository, fraudRuleEngine,
            ledgerEntryRepository, osrmRoutingClient, nominatimGeocodingClient, rateLimiter, notificationRepository,
        )
        val riderWithLocation = Rider(id = "rider_1", userId = "rider_user_1", walletId = "wallet_rider", currentLatitude = -1.95, currentLongitude = 30.06, locationUpdatedAt = java.time.Instant.parse("2026-07-19T12:00:00Z"))
        val restaurant = Merchant(id = "restaurant_1", ownerUserId = "owner_1", walletId = "wallet_restaurant", businessName = "Test Spot", status = MerchantStatus.ACTIVE)

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
            val riderWithNoLocation = Rider(id = "rider_2", userId = "rider_user_2", walletId = "wallet_rider_2")
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
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val riderRepository = mockk<RiderRepository>()
        val eatsOrderRepository = mockk<EatsOrderRepository>()
        val eatsOrderItemRepository = mockk<EatsOrderItemRepository>(relaxed = true)
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>()
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>()
        val rateLimiter = mockk<RateLimiter>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        // Real buyer order-status notifications (2026-07-20) -- explicit stub, same
        // known "relaxed mockk can't correctly infer JpaRepository's generic save()
        // signature" gotcha this project's own tests already document repeatedly
        // (MerchantServiceTest/OrderServiceTest/EatsOrderServiceTest/GroupMessagingServiceTest, etc).
        every { notificationRepository.save(any()) } answers { firstArg() }
        val service = EatsOrderService(
            merchantRepository, merchantProductRepository, riderRepository, eatsOrderRepository,
            eatsOrderItemRepository, walletRepository, ledgerService, transactionRepository, fraudRuleEngine,
            ledgerEntryRepository, osrmRoutingClient, nominatimGeocodingClient, rateLimiter, notificationRepository,
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

    Given("a real rider browsing available deliveries") {
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
        val osrmRoutingClient = mockk<OsrmRoutingClient>()
        // Not configured for most cases in this block -- exercises the honest Haversine
        // fallback, same as OSRM being unavailable in production. A dedicated case below
        // separately proves the real road-distance path when OSRM IS configured.
        every { osrmRoutingClient.isConfigured } returns false
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        // Real buyer order-status notifications (2026-07-20) -- explicit stub, same
        // known "relaxed mockk can't correctly infer JpaRepository's generic save()
        // signature" gotcha this project's own tests already document repeatedly
        // (MerchantServiceTest/OrderServiceTest/EatsOrderServiceTest/GroupMessagingServiceTest, etc).
        every { notificationRepository.save(any()) } answers { firstArg() }
        val service = EatsOrderService(
            merchantRepository, merchantProductRepository, riderRepository, eatsOrderRepository,
            eatsOrderItemRepository, walletRepository, ledgerService, transactionRepository, fraudRuleEngine,
            ledgerEntryRepository, osrmRoutingClient, nominatimGeocodingClient, rateLimiter, notificationRepository,
        )

        // Kigali city center vs. Huye (real Rwandan towns, ~135km apart) -- a rider
        // standing in Kigali should see the Kigali restaurant's order first.
        val nearRestaurant = Merchant(id = "restaurant_near", ownerUserId = "owner_near", walletId = "wallet_near", businessName = "Kigali Grill", status = MerchantStatus.ACTIVE, latitude = -1.9536, longitude = 30.0605)
        val farRestaurant = Merchant(id = "restaurant_far", ownerUserId = "owner_far", walletId = "wallet_far", businessName = "Huye Diner", status = MerchantStatus.ACTIVE, latitude = -2.5967, longitude = 29.7392)
        val orderFromFarRestaurant = EatsOrder(
            id = "order_far", buyerId = "buyer_1", restaurantId = "restaurant_far", deliveryAddress = "addr",
            itemsSubtotal = BigDecimal("5000"), deliveryFee = BigDecimal("1000"), platformFee = BigDecimal("75"),
            totalAmount = BigDecimal("6075"), transactionId = "ledgertxn_far", status = EatsOrderStatus.READY_FOR_PICKUP,
        )
        val orderFromNearRestaurant = EatsOrder(
            id = "order_near", buyerId = "buyer_2", restaurantId = "restaurant_near", deliveryAddress = "addr",
            itemsSubtotal = BigDecimal("5000"), deliveryFee = BigDecimal("1000"), platformFee = BigDecimal("75"),
            totalAmount = BigDecimal("6075"), transactionId = "ledgertxn_near", status = EatsOrderStatus.READY_FOR_PICKUP,
        )

        When("the rider has shared a real current location") {
            val riderAtKigaliCenter = Rider(id = "rider_1", userId = "rider_user_1", walletId = "wallet_rider", currentLatitude = -1.9441, currentLongitude = 30.0619)
            every { riderRepository.findByUserId("rider_user_1") } returns riderAtKigaliCenter
            every { eatsOrderRepository.findByStatusAndRiderIdIsNullOrderByCreatedAtAsc(EatsOrderStatus.READY_FOR_PICKUP, Pageable.unpaged()) } returns
                org.springframework.data.domain.PageImpl(listOf(orderFromFarRestaurant, orderFromNearRestaurant))
            every { merchantRepository.findAllById(listOf("restaurant_far", "restaurant_near")) } returns listOf(farRestaurant, nearRestaurant)

            val page = service.getAvailableDeliveries("rider_user_1", PageRequest.of(0, 20))

            Then("it real-ranks the nearer restaurant's order first, not createdAt order") {
                page.content.map { it.id } shouldBe listOf("order_near", "order_far")
            }
        }

        When("the rider has NOT shared a real current location yet") {
            val riderWithNoLocation = Rider(id = "rider_2", userId = "rider_user_2", walletId = "wallet_rider_2")
            every { riderRepository.findByUserId("rider_user_2") } returns riderWithNoLocation
            val fallbackPage = org.springframework.data.domain.PageImpl(listOf(orderFromFarRestaurant, orderFromNearRestaurant))
            every { eatsOrderRepository.findByStatusAndRiderIdIsNullOrderByCreatedAtAsc(EatsOrderStatus.READY_FOR_PICKUP, any()) } returns fallbackPage

            val page = service.getAvailableDeliveries("rider_user_2", PageRequest.of(0, 20))

            Then("it honestly falls back to createdAt order -- never a fabricated distance") {
                page.content.map { it.id } shouldBe listOf("order_far", "order_near")
            }
        }

        When("OSRM is configured and reports a real road distance that flips the Haversine ranking") {
            // Real road distance can exceed straight-line -- here the "near" restaurant's
            // actual road distance is longer than the "far" one's, proving the final sort
            // genuinely comes from OSRM's real /table response, not just Haversine.
            every { osrmRoutingClient.isConfigured } returns true
            val riderAtKigaliCenter = Rider(id = "rider_3", userId = "rider_user_3", walletId = "wallet_rider_3", currentLatitude = -1.9441, currentLongitude = 30.0619)
            every { riderRepository.findByUserId("rider_user_3") } returns riderAtKigaliCenter
            every { eatsOrderRepository.findByStatusAndRiderIdIsNullOrderByCreatedAtAsc(EatsOrderStatus.READY_FOR_PICKUP, Pageable.unpaged()) } returns
                org.springframework.data.domain.PageImpl(listOf(orderFromFarRestaurant, orderFromNearRestaurant))
            every { merchantRepository.findAllById(listOf("restaurant_far", "restaurant_near")) } returns listOf(farRestaurant, nearRestaurant)
            // routeDistancesKm's destinations line up positionally with the in-Rwanda
            // candidate order the service builds them in (far, then near, matching
            // eatsOrderRepository's own returned order) -- real road distance flips it:
            // "near" is actually 200km by road, "far" is only 10km.
            every { osrmRoutingClient.routeDistancesKm(-1.9441, 30.0619, listOf(-2.5967 to 29.7392, -1.9536 to 30.0605)) } returns listOf(10.0, 200.0)

            val page = service.getAvailableDeliveries("rider_user_3", PageRequest.of(0, 20))

            Then("it real-ranks by the real road distance, not the straight-line one") {
                page.content.map { it.id } shouldBe listOf("order_far", "order_near")
            }
        }

        When("someone who never registered as a rider tries to browse") {
            every { riderRepository.findByUserId("stranger") } returns null

            Then("it throws RiderNotRegisteredException") {
                try {
                    service.getAvailableDeliveries("stranger", PageRequest.of(0, 20))
                    error("expected RiderNotRegisteredException")
                } catch (e: RiderNotRegisteredException) {
                    // expected
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
