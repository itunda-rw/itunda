package rw.itunda.app.network

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import rw.itunda.app.BuildConfig

// Mirrors services/backend/auth/src/main/kotlin/rw/itunda/auth/AuthDtos.kt exactly --
// same field names/nullability, so Gson deserializes the real backend's JSON directly.
data class RegisterRequest(
    val phoneNumber: String,
    val email: String?,
    val firstName: String,
    val lastName: String,
    val password: String,
    val referralCode: String? = null,
)

data class LoginRequest(val phoneNumber: String, val password: String)
data class RefreshRequest(val refreshToken: String)
data class LogoutRequest(val refreshToken: String?)

data class PublicUser(
    val id: String,
    val phoneNumber: String,
    val email: String?,
    val firstName: String,
    val lastName: String,
    val kycVerified: Boolean,
    val creditScore: Int,
    val createdAt: String,
)

data class AuthResponse(
    val message: String,
    val user: PublicUser,
    val accessToken: String,
    val refreshToken: String,
)

// Real login/session flow (2026-07-11) -- see TokenStore.kt and SessionManager.kt.
// Register/login/refresh are unauthenticated per SecurityConfig.kt's permitAll list;
// logout requires the access token being revoked, passed explicitly rather than via
// the auth interceptor so it's unambiguous which token is being killed.
interface AuthApi {
    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): AuthResponse

    @POST("api/v1/auth/logout")
    suspend fun logout(@Header("Authorization") bearerAccessToken: String, @Body request: LogoutRequest)

    // Real account settings screen (2026-07-12) -- backs the "내 정보" section of
    // the new Settings screen.
    @GET("api/v1/auth/profile")
    suspend fun getProfile(): ProfileResponse
}

data class ProfileResponse(val success: Boolean, val user: PublicUser)

// Mirrors services/backend/core/.../domain/Wallet.kt exactly (2026-07-11 fix) --
// the previous shape (currency/balance/isPrimary only) didn't match the real
// backend's serialized Wallet entity at all -- there is no "isPrimary" field on
// the real backend, so `wallets.firstOrNull { it.isPrimary }` silently always
// returned null and fell through to whatever wallet happened to be first, not
// actually the primary one. `type == "MAIN"` is the real signal.
data class Wallet(
    val id: String,
    val userId: String,
    val accountNumber: String,
    val accountName: String,
    val type: String,
    val balance: Double,
    val availableBalance: Double,
    val currency: String,
    val isActive: Boolean,
)

data class WalletResponse(
    val success: Boolean,
    val wallets: List<Wallet>
)

// Mirrors services/backend/core/.../domain/SavingsGoal.kt / InterestJar.kt.
data class SavingsGoal(
    val id: String,
    val userId: String,
    val walletId: String,
    val name: String,
    val targetAmount: Double,
    val currentAmount: Double,
    val monthlyContribution: Double,
    val interestRate: Double,
    val targetDate: String?,
    val category: String,
    val status: String,
    val color: String,
)

data class SavingsGoalsResponse(val success: Boolean, val goals: List<SavingsGoal>)

data class InterestJar(
    val userId: String,
    val walletId: String,
    val balance: Double,
    val rate: Double,
    val earnedThisMonth: Double,
    val earnedTotal: Double,
)

data class InterestJarResponse(val success: Boolean, val jar: InterestJar)

data class DiscoverItem(
    val id: String,
    val category: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val color: String,
    val isNew: Boolean,
    val badge: String?
)

data class DiscoverResponse(
    val success: Boolean,
    val items: List<DiscoverItem>
)

// Mirrors services/backend/wallet's WalletController/TransferQuote.kt exactly
// (2026-07-12) -- the real transfer flow (RecipientEntryScreen/TransferAmountScreen
// in :features:payments:impl) was UI-only until now; these are what wire it to the
// actual quoteTransfer/confirmTransfer endpoints.
data class QuoteTransferRequest(val amount: java.math.BigDecimal, val recipient: String, val fromWalletId: String? = null, val description: String? = null)

data class TransferQuoteDto(
    val id: String,
    val fromWalletId: String,
    val recipient: String,
    val amount: Double,
    val fee: Double,
    val totalDebit: Double,
    val currency: String,
    val expiresAt: String,
)

data class QuoteTransferResponse(val success: Boolean, val quote: TransferQuoteDto)

data class ConfirmTransferRequest(val quoteId: String)

