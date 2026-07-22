package rw.itunda.marketplace.web

import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta
import rw.itunda.core.web.trustScores
import rw.itunda.marketplace.FavoriteListingNotFoundException
import rw.itunda.marketplace.InvalidCoordinatesException
import rw.itunda.marketplace.InvalidListingException
import rw.itunda.marketplace.InvalidOfferAmountException
import rw.itunda.marketplace.ListingFavoriteService
import rw.itunda.marketplace.ListingNotActiveException
import rw.itunda.marketplace.ListingNotFoundException
import rw.itunda.marketplace.MarketplaceService
import rw.itunda.marketplace.NeighborhoodNotSetException
import rw.itunda.marketplace.OfferAlreadyResolvedException
import rw.itunda.marketplace.OfferResponseAction
import rw.itunda.marketplace.OwnListingException
import rw.itunda.marketplace.OwnOfferException
import rw.itunda.marketplace.PriceOfferNotFoundException
import rw.itunda.marketplace.PriceOfferService
import java.math.BigDecimal

data class CreateListingRequest(
    val title: String,
    val description: String,
    val price: BigDecimal,
    val category: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val meetingPlace: String? = null,
)

data class MakeOfferRequest(val amount: BigDecimal)
data class RespondToOfferRequest(val action: OfferResponseAction, val counterAmount: BigDecimal? = null)

