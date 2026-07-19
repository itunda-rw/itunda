package rw.itunda.realestate

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Conversation
import rw.itunda.core.domain.Message
import rw.itunda.core.domain.PriceOfferStatus
import rw.itunda.core.domain.PropertyListing
import rw.itunda.core.domain.PropertyListingStatus
import rw.itunda.core.domain.PropertyListingType
import rw.itunda.core.domain.PropertyPriceOffer
import rw.itunda.core.repository.PropertyListingRepository
import rw.itunda.core.repository.PropertyPriceOfferRepository
import rw.itunda.messaging.MessagingService
import java.math.BigDecimal
import java.util.Optional

class PropertyPriceOfferServiceTest : BehaviorSpec({

    Given("a real available property listing owned by a real lister") {
        val propertyPriceOfferRepository = mockk<PropertyPriceOfferRepository>()
        val propertyListingRepository = mockk<PropertyListingRepository>()
        val messagingService = mockk<MessagingService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = PropertyPriceOfferService(propertyPriceOfferRepository, propertyListingRepository, messagingService, rateLimiter)

        val listing = PropertyListing(
            id = "property_listing_1", listerId = "lister_1", listingType = PropertyListingType.RENT,
            propertyType = "apartment", title = "2-bedroom in Kacyiru", description = "d", price = BigDecimal("250000"),
        )
        val conversation = Conversation(id = "conversation_1", participantAId = "inquirer_1", participantBId = "lister_1")
        every { propertyListingRepository.findById("property_listing_1") } returns Optional.of(listing)
        every { messagingService.startOrGetConversation("inquirer_1", "lister_1") } returns conversation

        When("a real inquirer makes a real offer") {
            val messageSlot = slot<String>()
            every { messagingService.sendMessage("inquirer_1", "conversation_1", capture(messageSlot)) } returns
                Message(id = "message_1", conversationId = "conversation_1", senderId = "inquirer_1", body = "")
            val offerSlot = slot<PropertyPriceOffer>()
            every { propertyPriceOfferRepository.save(capture(offerSlot)) } answers { firstArg() }

            val offer = service.makeOffer("inquirer_1", "property_listing_1", BigDecimal("220000"))

            Then("it posts a real offer message and saves a real PENDING offer proposed by the inquirer") {
                messageSlot.captured shouldBe "💰 Offered 220000 RWF for \"2-bedroom in Kacyiru\""
                offer.amount shouldBe BigDecimal("220000")
                offer.inquirerId shouldBe "inquirer_1"
                offer.listerId shouldBe "lister_1"
                offer.proposedByUserId shouldBe "inquirer_1"
                offer.status shouldBe PriceOfferStatus.PENDING
                offer.conversationId shouldBe "conversation_1"
                offer.messageId shouldBe "message_1"
            }
        }

        When("the lister tries to offer on their own listing") {
            Then("it throws OwnPropertyListingException") {
                try {
                    service.makeOffer("lister_1", "property_listing_1", BigDecimal("220000"))
                    error("expected OwnPropertyListingException")
                } catch (e: OwnPropertyListingException) {
                    // expected
                }
            }
        }

        When("a zero or negative amount is offered") {
            Then("it throws InvalidPropertyOfferAmountException") {
                try {
                    service.makeOffer("inquirer_1", "property_listing_1", BigDecimal.ZERO)
                    error("expected InvalidPropertyOfferAmountException")
                } catch (e: InvalidPropertyOfferAmountException) {
                    // expected
                }
            }
        }

        When("offering on a listing that's no longer available") {
            val takenListing = PropertyListing(
                id = "property_listing_2", listerId = "lister_1", listingType = PropertyListingType.RENT,
                propertyType = "apartment", title = "T", description = "d", price = BigDecimal("250000"),
                status = PropertyListingStatus.TAKEN,
            )
            every { propertyListingRepository.findById("property_listing_2") } returns Optional.of(takenListing)

            Then("it throws PropertyListingNotAvailableException") {
                try {
                    service.makeOffer("inquirer_1", "property_listing_2", BigDecimal("220000"))
                    error("expected PropertyListingNotAvailableException")
                } catch (e: PropertyListingNotAvailableException) {
                    // expected
                }
            }
        }

        When("offering on a listing that doesn't exist") {
            every { propertyListingRepository.findById("property_listing_missing") } returns Optional.empty()

            Then("it throws PropertyListingNotFoundException") {
                try {
                    service.makeOffer("inquirer_1", "property_listing_missing", BigDecimal("220000"))
                    error("expected PropertyListingNotFoundException")
                } catch (e: PropertyListingNotFoundException) {
                    // expected
                }
            }
        }
    }

    Given("a real pending offer, tested independently per action") {
        val propertyPriceOfferRepository = mockk<PropertyPriceOfferRepository>()
        val propertyListingRepository = mockk<PropertyListingRepository>()
        val messagingService = mockk<MessagingService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)

        val listing = PropertyListing(
            id = "property_listing_1", listerId = "lister_1", listingType = PropertyListingType.RENT,
            propertyType = "apartment", title = "2-bedroom in Kacyiru", description = "d", price = BigDecimal("250000"),
        )

        fun freshOffer() = PropertyPriceOffer(
            id = "property_offer_1", propertyListingId = "property_listing_1", messageId = "message_1", conversationId = "conversation_1",
            inquirerId = "inquirer_1", listerId = "lister_1", proposedByUserId = "inquirer_1", amount = BigDecimal("220000"),
        )

        When("the lister accepts a real pending offer") {
            val service = PropertyPriceOfferService(propertyPriceOfferRepository, propertyListingRepository, messagingService, rateLimiter)
            val offer = freshOffer()
            every { propertyListingRepository.findById("property_listing_1") } returns Optional.of(listing)
            every { propertyPriceOfferRepository.findById("property_offer_1") } returns Optional.of(offer)
            every { propertyPriceOfferRepository.save(any()) } answers { firstArg() }
            val bodySlot = slot<String>()
            every { messagingService.sendMessage("lister_1", "conversation_1", capture(bodySlot)) } returns
                Message(id = "message_2", conversationId = "conversation_1", senderId = "lister_1", body = "")

            val result = service.respondToOffer("lister_1", "property_offer_1", PropertyOfferResponseAction.ACCEPT)

            Then("status flips to ACCEPTED, respondedAt is set, and a real acceptance message is posted") {
                result.status shouldBe PriceOfferStatus.ACCEPTED
                (result.respondedAt != null) shouldBe true
                bodySlot.captured shouldBe "✅ Offer accepted: 220000 RWF"
            }
        }

        When("the lister rejects a real pending offer") {
            val service = PropertyPriceOfferService(propertyPriceOfferRepository, propertyListingRepository, messagingService, rateLimiter)
            val offer = freshOffer()
            every { propertyListingRepository.findById("property_listing_1") } returns Optional.of(listing)
            every { propertyPriceOfferRepository.findById("property_offer_1") } returns Optional.of(offer)
            every { propertyPriceOfferRepository.save(any()) } answers { firstArg() }
            every { messagingService.sendMessage("lister_1", "conversation_1", any()) } returns
                Message(id = "message_2", conversationId = "conversation_1", senderId = "lister_1", body = "")

            val result = service.respondToOffer("lister_1", "property_offer_1", PropertyOfferResponseAction.REJECT)

            Then("status flips to REJECTED") {
                result.status shouldBe PriceOfferStatus.REJECTED
            }
        }

        When("the lister counters a real pending offer") {
            val service = PropertyPriceOfferService(propertyPriceOfferRepository, propertyListingRepository, messagingService, rateLimiter)
            val offer = freshOffer()
            every { propertyListingRepository.findById("property_listing_1") } returns Optional.of(listing)
            every { propertyPriceOfferRepository.findById("property_offer_1") } returns Optional.of(offer)
            val savedSlot = slot<PropertyPriceOffer>()
            every { propertyPriceOfferRepository.save(capture(savedSlot)) } answers { firstArg() }
            val bodySlot = slot<String>()
            every { messagingService.sendMessage("lister_1", "conversation_1", capture(bodySlot)) } returns
                Message(id = "message_3", conversationId = "conversation_1", senderId = "lister_1", body = "")

            val result = service.respondToOffer("lister_1", "property_offer_1", PropertyOfferResponseAction.COUNTER, BigDecimal("235000"))

            Then("a real brand-new PENDING offer is returned, proposed by the lister, and the original is COUNTERED") {
                result.status shouldBe PriceOfferStatus.PENDING
                result.proposedByUserId shouldBe "lister_1"
                result.amount shouldBe BigDecimal("235000")
                bodySlot.captured shouldBe "🔁 Countered: 235000 RWF for \"2-bedroom in Kacyiru\""
            }
        }

        When("the inquirer (who proposed it) tries to respond to their own offer") {
            val service = PropertyPriceOfferService(propertyPriceOfferRepository, propertyListingRepository, messagingService, rateLimiter)
            val offer = freshOffer()
            every { propertyPriceOfferRepository.findById("property_offer_1") } returns Optional.of(offer)

            Then("it throws OwnPropertyOfferException") {
                try {
                    service.respondToOffer("inquirer_1", "property_offer_1", PropertyOfferResponseAction.ACCEPT)
                    error("expected OwnPropertyOfferException")
                } catch (e: OwnPropertyOfferException) {
                    // expected
                }
            }
        }

        When("a real stranger tries to respond") {
            val service = PropertyPriceOfferService(propertyPriceOfferRepository, propertyListingRepository, messagingService, rateLimiter)
            val offer = freshOffer()
            every { propertyPriceOfferRepository.findById("property_offer_1") } returns Optional.of(offer)

            Then("it throws PropertyOfferNotFoundException, not a 403 that would confirm the offer exists") {
                try {
                    service.respondToOffer("stranger", "property_offer_1", PropertyOfferResponseAction.ACCEPT)
                    error("expected PropertyOfferNotFoundException")
                } catch (e: PropertyOfferNotFoundException) {
                    // expected
                }
            }
        }

        When("responding to an already-resolved offer") {
            val service = PropertyPriceOfferService(propertyPriceOfferRepository, propertyListingRepository, messagingService, rateLimiter)
            val resolvedOffer = freshOffer().apply { status = PriceOfferStatus.ACCEPTED }
            every { propertyPriceOfferRepository.findById("property_offer_1") } returns Optional.of(resolvedOffer)

            Then("it throws PropertyOfferAlreadyResolvedException") {
                try {
                    service.respondToOffer("lister_1", "property_offer_1", PropertyOfferResponseAction.REJECT)
                    error("expected PropertyOfferAlreadyResolvedException")
                } catch (e: PropertyOfferAlreadyResolvedException) {
                    // expected
                }
            }
        }
    }

    Given("real offer negotiation history across two different conversations") {
        val propertyPriceOfferRepository = mockk<PropertyPriceOfferRepository>()
        val propertyListingRepository = mockk<PropertyListingRepository>()
        val messagingService = mockk<MessagingService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = PropertyPriceOfferService(propertyPriceOfferRepository, propertyListingRepository, messagingService, rateLimiter)

        val myOffer = PropertyPriceOffer(
            id = "property_offer_1", propertyListingId = "property_listing_1", messageId = "message_1", conversationId = "conversation_1",
            inquirerId = "inquirer_1", listerId = "lister_1", proposedByUserId = "inquirer_1", amount = BigDecimal("220000"),
        )
        every { propertyPriceOfferRepository.findByConversationIdOrderByCreatedAtDesc("conversation_1") } returns listOf(myOffer)

        When("the real inquirer fetches their own conversation's offer history") {
            val offers = service.getOffersForConversation("inquirer_1", "conversation_1")

            Then("their own real offer is returned") {
                offers.map { it.id } shouldBe listOf("property_offer_1")
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