data class TransactionDto(
    val id: String,
    val senderId: String,
    val recipientId: String,
    val type: String,
    val amount: Double,
    val fee: Double,
    val currency: String,
    val status: String,
    val description: String,
    val createdAt: String,
)

data class ConfirmTransferResponse(val success: Boolean, val message: String, val transaction: TransactionDto, val newBalance: Double)

// Mirrors services/backend/savings's SavingsController.kt.
data class DepositRequest(val goalId: String, val amount: java.math.BigDecimal, val fromWalletId: String? = null)
data class DepositResponse(val success: Boolean, val message: String, val goal: SavingsGoal)
data class ClaimInterestResponse(val success: Boolean, val message: String, val claimed: Double? = null)

// Mirrors services/backend/notifications's NotificationController.kt (2026-07-12) --
// backs the Settings screen's notifications list.
data class NotificationDto(
    val id: String,
    val userId: String,
    val type: String,
    val title: String,
    val body: String,
    val isRead: Boolean,
    val createdAt: String,
)

data class NotificationsResponse(val success: Boolean, val notifications: List<NotificationDto>, val unreadCount: Int)
data class MarkReadResponse(val success: Boolean)

// Mirrors services/backend/partners's PartnerMiniApp entity exactly (field names match
// its real Jackson-serialized JSON) -- the real "app store" catalog a mobile Saronite
// host client fetches to know which third-party mini-apps are approved and available
// (2026-07-17, closing the mobile half of the Partner SDK gap named in
// docs/TOSS_PARITY_MATRIX.md's Partner SDK row). `permissions` is the same real
// comma-joined scope string PartnerService.submitMiniApp stored at submission time
// (validated then against PartnerMiniAppPermissions.ALLOWED) -- split on "," client-side
// by MiniAppSecurityContext before a partner bundle is actually loaded.
data class PartnerMiniAppDto(
    val id: String,
    val partnerId: String,
    val name: String,
    val description: String,
    val iconUrl: String?,
    val bundleUrl: String,
    val permissions: String,
    val status: String,
    val createdAt: String,
)

data class MiniAppCatalogResponse(val success: Boolean, val miniApps: List<PartnerMiniAppDto>)

// Mirrors services/backend/messaging's real DTOs exactly (2026-07-18) -- backs the new
// "Talk" bottom-nav tab (Kakao-style 1:1 chat). See rw.itunda.messaging.MessagingService
// / MessagingController's own doc comments for the full backend account, including the
// honest "poll-based delivery, no live transport yet" scope this mobile client matches.
data class ConversationDto(
    val id: String,
    val participantAId: String,
    val participantBId: String,
    val lastMessageAt: String,
    val createdAt: String,
)

// What GET /api/v1/messages/conversations actually returns per row -- a different,
// flatter shape than ConversationDto above (MessagingService.ConversationSummary).
data class ConversationSummaryDto(
    val conversationId: String,
    val otherUserId: String,
    val otherUserName: String,
    val lastMessageAt: String,
    val lastMessagePreview: String?,
    val unreadCount: Int,
)

// Real emoji reactions (2026-07-19) -- see MessagingService.toggleReaction's own doc
// comment for the real toggle semantics (tapping an active reaction removes it).
data class ReactionGroupDto(val emoji: String, val userIds: List<String>)

data class MessageDto(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val body: String,
    val sentAt: String,
    val readAt: String?,
    val reactions: List<ReactionGroupDto> = emptyList(),
)

data class StartConversationRequest(val phoneNumber: String? = null, val otherUserId: String? = null)
data class SendMessageRequest(val body: String)
data class ToggleReactionRequest(val emoji: String)

data class ConversationResponse(val success: Boolean, val conversation: ConversationDto)
data class ConversationsResponse(val success: Boolean, val conversations: List<ConversationSummaryDto>)
data class MessagesResponse(val success: Boolean, val messages: List<MessageDto>)
data class MessageResponse(val success: Boolean, val message: MessageDto)
data class ReactionsResponse(val success: Boolean, val reactions: List<ReactionGroupDto>)

// Real group chat (2026-07-18) -- see rw.itunda.messaging.web.GroupMessagingController.
// memberPhoneNumbers is the real human-friendly entry point (same reasoning as
// StartConversationRequest.phoneNumber).
data class CreateGroupRequest(val name: String, val memberUserIds: List<String> = emptyList(), val memberPhoneNumbers: List<String> = emptyList())
data class SendGroupMessageRequest(val body: String)
data class AddGroupMemberRequest(val userId: String)

