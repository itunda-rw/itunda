package rw.itunda.core.network

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

// Mirrors services/backend/messaging's real DTOs exactly (2026-07-18) -- backs the new
// "Talk" bottom-nav tab (Kakao-style 1:1 chat). See rw.itunda.messaging.MessagingService
// / MessagingController's own doc comments for the full backend account, including the
// honest "poll-based delivery, no live transport yet" scope this mobile client matches.

data class JobPostResponse(val success: Boolean, val post: JobPostDto)
data class JobPostsResponse(val success: Boolean, val posts: List<JobPostDto>, val trustScores: Map<String, Int> = emptyMap(), val page: Int = 0, val totalPages: Int = 1, val totalElements: Int = 0)
data class JobCategoriesResponse(val success: Boolean, val categories: List<JobCategoryDto>)
data class FavoriteJobPostDto(val jobPostId: String, val title: String, val payAmount: Double, val category: String, val favoritedAt: String)

// Real 당근마켓-style Keyword Alert -- mirrors KeywordAlert.kt/KeywordAlertQuietHours.kt
// exactly.
data class AddKeywordAlertRequest(val keyword: String)
data class KeywordAlertDto(val id: String, val userId: String, val keyword: String, val createdAt: String)
data class KeywordAlertResponse(val success: Boolean, val alert: KeywordAlertDto)
data class KeywordAlertsResponse(val success: Boolean, val alerts: List<KeywordAlertDto>)
data class SetKeywordAlertQuietHoursRequest(val startTime: String, val endTime: String, val enabled: Boolean)
data class KeywordAlertQuietHoursDto(val id: String, val userId: String, val startTime: String, val endTime: String, val enabled: Boolean)
data class KeywordAlertQuietHoursResponse(val success: Boolean, val quietHours: KeywordAlertQuietHoursDto?)
data class FavoriteJobPostsResponse(val success: Boolean, val favorites: List<FavoriteJobPostDto>, val page: Int = 0, val totalPages: Int = 1, val totalElements: Int = 0)

// Real 당근알바-style structured application (2026-07-25) -- see backend
// JobApplicationService's own doc comment.
data class ApplyToJobRequest(val message: String)
data class JobApplicationDto(
    val id: String, val jobPostId: String, val applicantId: String, val message: String,
    val status: String, val submittedAt: String, val respondedAt: String? = null,
    // Real résumé attach at submission time (2026-08-28) -- see backend
    // JobApplication.resumeSnapshotJson's own doc comment. A raw JSON string; only
    // ever parsed to show "Résumé attached" -- never re-rendered field-by-field here.
    val resumeSnapshotJson: String? = null,
)
data class JobApplicationResponse(val success: Boolean, val application: JobApplicationDto, val conversation: ConversationDto? = null)
data class JobApplicationsResponse(val success: Boolean, val applications: List<JobApplicationDto>)
data class RespondToApplicationRequest(val accept: Boolean)
data class ContactPosterResponse(val success: Boolean, val conversation: ConversationDto)

// Real 당근부동산-style property listing (2026-07-19) -- see rw.itunda.realestate.web.PropertyListingController.
data class PropertyTypeDto(val id: String, val label: String)
data class PropertyListingDto(
    val id: String, val listerId: String, val listingType: String, val propertyType: String,
    val title: String, val description: String, val price: Double, val bedrooms: Int? = null, val sizeSqm: Double? = null,
    val status: String, val createdAt: String, val latitude: Double? = null, val longitude: Double? = null,
    // counterpartyId added 2026-07-24 -- real optional buyer/tenant identification
    // captured at mark-taken time, see backend PropertyListing.kt's own doc comment.
    // Only set once a real review becomes possible for this transaction.
    val counterpartyId: String? = null,
    // Real ownership verification (2026-07-25) -- NONE/PENDING/VERIFIED, see backend
    // PropertyOwnershipSubmission's own doc comment.
    val ownershipVerificationStatus: String = "NONE",
    // Real hyperlocal neighborhood -- the backend has stamped this on every listing
    // since 2026-07-20 (PropertyListingService reverse-geocodes it, falling back to the
    // lister's own neighborhood) and serializes the entity directly on every browse
    // endpoint, but this DTO omitted the field until 2026-08-14, so Jackson dropped it
    // and Property rows could never show the hyperlocal label Hood shows on every row.
    val neighborhood: String? = null,
)
data class MarkTakenRequest(val counterpartyPhoneNumber: String? = null)
data class SubmitOwnershipVerificationRequest(val documentUrl: String)
data class PropertyOwnershipSubmissionDto(
    val id: String, val listingId: String, val userId: String, val documentUrl: String,
    val status: String, val submittedAt: String,
)
data class PropertyOwnershipSubmissionResponse(val success: Boolean, val submission: PropertyOwnershipSubmissionDto)
data class CreatePropertyListingRequest(
    val listingType: String, val propertyType: String, val title: String, val description: String, val price: Double,
    val bedrooms: Int? = null, val sizeSqm: Double? = null, val latitude: Double? = null, val longitude: Double? = null,
)
data class PropertyListingResponse(val success: Boolean, val listing: PropertyListingDto)
data class PropertyListingsResponse(val success: Boolean, val listings: List<PropertyListingDto>, val trustScores: Map<String, Int> = emptyMap(), val page: Int = 0, val totalPages: Int = 1, val totalElements: Int = 0)
data class FavoritePropertyListingDto(val propertyListingId: String, val title: String, val price: Double, val listingType: String, val favoritedAt: String)
data class FavoritePropertyListingsResponse(val success: Boolean, val favorites: List<FavoritePropertyListingDto>, val page: Int = 0, val totalPages: Int = 1, val totalElements: Int = 0)
data class PropertyTypesResponse(val success: Boolean, val propertyTypes: List<PropertyTypeDto>)
data class ContactListerResponse(val success: Boolean, val conversation: ConversationDto)