// Real 당근마켓-style marketplace -- see MarketplaceService's own doc comment. Normal
// itunda-user JWT gate (default SecurityConfig .anyRequest().authenticated()).
@RestController
@RequestMapping("/api/v1/marketplace")
class MarketplaceController(
    private val marketplaceService: MarketplaceService,
    private val priceOfferService: PriceOfferService,
    private val listingFavoriteService: ListingFavoriteService,
    private val userRepository: UserRepository,
) {

    @PostMapping("/listings")
    fun createListing(
        @RequestBody request: CreateListingRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val listing = marketplaceService.createListing(
            currentUser.userId, request.title, request.description, request.price, request.category,
            request.latitude, request.longitude, request.meetingPlace,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "listing" to listing))
    }

    @GetMapping("/listings")
    fun browse(
        @RequestParam(required = false) category: String?,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Map<String, Any?>> {
        val page = marketplaceService.browse(pageable, category)
        // Real Karrot-Score-style trust badge (2026-07-21) -- see trustScores' own doc
        // comment. One batch findAllById, not one query per listing's seller.
        val scores = trustScores(userRepository, page.content.map { it.sellerId })
        return ResponseEntity.ok(mapOf("success" to true, "listings" to page.content, "trustScores" to scores) + pageMeta(page))
    }

    // Real proximity search (2026-07-18) -- see MarketplaceService.nearby's own doc
    // comment. radiusKm defaults to 5km, a reasonable real walkable/boda-boda-trip
    // neighborhood radius for Rwanda's urban density.
    @GetMapping("/listings/nearby")
    fun nearby(
        @RequestParam latitude: Double,
        @RequestParam longitude: Double,
        @RequestParam(required = false, defaultValue = "5.0") radiusKm: Double,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Map<String, Any?>> {
        val page = marketplaceService.nearby(latitude, longitude, radiusKm, pageable)
        val scores = trustScores(userRepository, page.content.map { it.sellerId })
        return ResponseEntity.ok(mapOf("success" to true, "listings" to page.content, "trustScores" to scores) + pageMeta(page))
    }

    // Real hyperlocal "my neighborhood" browse (2026-07-20) -- see MarketplaceService.
    // myNeighborhood's own doc comment.
    @GetMapping("/listings/my-neighborhood")
    fun myNeighborhood(
        @RequestParam(required = false) category: String?,
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = marketplaceService.myNeighborhood(currentUser.userId, category, pageable)
        val scores = trustScores(userRepository, page.content.map { it.sellerId })
        return ResponseEntity.ok(mapOf("success" to true, "listings" to page.content, "trustScores" to scores) + pageMeta(page))
    }

    @GetMapping("/listings/{listingId}")
    fun getListing(@PathVariable listingId: String): ResponseEntity<Map<String, Any?>> {
        val listing = marketplaceService.getListing(listingId)
        val sellerTrustScore = trustScores(userRepository, listOf(listing.sellerId))[listing.sellerId]
        return ResponseEntity.ok(mapOf("success" to true, "listing" to listing, "sellerTrustScore" to sellerTrustScore))
    }

    @GetMapping("/my-listings")
    fun getMyListings(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = marketplaceService.getMyListings(currentUser.userId, pageable)
        val scores = trustScores(userRepository, page.content.map { it.sellerId })
        return ResponseEntity.ok(mapOf("success" to true, "listings" to page.content, "trustScores" to scores) + pageMeta(page))
    }

    @PostMapping("/listings/{listingId}/mark-sold")
    fun markSold(
        @PathVariable listingId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "listing" to marketplaceService.markSold(currentUser.userId, listingId)))

    @DeleteMapping("/listings/{listingId}")
    fun removeListing(
        @PathVariable listingId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "listing" to marketplaceService.removeListing(currentUser.userId, listingId)))

    // Real Marketplace listing wishlist (2026-07-21) -- see ListingFavoriteService's own
    // doc comment. Mirrors OrderController's own favorite-product endpoints field-for-field.
    @PostMapping("/listings/{listingId}/favorite")
    fun addFavorite(
        @PathVariable listingId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val favorite = listingFavoriteService.addFavorite(currentUser.userId, listingId)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "favorite" to favorite))
    }

    @DeleteMapping("/listings/{listingId}/favorite")
    fun removeFavorite(
        @PathVariable listingId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Boolean>> {
        listingFavoriteService.removeFavorite(currentUser.userId, listingId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @GetMapping("/listings/favorites")
    fun getMyFavoriteListings(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = listingFavoriteService.getMyFavorites(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "favorites" to page.content) + pageMeta(page))
    }

    @PostMapping("/listings/{listingId}/contact-seller")
    fun contactSeller(
        @PathVariable listingId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val conversation = marketplaceService.contactSeller(currentUser.userId, listingId)
        return ResponseEntity.ok(mapOf("success" to true, "conversation" to conversation))
    }

    // Real 당근-style price-offer negotiation (2026-07-19) -- see PriceOfferService's
    // own doc comment. Posts each offer/counter/accept/reject as a real message in the
    // buyer-seller conversation `contactSeller` already established.
    @PostMapping("/listings/{listingId}/offers")
    fun makeOffer(
        @PathVariable listingId: String,
        @RequestBody request: MakeOfferRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val offer = priceOfferService.makeOffer(currentUser.userId, listingId, request.amount)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "offer" to offer))
    }

    @PostMapping("/offers/{offerId}/respond")
    fun respondToOffer(
        @PathVariable offerId: String,
        @RequestBody request: RespondToOfferRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val offer = priceOfferService.respondToOffer(currentUser.userId, offerId, request.action, request.counterAmount)
        return ResponseEntity.ok(mapOf("success" to true, "offer" to offer))
    }

    // Real per-thread offer history, so a client can render offer bubbles inline in the
    // existing real conversation thread it's already fetching from :messaging.
    @GetMapping("/conversations/{conversationId}/offers")
    fun getOffersForConversation(
        @PathVariable conversationId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "offers" to priceOfferService.getOffersForConversation(currentUser.userId, conversationId)))

    @ExceptionHandler(PriceOfferNotFoundException::class)
    fun handleOfferNotFound(ex: PriceOfferNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("OFFER_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidOfferAmountException::class)
    fun handleInvalidOfferAmount(ex: InvalidOfferAmountException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_OFFER_AMOUNT", ex.message ?: "Bad request"))

    @ExceptionHandler(OfferAlreadyResolvedException::class)
    fun handleOfferAlreadyResolved(ex: OfferAlreadyResolvedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("OFFER_ALREADY_RESOLVED", ex.message ?: "Conflict"))

    @ExceptionHandler(OwnOfferException::class)
    fun handleOwnOffer(ex: OwnOfferException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("OWN_OFFER", ex.message ?: "Bad request"))

    @ExceptionHandler(ListingNotFoundException::class)
    fun handleNotFound(ex: ListingNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("LISTING_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidListingException::class)
    fun handleInvalid(ex: InvalidListingException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_LISTING", ex.message ?: "Bad request"))

    @ExceptionHandler(ListingNotActiveException::class)
    fun handleNotActive(ex: ListingNotActiveException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("LISTING_NOT_ACTIVE", ex.message ?: "Conflict"))

    @ExceptionHandler(OwnListingException::class)
    fun handleOwnListing(ex: OwnListingException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("OWN_LISTING", ex.message ?: "Bad request"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))

    @ExceptionHandler(InvalidCoordinatesException::class)
    fun handleInvalidCoordinates(ex: InvalidCoordinatesException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_COORDINATES", ex.message ?: "Bad request"))

    @ExceptionHandler(NeighborhoodNotSetException::class)
    fun handleNeighborhoodNotSet(ex: NeighborhoodNotSetException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("NEIGHBORHOOD_NOT_SET", ex.message ?: "Bad request"))

    @ExceptionHandler(FavoriteListingNotFoundException::class)
    fun handleFavoriteListingNotFound(ex: FavoriteListingNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("LISTING_NOT_FOUND", ex.message ?: "Not found"))
}