data class GroupSummaryDto(
    val groupId: String,
    val name: String,
    val memberCount: Int,
    val lastMessageAt: String,
    val lastMessagePreview: String?,
    val unreadCount: Long,
)
data class GroupMessageDto(
    val id: String,
    val groupConversationId: String,
    val senderId: String,
    val body: String,
    val sentAt: String,
    val reactions: List<ReactionGroupDto> = emptyList(),
)
data class GroupResponse(val success: Boolean, val group: GroupSummaryDto)
data class GroupsResponse(val success: Boolean, val groups: List<GroupSummaryDto>)
data class GroupMessagesResponse(val success: Boolean, val messages: List<GroupMessageDto>)
data class GroupMessageResponse(val success: Boolean, val message: GroupMessageDto)
data class LeaveGroupResponse(val success: Boolean)

// Real member list with real resolved display names (2026-07-18) -- closes the honest,
// named limitation this UI carried since group chat first shipped: message bubbles
// showing a truncated sender id instead of a real name.
data class GroupMemberDto(val userId: String, val name: String)
data class GroupMembersResponse(val success: Boolean, val members: List<GroupMemberDto>)

// Real online/offline presence (2026-07-19) -- see MessagingService.getPresence's own
// doc comment on the backend.
data class PresenceResponse(val success: Boolean, val presence: Map<String, Boolean>)

// Mirrors services/backend/marketplace's real DTOs exactly (2026-07-18) -- backs the
// new "Hood" bottom-nav tab (당근마켓/Danggeun-style neighborhood marketplace). See
// rw.itunda.marketplace.MarketplaceService's own doc comment for the honest "no real
// location data" scope this mobile client inherits unchanged.
data class ListingDto(
    val id: String,
    val sellerId: String,
    val title: String,
    val description: String,
    val price: Double,
    val category: String,
    val status: String,
    val createdAt: String,
)

data class CreateListingRequest(val title: String, val description: String, val price: Double, val category: String)
data class ListingResponse(val success: Boolean, val listing: ListingDto)
data class ListingsResponse(val success: Boolean, val listings: List<ListingDto>)

// Real 당근-style price-offer negotiation (2026-07-19) -- see PriceOfferService's own
// doc comment. Each offer/counter/accept/reject is a real message in the same real
// conversation contactSeller establishes, rendered inline as an offer bubble.
data class PriceOfferDto(
    val id: String,
    val listingId: String,
    val messageId: String,
    val conversationId: String,
    val buyerId: String,
    val sellerId: String,
    val proposedByUserId: String,
    val amount: Double,
    val status: String,
    val createdAt: String,
    val respondedAt: String?,
)
data class MakeOfferRequest(val amount: Double)
data class RespondToOfferRequest(val action: String, val counterAmount: Double? = null)
data class PriceOfferResponse(val success: Boolean, val offer: PriceOfferDto)
data class PriceOffersResponse(val success: Boolean, val offers: List<PriceOfferDto>)
data class ContactSellerResponse(val success: Boolean, val conversation: ConversationDto)

// Mirrors services/backend/commerce's real DTOs exactly (2026-07-18) -- backs the new
// "Shop" bottom-nav tab (Coupang-style multi-item checkout), replacing the old Shop
// tab's Toss-Shopping-cashback content. See rw.itunda.commerce.OrderService's own doc
// comment for the honest "self-declared fulfillment, no real courier network" scope.
data class ShoppingMerchantDto(
    val merchantId: String, val businessName: String, val category: String?, val cashbackRate: String,
    // Real optional location (2026-07-19) -- backs the real self-hosted Map view.
    val latitude: Double? = null, val longitude: Double? = null,
)
data class ShoppingMerchantsResponse(val success: Boolean, val merchants: List<ShoppingMerchantDto>)

// Real "search this map" + "directions" (2026-07-19) -- see rw.itunda.maps.MapsService's
// own doc comment on the backend for why these are a new, general-purpose front door
// onto itunda's already-deployed self-hosted Nominatim/OSRM.
data class PlaceSearchResultDto(val displayName: String, val latitude: Double, val longitude: Double)
data class MapsSearchResponse(val success: Boolean, val results: List<PlaceSearchResultDto>)
data class RouteResultDto(val distanceKm: Double, val durationMinutes: Double, val geometry: List<List<Double>>)
data class MapsDirectionsResponse(val success: Boolean, val route: RouteResultDto)
data class MerchantCategoriesResponse(val success: Boolean, val categories: List<String>)