// Real Toss Bank 우리집 시세 (my home's estimated value, item 228) -- see the backend's
// PropertyListingService.estimateValue doc comment. Read-only, computed fresh from
// real comparable listings on every call, nothing persisted.
data class PropertyValuationEstimateDto(
    val estimatedValue: Double, val comparableCount: Int, val averagePricePerSqm: Double, val radiusKm: Double,
)
data class PropertyValuationResponse(val success: Boolean, val estimate: PropertyValuationEstimateDto)

// Real 당근-style price-offer negotiation on a real property listing (2026-07-19) -- see
// PropertyPriceOfferService's own doc comment. Mirrors PriceOfferDto field-for-field.
data class PropertyPriceOfferDto(
    val id: String,
    val propertyListingId: String,
    val messageId: String,
    val conversationId: String,
    val inquirerId: String,
    val listerId: String,
    val proposedByUserId: String,
    val amount: Double,
    val status: String,
    val createdAt: String,
    val respondedAt: String?,
)
data class MakePropertyOfferRequest(val amount: Double)
data class RespondToPropertyOfferRequest(val action: String, val counterAmount: Double? = null)
data class PropertyPriceOfferResponse(val success: Boolean, val offer: PropertyPriceOfferDto)
data class PropertyPriceOffersResponse(val success: Boolean, val offers: List<PropertyPriceOfferDto>)

data class CreateHoodReportRequest(val targetType: String, val targetId: String, val reason: String)
data class HoodReportResponse(val success: Boolean)

// Mirrors services/backend/commerce's real DTOs exactly (2026-07-18) -- backs the new
// "Shop" bottom-nav tab (Coupang-style multi-item checkout), replacing the old Shop
// tab's Toss-Shopping-cashback content. See rw.itunda.commerce.OrderService's own doc
// comment for the honest "self-declared fulfillment, no real courier network" scope.
data class ShoppingMerchantDto(
    val merchantId: String, val businessName: String, val category: String?, val cashbackRate: String,
    // Real optional location (2026-07-19) -- backs the real self-hosted Map view.
    val latitude: Double? = null, val longitude: Double? = null,
    // Real browse-card enrichment (2026-07-21) -- closes docs/DESIGN_REFERENCES.md's
    // Eats recommendations #1/#2. photoUrl/minOrderAmount are real, merchant-set (null
    // when unset); rating/reviewCount are real, batch-aggregated from EatsReview.
    // distanceKm/deliveryTimeMinutes are only present when this call supplied its own
    // real buyerLat/buyerLng -- deliveryTimeMinutes is a real, clearly-an-ESTIMATE
    // derived from that distance (see ShoppingController.estimateDeliveryMinutes's own
    // doc comment on the backend), never a fabricated/measured number.
    val photoUrl: String? = null,
    val minOrderAmount: Double? = null,
    val rating: Double? = null,
    val reviewCount: Long = 0,
    val distanceKm: Double? = null,
    val deliveryTimeMinutes: Int? = null,
    // Real merchant-set phone/hours (2026-08-09) -- see Merchant.kt's own doc comment on
    // the backend. Null unless the merchant has actually set one.
    val phoneNumber: String? = null,
    val openingHours: String? = null,
    // Real per-restaurant WOW membership badge + max-active-discount (2026-08-28,
    // see backend Merchant.participatesInEatsMembership / getMaxDiscountByMerchantIds).
    val participatesInEatsMembership: Boolean = false,
    val maxDiscountPercent: Int? = null,
)
data class ShoppingMerchantsResponse(val success: Boolean, val merchants: List<ShoppingMerchantDto>)

// Maps DTOs (search/directions/transit/place-detail/weather) live in MapsDtos.kt
// (extracted 2026-08-28, itunda Maps redesign, to stay under this file's own baseline).
data class MerchantCategoriesResponse(val success: Boolean, val categories: List<String>)

