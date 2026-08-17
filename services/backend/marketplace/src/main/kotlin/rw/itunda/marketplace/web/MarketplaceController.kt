package rw.itunda.marketplace.web

import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.domain.HoodTransactionType
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.WalletFrozenException
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
import rw.itunda.marketplace.BuyerNoWalletException
import rw.itunda.marketplace.BuyerNotFoundException
import rw.itunda.marketplace.FavoriteListingNotFoundException
import rw.itunda.marketplace.InvalidBoostDurationException
import rw.itunda.marketplace.InvalidCoordinatesException
import rw.itunda.marketplace.InvalidDisputeReasonException
import rw.itunda.marketplace.InvalidEscrowStatusException
import rw.itunda.marketplace.InvalidListingException
import rw.itunda.marketplace.InvalidListingPriceException
import rw.itunda.marketplace.InvalidOfferAmountException
import rw.itunda.marketplace.KeywordAlertService
import rw.itunda.marketplace.ListingFavoriteService
import rw.itunda.marketplace.ListingAlreadyReportedException
import rw.itunda.marketplace.ListingBumpCooldownException
import rw.itunda.marketplace.ListingNotActiveException
import rw.itunda.marketplace.ListingNotFoundException
import rw.itunda.marketplace.MarketplaceEscrowNotFoundException
import rw.itunda.marketplace.MarketplaceService
import rw.itunda.marketplace.NeighborhoodNotSetException
import rw.itunda.marketplace.OfferAlreadyResolvedException
import rw.itunda.marketplace.OfferResponseAction
import rw.itunda.marketplace.OwnListingException
import rw.itunda.marketplace.OwnListingReportException
import rw.itunda.marketplace.OwnOfferException
import rw.itunda.marketplace.PriceOfferNotFoundException
import rw.itunda.marketplace.PriceOfferService
import rw.itunda.marketplace.SellerNoWalletException
import rw.itunda.core.domain.MarketplaceReportReason
import java.math.BigDecimal

data class CreateListingRequest(
    val title: String,
    val description: String,
    val price: BigDecimal,
    val category: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val meetingPlace: String? = null,
    val photoUrl: String? = null,
)

// Real 당근마켓 신고하기 (report a listing) -- see MarketplaceListingReport.kt's own doc
// comment.
data class ReportListingRequest(val reason: MarketplaceReportReason, val details: String? = null)

data class MakeOfferRequest(val amount: BigDecimal)
data class RespondToOfferRequest(val action: OfferResponseAction, val counterAmount: BigDecimal? = null)
data class MarkSoldRequest(val buyerPhoneNumber: String? = null)
data class SubmitHoodReviewRequest(val goodPoints: List<String> = emptyList(), val uncomfortablePoints: List<String> = emptyList())
data class BoostListingRequest(val days: Int)
// Real 가격 수정 (price edit) -- see MarketplaceService.updatePrice's own doc comment.
data class UpdateListingPriceRequest(val price: BigDecimal)
data class DisputeEscrowRequest(val reason: String)
// Real gap closed 2026-08-15 -- see MarketplaceEscrow.deliveryAddress's own doc comment.
// Optional: omit it (or send it empty) for the original in-person handoff.
data class PayEscrowRequest(val deliveryAddress: String? = null)