// Mirrors services/backend/core's real MerchantProduct entity exactly.
data class MerchantProductDto(val id: String, val merchantId: String, val name: String, val price: Double, val active: Boolean, val createdAt: String)
data class MerchantSummaryDto(val id: String, val businessName: String)
data class MerchantProductsResponse(val success: Boolean, val merchant: MerchantSummaryDto, val products: List<MerchantProductDto>)

data class OrderItemRequest(val productId: String, val quantity: Int)
data class PlaceOrderRequest(val merchantId: String, val items: List<OrderItemRequest>, val deliveryAddress: String)
data class UpdateOrderStatusRequest(val status: String)

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
data class OrdersResponse(val success: Boolean, val orders: List<OrderDto>)

// Mirrors services/backend/eats's real DTOs exactly (2026-07-18) -- backs the Eats mode
// folded into the Shop tab. Restaurant/menu browsing reuses ShoppingMerchantDto/
// MerchantProductDto above (a restaurant IS a Merchant, a menu item IS a
// MerchantProduct -- see rw.itunda.eats.EatsOrderService's own doc comment).
data class EatsOrderItemRequest(val menuItemId: String, val quantity: Int)
data class PlaceEatsOrderRequest(
    val restaurantId: String,
    val items: List<EatsOrderItemRequest>,
    val deliveryAddress: String,
    val deliveryLatitude: Double? = null,
    val deliveryLongitude: Double? = null,
    val deliveryNotes: String? = null,
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
    val createdAt: String,
)
data class EatsReviewResponse(val success: Boolean, val review: EatsReviewDto)
data class EatsRatingResponse(val success: Boolean, val average: Double?, val count: Long)

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
)

data class EatsOrderItemDto(val id: String, val orderId: String, val productId: String, val productName: String, val unitPrice: Double, val quantity: Int)
data class EatsOrderDetailResponse(val success: Boolean, val order: EatsOrderDto, val items: List<EatsOrderItemDto>)
data class EatsOrdersResponse(val success: Boolean, val orders: List<EatsOrderDto>)

data class RiderDto(val id: String, val userId: String, val walletId: String, val status: String, val available: Boolean, val createdAt: String)
data class RiderResponse(val success: Boolean, val rider: RiderDto)

// Real bookmarked/favorited restaurants (2026-07-19) -- add/remove are both idempotent
// on the backend, see EatsFavoriteService.kt's own doc comment.
data class FavoriteRestaurantDto(val restaurantId: String, val businessName: String, val category: String?, val favoritedAt: String)
data class FavoriteRestaurantsResponse(val success: Boolean, val favorites: List<FavoriteRestaurantDto>)
data class AddFavoriteResponse(val success: Boolean)
data class RemoveFavoriteResponse(val success: Boolean)

// Retrofit Interface to map to your Spring endpoints -- all require the real
// Bearer token NetworkClient's authInterceptor now injects (2026-07-11).
interface ApiService {
    @GET("api/v1/wallet")
    suspend fun getWallets(): WalletResponse

    @GET("api/v1/discover")
    suspend fun getDiscoverItems(): DiscoverResponse

    @GET("api/v1/savings/goals")
    suspend fun getSavingsGoals(): SavingsGoalsResponse

    @GET("api/v1/savings/interest-jar")
    suspend fun getInterestJar(): InterestJarResponse

    @POST("api/v1/wallet/transfer/quote")
    suspend fun quoteTransfer(@Body request: QuoteTransferRequest): QuoteTransferResponse

    @POST("api/v1/wallet/transfer/confirm")
    suspend fun confirmTransfer(@Header("Idempotency-Key") idempotencyKey: String, @Body request: ConfirmTransferRequest): ConfirmTransferResponse

    @POST("api/v1/savings/deposit")
    suspend fun depositToGoal(@Header("Idempotency-Key") idempotencyKey: String, @Body request: DepositRequest): DepositResponse

    @POST("api/v1/savings/interest-jar/claim")
    suspend fun claimInterest(@Header("Idempotency-Key") idempotencyKey: String): ClaimInterestResponse

    // Real transaction history (2026-07-12) -- backs the new card/transaction-
    // history screen; see services/backend/wallet's new WalletController endpoint.
    @GET("api/v1/wallet/transactions")
    suspend fun getTransactionHistory(): TransactionHistoryResponse

    @GET("api/v1/notifications")
    suspend fun getNotifications(): NotificationsResponse

    @POST("api/v1/notifications/{id}/read")
    suspend fun markNotificationRead(@retrofit2.http.Path("id") id: String): MarkReadResponse

