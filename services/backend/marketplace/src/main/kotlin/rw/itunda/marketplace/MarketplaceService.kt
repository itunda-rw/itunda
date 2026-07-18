package rw.itunda.marketplace

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Conversation
import rw.itunda.core.domain.Listing
import rw.itunda.core.domain.ListingStatus
import rw.itunda.core.geo.GeoUtils
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
class InvalidCoordinatesException(message: String) : RuntimeException(message)

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
    fun createListing(
        sellerId: String,
        title: String,
        description: String,
        price: BigDecimal,
        category: String,
        latitude: Double? = null,
        longitude: Double? = null,
    ): Listing {
        val trimmedTitle = title.trim()
        val trimmedDescription = description.trim()
        val trimmedCategory = category.trim()
        if (trimmedTitle.isEmpty() || trimmedDescription.isEmpty() || trimmedCategory.isEmpty()) {
            throw InvalidListingException("Title, description, and category are all required")
        }
        if (price <= BigDecimal.ZERO) {
            throw InvalidListingException("Price must be greater than zero")
        }
        // Real optional location (2026-07-18) -- see this class's own doc comment on why
        // it's no longer honestly out of reach. Both-or-neither, never a fabricated pair.
        if ((latitude == null) != (longitude == null)) {
            throw InvalidCoordinatesException("Both latitude and longitude are required together")
        }
        if (latitude != null && longitude != null && !GeoUtils.isValidCoordinate(latitude, longitude)) {
            throw InvalidCoordinatesException("Latitude must be between -90 and 90, longitude between -180 and 180")
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
                latitude = latitude, longitude = longitude,
            ),
        )
    }

    // Real proximity search (2026-07-18) -- the hyperlocal-discovery gap this class's own
    // doc comment originally named as impossible without real location data, now closed
    // for listings that have set one. Distance computed via GeoUtils.haversineKm over the
    // bounded set of ACTIVE listings that have coordinates (see ListingRepository's own
    // note on why this is in-app, not a real geospatial DB index, at current scale).
    fun nearby(latitude: Double, longitude: Double, radiusKm: Double, pageable: Pageable): Page<Listing> {
        if (!GeoUtils.isValidCoordinate(latitude, longitude)) {
            throw InvalidCoordinatesException("Latitude must be between -90 and 90, longitude between -180 and 180")
        }
        if (radiusKm <= 0.0) {
            throw InvalidCoordinatesException("radiusKm must be greater than zero")
        }
        val candidates = listingRepository.findByStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(ListingStatus.ACTIVE)
        val sorted = candidates
            .map { it to GeoUtils.haversineKm(latitude, longitude, it.latitude!!, it.longitude!!) }
            .filter { (_, distanceKm) -> distanceKm <= radiusKm }
            .sortedBy { (_, distanceKm) -> distanceKm }
            .map { (listing, _) -> listing }

        val start = (pageable.pageNumber * pageable.pageSize).coerceAtMost(sorted.size)
        val end = (start + pageable.pageSize).coerceAtMost(sorted.size)
        return PageImpl(sorted.subList(start, end), pageable, sorted.size.toLong())
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
