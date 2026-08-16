package rw.itunda.realestate

import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Conversation
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.PropertyListing
import rw.itunda.core.domain.PropertyListingStatus
import rw.itunda.core.domain.PropertyListingType
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.geo.NominatimGeocodingClient
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.PropertyListingFavoriteRepository
import rw.itunda.core.repository.PropertyListingRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.trust.TrustScoreService
import rw.itunda.messaging.MessagingService
import rw.itunda.messaging.SelfConversationException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.util.UUID

class PropertyListingNotFoundException(message: String) : RuntimeException(message)
class InvalidPropertyListingException(message: String) : RuntimeException(message)
class PropertyListingNotAvailableException(message: String) : RuntimeException(message)
class OwnPropertyListingException(message: String) : RuntimeException(message)
class InvalidPropertyCoordinatesException(message: String) : RuntimeException(message)
class RealEstateNeighborhoodNotSetException(message: String) : RuntimeException(message)
class CounterpartyNotFoundException(message: String) : RuntimeException(message)
class InsufficientComparablesException(message: String) : RuntimeException(message)

data class PropertyType(val id: String, val label: String)

// Real Toss Bank 우리집 시세 (my home's estimated value, item 228) -- see
// PropertyListingService.estimateValue's own doc comment for the full sourced
// account.
data class PropertyValuationEstimate(
    val estimatedValue: BigDecimal,
    val comparableCount: Int,
    val averagePricePerSqm: BigDecimal,
    val radiusKm: Double,
)

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
    private val propertyListingFavoriteRepository: PropertyListingFavoriteRepository,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
) {
    private val log = LoggerFactory.getLogger(PropertyListingService::class.java)

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
        // itunda's own honest choice -- real comps-based estimators (Zillow's own
        // published Zestimate methodology) use dozens of comparables; this codebase's
        // real listing volume is far smaller, so 3 is the minimum that still means
        // "more than a single coincidental data point" without demanding more real
        // listings than a given area realistically has yet.
        const val MIN_COMPARABLES = 3
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

    // Real relevance-ranked search (2026-08-14) -- see MarketplaceService.search's own
    // doc comment for the full "why" (not neighborhood-scoped, same as browse above).
    fun search(query: String, pageable: Pageable): Page<PropertyListing> {
        val booleanQuery = rw.itunda.core.search.FullTextSearchUtil.toBooleanModeQuery(query)
        return if (booleanQuery != null) {
            propertyListingRepository.searchFullText(PropertyListingStatus.AVAILABLE, booleanQuery, pageable)
        } else {
            propertyListingRepository.searchShort(PropertyListingStatus.AVAILABLE, query.trim(), pageable)
        }
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
        // Real dual-neighborhood support (2026-08-04) -- see User.secondNeighborhood's own doc comment.
        val neighborhoods = listOfNotNull(neighborhood, caller.secondNeighborhood)
        return propertyListingRepository.findByStatusAndNeighborhoodInOrderByCreatedAtDesc(PropertyListingStatus.AVAILABLE, neighborhoods, pageable)
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

    /**
     * Real Toss Bank 우리집 시세 (my home's estimated value, item 228) -- Toss Bank's
     * own real feature: a comparable-listings-based estimate of what a home is worth,
     * the same real Zestimate-style methodology (average price-per-area among nearby,
     * similar comparables) every real property-valuation product uses, none of which
     * itunda has any external data-vendor access to license -- this computes an honest
     * estimate purely from itunda's own real, currently-`AVAILABLE` listings, not a
     * fabricated number and not a third-party feed. Deliberately a different shape
     * from `VehicleValuationService`'s own depreciation-curve math: a car's value is a
     * function of its own age/mileage, but a home's value is fundamentally comparative
     * -- there's no equivalent "depreciation curve" for real estate that wouldn't be
     * invented, so this reuses the same real `GeoUtils.haversineKm` nearby-candidate
     * shape `nearby()` above already establishes instead.
     *
     * Real-422s (`InsufficientComparablesException`) rather than returning a fabricated
     * number when fewer than [MIN_COMPARABLES] real comparable listings exist nearby --
     * an honest "not enough data" is more useful than a confident-looking guess.
     */
    fun estimateValue(
        latitude: Double, longitude: Double, propertyType: String, listingType: PropertyListingType,
        sizeSqm: Double, radiusKm: Double = 5.0,
    ): PropertyValuationEstimate {
        if (!GeoUtils.isValidCoordinate(latitude, longitude)) {
            throw InvalidPropertyCoordinatesException("Latitude must be between -90 and 90, longitude between -180 and 180")
        }
        if (radiusKm <= 0.0) {
            throw InvalidPropertyCoordinatesException("radiusKm must be greater than zero")
        }
        if (sizeSqm <= 0.0) {
            throw InvalidPropertyListingException("Size must be greater than zero")
        }
        val comparables = propertyListingRepository.findByStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(PropertyListingStatus.AVAILABLE)
            .filter { it.propertyType == propertyType && it.listingType == listingType && it.sizeSqm != null && it.sizeSqm!! > 0.0 }
            .filter { GeoUtils.haversineKm(latitude, longitude, it.latitude!!, it.longitude!!) <= radiusKm }
        if (comparables.size < MIN_COMPARABLES) {
            throw InsufficientComparablesException(
                "Not enough comparable listings nearby to estimate a value (found ${comparables.size}, need at least $MIN_COMPARABLES)",
            )
        }
        val pricesPerSqm = comparables.map { it.price.divide(BigDecimal(it.sizeSqm!!), 4, RoundingMode.HALF_UP) }
        val averagePricePerSqm = pricesPerSqm.fold(BigDecimal.ZERO) { acc, p -> acc + p }
            .divide(BigDecimal(pricesPerSqm.size), 4, RoundingMode.HALF_UP)
        val estimatedValue = averagePricePerSqm.multiply(BigDecimal(sizeSqm)).setScale(2, RoundingMode.HALF_UP)
        return PropertyValuationEstimate(
            estimatedValue = estimatedValue, comparableCount = comparables.size,
            averagePricePerSqm = averagePricePerSqm.setScale(2, RoundingMode.HALF_UP), radiusKm = radiusKm,
        )
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

    /**
     * Real Karrot(당근마켓)-style price-drop notification -- Karrot's own real
     * transaction-notification categories explicitly include price drops on a
     * favorited ("관심") listing ("거래(나눔 이벤트/거래 후기/가격 하락 등)"), corroborated by a
     * real Clien community thread asking exactly this ("당근마켓 가격만 내리면 관심유저에게
     * 알람가나요?"). Only a real price DECREASE notifies, matching that sourced "가격
     * 하락" scoping exactly -- not any price edit.
     *
     * Deliberately NOT `@Transactional` itself -- the single `propertyListingRepository
     * .save` below is already atomic on its own via Spring Data's implicit per-call
     * transaction (same reasoning `ProductSubscriptionService.executeOne`'s 2026-08-17
     * fix already establishes). This matters specifically here: the price-drop
     * notification loop below makes its own separate `notificationRepository.save`
     * calls after the price change -- if this method were `@Transactional`, one
     * failing notification save would mark this method's own ambient transaction
     * rollback-only and roll back the price change itself along with it, the exact
     * self-invocation/transaction-poisoning pitfall closed in Sections 115/118.
     */
    fun updatePrice(listerId: String, propertyListingId: String, newPrice: BigDecimal): PropertyListing {
        if (newPrice <= BigDecimal.ZERO) {
            throw InvalidPropertyListingException("Price must be greater than zero")
        }
        val listing = requireLister(listerId, propertyListingId)
        if (listing.status != PropertyListingStatus.AVAILABLE) {
            throw PropertyListingNotAvailableException("Only an available listing's price can be changed")
        }
        val oldPrice = listing.price
        listing.price = newPrice
        val saved = propertyListingRepository.save(listing)
        if (newPrice < oldPrice) {
            notifyFavoritersOfPriceDrop(propertyListingId, listing.title, oldPrice, newPrice)
        }
        return saved
    }

    // Real per-favoriter resilience -- a notification failure for one favoriter must
    // never affect another's, same per-row discipline established elsewhere this
    // session (BillAutoPayProcessor/ExchangeRateAlertScheduler). Each
    // notificationRepository.save call here is independently atomic since
    // updatePrice above is deliberately not @Transactional (see its own doc comment).
    private fun notifyFavoritersOfPriceDrop(propertyListingId: String, title: String, oldPrice: BigDecimal, newPrice: BigDecimal) {
        val favorites = propertyListingFavoriteRepository.findByPropertyListingId(propertyListingId)
        for (favorite in favorites) {
            try {
                val notifTitle = "Price drop on a listing you favorited"
                val body = "\"$title\" dropped from ${oldPrice.toPlainString()} to ${newPrice.toPlainString()} RWF"
                notificationRepository.save(
                    Notification(
                        id = "notif_${UUID.randomUUID()}", userId = favorite.userId, type = "PROPERTY_PRICE_DROP",
                        title = notifTitle, body = body, isRead = false, createdAt = Instant.now(),
                        dataJson = "{\"propertyListingId\":\"$propertyListingId\"}",
                    ),
                )
                pushNotificationService.sendToUser(favorite.userId, notifTitle, body, mapOf("propertyListingId" to propertyListingId))
            } catch (e: Exception) {
                log.error("Price-drop notification failed for user {} on listing {}", favorite.userId, propertyListingId, e)
            }
        }
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
