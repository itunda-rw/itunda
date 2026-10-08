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

// Mirrors services/backend/auth/src/main/kotlin/rw/itunda/auth/AuthDtos.kt exactly --
// same field names/nullability, so Gson deserializes the real backend's JSON directly.
data class CreateProductSubscriptionRequest(val merchantId: String, val productId: String, val quantity: Int, val intervalDays: Int, val deliveryAddress: String)
data class ProductSubscriptionResponse(val success: Boolean, val subscription: ProductSubscriptionDto)
data class ProductSubscriptionsResponse(val success: Boolean, val subscriptions: List<ProductSubscriptionDto>)

// Real "nearby places" category search + bookmarked/favorite places (2026-07-19) -- see
// rw.itunda.maps.MapsService's own doc comment on the backend. `MAP_NEARBY_CATEGORIES`
// mirrors bank-mfe's own hardcoded `NEARBY_CATEGORIES` list exactly.
data class NearbyPlaceDto(val displayName: String, val latitude: Double, val longitude: Double, val distanceKm: Double)
data class MapNearbyResponse(val success: Boolean, val places: List<NearbyPlaceDto>)
// Real "Smart Around"-style default map state (2026-08-04) -- see MapsService's own
// getAroundMe/getTrendingSavedPlaces doc comments for the real, re-verified Naver Map
// sourcing (brunch.co.kr/@bydot/4) and the honest scope decision.
data class MapAroundMeResponse(val success: Boolean, val places: List<NearbyPlaceDto>)
data class TrendingPlaceDto(val displayName: String, val latitude: Double, val longitude: Double, val saveCount: Long)
data class MapTrendingResponse(val success: Boolean, val places: List<TrendingPlaceDto>)
// Public-safe cash-point discovery. The backend deliberately omits tills, operator
// details, and cash availability; customers only need a name, location and distance.
data class NearbyAgentDto(val id: String, val displayName: String, val latitude: Double, val longitude: Double, val distanceKm: Double)
data class NearbyAgentsResponse(val success: Boolean, val agents: List<NearbyAgentDto>)
// folderName/color added 2026-07-22 -- see MapBookmark.kt's own doc comment on the
// backend (migration V73). Every bookmark belongs to exactly one named folder with its
// own pin color; a bookmark saved before this existed defaults into "Saved places" /
// "#F5A623" (the same star-yellow the ★ icon already used).
data class MapBookmarkDto(val id: String, val displayName: String, val latitude: Double, val longitude: Double, val folderName: String, val color: String, val isPublic: Boolean = false, val createdAt: String)
data class MapBookmarksResponse(val success: Boolean, val bookmarks: List<MapBookmarkDto>)
data class AddMapBookmarkRequest(val displayName: String, val latitude: Double, val longitude: Double, val folderName: String? = null, val color: String? = null)
data class AddMapBookmarkResponse(val success: Boolean, val bookmark: MapBookmarkDto)
// Real "move to folder" (2026-07-22) -- see MapsService.moveBookmark's own doc comment.
data class MoveMapBookmarkRequest(val folderName: String, val color: String)
data class MoveMapBookmarkResponse(val success: Boolean, val bookmark: MapBookmarkDto)
data class RemoveMapBookmarkResponse(val success: Boolean)
// Real Naver Map-style public/private folder + share (2026-08-04) -- see
// MapBookmark.isPublic's own doc comment on the backend.
data class SetMapFolderVisibilityRequest(val folderName: String, val isPublic: Boolean)
data class SetMapFolderVisibilityResponse(val success: Boolean, val updatedCount: Int)
data class SharedMapFolderResponse(val success: Boolean, val bookmarks: List<MapBookmarkDto>)
// Real Kakao Map-style "구독" (subscribe) -- see MapsService.subscribeToSharedFolder's
// own doc comment on the backend. Ported from bank-mfe (2026-08-18); Android already had
// the view half (SharedFolderSection) but no way to keep what it showed.
data class SubscribeToSharedMapFolderResponse(val success: Boolean, val copiedCount: Int)

// Real Kakao Map-style "친구위치" (Friend Location) live location sharing -- a real,
// moving position shared for a bounded window, distinct from the static bookmark-folder
// share/subscribe above. Ported from bank-mfe/maps-mfe (2026-09-03) -- itunda's own v1 is
// ALWAYS time-bounded (no "unlimited" option); "live" means periodically-refreshed via
// polling, not a push channel (itunda has no WebSocket infra for this feature specifically).
data class LiveLocationShareDto(
    val id: String, val sharerUserId: String, val recipientUserId: String,
    val latitude: Double?, val longitude: Double?, val locationUpdatedAt: String?,
    val expiresAt: String, val revoked: Boolean, val createdAt: String,
)
data class StartLocationShareRequest(val recipientPhoneNumber: String, val durationHours: Int = 1)
data class StartLocationShareResponse(val success: Boolean, val share: LiveLocationShareDto)
data class UpdateLocationShareRequest(val latitude: Double, val longitude: Double)
data class UpdateLocationShareResponse(val success: Boolean, val updatedShareCount: Int)
data class ExtendLocationShareRequest(val additionalHours: Int = 1)
data class ExtendLocationShareResponse(val success: Boolean, val share: LiveLocationShareDto)
data class LocationSharesResponse(val success: Boolean, val shares: List<LiveLocationShareDto>)
data class LocationShareResponse(val success: Boolean, val share: LiveLocationShareDto)
data class StopLocationShareResponse(val success: Boolean)

