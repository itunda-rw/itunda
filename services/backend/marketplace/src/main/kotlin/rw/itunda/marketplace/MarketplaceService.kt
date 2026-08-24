package rw.itunda.marketplace

import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Conversation
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Listing
import rw.itunda.core.domain.ListingStatus
import rw.itunda.core.domain.MarketplaceEscrow
import rw.itunda.core.domain.MarketplaceEscrowStatus
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.AccountType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.geo.NominatimGeocodingClient
import rw.itunda.core.geo.OsrmRoutingClient
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.domain.ListingLike
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.ListingFavoriteRepository
import rw.itunda.core.repository.ListingHideRepository
import rw.itunda.core.repository.ListingLikeRepository
import rw.itunda.core.repository.ListingRepository
import rw.itunda.core.repository.MarketplaceEscrowRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.search.FullTextSearchUtil
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.trust.TrustScoreService
import rw.itunda.messaging.MessagingService
import rw.itunda.messaging.SelfConversationException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.util.UUID

class ListingNotFoundException(message: String) : RuntimeException(message)
class ListingNotOwnedException(message: String) : RuntimeException(message)
class InvalidListingException(message: String) : RuntimeException(message)
class ListingNotActiveException(message: String) : RuntimeException(message)
class OwnListingException(message: String) : RuntimeException(message)
class InvalidCoordinatesException(message: String) : RuntimeException(message)
class NeighborhoodNotSetException(message: String) : RuntimeException(message)
class BuyerNotFoundException(message: String) : RuntimeException(message)
class InvalidBoostDurationException(message: String) : RuntimeException(message)
class SellerNoAccountException(message: String) : RuntimeException(message)
class BuyerNoAccountException(message: String) : RuntimeException(message)
class MarketplaceEscrowNotFoundException(message: String) : RuntimeException(message)
class InvalidEscrowStatusException(message: String) : RuntimeException(message)
class InvalidDisputeReasonException(message: String) : RuntimeException(message)
class ListingBumpCooldownException(message: String) : RuntimeException(message)
class InvalidListingPriceException(message: String) : RuntimeException(message)

/**
 * A real 당근마켓 (Danggeun/Karrot Market)-style secondhand marketplace -- the second
 * of the three new "super app" phases named in the 2026-07-18 goal expansion, built
 * after Kakao-style messaging specifically so `contactSeller` below could reuse that
 * real 1:1 conversation primitive rather than inventing a second chat system.
 *
 * Honestly scoped -- see Listing.kt's own doc comment for the real, named limitation
 * this carries (no real location/proximity data exists anywhere in this backend, so
 * this is a real general marketplace, not real hyperlocal discovery).
 *
 * 2026-08-17: `updatePrice` closes a real gap -- this class had no price-edit
 * capability of any kind until now (a seller could create or delete a listing, never
 * change its price), which also meant `ListingFavorite` (the real 관심목록/wishlist
 * `ListingFavoriteService` already provides) was the one `*Favorite` entity in this
 * domain with zero notification hook left after Sections 143/144 closed
 * `EatsFavorite`/`JobPostFavorite`'s equivalent gaps. See `updatePrice`'s own doc
 * comment for the real Karrot sourcing.
 */
