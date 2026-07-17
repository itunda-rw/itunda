package rw.itunda.marketplace

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.springframework.data.domain.PageRequest
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Conversation
import rw.itunda.core.domain.Listing
import rw.itunda.core.domain.ListingStatus
import rw.itunda.core.repository.ListingRepository
import rw.itunda.messaging.MessagingService
import rw.itunda.messaging.SelfConversationException
import java.math.BigDecimal
import java.util.Optional

class MarketplaceServiceTest : BehaviorSpec({

    Given("a seller listing a real item") {
        val listingRepository = mockk<ListingRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>()
        val service = MarketplaceService(listingRepository, rateLimiter, messagingService)

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
    }

    Given("an existing real listing") {
        val listingRepository = mockk<ListingRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>()
        val service = MarketplaceService(listingRepository, rateLimiter, messagingService)
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
        val service = MarketplaceService(listingRepository, rateLimiter, messagingService)

        When("no category filter is given") {
            every { listingRepository.findByStatusOrderByCreatedAtDesc(ListingStatus.ACTIVE, any()) } returns
                mockk(relaxed = true)

            service.browse(PageRequest.of(0, 20), null)

            Then("it queries the unfiltered ACTIVE listing method, not the category one") {
                io.mockk.verify { listingRepository.findByStatusOrderByCreatedAtDesc(ListingStatus.ACTIVE, any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