data class MapPlaceCategory(val id: String, val label: String)
data class MapCategoriesResponse(val success: Boolean, val categories: List<MapPlaceCategory>)

// Real, honest fallback (Maps product-completeness pass, 2026-09-07) -- this hardcoded
// list used to be the ONLY source, requiring a manual, error-prone 3-way sync with the
// backend's own MapPlaceCategory.kt enum and web's/iOS's own identical hardcoded
// copies. `getMapCategories()` (see ApiService.getMapCategories) is the new real
// source of truth; this stays only as the pre-fetch/offline default MapsScreen starts
// from before that call resolves.
val MAP_NEARBY_CATEGORIES = listOf(
    MapPlaceCategory("RESTAURANT", "Restaurants"),
    MapPlaceCategory("CAFE", "Cafes"),
    MapPlaceCategory("HOSPITAL", "Hospitals"),
    MapPlaceCategory("PHARMACY", "Pharmacies"),
    MapPlaceCategory("BANK", "Banks"),
    MapPlaceCategory("ATM", "ATMs"),
    MapPlaceCategory("HOTEL", "Hotels"),
    MapPlaceCategory("SUPERMARKET", "Supermarkets"),
    MapPlaceCategory("GAS_STATION", "Gas stations"),
    MapPlaceCategory("SCHOOL", "Schools"),
    MapPlaceCategory("ITUNDA_AGENT", "Cash agents"),
    // Real additions (2026-08-09), same pass as MapPlaceCategory.kt's own backend enum --
    // live-verified against itunda's real self-hosted Nominatim before adding (see that
    // file's own doc comment for the real curl results).
    MapPlaceCategory("MARKET", "Markets"),
    MapPlaceCategory("BUS_STOP", "Bus stops"),
)

// Mirrors services/backend/core's real MerchantProduct entity exactly.
// imageUrl/originalPrice/discountPercent added 2026-07-21, closing
// docs/DESIGN_REFERENCES.md Section 5 recommendation #4 -- see backend
// MerchantProduct.kt's own doc comment for the full account (merchant-supplied external
// URL, no upload/storage layer; discountPercent is server-computed, never client-set).
// Real menu-item option groups (2026-07-21, v1: required single-select only) -- ports
// bank-mfe's own MenuOptionChoice/MenuOptionGroup interfaces (lib/eats.ts). See
// MenuOptionGroup.kt's own doc comment on the backend for the full, honestly-scoped
// account.
data class EatsMenuOptionChoiceDto(val id: String, val name: String, val priceDelta: Double)
// required/multiSelect added 2026-08-28 -- the backend has always returned these
// real fields (4 real group combinations, MenuOptionGroup.kt), never declared here.
data class EatsMenuOptionGroupDto(val id: String, val name: String, val choices: List<EatsMenuOptionChoiceDto> = emptyList(), val required: Boolean = true, val multiSelect: Boolean = false)

data class MerchantProductDto(
    val id: String,
    val merchantId: String,
    val name: String,
    val price: Double,
    val active: Boolean,
    val createdAt: String,
    val imageUrl: String? = null,
    val originalPrice: Double? = null,
    val discountPercent: Int? = null,
    // description added 2026-07-21, backing the new dedicated product-detail screen
    // (closes docs/DESIGN_REFERENCES.md Section 5 recommendation #6).
    val description: String? = null,
    // Absent/empty on endpoints that don't fold it in (e.g. product search) -- only
    // ShoppingController.getMerchantProducts (Eats' menu) populates this today.
    val optionGroups: List<EatsMenuOptionGroupDto> = emptyList(),
    // Real bookable-service duration (2026-07-25) -- a non-null value means this
    // "product" is actually a real appointment-bookable service (e.g. a 30-minute
    // haircut). See MerchantProduct.kt's own doc comment on the backend.
    val durationMinutes: Int? = null,
    // Real bulk/wholesale pricing (2026-07-25) -- closes the gap named in Baemin's own
    // real 배민상회 B2B supplies marketplace research. Empty for every product with no
    // real tiers set, the pre-existing behavior for every product before this field
    // existed. See ProductPriceTier.kt's own doc comment on the backend.
    val priceTiers: List<PriceTierDto> = emptyList(),
    val stockQuantity: Int? = null,
    // Real "Best seller" badge (2026-08-28) -- see DealProductDto's own doc comment.
    val isBestSeller: Boolean = false,
)
data class PriceTierDto(val minQuantity: Int, val unitPrice: Double)

