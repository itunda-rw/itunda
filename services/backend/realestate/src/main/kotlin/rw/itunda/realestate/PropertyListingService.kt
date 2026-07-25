package rw.itunda.realestate

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Conversation
import rw.itunda.core.domain.PropertyListing
import rw.itunda.core.domain.PropertyListingStatus
import rw.itunda.core.domain.PropertyListingType
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.geo.NominatimGeocodingClient
import rw.itunda.core.repository.PropertyListingRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.trust.TrustScoreService
import rw.itunda.messaging.MessagingService
import rw.itunda.messaging.SelfConversationException
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID

class PropertyListingNotFoundException(message: String) : RuntimeException(message)
class InvalidPropertyListingException(message: String) : RuntimeException(message)
class PropertyListingNotAvailableException(message: String) : RuntimeException(message)
class OwnPropertyListingException(message: String) : RuntimeException(message)
class InvalidPropertyCoordinatesException(message: String) : RuntimeException(message)
class RealEstateNeighborhoodNotSetException(message: String) : RuntimeException(message)
class CounterpartyNotFoundException(message: String) : RuntimeException(message)

data class PropertyType(val id: String, val label: String)

/**
 * A real 당근부동산 (Danggeun/Karrot "Real Estate")-style property board -- see
 * `PropertyListing`'s own doc comment for why this is its own entity. This closes the
 * third and last of the three explicitly-named 당근-style neighborhood-services
 * products.
 *
 * v1, honestly scoped, mirroring `JobPostService`'s own v1 shape: real listings, real
 * listing-type (sale/rent) + property-type browse filters (independently combinable),
 * a real opt-in Haversine "near me" browse, real "message lister" (reusing
 * `MessagingService` unmodified, same as Marketplace/Jobs), real lister-only
 * mark-taken/remove. No price-offer negotiation yet -- a real, named follow-up, matching
 * how Marketplace's own price-offers shipped as a later addition, not required for a
 * usable v1.
 */