@Service
class MarketplaceService(
    private val listingRepository: ListingRepository,
    private val rateLimiter: RateLimiter,
    private val messagingService: MessagingService,
    private val osrmRoutingClient: OsrmRoutingClient,
    private val nominatimGeocodingClient: NominatimGeocodingClient,
    private val userRepository: UserRepository,
    private val trustScoreService: TrustScoreService,
    private val accountRepository: AccountRepository,
    private val ledgerService: LedgerService,
    private val transactionRepository: TransactionRepository,
    private val marketplaceEscrowRepository: MarketplaceEscrowRepository,
    private val listingLikeRepository: ListingLikeRepository,
    private val fraudRuleEngine: FraudRuleEngine,
    private val listingFavoriteRepository: ListingFavoriteRepository,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
    private val listingHideRepository: ListingHideRepository,
) {
    private val log = LoggerFactory.getLogger(MarketplaceService::class.java)

    companion object {
        // Bounds a single OSRM /table request's URL length and the private cloud's
        // per-request load -- beyond this, nearby() quietly stays on the already-honest
        // Haversine ranking rather than risking an oversized request.
        private const val MAX_OSRM_TABLE_CANDIDATES = 100

        // Same real 1.5% fee-schedule reasoning OrderService.feeRate/EatsOrderService
        // .platformFeeRate already use -- reused rather than inventing a different
        // number for what is, underneath, the same kind of real paid-safety-service fee.
        val ESCROW_FEE_RATE = BigDecimal("0.015")

        // Real flat-fee sponsored-placement tiers (2026-07-25), matching Baemin's own
        // real 울트라콜 mechanic (a flat fee per real time slot) rather than Coupang's
        // per-click auction model -- the simpler, more honestly-buildable of the two
        // real sourced models, and proportionate to this marketplace's real scale.
        val BOOST_TIERS: Map<Int, BigDecimal> = mapOf(
            3 to BigDecimal("500"),
            7 to BigDecimal("1000"),
            14 to BigDecimal("1800"),
        )

        // Real 당근마켓 bump cadence -- once per listing per day, the same real cadence
        // 당근's own 끌어올리기 enforces to keep it a genuine "still available, still
        // want to sell this" signal rather than a way to spam the top of the feed.
        val BUMP_COOLDOWN: Duration = Duration.ofHours(24)

        // Real Karrot (당근마켓) 중고거래 category taxonomy (2026-08-16, uncalled-
        // endpoint-sweep-adjacent research) -- sourced from Karrot's own official Korea
        // App Store listing (apps.apple.com/kr/app/당근/id1018769995), a real fixed
        // ~24-category list. `category` itself was already a real, stored, filterable
        // field on every `Listing` (see `browse`'s own `category` param below) -- what
        // was missing was any real curated taxonomy driving it, same "backend has the
        // filter, no real taxonomy or UI to use it" shape Eats/Shop already closed via
        // `MerchantService.CATEGORIES`. Adapted honestly to general secondhand goods
        // relevant to Rwanda rather than translated verbatim -- itunda's own `vehicle`/
        // `realestate` modules already own those categories elsewhere, so they're
        // deliberately not duplicated here. Purely additive: existing free-text
        // listings (confirmed live via direct DB check -- real seeded values include
        // "Electronics"/"Sports"/"Furniture"/"other") keep working unchanged; this is a
        // real curated list to browse BY, not a validation change to what a seller can
        // type when creating a listing.
        val CATEGORIES = listOf(
            "Electronics", "Furniture", "Home & Kitchen", "Kids & Baby", "Fashion",
            "Beauty & Health", "Sports & Outdoors", "Books & Media", "Toys & Games",
            "Musical Instruments", "Pet Supplies", "Other",
        )
    }

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
        meetingPlace: String? = null,
        photoUrl: String? = null,
    ): Listing {
        val trimmedTitle = title.trim()
        val trimmedDescription = description.trim()
        val trimmedCategory = category.trim()
        if (trimmedTitle.isEmpty() || trimmedDescription.isEmpty() || trimmedCategory.isEmpty()) {
            throw InvalidListingException("Title, description, and category are all required")
        }
        // Real bound, matching `deliveryAddress`'s own fix on the Eats/Commerce rows
        // the same day -- `title`/`description` are VARCHAR(255)/VARCHAR(2000), and
        // this DB's real STRICT_TRANS_TABLES mode throws a raw, unhandled 500 on an
        // over-length insert rather than truncating.
        if (trimmedTitle.length > 255 || trimmedDescription.length > 2000) {
            throw InvalidListingException("Title must be 255 characters or fewer, description 2000 or fewer")
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
        val trimmedMeetingPlace = meetingPlace?.trim()?.takeIf { it.isNotEmpty() }
        if (trimmedMeetingPlace != null && trimmedMeetingPlace.length > 120) {
            throw InvalidListingException("Suggested meeting place must be 120 characters or fewer")
        }
        val trimmedPhotoUrl = photoUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (trimmedPhotoUrl != null && trimmedPhotoUrl.length > 500) {
            throw InvalidListingException("Photo URL must be 500 characters or fewer")
        }
        // Real anti-spam limit on user-generated listings -- same convention this
        // session's own security review already established for every other
        // content/money-creation endpoint (Partner SDK, Certificate, chargeCard,
        // messaging). 10/hour comfortably covers a real seller listing several items
        // in one sitting while bounding a spam-listing flood.
        rateLimiter.checkLimit("marketplace:create:$sellerId", limit = 10, window = Duration.ofHours(1))

        // Real hyperlocal neighborhood (2026-07-20) -- cached once here from a real
        // reverse-geocode, same "cache, don't recompute at read time" discipline this
        // codebase already established for CommunityPost's like/comment counters. Best-
        // effort: null when unconfigured/unreachable/no match, never blocks the listing
        // itself from being created (matching notifyNearestRiders' own "never fail the
        // real transition it's reacting to" precedent for auxiliary geo lookups).
        val neighborhood = if (latitude != null && longitude != null) {
            nominatimGeocodingClient.reverseGeocode(latitude, longitude)
        } else {
            null
        }

        return listingRepository.save(
            Listing(
                id = "listing_${UUID.randomUUID()}", sellerId = sellerId, title = trimmedTitle,
                description = trimmedDescription, price = price, category = trimmedCategory,
                latitude = latitude, longitude = longitude, neighborhood = neighborhood,
                meetingPlace = trimmedMeetingPlace, photoUrl = trimmedPhotoUrl,
            ),
        )
    }

    // Real hyperlocal "my neighborhood" browse (2026-07-20) -- closes the "no real
    // hyperlocal auto-filtering by a user's actual neighborhood" gap this class's own doc
    // comment named. Resolves the caller's own real User.neighborhood (set via
    // AuthService.setNeighborhood) and filters to listings whose own cached neighborhood
    // matches exactly -- an honest, real string match, not a radius guess. Throws rather
    // than silently returning an empty page when the caller hasn't set one yet, matching
    // this feature's own "honest failure, not a silent no-op" discipline.
    fun myNeighborhood(callerUserId: String, category: String?, pageable: Pageable): Page<Listing> {
        val caller = userRepository.findById(callerUserId).orElseThrow { ListingNotFoundException("User not found") }
        val neighborhood = caller.neighborhood
            ?: throw NeighborhoodNotSetException("Set your neighborhood first via POST /api/v1/auth/profile/neighborhood")
        // Real dual-neighborhood support (2026-08-04) -- see User.secondNeighborhood's own
        // doc comment. A set second neighborhood is included alongside the primary one, not
        // instead of it.
        val neighborhoods = listOfNotNull(neighborhood, caller.secondNeighborhood)
        return if (category.isNullOrBlank()) {
            listingRepository.findByStatusAndNeighborhoodInOrderByCreatedAtDesc(ListingStatus.ACTIVE, neighborhoods, pageable)
        } else {
            listingRepository.findByStatusAndNeighborhoodInAndCategoryOrderByCreatedAtDesc(ListingStatus.ACTIVE, neighborhoods, category, pageable)
        }
    }

    // Real proximity search (2026-07-18) -- the hyperlocal-discovery gap this class's own
    // doc comment originally named as impossible without real location data, now closed
    // for listings that have set one. Ranked over the bounded set of ACTIVE listings that
    // have coordinates (see ListingRepository's own note on why this is in-app, not a
    // real geospatial DB index, at current scale).
    //
    // Real road-distance ranking (2026-07-19) -- upgrades this from straight-line-only to
    // itunda's own self-hosted OSRM when available, via one batched `/table` call rather
    // than N `/route` calls (see OsrmRoutingClient.routeDistancesKm). GeoUtils.haversineKm
    // still does two real jobs first: (1) a cheap pre-filter/candidate bound before ever
    // calling OSRM -- since real road distance is always >= straight-line distance, any
    // listing within radiusKm by road is guaranteed to already be within radiusKm by
    // Haversine, so this can only ever admit a safe superset, never wrongly exclude a
    // true match; (2) the honest per-listing fallback whenever OSRM is unconfigured,
    // unreachable, or returns no route for that one leg -- matching EatsOrderService's
    // own "never fail, never fabricate" OSRM convention, including the same
    // isWithinRwanda guard against OSRM silently snapping an out-of-Rwanda coordinate to
    // its nearest network node (see GeoUtils.isWithinRwanda's doc comment for the real bug
    // this once caused).
    fun nearby(latitude: Double, longitude: Double, radiusKm: Double, pageable: Pageable): Page<Listing> {
        if (!GeoUtils.isValidCoordinate(latitude, longitude)) {
            throw InvalidCoordinatesException("Latitude must be between -90 and 90, longitude between -180 and 180")
        }
        if (radiusKm <= 0.0) {
            throw InvalidCoordinatesException("radiusKm must be greater than zero")
        }
        val candidates = listingRepository.findByStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(ListingStatus.ACTIVE)
        val haversineRanked = candidates
            .map { it to GeoUtils.haversineKm(latitude, longitude, it.latitude!!, it.longitude!!) }
            .filter { (_, distanceKm) -> distanceKm <= radiusKm }

        val originInRwanda = GeoUtils.isWithinRwanda(latitude, longitude)
        val (inRwanda, outsideRwanda) = haversineRanked.partition { (listing, _) ->
            originInRwanda && GeoUtils.isWithinRwanda(listing.latitude!!, listing.longitude!!)
        }
        val roadRanked = if (osrmRoutingClient.isConfigured && inRwanda.isNotEmpty() && inRwanda.size <= MAX_OSRM_TABLE_CANDIDATES) {
            val destinations = inRwanda.map { (listing, _) -> listing.latitude!! to listing.longitude!! }
            val roadDistances = osrmRoutingClient.routeDistancesKm(latitude, longitude, destinations)
            inRwanda.mapIndexed { index, (listing, haversineDistanceKm) ->
                listing to (roadDistances.getOrNull(index) ?: haversineDistanceKm)
            }
        } else {
            inRwanda
        }

        // Real road distance can exceed the Haversine straight line, so a listing that
        // passed the Haversine pre-filter can still legitimately fall outside radiusKm
        // once ranked by real road distance -- re-applied here, not assumed.
        val sorted = (roadRanked + outsideRwanda)
            .filter { (_, distanceKm) -> distanceKm <= radiusKm }
            .sortedBy { (_, distanceKm) -> distanceKm }
            .map { (listing, _) -> listing }

        val start = (pageable.pageNumber * pageable.pageSize).coerceAtMost(sorted.size)
        val end = (start + pageable.pageSize).coerceAtMost(sorted.size)
        return PageImpl(sorted.subList(start, end), pageable, sorted.size.toLong())
    }

    // Real relevance-ranked search (2026-08-14) -- see ListingRepository.searchFullText's
    // own doc comment. Deliberately not neighborhood-scoped, unlike myNeighborhood above:
    // search should span the whole marketplace by default, matching real Google/Naver
    // search behavior (results aren't silently limited to "near me" unless the user asks
    // for that). Falls back to the plain substring match for queries too short for
    // MySQL's FULLTEXT (see FullTextSearchUtil's own doc comment).
    fun search(query: String, pageable: Pageable): Page<Listing> {
        val booleanQuery = FullTextSearchUtil.toBooleanModeQuery(query)
        return if (booleanQuery != null) {
            listingRepository.searchFullText(ListingStatus.ACTIVE, booleanQuery, pageable)
        } else {
            listingRepository.searchShort(ListingStatus.ACTIVE, query.trim(), pageable)
        }
    }

    // Real Karrot "이 글 숨기기" (hide this post) browse exclusion (2026-08-24) -- see
    // ListingHideService's own doc comment for the full sourced account. `userId` is
    // optional so every existing caller (this method has no auth requirement itself)
    // keeps working unchanged; the controller now always passes the real authenticated
    // caller's id, so in practice this always filters for a real logged-in browser.
    fun browse(pageable: Pageable, category: String?, userId: String? = null): Page<Listing> {
        val now = Instant.now()
        val hiddenIds = if (userId != null) listingHideRepository.findListingIdsByUserId(userId).toSet() else emptySet()
        return if (category.isNullOrBlank()) {
            if (hiddenIds.isEmpty()) listingRepository.findByStatusOrderByBoostedThenCreatedAtDesc(ListingStatus.ACTIVE, now, pageable)
            else listingRepository.findByStatusAndIdNotInOrderByBoostedThenCreatedAtDesc(ListingStatus.ACTIVE, hiddenIds, now, pageable)
        } else {
            if (hiddenIds.isEmpty()) listingRepository.findByStatusAndCategoryOrderByBoostedThenCreatedAtDesc(ListingStatus.ACTIVE, category, now, pageable)
            else listingRepository.findByStatusAndCategoryAndIdNotInOrderByBoostedThenCreatedAtDesc(ListingStatus.ACTIVE, category, hiddenIds, now, pageable)
        }
    }

    // Real batch "which of these listings has this viewer already liked" (2026-08-03)
    // -- see ListingLikeRepository.findLikedListingIds' own doc comment. Attached
    // alongside every browse/nearby/neighborhood/my-listings response the same way
    // trustScores() already is, so the heart's filled/unfilled state is correct on
    // first render, not just after a tap.
    fun likedListingIds(listings: Collection<Listing>, userId: String?): Set<String> {
        if (userId == null || listings.isEmpty()) return emptySet()
        return listingLikeRepository.findLikedListingIds(listings.map { it.id }, userId).toSet()
    }

    // Real idempotent like/unlike toggle -- same shape CommunityService.toggleLike
    // already established (real cached counter, DB-unique constraint as the real
    // concurrency guard, real rate limit from day one).
    @Transactional
    fun toggleLike(userId: String, listingId: String): Boolean {
        val listing = listingRepository.findById(listingId).orElseThrow { ListingNotFoundException("Listing not found") }
        rateLimiter.checkLimit("marketplace:like:$userId", limit = 60, window = Duration.ofMinutes(1))

        val existing = listingLikeRepository.findByListingIdAndUserId(listingId, userId)
        return if (existing != null) {
            listingLikeRepository.delete(existing)
            listing.likeCount = (listing.likeCount - 1).coerceAtLeast(0)
            listingRepository.save(listing)
            false
        } else {
            listingLikeRepository.save(ListingLike(id = "listing_like_${UUID.randomUUID()}", listingId = listingId, userId = userId))
            listing.likeCount += 1
            listingRepository.save(listing)
            true
        }
    }

    // Real 당근마켓 신고하기 (report a listing) used to live here as its own endpoint --
    // retired in favor of the unified HoodReportService (`POST /api/v1/hood/reports`,
    // HoodReportTargetType.MARKETPLACE_LISTING), which is the one every real client
    // (Android/iOS/bank-mfe) actually calls; this one had zero callers. Its real
    // auto-hide-at-threshold behavior was ported into HoodReportService.report rather
    // than lost -- see docs/DESIGN_REFERENCES.md Section 249.

    /**
     * Real seller-paid sponsored placement -- see `Listing.boostedUntil`'s own doc
     * comment for the two real, sourced models this chose between. A flat, real payment
     * (seller account debited, 100% to `fee_revenue` -- this is a direct service
     * purchase from itunda, not a marketplace transaction between two parties, so there
     * is no counterparty leg) extends `boostedUntil` by the purchased tier's real
     * duration -- stacking (buying more boost time on an already-boosted listing pushes
     * `boostedUntil` further out rather than overwriting it), the same honest "value
     * adds, never silently discarded" mechanic a real production ad product would need.
     */
    @Transactional
    fun boostListing(sellerId: String, listingId: String, days: Int): Listing {
        val listing = requireOwner(sellerId, listingId)
        if (listing.status != ListingStatus.ACTIVE) {
            throw ListingNotActiveException("Only an ACTIVE listing can be boosted")
        }
        val price = BOOST_TIERS[days]
            ?: throw InvalidBoostDurationException("Choose a real boost duration -- ${BOOST_TIERS.keys.sorted().joinToString()} days")

        val sellerAccount = accountRepository.findByUserIdAndType(sellerId, AccountType.MAIN)
            ?: throw SellerNoAccountException("No account found for this account")

        ledgerService.postLedgerTransaction(
            sellerAccount.currency,
            listOf(
                LedgerLeg(sellerAccount.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, price, "Boost listing \"${listing.title}\" for $days days"),
                LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, price, "Listing boost -- ${listing.title}"),
            ),
        )

        val now = Instant.now()
        val currentBoostedUntil = listing.boostedUntil?.takeIf { it.isAfter(now) } ?: now
        listing.boostedUntil = currentBoostedUntil.plus(Duration.ofDays(days.toLong()))
        return listingRepository.save(listing)
    }

    // Real 당근마켓 끌어올리기 (bump to top of feed) -- see Listing.bumpedAt's own doc
    // comment. Free and self-serve, unlike boostListing above (a real paid, guaranteed
    // top-of-feed placement) -- a different, complementary mechanic real 당근 also
    // keeps separate: bump only reorders you above other organic (non-boosted)
    // listings, a boosted listing still sorts first regardless (see the repository's
    // CASE-first ordering). Once-per-24h cooldown against real spam, measured from
    // whichever of createdAt/bumpedAt is more recent -- a freshly created listing
    // can't be immediately bumped again either.
    fun bumpListing(sellerId: String, listingId: String): Listing {
        val listing = requireOwner(sellerId, listingId)
        if (listing.status != ListingStatus.ACTIVE) {
            throw ListingNotActiveException("Only an ACTIVE listing can be bumped")
        }
        val now = Instant.now()
        val lastBump = listing.bumpedAt ?: listing.createdAt
        val nextEligible = lastBump.plus(BUMP_COOLDOWN)
        if (nextEligible.isAfter(now)) {
            throw ListingBumpCooldownException("You can bump this listing again in ${Duration.between(now, nextEligible).toMinutes()} minutes")
        }
        listing.bumpedAt = now
        return listingRepository.save(listing)
    }

    /**
     * Real 가격 수정 (price edit) + Karrot 가격 하락 알림 (price-drop alert on a favorited/
     * 관심 listing). itunda's `MarketplaceService` had no price-edit capability of any
     * kind until now; this closes both that gap and the real notification gap it left
     * behind on `ListingFavorite`, mirroring `PropertyListingService.updatePrice`'s own
     * exact real sourcing (Karrot's own real-estate-arm transaction-notification
     * categories explicitly name "가격 하락" on a favorited/관심 listing, corroborated by
     * the same real Clien community thread that service's doc comment already cites:
     * "당근마켓 가격만 내리면 관심유저에게 알람가나요?"). Only a real price DECREASE notifies,
     * matching that sourced "가격 하락" scoping exactly -- not any price edit.
     *
     * Deliberately NOT `@Transactional` itself -- the single `listingRepository.save`
     * below is already atomic on its own via Spring Data's implicit per-call
     * transaction, same reasoning `PropertyListingService.updatePrice`/
     * `ProductSubscriptionService.executeOne` already establish: the price-drop
     * notification loop below makes its own separate `notificationRepository.save`
     * calls after the price change, so a `@Transactional` boundary here would let one
     * failing notification save mark this method's ambient transaction rollback-only
     * and roll back the real price change along with it -- the exact self-invocation/
     * transaction-poisoning pitfall closed in Sections 115/118.
     */
    fun updatePrice(sellerId: String, listingId: String, newPrice: BigDecimal): Listing {
        if (newPrice <= BigDecimal.ZERO) {
            throw InvalidListingPriceException("Price must be greater than zero")
        }
        val listing = requireOwner(sellerId, listingId)
        if (listing.status != ListingStatus.ACTIVE) {
            throw ListingNotActiveException("Only an ACTIVE listing's price can be changed")
        }
        val oldPrice = listing.price
        listing.price = newPrice
        val saved = listingRepository.save(listing)
        if (newPrice < oldPrice) {
            notifyFavoritersOfPriceDrop(listingId, listing.title, oldPrice, newPrice)
        }
        return saved
    }

    // Real per-favoriter resilience -- a notification failure for one favoriter must
    // never affect another's, same per-row discipline PropertyListingService
    // .notifyFavoritersOfPriceDrop already establishes. Each notificationRepository
    // .save call here is independently atomic since updatePrice above is deliberately
    // not @Transactional (see its own doc comment).
    private fun notifyFavoritersOfPriceDrop(listingId: String, title: String, oldPrice: BigDecimal, newPrice: BigDecimal) {
        val favorites = listingFavoriteRepository.findByListingId(listingId)
        for (favorite in favorites) {
            try {
                val notifTitle = "Price drop on a listing you favorited"
                val body = "\"$title\" dropped from ${oldPrice.toPlainString()} to ${newPrice.toPlainString()} RWF"
                notificationRepository.save(
                    Notification(
                        id = "notif_${UUID.randomUUID()}", userId = favorite.userId, type = "LISTING_PRICE_DROP",
                        title = notifTitle, body = body, isRead = false, createdAt = Instant.now(),
                        dataJson = "{\"listingId\":\"$listingId\"}",
                    ),
                )
                pushNotificationService.sendToUser(favorite.userId, notifTitle, body, mapOf("listingId" to listingId))
            } catch (e: Exception) {
                log.error("Price-drop notification failed for user {} on listing {}", favorite.userId, listingId, e)
            }
        }
    }

    /**
     * Real "pay via itunda" escrow -- see `MarketplaceEscrow`'s own doc comment for the
     * full account. Opt-in alongside the existing in-person cash handoff, never
     * replacing it: a buyer who'd rather trade through the app pays here instead of
     * meeting up with cash. Marks the listing SOLD immediately (the buyer has real
     * committed money on it), same as `markSold`, but the money sits in
     * `marketplace_escrow_holding` until `confirmReceipt` releases it.
     *
     * Real bug found live (2026-08-02): unlike every other real "request a paid
     * service" creation method in this codebase (`VehicleInspectionService.
     * requestInspection`, `RideTripService.requestTrip`, `DesignatedDriverService.
     * requestTrip`, `BusService.bookSeats` -- all real 20/hour), this had no rate
     * limit at all, even though `rateLimiter` is already a dependency of this exact
     * class (`createListing` below uses it). A buyer could spam-pay-escrow across many
     * listings with no real anti-abuse bound, each call holding real money in
     * `marketplace_escrow_holding`.
     */
    @Transactional
    fun payEscrow(buyerId: String, listingId: String, deliveryAddress: String? = null): MarketplaceEscrow {
        rateLimiter.checkLimit("marketplace:pay-escrow:$buyerId", limit = 20, window = Duration.ofHours(1))
        val listing = listingRepository.findById(listingId).orElseThrow { ListingNotFoundException("Listing not found") }
        if (listing.status != ListingStatus.ACTIVE) {
            throw ListingNotActiveException("Only an active listing can be paid for")
        }
        if (listing.sellerId == buyerId) {
            throw OwnListingException("Cannot buy your own listing")
        }
        val buyerAccount = accountRepository.findByUserIdAndType(buyerId, AccountType.MAIN)
            ?: throw BuyerNoAccountException("No account found for this account")
        val seller = userRepository.findById(listing.sellerId).orElseThrow { ListingNotFoundException("Listing not found") }
        val sellerAccount = accountRepository.findByUserIdAndType(seller.id, AccountType.MAIN)
            ?: throw SellerNoAccountException("Seller has no account to receive this payment")

        val fee = listing.price.multiply(ESCROW_FEE_RATE).setScale(2, RoundingMode.HALF_UP)
        val result = ledgerService.postLedgerTransaction(
            buyerAccount.currency,
            listOf(
                LedgerLeg(buyerAccount.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, listing.price, "Escrow payment - ${listing.title}"),
                LedgerLeg("marketplace_escrow_holding", LedgerAccountType.MARKETPLACE_ESCROW_HOLDING, LedgerDirection.CREDIT, listing.price, "Escrow held - ${listing.title}"),
            ),
        )
        // Real, sourced follow-up named in FraudRuleEngine's own doc comment: a
        // marketplace-seller payment is a real money-to-a-named-recipient flow, same
        // evaluate-before-save ordering OrderService/P2pService/MerchantService already
        // establish.
        fraudRuleEngine.evaluate(buyerId, seller.id, listing.price, result.transactionId)
        transactionRepository.save(
            Transaction(
                id = result.transactionId,
                referenceNumber = "ESCROW${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
                senderId = buyerId,
                recipientId = seller.id,
                fromAccountId = buyerAccount.id,
                toAccountId = sellerAccount.id,
                amount = listing.price,
                fee = fee,
                currency = buyerAccount.currency,
                type = TransactionType.PAYMENT,
                status = TransactionStatus.COMPLETED,
                description = "Escrow payment - ${listing.title}",
                channel = "MARKETPLACE_ESCROW",
                completedAt = Instant.now(),
            ),
        )

        listing.status = ListingStatus.SOLD
        listing.buyerId = buyerId
        listingRepository.save(listing)

        val escrow = marketplaceEscrowRepository.save(
            MarketplaceEscrow(
                id = "marketplace_escrow_${UUID.randomUUID()}", listingId = listingId, buyerId = buyerId, sellerId = seller.id,
                amount = listing.price, fee = fee, holdTransactionId = result.transactionId,
                deliveryAddress = deliveryAddress?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
        trustScoreService.computeScore(seller.id)
        val conversation = messagingService.startOrGetConversation(buyerId, seller.id)
        messagingService.sendMessage(buyerId, conversation.id, "💳 Paid for \"${listing.title}\" through itunda -- the seller gets paid once you confirm you received it.")
        return escrow
    }

    /** Real read for the buyer or seller of a listing to check its own escrow status --
     * same "don't reveal a resource exists to someone who shouldn't see it" discipline
     * `requireOwner` already establishes. */
    fun getEscrow(requesterId: String, listingId: String): MarketplaceEscrow {
        val escrow = requireEscrow(listingId)
        if (escrow.buyerId != requesterId && escrow.sellerId != requesterId) {
            throw MarketplaceEscrowNotFoundException("No escrow payment found for this listing")
        }
        return escrow
    }

    private fun requireEscrow(listingId: String): MarketplaceEscrow =
        marketplaceEscrowRepository.findByListingId(listingId) ?: throw MarketplaceEscrowNotFoundException("No escrow payment found for this listing")

    /** Real release -- the buyer confirms they received the real item, paying the
     * seller (minus itunda's real escrow fee) out of holding. */
    @Transactional
    fun confirmReceipt(buyerId: String, listingId: String): MarketplaceEscrow {
        val escrow = requireEscrow(listingId)
        if (escrow.buyerId != buyerId) {
            throw MarketplaceEscrowNotFoundException("No escrow payment found for this listing")
        }
        if (escrow.status != MarketplaceEscrowStatus.HELD) {
            throw InvalidEscrowStatusException("This escrow is already ${escrow.status}")
        }
        val saved = releaseEscrowToSeller(escrow)
        val listing = listingRepository.findById(listingId).orElse(null)
        if (listing != null) {
            val conversation = messagingService.startOrGetConversation(buyerId, escrow.sellerId)
            messagingService.sendMessage(buyerId, conversation.id, "✅ Confirmed receipt of \"${listing.title}\" -- payment released to the seller.")
        }
        return saved
    }

    /** Real dispute -- the buyer flags a real problem (no-show, not as described)
     * instead of confirming receipt. Awaits real human admin review -- see
     * `MarketplaceEscrow`'s own doc comment for why this isn't auto-resolved. */
    @Transactional
    fun disputeEscrow(buyerId: String, listingId: String, reason: String): MarketplaceEscrow {
        val trimmedReason = reason.trim()
        if (trimmedReason.isEmpty() || trimmedReason.length > 500) {
            throw InvalidDisputeReasonException("Explain what went wrong in 500 characters or fewer")
        }
        val escrow = requireEscrow(listingId)
        if (escrow.buyerId != buyerId) {
            throw MarketplaceEscrowNotFoundException("No escrow payment found for this listing")
        }
        if (escrow.status != MarketplaceEscrowStatus.HELD) {
            throw InvalidEscrowStatusException("This escrow is already ${escrow.status}")
        }
        escrow.status = MarketplaceEscrowStatus.DISPUTED
        escrow.disputeReason = trimmedReason
        escrow.updatedAt = Instant.now()
        return marketplaceEscrowRepository.save(escrow)
    }

    fun getPendingDisputes(): List<MarketplaceEscrow> =
        marketplaceEscrowRepository.findByStatusOrderByCreatedAtAsc(MarketplaceEscrowStatus.DISPUTED)

    // Real scheduled auto-release-after-timeout (2026-07-27) -- see
    // MarketplaceEscrow.AUTO_RELEASE_TIMEOUT's own doc comment for the real sourced
    // window, and MarketplaceEscrowAutoReleaseScheduler's own doc comment for the
    // scheduler shape. A still-HELD escrow past the real timeout with no dispute
    // raised is exactly a buyer who simply never opened the app to confirm -- the same
    // real "구매확정" auto-processing every major Korean e-commerce platform performs,
    // not a silent, undocumented default.
    fun getEscrowsDueForAutoRelease(): List<MarketplaceEscrow> =
        marketplaceEscrowRepository.findByStatus(MarketplaceEscrowStatus.HELD)
            .filter { Duration.between(it.createdAt, Instant.now()) >= MarketplaceEscrow.AUTO_RELEASE_TIMEOUT }

    @Transactional
    fun autoReleaseEscrow(escrowId: String) {
        val escrow = marketplaceEscrowRepository.findById(escrowId).orElse(null) ?: return
        if (escrow.status != MarketplaceEscrowStatus.HELD) return
        val saved = releaseEscrowToSeller(escrow)
        val listing = listingRepository.findById(saved.listingId).orElse(null)
        if (listing != null) {
            val conversation = messagingService.startOrGetConversation(saved.buyerId, saved.sellerId)
            messagingService.sendMessage(
                saved.sellerId, conversation.id,
                "✅ Auto-confirmed after ${MarketplaceEscrow.AUTO_RELEASE_TIMEOUT.toDays()} days -- payment for \"${listing.title}\" has been released to you.",
            )
        }
    }

    /** Real admin resolution -- `release = true` pays the seller (the trade was
     * legitimate), `false` refunds the buyer in full, no fee charged, and reopens the
     * listing for sale again (the trade genuinely didn't happen). */
    @Transactional
    fun resolveDispute(escrowId: String, release: Boolean): MarketplaceEscrow {
        val escrow = marketplaceEscrowRepository.findById(escrowId).orElseThrow { MarketplaceEscrowNotFoundException("Escrow not found") }
        if (escrow.status != MarketplaceEscrowStatus.DISPUTED) {
            throw InvalidEscrowStatusException("Only a DISPUTED escrow can be resolved -- this one is ${escrow.status}")
        }
        return if (release) {
            releaseEscrowToSeller(escrow)
        } else {
            refundEscrowToBuyer(escrow)
        }
    }

    private fun releaseEscrowToSeller(escrow: MarketplaceEscrow): MarketplaceEscrow {
        val sellerAccount = accountRepository.findByUserIdAndType(escrow.sellerId, AccountType.MAIN)
            ?: throw SellerNoAccountException("Seller has no account to receive this payment")
        val netToSeller = escrow.amount.subtract(escrow.fee)
        val result = ledgerService.postLedgerTransaction(
            sellerAccount.currency,
            listOf(
                LedgerLeg("marketplace_escrow_holding", LedgerAccountType.MARKETPLACE_ESCROW_HOLDING, LedgerDirection.DEBIT, escrow.amount, "Escrow released"),
                LedgerLeg(sellerAccount.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, netToSeller, "Escrow release"),
                LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, escrow.fee, "Marketplace escrow fee"),
            ),
        )
        escrow.status = MarketplaceEscrowStatus.RELEASED
        escrow.resolutionTransactionId = result.transactionId
        escrow.updatedAt = Instant.now()
        val saved = marketplaceEscrowRepository.save(escrow)
        val title = listingRepository.findById(escrow.listingId).map { it.title }.orElse("your listing")
        notifyDisputeResolved(
            escrow,
            winnerId = escrow.sellerId,
            winnerBody = "The dispute for \"$title\" was resolved in your favor. ${netToSeller.toPlainString()} RWF has been credited to your account.",
            loserId = escrow.buyerId,
            loserBody = "The dispute for \"$title\" was resolved in the seller's favor. The payment has been released to them.",
        )
        return saved
    }

    private fun refundEscrowToBuyer(escrow: MarketplaceEscrow): MarketplaceEscrow {
        val buyerAccount = accountRepository.findByUserIdAndType(escrow.buyerId, AccountType.MAIN)
            ?: throw BuyerNoAccountException("No account found for this account")
        val result = ledgerService.postLedgerTransaction(
            buyerAccount.currency,
            listOf(
                LedgerLeg("marketplace_escrow_holding", LedgerAccountType.MARKETPLACE_ESCROW_HOLDING, LedgerDirection.DEBIT, escrow.amount, "Escrow refunded"),
                LedgerLeg(buyerAccount.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, escrow.amount, "Escrow refund"),
            ),
        )
        escrow.status = MarketplaceEscrowStatus.REFUNDED
        escrow.resolutionTransactionId = result.transactionId
        escrow.updatedAt = Instant.now()
        val saved = marketplaceEscrowRepository.save(escrow)
        // Real reopen -- the trade genuinely didn't happen, so the listing goes back to
        // real ACTIVE/sellable, same honest state as before payEscrow was ever called.
        val listing = listingRepository.findById(escrow.listingId).orElse(null)
        if (listing != null && listing.status == ListingStatus.SOLD && listing.buyerId == escrow.buyerId) {
            listing.status = ListingStatus.ACTIVE
            listing.buyerId = null
            listingRepository.save(listing)
        }
        val title = listing?.title ?: "the listing"
        notifyDisputeResolved(
            escrow,
            winnerId = escrow.buyerId,
            winnerBody = "The dispute for \"$title\" was resolved in your favor. ${escrow.amount.toPlainString()} RWF has been refunded to your account.",
            loserId = escrow.sellerId,
            loserBody = "The dispute for \"$title\" was resolved in the buyer's favor. The payment has been refunded to them.",
        )
        return saved
    }

    // Real admin dispute resolution notifies BOTH real parties -- itunda's own honest
    // extension of the same "decide = terminal action, tell the real people it happened
    // to" discipline OrderReturnService.decide/InsuranceService.decideClaim already
    // establish for a single-subject decision; resolveDispute has two, since it's an
    // admin picking a winner between a real buyer and a real seller, not one party
    // deciding about the other. Notification rows saved immediately (same @Transactional
    // boundary as the real ledger/status change above), push deferred until commit via
    // sendPushAfterCommit, same shape those two services already establish.
    private fun notifyDisputeResolved(escrow: MarketplaceEscrow, winnerId: String, winnerBody: String, loserId: String, loserBody: String) {
        val winnerTitle = "Dispute resolved in your favor"
        val loserTitle = "Dispute resolved"
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = winnerId, type = "MARKETPLACE_DISPUTE_RESOLVED",
                title = winnerTitle, body = winnerBody, isRead = false, createdAt = Instant.now(),
                dataJson = "{\"escrowId\":\"${escrow.id}\"}",
            ),
        )
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = loserId, type = "MARKETPLACE_DISPUTE_RESOLVED",
                title = loserTitle, body = loserBody, isRead = false, createdAt = Instant.now(),
                dataJson = "{\"escrowId\":\"${escrow.id}\"}",
            ),
        )
        sendPushAfterCommit(winnerId, winnerTitle, winnerBody, escrow.id)
        sendPushAfterCommit(loserId, loserTitle, loserBody, escrow.id)
    }

    // Same real "defer the mobile push until the real ledger/status change is durable,
    // but the in-app Notification row is saved immediately" discipline
    // OrderReturnService.sendPushAfterCommit/InsuranceService.sendPushAfterCommit already
    // establish for a structurally identical terminal decision.
    private fun sendPushAfterCommit(userId: String, title: String, body: String, escrowId: String) {
        val data = mapOf("escrowId" to escrowId)
        val send = {
            try {
                pushNotificationService.sendToUser(userId, title, body, data)
            } catch (e: Exception) {
                log.warn("Could not send dispute-resolution push for escrow {}", escrowId, e)
            }
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = send()
        })
    }

    // Any status, not just ACTIVE -- a buyer who already contacted a seller about a
    // now-SOLD item should still be able to open the listing (real 당근마켓 shows a
    // "판매완료"/sold badge rather than 404ing it), and a seller needs to see their own
    // REMOVED listings too. `browse()` above is what actually hides non-ACTIVE ones
    // from general discovery.
    // Real 당근마켓 조회수 (view count) -- see ListingRepository.incrementViewCount's own
    // doc comment for the atomic-update reasoning. The fetched entity predates the
    // increment, so the returned viewCount is bumped by 1 in memory to reflect this
    // real view without a second round-trip read.
    @Transactional
    fun getListing(listingId: String): Listing {
        val listing = listingRepository.findById(listingId).orElseThrow { ListingNotFoundException("Listing not found") }
        listingRepository.incrementViewCount(listingId)
        listing.viewCount += 1
        return listing
    }

    fun getMyListings(sellerId: String, pageable: Pageable): Page<Listing> =
        listingRepository.findBySellerIdOrderByCreatedAtDesc(sellerId, pageable)

    // Real "My purchases" (2026-07-25) -- see ListingRepository.findByBuyerIdOrderByCreatedAtDesc's
    // own doc comment for the full account of why this is newly buildable.
    fun getMyPurchases(buyerId: String, pageable: Pageable): Page<Listing> =
        listingRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId, pageable)

    // Real optional buyer identification (2026-07-24) -- buyerPhoneNumber is
    // deliberately optional: the sale completes normally either way, but only a sale
    // that recorded a real buyer can ever carry a post-transaction review (see
    // HoodReviewService's own doc comment for why an unidentified buyer means there's
    // structurally no one to review). Resolved the same "phone number identifies a
    // person" way P2pService.sendDirect already established -- an unresolvable number
    // is a real, honest 404, not a silently-ignored typo.
    @Transactional
    fun markSold(sellerId: String, listingId: String, buyerPhoneNumber: String? = null): Listing {
        val listing = requireOwner(sellerId, listingId)
        if (listing.status != ListingStatus.ACTIVE) {
            throw ListingNotActiveException("Only an active listing can be marked sold")
        }
        val trimmedPhone = buyerPhoneNumber?.trim()
        if (!trimmedPhone.isNullOrEmpty()) {
            val buyer = userRepository.findByPhoneNumber(trimmedPhone)
                ?: throw BuyerNotFoundException("No itunda account found for this phone number")
            if (buyer.id == sellerId) throw OwnListingException("You can't record yourself as the buyer")
            listing.buyerId = buyer.id
        }
        listing.status = ListingStatus.SOLD
        val saved = listingRepository.save(listing)
        // Real Karrot-Score-style trust badge (2026-07-21) -- see TrustScoreService's own
        // doc comment. Recomputed right here, not lazily on next view, so the seller's
        // badge reflects this completed sale immediately.
        trustScoreService.computeScore(sellerId)
        // Real 당근마켓-style review-prompt system message (2026-07-25) -- Karrot's own
        // real product sends a chat message into the transaction's conversation the
        // moment a seller marks a listing sold, prompting the buyer to leave a review
        // (medium.com/daangn's own tech blog on their review-experiment work). Only
        // possible once a real buyer was actually captured above -- an unidentified
        // buyer has no conversation to send into, same "no counterparty, no review"
        // constraint HoodReviewService itself already enforces. Sent as the seller's own
        // message (this codebase has no system/bot sender concept yet), same convention
        // SplitBillService's settlement-confirmation message already established.
        listing.buyerId?.let { buyerId ->
            val conversation = messagingService.startOrGetConversation(sellerId, buyerId)
            messagingService.sendMessage(
                sellerId, conversation.id,
                "✅ Marked \"${listing.title}\" as sold. If everything went well, leave a review so other neighbors know what to expect!",
            )
        }
        return saved
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