// Real local-business appointment booking (2026-07-25) -- see
// rw.itunda.merchant.MerchantBookingService on the backend for the full account. Date
// ("yyyy-MM-dd") and time ("HH:mm:ss") fields stay plain ISO strings here, same
// convention every other temporal field (createdAt etc.) in this file already uses --
// Gson has no built-in java.time.LocalDate/LocalTime adapter registered, so this avoids
// that pitfall entirely rather than registering one just for this feature.
data class BookingSlotDto(val startTime: String, val endTime: String)
data class BookingSlotsResponse(val success: Boolean, val slots: List<BookingSlotDto>)
data class CreateBookingRequest(val merchantId: String, val serviceId: String, val date: String, val startTime: String, val notes: String? = null)
data class MerchantBookingDto(
    val id: String,
    val merchantId: String,
    val customerId: String,
    val serviceId: String,
    val serviceName: String,
    val bookingDate: String,
    val startTime: String,
    val endTime: String,
    val status: String,
    val notes: String? = null,
    val createdAt: String,
    val updatedAt: String,
)
data class MerchantBookingDetailResponse(val success: Boolean, val booking: MerchantBookingDto)
data class MerchantBookingsResponse(val success: Boolean, val bookings: List<MerchantBookingDto>)

// Real post-appointment reviews (item 143) -- see MerchantBookingReview.kt's own doc
// comment. The owner-side list+reply half is real on merchant-mfe; this is the
// CUSTOMER-facing submit-a-review half, real on bank-mfe since 2026-08-01 -- this is
// the first native client.
data class SubmitBookingReviewRequest(val rating: Int, val comment: String? = null)
data class MerchantBookingReviewDto(
    val id: String,
    val bookingId: String,
    val merchantId: String,
    val customerId: String,
    val serviceName: String,
    val rating: Int,
    val comment: String? = null,
    val ownerReply: String? = null,
    val ownerRepliedAt: String? = null,
    val createdAt: String,
)
data class MerchantBookingReviewResponse(val success: Boolean, val review: MerchantBookingReviewDto)
data class MerchantBookingReviewsResponse(val success: Boolean, val reviews: List<MerchantBookingReviewDto>)
data class MerchantBookingRatingDto(val average: Double?, val count: Long)
data class MerchantReviewsWithRatingResponse(val success: Boolean, val reviews: List<MerchantBookingReviewDto>, val rating: MerchantBookingRatingDto)
data class MerchantSummaryDto(val id: String, val businessName: String)
data class MerchantProductsResponse(val success: Boolean, val merchant: MerchantSummaryDto, val products: List<MerchantProductDto>)

// Real cross-merchant product search (item 137) -- see backend
// MerchantProductRepository.search's own doc comment. Mirrors bank-mfe's
// lib/shopping.ts ProductSearchResult exactly; Android never had this endpoint at all.
data class ProductSearchResultDto(
    val id: String,
    val merchantId: String,
    val merchantName: String,
    val name: String,
    val price: Double,
    val imageUrl: String? = null,
    val originalPrice: Double? = null,
    val discountPercent: Int? = null,
    val description: String? = null,
    val stockQuantity: Int? = null,
    // Real "Best seller" badge (2026-08-28) -- see DealProductDto's own doc comment.
    val isBestSeller: Boolean = false,
)
data class ProductSearchResponse(val success: Boolean, val products: List<ProductSearchResultDto>)

// Real KakaoTalk-style 기프티콘 (mobile gift voucher, item 137) -- see backend
// GiftVoucher.kt's own doc comment. Mirrors bank-mfe's lib/giftVouchers.ts (item 134)
// exactly.
data class GiftVoucherDto(
    val id: String,
    val purchaserId: String,
    val recipientId: String,
    val conversationId: String,
    val messageId: String,
    val merchantId: String,
    val merchantProductId: String?,
    val productNameSnapshot: String?,
    val amount: Double,
    val status: String,
    val holdTransactionId: String,
    val redeemTransactionId: String?,
    val refundTransactionId: String?,
    val expiresAt: String,
    val redeemedAt: String?,
    val extended: Boolean,
    val createdAt: String,
)
data class PurchaseGiftVoucherRequest(val recipientPhoneNumber: String, val merchantId: String, val merchantProductId: String? = null, val amount: Double? = null)
data class GiftVoucherResponse(val success: Boolean, val voucher: GiftVoucherDto)
data class GiftVouchersResponse(val success: Boolean, val vouchers: List<GiftVoucherDto>)

// Real Naver Smart Store-style "알림받기" (follow a store for its own broadcast
// notices) -- see backend MerchantFollowService.kt's own doc comment. Mirrors
// bank-mfe's lib/shopping.ts FollowedMerchant exactly.
data class FollowedMerchantDto(val merchantId: String, val businessName: String, val category: String?, val followedAt: String)
data class FollowedMerchantsResponse(val success: Boolean, val follows: List<FollowedMerchantDto>)
data class MerchantFollowDto(val id: String, val userId: String, val merchantId: String, val createdAt: String)
data class MerchantFollowResponse(val success: Boolean, val follow: MerchantFollowDto)

// Real "pay a merchant" -- mirrors bank-mfe's lib/shopping.ts CollectPaymentResult
// exactly (a flat response, not nested under a key).
data class CollectPaymentRequest(val couponId: String? = null)
data class StaticQrPayRequest(val amount: java.math.BigDecimal, val description: String? = null)
// Real customer-presented payment code (2026-08-11) -- see backend's
// MerchantService.generateCustomerPaymentCode/chargeByCustomerCode doc comments.
data class CustomerPaymentCodeResponse(val success: Boolean, val code: String, val expiresAt: String, val accountId: String? = null)
data class GenerateCustomerPaymentCodeRequest(val accountId: String? = null)
data class ChargeByCustomerCodeRequest(val code: String, val amount: java.math.BigDecimal)
data class CollectPaymentResultDto(
    val success: Boolean, val transactionId: String, val merchantName: String,
    val amount: java.math.BigDecimal, val fee: java.math.BigDecimal, val status: String,
    val channel: String, val completedAt: String, val cashbackEarned: java.math.BigDecimal,
)

