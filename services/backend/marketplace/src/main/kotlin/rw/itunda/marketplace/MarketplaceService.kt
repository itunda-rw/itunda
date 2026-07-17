package rw.itunda.marketplace

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Conversation
import rw.itunda.core.domain.Listing
import rw.itunda.core.domain.ListingStatus
import rw.itunda.core.repository.ListingRepository
import rw.itunda.messaging.MessagingService
import rw.itunda.messaging.SelfConversationException
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID

class ListingNotFoundException(message: String) : RuntimeException(message)
class ListingNotOwnedException(message: String) : RuntimeException(message)
class InvalidListingException(message: String) : RuntimeException(message)
class ListingNotActiveException(message: String) : RuntimeException(message)
class OwnListingException(message: String) : RuntimeException(message)

/**
 * A real 당근마켓 (Danggeun/Karrot Market)-style secondhand marketplace -- the second
 * of the three new "super app" phases named in the 2026-07-18 goal expansion, built
 * after Kakao-style messaging specifically so `contactSeller` below could reuse that
 * real 1:1 conversation primitive rather than inventing a second chat system.
 *
 * Honestly scoped -- see Listing.kt's own doc comment for the real, named limitation
 * this carries (no real location/proximity data exists anywhere in this backend, so
 * this is a real general marketplace, not real hyperlocal discovery).
 */
@Service
class MarketplaceService(
    private val listingRepository: ListingRepository,
    private val rateLimiter: RateLimiter,
    private val messagingService: MessagingService,
) {
    private fun requireOwner(sellerId: String, listingId: String): Listing {
        val listing = listingRepository.findById(listingId)
            .orElseThrow { ListingNotFoundException("Listing not found") }
        if (listing.sellerId != sellerId) {
            // Same "don't reveal a resource exists to someone who shouldn't act on it"
            // discipline MerchantProductService/PayrollService already established.
            throw ListingNotFoundException("Listing not found")
        }
        return listing
    }

    @Transactional
    fun createListing(sellerId: String, title: String, description: String, price: BigDecimal, category: String): Listing {
        val trimmedTitle = title.trim()
        val trimmedDescription = description.trim()
        val trimmedCategory = category.trim()
        if (trimmedTitle.isEmpty() || trimmedDescription.isEmpty() || trimmedCategory.isEmpty()) {
            throw InvalidListingException("Title, description, and category are all required")
        }
        if (price <= BigDecimal.ZERO) {
            throw InvalidListingException("Price must be greater than zero")
        }
        // Real anti-spam limit on user-generated listings -- same convention this
        // session's own security review already established for every other
        // content/money-creation endpoint (Partner SDK, Certificate, chargeCard,
        // messaging). 10/hour comfortably covers a real seller listing several items
        // in one sitting while bounding a spam-listing flood.
        rateLimiter.checkLimit("marketplace:create:$sellerId", limit = 10, window = Duration.ofHours(1))

        return listingRepository.save(
            Listing(
                id = "listing_${UUID.randomUUID()}", sellerId = sellerId, title = trimmedTitle,
                description = trimmedDescription, price = price, category = trimmedCategory,
            ),
        )
    }

    fun browse(pageable: Pageable, category: String?): Page<Listing> =
        if (category.isNullOrBlank()) {
            listingRepository.findByStatusOrderByCreatedAtDesc(ListingStatus.ACTIVE, pageable)
        } else {
            listingRepository.findByStatusAndCategoryOrderByCreatedAtDesc(ListingStatus.ACTIVE, category, pageable)
        }

    // Any status, not just ACTIVE -- a buyer who already contacted a seller about a
    // now-SOLD item should still be able to open the listing (real 당근마켓 shows a
    // "판매완료"/sold badge rather than 404ing it), and a seller needs to see their own
    // REMOVED listings too. `browse()` above is what actually hides non-ACTIVE ones
    // from general discovery.
    fun getListing(listingId: String): Listing =
        listingRepository.findById(listingId).orElseThrow { ListingNotFoundException("Listing not found") }

    fun getMyListings(sellerId: String, pageable: Pageable): Page<Listing> =
        listingRepository.findBySellerIdOrderByCreatedAtDesc(sellerId, pageable)

    @Transactional
    fun markSold(sellerId: String, listingId: String): Listing {
        val listing = requireOwner(sellerId, listingId)
        if (listing.status != ListingStatus.ACTIVE) {
            throw ListingNotActiveException("Only an active listing can be marked sold")
        }
        listing.status = ListingStatus.SOLD
        return listingRepository.save(listing)
    }

    @Transactional
    fun removeListing(sellerId: String, listingId: String): Listing {
        val listing = requireOwner(sellerId, listingId)
        listing.status = ListingStatus.REMOVED
        return listingRepository.save(listing)
    }

    /** Real "message seller" -- the entire reason messaging was built first this
     * session. Reuses `MessagingService.startOrGetConversation` completely unmodified,
     * the same "compose a real, already-proven service rather than duplicating its
     * logic" discipline `FacePayService` already established for `MerchantService`. */
    fun contactSeller(buyerId: String, listingId: String): Conversation {
        val listing = getListing(listingId)
        try {
            return messagingService.startOrGetConversation(buyerId, listing.sellerId)
        } catch (e: SelfConversationException) {
            throw OwnListingException("This is your own listing")
        }
    }
}