// Real 당근마켓-style marketplace -- see MarketplaceService's own doc comment. Normal
// itunda-user JWT gate (default SecurityConfig .anyRequest().authenticated()).
@RestController
@RequestMapping("/api/v1/marketplace")
class MarketplaceController(
    private val marketplaceService: MarketplaceService,
    private val priceOfferService: PriceOfferService,
    private val listingFavoriteService: ListingFavoriteService,
    private val userRepository: UserRepository,
    private val hoodReviewService: HoodReviewService,
    private val idempotencyService: IdempotencyService,
    private val keywordAlertService: KeywordAlertService,
) {

    @PostMapping("/listings")
    fun createListing(
        @RequestBody request: CreateListingRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val listing = marketplaceService.createListing(
            currentUser.userId, request.title, request.description, request.price, request.category,
            request.latitude, request.longitude, request.meetingPlace, request.photoUrl,
        )
        // Real 당근마켓 Keyword Alert (2026-07-26) -- see KeywordAlertService's own doc
        // comment for why this lives here, at the controller layer, rather than inside
        // MarketplaceService.createListing itself.
        keywordAlertService.notifyMatchingAlerts(listing)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "listing" to listing))
    }

    // Real Karrot 중고거래 category taxonomy -- see MarketplaceService.CATEGORIES's own
    // doc comment.
    @GetMapping("/categories")
    fun getCategories(): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "categories" to MarketplaceService.CATEGORIES))

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
        val liked = marketplaceService.likedListingIds(page.content, currentUser.userId)
        return ResponseEntity.ok(mapOf("success" to true, "listings" to page.content, "trustScores" to scores, "likedByMe" to liked) + pageMeta(page))
    }

    // Real relevance-ranked search (2026-08-14) -- see MarketplaceService.search's own
    // doc comment.
    @GetMapping("/listings/search")
    fun search(
        @RequestParam q: String,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Map<String, Any?>> {
        val page = marketplaceService.search(q, pageable)
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
        val liked = marketplaceService.likedListingIds(page.content, currentUser.userId)
        return ResponseEntity.ok(mapOf("success" to true, "listings" to page.content, "trustScores" to scores, "likedByMe" to liked) + pageMeta(page))
    }

    // Real "My purchases" (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 4
    // recommendation #6. See ListingRepository.findByBuyerIdOrderByCreatedAtDesc's own
    // doc comment for the full account.
    @GetMapping("/my-purchases")
    fun getMyPurchases(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = marketplaceService.getMyPurchases(currentUser.userId, pageable)
        val scores = trustScores(userRepository, page.content.map { it.sellerId })
        val liked = marketplaceService.likedListingIds(page.content, currentUser.userId)
        return ResponseEntity.ok(mapOf("success" to true, "listings" to page.content, "trustScores" to scores, "likedByMe" to liked) + pageMeta(page))
    }

    // Real like/unlike toggle (2026-08-03) -- see MarketplaceService.toggleLike's own
    // doc comment. browse/nearby stay unauthenticated (see their own methods above,
    // no currentUser param -- a deliberate guest-browse allowance this endpoint
    // doesn't change), so those two responses don't carry likedByMe; the heart still
    // shows the real count either way, just starts unfilled until a real tap
    // authenticates it, an honest tradeoff rather than requiring login to browse.
    @PostMapping("/listings/{listingId}/like")
    fun toggleLike(
        @PathVariable listingId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val liked = marketplaceService.toggleLike(currentUser.userId, listingId)
        return ResponseEntity.ok(mapOf("success" to true, "liked" to liked))
    }

    // Real 당근마켓 신고하기 (report a listing) -- see MarketplaceService.reportListing's
    // own doc comment. Not money-moving, so no Idempotency-Key required, same simpler
    // discipline toggleLike above already follows.
    @PostMapping("/listings/{listingId}/report")
    fun reportListing(
        @PathVariable listingId: String,
        @RequestBody request: ReportListingRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val report = marketplaceService.reportListing(currentUser.userId, listingId, request.reason, request.details)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "report" to report))
    }

    // Real 당근마켓 끌어올리기 (bump to top of feed) -- see MarketplaceService
    // .bumpListing's own doc comment. Free and self-serve (unlike boost below), so no
    // real money moves and no Idempotency-Key is required, same simpler discipline
    // toggleLike above already follows.
    @PostMapping("/listings/{listingId}/bump")
    fun bumpListing(
        @PathVariable listingId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val listing = marketplaceService.bumpListing(currentUser.userId, listingId)
        return ResponseEntity.ok(mapOf("success" to true, "listing" to listing))
    }

    // Real 가격 수정 (price edit) + Karrot 가격 하락 알림 -- see
    // MarketplaceService.updatePrice's own doc comment. Not money-moving itself, so no
    // Idempotency-Key required, same simpler discipline bumpListing above already
    // follows.
    @PatchMapping("/listings/{listingId}/price")
    fun updatePrice(
        @PathVariable listingId: String,
        @RequestBody request: UpdateListingPriceRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val listing = marketplaceService.updatePrice(currentUser.userId, listingId, request.price)
        return ResponseEntity.ok(mapOf("success" to true, "listing" to listing))
    }

    // Real seller-paid sponsored placement -- see MarketplaceService.boostListing's own
    // doc comment. Real money movement (unlike every other write in this controller,
    // which settles buyer/seller in person), so this is the first marketplace endpoint
    // to require a real Idempotency-Key.
    @PostMapping("/listings/{listingId}/boost")
    fun boostListing(
        @PathVariable listingId: String,
        @RequestBody request: BoostListingRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/marketplace/listings/$listingId/boost", idempotencyKey, request) {
            val listing = marketplaceService.boostListing(currentUser.userId, listingId, request.days)
            200 to mapOf("success" to true, "listing" to listing)
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/boost-tiers")
    fun getBoostTiers(): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "tiers" to MarketplaceService.BOOST_TIERS.toSortedMap()))

    // Real "pay via itunda" Marketplace escrow -- see MarketplaceService's own doc
    // comment. Real money movement, so this and confirm-receipt both require a real
    // Idempotency-Key, same discipline boost above already established for this
    // controller's first money-moving endpoint.
    @PostMapping("/listings/{listingId}/pay-escrow")
    fun payEscrow(
        @PathVariable listingId: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        // Not required -- existing callers (Android's own in-person escrow flow) send
        // no body at all today, and that must keep working unchanged.
        @RequestBody(required = false) request: PayEscrowRequest?,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/marketplace/listings/$listingId/pay-escrow", idempotencyKey, listingId) {
            201 to mapOf("success" to true, "escrow" to marketplaceService.payEscrow(currentUser.userId, listingId, request?.deliveryAddress))
        }
        return ResponseEntity.status(status).body(body)
    }

    @PostMapping("/listings/{listingId}/confirm-receipt")
    fun confirmReceipt(
        @PathVariable listingId: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/marketplace/listings/$listingId/confirm-receipt", idempotencyKey, listingId) {
            200 to mapOf("success" to true, "escrow" to marketplaceService.confirmReceipt(currentUser.userId, listingId))
        }
        return ResponseEntity.status(status).body(body)
    }

    @PostMapping("/listings/{listingId}/dispute-escrow")
    fun disputeEscrow(
        @PathVariable listingId: String,
        @RequestBody request: DisputeEscrowRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val escrow = marketplaceService.disputeEscrow(currentUser.userId, listingId, request.reason)
        return ResponseEntity.ok(mapOf("success" to true, "escrow" to escrow))
    }

    @GetMapping("/listings/{listingId}/escrow")
    fun getEscrow(
        @PathVariable listingId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val escrow = marketplaceService.getEscrow(currentUser.userId, listingId)
        return ResponseEntity.ok(mapOf("success" to true, "escrow" to escrow))
    }

    @PostMapping("/listings/{listingId}/mark-sold")
    fun markSold(
        @PathVariable listingId: String,
        @RequestBody(required = false) request: MarkSoldRequest?,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(
            mapOf("success" to true, "listing" to marketplaceService.markSold(currentUser.userId, listingId, request?.buyerPhoneNumber)),
        )

    // Real post-transaction review with asymmetric public/private visibility
    // (2026-07-24) -- see HoodReviewService's own doc comment for the full account.
    @PostMapping("/listings/{listingId}/review")
    fun submitReview(
        @PathVariable listingId: String,
        @RequestBody request: SubmitHoodReviewRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val review = hoodReviewService.submitReview(
            currentUser.userId, HoodTransactionType.LISTING, listingId, request.goodPoints, request.uncomfortablePoints,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "review" to review.toResponseDto()))
    }

    @GetMapping("/listings/{listingId}/review")
    fun getReviews(
        @PathVariable listingId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val reviews = hoodReviewService.getTransactionReviews(currentUser.userId, HoodTransactionType.LISTING, listingId).map { it.toResponseDto() }
        return ResponseEntity.ok(mapOf("success" to true, "reviews" to reviews))
    }

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

    @ExceptionHandler(InvalidListingPriceException::class)
    fun handleInvalidPrice(ex: InvalidListingPriceException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_LISTING_PRICE", ex.message ?: "Bad request"))

    @ExceptionHandler(ListingNotActiveException::class)
    fun handleNotActive(ex: ListingNotActiveException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("LISTING_NOT_ACTIVE", ex.message ?: "Conflict"))

    @ExceptionHandler(OwnListingException::class)
    fun handleOwnListing(ex: OwnListingException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("OWN_LISTING", ex.message ?: "Bad request"))

    @ExceptionHandler(OwnListingReportException::class)
    fun handleOwnListingReport(ex: OwnListingReportException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("OWN_LISTING_REPORT", ex.message ?: "Bad request"))

    @ExceptionHandler(ListingAlreadyReportedException::class)
    fun handleListingAlreadyReported(ex: ListingAlreadyReportedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("LISTING_ALREADY_REPORTED", ex.message ?: "Conflict"))

    @ExceptionHandler(ListingBumpCooldownException::class)
    fun handleBumpCooldown(ex: ListingBumpCooldownException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("BUMP_COOLDOWN", ex.message ?: "Too many requests"))

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

    @ExceptionHandler(BuyerNotFoundException::class)
    fun handleBuyerNotFound(ex: BuyerNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("BUYER_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(HoodReviewTransactionNotFoundException::class)
    fun handleReviewTransactionNotFound(ex: HoodReviewTransactionNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("LISTING_NOT_FOUND", ex.message ?: "Not found"))

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

    @ExceptionHandler(InvalidBoostDurationException::class)
    fun handleInvalidBoostDuration(ex: InvalidBoostDurationException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_BOOST_DURATION", ex.message ?: "Bad request"))

    @ExceptionHandler(SellerNoWalletException::class)
    fun handleSellerNoWallet(ex: SellerNoWalletException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(BuyerNoWalletException::class)
    fun handleBuyerNoWallet(ex: BuyerNoWalletException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(MarketplaceEscrowNotFoundException::class)
    fun handleEscrowNotFound(ex: MarketplaceEscrowNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ESCROW_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidEscrowStatusException::class)
    fun handleInvalidEscrowStatus(ex: InvalidEscrowStatusException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("INVALID_ESCROW_STATUS", ex.message ?: "Conflict"))

    @ExceptionHandler(InvalidDisputeReasonException::class)
    fun handleInvalidDisputeReason(ex: InvalidDisputeReasonException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_DISPUTE_REASON", ex.message ?: "Bad request"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(WalletFrozenException::class)
    fun handleWalletFrozen(ex: WalletFrozenException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("WALLET_FROZEN", ex.message ?: "Wallet is frozen"))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleIdempotencyConflict(ex: IdempotencyConflictException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleIdempotencyInProgress(ex: IdempotencyInProgressException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))
}
