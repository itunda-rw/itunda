package rw.itunda.realestate

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Conversation
import rw.itunda.core.domain.PropertyListing
import rw.itunda.core.domain.PropertyListingStatus
import rw.itunda.core.domain.PropertyListingType
import rw.itunda.core.domain.User
import rw.itunda.core.geo.NominatimGeocodingClient
import rw.itunda.core.repository.PropertyListingRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.trust.TrustScoreService
import rw.itunda.messaging.MessagingService
import rw.itunda.messaging.SelfConversationException
import java.math.BigDecimal
import java.time.Duration
import java.util.Optional

class PropertyListingServiceTest : BehaviorSpec({

    Given("a real lister creating a property listing") {
        val propertyListingRepository = mockk<PropertyListingRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>()
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val trustScoreService = mockk<TrustScoreService>(relaxed = true)
        val service = PropertyListingService(propertyListingRepository, rateLimiter, messagingService, nominatimGeocodingClient, userRepository, trustScoreService)

        When("listing with valid fields") {
            val savedSlot = slot<PropertyListing>()
            every { propertyListingRepository.save(capture(savedSlot)) } answers { firstArg() }
            every { userRepository.findById("lister_1") } returns java.util.Optional.empty()

            val listing = service.createListing(
                "lister_1", PropertyListingType.RENT, "apartment", "  2-bedroom in Kacyiru  ", "  Quiet, close to town  ",
                BigDecimal("250000"), bedrooms = 2, sizeSqm = 65.0,
            )

            Then("it trims text and defaults to AVAILABLE") {
                listing.title shouldBe "2-bedroom in Kacyiru"
                listing.description shouldBe "Quiet, close to town"
                listing.status shouldBe PropertyListingStatus.AVAILABLE
                listing.listingType shouldBe PropertyListingType.RENT
                listing.bedrooms shouldBe 2
            }
        }

        When("listing with an unknown property type") {
            Then("it throws InvalidPropertyListingException") {
                try {
                    service.createListing("lister_1", PropertyListingType.SALE, "not_real", "T", "D", BigDecimal("1000"))
                    error("expected InvalidPropertyListingException")
                } catch (e: InvalidPropertyListingException) {
                    // expected
                }
            }
        }

        When("listing with a zero price") {
            Then("it throws InvalidPropertyListingException") {
                try {
                    service.createListing("lister_1", PropertyListingType.SALE, "house", "T", "D", BigDecimal.ZERO)
                    error("expected InvalidPropertyListingException")
                } catch (e: InvalidPropertyListingException) {
                    // expected
                }
            }
        }

        When("listing with a title longer than the real 200-char DB column bound") {
            Then("it throws InvalidPropertyListingException rather than risking a raw DB insert failure") {
                try {
                    service.createListing("lister_1", PropertyListingType.SALE, "house", "x".repeat(201), "D", BigDecimal("1000"))
                    error("expected InvalidPropertyListingException")
                } catch (e: InvalidPropertyListingException) {
                    // expected
                }
            }
        }

        When("listing with negative bedrooms") {
            Then("it throws InvalidPropertyListingException") {
                try {
                    service.createListing("lister_1", PropertyListingType.SALE, "house", "T", "D", BigDecimal("1000"), bedrooms = -1)
                    error("expected InvalidPropertyListingException")
                } catch (e: InvalidPropertyListingException) {
                    // expected
                }
            }
        }

        When("listing with only one of latitude/longitude") {
            Then("it throws InvalidPropertyCoordinatesException") {
                try {
                    service.createListing(
                        "lister_1", PropertyListingType.SALE, "house", "T", "D", BigDecimal("1000"),
                        latitude = -1.9441, longitude = null,
                    )
                    error("expected InvalidPropertyCoordinatesException")
                } catch (e: InvalidPropertyCoordinatesException) {
                    // expected
                }
            }
        }

        When("a real lister exceeds the real listing-creation rate limit") {
            every { rateLimiter.checkLimit("realestate:listing:lister_1", limit = 10, window = Duration.ofHours(1)) } throws
                RateLimitExceededException("Too many requests")

            Then("it real-propagates RateLimitExceededException") {
                try {
                    service.createListing("lister_1", PropertyListingType.SALE, "house", "T", "D", BigDecimal("1000"))
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    // expected
                }
            }
        }
    }

    Given("an existing real property listing") {
        val propertyListingRepository = mockk<PropertyListingRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>()
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val trustScoreService = mockk<TrustScoreService>(relaxed = true)
        val service = PropertyListingService(propertyListingRepository, rateLimiter, messagingService, nominatimGeocodingClient, userRepository, trustScoreService)
        val listing = PropertyListing(
            id = "property_listing_1", listerId = "lister_1", listingType = PropertyListingType.RENT,
            propertyType = "apartment", title = "T", description = "D", price = BigDecimal("250000"),
        )

        When("the real lister marks it taken") {
            every { propertyListingRepository.findById("property_listing_1") } returns Optional.of(listing)
            every { propertyListingRepository.save(any()) } answers { firstArg() }

            val result = service.markTaken("lister_1", "property_listing_1")

            Then("its status flips to TAKEN") {
                result.status shouldBe PropertyListingStatus.TAKEN
            }
            Then("the real Karrot-Score-style trust badge is recomputed for the lister immediately") {
                verify(exactly = 1) { trustScoreService.computeScore("lister_1") }
            }
        }

        When("someone who doesn't own it tries to mark it taken") {
            val freshListing = PropertyListing(
                id = "property_listing_1", listerId = "lister_1", listingType = PropertyListingType.RENT,
                propertyType = "apartment", title = "T", description = "D", price = BigDecimal("250000"),
            )
            every { propertyListingRepository.findById("property_listing_1") } returns Optional.of(freshListing)

            Then("it throws PropertyListingNotFoundException, not revealing the listing exists") {
                try {
                    service.markTaken("stranger", "property_listing_1")
                    error("expected PropertyListingNotFoundException")
                } catch (e: PropertyListingNotFoundException) {
                    // expected
                }
            }
        }

        When("marking an already-TAKEN listing taken again") {
            val takenListing = PropertyListing(
                id = "property_listing_1", listerId = "lister_1", listingType = PropertyListingType.RENT,
                propertyType = "apartment", title = "T", description = "D", price = BigDecimal("250000"),
                status = PropertyListingStatus.TAKEN,
            )
            every { propertyListingRepository.findById("property_listing_1") } returns Optional.of(takenListing)

            Then("it throws PropertyListingNotAvailableException") {
                try {
                    service.markTaken("lister_1", "property_listing_1")
                    error("expected PropertyListingNotAvailableException")
                } catch (e: PropertyListingNotAvailableException) {
                    // expected
                }
            }
        }

        When("a real inquirer contacts the lister") {
            val freshListing = PropertyListing(
                id = "property_listing_1", listerId = "lister_1", listingType = PropertyListingType.RENT,
                propertyType = "apartment", title = "T", description = "D", price = BigDecimal("250000"),
            )
            val conversation = Conversation(id = "conv_1", participantAId = "inquirer_1", participantBId = "lister_1")
            every { propertyListingRepository.findById("property_listing_1") } returns Optional.of(freshListing)
            every { messagingService.startOrGetConversation("inquirer_1", "lister_1") } returns conversation

            val result = service.contactLister("inquirer_1", "property_listing_1")

            Then("it reuses MessagingService's real conversation unmodified") {
                result.id shouldBe "conv_1"
            }
        }

        When("the real lister tries to contact themselves about their own listing") {
            val freshListing = PropertyListing(
                id = "property_listing_1", listerId = "lister_1", listingType = PropertyListingType.RENT,
                propertyType = "apartment", title = "T", description = "D", price = BigDecimal("250000"),
            )
            every { propertyListingRepository.findById("property_listing_1") } returns Optional.of(freshListing)
            every { messagingService.startOrGetConversation("lister_1", "lister_1") } throws SelfConversationException("self")

            Then("it throws OwnPropertyListingException") {
                try {
                    service.contactLister("lister_1", "property_listing_1")
                    error("expected OwnPropertyListingException")
                } catch (e: OwnPropertyListingException) {
                    // expected
                }
            }
        }
    }

    Given("a real browse request with independent filters") {
        val propertyListingRepository = mockk<PropertyListingRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>()
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val trustScoreService = mockk<TrustScoreService>(relaxed = true)
        val service = PropertyListingService(propertyListingRepository, rateLimiter, messagingService, nominatimGeocodingClient, userRepository, trustScoreService)

        When("no filter is given") {
            val page = PageImpl(listOf<PropertyListing>())
            every { propertyListingRepository.findByStatusOrderByCreatedAtDesc(PropertyListingStatus.AVAILABLE, any()) } returns page

            service.browse(PageRequest.of(0, 20), null, null)

            Then("it browses AVAILABLE listings unfiltered") {
                verify { propertyListingRepository.findByStatusOrderByCreatedAtDesc(PropertyListingStatus.AVAILABLE, any()) }
            }
        }

        When("only listingType is given") {
            val page = PageImpl(listOf<PropertyListing>())
            every {
                propertyListingRepository.findByStatusAndListingTypeOrderByCreatedAtDesc(PropertyListingStatus.AVAILABLE, PropertyListingType.RENT, any())
            } returns page

            service.browse(PageRequest.of(0, 20), PropertyListingType.RENT, null)

            Then("it filters by that real listing type") {
                verify { propertyListingRepository.findByStatusAndListingTypeOrderByCreatedAtDesc(PropertyListingStatus.AVAILABLE, PropertyListingType.RENT, any()) }
            }
        }

        When("both listingType and propertyType are given") {
            val page = PageImpl(listOf<PropertyListing>())
            every {
                propertyListingRepository.findByStatusAndListingTypeAndPropertyTypeOrderByCreatedAtDesc(
                    PropertyListingStatus.AVAILABLE, PropertyListingType.SALE, "house", any(),
                )
            } returns page

            service.browse(PageRequest.of(0, 20), PropertyListingType.SALE, "house")

            Then("it filters by both real filters combined") {
                verify {
                    propertyListingRepository.findByStatusAndListingTypeAndPropertyTypeOrderByCreatedAtDesc(
                        PropertyListingStatus.AVAILABLE, PropertyListingType.SALE, "house", any(),
                    )
                }
            }
        }
    }

    Given("a real proximity ('near me') browse") {
        val propertyListingRepository = mockk<PropertyListingRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>()
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val trustScoreService = mockk<TrustScoreService>(relaxed = true)
        val service = PropertyListingService(propertyListingRepository, rateLimiter, messagingService, nominatimGeocodingClient, userRepository, trustScoreService)

        val near = PropertyListing(
            id = "property_near", listerId = "a", listingType = PropertyListingType.RENT, propertyType = "house",
            title = "T", description = "D", price = BigDecimal("100000"), latitude = -1.9500, longitude = 30.0619,
        )
        val far = PropertyListing(
            id = "property_far", listerId = "a", listingType = PropertyListingType.RENT, propertyType = "house",
            title = "T", description = "D", price = BigDecimal("100000"), latitude = -1.5, longitude = 30.0619,
        )

        When("searching a 5km radius around real Kigali-center coordinates") {
            every { propertyListingRepository.findByStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(PropertyListingStatus.AVAILABLE) } returns listOf(near, far)

            val result = service.nearby(-1.9441, 30.0619, 5.0, PageRequest.of(0, 20))

            Then("only the real near listing is returned") {
                result.content.map { it.id } shouldBe listOf("property_near")
            }
        }

        When("radiusKm is zero or negative") {
            Then("it throws InvalidPropertyCoordinatesException") {
                try {
                    service.nearby(-1.9441, 30.0619, 0.0, PageRequest.of(0, 20))
                    error("expected InvalidPropertyCoordinatesException")
                } catch (e: InvalidPropertyCoordinatesException) {
                    // expected
                }
            }
        }
    }

    Given("a real Toss Bank 우리집 시세 (home value estimate) request") {
        val propertyListingRepository = mockk<PropertyListingRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>()
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val trustScoreService = mockk<TrustScoreService>(relaxed = true)
        val service = PropertyListingService(propertyListingRepository, rateLimiter, messagingService, nominatimGeocodingClient, userRepository, trustScoreService)

        fun comp(id: String, price: String, sizeSqm: Double, lat: Double = -1.9500, lng: Double = 30.0619) = PropertyListing(
            id = id, listerId = "a", listingType = PropertyListingType.SALE, propertyType = "house",
            title = "T", description = "D", price = BigDecimal(price), sizeSqm = sizeSqm, latitude = lat, longitude = lng,
        )

        When("at least 3 real comparable listings exist nearby") {
            val comps = listOf(comp("c1", "10000000", 100.0), comp("c2", "12000000", 120.0), comp("c3", "9000000", 90.0))
            every { propertyListingRepository.findByStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(PropertyListingStatus.AVAILABLE) } returns comps

            val estimate = service.estimateValue(-1.9441, 30.0619, "house", PropertyListingType.SALE, 100.0, 5.0)

            Then("a real estimate is computed from their real average price-per-sqm") {
                // Each comp is exactly 100,000/sqm, so the average is exact and the
                // estimate for a 100sqm target is exactly 10,000,000.
                estimate.estimatedValue shouldBe BigDecimal("10000000.00")
                estimate.comparableCount shouldBe 3
            }
        }

        When("fewer than 3 real comparable listings exist nearby") {
            val comps = listOf(comp("c1", "10000000", 100.0), comp("c2", "12000000", 120.0))
            every { propertyListingRepository.findByStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(PropertyListingStatus.AVAILABLE) } returns comps

            Then("it throws InsufficientComparablesException rather than a fabricated number") {
                try {
                    service.estimateValue(-1.9441, 30.0619, "house", PropertyListingType.SALE, 100.0, 5.0)
                    error("expected InsufficientComparablesException")
                } catch (e: InsufficientComparablesException) {
                    // expected
                }
            }
        }

        When("only far-away comparables exist") {
            val comps = listOf(
                comp("c1", "10000000", 100.0, lat = -1.5), comp("c2", "12000000", 120.0, lat = -1.5), comp("c3", "9000000", 90.0, lat = -1.5),
            )
            every { propertyListingRepository.findByStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(PropertyListingStatus.AVAILABLE) } returns comps

            Then("they're correctly excluded by the real radius filter, real-422ing") {
                try {
                    service.estimateValue(-1.9441, 30.0619, "house", PropertyListingType.SALE, 100.0, 5.0)
                    error("expected InsufficientComparablesException")
                } catch (e: InsufficientComparablesException) {
                    // expected
                }
            }
        }
    }

    Given("a real caller browsing their own real neighborhood") {
        val propertyListingRepository = mockk<PropertyListingRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>()
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val trustScoreService = mockk<TrustScoreService>(relaxed = true)
        val service = PropertyListingService(propertyListingRepository, rateLimiter, messagingService, nominatimGeocodingClient, userRepository, trustScoreService)

        When("the caller has a real neighborhood set") {
            val caller = User(id = "user_1", phoneNumber = "+250780000001", firstName = "A", lastName = "B", passwordHash = "x", neighborhood = "Kimironko")
            val expectedPage = PageImpl(listOf(mockk<PropertyListing>()))
            every { userRepository.findById("user_1") } returns Optional.of(caller)
            every {
                propertyListingRepository.findByStatusAndNeighborhoodInOrderByCreatedAtDesc(PropertyListingStatus.AVAILABLE, listOf("Kimironko"), any())
            } returns expectedPage

            val page = service.myNeighborhood("user_1", PageRequest.of(0, 20))

            Then("it real-filters to exactly that neighborhood") {
                page shouldBe expectedPage
            }
        }

        When("the caller hasn't set a real neighborhood yet") {
            val caller = User(id = "user_1", phoneNumber = "+250780000001", firstName = "A", lastName = "B", passwordHash = "x")
            every { userRepository.findById("user_1") } returns Optional.of(caller)

            Then("it throws RealEstateNeighborhoodNotSetException rather than silently returning an empty page") {
                try {
                    service.myNeighborhood("user_1", PageRequest.of(0, 20))
                    error("expected RealEstateNeighborhoodNotSetException")
                } catch (e: RealEstateNeighborhoodNotSetException) {
                    // expected
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