@Service
class PropertyListingService(
    private val propertyListingRepository: PropertyListingRepository,
    private val rateLimiter: RateLimiter,
    private val messagingService: MessagingService,
    private val nominatimGeocodingClient: NominatimGeocodingClient,
    private val userRepository: UserRepository,
    private val trustScoreService: TrustScoreService,
) {
    companion object {
        val PROPERTY_TYPES = listOf(
            PropertyType("apartment", "Apartment"),
            PropertyType("house", "House"),
            PropertyType("room", "Room"),
            PropertyType("land", "Land"),
            PropertyType("commercial", "Commercial"),
            PropertyType("other", "Other"),
        )
        private val PROPERTY_TYPE_IDS = PROPERTY_TYPES.map { it.id }.toSet()
    }

    private fun requireLister(listerId: String, propertyListingId: String): PropertyListing {
        val listing = propertyListingRepository.findById(propertyListingId)
            .orElseThrow { PropertyListingNotFoundException("Property listing not found") }
        if (listing.listerId != listerId) {
            // Same "don't reveal a resource exists to someone who shouldn't act on it"
            // discipline MarketplaceService.requireOwner/JobPostService.requirePoster
            // already established.
            throw PropertyListingNotFoundException("Property listing not found")
        }
        return listing
    }

    @Transactional
    fun createListing(
        listerId: String,
        listingType: PropertyListingType,
        propertyType: String,
        title: String,
        description: String,
        price: BigDecimal,
        bedrooms: Int? = null,
        sizeSqm: Double? = null,
        latitude: Double? = null,
        longitude: Double? = null,
    ): PropertyListing {
        val trimmedTitle = title.trim()
        val trimmedDescription = description.trim()
        if (trimmedTitle.isEmpty() || trimmedDescription.isEmpty()) {
            throw InvalidPropertyListingException("Title and description are both required")
        }
        // Real bound, matching `deliveryAddress`'s own fix on the Eats/Commerce rows
        // the same day -- `title`/`description` are VARCHAR(200)/VARCHAR(2000), and
        // this DB's real STRICT_TRANS_TABLES mode throws a raw, unhandled 500 on an
        // over-length insert rather than truncating.
        if (trimmedTitle.length > 200 || trimmedDescription.length > 2000) {
            throw InvalidPropertyListingException("Title must be 200 characters or fewer, description 2000 or fewer")
        }
        if (propertyType !in PROPERTY_TYPE_IDS) {
            throw InvalidPropertyListingException("Unknown property type")
        }
        if (price <= BigDecimal.ZERO) {
            throw InvalidPropertyListingException("Price must be greater than zero")
        }
        if (bedrooms != null && bedrooms < 0) {
            throw InvalidPropertyListingException("Bedrooms cannot be negative")
        }
        if (sizeSqm != null && sizeSqm <= 0) {
            throw InvalidPropertyListingException("Size must be greater than zero")
        }
        if ((latitude == null) != (longitude == null)) {
            throw InvalidPropertyCoordinatesException("Both latitude and longitude are required together")
        }
        if (latitude != null && longitude != null && !GeoUtils.isValidCoordinate(latitude, longitude)) {
            throw InvalidPropertyCoordinatesException("Latitude must be between -90 and 90, longitude between -180 and 180")
        }
        // Real anti-spam limit, same 10/hour convention Marketplace/Community/Jobs
        // already established.
        rateLimiter.checkLimit("realestate:listing:$listerId", limit = 10, window = Duration.ofHours(1))

        // Real hyperlocal neighborhood (2026-07-20) -- see MarketplaceService.
        // createListing's own doc comment for the full account; identical here.
        val neighborhood = if (latitude != null && longitude != null) {
            nominatimGeocodingClient.reverseGeocode(latitude, longitude)
        } else {
            userRepository.findById(listerId).orElse(null)?.neighborhood
        }

        return propertyListingRepository.save(
            PropertyListing(
                id = "property_listing_${UUID.randomUUID()}", listerId = listerId, listingType = listingType,
                propertyType = propertyType, title = trimmedTitle, description = trimmedDescription, price = price,
                bedrooms = bedrooms, sizeSqm = sizeSqm, latitude = latitude, longitude = longitude,
                neighborhood = neighborhood,
            ),
        )
    }

    fun browse(pageable: Pageable, listingType: PropertyListingType?, propertyType: String?): Page<PropertyListing> =
        when {
            listingType != null && propertyType != null ->
                propertyListingRepository.findByStatusAndListingTypeAndPropertyTypeOrderByCreatedAtDesc(
                    PropertyListingStatus.AVAILABLE, listingType, propertyType, pageable,
                )
            listingType != null ->
                propertyListingRepository.findByStatusAndListingTypeOrderByCreatedAtDesc(PropertyListingStatus.AVAILABLE, listingType, pageable)
            propertyType != null ->
                propertyListingRepository.findByStatusAndPropertyTypeOrderByCreatedAtDesc(PropertyListingStatus.AVAILABLE, propertyType, pageable)
            else -> propertyListingRepository.findByStatusOrderByCreatedAtDesc(PropertyListingStatus.AVAILABLE, pageable)
        }

    fun getMyListings(listerId: String, pageable: Pageable): Page<PropertyListing> =
        propertyListingRepository.findByListerIdOrderByCreatedAtDesc(listerId, pageable)

    // Real "Places I got" (2026-07-25) -- see PropertyListingRepository.
    // findByCounterpartyIdOrderByCreatedAtDesc's own doc comment for the full account.
    fun getMyAcquiredListings(counterpartyId: String, pageable: Pageable): Page<PropertyListing> =
        propertyListingRepository.findByCounterpartyIdOrderByCreatedAtDesc(counterpartyId, pageable)

    // Real hyperlocal "my neighborhood" browse (2026-07-20) -- see MarketplaceService.
    // myNeighborhood's own doc comment for the full account. See
    // PropertyListingRepository's own note on why this isn't combined with
    // listingType/propertyType this pass.
    fun myNeighborhood(callerUserId: String, pageable: Pageable): Page<PropertyListing> {
        val caller = userRepository.findById(callerUserId).orElseThrow { PropertyListingNotFoundException("User not found") }
        val neighborhood = caller.neighborhood
            ?: throw RealEstateNeighborhoodNotSetException("Set your neighborhood first via POST /api/v1/auth/profile/neighborhood")
        return propertyListingRepository.findByStatusAndNeighborhoodOrderByCreatedAtDesc(PropertyListingStatus.AVAILABLE, neighborhood, pageable)
    }

    // Real opt-in "near me" browse -- location matters a lot for real estate, same shape
    // MarketplaceService.nearby/CommunityService.nearby/JobPostService.nearby already use.
    fun nearby(latitude: Double, longitude: Double, radiusKm: Double, pageable: Pageable): Page<PropertyListing> {
        if (!GeoUtils.isValidCoordinate(latitude, longitude)) {
            throw InvalidPropertyCoordinatesException("Latitude must be between -90 and 90, longitude between -180 and 180")
        }
        if (radiusKm <= 0.0) {
            throw InvalidPropertyCoordinatesException("radiusKm must be greater than zero")
        }
        val sorted = propertyListingRepository.findByStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(PropertyListingStatus.AVAILABLE)
            .map { it to GeoUtils.haversineKm(latitude, longitude, it.latitude!!, it.longitude!!) }
            .filter { (_, distanceKm) -> distanceKm <= radiusKm }
            .sortedBy { (_, distanceKm) -> distanceKm }
            .map { (listing, _) -> listing }

        val start = (pageable.pageNumber * pageable.pageSize).coerceAtMost(sorted.size)
        val end = (start + pageable.pageSize).coerceAtMost(sorted.size)
        return PageImpl(sorted.subList(start, end), pageable, sorted.size.toLong())
    }

    // Any status, not just AVAILABLE -- a buyer/renter who already messaged about a
    // now-TAKEN property should still be able to open it, matching Marketplace's own
    // getListing precedent.
    fun getListing(propertyListingId: String): PropertyListing =
        propertyListingRepository.findById(propertyListingId).orElseThrow { PropertyListingNotFoundException("Property listing not found") }

    // Real optional buyer/tenant identification (2026-07-24) -- see MarketplaceService.
    // markSold's own doc comment for the full account; identical shape here.
    @Transactional
    fun markTaken(listerId: String, propertyListingId: String, counterpartyPhoneNumber: String? = null): PropertyListing {
        val listing = requireLister(listerId, propertyListingId)
        if (listing.status != PropertyListingStatus.AVAILABLE) {
            throw PropertyListingNotAvailableException("Only an available listing can be marked taken")
        }
        val trimmedPhone = counterpartyPhoneNumber?.trim()
        if (!trimmedPhone.isNullOrEmpty()) {
            val counterparty = userRepository.findByPhoneNumber(trimmedPhone)
                ?: throw CounterpartyNotFoundException("No itunda account found for this phone number")
            if (counterparty.id == listerId) throw OwnPropertyListingException("You can't record yourself as the buyer/tenant")
            listing.counterpartyId = counterparty.id
        }
        listing.status = PropertyListingStatus.TAKEN
        val saved = propertyListingRepository.save(listing)
        // Real Karrot-Score-style trust badge (2026-07-21) -- see TrustScoreService's own
        // doc comment; identical shape to MarketplaceService.markSold/JobPostService.markFilled.
        trustScoreService.computeScore(listerId)
        // Real review-prompt system message (2026-07-25) -- see
        // MarketplaceService.markSold's own doc comment for the full sourced account;
        // identical shape here.
        listing.counterpartyId?.let { counterpartyId ->
            val conversation = messagingService.startOrGetConversation(listerId, counterpartyId)
            messagingService.sendMessage(
                listerId, conversation.id,
                "✅ Marked \"${listing.title}\" as taken. If everything went well, leave a review so other neighbors know what to expect!",
            )
        }
        return saved
    }

    @Transactional
    fun removeListing(listerId: String, propertyListingId: String): PropertyListing {
        val listing = requireLister(listerId, propertyListingId)
        listing.status = PropertyListingStatus.REMOVED
        return propertyListingRepository.save(listing)
    }

    /** Real "message lister" -- reuses `MessagingService.startOrGetConversation`
     * completely unmodified, the exact same discipline `MarketplaceService.contactSeller`/
     * `JobPostService.contactPoster` already established. */
    fun contactLister(inquirerId: String, propertyListingId: String): Conversation {
        val listing = getListing(propertyListingId)
        try {
            return messagingService.startOrGetConversation(inquirerId, listing.listerId)
        } catch (e: SelfConversationException) {
            throw OwnPropertyListingException("This is your own property listing")
        }
    }
}
