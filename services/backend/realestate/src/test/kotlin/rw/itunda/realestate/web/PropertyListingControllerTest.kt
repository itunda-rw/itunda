package rw.itunda.realestate.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import rw.itunda.core.domain.HoodTransactionReview
import rw.itunda.core.domain.HoodTransactionType
import rw.itunda.core.domain.PropertyListing
import rw.itunda.core.domain.PropertyListingType
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.review.HoodReviewService
import rw.itunda.core.security.CurrentUser
import rw.itunda.realestate.PropertyListingFavoriteService
import rw.itunda.realestate.PropertyListingService
import rw.itunda.realestate.PropertyOfferResponseAction
import rw.itunda.realestate.PropertyOwnershipService
import rw.itunda.realestate.PropertyPriceOfferService
import java.math.BigDecimal

/**
 * First test coverage for PropertyListingController -- one of the 4 highest-
 * transaction-volume Hood controllers, previously untested despite every service
 * class in this ecosystem already having its own test file. Covers real delegation
 * (caller-scoped userId, never client-supplied). Exception-handler mapping lives in
 * PropertyListingControllerExceptionHandlingTest.kt, split out to stay under the
 * 500-line guideline.
 */
class PropertyListingControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")
    val pageable = PageRequest.of(0, 20)

    fun controller(
        propertyListingService: PropertyListingService = mockk(),
        propertyPriceOfferService: PropertyPriceOfferService = mockk(),
        propertyListingFavoriteService: PropertyListingFavoriteService = mockk(),
        userRepository: UserRepository = mockk(),
        hoodReviewService: HoodReviewService = mockk(),
        propertyOwnershipService: PropertyOwnershipService = mockk(),
    ) = PropertyListingController(
        propertyListingService, propertyPriceOfferService, propertyListingFavoriteService,
        userRepository, hoodReviewService, propertyOwnershipService,
    )

    Given("a real property-types request") {
        val ctl = controller()
        When("fetching them") {
            val response = ctl.propertyTypes()
            Then("it real-returns the backend's own property-type list") {
                response.body?.get("propertyTypes") shouldBe PropertyListingService.PROPERTY_TYPES
            }
        }
    }

    Given("a real listing creation") {
        val propertyListingService = mockk<PropertyListingService>()
        val ctl = controller(propertyListingService = propertyListingService)
        val listing = mockk<PropertyListing>(relaxed = true)
        val request = CreatePropertyListingRequest(PropertyListingType.RENT, "apartment", "Cozy 2BR", "Near town", BigDecimal("150000"))
        every {
            propertyListingService.createListing("user_1", PropertyListingType.RENT, "apartment", "Cozy 2BR", "Near town", BigDecimal("150000"), null, null, null, null)
        } returns listing

        When("creating it") {
            ctl.createListing(request, currentUser)
            Then("it queries scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) {
                    propertyListingService.createListing("user_1", PropertyListingType.RENT, "apartment", "Cozy 2BR", "Near town", BigDecimal("150000"), null, null, null, null)
                }
            }
        }
    }

    Given("a real browse request") {
        val propertyListingService = mockk<PropertyListingService>()
        val ctl = controller(propertyListingService = propertyListingService)
        every { propertyListingService.browse(pageable, PropertyListingType.RENT, "apartment") } returns PageImpl(emptyList())

        When("browsing by filters") {
            ctl.browse(PropertyListingType.RENT, "apartment", pageable)
            Then("it real-delegates the caller-selected filters") {
                verify(exactly = 1) { propertyListingService.browse(pageable, PropertyListingType.RENT, "apartment") }
            }
        }
    }

    Given("a real nearby request") {
        val propertyListingService = mockk<PropertyListingService>()
        val ctl = controller(propertyListingService = propertyListingService)
        every { propertyListingService.nearby(-1.9, 30.0, 5.0, pageable) } returns PageImpl(emptyList())

        When("fetching nearby listings") {
            ctl.nearby(-1.9, 30.0, 5.0, pageable)
            Then("it real-delegates the caller's real coordinates") {
                verify(exactly = 1) { propertyListingService.nearby(-1.9, 30.0, 5.0, pageable) }
            }
        }
    }

    Given("a real my-neighborhood request") {
        val propertyListingService = mockk<PropertyListingService>()
        val ctl = controller(propertyListingService = propertyListingService)
        every { propertyListingService.myNeighborhood("user_1", pageable) } returns PageImpl(emptyList())

        When("browsing it") {
            ctl.myNeighborhood(pageable, currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { propertyListingService.myNeighborhood("user_1", pageable) }
            }
        }
    }

    Given("a real search request") {
        val propertyListingService = mockk<PropertyListingService>()
        val ctl = controller(propertyListingService = propertyListingService)
        every { propertyListingService.search("apartment", pageable) } returns PageImpl(emptyList())

        When("searching") {
            ctl.search("apartment", pageable)
            Then("it real-delegates the caller's real query") {
                verify(exactly = 1) { propertyListingService.search("apartment", pageable) }
            }
        }
    }

    Given("a real my-listings request") {
        val propertyListingService = mockk<PropertyListingService>()
        val ctl = controller(propertyListingService = propertyListingService)
        every { propertyListingService.getMyListings("user_1", pageable) } returns PageImpl(emptyList())

        When("fetching them") {
            ctl.myListings(pageable, currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { propertyListingService.getMyListings("user_1", pageable) }
            }
        }
    }

    Given("a real my-acquired-listings request") {
        val propertyListingService = mockk<PropertyListingService>()
        val ctl = controller(propertyListingService = propertyListingService)
        every { propertyListingService.getMyAcquiredListings("user_1", pageable) } returns PageImpl(emptyList())

        When("fetching them") {
            ctl.myAcquiredListings(pageable, currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { propertyListingService.getMyAcquiredListings("user_1", pageable) }
            }
        }
    }

    Given("a real valuation request") {
        val propertyListingService = mockk<PropertyListingService>()
        val ctl = controller(propertyListingService = propertyListingService)
        every { propertyListingService.estimateValue(-1.9, 30.0, "apartment", PropertyListingType.RENT, 80.0, 5.0) } returns mockk(relaxed = true)

        When("estimating it") {
            ctl.estimateValue(-1.9, 30.0, "apartment", PropertyListingType.RENT, 80.0, 5.0)
            Then("it real-delegates the caller's real inputs") {
                verify(exactly = 1) { propertyListingService.estimateValue(-1.9, 30.0, "apartment", PropertyListingType.RENT, 80.0, 5.0) }
            }
        }
    }

    Given("a real single-listing request") {
        val propertyListingService = mockk<PropertyListingService>()
        val userRepository = mockk<UserRepository>(relaxed = true)
        val ctl = controller(propertyListingService = propertyListingService, userRepository = userRepository)
        val listing = mockk<PropertyListing>(relaxed = true)
        every { listing.listerId } returns "lister_1"
        every { propertyListingService.getListing("listing_1") } returns listing

        When("fetching it") {
            ctl.getListing("listing_1")
            Then("it real-delegates by the real listing id") {
                verify(exactly = 1) { propertyListingService.getListing("listing_1") }
            }
        }
    }

    Given("a real mark-taken request") {
        val propertyListingService = mockk<PropertyListingService>()
        val ctl = controller(propertyListingService = propertyListingService)
        every { propertyListingService.markTaken("user_1", "listing_1", "0788000000") } returns mockk(relaxed = true)

        When("marking it taken") {
            ctl.markTaken("listing_1", MarkTakenRequest("0788000000"), currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { propertyListingService.markTaken("user_1", "listing_1", "0788000000") }
            }
        }
    }

    Given("a real price update") {
        val propertyListingService = mockk<PropertyListingService>()
        val ctl = controller(propertyListingService = propertyListingService)
        every { propertyListingService.updatePrice("user_1", "listing_1", BigDecimal("140000")) } returns mockk(relaxed = true)

        When("updating it") {
            ctl.updatePrice("listing_1", UpdatePropertyPriceRequest(BigDecimal("140000")), currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { propertyListingService.updatePrice("user_1", "listing_1", BigDecimal("140000")) }
            }
        }
    }

    Given("a real ownership-verification submission") {
        val propertyOwnershipService = mockk<PropertyOwnershipService>()
        val ctl = controller(propertyOwnershipService = propertyOwnershipService)
        every { propertyOwnershipService.submit("user_1", "listing_1", "/api/v1/uploads/doc1") } returns mockk(relaxed = true)

        When("submitting it") {
            ctl.submitOwnershipVerification("listing_1", SubmitOwnershipVerificationRequest("/api/v1/uploads/doc1"), currentUser)
            Then("it queries scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { propertyOwnershipService.submit("user_1", "listing_1", "/api/v1/uploads/doc1") }
            }
        }
    }

    Given("a real review submission") {
        val hoodReviewService = mockk<HoodReviewService>()
        val ctl = controller(hoodReviewService = hoodReviewService)
        val review = mockk<HoodTransactionReview>(relaxed = true)
        every {
            hoodReviewService.submitReview("user_1", HoodTransactionType.PROPERTY_LISTING, "listing_1", listOf("clean"), emptyList())
        } returns review

        When("submitting it") {
            ctl.submitReview("listing_1", SubmitHoodReviewRequest(listOf("clean")), currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { hoodReviewService.submitReview("user_1", HoodTransactionType.PROPERTY_LISTING, "listing_1", listOf("clean"), emptyList()) }
            }
        }
    }

    Given("a real review-list request") {
        val hoodReviewService = mockk<HoodReviewService>()
        val ctl = controller(hoodReviewService = hoodReviewService)
        every { hoodReviewService.getTransactionReviews("user_1", HoodTransactionType.PROPERTY_LISTING, "listing_1") } returns emptyList()

        When("fetching them") {
            ctl.getReviews("listing_1", currentUser)
            Then("it queries scoped to the caller's own userId, never an unrelated viewer") {
                verify(exactly = 1) { hoodReviewService.getTransactionReviews("user_1", HoodTransactionType.PROPERTY_LISTING, "listing_1") }
            }
        }
    }

    Given("a real listing removal") {
        val propertyListingService = mockk<PropertyListingService>()
        val ctl = controller(propertyListingService = propertyListingService)
        every { propertyListingService.removeListing("user_1", "listing_1") } returns mockk(relaxed = true)

        When("removing it") {
            ctl.removeListing("listing_1", currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { propertyListingService.removeListing("user_1", "listing_1") }
            }
        }
    }

    Given("a real favorite-add") {
        val propertyListingFavoriteService = mockk<PropertyListingFavoriteService>()
        val ctl = controller(propertyListingFavoriteService = propertyListingFavoriteService)
        every { propertyListingFavoriteService.addFavorite("user_1", "listing_1") } returns mockk(relaxed = true)

        When("adding it") {
            ctl.addFavorite("listing_1", currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { propertyListingFavoriteService.addFavorite("user_1", "listing_1") }
            }
        }
    }

    Given("a real favorite-remove") {
        val propertyListingFavoriteService = mockk<PropertyListingFavoriteService>(relaxed = true)
        val ctl = controller(propertyListingFavoriteService = propertyListingFavoriteService)

        When("removing it") {
            ctl.removeFavorite("listing_1", currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { propertyListingFavoriteService.removeFavorite("user_1", "listing_1") }
            }
        }
    }

    Given("a real favorites-list request") {
        val propertyListingFavoriteService = mockk<PropertyListingFavoriteService>()
        val ctl = controller(propertyListingFavoriteService = propertyListingFavoriteService)
        every { propertyListingFavoriteService.getMyFavorites("user_1", pageable) } returns PageImpl(emptyList())

        When("fetching them") {
            ctl.getMyFavoriteListings(pageable, currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { propertyListingFavoriteService.getMyFavorites("user_1", pageable) }
            }
        }
    }

    Given("a real contact-lister request") {
        val propertyListingService = mockk<PropertyListingService>()
        val ctl = controller(propertyListingService = propertyListingService)
        every { propertyListingService.contactLister("user_1", "listing_1") } returns mockk(relaxed = true)

        When("contacting the lister") {
            ctl.contactLister("listing_1", currentUser)
            Then("it real-passes the caller as inquirer, never a client-supplied id") {
                verify(exactly = 1) { propertyListingService.contactLister("user_1", "listing_1") }
            }
        }
    }

    Given("a real price offer") {
        val propertyPriceOfferService = mockk<PropertyPriceOfferService>()
        val ctl = controller(propertyPriceOfferService = propertyPriceOfferService)
        every { propertyPriceOfferService.makeOffer("user_1", "listing_1", BigDecimal("130000")) } returns mockk(relaxed = true)

        When("making it") {
            ctl.makeOffer("listing_1", MakePropertyOfferRequest(BigDecimal("130000")), currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { propertyPriceOfferService.makeOffer("user_1", "listing_1", BigDecimal("130000")) }
            }
        }
    }

    Given("a real offer response") {
        val propertyPriceOfferService = mockk<PropertyPriceOfferService>()
        val ctl = controller(propertyPriceOfferService = propertyPriceOfferService)
        every { propertyPriceOfferService.respondToOffer("user_1", "offer_1", PropertyOfferResponseAction.ACCEPT, null) } returns mockk(relaxed = true)

        When("accepting it") {
            ctl.respondToOffer("offer_1", RespondToPropertyOfferRequest(PropertyOfferResponseAction.ACCEPT), currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { propertyPriceOfferService.respondToOffer("user_1", "offer_1", PropertyOfferResponseAction.ACCEPT, null) }
            }
        }
    }

    Given("a real request for a conversation's own offers") {
        val propertyPriceOfferService = mockk<PropertyPriceOfferService>()
        val ctl = controller(propertyPriceOfferService = propertyPriceOfferService)
        every { propertyPriceOfferService.getOffersForConversation("user_1", "conv_1") } returns emptyList()

        When("fetching them") {
            ctl.getOffersForConversation("conv_1", currentUser)
            Then("it queries scoped to the caller's own userId, never an unrelated viewer") {
                verify(exactly = 1) { propertyPriceOfferService.getOffersForConversation("user_1", "conv_1") }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