// Real read-only payment-code preview (item 149) -- see MerchantService.previewIntent's
// own doc comment. Lets a payer see the merchant/amount/their own real coupon
// eligibility before committing to collectPayment -- mirrors bank-mfe's lib/shopping.ts
// PaymentIntentPreview/MerchantCouponView exactly. bank-mfe already has this
// (previewPaymentIntent, item 146); this is the first Android client.
data class MerchantCouponPreviewDto(
    val id: String, val merchantId: String, val title: String, val description: String?,
    val discountType: String, val discountValue: java.math.BigDecimal, val regularsOnly: Boolean,
    val active: Boolean, val expiresAt: String?, val createdAt: String,
)
data class MerchantCouponViewDto(val coupon: MerchantCouponPreviewDto, val eligible: Boolean, val alreadyRedeemed: Boolean)
data class MerchantCouponsForCustomerResponse(val success: Boolean, val coupons: List<MerchantCouponPreviewDto>)

// Real itunda Pay redesign (2026-08-28, direct user reference: real Toss Pay
// Coupon box + Membership screens). Both endpoints these DTOs back had ZERO
// controller endpoint anywhere before this pass -- see MerchantCouponService.kt's
// own doc comments (browseCoupons/getMyRedemptions) and
// MerchantLoyaltyPointsService.kt's (getMyBalances). Mirrors bank-mfe's
// lib/coupons.ts exactly.
data class CouponBrowseViewDto(val coupon: MerchantCouponPreviewDto, val merchantName: String, val eligible: Boolean, val alreadyRedeemed: Boolean)
data class CouponBrowseResponse(val success: Boolean, val coupons: List<CouponBrowseViewDto>)
data class CouponRedemptionDto(
    val id: String, val couponId: String, val merchantId: String, val transactionId: String,
    val discountAmount: java.math.BigDecimal, val redeemedAt: String,
)
data class CouponRedemptionsResponse(val success: Boolean, val redemptions: List<CouponRedemptionDto>)
data class LoyaltyBalanceDto(val merchantId: String, val merchantName: String, val pointBalance: java.math.BigDecimal)
data class LoyaltyBalancesResponse(val success: Boolean, val balances: List<LoyaltyBalanceDto>, val total: java.math.BigDecimal)
data class PaymentIntentPreviewResponse(
    val success: Boolean, val merchantId: String, val businessName: String,
    val amount: java.math.BigDecimal, val description: String?, val coupons: List<MerchantCouponViewDto>,
)

// Real Face Pay -- mirrors bank-mfe's lib/facepay.ts exactly.
data class FacePayEnrollmentDto(val id: String, val userId: String, val active: Boolean, val enrolledAt: String, val revokedAt: String?)
data class FacePayEnrollmentResponse(val success: Boolean, val enrollment: FacePayEnrollmentDto)
data class FacePayStatusResponse(val success: Boolean, val enrolled: Boolean, val enrollment: FacePayEnrollmentDto?)

// Real RewardsService task list -- mirrors bank-mfe's lib/rewards.ts RewardTasksResult.
data class RewardTaskDto(val id: String, val title: String, val subtitle: String, val rewardAmount: Double, val claimed: Boolean, val claimedAt: String?, val eligible: Boolean)
data class RewardTasksResponse(val success: Boolean, val tasks: List<RewardTaskDto>, val rewardsTotal: Double)
data class ClaimRewardTaskRequest(val taskId: String)
data class ClaimRewardTaskResponse(val success: Boolean, val message: String, val rewardAmount: Double, val newBalance: Double)

// Real Shop product wishlist (2026-07-24) -- closes docs/DESIGN_REFERENCES.md Section 5
// recommendation #3: the backend (ProductFavoriteService, shipped 2026-07-20) and
// bank-mfe (ProductCatalogView.toggleFavorite) already had this; Android had zero
// wiring. Mirrors FavoriteListingDto/FavoriteJobPostDto/FavoritePropertyListingDto
// field-for-field.
data class FavoriteProductDto(
    val productId: String,
    val merchantId: String,
    val name: String,
    val price: Double,
    val businessName: String,
    val favoritedAt: String,
    val imageUrl: String? = null,
    val originalPrice: Double? = null,
    val discountPercent: Int? = null,
    val description: String? = null,
    // Real Naver Shopping 가격 변동 알림 (price-drop alert, item 227) -- true once the
    // product's real current price has dropped below the price it was at when
    // favorited. See the backend's ProductFavoriteService.getMyFavorites doc comment.
    val priceDropped: Boolean = false,
)
data class FavoriteProductsResponse(val success: Boolean, val favorites: List<FavoriteProductDto>)

