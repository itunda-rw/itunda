package rw.itunda.marketplace

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Listing
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.geo.NominatimGeocodingClient
import rw.itunda.core.geo.OsrmRoutingClient
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.repository.ListingFavoriteRepository
import rw.itunda.core.repository.ListingLikeRepository
import rw.itunda.core.repository.ListingRepository
import rw.itunda.core.repository.MarketplaceEscrowRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.trust.TrustScoreService
import rw.itunda.messaging.MessagingService
import java.math.BigDecimal

// Real 당근카 (Karrot Vehicles) listing tests -- extracted from MarketplaceServiceTest.kt
// (2026-08-28, itunda Hood redesign) to keep that file under its frozen file-size-lint
// baseline, same "extract, don't grow the frozen file" discipline as every other pass
// this session. See Listing.vehicleIsLeaseTakeover's own doc comment for why vehicles
// extend Listing rather than becoming a parallel entity.
class MarketplaceVehicleListingTest : BehaviorSpec({

    Given("a seller listing a real vehicle") {
        val listingRepository = mockk<ListingRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>(relaxed = true)
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val trustScoreService = mockk<TrustScoreService>(relaxed = true)
        val accountRepository = mockk<AccountRepository>(relaxed = true)
        val ledgerService = mockk<LedgerService>(relaxed = true)
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val marketplaceEscrowRepository = mockk<MarketplaceEscrowRepository>(relaxed = true)
        val listingLikeRepository = mockk<ListingLikeRepository>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val listingFavoriteRepository = mockk<ListingFavoriteRepository>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = MarketplaceService(
            listingRepository, rateLimiter, messagingService, osrmRoutingClient, nominatimGeocodingClient, userRepository, trustScoreService,
            accountRepository, ledgerService, transactionRepository, marketplaceEscrowRepository, listingLikeRepository, fraudRuleEngine,
            listingFavoriteRepository, notificationRepository, pushNotificationService,
            mockk(relaxed = true),
        )

        When("creating a real 당근카 lease-takeover listing with a full real cost breakdown") {
            val savedSlot = slot<Listing>()
            every { listingRepository.save(capture(savedSlot)) } answers { firstArg() }

            val listing = service.createListing(
                "seller_1", "Sonata Hybrid lease takeover", "60km driven, single owner", BigDecimal("2104"), "vehicle",
                vehicleMileageKm = 60, vehicleInsuranceClaimCount = 0, vehicleIsLeaseTakeover = true,
                leaseTotalAcquisitionCost = BigDecimal("2104"), leaseRemainingMonths = 46, leaseTotalMonths = 48,
                leaseMonthlyPayment = BigDecimal("470"), leaseSubsidyAmount = BigDecimal("200"),
            )

            Then("it real-persists the full lease-takeover breakdown") {
                listing.vehicleIsLeaseTakeover shouldBe true
                listing.vehicleMileageKm shouldBe 60
                listing.leaseRemainingMonths shouldBe 46
                listing.leaseTotalMonths shouldBe 48
            }

            // Real gap found live (repo-wide rate-limiter-verification sweep,
            // 2026-09-08): rateLimiter was relaxed = true with zero verify{} anywhere
            // in this file, so a future accidental removal of the real checkLimit call
            // would have compiled and passed silently.
            Then("the real rate limiter is actually consulted, not just mocked away") {
                verify(exactly = 1) { rateLimiter.checkLimit("marketplace:create:seller_1", limit = 10, window = java.time.Duration.ofHours(1)) }
            }
        }

        When("creating a lease-takeover listing missing the real cost breakdown") {
            Then("it throws InvalidListingException rather than a half-filled takeover") {
                try {
                    service.createListing(
                        "seller_1", "Car", "desc", BigDecimal("100"), "vehicle",
                        vehicleIsLeaseTakeover = true,
                    )
                    error("expected InvalidListingException")
                } catch (e: InvalidListingException) {
                    // expected
                }
            }
        }

        When("creating a listing with negative vehicle mileage") {
            Then("it throws InvalidListingException") {
                try {
                    service.createListing("seller_1", "Car", "desc", BigDecimal("100"), "vehicle", vehicleMileageKm = -1)
                    error("expected InvalidListingException")
                } catch (e: InvalidListingException) {
                    // expected
                }
            }
        }

        When("creating a lease-takeover listing where remaining months exceeds total months") {
            Then("it throws InvalidListingException") {
                try {
                    service.createListing(
                        "seller_1", "Car", "desc", BigDecimal("100"), "vehicle",
                        vehicleIsLeaseTakeover = true, leaseTotalAcquisitionCost = BigDecimal("1000"),
                        leaseRemainingMonths = 50, leaseTotalMonths = 48, leaseMonthlyPayment = BigDecimal("100"),
                    )
                    error("expected InvalidListingException")
                } catch (e: InvalidListingException) {
                    // expected
                }
            }
        }
    }
})
