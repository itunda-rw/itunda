package rw.itunda.marketplace

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.ListingStatus
import rw.itunda.core.domain.PriceOffer
import rw.itunda.core.domain.PriceOfferStatus
import rw.itunda.core.format.formatAmount
import rw.itunda.core.repository.ListingRepository
import rw.itunda.core.repository.PriceOfferRepository
import rw.itunda.messaging.MessagingService
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID

class PriceOfferNotFoundException(message: String) : RuntimeException(message)
class InvalidOfferAmountException(message: String) : RuntimeException(message)
class OfferAlreadyResolvedException(message: String) : RuntimeException(message)
class OwnOfferException(message: String) : RuntimeException(message)

enum class OfferResponseAction { ACCEPT, REJECT, COUNTER }

/**
 * A real 당근마켓-style price offer -- the last item on the Talk polish roadmap
 * ("당근-style 'message seller with a price offer' negotiation"). Reuses the real 1:1
 * conversation `MarketplaceService.contactSeller` already establishes between a buyer
 * and a listing's seller, and posts each real offer/counter/accept/reject as a real
 * chat [rw.itunda.core.domain.Message] in that same thread -- see [PriceOffer]'s own
 * doc comment for why this is the honest, minimal design (no new chat surface, no
 * automatic money movement or listing-status change).
 */
@Service
class PriceOfferService(
    private val priceOfferRepository: PriceOfferRepository,
    private val listingRepository: ListingRepository,
    private val messagingService: MessagingService,
    private val rateLimiter: RateLimiter,
) {
    @Transactional
    fun makeOffer(buyerId: String, listingId: String, amount: BigDecimal): PriceOffer {
        if (amount <= BigDecimal.ZERO) throw InvalidOfferAmountException("Offer amount must be greater than zero")
        val listing = listingRepository.findById(listingId).orElseThrow { ListingNotFoundException("Listing not found") }
        if (listing.status != ListingStatus.ACTIVE) throw ListingNotActiveException("This listing is no longer active")
        if (listing.sellerId == buyerId) throw OwnListingException("This is your own listing")

        // Real anti-spam limit -- same convention every other user-generated
        // content/negotiation endpoint in this codebase already has (Partner SDK,
        // Certificate, chargeCard, messaging, listing creation itself).
        rateLimiter.checkLimit("marketplace:offer:$buyerId", limit = 20, window = Duration.ofHours(1))

        val conversation = messagingService.startOrGetConversation(buyerId, listing.sellerId)
        val message = messagingService.sendMessage(buyerId, conversation.id, formatOfferBody(amount, listing.title))
        return priceOfferRepository.save(
            PriceOffer(
                id = "price_offer_${UUID.randomUUID()}", listingId = listing.id, messageId = message.id, conversationId = conversation.id,
                buyerId = buyerId, sellerId = listing.sellerId, proposedByUserId = buyerId, amount = amount,
            ),
        )
    }

    // Real accept/reject/counter -- only the participant who did NOT propose the
    // current pending amount may respond (a buyer can't accept their own offer), same
    // "don't reveal/act on a resource you don't own" IDOR discipline as everywhere else
    // in this codebase: a non-participant gets a 404, not a 403.
    @Transactional
    fun respondToOffer(userId: String, offerId: String, action: OfferResponseAction, counterAmount: BigDecimal? = null): PriceOffer {
        val offer = priceOfferRepository.findById(offerId).orElseThrow { PriceOfferNotFoundException("Offer not found") }
        if (userId != offer.buyerId && userId != offer.sellerId) throw PriceOfferNotFoundException("Offer not found")
        if (offer.status != PriceOfferStatus.PENDING) throw OfferAlreadyResolvedException("This offer has already been resolved")
        if (userId == offer.proposedByUserId) throw OwnOfferException("You can't respond to your own offer")

        rateLimiter.checkLimit("marketplace:offer-response:$userId", limit = 30, window = Duration.ofHours(1))

        val listing = listingRepository.findById(offer.listingId).orElseThrow { ListingNotFoundException("Listing not found") }
        val now = Instant.now()
        return when (action) {
            OfferResponseAction.ACCEPT -> {
                offer.status = PriceOfferStatus.ACCEPTED
                offer.respondedAt = now
                messagingService.sendMessage(userId, offer.conversationId, "✅ Offer accepted: ${formatAmount(offer.amount)} RWF")
                priceOfferRepository.save(offer)
            }
            OfferResponseAction.REJECT -> {
                offer.status = PriceOfferStatus.REJECTED
                offer.respondedAt = now
                messagingService.sendMessage(userId, offer.conversationId, "❌ Offer declined: ${formatAmount(offer.amount)} RWF")
                priceOfferRepository.save(offer)
            }
            OfferResponseAction.COUNTER -> {
                val newAmount = counterAmount ?: throw InvalidOfferAmountException("counterAmount is required for a counter-offer")
                if (newAmount <= BigDecimal.ZERO) throw InvalidOfferAmountException("Offer amount must be greater than zero")
                offer.status = PriceOfferStatus.COUNTERED
                offer.respondedAt = now
                priceOfferRepository.save(offer)
                val message = messagingService.sendMessage(userId, offer.conversationId, formatCounterBody(newAmount, listing.title))
                priceOfferRepository.save(
                    PriceOffer(
                        id = "price_offer_${UUID.randomUUID()}", listingId = offer.listingId, messageId = message.id,
                        conversationId = offer.conversationId, buyerId = offer.buyerId, sellerId = offer.sellerId,
                        proposedByUserId = userId, amount = newAmount,
                    ),
                )
            }
        }
    }

    // Real per-thread negotiation history, for rendering offer bubbles inline in a real
    // conversation -- filtered to rows where the caller is genuinely the buyer or seller
    // (a real IDOR guard: `conversationId` alone isn't a safe scope by itself, since a
    // caller could pass any real conversation id belonging to two other people; every
    // legitimate offer row already has buyerId/sellerId set to that conversation's own
    // two participants, so this filter returns an honest empty list for a non-participant
    // rather than a 404 that would confirm the conversation exists).
    fun getOffersForConversation(userId: String, conversationId: String): List<PriceOffer> =
        priceOfferRepository.findByConversationIdOrderByCreatedAtDesc(conversationId)
            .filter { it.buyerId == userId || it.sellerId == userId }

    private fun formatOfferBody(amount: BigDecimal, listingTitle: String): String = "💰 Offered ${formatAmount(amount)} RWF for \"$listingTitle\""
    private fun formatCounterBody(amount: BigDecimal, listingTitle: String): String = "🔁 Countered: ${formatAmount(amount)} RWF for \"$listingTitle\""
}