data class OrderItemRequest(val productId: String, val quantity: Int)
// referralCode added for the real 쿠팡파트너스-style affiliate program, item 229 --
// see AffiliateLinkDto's own doc comment.
data class PlaceOrderRequest(val merchantId: String, val items: List<OrderItemRequest>, val deliveryAddress: String, val referralCode: String? = null)
data class UpdateOrderStatusRequest(val status: String)

// Real 쿠팡파트너스 (Coupang Partners)-style affiliate link program (item 229) -- see
// the backend's AffiliateService doc comment. bank-mfe already has this; this is the
// first Android client, generation + earnings only (referral capture-at-checkout
// stays bank-mfe-only, since that relies on a real ?ref= URL query param this native
// app has no deep-link precedent for -- an honest v1 scope-down, not an invented one).
data class CreateAffiliateLinkRequest(val productId: String)
data class AffiliateLinkDto(val id: String, val userId: String, val productId: String, val code: String, val clickCount: Long, val createdAt: String)
data class AffiliateLinkResponse(val success: Boolean, val link: AffiliateLinkDto)
data class AffiliateLinksResponse(val success: Boolean, val links: List<AffiliateLinkDto>)
data class AffiliateCommissionDto(
    val id: String, val linkId: String, val referrerId: String, val orderId: String, val buyerId: String,
    val commissionAmount: Double, val payoutTransactionId: String, val createdAt: String,
)
data class AffiliateCommissionsResponse(val success: Boolean, val commissions: List<AffiliateCommissionDto>)

data class OrderDto(
    val id: String,
    val buyerId: String,
    val merchantId: String,
    val deliveryAddress: String,
    val totalAmount: Double,
    val fee: Double,
    val transactionId: String,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
    val refundTransactionId: String? = null,
)

data class OrderItemDto(val id: String, val orderId: String, val productId: String, val productName: String, val unitPrice: Double, val quantity: Int)
data class OrderDetailResponse(val success: Boolean, val order: OrderDto, val items: List<OrderItemDto>)
data class OrdersResponse(val success: Boolean, val orders: List<OrderDto>, val page: Int, val totalPages: Int)

// Real Coupang-style post-delivery Return & Exchange requests (item 166/174) -- see
// OrderReturnService's own doc comment.
val ORDER_RETURN_REASON_CODES = listOf("DEFECTIVE", "WRONG_ITEM", "NOT_AS_DESCRIBED", "NO_LONGER_NEEDED", "SIZE_FIT", "OTHER")
data class RequestOrderReturnRequest(val type: String, val reasonCode: String, val reasonNote: String? = null)
data class OrderReturnRequestDto(
    val id: String,
    val orderId: String,
    val buyerId: String,
    val merchantId: String,
    val type: String,
    val reasonCode: String,
    val reasonNote: String?,
    val status: String,
    val refundTransactionId: String? = null,
    val requestedAt: String,
    val decidedAt: String? = null,
)
data class OrderReturnRequestResponse(val success: Boolean, val returnRequest: OrderReturnRequestDto)
data class OrderReturnRequestsResponse(val success: Boolean, val returnRequests: List<OrderReturnRequestDto>)
data class DecideOrderReturnRequest(val approve: Boolean)

// Real post-delivery product reviews (2026-07-20) -- see ProductReviewService's own doc
// comment, mirroring Eats' SubmitEatsReviewRequest/EatsReviewDto pattern above but keyed
// to one order line item rather than the whole order (a Commerce order can carry
// several different products from one merchant, and real Coupang reviews are per-product).
data class SubmitProductReviewRequest(val rating: Int, val comment: String? = null)
data class ProductReviewDto(
    val id: String,
    val orderItemId: String,
    val orderId: String,
    val buyerId: String,
    val productId: String,
    val merchantId: String,
    val rating: Int,
    val comment: String?,
    // Real owner-side reply (item 187/188) -- see ProductReviewService.replyToProductReview's
    // own doc comment. merchant-mfe already has this (item 187); customer-side display only
    // here (the reply-writing side lives on merchantapp, the merchant-owner app).
    val ownerReply: String? = null,
    val ownerRepliedAt: String? = null,
    // helpfulCount added 2026-08-25 -- real Coupang/Naver-style "도움돼요" counter, see
    // ProductReview.kt's own doc comment on the backend.
    val helpfulCount: Long = 0,
    val createdAt: String,
)
data class ToggleHelpfulReviewResponse(val success: Boolean, val helpful: Boolean)
data class ProductReviewResponse(val success: Boolean, val review: ProductReviewDto)
data class ProductReviewsResponse(val success: Boolean, val reviews: List<ProductReviewDto>, val page: Int = 0, val totalPages: Int = 1)
data class ProductRatingResponse(val success: Boolean, val average: Double?, val count: Long)

// Real Coupang-style pre-purchase product Q&A -- mirrors bank-mfe's lib/commerce.ts
// ProductInquiry exactly.
data class AskProductInquiryRequest(val question: String)
data class ProductInquiryDto(
    val id: String, val productId: String, val merchantId: String, val buyerId: String,
    val question: String, val answer: String?, val answeredAt: String?, val createdAt: String,
)
data class ProductInquiryResponse(val success: Boolean, val inquiry: ProductInquiryDto)
data class ProductInquiriesResponse(val success: Boolean, val inquiries: List<ProductInquiryDto>, val page: Int = 0, val totalPages: Int = 1)