    // Real offline-action-queue replay (2026-07-13) -- see network/OfflineActionQueue.kt.
    @POST("api/v1/actions/batch")
    suspend fun submitActionBatch(@Body request: BatchRequest): BatchResponse

    // Real Partner SDK catalog (2026-07-17) -- services/backend/partners's
    // MiniAppCatalogController, itunda-user-JWT-gated like every other endpoint on this
    // interface (no ADMIN role, no partner API key -- this is the public "app store"
    // surface a logged-in itunda user's own client fetches). See
    // miniapps/PartnerMiniAppLoader.kt for what actually happens when one of these is tapped.
    @GET("api/v1/mini-apps/catalog")
    suspend fun getMiniAppCatalog(): MiniAppCatalogResponse

    // Real 1:1 messaging (2026-07-18) -- see rw.itunda.messaging.web.MessagingController.
    @POST("api/v1/messages/conversations")
    suspend fun startConversation(@Body request: StartConversationRequest): ConversationResponse

    @GET("api/v1/messages/conversations")
    suspend fun getConversations(): ConversationsResponse

    @GET("api/v1/messages/conversations/{id}/messages")
    suspend fun getMessages(@Path("id") conversationId: String): MessagesResponse

    @POST("api/v1/messages/conversations/{id}/messages")
    suspend fun sendMessage(@Path("id") conversationId: String, @Body request: SendMessageRequest): MessageResponse

    // Real toggle -- tapping an already-active reaction removes it, same semantics as
    // MessagingService.toggleReaction on the backend.
    @POST("api/v1/messages/messages/{id}/reactions")
    suspend fun toggleReaction(@Path("id") messageId: String, @Body request: ToggleReactionRequest): ReactionsResponse

    // Real group chat (2026-07-18) -- see rw.itunda.messaging.web.GroupMessagingController.
    @POST("api/v1/messages/groups")
    suspend fun createGroup(@Body request: CreateGroupRequest): GroupResponse

    @GET("api/v1/messages/groups")
    suspend fun getMyGroups(): GroupsResponse

    @GET("api/v1/messages/groups/{id}/messages")
    suspend fun getGroupMessages(@Path("id") groupId: String): GroupMessagesResponse

    @POST("api/v1/messages/groups/{id}/messages")
    suspend fun sendGroupMessage(@Path("id") groupId: String, @Body request: SendGroupMessageRequest): GroupMessageResponse

    @POST("api/v1/messages/groups/messages/{id}/reactions")
    suspend fun toggleGroupReaction(@Path("id") groupMessageId: String, @Body request: ToggleReactionRequest): ReactionsResponse

    @POST("api/v1/messages/groups/{id}/members")
    suspend fun addGroupMember(@Path("id") groupId: String, @Body request: AddGroupMemberRequest): GroupResponse

    @DELETE("api/v1/messages/groups/{id}/members/me")
    suspend fun leaveGroup(@Path("id") groupId: String): LeaveGroupResponse

    @GET("api/v1/messages/groups/{id}/members")
    suspend fun getGroupMembers(@Path("id") groupId: String): GroupMembersResponse

    // Real online/offline presence (2026-07-19) -- see MessagingService.getPresence's
    // own doc comment on the backend. Works for any set of user ids, not just 1:1
    // conversation partners -- e.g. a group thread can pass every member's id.
    @GET("api/v1/messages/presence")
    suspend fun getPresence(@Query("userIds") userIds: List<String>): PresenceResponse

    // Real 당근마켓-style marketplace (2026-07-18) -- see rw.itunda.marketplace.web.MarketplaceController.
    @POST("api/v1/marketplace/listings")
    suspend fun createListing(@Body request: CreateListingRequest): ListingResponse

    @GET("api/v1/marketplace/listings")
    suspend fun browseListings(@Query("category") category: String? = null): ListingsResponse

    @GET("api/v1/marketplace/my-listings")
    suspend fun getMyListings(): ListingsResponse

    @POST("api/v1/marketplace/listings/{id}/mark-sold")
    suspend fun markListingSold(@Path("id") listingId: String): ListingResponse

    @DELETE("api/v1/marketplace/listings/{id}")
    suspend fun removeListing(@Path("id") listingId: String): ListingResponse

    @POST("api/v1/marketplace/listings/{id}/contact-seller")
    suspend fun contactSeller(@Path("id") listingId: String): ContactSellerResponse

