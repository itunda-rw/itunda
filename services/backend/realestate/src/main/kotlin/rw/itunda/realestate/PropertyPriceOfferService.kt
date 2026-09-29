package rw.itunda.realestate

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.PriceOfferStatus
import rw.itunda.core.domain.PropertyListingStatus
import rw.itunda.core.domain.PropertyPriceOffer
import rw.itunda.core.format.formatAmount
import rw.itunda.core.repository.PropertyListingRepository
import rw.itunda.core.repository.PropertyPriceOfferRepository
import rw.itunda.messaging.MessagingService
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID

class PropertyOfferNotFoundException(message: String) : RuntimeException(message)
class InvalidPropertyOfferAmountException(message: String) : RuntimeException(message)
class PropertyOfferAlreadyResolvedException(message: String) : RuntimeException(message)
class OwnPropertyOfferException(message: String) : RuntimeException(message)

enum class PropertyOfferResponseAction { ACCEPT, REJECT, COUNTER }

/**
 * A real 당근-style price offer negotiation on a real `PropertyListing` -- mirrors
 * `rw.itunda.marketplace.PriceOfferService`'s exact design (see `PropertyPriceOffer`'s
 * own doc comment for why real estate, unlike Jobs, genuinely warrants this). Reuses the
 * real 1:1 conversation `PropertyListingService.contactLister` already establishes.
 */
@Service
class PropertyPriceOfferService(
    private val propertyPriceOfferRepository: PropertyPriceOfferRepository,
    private val propertyListingRepository: PropertyListingRepository,
    private val messagingService: MessagingService,
    private val rateLimiter: RateLimiter,
) {
    @Transactional
    fun makeOffer(inquirerId: String, propertyListingId: String, amount: BigDecimal): PropertyPriceOffer {
        if (amount <= BigDecimal.ZERO) throw InvalidPropertyOfferAmountException("Offer amount must be greater than zero")
        val listing = propertyListingRepository.findById(propertyListingId)
            .orElseThrow { PropertyListingNotFoundException("Property listing not found") }
        if (listing.status != PropertyListingStatus.AVAILABLE) throw PropertyListingNotAvailableException("This listing is no longer available")
        if (listing.listerId == inquirerId) throw OwnPropertyListingException("This is your own listing")

        // Real anti-spam limit, same convention Marketplace's own price offers use.
        rateLimiter.checkLimit("realestate:offer:$inquirerId", limit = 20, window = Duration.ofHours(1))

        val conversation = messagingService.startOrGetConversation(inquirerId, listing.listerId)
        val message = messagingService.sendMessage(inquirerId, conversation.id, formatOfferBody(amount, listing.title))
        return propertyPriceOfferRepository.save(
            PropertyPriceOffer(
                id = "property_offer_${UUID.randomUUID()}", propertyListingId = listing.id, messageId = message.id,
                conversationId = conversation.id, inquirerId = inquirerId, listerId = listing.listerId,
                proposedByUserId = inquirerId, amount = amount,
            ),
        )
    }

    // Real accept/reject/counter -- only the participant who did NOT propose the current
    // pending amount may respond, same IDOR discipline as PriceOfferService.respondToOffer.
    @Transactional
    fun respondToOffer(userId: String, offerId: String, action: PropertyOfferResponseAction, counterAmount: BigDecimal? = null): PropertyPriceOffer {
        val offer = propertyPriceOfferRepository.findById(offerId).orElseThrow { PropertyOfferNotFoundException("Offer not found") }
        if (userId != offer.inquirerId && userId != offer.listerId) throw PropertyOfferNotFoundException("Offer not found")
        if (offer.status != PriceOfferStatus.PENDING) throw PropertyOfferAlreadyResolvedException("This offer has already been resolved")
        if (userId == offer.proposedByUserId) throw OwnPropertyOfferException("You can't respond to your own offer")

        rateLimiter.checkLimit("realestate:offer-response:$userId", limit = 30, window = Duration.ofHours(1))

        val listing = propertyListingRepository.findById(offer.propertyListingId)
            .orElseThrow { PropertyListingNotFoundException("Property listing not found") }
        val now = Instant.now()
        return when (action) {
            PropertyOfferResponseAction.ACCEPT -> {
                offer.status = PriceOfferStatus.ACCEPTED
                offer.respondedAt = now
                messagingService.sendMessage(userId, offer.conversationId, "✅ Offer accepted: ${formatAmount(offer.amount)} RWF")
                propertyPriceOfferRepository.save(offer)
            }
            PropertyOfferResponseAction.REJECT -> {
                offer.status = PriceOfferStatus.REJECTED
                offer.respondedAt = now
                messagingService.sendMessage(userId, offer.conversationId, "❌ Offer declined: ${formatAmount(offer.amount)} RWF")
                propertyPriceOfferRepository.save(offer)
            }
            PropertyOfferResponseAction.COUNTER -> {
                val newAmount = counterAmount ?: throw InvalidPropertyOfferAmountException("counterAmount is required for a counter-offer")
                if (newAmount <= BigDecimal.ZERO) throw InvalidPropertyOfferAmountException("Offer amount must be greater than zero")
                offer.status = PriceOfferStatus.COUNTERED
                offer.respondedAt = now
                propertyPriceOfferRepository.save(offer)
                val message = messagingService.sendMessage(userId, offer.conversationId, formatCounterBody(newAmount, listing.title))
                propertyPriceOfferRepository.save(
                    PropertyPriceOffer(
                        id = "property_offer_${UUID.randomUUID()}", propertyListingId = offer.propertyListingId, messageId = message.id,
                        conversationId = offer.conversationId, inquirerId = offer.inquirerId, listerId = offer.listerId,
                        proposedByUserId = userId, amount = newAmount,
                    ),
                )
            }
        }
    }

    // Real per-thread negotiation history -- same real IDOR guard PriceOfferService's own
    // getOffersForConversation already established (an honest empty list for a
    // non-participant, not a 404 that would confirm the conversation exists).
    fun getOffersForConversation(userId: String, conversationId: String): List<PropertyPriceOffer> =
        propertyPriceOfferRepository.findByConversationIdOrderByCreatedAtDesc(conversationId)
            .filter { it.inquirerId == userId || it.listerId == userId }

    private fun formatOfferBody(amount: BigDecimal, listingTitle: String): String = "💰 Offered ${formatAmount(amount)} RWF for \"$listingTitle\""
    private fun formatCounterBody(amount: BigDecimal, listingTitle: String): String = "🔁 Countered: ${formatAmount(amount)} RWF for \"$listingTitle\""
}
