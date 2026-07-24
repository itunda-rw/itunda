package rw.itunda.realestate.web

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
import rw.itunda.core.domain.HoodTransactionType
import rw.itunda.core.domain.PropertyListingType
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.review.HoodReviewAlreadySubmittedException
import rw.itunda.core.review.HoodReviewNoCounterpartyException
import rw.itunda.core.review.HoodReviewNotPartyException
import rw.itunda.core.review.HoodReviewService
import rw.itunda.core.review.HoodReviewTransactionNotCompletedException
import rw.itunda.core.review.HoodReviewTransactionNotFoundException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta
import rw.itunda.core.web.toResponseDto
import rw.itunda.core.web.trustScores
import rw.itunda.realestate.CounterpartyNotFoundException
import rw.itunda.realestate.FavoritePropertyListingNotFoundException
import rw.itunda.realestate.InvalidPropertyCoordinatesException
import rw.itunda.realestate.InvalidPropertyListingException
import rw.itunda.realestate.InvalidPropertyOfferAmountException
import rw.itunda.realestate.OwnPropertyListingException
import rw.itunda.realestate.PropertyListingFavoriteService
import rw.itunda.realestate.OwnPropertyOfferException
import rw.itunda.realestate.PropertyListingNotAvailableException
import rw.itunda.realestate.PropertyListingNotFoundException
import rw.itunda.realestate.PropertyListingService
import rw.itunda.realestate.PropertyOfferAlreadyResolvedException
import rw.itunda.realestate.PropertyOfferNotFoundException
import rw.itunda.realestate.PropertyOfferResponseAction
import rw.itunda.realestate.PropertyPriceOfferService
import rw.itunda.realestate.RealEstateNeighborhoodNotSetException
import java.math.BigDecimal