    // Real 당근-style price-offer negotiation (2026-07-19) -- see PriceOfferService.
    @POST("api/v1/marketplace/listings/{id}/offers")
    suspend fun makeOffer(@Path("id") listingId: String, @Body request: MakeOfferRequest): PriceOfferResponse

    @POST("api/v1/marketplace/offers/{id}/respond")
    suspend fun respondToOffer(@Path("id") offerId: String, @Body request: RespondToOfferRequest): PriceOfferResponse

    @GET("api/v1/marketplace/conversations/{id}/offers")
    suspend fun getOffersForConversation(@Path("id") conversationId: String): PriceOffersResponse

    // Real per-merchant public product browse (2026-07-18) -- see
    // rw.itunda.merchant.web.ShoppingController.getMerchantProducts.
    @GET("api/v1/shopping/merchants")
    suspend fun getShoppingMerchants(@Query("category") category: String? = null, @Query("q") q: String? = null): ShoppingMerchantsResponse

    // Real "search this map" + "directions" (2026-07-19) -- see MapsService.
    @GET("api/v1/maps/search")
    suspend fun searchPlaces(@Query("q") q: String): MapsSearchResponse

    @GET("api/v1/maps/directions")
    suspend fun getDirections(
        @Query("fromLat") fromLat: Double,
        @Query("fromLng") fromLng: Double,
        @Query("toLat") toLat: Double,
        @Query("toLng") toLng: Double,
    ): MapsDirectionsResponse

    // Real distinct category list -- see MerchantRepository.findDistinctCategories's own
    // doc comment on the backend.
    @GET("api/v1/shopping/merchants/categories")
    suspend fun getMerchantCategories(): MerchantCategoriesResponse

    @GET("api/v1/shopping/merchants/{id}/products")
    suspend fun getMerchantProducts(@Path("id") merchantId: String): MerchantProductsResponse

    // Real Coupang-style multi-item checkout (2026-07-18) -- see rw.itunda.commerce.web.OrderController.
    @POST("api/v1/orders")
    suspend fun placeOrder(@Header("Idempotency-Key") idempotencyKey: String, @Body request: PlaceOrderRequest): OrderDetailResponse

    @GET("api/v1/orders/my-orders")
    suspend fun getMyOrders(): OrdersResponse

    @GET("api/v1/orders/{id}")
    suspend fun getOrder(@Path("id") orderId: String): OrderDetailResponse

    // Real cancellation + refund (2026-07-18) -- buyer or seller, PLACED orders only.
    // See rw.itunda.commerce.OrderService.cancelOrder's own doc comment.
    @POST("api/v1/orders/{id}/cancel")
    suspend fun cancelOrder(@Path("id") orderId: String): OrderDetailResponse

    // Real Coupang Eats-style food delivery (2026-07-18) -- see rw.itunda.eats.web.EatsController.
    @POST("api/v1/eats/orders")
    suspend fun placeEatsOrder(@Header("Idempotency-Key") idempotencyKey: String, @Body request: PlaceEatsOrderRequest): EatsOrderDetailResponse

    @GET("api/v1/eats/orders/my-orders")
    suspend fun getMyEatsOrders(): EatsOrdersResponse

    // Real order detail, including items -- backs the real "Reorder" button
    // (2026-07-19): a buyer can re-populate a cart from a past order's real items
    // rather than retyping their whole order from scratch.
    @GET("api/v1/eats/orders/{id}")
    suspend fun getEatsOrder(@Path("id") orderId: String): EatsOrderDetailResponse

    // Real address-search autocomplete (2026-07-18) -- see EatsController.searchDeliveryAddress.
    @GET("api/v1/eats/geocode/search")
    suspend fun searchDeliveryAddress(@Query("q") query: String): AddressSearchResponse

    // Real post-delivery ratings & reviews (2026-07-18) -- see EatsController.submitReview.
    @POST("api/v1/eats/orders/{id}/review")
    suspend fun submitEatsReview(@Path("id") orderId: String, @Body request: SubmitEatsReviewRequest): EatsReviewResponse

    @GET("api/v1/eats/restaurants/{id}/rating")
    suspend fun getRestaurantRating(@Path("id") restaurantId: String): EatsRatingResponse

    // Real cancellation + refund (2026-07-18) -- buyer or restaurant, PLACED orders
    // only. See rw.itunda.eats.EatsOrderService.cancelOrder's own doc comment.
    @POST("api/v1/eats/orders/{id}/cancel")
    suspend fun cancelEatsOrder(@Path("id") orderId: String): EatsOrderDetailResponse