// Real "Deals" rail (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 5
// recommendation #8: a curated deal rail on the Shop landing surface. Every entry is a
// real merchant-set discount, never a fabricated promo -- see backend
// MerchantProductRepository.findDeals's own doc comment.
data class DealProductDto(
    val id: String, val merchantId: String, val merchantName: String, val name: String, val price: Double,
    val imageUrl: String? = null, val originalPrice: Double? = null, val discountPercent: Int? = null, val description: String? = null,
    val stockQuantity: Int? = null,
    // rating/reviewCount added 2026-08-25 -- real ProductReview data (batched GROUP BY
    // lookup, see ShoppingController.getDeals' own doc comment), not fabricated. null
    // rating means the product genuinely has zero reviews yet -- render no stars, not a
    // fake default.
    val rating: Double? = null, val reviewCount: Long = 0L,
    // Real "Best seller" badge (2026-08-28) -- a genuine, derived signal (real gross
    // order count per product, see backend ShoppingController.bestSellerProductIds'
    // own doc comment), not fabricated marketing copy.
    val isBestSeller: Boolean = false,
)
data class DealsResponse(val success: Boolean, val products: List<DealProductDto>)

// Real "frequently ordered together" item (2026-08-28) -- see getFrequentlyOrderedWith.
data class FrequentlyOrderedWithItemDto(val id: String, val merchantId: String, val merchantName: String, val name: String, val price: Double, val imageUrl: String? = null, val originalPrice: Double? = null, val discountPercent: Int? = null, val stockQuantity: Int? = null)
data class FrequentlyOrderedWithResponse(val success: Boolean, val products: List<FrequentlyOrderedWithItemDto>)

// Real Coupang Eats-style dish grid (2026-08-03) -- see backend EatsController.kt's
// own doc comment for the full 100%-UI/UX-parity sourcing. Same shape as
// DealProductDto above (this app's established "product-in-a-list" DTO shape) minus
// the discount fields, which don't apply here.
// Real Coupang Eats-style budget filter + "recommended for you" ranking (2026-08-16,
// "AI 개인화 메뉴 추천") -- recommended is real, not fabricated: true only when the
// buyer has actually ordered from that dish's restaurant before. See
// EatsController.getDishes' own doc comment on the backend.
data class EatsDishDto(val id: String, val merchantId: String, val merchantName: String, val name: String, val price: Double, val imageUrl: String? = null, val recommended: Boolean = false)
data class EatsDishesResponse(val success: Boolean, val dishes: List<EatsDishDto>)
data class MembershipDayStatusResponse(val success: Boolean, val isMembershipDay: Boolean, val multiplier: Double)

// Real Coupang 타임특가 (Time Deal, item 226) -- see the backend TimeDeal.kt's own doc
// comment. A time-boxed, quantity-capped discount OVERLAY on an existing product,
// distinct from the always-on "Deals" rail above (DealProductDto), which surfaces a
// permanent discountPercent/originalPrice, not a scheduled event. bank-mfe already has
// this; this is the first Android client (consumer browse only -- merchant creation
// lives on merchant-mfe, mirroring this app's existing consumer/merchant app split).
data class TimeDealDto(
    val id: String, val merchantId: String, val productId: String, val dealPrice: Double, val originalPrice: Double,
    val totalQuantity: Int, val remainingQuantity: Int, val startsAt: String, val endsAt: String, val createdAt: String,
)
data class TimeDealViewDto(val deal: TimeDealDto, val productName: String, val productImageUrl: String?, val businessName: String)
data class TimeDealsResponse(val success: Boolean, val deals: List<TimeDealViewDto>)

// Real Toss Shopping banner carousel (2026-08-12) -- see backend
// TimeDealService.getBanners's own doc comment. Same real shape as TimeDealViewDto
// above -- a banner IS a real active Time Deal, not a separate DTO for fabricated
// promotional content.
data class ShoppingBannersResponse(val success: Boolean, val banners: List<TimeDealViewDto>)

// Real Toss Shopping "포인트 및 쿠폰받기" (get points and coupons) mission row --
// see backend ShoppingMissionService's own doc comment. Every mission pays real RWF
// straight into the real account -- itunda has never had a separate "points" currency.
data class ShoppingMissionDto(
    val type: String,
    val label: String,
    val rewardAmount: java.math.BigDecimal,
    val completedToday: Boolean,
    val claimedEver: Boolean = false,
)
data class SpinOutcomeDto(val amount: java.math.BigDecimal, val odds: Double)
data class ShoppingMissionsResponse(val success: Boolean, val missions: List<ShoppingMissionDto>, val spinOutcomes: List<SpinOutcomeDto>)
data class MissionCompleteResponse(val success: Boolean, val type: String, val amountEarned: java.math.BigDecimal, val newAccountBalance: java.math.BigDecimal)

// Real Coupang 정기배송 (subscribe & save)-style recurring product delivery
// (rw.itunda.commerce's ProductSubscriptionService) -- real on bank-mfe since
// 2026-07-25, found 2026-08-01 with zero client on Android/iOS despite that.
// Honest scope boundary matches bank-mfe's own: 5% single-item discount only.
data class ProductSubscriptionDto(
    val id: String, val merchantId: String, val productId: String, val quantity: Int, val intervalDays: Int,
    val deliveryAddress: String, val status: String, val nextDeliveryAt: String, val createdAt: String,
    val lastDeliveredAt: String?, val deliveryCount: Int, val lastFailureReason: String?, val cancelledAt: String?,
)
