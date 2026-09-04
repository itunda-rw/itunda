package rw.itunda.marketplace

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Conversation
import rw.itunda.core.domain.Listing
import rw.itunda.core.domain.ListingStatus
import rw.itunda.core.domain.Message
import rw.itunda.core.domain.PriceOffer
import rw.itunda.core.domain.PriceOfferStatus
import rw.itunda.core.repository.ListingRepository
import rw.itunda.core.repository.PriceOfferRepository
import rw.itunda.messaging.MessagingService
import java.math.BigDecimal
import java.util.Optional

class PriceOfferServiceTest : BehaviorSpec({

    Given("a real active listing owned by a real seller") {
        val priceOfferRepository = mockk<PriceOfferRepository>()
        val listingRepository = mockk<ListingRepository>()
        val messagingService = mockk<MessagingService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = PriceOfferService(priceOfferRepository, listingRepository, messagingService, rateLimiter)

        val listing = Listing(
            id = "listing_1", sellerId = "seller_1", title = "Bicycle", description = "d",
            price = BigDecimal("15000"), category = "sports",
        )
        val conversation = Conversation(id = "conversation_1", participantAId = "buyer_1", participantBId = "seller_1")
        every { listingRepository.findById("listing_1") } returns Optional.of(listing)
        every { messagingService.startOrGetConversation("buyer_1", "seller_1") } returns conversation

        When("a real buyer makes a real offer") {
            val messageSlot = slot<String>()
            every { messagingService.sendMessage("buyer_1", "conversation_1", capture(messageSlot)) } returns
                Message(id = "message_1", conversationId = "conversation_1", senderId = "buyer_1", body = "")
            val offerSlot = slot<PriceOffer>()
            every { priceOfferRepository.save(capture(offerSlot)) } answers { firstArg() }

            val offer = service.makeOffer("buyer_1", "listing_1", BigDecimal("12000"))

            Then("it posts a real offer message and saves a real PENDING offer proposed by the buyer") {
                messageSlot.captured shouldBe "💰 Offered 12,000 RWF for \"Bicycle\""
                offer.amount shouldBe BigDecimal("12000")
                offer.buyerId shouldBe "buyer_1"
                offer.sellerId shouldBe "seller_1"
                offer.proposedByUserId shouldBe "buyer_1"
                offer.status shouldBe PriceOfferStatus.PENDING
                offer.conversationId shouldBe "conversation_1"
                offer.messageId shouldBe "message_1"
            }
        }

        When("the seller tries to offer on their own listing") {
            Then("it throws OwnListingException") {
                try {
                    service.makeOffer("seller_1", "listing_1", BigDecimal("12000"))
                    error("expected OwnListingException")
                } catch (e: OwnListingException) {
                    // expected
                }
            }
        }

        When("a zero or negative amount is offered") {
            Then("it throws InvalidOfferAmountException") {
                try {
                    service.makeOffer("buyer_1", "listing_1", BigDecimal.ZERO)
                    error("expected InvalidOfferAmountException")
                } catch (e: InvalidOfferAmountException) {
                    // expected
                }
            }
        }

        When("offering on a listing that's no longer active") {
            val soldListing = Listing(
                id = "listing_2", sellerId = "seller_1", title = "Bicycle", description = "d",
                price = BigDecimal("15000"), category = "sports", status = ListingStatus.SOLD,
            )
            every { listingRepository.findById("listing_2") } returns Optional.of(soldListing)

            Then("it throws ListingNotActiveException") {
                try {
                    service.makeOffer("buyer_1", "listing_2", BigDecimal("12000"))
                    error("expected ListingNotActiveException")
                } catch (e: ListingNotActiveException) {
                    // expected
                }
            }
        }

        When("offering on a listing that doesn't exist") {
            every { listingRepository.findById("listing_missing") } returns Optional.empty()

            Then("it throws ListingNotFoundException") {
                try {
                    service.makeOffer("buyer_1", "listing_missing", BigDecimal("12000"))
                    error("expected ListingNotFoundException")
                } catch (e: ListingNotFoundException) {
                    // expected
                }
            }
        }
    }

    Given("a real pending offer, tested independently per action") {
        val priceOfferRepository = mockk<PriceOfferRepository>()
        val listingRepository = mockk<ListingRepository>()
        val messagingService = mockk<MessagingService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)

        val listing = Listing(
            id = "listing_1", sellerId = "seller_1", title = "Bicycle", description = "d",
            price = BigDecimal("15000"), category = "sports",
        )

        fun freshOffer() = PriceOffer(
            id = "price_offer_1", listingId = "listing_1", messageId = "message_1", conversationId = "conversation_1",
            buyerId = "buyer_1", sellerId = "seller_1", proposedByUserId = "buyer_1", amount = BigDecimal("12000"),
        )

        When("the seller accepts a real pending offer") {
            val service = PriceOfferService(priceOfferRepository, listingRepository, messagingService, rateLimiter)
            val offer = freshOffer()
            every { listingRepository.findById("listing_1") } returns Optional.of(listing)
            every { priceOfferRepository.findById("price_offer_1") } returns Optional.of(offer)
            every { priceOfferRepository.save(any()) } answers { firstArg() }
            val bodySlot = slot<String>()
            every { messagingService.sendMessage("seller_1", "conversation_1", capture(bodySlot)) } returns
                Message(id = "message_2", conversationId = "conversation_1", senderId = "seller_1", body = "")

            val result = service.respondToOffer("seller_1", "price_offer_1", OfferResponseAction.ACCEPT)

            Then("status flips to ACCEPTED, respondedAt is set, and a real acceptance message is posted") {
                result.status shouldBe PriceOfferStatus.ACCEPTED
                (result.respondedAt != null) shouldBe true
                bodySlot.captured shouldBe "✅ Offer accepted: 12,000 RWF"
            }
        }

        When("the seller rejects a real pending offer") {
            val service = PriceOfferService(priceOfferRepository, listingRepository, messagingService, rateLimiter)
            val offer = freshOffer()
            every { listingRepository.findById("listing_1") } returns Optional.of(listing)
            every { priceOfferRepository.findById("price_offer_1") } returns Optional.of(offer)
            every { priceOfferRepository.save(any()) } answers { firstArg() }
            every { messagingService.sendMessage("seller_1", "conversation_1", any()) } returns
                Message(id = "message_2", conversationId = "conversation_1", senderId = "seller_1", body = "")

            val result = service.respondToOffer("seller_1", "price_offer_1", OfferResponseAction.REJECT)

            Then("status flips to REJECTED") {
                result.status shouldBe PriceOfferStatus.REJECTED
            }
        }

        When("the seller counters a real pending offer") {
            val service = PriceOfferService(priceOfferRepository, listingRepository, messagingService, rateLimiter)
            val offer = freshOffer()
            every { listingRepository.findById("listing_1") } returns Optional.of(listing)
            every { priceOfferRepository.findById("price_offer_1") } returns Optional.of(offer)
            val savedSlot = slot<PriceOffer>()
            every { priceOfferRepository.save(capture(savedSlot)) } answers { firstArg() }
            val bodySlot = slot<String>()
            every { messagingService.sendMessage("seller_1", "conversation_1", capture(bodySlot)) } returns
                Message(id = "message_3", conversationId = "conversation_1", senderId = "seller_1", body = "")

            val result = service.respondToOffer("seller_1", "price_offer_1", OfferResponseAction.COUNTER, BigDecimal("13500"))

            Then("a real brand-new PENDING offer is returned, proposed by the seller, and the original is COUNTERED") {
                result.status shouldBe PriceOfferStatus.PENDING
                result.proposedByUserId shouldBe "seller_1"
                result.amount shouldBe BigDecimal("13500")
                bodySlot.captured shouldBe "🔁 Countered: 13,500 RWF for \"Bicycle\""
            }
        }

        When("the buyer (who proposed it) tries to respond to their own offer") {
            val service = PriceOfferService(priceOfferRepository, listingRepository, messagingService, rateLimiter)
            val offer = freshOffer()
            every { priceOfferRepository.findById("price_offer_1") } returns Optional.of(offer)

            Then("it throws OwnOfferException") {
                try {
                    service.respondToOffer("buyer_1", "price_offer_1", OfferResponseAction.ACCEPT)
                    error("expected OwnOfferException")
                } catch (e: OwnOfferException) {
                    // expected
                }
            }
        }

        When("a real stranger tries to respond") {
            val service = PriceOfferService(priceOfferRepository, listingRepository, messagingService, rateLimiter)
            val offer = freshOffer()
            every { priceOfferRepository.findById("price_offer_1") } returns Optional.of(offer)

            Then("it throws PriceOfferNotFoundException, not a 403 that would confirm the offer exists") {
                try {
                    service.respondToOffer("stranger", "price_offer_1", OfferResponseAction.ACCEPT)
                    error("expected PriceOfferNotFoundException")
                } catch (e: PriceOfferNotFoundException) {
                    // expected
                }
            }
        }

        When("responding to an already-resolved offer") {
            val service = PriceOfferService(priceOfferRepository, listingRepository, messagingService, rateLimiter)
            val resolvedOffer = freshOffer().apply { status = PriceOfferStatus.ACCEPTED }
            every { priceOfferRepository.findById("price_offer_1") } returns Optional.of(resolvedOffer)

            Then("it throws OfferAlreadyResolvedException") {
                try {
                    service.respondToOffer("seller_1", "price_offer_1", OfferResponseAction.REJECT)
                    error("expected OfferAlreadyResolvedException")
                } catch (e: OfferAlreadyResolvedException) {
                    // expected
                }
            }
        }
    }

    Given("real offer negotiation history across two different conversations") {
        val priceOfferRepository = mockk<PriceOfferRepository>()
        val listingRepository = mockk<ListingRepository>()
        val messagingService = mockk<MessagingService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = PriceOfferService(priceOfferRepository, listingRepository, messagingService, rateLimiter)

        val myOffer = PriceOffer(
            id = "price_offer_1", listingId = "listing_1", messageId = "message_1", conversationId = "conversation_1",
            buyerId = "buyer_1", sellerId = "seller_1", proposedByUserId = "buyer_1", amount = BigDecimal("12000"),
        )
        every { priceOfferRepository.findByConversationIdOrderByCreatedAtDesc("conversation_1") } returns listOf(myOffer)

        When("the real buyer fetches their own conversation's offer history") {
            val offers = service.getOffersForConversation("buyer_1", "conversation_1")

            Then("their own real offer is returned") {
                offers.map { it.id } shouldBe listOf("price_offer_1")
            }
        }

        When("a real stranger requests the same conversation id") {
            val offers = service.getOffersForConversation("stranger", "conversation_1")

            Then("an honest empty list is returned, not another user's real negotiation data") {
                offers shouldBe emptyList()
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
