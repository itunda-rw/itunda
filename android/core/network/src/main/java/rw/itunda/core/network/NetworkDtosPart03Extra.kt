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