    @GET("api/v1/eats/orders/rider-deliveries")
    suspend fun getRiderDeliveries(): EatsOrdersResponse

    @GET("api/v1/eats/orders/available")
    suspend fun getAvailableDeliveries(): EatsOrdersResponse

    @POST("api/v1/eats/orders/{id}/claim")
    suspend fun claimDelivery(@Path("id") orderId: String): EatsOrderDetailResponse

    @POST("api/v1/eats/orders/{id}/rider-status")
    suspend fun updateRiderOrderStatus(@Path("id") orderId: String, @Body request: UpdateEatsOrderStatusRequest): EatsOrderDetailResponse

    @POST("api/v1/eats/riders/register")
    suspend fun registerRider(): RiderResponse

    @GET("api/v1/eats/riders/me")
    suspend fun getMyRiderProfile(): RiderResponse

    @POST("api/v1/eats/riders/availability")
    suspend fun setRiderAvailability(@Body request: SetRiderAvailabilityRequest): RiderResponse

    // Real bookmarked/favorited restaurants (2026-07-19) -- see
    // EatsFavoriteService.kt's own doc comment for why add/remove are both idempotent.
    @POST("api/v1/eats/restaurants/{id}/favorite")
    suspend fun addFavoriteRestaurant(@Path("id") restaurantId: String): AddFavoriteResponse

    @DELETE("api/v1/eats/restaurants/{id}/favorite")
    suspend fun removeFavoriteRestaurant(@Path("id") restaurantId: String): RemoveFavoriteResponse

    @GET("api/v1/eats/favorites")
    suspend fun getMyFavoriteRestaurants(): FavoriteRestaurantsResponse
}

data class TransactionHistoryResponse(val success: Boolean, val transactions: List<TransactionDto>)

// Real offline-action-queue replay (2026-07-13) -- mirrors
// services/backend/offline/src/main/kotlin/rw/itunda/offline/web/ActionsBatchController.kt
// exactly. See network/OfflineActionQueue.kt for the local persisted queue this replays.
data class BatchActionRequest(
    val clientActionId: String,
    val type: String,
    val idempotencyKey: String,
    val body: Map<String, @JvmSuppressWildcards Any?>,
)

data class BatchRequest(val actions: List<BatchActionRequest>)

data class BatchActionResultDto(
    val clientActionId: String,
    val type: String,
    val status: Int,
    val body: Map<String, @JvmSuppressWildcards Any?>,
)

data class BatchResponse(val success: Boolean, val results: List<BatchActionResultDto>)

// Network Client Singleton
object NetworkClient {
    // Was hardcoded to "http://10.0.2.2:8080/" -- the emulator-only loopback alias, at
    // the wrong port (services/backend listens on 4001). BuildConfig.API_BASE_URL
    // defaults to the same emulator alias at the right port, overridable at build time
    // for a physical device -- see app/build.gradle.kts's apiBaseUrl comment
    // (2026-07-11 fix).
    private const val BASE_URL = BuildConfig.API_BASE_URL

    // Must be initialized once, from ItundaApplication.onCreate(), before any request
    // fires -- see that file. Held nullable rather than lateinit so a request made
    // before init() (which should never happen, but interceptors must never crash the
    // whole app over it) just goes out unauthenticated instead of throwing.
    private var tokenStore: TokenStore? = null

    fun init(context: Context) {
        tokenStore = TokenStore(context.applicationContext)
    }

    fun currentTokenStore(): TokenStore =
        tokenStore ?: throw IllegalStateException("NetworkClient.init() was never called")