// Real Kakao Pay 정기결제/Toss 빌링키-style recurring merchant billing -- mirrors
// bank-mfe's lib/shopping.ts exactly.
data class MerchantBillingPlanDto(
    val id: String, val merchantId: String, val name: String, val description: String?,
    val amount: java.math.BigDecimal, val intervalDays: Int, val active: Boolean, val createdAt: String,
)
data class MerchantBillingSubscriptionDto(
    val id: String, val planId: String, val merchantId: String, val customerId: String,
    val status: String, val nextChargeAt: String, val lastChargedAt: String?,
    val chargeCount: Int, val lastFailureReason: String?, val createdAt: String, val cancelledAt: String?,
)
data class MerchantBillingPlansResponse(val success: Boolean, val plans: List<MerchantBillingPlanDto>)
data class MerchantBillingSubscriptionResponse(val success: Boolean, val subscription: MerchantBillingSubscriptionDto)
data class MerchantBillingSubscriptionsResponse(val success: Boolean, val subscriptions: List<MerchantBillingSubscriptionDto>)

// Real recurring-payment ("subscription") detection -- mirrors bank-mfe's lib/account.ts
// DetectedSubscription exactly.
data class DetectedSubscriptionDto(
    val displayName: String, val amount: java.math.BigDecimal, val cadence: String, val occurrenceCount: Int,
    val lastPaidAt: String, val nextExpectedAt: String, val monthlyEquivalent: java.math.BigDecimal,
    val priceIncreased: Boolean, val previousAmount: java.math.BigDecimal?,
)
data class DetectedSubscriptionsResponse(val success: Boolean, val subscriptions: List<DetectedSubscriptionDto>, val estimatedMonthlyTotal: java.math.BigDecimal)

// Real 당근(Karrot) 반경 타기팅-style radius-targeted local ads -- mirrors bank-mfe's
// lib/shopping.ts NearbyMerchantAd exactly.
data class NearbyAdDto(val id: String, val merchantId: String, val title: String, val description: String?, val radiusMeters: Int, val activeUntil: String)
data class NearbyMerchantAdDto(val ad: NearbyAdDto, val businessName: String, val distanceKm: Double)
data class NearbyMerchantAdsResponse(val success: Boolean, val ads: List<NearbyMerchantAdDto>)

// Real Toss Pay home reference -- mirrors backend's MerchantDiscoveryService.kt
// NearbyMerchant exactly.
data class NearbyMerchantDto(val id: String, val businessName: String, val category: String?, val cashbackRate: Double, val latitude: Double, val longitude: Double, val distanceKm: Double)
data class NearbyMerchantsResponse(val success: Boolean, val merchants: List<NearbyMerchantDto>)

// Mirrors services/backend/eats's real DTOs exactly (2026-07-18) -- backs the Eats mode
// folded into the Shop tab. Restaurant/menu browsing reuses ShoppingMerchantDto/
// MerchantProductDto above (a restaurant IS a Merchant, a menu item IS a
// MerchantProduct -- see rw.itunda.eats.EatsOrderService's own doc comment).
// selectedChoiceIds added 2026-07-21 (v1: required single-select only) -- one choice
// id per required option group on this menu item; omitted/null for any item with no
// option groups, the pre-existing, unaffected case. See MenuOptionGroup.kt's own doc
// comment on the backend for the full account.
data class EatsOrderItemRequest(val menuItemId: String, val quantity: Int, val selectedChoiceIds: List<String>? = null)
data class PlaceEatsOrderRequest(
    val restaurantId: String,
    val items: List<EatsOrderItemRequest>,
    val deliveryAddress: String,
    val deliveryLatitude: Double? = null,
    val deliveryLongitude: Double? = null,
    val deliveryNotes: String? = null,
    // Real Baemin-style 포장주문 (Pickup) order type (item 208) -- backend-complete
    // since 2026-07-26, bank-mfe client 2026-07-31; this is the first Android client.
    val fulfillmentType: String = "DELIVERY",
)
data class UpdateEatsOrderStatusRequest(val status: String)
data class SetRiderAvailabilityRequest(val available: Boolean)

// Real self-hosted address-search autocomplete (2026-07-18) -- backed by itunda's own
// Nominatim geocoder, not a third-party Maps API. See EatsOrderService.searchDeliveryAddress's
// own doc comment.
data class AddressSuggestionDto(val displayName: String, val latitude: Double, val longitude: Double)
data class AddressSearchResponse(val success: Boolean, val suggestions: List<AddressSuggestionDto>)

