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

// Real fix (2026-08-26): split out of EatsOrderServiceTest.kt once that file grew
// past its file-size-lint baseline. Both Given blocks were already fully self-
// contained (own fresh mocks + own EatsOrderService instance, no shared spec-level
// state) -- a clean, zero-risk test-file split by concern.
class EatsOrderDeliveryBrowseTest : BehaviorSpec({

    fun account(id: String, userId: String) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = AccountType.MAIN, balance = BigDecimal("100000"), availableBalance = BigDecimal("100000"),
    )

    Given("a real rider browsing available deliveries") {
        val merchantRepository = mockk<MerchantRepository>()
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
        // Not configured for most cases in this block -- exercises the honest Haversine
        // fallback, same as OSRM being unavailable in production. A dedicated case below
        // separately proves the real road-distance path when OSRM IS configured.
        every { osrmRoutingClient.isConfigured } returns false
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
            autoTopUpService,
        )

        // Kigali city center vs. Huye (real Rwandan towns, ~135km apart) -- a rider
        // standing in Kigali should see the Kigali restaurant's order first.
        val nearRestaurant = Merchant(id = "restaurant_near", ownerUserId = "owner_near", accountId = "account_near", businessName = "Kigali Grill", status = MerchantStatus.ACTIVE, latitude = -1.9536, longitude = 30.0605)
        val farRestaurant = Merchant(id = "restaurant_far", ownerUserId = "owner_far", accountId = "account_far", businessName = "Huye Diner", status = MerchantStatus.ACTIVE, latitude = -2.5967, longitude = 29.7392)
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
            val riderAtKigaliCenter = Rider(id = "rider_1", userId = "rider_user_1", accountId = "account_rider", currentLatitude = -1.9441, currentLongitude = 30.0619)
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
            val riderWithNoLocation = Rider(id = "rider_2", userId = "rider_user_2", accountId = "account_rider_2")
            every { riderRepository.findByUserId("rider_user_2") } returns riderWithNoLocation
            val fallbackPage = org.springframework.data.domain.PageImpl(listOf(orderFromFarRestaurant, orderFromNearRestaurant))
            every { eatsOrderRepository.findByStatusAndRiderIdIsNullOrderByCreatedAtAsc(EatsOrderStatus.READY_FOR_PICKUP, any()) } returns fallbackPage

            val page = service.getAvailableDeliveries("rider_user_2", PageRequest.of(0, 20))

            Then("it honestly falls back to createdAt order -- never a fabricated distance") {
                page.content.map { it.id } shouldBe listOf("order_far", "order_near")
            }
        }

        When("a real Baemin-style PICKUP order is among the real READY_FOR_PICKUP candidates") {
            val pickupOrder = EatsOrder(
                id = "order_pickup", buyerId = "buyer_3", restaurantId = "restaurant_near", deliveryAddress = "Pickup at Kigali Grill",
                itemsSubtotal = BigDecimal("5000"), deliveryFee = BigDecimal.ZERO, platformFee = BigDecimal("75"),
                totalAmount = BigDecimal("5000"), transactionId = "ledgertxn_pickup_browse", status = EatsOrderStatus.READY_FOR_PICKUP,
                fulfillmentType = EatsFulfillmentType.PICKUP,
            )
            val riderWithNoLocation = Rider(id = "rider_4", userId = "rider_user_4", accountId = "account_rider_4")
            every { riderRepository.findByUserId("rider_user_4") } returns riderWithNoLocation
            val fallbackPage = org.springframework.data.domain.PageImpl(listOf(orderFromFarRestaurant, orderFromNearRestaurant, pickupOrder))
            every { eatsOrderRepository.findByStatusAndRiderIdIsNullOrderByCreatedAtAsc(EatsOrderStatus.READY_FOR_PICKUP, any()) } returns fallbackPage

            val page = service.getAvailableDeliveries("rider_user_4", PageRequest.of(0, 20))

            Then("it real-excludes the PICKUP order -- no rider should ever see or claim it via browse") {
                page.content.map { it.id } shouldBe listOf("order_far", "order_near")
            }
        }

        When("OSRM is configured and reports a real road distance that flips the Haversine ranking") {
            // Real road distance can exceed straight-line -- here the "near" restaurant's
            // actual road distance is longer than the "far" one's, proving the final sort
            // genuinely comes from OSRM's real /table response, not just Haversine.
            every { osrmRoutingClient.isConfigured } returns true
            val riderAtKigaliCenter = Rider(id = "rider_3", userId = "rider_user_3", accountId = "account_rider_3", currentLatitude = -1.9441, currentLongitude = 30.0619)
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

    Given("a real delivered order with an assigned rider") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val riderRepository = mockk<RiderRepository>()
        val eatsOrderRepository = mockk<EatsOrderRepository>()
        val eatsOrderItemRepository = mockk<EatsOrderItemRepository>(relaxed = true)
        val menuOptionGroupRepository = mockk<MenuOptionGroupRepository>(relaxed = true)
        val menuOptionChoiceRepository = mockk<MenuOptionChoiceRepository>(relaxed = true)
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>()
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val autoTopUpService = mockk<rw.itunda.account.AutoTopUpService>(relaxed = true)
        every { autoTopUpService.ensureSufficientPayBalance(any(), any(), any()) } answers { secondArg() }
        val service = EatsOrderService(
            merchantRepository, merchantProductRepository, riderRepository, eatsOrderRepository,
            eatsOrderItemRepository, menuOptionGroupRepository, menuOptionChoiceRepository, accountRepository, ledgerService, transactionRepository, fraudRuleEngine,
            ledgerEntryRepository, osrmRoutingClient, nominatimGeocodingClient, rateLimiter, notificationRepository,
            mockk<EatsMembershipService>(relaxed = true).also { every { it.hasActiveMembership(any()) } returns false },
            mockk<PlatformMembershipService>(relaxed = true).also { every { it.hasActiveMembership(any()) } returns false },
            pushNotificationService,
            autoTopUpService,
        )
        val rider = Rider(id = "rider_1", userId = "rider_user_1", accountId = "account_rider", available = true)
        val riderAccount = account("account_rider", "rider_user_1")
        val buyerAccount = account("account_buyer", "buyer_1")
        every { riderRepository.findById("rider_1") } returns Optional.of(rider)
        every { accountRepository.findById("account_rider") } returns Optional.of(riderAccount)
        every { accountRepository.findByUserIdAndType("buyer_1", AccountType.MAIN) } returns buyerAccount
        every { eatsOrderRepository.save(any()) } answers { firstArg() }

        fun deliveredOrder(updatedAt: java.time.Instant = java.time.Instant.now(), tipAmount: BigDecimal? = null) = EatsOrder(
            id = "eats_order_1", buyerId = "buyer_1", restaurantId = "restaurant_1", deliveryAddress = "addr",
            itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
            totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_1", riderId = "rider_1",
            status = EatsOrderStatus.DELIVERED, updatedAt = updatedAt, tipAmount = tipAmount,
        )

        When("the buyer tips the rider a real amount") {
            every { eatsOrderRepository.findByIdForUpdate("eats_order_1") } returns Optional.of(deliveredOrder())
            val legsSlot = slot<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("tip_txn_1", emptyList())

            val result = service.tipRider("buyer_1", "eats_order_1", BigDecimal("500"))

            Then("it moves the real tip straight from the buyer's account to the rider's account") {
                result.tipAmount shouldBe BigDecimal("500")
                result.tipTransactionId shouldBe "tip_txn_1"
                val legs = legsSlot.captured
                legs.first { it.accountId == "account_buyer" }.amount shouldBe BigDecimal("500")
                legs.first { it.accountId == "account_rider" }.amount shouldBe BigDecimal("500")
            }

            // Real lost-update regression test (concurrency sweep, §236) -- proves the
            // order row is actually locked for the check-then-act-then-write on
            // tipAmount, same convention P2pTransferLimitServiceTest already
            // establishes for its own findByIdForUpdate fix.
            Then("it locks the order row for the check-then-act-then-write on tipAmount") {
                verify(exactly = 1) { eatsOrderRepository.findByIdForUpdate("eats_order_1") }
            }

            Then("it real-notifies the rider they received a tip") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "rider_user_1" && it.type == "EATS_TIP_RECEIVED" }) }
            }
        }

        When("the order hasn't been delivered yet") {
            val placedOrder = deliveredOrder().also { it.status = EatsOrderStatus.PICKED_UP }
            every { eatsOrderRepository.findByIdForUpdate("eats_order_1") } returns Optional.of(placedOrder)

            Then("it throws EatsOrderNotDeliveredException and never touches the ledger") {
                try {
                    service.tipRider("buyer_1", "eats_order_1", BigDecimal("500"))
                    error("expected EatsOrderNotDeliveredException")
                } catch (e: EatsOrderNotDeliveredException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("the order has already been tipped once") {
            every { eatsOrderRepository.findByIdForUpdate("eats_order_1") } returns Optional.of(deliveredOrder(tipAmount = BigDecimal("300")))

            Then("it throws EatsOrderAlreadyTippedException") {
                try {
                    service.tipRider("buyer_1", "eats_order_1", BigDecimal("500"))
                    error("expected EatsOrderAlreadyTippedException")
                } catch (e: EatsOrderAlreadyTippedException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("the tip amount is zero") {
            every { eatsOrderRepository.findByIdForUpdate("eats_order_1") } returns Optional.of(deliveredOrder())

            Then("it throws InvalidEatsTipAmountException") {
                try {
                    service.tipRider("buyer_1", "eats_order_1", BigDecimal.ZERO)
                    error("expected InvalidEatsTipAmountException")
                } catch (e: InvalidEatsTipAmountException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("the 30-day tip window has expired") {
            every { eatsOrderRepository.findByIdForUpdate("eats_order_1") } returns Optional.of(deliveredOrder(updatedAt = java.time.Instant.now().minus(EatsOrderService.TIP_WINDOW).minusSeconds(60)))

            Then("it throws EatsOrderTipWindowExpiredException") {
                try {
                    service.tipRider("buyer_1", "eats_order_1", BigDecimal("500"))
                    error("expected EatsOrderTipWindowExpiredException")
                } catch (e: EatsOrderTipWindowExpiredException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("someone who isn't the buyer tries to tip") {
            every { eatsOrderRepository.findByIdForUpdate("eats_order_1") } returns Optional.of(deliveredOrder())

            Then("it throws EatsOrderNotFoundException, the same real-vs-fake IDOR discipline every other order lookup uses") {
                try {
                    service.tipRider("stranger", "eats_order_1", BigDecimal("500"))
                    error("expected EatsOrderNotFoundException")
                } catch (e: EatsOrderNotFoundException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