    // Real bearer-token injection (2026-07-11) -- previously commented out entirely
    // (see this file's git history / the removed "TODO: Inject Token" line), which is
    // exactly the gap SaroniteBridge.kt's ItundaSaroniteHostBridge.getAuthToken()
    // named as the reason it honestly returned null instead of a fabricated token.
    private val authInterceptor = Interceptor { chain ->
        val token = tokenStore?.getAccessToken()
        val newRequest = chain.request().newBuilder().apply {
            if (token != null) addHeader("Authorization", "Bearer $token")
        }.build()
        chain.proceed(newRequest)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .build()

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val apiService: ApiService by lazy { retrofit.create(ApiService::class.java) }
    val authApi: AuthApi by lazy { retrofit.create(AuthApi::class.java) }

    // Real WebSocket live-transport (2026-07-18) -- see
    // rw.itunda.app.websocket.MessagingWebSocketHandler's own doc comment for the real
    // backend push shape this mirrors exactly. Ported from bank-mfe's own
    // connectMessagingSocket, which established this session's push-payload contract.
    // Now routes both "message" (1:1) and "group_message" pushes -- group chat gained a
    // real mobile UI the same day this was extended.
    private val gson = Gson()

    fun connectMessagingSocket(onPush: (MessagingSocketPush) -> Unit): WebSocket {
        val token = tokenStore?.getAccessToken().orEmpty()
        val wsUrl = BASE_URL.replaceFirst("http://", "ws://").replaceFirst("https://", "wss://") + "ws/messaging?token=$token"
        val request = Request.Builder().url(wsUrl).build()
        return okHttpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val json = gson.fromJson(text, JsonObject::class.java)
                        when (json.get("type")?.asString) {
                            "message" -> onPush(MessagingSocketPush.DirectMessage(gson.fromJson(json.get("message"), MessageDto::class.java)))
                            "group_message" -> {
                                val groupId = json.get("groupConversationId")?.asString ?: return
                                onPush(MessagingSocketPush.GroupMessagePush(groupId, gson.fromJson(json.get("message"), GroupMessageDto::class.java)))
                            }
                            "presence" -> {
                                val userId = json.get("userId")?.asString ?: return
                                val online = json.get("online")?.asBoolean ?: return
                                onPush(MessagingSocketPush.PresenceChange(userId, online))
                            }
                            "typing" -> {
                                val userId = json.get("userId")?.asString ?: return
                                onPush(
                                    MessagingSocketPush.TypingChange(
                                        conversationId = json.get("conversationId")?.asString,
                                        groupConversationId = json.get("groupConversationId")?.asString,
                                        userId = userId,
                                    ),
                                )
                            }
                            "reaction" -> {
                                val messageId = json.get("messageId")?.asString ?: return
                                val reactionsType = object : com.google.gson.reflect.TypeToken<List<ReactionGroupDto>>() {}.type
                                val reactions: List<ReactionGroupDto> = gson.fromJson(json.get("reactions"), reactionsType)
                                onPush(
                                    MessagingSocketPush.ReactionChange(
                                        conversationId = json.get("conversationId")?.asString,
                                        groupConversationId = json.get("groupConversationId")?.asString,
                                        messageId = messageId,
                                        reactions = reactions,
                                    ),
                                )
                            }
                        }
                    } catch (e: Exception) {
                        // Real, non-critical -- a malformed/unexpected push shouldn't
                        // crash the socket listener; the 4s poll stays as the real
                        // fallback delivery path regardless.
                    }
                }
            },
        )
    }

    // Real typing indicator send (2026-07-19) -- best-effort, matching bank-mfe's own
    // sendTyping helper; OkHttp's WebSocket.send already silently no-ops on a
    // closed/never-connected socket.
    fun sendTyping(socket: WebSocket, conversationId: String? = null, groupConversationId: String? = null) {
        val payload = mutableMapOf<String, Any>("type" to "typing")
        conversationId?.let { payload["conversationId"] = it }
        groupConversationId?.let { payload["groupConversationId"] = it }
        socket.send(gson.toJson(payload))
    }
}

sealed class MessagingSocketPush {
    data class DirectMessage(val message: MessageDto) : MessagingSocketPush()
    data class GroupMessagePush(val groupConversationId: String, val message: GroupMessageDto) : MessagingSocketPush()
    // Real online/offline presence (2026-07-19) -- see
    // rw.itunda.core.realtime.RealtimeMessagePublisher.publishPresenceChange's own doc
    // comment for the real transition-only/1:1-only scoping.
    data class PresenceChange(val userId: String, val online: Boolean) : MessagingSocketPush()
    // Real typing indicator (2026-07-19) -- see
    // MessagingWebSocketHandler.handleTextMessage's own doc comment on the backend.
    // Ephemeral, never persisted; server-ratelimited to one relay per (user,
    // conversation) per 2s. Exactly one of conversationId/groupConversationId is set.
    data class TypingChange(val conversationId: String?, val groupConversationId: String?, val userId: String) : MessagingSocketPush()
    // Real live reaction push (2026-07-19) -- see
    // MessagingWebSocketHandler.publishReactionChange/publishGroupReactionChange's own
    // doc comments. Exactly one of conversationId/groupConversationId is set.
    data class ReactionChange(
        val conversationId: String?, val groupConversationId: String?, val messageId: String, val reactions: List<ReactionGroupDto>,
    ) : MessagingSocketPush()
}
