package rw.itunda.marketplace.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import rw.itunda.core.domain.Listing
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.review.HoodReviewService
import rw.itunda.core.security.CurrentUser
import rw.itunda.marketplace.KeywordAlertService
import rw.itunda.marketplace.ListingFavoriteService
import rw.itunda.marketplace.ListingHideService
import rw.itunda.marketplace.MarketplaceService
import rw.itunda.marketplace.PriceOfferService
import java.math.BigDecimal

/**
 * First test coverage for MarketplaceController -- the largest controller in this
 * ecosystem (30 endpoints + 30 exception handlers), previously untested despite
 * every service class in this ecosystem already having its own test file. Covers
 * real delegation (caller-scoped userId, never client-supplied) for listing
 * creation/browse/hide/lifecycle. Escrow/favorites/offers delegation lives in
 * MarketplaceControllerEscrowAndOffersTest.kt, split out to stay under the 500-line
 * guideline once the hidden-listings test pushed this file past it; exception-handler
 * mapping lives in MarketplaceControllerExceptionHandlingTest.kt -- same precedent
 * MapsControllerTest.kt/MapsControllerExceptionHandlingTest.kt already established.
 */
class MarketplaceControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")
    val pageable = PageRequest.of(0, 20)

    fun controller(
        marketplaceService: MarketplaceService = mockk(),
        priceOfferService: PriceOfferService = mockk(),
        listingFavoriteService: ListingFavoriteService = mockk(),
        listingHideService: ListingHideService = mockk(relaxed = true),
        userRepository: UserRepository = mockk(),
        hoodReviewService: HoodReviewService = mockk(),
        idempotencyService: IdempotencyService = mockk(),
        keywordAlertService: KeywordAlertService = mockk(relaxed = true),
    ) = MarketplaceController(
        marketplaceService, priceOfferService, listingFavoriteService, listingHideService,
        userRepository, hoodReviewService, idempotencyService, keywordAlertService,
    )

    Given("a real listing creation") {
        val marketplaceService = mockk<MarketplaceService>()
        val keywordAlertService = mockk<KeywordAlertService>(relaxed = true)
        val ctl = controller(marketplaceService = marketplaceService, keywordAlertService = keywordAlertService)
        val listing = mockk<Listing>(relaxed = true)
        val request = CreateListingRequest("Sofa", "Barely used", BigDecimal("50000"), "furniture")
        every {
            marketplaceService.createListing(
                "user_1", "Sofa", "Barely used", BigDecimal("50000"), "furniture", null, null, null, null,
                null, null, false, null, null, null, null, null, BigDecimal.ZERO,
            )
        } returns listing

        When("creating it") {
            ctl.createListing(request, currentUser)
            Then("it queries scoped to the caller's own userId and real-notifies matching keyword alerts") {
                verify(exactly = 1) {
                    marketplaceService.createListing(
                        "user_1", "Sofa", "Barely used", BigDecimal("50000"), "furniture", null, null, null, null,
                        null, null, false, null, null, null, null, null, BigDecimal.ZERO,
                    )
                }
                verify(exactly = 1) { keywordAlertService.notifyMatchingAlerts(listing) }
            }
        }
    }

    Given("a real categories request") {
        val ctl = controller()
        When("fetching them") {
            val response = ctl.getCategories()
            Then("it real-returns the backend's own category list") {
                response.body?.get("categories") shouldBe MarketplaceService.CATEGORIES
            }
        }
    }

    Given("a real browse request") {
        val marketplaceService = mockk<MarketplaceService>()
        val ctl = controller(marketplaceService = marketplaceService)
        every { marketplaceService.browse(pageable, "furniture", "user_1") } returns PageImpl(emptyList())

        When("browsing by category") {
            ctl.browse("furniture", pageable, currentUser)
            Then("it real-passes the caller's own userId (so their own hides apply), never a client-supplied one") {
                verify(exactly = 1) { marketplaceService.browse(pageable, "furniture", "user_1") }
            }
        }
    }

    Given("a real listing hide") {
        val listingHideService = mockk<ListingHideService>()
        val ctl = controller(listingHideService = listingHideService)
        every { listingHideService.hideListing("user_1", "listing_1") } returns mockk(relaxed = true)

        When("hiding it") {
            ctl.hideListing("listing_1", currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { listingHideService.hideListing("user_1", "listing_1") }
            }
        }
    }

    Given("a real listing unhide") {
        val listingHideService = mockk<ListingHideService>(relaxed = true)
        val ctl = controller(listingHideService = listingHideService)

        When("unhiding it") {
            ctl.unhideListing("listing_1", currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { listingHideService.unhideListing("user_1", "listing_1") }
            }
        }
    }

    Given("a real hidden-listings request") {
        val listingHideService = mockk<ListingHideService>()
        val ctl = controller(listingHideService = listingHideService)
        every { listingHideService.getMyHiddenListings("user_1", pageable) } returns PageImpl(emptyList())

        When("fetching them") {
            ctl.getMyHiddenListings(pageable, currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { listingHideService.getMyHiddenListings("user_1", pageable) }
            }
        }
    }

    Given("a real nearby request") {
        val marketplaceService = mockk<MarketplaceService>()
        val ctl = controller(marketplaceService = marketplaceService)
        every { marketplaceService.nearby(-1.9, 30.0, 5.0, pageable) } returns PageImpl(emptyList())

        When("fetching nearby listings") {
            ctl.nearby(-1.9, 30.0, 5.0, pageable)
            Then("it real-delegates the caller's real coordinates") {
                verify(exactly = 1) { marketplaceService.nearby(-1.9, 30.0, 5.0, pageable) }
            }
        }
    }

    Given("a real my-neighborhood request") {
        val marketplaceService = mockk<MarketplaceService>()
        val ctl = controller(marketplaceService = marketplaceService)
        every { marketplaceService.myNeighborhood("user_1", null, pageable) } returns PageImpl(emptyList())
        every { marketplaceService.likedListingIds(emptyList(), "user_1") } returns emptySet()

        When("browsing it") {
            ctl.myNeighborhood(null, pageable, currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { marketplaceService.myNeighborhood("user_1", null, pageable) }
            }
        }
    }

    Given("a real search request") {
        val marketplaceService = mockk<MarketplaceService>()
        val ctl = controller(marketplaceService = marketplaceService)
        every { marketplaceService.search("sofa", pageable) } returns PageImpl(emptyList())

        When("searching") {
            ctl.search("sofa", pageable)
            Then("it real-delegates the caller's real query") {
                verify(exactly = 1) { marketplaceService.search("sofa", pageable) }
            }
        }
    }

    Given("a real single-listing request") {
        val marketplaceService = mockk<MarketplaceService>()
        val userRepository = mockk<UserRepository>(relaxed = true)
        val ctl = controller(marketplaceService = marketplaceService, userRepository = userRepository)
        val listing = mockk<Listing>(relaxed = true)
        every { listing.sellerId } returns "seller_1"
        every { marketplaceService.getListing("listing_1") } returns listing

        When("fetching it") {
            ctl.getListing("listing_1")
            Then("it real-delegates by the real listing id") {
                verify(exactly = 1) { marketplaceService.getListing("listing_1") }
            }
        }
    }

    Given("a real my-listings request") {
        val marketplaceService = mockk<MarketplaceService>()
        val ctl = controller(marketplaceService = marketplaceService)
        every { marketplaceService.getMyListings("user_1", pageable) } returns PageImpl(emptyList())
        every { marketplaceService.likedListingIds(emptyList(), "user_1") } returns emptySet()

        When("fetching them") {
            ctl.getMyListings(pageable, currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { marketplaceService.getMyListings("user_1", pageable) }
            }
        }
    }

    Given("a real my-purchases request") {
        val marketplaceService = mockk<MarketplaceService>()
        val ctl = controller(marketplaceService = marketplaceService)
        every { marketplaceService.getMyPurchases("user_1", pageable) } returns PageImpl(emptyList())
        every { marketplaceService.likedListingIds(emptyList(), "user_1") } returns emptySet()

        When("fetching them") {
            ctl.getMyPurchases(pageable, currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { marketplaceService.getMyPurchases("user_1", pageable) }
            }
        }
    }

    Given("a real like toggle") {
        val marketplaceService = mockk<MarketplaceService>()
        val ctl = controller(marketplaceService = marketplaceService)
        every { marketplaceService.toggleLike("user_1", "listing_1") } returns true

        When("toggling it") {
            ctl.toggleLike("listing_1", currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { marketplaceService.toggleLike("user_1", "listing_1") }
            }
        }
    }

    Given("a real listing bump") {
        val marketplaceService = mockk<MarketplaceService>()
        val ctl = controller(marketplaceService = marketplaceService)
        every { marketplaceService.bumpListing("user_1", "listing_1") } returns mockk(relaxed = true)

        When("bumping it") {
            ctl.bumpListing("listing_1", currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { marketplaceService.bumpListing("user_1", "listing_1") }
            }
        }
    }

    Given("a real price update") {
        val marketplaceService = mockk<MarketplaceService>()
        val ctl = controller(marketplaceService = marketplaceService)
        every { marketplaceService.updatePrice("user_1", "listing_1", BigDecimal("45000")) } returns mockk(relaxed = true)

        When("updating it") {
            ctl.updatePrice("listing_1", UpdateListingPriceRequest(BigDecimal("45000")), currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { marketplaceService.updatePrice("user_1", "listing_1", BigDecimal("45000")) }
            }
        }
    }

    Given("a real listing boost") {
        val marketplaceService = mockk<MarketplaceService>()
        val idempotencyService = mockk<IdempotencyService>()
        val ctl = controller(marketplaceService = marketplaceService, idempotencyService = idempotencyService)
        val request = BoostListingRequest(3)
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every { marketplaceService.boostListing("user_1", "listing_1", 3) } returns mockk(relaxed = true)
        every {
            idempotencyService.replayOrExecute("POST /api/v1/marketplace/listings/listing_1/boost", "key-1", request, capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("boosting it") {
            ctl.boostListing("listing_1", request, "key-1", currentUser)
            Then("it routes through the real idempotency service scoped to the caller's own userId") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/marketplace/listings/listing_1/boost", "key-1", request, any()) }
                verify(exactly = 1) { marketplaceService.boostListing("user_1", "listing_1", 3) }
            }
        }
    }

    Given("a real boost-tiers request") {
        val ctl = controller()
        When("fetching them") {
            val response = ctl.getBoostTiers()
            Then("it real-returns the backend's own boost-tier table") {
                response.body?.get("tiers") shouldBe MarketplaceService.BOOST_TIERS.toSortedMap()
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
