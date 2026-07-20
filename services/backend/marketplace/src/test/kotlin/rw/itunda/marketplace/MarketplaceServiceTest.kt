package rw.itunda.marketplace

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Conversation
import rw.itunda.core.domain.Listing
import rw.itunda.core.domain.ListingStatus
import rw.itunda.core.domain.User
import rw.itunda.core.geo.NominatimGeocodingClient
import rw.itunda.core.geo.OsrmRoutingClient
import rw.itunda.core.repository.ListingRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.messaging.MessagingService
import rw.itunda.messaging.SelfConversationException
import java.math.BigDecimal
import java.util.Optional

class MarketplaceServiceTest : BehaviorSpec({

    Given("a seller listing a real item") {
        val listingRepository = mockk<ListingRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>(relaxed = true)
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val service = MarketplaceService(listingRepository, rateLimiter, messagingService, osrmRoutingClient, nominatimGeocodingClient, userRepository)

        When("creating a listing with valid fields") {
            val savedSlot = slot<Listing>()
            every { listingRepository.save(capture(savedSlot)) } answers { firstArg() }

            val listing = service.createListing("seller_1", "  Bicycle  ", "  Barely used  ", BigDecimal("15000"), "  sports  ")

            Then("it trims every text field and defaults to ACTIVE") {
                listing.title shouldBe "Bicycle"
                listing.description shouldBe "Barely used"
                listing.category shouldBe "sports"
                listing.status shouldBe ListingStatus.ACTIVE
            }
        }

        When("creating a listing with a zero or negative price") {
            Then("it throws InvalidListingException") {
                try {
                    service.createListing("seller_1", "Bike", "desc", BigDecimal.ZERO, "sports")
                    error("expected InvalidListingException")
                } catch (e: InvalidListingException) {
                    // expected
                }
            }
        }

        When("creating a listing with a blank title") {
            Then("it throws InvalidListingException") {
                try {
                    service.createListing("seller_1", "   ", "desc", BigDecimal("100"), "sports")
                    error("expected InvalidListingException")
                } catch (e: InvalidListingException) {
                    // expected
                }
            }
        }

        When("creating a listing with a title longer than the real 255-char DB column bound") {
            Then("it throws InvalidListingException rather than risking a raw DB insert failure") {
                try {
                    service.createListing("seller_1", "x".repeat(256), "desc", BigDecimal("100"), "sports")
                    error("expected InvalidListingException")
                } catch (e: InvalidListingException) {
                    // expected
                }
            }
        }
    }

    Given("an existing real listing") {
        val listingRepository = mockk<ListingRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>(relaxed = true)
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val service = MarketplaceService(listingRepository, rateLimiter, messagingService, osrmRoutingClient, nominatimGeocodingClient, userRepository)
        val listing = Listing(
            id = "listing_1", sellerId = "seller_1", title = "Bicycle", description = "desc",
            price = BigDecimal("15000"), category = "sports",
        )

        When("the real owner marks it sold") {
            every { listingRepository.findById("listing_1") } returns Optional.of(listing)
            every { listingRepository.save(any()) } answers { firstArg() }

            val result = service.markSold("seller_1", "listing_1")

            Then("its status flips to SOLD") {
                result.status shouldBe ListingStatus.SOLD
            }
        }

        When("someone who doesn't own it tries to mark it sold") {
            val freshListing = Listing(
                id = "listing_2", sellerId = "seller_1", title = "Bicycle", description = "desc",
                price = BigDecimal("15000"), category = "sports",
            )
            every { listingRepository.findById("listing_2") } returns Optional.of(freshListing)

            Then("it throws ListingNotFoundException, not a 403 that would confirm the listing exists") {
                try {
                    service.markSold("stranger", "listing_2")
                    error("expected ListingNotFoundException")
                } catch (e: ListingNotFoundException) {
                    // expected
                }
            }
        }

        When("a real buyer contacts the seller") {
            val freshListing = Listing(
                id = "listing_3", sellerId = "seller_1", title = "Bicycle", description = "desc",
                price = BigDecimal("15000"), category = "sports",
            )
            every { listingRepository.findById("listing_3") } returns Optional.of(freshListing)
            val conversation = Conversation(id = "conversation_1", participantAId = "buyer_1", participantBId = "seller_1")
            every { messagingService.startOrGetConversation("buyer_1", "seller_1") } returns conversation

            val result = service.contactSeller("buyer_1", "listing_3")

            Then("it reuses the real MessagingService conversation, unmodified") {
                result.id shouldBe "conversation_1"
            }
        }

        When("the seller tries to contact themselves about their own listing") {
            val freshListing = Listing(
                id = "listing_4", sellerId = "seller_1", title = "Bicycle", description = "desc",
                price = BigDecimal("15000"), category = "sports",
            )
            every { listingRepository.findById("listing_4") } returns Optional.of(freshListing)
            every { messagingService.startOrGetConversation("seller_1", "seller_1") } throws
                SelfConversationException("Cannot start a conversation with yourself")

            Then("it throws OwnListingException, a real domain-specific error rather than leaking the messaging one") {
                try {
                    service.contactSeller("seller_1", "listing_4")
                    error("expected OwnListingException")
                } catch (e: OwnListingException) {
                    // expected
                }
            }
        }
    }

    Given("browsing the real marketplace") {
        val listingRepository = mockk<ListingRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>(relaxed = true)
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val service = MarketplaceService(listingRepository, rateLimiter, messagingService, osrmRoutingClient, nominatimGeocodingClient, userRepository)

        When("no category filter is given") {
            every { listingRepository.findByStatusOrderByCreatedAtDesc(ListingStatus.ACTIVE, any()) } returns
                mockk(relaxed = true)

            service.browse(PageRequest.of(0, 20), null)

            Then("it queries the unfiltered ACTIVE listing method, not the category one") {
                io.mockk.verify { listingRepository.findByStatusOrderByCreatedAtDesc(ListingStatus.ACTIVE, any()) }
            }
        }
    }

    Given("a seller listing a real item with a real location") {
        val listingRepository = mockk<ListingRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>(relaxed = true)
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val service = MarketplaceService(listingRepository, rateLimiter, messagingService, osrmRoutingClient, nominatimGeocodingClient, userRepository)

        When("only one of latitude/longitude is given") {
            Then("it throws InvalidCoordinatesException") {
                try {
                    service.createListing("seller_1", "Bike", "desc", BigDecimal("100"), "sports", latitude = -1.9441)
                    error("expected InvalidCoordinatesException")
                } catch (e: InvalidCoordinatesException) {
                    // expected
                }
            }
        }

        When("an out-of-range coordinate is given") {
            Then("it throws InvalidCoordinatesException") {
                try {
                    service.createListing("seller_1", "Bike", "desc", BigDecimal("100"), "sports", latitude = 999.0, longitude = 30.0)
                    error("expected InvalidCoordinatesException")
                } catch (e: InvalidCoordinatesException) {
                    // expected
                }
            }
        }

        When("a real valid location is given") {
            val savedSlot = slot<Listing>()
            every { listingRepository.save(capture(savedSlot)) } answers { firstArg() }

            service.createListing("seller_1", "Bike", "desc", BigDecimal("100"), "sports", latitude = -1.9441, longitude = 30.0619)

            Then("it's saved on the real listing") {
                savedSlot.captured.latitude shouldBe -1.9441
                savedSlot.captured.longitude shouldBe 30.0619
            }
        }
    }

    Given("real listings at different real distances from a searcher") {
        val listingRepository = mockk<ListingRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>(relaxed = true)
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val service = MarketplaceService(listingRepository, rateLimiter, messagingService, osrmRoutingClient, nominatimGeocodingClient, userRepository)

        // Searcher at (-1.9441, 30.0619). Same longitude as both listings, only latitude
        // differs, so a real Haversine distance along a meridian is exact:
        // 6371km * (latitude difference in radians).
        val near = Listing(
            id = "listing_near", sellerId = "seller_1", title = "Near", description = "d",
            price = BigDecimal("100"), category = "sports", latitude = -1.9541, longitude = 30.0619, // ~1.11km away
        )
        val far = Listing(
            id = "listing_far", sellerId = "seller_1", title = "Far", description = "d",
            price = BigDecimal("100"), category = "sports", latitude = -2.9441, longitude = 30.0619, // ~111.2km away
        )
        // Deliberately returned out of distance order -- proves the service does the
        // real sorting, not just passing through whatever order the repository gave it.
        every { listingRepository.findByStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(ListingStatus.ACTIVE) } returns listOf(far, near)

        When("searching within a real 5km radius") {
            val page = service.nearby(-1.9441, 30.0619, 5.0, PageRequest.of(0, 20))

            Then("only the real near listing is returned") {
                page.content.map { it.id } shouldBe listOf("listing_near")
            }
        }

        When("searching within a real 200km radius") {
            val page = service.nearby(-1.9441, 30.0619, 200.0, PageRequest.of(0, 20))

            Then("both real listings are returned, closest first") {
                page.content.map { it.id } shouldBe listOf("listing_near", "listing_far")
            }
        }

        When("searching with an out-of-range coordinate") {
            Then("it throws InvalidCoordinatesException") {
                try {
                    service.nearby(999.0, 30.0, 5.0, PageRequest.of(0, 20))
                    error("expected InvalidCoordinatesException")
                } catch (e: InvalidCoordinatesException) {
                    // expected
                }
            }
        }

        When("searching with a zero radius") {
            Then("it throws InvalidCoordinatesException") {
                try {
                    service.nearby(-1.9441, 30.0619, 0.0, PageRequest.of(0, 20))
                    error("expected InvalidCoordinatesException")
                } catch (e: InvalidCoordinatesException) {
                    // expected
                }
            }
        }
    }

    Given("real OSRM road-distance ranking for Marketplace proximity search") {
        val listingRepository = mockk<ListingRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>()
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val service = MarketplaceService(listingRepository, rateLimiter, messagingService, osrmRoutingClient, nominatimGeocodingClient, userRepository)

        // Both within Rwanda's bounding envelope, both within a real 5km straight-line
        // radius of the searcher -- Haversine says listingA is closer.
        val listingA = Listing(
            id = "listing_a", sellerId = "seller_1", title = "A", description = "d",
            price = BigDecimal("100"), category = "sports", latitude = -1.9541, longitude = 30.0619, // ~1.11km Haversine
        )
        val listingB = Listing(
            id = "listing_b", sellerId = "seller_1", title = "B", description = "d",
            price = BigDecimal("100"), category = "sports", latitude = -1.9641, longitude = 30.0619, // ~2.22km Haversine
        )
        every { listingRepository.findByStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(ListingStatus.ACTIVE) } returns
            listOf(listingA, listingB)

        When("OSRM is configured and returns a real road distance that reorders the Haversine ranking") {
            every { osrmRoutingClient.isConfigured } returns true
            // Road distance flips the order: B is closer by road than A, despite being
            // farther by straight line.
            every {
                osrmRoutingClient.routeDistancesKm(-1.9441, 30.0619, listOf(-1.9541 to 30.0619, -1.9641 to 30.0619))
            } returns listOf(4.0, 1.0)

            val page = service.nearby(-1.9441, 30.0619, 5.0, PageRequest.of(0, 20))

            Then("real road distance, not straight-line distance, decides the order") {
                page.content.map { it.id } shouldBe listOf("listing_b", "listing_a")
            }
        }

        When("OSRM's real road distance pushes a Haversine-in-range listing outside the search radius") {
            every { osrmRoutingClient.isConfigured } returns true
            every {
                osrmRoutingClient.routeDistancesKm(-1.9441, 30.0619, listOf(-1.9541 to 30.0619, -1.9641 to 30.0619))
            } returns listOf(1.0, 6.0)

            val page = service.nearby(-1.9441, 30.0619, 5.0, PageRequest.of(0, 20))

            Then("it's excluded even though it passed the Haversine pre-filter") {
                page.content.map { it.id } shouldBe listOf("listing_a")
            }
        }

        When("OSRM has no route for one candidate") {
            every { osrmRoutingClient.isConfigured } returns true
            every {
                osrmRoutingClient.routeDistancesKm(-1.9441, 30.0619, listOf(-1.9541 to 30.0619, -1.9641 to 30.0619))
            } returns listOf(null, 2.5)

            val page = service.nearby(-1.9441, 30.0619, 5.0, PageRequest.of(0, 20))

            Then("that one candidate honestly falls back to its own Haversine distance, not a fabricated value") {
                // listingA's Haversine (~1.11km) still beats listingB's real road distance (2.5km).
                page.content.map { it.id } shouldBe listOf("listing_a", "listing_b")
            }
        }

        When("the searcher's own coordinate is outside Rwanda's bounding envelope") {
            every { osrmRoutingClient.isConfigured } returns true

            val page = service.nearby(0.0, 30.0, 500.0, PageRequest.of(0, 20))

            Then("OSRM is never consulted -- ranking falls straight back to Haversine, matching EatsOrderService's own guard against OSRM silently snapping an out-of-Rwanda point") {
                io.mockk.verify(exactly = 0) { osrmRoutingClient.routeDistancesKm(any(), any(), any()) }
                page.content.map { it.id } shouldBe listOf("listing_a", "listing_b")
            }
        }
    }

    Given("a real seller listing an item with real coordinates") {
        val listingRepository = mockk<ListingRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>(relaxed = true)
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>()
        val userRepository = mockk<UserRepository>()
        val service = MarketplaceService(listingRepository, rateLimiter, messagingService, osrmRoutingClient, nominatimGeocodingClient, userRepository)

        When("the real coordinates reverse-geocode to a real neighborhood") {
            val savedSlot = slot<Listing>()
            every { nominatimGeocodingClient.reverseGeocode(-1.9536, 30.0605) } returns "Nyarugenge"
            every { listingRepository.save(capture(savedSlot)) } answers { firstArg() }

            service.createListing("seller_1", "Sofa", "Real leather sofa", BigDecimal("50000"), "furniture", -1.9536, 30.0605)

            Then("the real neighborhood is cached on the listing at creation time, not recomputed later") {
                savedSlot.captured.neighborhood shouldBe "Nyarugenge"
            }
        }

        When("no real coordinates are given") {
            val savedSlot = slot<Listing>()
            every { listingRepository.save(capture(savedSlot)) } answers { firstArg() }

            service.createListing("seller_1", "Sofa", "Real leather sofa", BigDecimal("50000"), "furniture")

            Then("neighborhood stays null -- never a fabricated guess, and geocoding is never even called") {
                savedSlot.captured.neighborhood shouldBe null
                io.mockk.verify(exactly = 0) { nominatimGeocodingClient.reverseGeocode(any(), any()) }
            }
        }
    }

    Given("a real caller browsing their own real neighborhood") {
        val listingRepository = mockk<ListingRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>(relaxed = true)
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val service = MarketplaceService(listingRepository, rateLimiter, messagingService, osrmRoutingClient, nominatimGeocodingClient, userRepository)

        When("the caller has a real neighborhood set, no category filter") {
            val caller = User(id = "user_1", phoneNumber = "+250780000001", firstName = "A", lastName = "B", passwordHash = "x", neighborhood = "Kimironko")
            val expectedPage = PageImpl(listOf(mockk<Listing>()))
            every { userRepository.findById("user_1") } returns Optional.of(caller)
            every { listingRepository.findByStatusAndNeighborhoodOrderByCreatedAtDesc(ListingStatus.ACTIVE, "Kimironko", any()) } returns expectedPage

            val page = service.myNeighborhood("user_1", null, PageRequest.of(0, 20))

            Then("it real-filters to exactly that neighborhood") {
                page shouldBe expectedPage
            }
        }

        When("the caller has a real neighborhood set, combined with a category filter") {
            val caller = User(id = "user_1", phoneNumber = "+250780000001", firstName = "A", lastName = "B", passwordHash = "x", neighborhood = "Kimironko")
            val expectedPage = PageImpl(listOf(mockk<Listing>()))
            every { userRepository.findById("user_1") } returns Optional.of(caller)
            every {
                listingRepository.findByStatusAndNeighborhoodAndCategoryOrderByCreatedAtDesc(ListingStatus.ACTIVE, "Kimironko", "furniture", any())
            } returns expectedPage

            val page = service.myNeighborhood("user_1", "furniture", PageRequest.of(0, 20))

            Then("neighborhood and category combine, matching the established combinable-filter shape") {
                page shouldBe expectedPage
            }
        }

        When("the caller hasn't set a real neighborhood yet") {
            val caller = User(id = "user_1", phoneNumber = "+250780000001", firstName = "A", lastName = "B", passwordHash = "x", neighborhood = null)
            every { userRepository.findById("user_1") } returns Optional.of(caller)

            Then("it throws NeighborhoodNotSetException rather than silently returning an empty page") {
                try {
                    service.myNeighborhood("user_1", null, PageRequest.of(0, 20))
                    error("expected NeighborhoodNotSetException")
                } catch (e: NeighborhoodNotSetException) {
                    // expected
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
