package rw.itunda.marketplace.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import rw.itunda.core.domain.HoodTransactionReview
import rw.itunda.core.domain.HoodTransactionType
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.review.HoodReviewService
import rw.itunda.core.security.CurrentUser
import rw.itunda.marketplace.KeywordAlertService
import rw.itunda.marketplace.ListingFavoriteService
import rw.itunda.marketplace.ListingHideService
import rw.itunda.marketplace.MarketplaceService
import rw.itunda.marketplace.OfferResponseAction
import rw.itunda.marketplace.PriceOfferService
import java.math.BigDecimal

/**
 * Escrow/review/favorites/offers delegation coverage for MarketplaceController, split
 * out of MarketplaceControllerTest.kt to stay under the 500-line guideline once the
 * hidden-listings test pushed that file past it -- same precedent
 * MapsControllerTest.kt/MapsControllerExceptionHandlingTest.kt already established for
 * splitting a single controller's test coverage across files.
 */
class MarketplaceControllerEscrowAndOffersTest : BehaviorSpec({

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

    Given("a real escrow payment") {
        val marketplaceService = mockk<MarketplaceService>()
        val idempotencyService = mockk<IdempotencyService>()
        val ctl = controller(marketplaceService = marketplaceService, idempotencyService = idempotencyService)
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every { marketplaceService.payEscrow("user_1", "listing_1", null) } returns mockk(relaxed = true)
        every {
            idempotencyService.replayOrExecute("POST /api/v1/marketplace/listings/listing_1/pay-escrow", "key-1", "listing_1", capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("paying it") {
            ctl.payEscrow("listing_1", "key-1", null, currentUser)
            Then("it routes through the real idempotency service scoped to the caller's own userId") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/marketplace/listings/listing_1/pay-escrow", "key-1", "listing_1", any()) }
                verify(exactly = 1) { marketplaceService.payEscrow("user_1", "listing_1", null) }
            }
        }
    }

    Given("a real escrow receipt confirmation") {
        val marketplaceService = mockk<MarketplaceService>()
        val idempotencyService = mockk<IdempotencyService>()
        val ctl = controller(marketplaceService = marketplaceService, idempotencyService = idempotencyService)
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every { marketplaceService.confirmReceipt("user_1", "listing_1") } returns mockk(relaxed = true)
        every {
            idempotencyService.replayOrExecute("POST /api/v1/marketplace/listings/listing_1/confirm-receipt", "key-1", "listing_1", capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("confirming it") {
            ctl.confirmReceipt("listing_1", "key-1", currentUser)
            Then("it routes through the real idempotency service scoped to the caller's own userId") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/marketplace/listings/listing_1/confirm-receipt", "key-1", "listing_1", any()) }
                verify(exactly = 1) { marketplaceService.confirmReceipt("user_1", "listing_1") }
            }
        }
    }

    Given("a real escrow dispute") {
        val marketplaceService = mockk<MarketplaceService>()
        val ctl = controller(marketplaceService = marketplaceService)
        every { marketplaceService.disputeEscrow("user_1", "listing_1", "Item never arrived") } returns mockk(relaxed = true)

        When("disputing it") {
            ctl.disputeEscrow("listing_1", DisputeEscrowRequest("Item never arrived"), currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { marketplaceService.disputeEscrow("user_1", "listing_1", "Item never arrived") }
            }
        }
    }

    Given("a real escrow-status request") {
        val marketplaceService = mockk<MarketplaceService>()
        val ctl = controller(marketplaceService = marketplaceService)
        every { marketplaceService.getEscrow("user_1", "listing_1") } returns mockk(relaxed = true)

        When("fetching it") {
            ctl.getEscrow("listing_1", currentUser)
            Then("it queries scoped to the caller's own userId, never an unrelated requester") {
                verify(exactly = 1) { marketplaceService.getEscrow("user_1", "listing_1") }
            }
        }
    }

    Given("a real mark-sold request") {
        val marketplaceService = mockk<MarketplaceService>()
        val ctl = controller(marketplaceService = marketplaceService)
        every { marketplaceService.markSold("user_1", "listing_1", "0788000000") } returns mockk(relaxed = true)

        When("marking it sold") {
            ctl.markSold("listing_1", MarkSoldRequest("0788000000"), currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { marketplaceService.markSold("user_1", "listing_1", "0788000000") }
            }
        }
    }

    Given("a real review submission") {
        val hoodReviewService = mockk<HoodReviewService>()
        val ctl = controller(hoodReviewService = hoodReviewService)
        val review = mockk<HoodTransactionReview>(relaxed = true)
        every {
            hoodReviewService.submitReview("user_1", HoodTransactionType.LISTING, "listing_1", listOf("as_described"), emptyList())
        } returns review

        When("submitting it") {
            ctl.submitReview("listing_1", SubmitHoodReviewRequest(listOf("as_described")), currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { hoodReviewService.submitReview("user_1", HoodTransactionType.LISTING, "listing_1", listOf("as_described"), emptyList()) }
            }
        }
    }

    Given("a real review-list request") {
        val hoodReviewService = mockk<HoodReviewService>()
        val ctl = controller(hoodReviewService = hoodReviewService)
        every { hoodReviewService.getTransactionReviews("user_1", HoodTransactionType.LISTING, "listing_1") } returns emptyList()

        When("fetching them") {
            ctl.getReviews("listing_1", currentUser)
            Then("it queries scoped to the caller's own userId, never an unrelated viewer") {
                verify(exactly = 1) { hoodReviewService.getTransactionReviews("user_1", HoodTransactionType.LISTING, "listing_1") }
            }
        }
    }

    Given("a real listing removal") {
        val marketplaceService = mockk<MarketplaceService>()
        val ctl = controller(marketplaceService = marketplaceService)
        every { marketplaceService.removeListing("user_1", "listing_1") } returns mockk(relaxed = true)

        When("removing it") {
            ctl.removeListing("listing_1", currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { marketplaceService.removeListing("user_1", "listing_1") }
            }
        }
    }

    Given("a real favorite-add") {
        val listingFavoriteService = mockk<ListingFavoriteService>()
        val ctl = controller(listingFavoriteService = listingFavoriteService)
        every { listingFavoriteService.addFavorite("user_1", "listing_1") } returns mockk(relaxed = true)

        When("adding it") {
            ctl.addFavorite("listing_1", currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { listingFavoriteService.addFavorite("user_1", "listing_1") }
            }
        }
    }

    Given("a real favorite-remove") {
        val listingFavoriteService = mockk<ListingFavoriteService>(relaxed = true)
        val ctl = controller(listingFavoriteService = listingFavoriteService)

        When("removing it") {
            ctl.removeFavorite("listing_1", currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { listingFavoriteService.removeFavorite("user_1", "listing_1") }
            }
        }
    }

    Given("a real favorites-list request") {
        val listingFavoriteService = mockk<ListingFavoriteService>()
        val ctl = controller(listingFavoriteService = listingFavoriteService)
        every { listingFavoriteService.getMyFavorites("user_1", pageable) } returns PageImpl(emptyList())

        When("fetching them") {
            ctl.getMyFavoriteListings(pageable, currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { listingFavoriteService.getMyFavorites("user_1", pageable) }
            }
        }
    }

    Given("a real contact-seller request") {
        val marketplaceService = mockk<MarketplaceService>()
        val ctl = controller(marketplaceService = marketplaceService)
        every { marketplaceService.contactSeller("user_1", "listing_1") } returns mockk(relaxed = true)

        When("contacting the seller") {
            ctl.contactSeller("listing_1", currentUser)
            Then("it real-passes the caller as buyer, never a client-supplied id") {
                verify(exactly = 1) { marketplaceService.contactSeller("user_1", "listing_1") }
            }
        }
    }

    Given("a real price offer") {
        val priceOfferService = mockk<PriceOfferService>()
        val ctl = controller(priceOfferService = priceOfferService)
        every { priceOfferService.makeOffer("user_1", "listing_1", BigDecimal("40000")) } returns mockk(relaxed = true)

        When("making it") {
            ctl.makeOffer("listing_1", MakeOfferRequest(BigDecimal("40000")), currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { priceOfferService.makeOffer("user_1", "listing_1", BigDecimal("40000")) }
            }
        }
    }

    Given("a real offer response") {
        val priceOfferService = mockk<PriceOfferService>()
        val ctl = controller(priceOfferService = priceOfferService)
        every { priceOfferService.respondToOffer("user_1", "offer_1", OfferResponseAction.ACCEPT, null) } returns mockk(relaxed = true)

        When("accepting it") {
            ctl.respondToOffer("offer_1", RespondToOfferRequest(OfferResponseAction.ACCEPT), currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { priceOfferService.respondToOffer("user_1", "offer_1", OfferResponseAction.ACCEPT, null) }
            }
        }
    }

    Given("a real request for a conversation's own offers") {
        val priceOfferService = mockk<PriceOfferService>()
        val ctl = controller(priceOfferService = priceOfferService)
        every { priceOfferService.getOffersForConversation("user_1", "conv_1") } returns emptyList()

        When("fetching them") {
            ctl.getOffersForConversation("conv_1", currentUser)
            Then("it queries scoped to the caller's own userId, never an unrelated viewer") {
                verify(exactly = 1) { priceOfferService.getOffersForConversation("user_1", "conv_1") }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