data class CreatePropertyListingRequest(
    val listingType: PropertyListingType,
    val propertyType: String,
    val title: String,
    val description: String,
    val price: BigDecimal,
    val bedrooms: Int? = null,
    val sizeSqm: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

// Real 당근-style price-offer negotiation (2026-07-19) -- see PropertyPriceOfferService's
// own doc comment. Mirrors MarketplaceController's MakeOfferRequest/RespondToOfferRequest exactly.
data class MakePropertyOfferRequest(val amount: BigDecimal)
data class RespondToPropertyOfferRequest(val action: PropertyOfferResponseAction, val counterAmount: BigDecimal? = null)
data class MarkTakenRequest(val counterpartyPhoneNumber: String? = null)
data class SubmitHoodReviewRequest(val goodPoints: List<String> = emptyList(), val uncomfortablePoints: List<String> = emptyList())

// Real 당근부동산-style property board -- see PropertyListingService's own doc comment.
// Normal itunda-user JWT gate (default SecurityConfig .anyRequest().authenticated()).
@RestController
@RequestMapping("/api/v1/realestate")
class PropertyListingController(
    private val propertyListingService: PropertyListingService,
    private val propertyPriceOfferService: PropertyPriceOfferService,
    private val propertyListingFavoriteService: PropertyListingFavoriteService,
    private val userRepository: UserRepository,
    private val hoodReviewService: HoodReviewService,
) {

    @GetMapping("/property-types")
    fun propertyTypes(): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "propertyTypes" to PropertyListingService.PROPERTY_TYPES))

    @PostMapping("/listings")
    fun createListing(
        @RequestBody request: CreatePropertyListingRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val listing = propertyListingService.createListing(
            currentUser.userId, request.listingType, request.propertyType, request.title, request.description,
            request.price, request.bedrooms, request.sizeSqm, request.latitude, request.longitude,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "listing" to listing))
    }

    @GetMapping("/listings")
    fun browse(
        @RequestParam(required = false) listingType: PropertyListingType?,
        @RequestParam(required = false) propertyType: String?,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Map<String, Any?>> {
        val page = propertyListingService.browse(pageable, listingType, propertyType)
        // Real Karrot-Score-style trust badge (2026-07-21) -- see trustScores' own doc
        // comment. One batch findAllById, not one query per listing's lister.
        val scores = trustScores(userRepository, page.content.map { it.listerId })
        return ResponseEntity.ok(mapOf("success" to true, "listings" to page.content, "trustScores" to scores) + pageMeta(page))
    }

    @GetMapping("/listings/nearby")
    fun nearby(
        @RequestParam latitude: Double,
        @RequestParam longitude: Double,
        @RequestParam(required = false, defaultValue = "5.0") radiusKm: Double,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Map<String, Any?>> {
        val page = propertyListingService.nearby(latitude, longitude, radiusKm, pageable)
        val scores = trustScores(userRepository, page.content.map { it.listerId })
        return ResponseEntity.ok(mapOf("success" to true, "listings" to page.content, "trustScores" to scores) + pageMeta(page))
    }

    // Real hyperlocal "my neighborhood" browse (2026-07-20) -- see
    // PropertyListingService.myNeighborhood's own doc comment.
    @GetMapping("/listings/my-neighborhood")
    fun myNeighborhood(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = propertyListingService.myNeighborhood(currentUser.userId, pageable)
        val scores = trustScores(userRepository, page.content.map { it.listerId })
        return ResponseEntity.ok(mapOf("success" to true, "listings" to page.content, "trustScores" to scores) + pageMeta(page))
    }

    @GetMapping("/my-listings")
    fun myListings(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = propertyListingService.getMyListings(currentUser.userId, pageable)
        val scores = trustScores(userRepository, page.content.map { it.listerId })
        return ResponseEntity.ok(mapOf("success" to true, "listings" to page.content, "trustScores" to scores) + pageMeta(page))
    }

    // Real "Places I got" (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 4
    // recommendation #6. See PropertyListingRepository.
    // findByCounterpartyIdOrderByCreatedAtDesc's own doc comment for the full account.
    @GetMapping("/my-acquired-listings")
    fun myAcquiredListings(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = propertyListingService.getMyAcquiredListings(currentUser.userId, pageable)
        val scores = trustScores(userRepository, page.content.map { it.listerId })
        return ResponseEntity.ok(mapOf("success" to true, "listings" to page.content, "trustScores" to scores) + pageMeta(page))
    }

    @GetMapping("/listings/{propertyListingId}")
    fun getListing(@PathVariable propertyListingId: String): ResponseEntity<Map<String, Any?>> {
        val listing = propertyListingService.getListing(propertyListingId)
        val listerTrustScore = trustScores(userRepository, listOf(listing.listerId))[listing.listerId]
        return ResponseEntity.ok(mapOf("success" to true, "listing" to listing, "listerTrustScore" to listerTrustScore))
    }

    @PostMapping("/listings/{propertyListingId}/mark-taken")
    fun markTaken(
        @PathVariable propertyListingId: String,
        @RequestBody(required = false) request: MarkTakenRequest?,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(
            mapOf(
                "success" to true,
                "listing" to propertyListingService.markTaken(currentUser.userId, propertyListingId, request?.counterpartyPhoneNumber),
            ),
        )

    // Real post-transaction review with asymmetric public/private visibility
    // (2026-07-24) -- see HoodReviewService's own doc comment for the full account.
    @PostMapping("/listings/{propertyListingId}/review")
    fun submitReview(
        @PathVariable propertyListingId: String,
        @RequestBody request: SubmitHoodReviewRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val review = hoodReviewService.submitReview(
            currentUser.userId, HoodTransactionType.PROPERTY_LISTING, propertyListingId, request.goodPoints, request.uncomfortablePoints,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "review" to review.toResponseDto()))
    }

    @GetMapping("/listings/{propertyListingId}/review")
    fun getReviews(
        @PathVariable propertyListingId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val reviews = hoodReviewService.getTransactionReviews(currentUser.userId, HoodTransactionType.PROPERTY_LISTING, propertyListingId).map { it.toResponseDto() }
        return ResponseEntity.ok(mapOf("success" to true, "reviews" to reviews))
    }

    @DeleteMapping("/listings/{propertyListingId}")
    fun removeListing(
        @PathVariable propertyListingId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "listing" to propertyListingService.removeListing(currentUser.userId, propertyListingId)))

    // Real 당근부동산 property-listing wishlist (2026-07-22) -- see
    // PropertyListingFavoriteService's own doc comment. Mirrors MarketplaceController's
    // own favorite-listing endpoints field-for-field.
    @PostMapping("/listings/{propertyListingId}/favorite")
    fun addFavorite(
        @PathVariable propertyListingId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val favorite = propertyListingFavoriteService.addFavorite(currentUser.userId, propertyListingId)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "favorite" to favorite))
    }

    @DeleteMapping("/listings/{propertyListingId}/favorite")
    fun removeFavorite(
        @PathVariable propertyListingId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Boolean>> {
        propertyListingFavoriteService.removeFavorite(currentUser.userId, propertyListingId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @GetMapping("/listings/favorites")
    fun getMyFavoriteListings(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = propertyListingFavoriteService.getMyFavorites(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "favorites" to page.content) + pageMeta(page))
    }

    @PostMapping("/listings/{propertyListingId}/contact-lister")
    fun contactLister(
        @PathVariable propertyListingId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val conversation = propertyListingService.contactLister(currentUser.userId, propertyListingId)
        return ResponseEntity.ok(mapOf("success" to true, "conversation" to conversation))
    }

    // Real 당근-style price-offer negotiation (2026-07-19) -- see PropertyPriceOfferService.
    @PostMapping("/listings/{propertyListingId}/offers")
    fun makeOffer(
        @PathVariable propertyListingId: String,
        @RequestBody request: MakePropertyOfferRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val offer = propertyPriceOfferService.makeOffer(currentUser.userId, propertyListingId, request.amount)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "offer" to offer))
    }

    @PostMapping("/offers/{offerId}/respond")
    fun respondToOffer(
        @PathVariable offerId: String,
        @RequestBody request: RespondToPropertyOfferRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val offer = propertyPriceOfferService.respondToOffer(currentUser.userId, offerId, request.action, request.counterAmount)
        return ResponseEntity.ok(mapOf("success" to true, "offer" to offer))
    }

    @GetMapping("/conversations/{conversationId}/offers")
    fun getOffersForConversation(
        @PathVariable conversationId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "offers" to propertyPriceOfferService.getOffersForConversation(currentUser.userId, conversationId)))

    @ExceptionHandler(PropertyOfferNotFoundException::class)
    fun handleOfferNotFound(ex: PropertyOfferNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("OFFER_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidPropertyOfferAmountException::class)
    fun handleInvalidOfferAmount(ex: InvalidPropertyOfferAmountException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_OFFER_AMOUNT", ex.message ?: "Bad request"))

    @ExceptionHandler(PropertyOfferAlreadyResolvedException::class)
    fun handleOfferAlreadyResolved(ex: PropertyOfferAlreadyResolvedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("OFFER_ALREADY_RESOLVED", ex.message ?: "Conflict"))

    @ExceptionHandler(OwnPropertyOfferException::class)
    fun handleOwnOffer(ex: OwnPropertyOfferException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("OWN_OFFER", ex.message ?: "Bad request"))

    @ExceptionHandler(PropertyListingNotFoundException::class)
    fun handleNotFound(ex: PropertyListingNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("PROPERTY_LISTING_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidPropertyListingException::class)
    fun handleInvalid(ex: InvalidPropertyListingException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_PROPERTY_LISTING", ex.message ?: "Bad request"))

    @ExceptionHandler(PropertyListingNotAvailableException::class)
    fun handleNotAvailable(ex: PropertyListingNotAvailableException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("PROPERTY_LISTING_NOT_AVAILABLE", ex.message ?: "Conflict"))

    @ExceptionHandler(OwnPropertyListingException::class)
    fun handleOwnListing(ex: OwnPropertyListingException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("OWN_PROPERTY_LISTING", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidPropertyCoordinatesException::class)
    fun handleInvalidCoordinates(ex: InvalidPropertyCoordinatesException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_COORDINATES", ex.message ?: "Bad request"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))

    @ExceptionHandler(RealEstateNeighborhoodNotSetException::class)
    fun handleNeighborhoodNotSet(ex: RealEstateNeighborhoodNotSetException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("NEIGHBORHOOD_NOT_SET", ex.message ?: "Bad request"))

    @ExceptionHandler(FavoritePropertyListingNotFoundException::class)
    fun handleFavoriteNotFound(ex: FavoritePropertyListingNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("PROPERTY_LISTING_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(CounterpartyNotFoundException::class)
    fun handleCounterpartyNotFound(ex: CounterpartyNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("COUNTERPARTY_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(HoodReviewTransactionNotFoundException::class)
    fun handleReviewTransactionNotFound(ex: HoodReviewTransactionNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("PROPERTY_LISTING_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(HoodReviewTransactionNotCompletedException::class)
    fun handleReviewTransactionNotCompleted(ex: HoodReviewTransactionNotCompletedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("REVIEW_TRANSACTION_NOT_COMPLETED", ex.message ?: "Conflict"))

    @ExceptionHandler(HoodReviewNoCounterpartyException::class)
    fun handleReviewNoCounterparty(ex: HoodReviewNoCounterpartyException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("REVIEW_NO_COUNTERPARTY", ex.message ?: "Bad request"))

    @ExceptionHandler(HoodReviewNotPartyException::class)
    fun handleReviewNotParty(ex: HoodReviewNotPartyException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("REVIEW_NOT_PARTY", ex.message ?: "Forbidden"))

    @ExceptionHandler(HoodReviewAlreadySubmittedException::class)
    fun handleReviewAlreadySubmitted(ex: HoodReviewAlreadySubmittedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("REVIEW_ALREADY_SUBMITTED", ex.message ?: "Conflict"))
}