// Real post-delivery ratings & reviews (2026-07-18) -- see EatsReviewService's own doc
// comment. Ported from bank-mfe's own review UI, the template for this Android version.
data class SubmitEatsReviewRequest(
    val restaurantRating: Int,
    val restaurantComment: String? = null,
    val riderRating: Int,
    val riderComment: String? = null,
    // Real optional review photo (2026-08-04) -- see EatsReviewDto.photoUrl's own doc
    // comment.
    val photoUrl: String? = null,
)
data class EatsReviewDto(
    val id: String,
    val orderId: String,
    val buyerId: String,
    val restaurantId: String,
    val riderId: String,
    val restaurantRating: Int,
    val restaurantComment: String?,
    val riderRating: Int,
    val riderComment: String?,
    // Real owner-side reply (item 184/185) -- see EatsReviewService.replyToRestaurantReview's
    // own doc comment. bank-mfe already has this (item 184); this is the first Android client.
    val ownerReply: String? = null,
    val ownerRepliedAt: String? = null,
    // Real optional review photo (2026-08-04) -- see docs/DESIGN_REFERENCES.md's Eats
    // recommendation #5 (food-delivery trust leans on real plated-food photos). Same
    // real-external-URL-only convention as Merchant.photoUrl -- a real URL the buyer
    // supplies, never an upload/storage pipeline.
    val photoUrl: String? = null,
    val createdAt: String,
    // Real Coupang/Naver-style "도움돼요" (helpful) counter -- see backend
    // EatsReviewService.toggleHelpful's own doc comment. Real, shipped on the backend +
    // bank-mfe with zero Android client until now -- found via a cross-platform-parity
    // check.
    val helpfulCount: Long = 0,
)
data class EatsReviewResponse(val success: Boolean, val review: EatsReviewDto)
data class EatsReviewsResponse(val success: Boolean, val reviews: List<EatsReviewDto>, val page: Int = 0, val totalPages: Int = 1)
data class ReplyToEatsReviewRequest(val reply: String)
data class EatsRatingResponse(val success: Boolean, val average: Double?, val count: Long)
data class ToggleEatsReviewHelpfulResponse(val success: Boolean, val helpful: Boolean)
data class ReportEatsReviewRequest(val reason: String, val details: String? = null)

data class EatsOrderDto(
    val id: String,
    val buyerId: String,
    val restaurantId: String,
    val riderId: String?,
    val deliveryAddress: String,
    val itemsSubtotal: Double,
    val deliveryFee: Double,
    val platformFee: Double,
    val totalAmount: Double,
    // Real Baemin-style tiered order-amount promotion (2026-08-16) -- itunda-funded,
    // not restaurant-funded. See EatsPromotionCalculator's own doc comment on the
    // backend. Zero for every order below the lowest real tier.
    val promotionDiscount: Double = 0.0,
    val transactionId: String,
    val deliveryPayoutTransactionId: String?,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
    val refundTransactionId: String? = null,
    val deliveryLatitude: Double? = null,
    val deliveryLongitude: Double? = null,
    val distanceKm: Double? = null,
    val deliveryNotes: String? = null,
    // Real fresh Uber Eats research (2026-08-15) -- see EatsController's own
    // withRiderEtaFields doc comment: itunda's order-tracking screen already had a real
    // stepped status UI and live rider-location map, but never resolved riderId to a
    // real name or turned the already-stored distanceKm into a customer-facing arrival
    // estimate, both real gaps against Uber Eats' own sourced tracker redesign. Only
    // returned by GET my-orders (the customer's own order-tracking endpoint), null
    // elsewhere -- never fabricated when no rider is assigned yet.
    val riderName: String? = null,
    val estimatedArrivalMinutes: Int? = null,
    // Real Uber Eats post-delivery tip -- see EatsOrderService.tipRider's own doc
    // comment. Ported from bank-mfe (2026-09-03). Non-null once tipped; used to hide
    // the tip prompt for an order the caller already tipped.
    val tipAmount: Double? = null,
)
data class TipEatsOrderRequest(val amount: Double)
data class TipEatsOrderResponse(val success: Boolean, val order: EatsOrderDto)

// selectedOptionsJson added 2026-07-21 -- unitPrice above already includes every
// selected choice's priceDelta; this is purely a human-readable receipt summary, never
// a second pricing source. See EatsOrderItem.kt's own doc comment.
data class EatsOrderItemDto(
    val id: String, val orderId: String, val productId: String, val productName: String, val unitPrice: Double, val quantity: Int,
    val selectedOptionsJson: String? = null,
)
data class EatsOrderDetailResponse(val success: Boolean, val order: EatsOrderDto, val items: List<EatsOrderItemDto>)
// Real pagination-discard fix (2026-09-11, ported from bank-mfe's own fix,
// 918529e4 -- see project_itunda_pagination_discard_sweep memory) -- all 3
// real consumers of this response (getMyEatsOrders, getRiderDeliveries,
// getAvailableDeliveries) are Pageable-backed on the backend, so page/
// totalPages are always present in the real JSON regardless of which
// endpoint is called. Only getMyEatsOrders's own client is wired to send
// page this pass -- getRiderDeliveries/getAvailableDeliveries (riderapp
// module) are a real, disclosed follow-up, not touched here.
data class EatsOrdersResponse(val success: Boolean, val orders: List<EatsOrderDto>, val page: Int, val totalPages: Int)

// Real 배달의민족 함께주문 (Baemin "Together Order") -- ported from bank-mfe
// (2026-09-03), see lib/eatsGroupOrders.ts's own doc comment. A join-code-shared cart
// in front of the same real checkout/payment path finalizeGroupEatsOrder already
// reuses underneath (GroupEatsOrderService.finalizeOrder calls the exact same backend
// EatsOrderService.placeOrder).
data class GroupEatsOrderDto(
    val id: String, val hostUserId: String, val restaurantId: String, val joinCode: String,
    val deliveryAddress: String, val deliveryLatitude: Double?, val deliveryLongitude: Double?,
    val fulfillmentType: String, val status: String, val resultingOrderId: String?,
    val createdAt: String, val finalizedAt: String?,
)
data class GroupEatsOrderItemView(val productId: String, val productName: String, val quantity: Int, val unitPrice: Double, val lineTotal: Double)
data class GroupEatsOrderParticipantView(val userId: String, val joinedAt: String, val subtotal: Double, val items: List<GroupEatsOrderItemView>)
data class CreateGroupEatsOrderRequest(val restaurantId: String, val deliveryAddress: String = "", val deliveryLatitude: Double? = null, val deliveryLongitude: Double? = null, val fulfillmentType: String = "DELIVERY")
data class JoinGroupEatsOrderRequest(val joinCode: String)
data class GroupEatsOrderItemRequest(val menuItemId: String, val quantity: Int, val selectedChoiceIds: List<String>? = null)
data class SetGroupEatsOrderItemsRequest(val items: List<GroupEatsOrderItemRequest>)
data class GroupEatsOrderResponse(val success: Boolean, val groupOrder: GroupEatsOrderDto)
data class GroupEatsOrderDetailResponse(val success: Boolean, val groupOrder: GroupEatsOrderDto, val grandTotal: Double, val participants: List<GroupEatsOrderParticipantView>)
data class FinalizeGroupEatsOrderResponse(val success: Boolean, val order: EatsOrderDto, val items: List<EatsOrderItemDto>)

data class RiderLocationDto(val latitude: Double, val longitude: Double, val updatedAt: String)
data class EatsRiderLocationResponse(val success: Boolean, val available: Boolean, val location: RiderLocationDto?)

// Real 배민오더-style table/QR in-store ordering (2026-07-25) -- see
// rw.itunda.eats.web.DineInOrderController. No delivery address/rider fields at all;
// totalAmount == itemsSubtotal since there's no delivery fee to add.
data class DineInOrderItemRequest(val menuItemId: String, val quantity: Int, val selectedChoiceIds: List<String>? = null)
data class PlaceDineInOrderRequest(
    val restaurantId: String,
    val tableNumber: String,
    val items: List<DineInOrderItemRequest>,
    val notes: String? = null,
)
data class UpdateDineInOrderStatusRequest(val status: String)
data class DineInOrderDto(
    val id: String,
    val buyerId: String,
    val restaurantId: String,
    val tableNumber: String,
    val itemsSubtotal: Double,
    val platformFee: Double,
    val totalAmount: Double,
    val transactionId: String,
    val status: String,
    val notes: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val refundTransactionId: String? = null,
)
data class DineInOrderItemDto(
    val id: String, val orderId: String, val productId: String, val productName: String, val unitPrice: Double, val quantity: Int,
    val selectedOptionsJson: String? = null,
)
data class DineInOrderDetailResponse(val success: Boolean, val order: DineInOrderDto, val items: List<DineInOrderItemDto>)
// Real pagination-discard fix (2026-09-11, ported from bank-mfe's own fix,
// 081d22a5, and iOS's port, 5b1612da -- see
// project_itunda_pagination_discard_sweep memory) -- getMyDineInOrders is
// real Pageable-backed on the backend (already bumped to size=50 at some
// point, but page was never sent). Safe to extend this struct directly:
// used only by this one endpoint on Android (no restaurant-side dine-in
// queue exists here at all, a real pre-existing feature gap).
data class DineInOrdersResponse(val success: Boolean, val orders: List<DineInOrderDto>, val page: Int, val totalPages: Int)

data class RiderDto(val id: String, val userId: String, val accountId: String, val status: String, val available: Boolean, val createdAt: String)
data class RiderResponse(val success: Boolean, val rider: RiderDto)

// Real bookmarked/favorited restaurants (2026-07-19) -- add/remove are both idempotent
// on the backend, see EatsFavoriteService.kt's own doc comment.
data class FavoriteRestaurantDto(val restaurantId: String, val businessName: String, val category: String?, val favoritedAt: String)
data class FavoriteRestaurantsResponse(val success: Boolean, val favorites: List<FavoriteRestaurantDto>, val page: Int = 0, val totalPages: Int = 1, val totalElements: Int = 0)
data class AddFavoriteResponse(val success: Boolean)

// Real Karrot "이 글 숨기기" (hide this post) -- the client only needs to know the call
// succeeded (same as AddFavoriteResponse), the real ListingHide object's own fields
// aren't rendered anywhere, matching bank-mfe's own hideListing() usage.
data class HideListingResponse(val success: Boolean)

// Real Baemin Club (배민클럽)-style free-delivery membership (item 209) -- see
// getMyEatsMembership's own doc comment.
data class EatsMembershipDto(
    val id: String,
    val userId: String,
    val activeUntil: String,
    val createdAt: String,
    val updatedAt: String,
)
data class EatsMembershipResponse(val success: Boolean, val membership: EatsMembershipDto?)
data class SubscribeEatsMembershipRequest(val days: Int)
