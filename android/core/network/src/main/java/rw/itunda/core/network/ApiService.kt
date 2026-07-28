package rw.itunda.core.network

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
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

// Mirrors services/backend/auth/src/main/kotlin/rw/itunda/auth/AuthDtos.kt exactly --
// same field names/nullability, so Gson deserializes the real backend's JSON directly.
data class RegisterRequest(
    val phoneNumber: String,
    val email: String?,
    val firstName: String,
    val lastName: String,
    val password: String,
    val referralCode: String? = null,
    // Added 2026-07-21, same reasoning as LoginRequest's deviceId/deviceName -- the
    // device that registers proves password ownership in the same request, so it's
    // auto-trusted server-side (DeviceService.recordRegistrationDevice) with no
    // separate step-up needed.
    val deviceId: String? = null,
    val deviceName: String? = null,
)

// deviceId/deviceName added 2026-07-21 -- mirrors bank-mfe's real device-binding login
// call exactly (lib/api.ts's login()). See DeviceStore.kt for how these are generated.
data class LoginRequest(val phoneNumber: String, val password: String, val deviceId: String? = null, val deviceName: String? = null)
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
    // Real hyperlocal neighborhood (2026-07-20) -- see AuthService.setNeighborhood's own
    // doc comment. Set via a real coordinate, reverse-geocoded server-side; never
    // self-declared free text.
    val neighborhood: String? = null,
    val neighborhoodVerifiedAt: String? = null,
    val neighborhoodVerificationCount: Int = 0,
    // Real age-eligibility gate for the Mini wallet (2026-07-28) -- see
    // MiniWalletService.kt's own doc comment. Set via AuthApi.setBirthDate.
    val birthDate: String? = null,
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

    // Real hyperlocal neighborhood (2026-07-20) -- see AuthService.setNeighborhood's own
    // doc comment. A real coordinate in, reverse-geocoded server-side into a real
    // neighborhood/sector name -- see lib/neighborhood.ts's bank-mfe equivalent this
    // mirrors exactly.
    @POST("api/v1/auth/profile/neighborhood")
    suspend fun setNeighborhood(@Body request: SetNeighborhoodRequest): ProfileResponse

    // Real age-eligibility gate for the Mini wallet (2026-07-28) -- see
    // AuthService.setBirthDate's own doc comment. birthDate is an ISO-8601 date
    // string ("YYYY-MM-DD").
    @POST("api/v1/auth/profile/birth-date")
    suspend fun setBirthDate(@Body request: SetBirthDateRequest): ProfileResponse

    // Real device binding (2026-07-21 port) -- mirrors bank-mfe's lib/device.ts
    // fetchMyDevices/verifyDevice/revokeDevice exactly (same real endpoints, same
    // shapes). See AuthController.kt on the backend for the real contract: verify
    // always re-verifies the CURRENT device (resolved server-side from the caller's
    // own JWT deviceId claim, never a client-supplied one), so no id is passed here.
    @GET("api/v1/auth/devices")
    suspend fun getMyDevices(): DevicesResponse

    @POST("api/v1/auth/devices/verify")
    suspend fun verifyDevice(@Body request: VerifyDeviceRequest): VerifyDeviceResponse

    @DELETE("api/v1/auth/devices/{deviceId}")
    suspend fun revokeDevice(@Path("deviceId") deviceId: String): RevokeDeviceResponse

    // Real push device-token registration (item 120) -- see backend DeviceToken.kt's
    // own doc comment: PushNotificationService.sendToUser silently no-ops for every
    // real user because no client anywhere ever registered a token. This app has no
    // real FCM SDK integrated, so reuses the same real, stable per-install device id
    // DeviceStore already established for trusted-device binding as this demo's
    // client-generated token, mirrors bank-mfe's registerDeviceToken exactly (item 119).
    @POST("api/v1/notifications/device-tokens")
    suspend fun registerDeviceToken(@Body request: RegisterDeviceTokenRequest): SuccessResponse
}

enum class DevicePlatform { ANDROID, IOS, WEB }
data class RegisterDeviceTokenRequest(val platform: DevicePlatform, val token: String)

data class ProfileResponse(val success: Boolean, val user: PublicUser)
data class SetNeighborhoodRequest(val latitude: Double, val longitude: Double)
data class SetBirthDateRequest(val birthDate: String)

// Mirrors services/backend/core/.../domain/TrustedDevice.kt exactly.
data class TrustedDeviceDto(
    val id: String,
    val userId: String,
    val deviceId: String,
    val deviceName: String?,
    val trusted: Boolean,
    val firstSeenAt: String,
    val lastSeenAt: String,
    val verifiedAt: String?,
)

data class DevicesResponse(val success: Boolean, val devices: List<TrustedDeviceDto>)
data class VerifyDeviceRequest(val password: String)
data class VerifyDeviceResponse(val success: Boolean, val device: TrustedDeviceDto)
data class RevokeDeviceResponse(val success: Boolean)

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

// Real 토스뱅크 외화통장 (foreign-currency account) equivalent (2026-07-25) -- see
// rw.itunda.wallet.ForeignCurrencyWalletService on the backend for the full account,
// incl. why USD/EUR/GBP specifically (real Rwandan diaspora remittance corridors) and
// why this is real-rate conversion between a user's own wallets, not a cross-border
// receiving rail.
data class OpenForeignWalletRequest(val currency: String)
data class ForeignWalletResponse(val success: Boolean, val wallet: Wallet)
data class ForeignWalletsResponse(val success: Boolean, val wallets: List<Wallet>)
data class ExchangeRateResponse(val success: Boolean, val from: String, val to: String, val rate: Double)
data class ConvertCurrencyRequest(val fromCurrency: String, val toCurrency: String, val amount: Double)
data class CurrencyConversionDto(
    val id: String, val userId: String, val fromCurrency: String, val toCurrency: String,
    val fromAmount: Double, val toAmount: Double, val rate: Double, val marginAmount: Double,
    val transactionId: String, val createdAt: String,
)
data class CurrencyConversionResponse(val success: Boolean, val conversion: CurrencyConversionDto)
data class CurrencyConversionsResponse(val success: Boolean, val conversions: List<CurrencyConversionDto>)

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

// Real direct itunda-to-itunda push-transfer (rw.itunda.p2p, 2026-07-20) -- mirrors
// P2pController's real SendDirectP2pRequest exactly. Deliberately distinct from
// QuoteTransferRequest/ConfirmTransferRequest above: those always route through a
// simulated external rail and never actually credit another itunda user's wallet, even
// when the recipient is a real itunda account (confirmed via a direct MySQL check while
// building this on the backend/bank-mfe side one day earlier). This is the real one --
// no quote step needed, since there's no external rail decision to quote.
data class SendDirectP2pRequest(val recipient: String, val amount: java.math.BigDecimal, val description: String = "")
data class SendDirectP2pResponse(val success: Boolean, val message: String, val transaction: TransactionDto, val newBalance: Double)

// Real Toss Bank 자동이체 (auto-transfer) equivalent -- mirrors AutoTransferController's
// real DTOs exactly. See AutoTransfer.kt's own doc comment on the backend for why
// execution reuses sendDirect's exact ledger movement rather than a separate rail.
enum class AutoTransferFrequency { WEEKLY, MONTHLY }
data class AutoTransferDto(
    val id: String,
    val recipientIdentifier: String,
    val recipientName: String,
    val amount: java.math.BigDecimal,
    val frequency: AutoTransferFrequency,
    val dayOfWeek: Int?,
    val dayOfMonth: Int?,
    val description: String,
    val status: String,
    val nextExecutionAt: String,
    val lastExecutedAt: String?,
    val executionCount: Int,
    val lastFailureReason: String?,
)
data class CreateAutoTransferRequest(
    val recipient: String,
    val amount: java.math.BigDecimal,
    val frequency: AutoTransferFrequency,
    val dayOfWeek: Int? = null,
    val dayOfMonth: Int? = null,
    val description: String = "",
)
data class AutoTransferResponse(val success: Boolean, val autoTransfer: AutoTransferDto)
data class AutoTransfersListResponse(val success: Boolean, val autoTransfers: List<AutoTransferDto>)

// Mirrors services/backend/savings's SavingsController.kt.
data class DepositRequest(val goalId: String, val amount: java.math.BigDecimal, val fromWalletId: String? = null)
data class DepositResponse(val success: Boolean, val message: String, val goal: SavingsGoal)
data class ClaimInterestResponse(val success: Boolean, val message: String, val claimed: Double? = null)

// Real Kakao Pay 머니굴리기 (round-up auto-save) equivalent (2026-07-25) -- see
// RoundUpSettings.kt's own doc comment. P2P transfers only in this v1, not every
// payment flow -- see RoundUpService.kt's processRoundUp doc comment.
data class RoundUpSettingsDto(
    val id: String,
    val userId: String,
    val enabled: Boolean,
    val roundToNearest: java.math.BigDecimal,
    val targetGoalId: String?,
)
data class RoundUpSettingsResponse(val success: Boolean, val settings: RoundUpSettingsDto?)
data class SetRoundUpSettingsRequest(val enabled: Boolean, val roundToNearest: java.math.BigDecimal, val targetGoalId: String? = null)

data class CreateAgentWithdrawalAuthorizationRequest(val amount: java.math.BigDecimal)
data class CancelAgentWithdrawalAuthorizationRequest(val code: String)
data class AgentWithdrawalAuthorizationDto(val id: String, val code: String, val amount: java.math.BigDecimal, val expiresAt: String, val status: String, val createdAt: String)
data class AgentWithdrawalAuthorizationResponse(val success: Boolean, val authorization: AgentWithdrawalAuthorizationDto)
data class AgentWithdrawalAuthorizationsResponse(val success: Boolean, val authorizations: List<AgentWithdrawalAuthorizationDto>)

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
    val quiet: Boolean = false,
    val pinnedMessageId: String? = null,
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
    val deletedAt: String? = null,
    val replyToMessageId: String? = null,
    val reactions: List<ReactionGroupDto> = emptyList(),
)

data class StartConversationRequest(val phoneNumber: String? = null, val otherUserId: String? = null)
data class SendMessageRequest(val body: String, val replyToMessageId: String? = null)
data class TalkContactDto(val userId: String, val name: String)
data class TalkContactsResponse(val success: Boolean, val contacts: List<TalkContactDto>)
data class ConversationQuietResponse(val success: Boolean, val quiet: Boolean)
data class CreateChatReportRequest(val messageId: String, val reason: String)
data class SetConversationQuietRequest(val quiet: Boolean)
data class ToggleReactionRequest(val emoji: String)

data class ConversationResponse(val success: Boolean, val conversation: ConversationDto)
data class ConversationsResponse(val success: Boolean, val conversations: List<ConversationSummaryDto>)
data class MessagesResponse(val success: Boolean, val messages: List<MessageDto>)
data class MessageResponse(val success: Boolean, val message: MessageDto)
data class PinnedMessageResponse(val success: Boolean, val message: MessageDto?)
data class ReactionsResponse(val success: Boolean, val reactions: List<ReactionGroupDto>)

// Real group chat (2026-07-18) -- see rw.itunda.messaging.web.GroupMessagingController.
// memberPhoneNumbers is the real human-friendly entry point (same reasoning as
// StartConversationRequest.phoneNumber).
data class CreateGroupRequest(val name: String, val memberUserIds: List<String> = emptyList(), val memberPhoneNumbers: List<String> = emptyList())
data class SendGroupMessageRequest(val body: String, val replyToMessageId: String? = null)
data class AddGroupMemberRequest(val userId: String)

data class GroupSummaryDto(
    val groupId: String,
    val name: String,
    val memberCount: Int,
    val lastMessageAt: String,
    val lastMessagePreview: String?,
    val unreadCount: Long,
    val quiet: Boolean = false,
)
data class GroupMessageDto(
    val id: String,
    val groupConversationId: String,
    val senderId: String,
    val body: String,
    val sentAt: String,
    val deletedAt: String? = null,
    val replyToMessageId: String? = null,
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
    // Real optional seller-set location (2026-07-18 backend) -- backs real proximity
    // search and, 2026-07-19, "Directions to this seller".
    val latitude: Double? = null,
    val longitude: Double? = null,
    val neighborhood: String? = null,
    val meetingPlace: String? = null,
    val photoUrl: String? = null,
    // buyerId added 2026-07-24 -- real optional buyer identification captured at
    // mark-sold time, see backend Listing.kt's own doc comment. Only set once a real
    // review becomes possible for this transaction.
    val buyerId: String? = null,
    // Real seller-paid sponsored placement (2026-07-25) -- see backend Listing.kt's own
    // doc comment. Null/expired means "not boosted" -- clients should only show a
    // "Sponsored" badge when this is a real, still-future ISO instant.
    val boostedUntil: String? = null,
)
data class MarkSoldRequest(val buyerPhoneNumber: String? = null)
data class BoostListingRequest(val days: Int)
data class BoostTiersResponse(val success: Boolean, val tiers: Map<String, Double>)

// Real "pay via itunda" Marketplace escrow (2026-07-25) -- see backend
// MarketplaceEscrow.kt's own doc comment. An opt-in safer alternative to the existing
// in-person cash handoff, never replacing it.
data class MarketplaceEscrowDto(
    val id: String, val listingId: String, val buyerId: String, val sellerId: String,
    val amount: Double, val fee: Double, val status: String,
    val holdTransactionId: String, val resolutionTransactionId: String? = null,
    val disputeReason: String? = null, val createdAt: String, val updatedAt: String,
)
data class MarketplaceEscrowResponse(val success: Boolean, val escrow: MarketplaceEscrowDto)
data class DisputeEscrowRequest(val reason: String)
// Real post-transaction review with asymmetric public/private visibility (2026-07-24)
// -- see backend HoodTransactionReview.kt's own doc comment. goodPoints/
// uncomfortablePoints are preset tag ids (never free text), matching Karrot's own real
// review UX.
data class SubmitHoodReviewRequest(val goodPoints: List<String> = emptyList(), val uncomfortablePoints: List<String> = emptyList())
data class HoodReviewDto(
    val id: String, val transactionType: String, val transactionId: String, val reviewerId: String, val revieweeId: String,
    val goodPoints: List<String>, val uncomfortablePoints: List<String>, val createdAt: String,
)
data class HoodReviewResponse(val success: Boolean, val review: HoodReviewDto)
data class HoodReviewsResponse(val success: Boolean, val reviews: List<HoodReviewDto>)

data class CreateListingRequest(
    val title: String,
    val description: String,
    val price: Double,
    val category: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val meetingPlace: String? = null,
    val photoUrl: String? = null,
)
data class UploadResponse(val success: Boolean, val url: String)
data class ListingResponse(val success: Boolean, val listing: ListingDto)
// trustScores added 2026-07-24 -- backend has spread this alongside every
// listing/job-post/property-listing browse response since 2026-07-21
// (rw.itunda.core.web.TrustScoreSupport), but no client ever parsed or rendered it.
// A sellerId/posterId/listerId -> User.trustScore map (Karrot-Score-style, 0-1000,
// starting at 30 -- see backend User.kt's own doc comment for why not a literal
// manner-temperature metaphor).
data class ListingsResponse(val success: Boolean, val listings: List<ListingDto>, val trustScores: Map<String, Int> = emptyMap())

// Real Marketplace listing wishlist (2026-07-21 backend + bank-mfe, ported here) --
// mirrors FavoriteRestaurantDto's exact shape; see ListingFavoriteService.kt's own doc
// comment on the backend for why add/remove are both idempotent.
data class FavoriteListingDto(val listingId: String, val title: String, val price: Double, val category: String, val favoritedAt: String)
data class FavoriteListingsResponse(val success: Boolean, val favorites: List<FavoriteListingDto>)

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

// Real KakaoTalk-style "선물하기" money gift (2026-07-20) -- see GiftService's own doc
// comment. Money leaves the sender's wallet into a real escrow account the moment a
// gift is sent, and only reaches the recipient's wallet once they explicitly claim it
// (or is auto-refunded after 7 days). Rendered inline as a gift bubble, same "special
// message body" convention PriceOfferDto already established.
data class GiftDto(
    val id: String,
    val senderId: String,
    val recipientId: String,
    val conversationId: String,
    val messageId: String,
    val amount: Double,
    val note: String?,
    val status: String,
    val holdTransactionId: String,
    val claimTransactionId: String?,
    val expiresAt: String,
    val claimedAt: String?,
    val createdAt: String,
)
data class SendGiftInConversationRequest(val amount: Double, val note: String? = null)
data class GiftResponse(val success: Boolean, val gift: GiftDto)
data class GiftsResponse(val success: Boolean, val gifts: List<GiftDto>)

// Real 동네생활-style community board (2026-07-19) -- see rw.itunda.community.web.CommunityController.
data class CommunityCategoryDto(val id: String, val label: String)
data class CommunityPostDto(
    val id: String, val authorId: String, val category: String, val title: String, val body: String,
    val status: String, val likeCount: Long, val commentCount: Long, val createdAt: String,
    val latitude: Double? = null, val longitude: Double? = null,
    // groupConversationId added 2026-07-24 -- see backend CommunityPost.kt's own doc
    // comment. Only ever set for category == "meetup" posts that have had at least one
    // real join.
    val groupConversationId: String? = null,
    // Real 당근모임-style structured meetup fields (2026-07-25) -- see backend
    // CommunityPost.kt's own doc comment. Only ever set for category == "meetup";
    // eventDate stays a plain ISO string, same convention every other temporal field in
    // this file already uses. capacity null means unlimited.
    val eventDate: String? = null,
    val capacity: Int? = null,
)
data class CreateCommunityPostRequest(
    val category: String, val title: String, val body: String,
    val latitude: Double? = null, val longitude: Double? = null,
    val eventDate: String? = null, val capacity: Int? = null,
)
data class CommunityPostResponse(val success: Boolean, val post: CommunityPostDto)
// joinedCounts added 2026-07-24 -- postId -> real member count of that meetup's group
// chat, closing docs/DESIGN_REFERENCES.md Section 4 recommendation #4's "같이해요
// (join-together) posts get a dedicated pinned mid-feed slot."
data class CommunityPostsResponse(val success: Boolean, val posts: List<CommunityPostDto>, val joinedCounts: Map<String, Int> = emptyMap())
data class CommunityCategoriesResponse(val success: Boolean, val categories: List<CommunityCategoryDto>)
data class CommunityPostDetailResponse(val success: Boolean, val post: CommunityPostDto, val authorName: String, val likedByMe: Boolean)
data class CommunityCommentDto(val id: String, val postId: String, val authorId: String, val body: String, val createdAt: String)
data class CommunityCommentWithAuthorDto(val comment: CommunityCommentDto, val authorName: String)
data class CommunityCommentsResponse(val success: Boolean, val comments: List<CommunityCommentWithAuthorDto>)
data class AddCommunityCommentRequest(val body: String)
data class CommunityCommentResponse(val success: Boolean, val comment: CommunityCommentDto)
data class ToggleCommunityLikeResponse(val success: Boolean, val liked: Boolean)
// Real 같이해요 (join-together) explicit join (2026-07-24) -- see backend
// CommunityService.joinMeetup's own doc comment.
data class JoinMeetupResponse(val success: Boolean, val groupId: String)

// Real 당근알바-style local job board (2026-07-19) -- see rw.itunda.jobs.web.JobPostController.
data class JobCategoryDto(val id: String, val label: String)
data class JobPostDto(
    val id: String, val posterId: String, val category: String, val title: String, val description: String,
    val payType: String, val payAmount: Double, val status: String, val createdAt: String,
    val latitude: Double? = null, val longitude: Double? = null,
    // workerId added 2026-07-24 -- real optional worker identification captured at
    // mark-filled time, see backend JobPost.kt's own doc comment. Only set once a
    // real review becomes possible for this transaction.
    val workerId: String? = null,
)
data class MarkFilledRequest(val workerPhoneNumber: String? = null)
data class CreateJobPostRequest(
    val category: String, val title: String, val description: String, val payType: String, val payAmount: Double,
    val latitude: Double? = null, val longitude: Double? = null,
)
data class JobPostResponse(val success: Boolean, val post: JobPostDto)
data class SuccessResponse(val success: Boolean)
data class JobPostsResponse(val success: Boolean, val posts: List<JobPostDto>, val trustScores: Map<String, Int> = emptyMap())
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
data class FavoriteJobPostsResponse(val success: Boolean, val favorites: List<FavoriteJobPostDto>)

// Real 당근알바-style structured application (2026-07-25) -- see backend
// JobApplicationService's own doc comment.
data class ApplyToJobRequest(val message: String)
data class JobApplicationDto(
    val id: String, val jobPostId: String, val applicantId: String, val message: String,
    val status: String, val submittedAt: String, val respondedAt: String? = null,
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
data class PropertyListingsResponse(val success: Boolean, val listings: List<PropertyListingDto>, val trustScores: Map<String, Int> = emptyMap())
data class FavoritePropertyListingDto(val propertyListingId: String, val title: String, val price: Double, val listingType: String, val favoritedAt: String)
data class FavoritePropertyListingsResponse(val success: Boolean, val favorites: List<FavoritePropertyListingDto>)
data class PropertyTypesResponse(val success: Boolean, val propertyTypes: List<PropertyTypeDto>)
data class ContactListerResponse(val success: Boolean, val conversation: ConversationDto)

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
)
data class ShoppingMerchantsResponse(val success: Boolean, val merchants: List<ShoppingMerchantDto>)

// Real "search this map" + "directions" (2026-07-19) -- see rw.itunda.maps.MapsService's
// own doc comment on the backend for why these are a new, general-purpose front door
// onto itunda's already-deployed self-hosted Nominatim/OSRM.
data class PlaceSearchResultDto(val displayName: String, val latitude: Double, val longitude: Double)
data class MapsSearchResponse(val success: Boolean, val results: List<PlaceSearchResultDto>)
data class MapsReverseGeocodeResponse(val success: Boolean, val placeName: String?)
data class RouteStepDto(val instruction: String, val distanceMeters: Double, val streetName: String?)
data class RouteResultDto(val distanceKm: Double, val durationMinutes: Double, val geometry: List<List<Double>>, val steps: List<RouteStepDto> = emptyList())
data class MapsDirectionsResponse(val success: Boolean, val route: RouteResultDto)
// Real alternative routes (2026-07-22) -- see OsrmRoutingClient.routeAlternatives' own
// doc comment on the backend. Often just a single-element list -- OSRM itself decides
// whether a real alternative exists for a given trip.
data class MapsDirectionsAlternativesResponse(val success: Boolean, val routes: List<RouteResultDto>)
// Ordered multi-stop directions (2026-07-22). The backend deliberately accepts only
// 2–5 Rwanda waypoints so the self-hosted OSRM request and the mobile itinerary stay
// legible. The returned RouteResultDto is one continuous road route through that order.
data class ItineraryWaypointRequest(val latitude: Double, val longitude: Double)
data class ItineraryDirectionsRequest(val waypoints: List<ItineraryWaypointRequest>, val mode: String = "DRIVING")
data class MapsItineraryResponse(val success: Boolean, val route: RouteResultDto)
data class MerchantCategoriesResponse(val success: Boolean, val categories: List<String>)

// Real "Deals" rail (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 5
// recommendation #8: a curated deal rail on the Shop landing surface. Every entry is a
// real merchant-set discount, never a fabricated promo -- see backend
// MerchantProductRepository.findDeals's own doc comment.
data class DealProductDto(
    val id: String, val merchantId: String, val merchantName: String, val name: String, val price: Double,
    val imageUrl: String? = null, val originalPrice: Double? = null, val discountPercent: Int? = null, val description: String? = null,
)
data class DealsResponse(val success: Boolean, val products: List<DealProductDto>)

// Real "nearby places" category search + bookmarked/favorite places (2026-07-19) -- see
// rw.itunda.maps.MapsService's own doc comment on the backend. `MAP_NEARBY_CATEGORIES`
// mirrors bank-mfe's own hardcoded `NEARBY_CATEGORIES` list exactly.
data class NearbyPlaceDto(val displayName: String, val latitude: Double, val longitude: Double, val distanceKm: Double)
data class MapNearbyResponse(val success: Boolean, val places: List<NearbyPlaceDto>)
// Public-safe cash-point discovery. The backend deliberately omits tills, operator
// details, and cash availability; customers only need a name, location and distance.
data class NearbyAgentDto(val id: String, val displayName: String, val latitude: Double, val longitude: Double, val distanceKm: Double)
data class NearbyAgentsResponse(val success: Boolean, val agents: List<NearbyAgentDto>)
// folderName/color added 2026-07-22 -- see MapBookmark.kt's own doc comment on the
// backend (migration V73). Every bookmark belongs to exactly one named folder with its
// own pin color; a bookmark saved before this existed defaults into "Saved places" /
// "#F5A623" (the same star-yellow the ★ icon already used).
data class MapBookmarkDto(val id: String, val displayName: String, val latitude: Double, val longitude: Double, val folderName: String, val color: String, val createdAt: String)
data class MapBookmarksResponse(val success: Boolean, val bookmarks: List<MapBookmarkDto>)
data class AddMapBookmarkRequest(val displayName: String, val latitude: Double, val longitude: Double, val folderName: String? = null, val color: String? = null)
data class AddMapBookmarkResponse(val success: Boolean, val bookmark: MapBookmarkDto)
// Real "move to folder" (2026-07-22) -- see MapsService.moveBookmark's own doc comment.
data class MoveMapBookmarkRequest(val folderName: String, val color: String)
data class MoveMapBookmarkResponse(val success: Boolean, val bookmark: MapBookmarkDto)
data class RemoveMapBookmarkResponse(val success: Boolean)

data class MapPlaceCategory(val id: String, val label: String)
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
    MapPlaceCategory("ITUNDA_AGENT", "Itunda agents"),
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
data class EatsMenuOptionGroupDto(val id: String, val name: String, val choices: List<EatsMenuOptionChoiceDto> = emptyList())

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
data class MerchantSummaryDto(val id: String, val businessName: String)
data class MerchantProductsResponse(val success: Boolean, val merchant: MerchantSummaryDto, val products: List<MerchantProductDto>)

// Real Naver Smart Store-style "알림받기" (follow a store for its own broadcast
// notices) -- see backend MerchantFollowService.kt's own doc comment. Mirrors
// bank-mfe's lib/shopping.ts FollowedMerchant exactly.
data class FollowedMerchantDto(val merchantId: String, val businessName: String, val category: String?, val followedAt: String)
data class FollowedMerchantsResponse(val success: Boolean, val follows: List<FollowedMerchantDto>)
data class MerchantFollowDto(val id: String, val userId: String, val merchantId: String, val createdAt: String)
data class MerchantFollowResponse(val success: Boolean, val follow: MerchantFollowDto)

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
)
data class FavoriteProductsResponse(val success: Boolean, val favorites: List<FavoriteProductDto>)

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
    val createdAt: String,
)
data class ProductReviewResponse(val success: Boolean, val review: ProductReviewDto)
data class ProductReviewsResponse(val success: Boolean, val reviews: List<ProductReviewDto>)
data class ProductRatingResponse(val success: Boolean, val average: Double?, val count: Long)

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

// selectedOptionsJson added 2026-07-21 -- unitPrice above already includes every
// selected choice's priceDelta; this is purely a human-readable receipt summary, never
// a second pricing source. See EatsOrderItem.kt's own doc comment.
data class EatsOrderItemDto(
    val id: String, val orderId: String, val productId: String, val productName: String, val unitPrice: Double, val quantity: Int,
    val selectedOptionsJson: String? = null,
)
data class EatsOrderDetailResponse(val success: Boolean, val order: EatsOrderDto, val items: List<EatsOrderItemDto>)
data class EatsOrdersResponse(val success: Boolean, val orders: List<EatsOrderDto>)

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
data class DineInOrdersResponse(val success: Boolean, val orders: List<DineInOrderDto>)

data class RiderDto(val id: String, val userId: String, val walletId: String, val status: String, val available: Boolean, val createdAt: String)
data class RiderResponse(val success: Boolean, val rider: RiderDto)

// Real bookmarked/favorited restaurants (2026-07-19) -- add/remove are both idempotent
// on the backend, see EatsFavoriteService.kt's own doc comment.
data class FavoriteRestaurantDto(val restaurantId: String, val businessName: String, val category: String?, val favoritedAt: String)
data class FavoriteRestaurantsResponse(val success: Boolean, val favorites: List<FavoriteRestaurantDto>)
data class AddFavoriteResponse(val success: Boolean)
data class RemoveFavoriteResponse(val success: Boolean)

// Real Toss Securities-style stock investing (2026-07-20) -- see ApiService's own
// getStocks doc comment for the full account.
data class StockDto(val id: String, val symbol: String, val name: String, val price: Double, val change: Double, val changePercent: Double, val marketCap: String, val volume: Long)
data class StocksResponse(val success: Boolean, val stocks: List<StockDto>, val watchlist: List<StockDto>? = null)
data class StockPricePointDto(val date: String, val price: Double)
data class StockHistoryResponse(val success: Boolean, val history: List<StockPricePointDto>)
data class PortfolioValuePointDto(val date: String, val value: Double)
data class PortfolioHistoryResponse(val success: Boolean, val history: List<PortfolioValuePointDto>)
data class StockHoldingDto(val stockId: String, val symbol: String, val name: String, val shares: Double, val avgPrice: Double, val currentPrice: Double, val value: Double, val `return`: Double)
data class StockPortfolioDto(val totalValue: Double, val totalReturn: Double, val totalReturnPercent: Double, val holdings: List<StockHoldingDto>)
data class StockPortfolioResponse(val success: Boolean, val portfolio: StockPortfolioDto)
data class TradeStockRequest(val stockId: String, val shares: Double)
data class TradeStockResponse(val success: Boolean, val message: String)
data class WatchStockResponse(val success: Boolean)
data class UnwatchStockResponse(val success: Boolean)

// Real Toss-style unified account overview (rw.itunda.overview.OverviewService) --
// found 2026-07-22 fully built on the backend with zero client UI anywhere (Android,
// iOS, or bank-mfe web). Aggregates wallets/savings/loans/investments/insurance/linked
// external accounts in one call; see OverviewService.kt's own doc comment for why
// insurance is excluded from netWorth (a sunk expense, not an asset).
data class AccountSummaryDto(val id: String, val type: String, val name: String, val balance: java.math.BigDecimal, val currency: String)
data class OverviewSavingsSummaryDto(val totalSaved: java.math.BigDecimal, val goalCount: Int)
data class OverviewLoansSummaryDto(val totalOutstanding: java.math.BigDecimal, val activeCount: Int)
data class OverviewInvestmentsSummaryDto(val totalCostBasis: java.math.BigDecimal, val holdingCount: Int)
data class OverviewInsuranceSummaryDto(val activePolicyCount: Int, val totalMonthlyPremium: java.math.BigDecimal)
data class LinkedAccountSummaryDto(
    val id: String, val provider: String, val maskedAccountNumber: String, val status: String,
    val demoBalance: java.math.BigDecimal?, val demoBalanceCurrency: String?, val isDemoBalance: Boolean,
)
data class OverviewResponse(
    val success: Boolean,
    val netWorth: java.math.BigDecimal,
    val accounts: List<AccountSummaryDto>,
    val savings: OverviewSavingsSummaryDto,
    val loans: OverviewLoansSummaryDto,
    val investments: OverviewInvestmentsSummaryDto,
    val insurance: OverviewInsuranceSummaryDto,
    val linkedAccounts: List<LinkedAccountSummaryDto>,
)

// Real external bank/MoMo account linking (rw.itunda.overview.LinkedAccountService) --
// a real simulated per-rail verification (same ProviderConnector/RailCatalog
// mechanism transfers/bills/airtime use), demo balance only generated when linking
// actually succeeds. No live Open Banking access exists, so demoBalance is a real,
// honestly-labeled demo value -- see LinkedAccount.kt's own doc comment. This is the
// raw entity shape LinkedAccountController returns, distinct from the summary shape
// OverviewResponse.linkedAccounts uses above (different field names: userId/
// externalAccountNumberMasked/linkedAt/unlinkedAt here, no isDemoBalance).
data class LinkAccountRequest(val provider: String, val externalAccountNumber: String)
data class LinkedAccountEntityDto(
    val id: String, val userId: String, val provider: String, val externalAccountNumberMasked: String,
    val status: String, val failureReason: String?, val linkedAt: String, val unlinkedAt: String?,
    val demoBalance: java.math.BigDecimal?, val demoBalanceCurrency: String?,
)
data class LinkAccountResponse(val success: Boolean, val linkedAccount: LinkedAccountEntityDto)
data class LinkedAccountsResponse(val success: Boolean, val linkedAccounts: List<LinkedAccountEntityDto>)

// Real multi-lender loan marketplace (rw.itunda.loans) -- BNR-licensed partner banks
// (Bank of Kigali, Equity Bank Rwanda, Urwego Bank) alongside itunda's own book; only
// itunda has a real underwriting/disbursement path, see LoanOffer.kt's own doc
// comment. Found 2026-07-22 fully built on the backend, but every "Loan"/"Get a loan"
// row in this app was 100% hardcoded static text with no API call at all.
data class LoanOfferDto(val id: String, val lenderId: String, val lenderName: String, val name: String, val maxAmount: java.math.BigDecimal, val interestRate: Double, val term: String, val requirements: String)
data class LenderDto(val id: String, val name: String, val kind: String)
data class LoanOffersResponse(val success: Boolean, val offers: List<LoanOfferDto>)
data class LendersResponse(val success: Boolean, val lenders: List<LenderDto>)
data class LoanAccountDto(val id: String, val userId: String, val walletId: String, val offerId: String, val principal: java.math.BigDecimal, val outstanding: java.math.BigDecimal, val interestRate: Double, val status: String, val disbursedAt: String)
data class MyLoansResponse(val success: Boolean, val loans: List<LoanAccountDto>)
data class ApplyLoanRequest(val loanId: String, val amount: java.math.BigDecimal)
data class ApplyLoanResponse(val success: Boolean, val message: String, val loan: LoanAccountDto)
data class RepayLoanRequest(val loanId: String, val amount: java.math.BigDecimal)
data class RepayLoanTransactionDto(val id: String, val amount: java.math.BigDecimal, val type: String, val status: String, val description: String, val completedAt: String)
data class RepayLoanResponse(val success: Boolean, val message: String, val transaction: RepayLoanTransactionDto, val remaining: java.math.BigDecimal, val newBalance: java.math.BigDecimal)

// Real customer support tickets, tied to a specific transaction (rw.itunda.support) --
// found 2026-07-22 fully built on the backend with zero client UI anywhere; the
// "Support" section in this app was five static rows (FAQ/Live chat/...) with no
// backend behind any of them. category must be one of GENERAL/PAYMENT_DISPUTE/
// ACCOUNT_TAKEOVER -- see SupportTicket.kt's own doc comment on why a ticket is always
// tied to a specific transaction, not a free-floating complaint.
data class CreateSupportTicketRequest(val transactionId: String, val category: String, val description: String)
data class SupportTicketDto(
    val id: String, val userId: String, val transactionId: String, val category: String, val description: String,
    val status: String, val resolution: String?, val resolutionNotes: String?, val refundTransactionId: String?,
    val frozeWalletId: String?, val dueBy: String, val reviewedBy: String?, val createdAt: String, val resolvedAt: String?,
)
data class CreateSupportTicketResponse(val success: Boolean, val ticket: SupportTicketDto)
data class SupportTicketsResponse(val success: Boolean, val tickets: List<SupportTicketDto>)

// Real "alternative data" credit score (rw.itunda.creditscore, computation lives in
// :core's CreditScoreService so LoansService's real risk-gating can share it) --
// found 2026-07-22 fully built on the backend with zero client UI anywhere. Not a
// real bureau score (no regulatory access exists for one) -- computed live from
// itunda's own real transaction/loan/savings/KYC history on every call.
data class CreditScoreFactorDto(val name: String, val points: Int, val description: String)
data class CreditScoreResponse(val success: Boolean, val score: Int, val factors: List<CreditScoreFactorDto>, val computedAt: String)

// Real digital identity/signing certificate (rw.itunda.certificate) -- already real
// and wired into bank-mfe (web) since 2026-07-17, but found 2026-07-22 completely
// absent from the Android app, a platform-parity gap rather than a never-built
// feature. Mirrors bank-mfe's lib/certificate.ts field-for-field.
data class CertificateDto(
    val id: String, val userId: String, val serialNumber: String, val publicKeyBase64: String,
    val algorithm: String, val status: String, val issuedAt: String, val expiresAt: String, val revokedAt: String?,
)
data class IssueCertificateResponse(val success: Boolean, val certificate: CertificateDto, val privateKey: String)
data class MyCertificateResponse(val success: Boolean, val certificate: CertificateDto?)
data class RevokeCertificateResponse(val success: Boolean, val certificate: CertificateDto)

// Real KYC identity submission (rw.itunda.identity) -- found 2026-07-22 fully built on
// the backend with zero client UI anywhere. documentReference is a real, honest
// demo-mode stand-in for an uploaded ID scan/selfie (no file-storage layer exists in
// this backend, see KycSubmission.kt's own doc comment) -- a free-text reference
// string, not an actual image upload. documentType must be NATIONAL_ID or PASSPORT for
// a personal submission (BUSINESS_TIN/KYB is a separate merchant-onboarding concern,
// out of scope for this consumer-app screen).
data class SubmitIdentityRequest(val documentType: String, val documentNumber: String, val documentReference: String)
data class KycSubmissionDto(
    val id: String, val userId: String, val documentType: String, val documentNumber: String, val documentReference: String,
    val status: String, val submittedAt: String, val reviewedBy: String?, val reviewedAt: String?, val decisionReason: String?,
    val autoVerificationStatus: String?, val autoVerificationDetail: String?,
)
data class SubmitIdentityResponse(val success: Boolean, val submission: KycSubmissionDto)
data class IdentityStatusResponse(val success: Boolean, val submissions: List<KycSubmissionDto>)

// Real KakaoPay-style "정산하기" (chat-embedded split-bill), rw.itunda.splitbill --
// found 2026-07-22 fully built on the backend, tied to an existing real group
// conversation, with zero client UI anywhere despite group chat itself being fully
// wired. See SplitBill.kt's own doc comment: a flat, even split with the rounding
// remainder silently absorbed into one participant's share so shares always sum
// exactly to totalAmount; each participant pays their own share directly to the
// organizer via a real wallet-to-wallet push, no escrow.
data class CreateSplitBillRequest(
    val totalAmount: java.math.BigDecimal, val description: String, val participantUserIds: List<String>,
    // Real KakaoPay 사다리타기 (ladder-game) mode (2026-07-25) -- see backend
    // SplitBillService.ladderSplit's own doc comment. Omit (or "EVEN") for the
    // unchanged flat-split v1 behavior.
    val mode: String = "EVEN",
    val ladderVarianceLevel: Int? = null,
)
data class SplitBillDto(
    val id: String, val organizerId: String, val groupConversationId: String, val messageId: String,
    val totalAmount: java.math.BigDecimal, val description: String, val status: String,
    val settledAt: String?, val createdAt: String,
    val mode: String = "EVEN", val ladderVarianceLevel: Int? = null,
)
data class SplitBillParticipantDto(
    val id: String, val splitBillId: String, val userId: String, val shareAmount: java.math.BigDecimal,
    val status: String, val paidTransactionId: String?, val paidAt: String?, val createdAt: String,
)
data class CreateSplitBillResponse(val success: Boolean, val splitBill: SplitBillDto, val participants: List<SplitBillParticipantDto>)
data class SplitBillResponse(val success: Boolean, val splitBill: SplitBillDto, val participants: List<SplitBillParticipantDto>)
data class SplitBillWithParticipants(val splitBill: SplitBillDto, val participants: List<SplitBillParticipantDto>)
data class SplitBillsForGroupResponse(val success: Boolean, val splitBills: List<SplitBillWithParticipants>)
data class PaySplitBillShareResponse(val success: Boolean, val participant: SplitBillParticipantDto)

// Real saved-contacts list for quick transfers (rw.itunda.contacts) -- found
// 2026-07-22 fully built on the backend (a real IDOR fix ported from the original
// Express controller, see ContactsController.kt's own doc comment) with zero client
// UI anywhere. RecipientEntryScreen.kt's "Recent" row was a single hardcoded demo
// name ("TUYIZERE Eric"), not backed by any real data.
data class AddContactRequest(val name: String, val bank: String? = null, val phoneNumber: String)
data class ContactDto(val id: String, val userId: String, val name: String, val bank: String, val acc: String, val phoneNumber: String, val color: String, val letter: String)
data class ContactsResponse(val success: Boolean, val contacts: List<ContactDto>)
data class AddContactResponse(val success: Boolean, val contact: ContactDto)

// Real KakaoBank 26주적금-style escalating 26-week savings plan (2026-07-21) -- the
// first mobile UI this feature has ever had; see WeeklySavingsController.kt/
// WeeklySavingsService.kt and the domain entities they wrap
// (services/backend/core/.../domain/WeeklySavingsPlan.kt / WeeklySavingsInstallment.kt)
// for the real ledger-backed mechanics. TERM_WEEKS=26 and ESCALATION_STEP_WEEKS=4 are
// display-only constants mirrored from WeeklySavingsService.kt below -- no endpoint
// exposes them since they never change.
data class WeeklySavingsPlanDto(
    val id: String,
    val userId: String,
    val walletId: String,
    val name: String,
    val baseWeeklyAmount: Double,
    val escalationRate: Double,
    val openingWeekday: Int,
    val baseRate: Double,
    val bonusRate: Double,
    val installmentsCollected: Int,
    val weeksElapsed: Int,
    val currentAmount: Double,
    val streakBroken: Boolean,
    val status: String,
    val nextInstallmentDueAt: String,
    val createdAt: String,
    val maturedAt: String? = null,
    val cancelledAt: String? = null,
    val withdrawnAt: String? = null,
    val totalInterestPaid: Double? = null,
)

data class WeeklySavingsInstallmentDto(
    val id: String,
    val planId: String,
    val weekNumber: Int,
    val amount: Double,
    val depositedAt: String,
)

data class WeeklySavingsPlansResponse(val success: Boolean, val plans: List<WeeklySavingsPlanDto>)

data class WeeklySavingsPlanDetailResponse(
    val success: Boolean,
    val plan: WeeklySavingsPlanDto,
    val walletBalance: Double,
    val installments: List<WeeklySavingsInstallmentDto>,
)

data class CreateWeeklySavingsPlanRequest(
    val name: String,
    val baseWeeklyAmount: java.math.BigDecimal,
    val escalationRate: java.math.BigDecimal,
)

// POST /plans's real response shape is just {success, plan} -- unlike get/cancel/
// withdraw it never returns walletBalance/installments (a brand-new plan's wallet is
// always empty and has no installments yet), so this gets its own response type
// rather than reusing WeeklySavingsPlanDetailResponse with fields that would silently
// come back null/0.0 via Gson's reflection-based construction.
data class CreateWeeklySavingsPlanResponse(val success: Boolean, val plan: WeeklySavingsPlanDto)

data class WeeklySavingsActionResponse(
    val success: Boolean,
    val message: String,
    val plan: WeeklySavingsPlanDto,
    val walletBalance: Double,
    val installments: List<WeeklySavingsInstallmentDto>,
)

// Retrofit Interface to map to your Spring endpoints -- all require the real
// Bearer token NetworkClient's authInterceptor now injects (2026-07-11).
interface ApiService {
    @GET("api/v1/wallet")
    suspend fun getWallets(): WalletResponse

    // Real Kakao Pay 소비 리포트-style spending categorization (rw.itunda.wallet.
    // WalletService.getSpendingInsight, real since 2026-07-13) -- first Android client
    // for this feature (item 107, found backend-only via a fresh matrix scan; bank-mfe
    // ported the same day as item 106). Ledger-based, not the transactions table -- see
    // the backend's own doc comment for the full account.
    @GET("api/v1/wallet/spending")
    suspend fun getSpendingInsight(): SpendingInsightResponse

    // Real 토스뱅크 외화통장 (foreign-currency account) equivalent (2026-07-25) -- see
    // rw.itunda.wallet.web.ForeignCurrencyController.
    @POST("api/v1/wallet/foreign-currency/wallets")
    suspend fun openForeignWallet(@Body request: OpenForeignWalletRequest): ForeignWalletResponse

    @GET("api/v1/wallet/foreign-currency/wallets")
    suspend fun getForeignWallets(): ForeignWalletsResponse

    @GET("api/v1/wallet/foreign-currency/rate")
    suspend fun getExchangeRate(@Query("from") from: String, @Query("to") to: String): ExchangeRateResponse

    @POST("api/v1/wallet/foreign-currency/convert")
    suspend fun convertCurrency(@Body request: ConvertCurrencyRequest): CurrencyConversionResponse

    @GET("api/v1/wallet/foreign-currency/conversions")
    suspend fun getMyConversions(): CurrencyConversionsResponse

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

    // Real direct P2P push-transfer (2026-07-20) -- see SendDirectP2pRequest's own doc
    // comment for why this replaces quoteTransfer/confirmTransfer above in
    // MainViewModel.sendTransfer.
    @POST("api/v1/p2p/send")
    suspend fun sendDirect(@Header("Idempotency-Key") idempotencyKey: String, @Body request: SendDirectP2pRequest): SendDirectP2pResponse

    @POST("api/v1/p2p/auto-transfers")
    suspend fun createAutoTransfer(@Body request: CreateAutoTransferRequest): AutoTransferResponse

    @GET("api/v1/p2p/auto-transfers")
    suspend fun getMyAutoTransfers(): AutoTransfersListResponse

    @POST("api/v1/p2p/auto-transfers/{id}/pause")
    suspend fun pauseAutoTransfer(@Path("id") id: String): AutoTransferResponse

    @POST("api/v1/p2p/auto-transfers/{id}/resume")
    suspend fun resumeAutoTransfer(@Path("id") id: String): AutoTransferResponse

    @DELETE("api/v1/p2p/auto-transfers/{id}")
    suspend fun cancelAutoTransfer(@Path("id") id: String): AutoTransferResponse

    @POST("api/v1/savings/deposit")
    suspend fun depositToGoal(@Header("Idempotency-Key") idempotencyKey: String, @Body request: DepositRequest): DepositResponse

    @POST("api/v1/savings/interest-jar/claim")
    suspend fun claimInterest(@Header("Idempotency-Key") idempotencyKey: String): ClaimInterestResponse

    // No Idempotency-Key -- a settings write, not money movement itself. See
    // RoundUpController.kt's own doc comment.
    @GET("api/v1/savings/round-up")
    suspend fun getRoundUpSettings(): RoundUpSettingsResponse

    @POST("api/v1/savings/round-up")
    suspend fun setRoundUpSettings(@Body request: SetRoundUpSettingsRequest): RoundUpSettingsResponse

    @POST("api/v1/wallet/agent-withdrawal-authorizations")
    suspend fun createAgentWithdrawalAuthorization(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: CreateAgentWithdrawalAuthorizationRequest,
    ): AgentWithdrawalAuthorizationResponse

    @GET("api/v1/wallet/agent-withdrawal-authorizations")
    suspend fun getAgentWithdrawalAuthorizations(): AgentWithdrawalAuthorizationsResponse

    @POST("api/v1/wallet/agent-withdrawal-authorizations/cancel")
    suspend fun cancelAgentWithdrawalAuthorization(@Body request: CancelAgentWithdrawalAuthorizationRequest): AgentWithdrawalAuthorizationResponse

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

    @GET("api/v1/messages/contacts")
    suspend fun getTalkContacts(): TalkContactsResponse

    @GET("api/v1/messages/conversations/{id}/messages")
    suspend fun getMessages(@Path("id") conversationId: String): MessagesResponse

    @GET("api/v1/messages/conversations/{id}/messages/search")
    suspend fun searchMessages(@Path("id") conversationId: String, @Query("query") query: String): MessagesResponse

    @POST("api/v1/messages/conversations/{id}/messages")
    suspend fun sendMessage(@Path("id") conversationId: String, @Body request: SendMessageRequest): MessageResponse

    @DELETE("api/v1/messages/conversations/{id}/messages/{messageId}")
    suspend fun deleteMessage(@Path("id") conversationId: String, @Path("messageId") messageId: String): SuccessResponse

    @GET("api/v1/messages/conversations/{id}/pin")
    suspend fun getPinnedConversationMessage(@Path("id") conversationId: String): PinnedMessageResponse

    @POST("api/v1/messages/conversations/{id}/pin/{messageId}")
    suspend fun pinConversationMessage(@Path("id") conversationId: String, @Path("messageId") messageId: String): SuccessResponse

    @DELETE("api/v1/messages/conversations/{id}/pin")
    suspend fun unpinConversationMessage(@Path("id") conversationId: String): SuccessResponse

    @POST("api/v1/messages/conversations/{id}/block")
    suspend fun blockConversationParticipant(@Path("id") conversationId: String): SuccessResponse

    @DELETE("api/v1/messages/conversations/{id}/block")
    suspend fun unblockConversationParticipant(@Path("id") conversationId: String): SuccessResponse

    @GET("api/v1/messages/conversations/{id}/quiet")
    suspend fun getConversationQuiet(@Path("id") conversationId: String): ConversationQuietResponse

    @POST("api/v1/messages/conversations/{id}/quiet")
    suspend fun setConversationQuiet(@Path("id") conversationId: String, @Body request: SetConversationQuietRequest): ConversationQuietResponse

    @POST("api/v1/chat/reports")
    suspend fun reportChatMessage(@Body request: CreateChatReportRequest): SuccessResponse

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

    @DELETE("api/v1/messages/groups/{id}/messages/{messageId}")
    suspend fun deleteGroupMessage(@Path("id") groupId: String, @Path("messageId") messageId: String): SuccessResponse

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

    // Real photo upload (2026-07-24) -- see rw.itunda.marketplace.web.UploadController.
    @Multipart
    @POST("api/v1/uploads")
    suspend fun uploadPhoto(@Part file: okhttp3.MultipartBody.Part): UploadResponse

    // Real 당근마켓-style marketplace (2026-07-18) -- see rw.itunda.marketplace.web.MarketplaceController.
    @POST("api/v1/marketplace/listings")
    suspend fun createListing(@Body request: CreateListingRequest): ListingResponse

    @GET("api/v1/marketplace/listings")
    suspend fun browseListings(@Query("category") category: String? = null): ListingsResponse

    @GET("api/v1/marketplace/listings/nearby")
    suspend fun getNearbyListings(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radiusKm") radiusKm: Double = 3.0,
    ): ListingsResponse

    @GET("api/v1/marketplace/my-listings")
    suspend fun getMyListings(): ListingsResponse

    // Real "My purchases" (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 4
    // recommendation #6. See backend ListingRepository's own doc comment.
    @GET("api/v1/marketplace/my-purchases")
    suspend fun getMyPurchases(): ListingsResponse

    // Real hyperlocal "my neighborhood" browse (2026-07-20) -- see AuthApi.setNeighborhood.
    // Real 400 NEIGHBORHOOD_NOT_SET if the caller hasn't set one yet.
    @GET("api/v1/marketplace/listings/my-neighborhood")
    suspend fun getListingsMyNeighborhood(@Query("category") category: String? = null): ListingsResponse

    @POST("api/v1/marketplace/listings/{id}/mark-sold")
    suspend fun markListingSold(@Path("id") listingId: String, @Body request: MarkSoldRequest = MarkSoldRequest()): ListingResponse

    // Real seller-paid sponsored placement (2026-07-25) -- see
    // rw.itunda.marketplace.web.MarketplaceController.boostListing.
    @GET("api/v1/marketplace/boost-tiers")
    suspend fun getBoostTiers(): BoostTiersResponse

    @POST("api/v1/marketplace/listings/{id}/boost")
    suspend fun boostListing(@Path("id") listingId: String, @Header("Idempotency-Key") idempotencyKey: String, @Body request: BoostListingRequest): ListingResponse

    // Real "pay via itunda" Marketplace escrow (2026-07-25) -- see
    // rw.itunda.marketplace.web.MarketplaceController.
    @POST("api/v1/marketplace/listings/{id}/pay-escrow")
    suspend fun payEscrow(@Path("id") listingId: String, @Header("Idempotency-Key") idempotencyKey: String): MarketplaceEscrowResponse

    @POST("api/v1/marketplace/listings/{id}/confirm-receipt")
    suspend fun confirmEscrowReceipt(@Path("id") listingId: String, @Header("Idempotency-Key") idempotencyKey: String): MarketplaceEscrowResponse

    @POST("api/v1/marketplace/listings/{id}/dispute-escrow")
    suspend fun disputeEscrow(@Path("id") listingId: String, @Body request: DisputeEscrowRequest): MarketplaceEscrowResponse

    @GET("api/v1/marketplace/listings/{id}/escrow")
    suspend fun getEscrow(@Path("id") listingId: String): MarketplaceEscrowResponse

    // Real post-transaction review with asymmetric public/private visibility
    // (2026-07-24) -- see backend HoodReviewService's own doc comment.
    @POST("api/v1/marketplace/listings/{id}/review")
    suspend fun submitListingReview(@Path("id") listingId: String, @Body request: SubmitHoodReviewRequest): HoodReviewResponse

    @GET("api/v1/marketplace/listings/{id}/review")
    suspend fun getListingReviews(@Path("id") listingId: String): HoodReviewsResponse

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

    // Real Marketplace listing wishlist (2026-07-21 backend + bank-mfe) -- ported here,
    // closing the "Android/iOS don't have this yet" gap that row's own doc comment
    // named. Mirrors addFavoriteRestaurant/removeFavoriteRestaurant exactly.
    @POST("api/v1/marketplace/listings/{id}/favorite")
    suspend fun addListingFavorite(@Path("id") listingId: String): AddFavoriteResponse

    @DELETE("api/v1/marketplace/listings/{id}/favorite")
    suspend fun removeListingFavorite(@Path("id") listingId: String): RemoveFavoriteResponse

    @GET("api/v1/marketplace/listings/favorites")
    suspend fun getMyFavoriteListings(): FavoriteListingsResponse

    // Real 당근마켓-style Keyword Alert (rw.itunda.marketplace.KeywordAlertService, real
    // since before this session) -- first Android client for this feature (item 115,
    // found via a content-grep sweep confirming zero client anywhere; bank-mfe ported
    // it the same day as item 114). Real, published Karrot 30-keyword-per-user cap
    // enforced server-side.
    @POST("api/v1/marketplace/keyword-alerts")
    suspend fun addKeywordAlert(@Body request: AddKeywordAlertRequest): KeywordAlertResponse

    @GET("api/v1/marketplace/keyword-alerts")
    suspend fun getKeywordAlerts(): KeywordAlertsResponse

    @DELETE("api/v1/marketplace/keyword-alerts/{id}")
    suspend fun removeKeywordAlert(@Path("id") alertId: String): SuccessResponse

    @POST("api/v1/marketplace/keyword-alerts/quiet-hours")
    suspend fun setKeywordAlertQuietHours(@Body request: SetKeywordAlertQuietHoursRequest): KeywordAlertQuietHoursResponse

    @GET("api/v1/marketplace/keyword-alerts/quiet-hours")
    suspend fun getKeywordAlertQuietHours(): KeywordAlertQuietHoursResponse

    @POST("api/v1/hood/reports")
    suspend fun reportHoodContent(@Body request: CreateHoodReportRequest): HoodReportResponse

    // Real KakaoTalk-style gift send/claim (2026-07-20) -- see GiftService.
    @POST("api/v1/gifts/conversations/{id}")
    suspend fun sendGiftInConversation(
        @Path("id") conversationId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: SendGiftInConversationRequest,
    ): GiftResponse

    @POST("api/v1/gifts/{id}/claim")
    suspend fun claimGift(@Path("id") giftId: String, @Header("Idempotency-Key") idempotencyKey: String): GiftResponse

    @GET("api/v1/gifts/conversations/{id}")
    suspend fun getGiftsForConversation(@Path("id") conversationId: String): GiftsResponse

    // Real 동네생활-style community board (2026-07-19) -- see rw.itunda.community.web.CommunityController.
    @GET("api/v1/community/categories")
    suspend fun getCommunityCategories(): CommunityCategoriesResponse

    @POST("api/v1/community/posts")
    suspend fun createCommunityPost(@Body request: CreateCommunityPostRequest): CommunityPostResponse

    // Real 당근모임-style "upcoming meetups" browse (2026-07-25) -- see
    // rw.itunda.community.web.CommunityController.upcomingMeetups.
    @GET("api/v1/community/meetups/upcoming")
    suspend fun getUpcomingMeetups(): CommunityPostsResponse

    @GET("api/v1/community/posts")
    suspend fun browseCommunityPosts(@Query("category") category: String? = null): CommunityPostsResponse

    @GET("api/v1/community/posts/nearby")
    suspend fun getNearbyCommunityPosts(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radiusKm") radiusKm: Double = 3.0,
    ): CommunityPostsResponse

    @GET("api/v1/community/my-posts")
    suspend fun getMyCommunityPosts(): CommunityPostsResponse

    // Real hyperlocal "my neighborhood" browse (2026-07-20) -- see AuthApi.setNeighborhood.
    @GET("api/v1/community/posts/my-neighborhood")
    suspend fun getCommunityPostsMyNeighborhood(@Query("category") category: String? = null): CommunityPostsResponse

    @GET("api/v1/community/posts/{id}")
    suspend fun getCommunityPost(@Path("id") postId: String): CommunityPostDetailResponse

    @DELETE("api/v1/community/posts/{id}")
    suspend fun removeCommunityPost(@Path("id") postId: String): CommunityPostResponse

    @GET("api/v1/community/posts/{id}/comments")
    suspend fun getCommunityComments(@Path("id") postId: String): CommunityCommentsResponse

    @POST("api/v1/community/posts/{id}/comments")
    suspend fun addCommunityComment(@Path("id") postId: String, @Body request: AddCommunityCommentRequest): CommunityCommentResponse

    @POST("api/v1/community/posts/{id}/like")
    suspend fun toggleCommunityLike(@Path("id") postId: String): ToggleCommunityLikeResponse

    // Real 같이해요 (join-together) explicit 참여하기 tap (2026-07-24) -- see backend
    // CommunityService.joinMeetup's own doc comment.
    @POST("api/v1/community/posts/{id}/join")
    suspend fun joinCommunityMeetup(@Path("id") postId: String): JoinMeetupResponse

    // Real 당근알바-style local job board (2026-07-19) -- see rw.itunda.jobs.web.JobPostController.
    @GET("api/v1/jobs/categories")
    suspend fun getJobCategories(): JobCategoriesResponse

    @POST("api/v1/jobs/posts")
    suspend fun createJobPost(@Body request: CreateJobPostRequest): JobPostResponse

    @GET("api/v1/jobs/posts")
    suspend fun browseJobPosts(@Query("category") category: String? = null): JobPostsResponse

    @GET("api/v1/jobs/posts/nearby")
    suspend fun getNearbyJobPosts(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radiusKm") radiusKm: Double = 3.0,
    ): JobPostsResponse

    @GET("api/v1/jobs/my-posts")
    suspend fun getMyJobPosts(): JobPostsResponse

    // Real "Jobs I did" (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 4
    // recommendation #6. See backend JobPostRepository's own doc comment.
    @GET("api/v1/jobs/my-worked-posts")
    suspend fun getMyWorkedJobPosts(): JobPostsResponse

    @POST("api/v1/jobs/posts/{id}/favorite")
    suspend fun addJobPostFavorite(@Path("id") jobPostId: String): SuccessResponse

    @DELETE("api/v1/jobs/posts/{id}/favorite")
    suspend fun removeJobPostFavorite(@Path("id") jobPostId: String): SuccessResponse

    @GET("api/v1/jobs/posts/favorites")
    suspend fun getMyFavoriteJobPosts(): FavoriteJobPostsResponse

    // Real hyperlocal "my neighborhood" browse (2026-07-20) -- see AuthApi.setNeighborhood.
    @GET("api/v1/jobs/posts/my-neighborhood")
    suspend fun getJobPostsMyNeighborhood(@Query("category") category: String? = null): JobPostsResponse

    @POST("api/v1/jobs/posts/{id}/mark-filled")
    suspend fun markJobPostFilled(@Path("id") jobPostId: String, @Body request: MarkFilledRequest = MarkFilledRequest()): JobPostResponse

    // Real post-transaction review with asymmetric public/private visibility
    // (2026-07-24) -- see backend HoodReviewService's own doc comment.
    @POST("api/v1/jobs/posts/{id}/review")
    suspend fun submitJobPostReview(@Path("id") jobPostId: String, @Body request: SubmitHoodReviewRequest): HoodReviewResponse

    @GET("api/v1/jobs/posts/{id}/review")
    suspend fun getJobPostReviews(@Path("id") jobPostId: String): HoodReviewsResponse

    @DELETE("api/v1/jobs/posts/{id}")
    suspend fun removeJobPost(@Path("id") jobPostId: String): JobPostResponse

    @POST("api/v1/jobs/posts/{id}/contact-poster")
    suspend fun contactPoster(@Path("id") jobPostId: String): ContactPosterResponse

    // Real 당근알바-style structured application (2026-07-25) -- see backend
    // JobApplicationService's own doc comment.
    @POST("api/v1/jobs/posts/{id}/apply")
    suspend fun applyToJob(@Path("id") jobPostId: String, @Body request: ApplyToJobRequest): JobApplicationResponse

    @GET("api/v1/jobs/posts/{id}/applications")
    suspend fun getApplicationsForJobPost(@Path("id") jobPostId: String): JobApplicationsResponse

    @GET("api/v1/jobs/my-applications")
    suspend fun getMyJobApplications(): JobApplicationsResponse

    @POST("api/v1/jobs/applications/{id}/respond")
    suspend fun respondToJobApplication(@Path("id") applicationId: String, @Body request: RespondToApplicationRequest): JobApplicationResponse

    // Real 당근부동산-style property listing (2026-07-19) -- see rw.itunda.realestate.web.PropertyListingController.
    @GET("api/v1/realestate/property-types")
    suspend fun getPropertyTypes(): PropertyTypesResponse

    @POST("api/v1/realestate/listings")
    suspend fun createPropertyListing(@Body request: CreatePropertyListingRequest): PropertyListingResponse

    @GET("api/v1/realestate/listings")
    suspend fun browsePropertyListings(
        @Query("listingType") listingType: String? = null,
        @Query("propertyType") propertyType: String? = null,
    ): PropertyListingsResponse

    @GET("api/v1/realestate/listings/nearby")
    suspend fun getNearbyPropertyListings(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radiusKm") radiusKm: Double = 3.0,
    ): PropertyListingsResponse

    @GET("api/v1/realestate/my-listings")
    suspend fun getMyPropertyListings(): PropertyListingsResponse

    // Real "Places I got" (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 4
    // recommendation #6. See backend PropertyListingRepository's own doc comment.
    @GET("api/v1/realestate/my-acquired-listings")
    suspend fun getMyAcquiredPropertyListings(): PropertyListingsResponse
    @POST("api/v1/realestate/listings/{id}/favorite") suspend fun addPropertyListingFavorite(@Path("id") id: String): SuccessResponse
    @DELETE("api/v1/realestate/listings/{id}/favorite") suspend fun removePropertyListingFavorite(@Path("id") id: String): SuccessResponse
    @GET("api/v1/realestate/listings/favorites") suspend fun getMyFavoritePropertyListings(): FavoritePropertyListingsResponse

    // Real hyperlocal "my neighborhood" browse (2026-07-20) -- see AuthApi.setNeighborhood.
    // Deliberately not combined with listingType/propertyType filters -- an honest v1
    // scoping choice, same as the real backend endpoint this calls.
    @GET("api/v1/realestate/listings/my-neighborhood")
    suspend fun getPropertyListingsMyNeighborhood(): PropertyListingsResponse

    @POST("api/v1/realestate/listings/{id}/mark-taken")
    suspend fun markPropertyListingTaken(@Path("id") propertyListingId: String, @Body request: MarkTakenRequest = MarkTakenRequest()): PropertyListingResponse

    // Real ownership verification (2026-07-25) -- see backend PropertyOwnershipService's
    // own doc comment. documentUrl comes from uploadPhoto() above.
    @POST("api/v1/realestate/listings/{id}/verify-ownership")
    suspend fun submitPropertyOwnershipVerification(
        @Path("id") propertyListingId: String,
        @Body request: SubmitOwnershipVerificationRequest,
    ): PropertyOwnershipSubmissionResponse

    // Real post-transaction review with asymmetric public/private visibility
    // (2026-07-24) -- see backend HoodReviewService's own doc comment.
    @POST("api/v1/realestate/listings/{id}/review")
    suspend fun submitPropertyListingReview(@Path("id") propertyListingId: String, @Body request: SubmitHoodReviewRequest): HoodReviewResponse

    @GET("api/v1/realestate/listings/{id}/review")
    suspend fun getPropertyListingReviews(@Path("id") propertyListingId: String): HoodReviewsResponse

    @DELETE("api/v1/realestate/listings/{id}")
    suspend fun removePropertyListing(@Path("id") propertyListingId: String): PropertyListingResponse

    @POST("api/v1/realestate/listings/{id}/contact-lister")
    suspend fun contactLister(@Path("id") propertyListingId: String): ContactListerResponse

    // Real 당근-style price-offer negotiation (2026-07-19) -- see PropertyPriceOfferService.
    @POST("api/v1/realestate/listings/{id}/offers")
    suspend fun makePropertyOffer(@Path("id") propertyListingId: String, @Body request: MakePropertyOfferRequest): PropertyPriceOfferResponse

    @POST("api/v1/realestate/offers/{id}/respond")
    suspend fun respondToPropertyOffer(@Path("id") offerId: String, @Body request: RespondToPropertyOfferRequest): PropertyPriceOfferResponse

    @GET("api/v1/realestate/conversations/{id}/offers")
    suspend fun getPropertyOffersForConversation(@Path("id") conversationId: String): PropertyPriceOffersResponse

    // Real per-merchant public product browse (2026-07-18) -- see
    // rw.itunda.merchant.web.ShoppingController.getMerchantProducts.
    @GET("api/v1/shopping/merchants")
    suspend fun getShoppingMerchants(
        @Query("category") category: String? = null,
        @Query("q") q: String? = null,
        // Real browse-card enrichment (2026-07-21) -- see ShoppingMerchantDto's own doc
        // comment. Omitted (null) means no real distanceKm/deliveryTimeMinutes back.
        @Query("buyerLat") buyerLat: Double? = null,
        @Query("buyerLng") buyerLng: Double? = null,
    ): ShoppingMerchantsResponse

    // Real "search this map" + "directions" (2026-07-19) -- see MapsService.
    @GET("api/v1/maps/search")
    suspend fun searchPlaces(@Query("q") q: String): MapsSearchResponse

    // Backs the ruler (distance-measurement) tool's "what's here" label -- ported from
    // bank-mfe's own real reverseGeocode call, 2026-07-23.
    @GET("api/v1/maps/reverse")
    suspend fun reverseGeocode(@Query("lat") lat: Double, @Query("lng") lng: Double): MapsReverseGeocodeResponse

    // mode added 2026-07-22 (default "DRIVING") -- see OsrmRoutingClient.route's own doc
    // comment on the backend for the real, separately-deployed foot-profile OSRM
    // instance this now lets a caller reach.
    @GET("api/v1/maps/directions")
    suspend fun getDirections(
        @Query("fromLat") fromLat: Double,
        @Query("fromLng") fromLng: Double,
        @Query("toLat") toLat: Double,
        @Query("toLng") toLng: Double,
        @Query("mode") mode: String = "DRIVING",
    ): MapsDirectionsResponse

    // Real alternative routes (2026-07-22) -- see MapsDirectionsAlternativesResponse's
    // own doc comment.
    @GET("api/v1/maps/directions/alternatives")
    suspend fun getDirectionsAlternatives(
        @Query("fromLat") fromLat: Double,
        @Query("fromLng") fromLng: Double,
        @Query("toLat") toLat: Double,
        @Query("toLng") toLng: Double,
        @Query("mode") mode: String = "DRIVING",
    ): MapsDirectionsAlternativesResponse

    @POST("api/v1/maps/directions/itinerary")
    suspend fun getItineraryDirections(
        @Body request: ItineraryDirectionsRequest,
    ): MapsItineraryResponse

    // Real "nearby places" category search + bookmarked/favorite places (2026-07-19) --
    // see rw.itunda.maps.MapsService's own doc comment on the backend.
    @GET("api/v1/maps/nearby")
    suspend fun searchNearbyPlaces(
        @Query("category") category: String,
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radiusKm") radiusKm: Double = 2.0,
    ): MapNearbyResponse

    @GET("api/v1/agents/nearby")
    suspend fun searchNearbyAgents(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radiusKm") radiusKm: Double = 5.0,
    ): NearbyAgentsResponse

    @GET("api/v1/maps/bookmarks")
    suspend fun getMyMapBookmarks(): MapBookmarksResponse

    @POST("api/v1/maps/bookmarks")
    suspend fun addMapBookmark(@Body request: AddMapBookmarkRequest): AddMapBookmarkResponse

    // Real "move to folder" (2026-07-22) -- see MoveMapBookmarkRequest's own doc comment.
    @PATCH("api/v1/maps/bookmarks")
    suspend fun moveMapBookmark(@Query("lat") lat: Double, @Query("lng") lng: Double, @Body request: MoveMapBookmarkRequest): MoveMapBookmarkResponse

    @DELETE("api/v1/maps/bookmarks")
    suspend fun removeMapBookmark(@Query("lat") lat: Double, @Query("lng") lng: Double): RemoveMapBookmarkResponse

    // Real distinct category list -- see MerchantRepository.findDistinctCategories's own
    // doc comment on the backend.
    @GET("api/v1/shopping/merchants/categories")
    suspend fun getMerchantCategories(): MerchantCategoriesResponse

    // Real "Deals" rail (2026-07-25) -- see backend MerchantProductRepository.findDeals's
    // own doc comment.
    @GET("api/v1/shopping/products/deals")
    suspend fun getShopDeals(): DealsResponse

    @GET("api/v1/shopping/merchants/{id}/products")
    suspend fun getMerchantProducts(@Path("id") merchantId: String): MerchantProductsResponse

    // Real Naver Smart Store-style "알림받기" (follow a store) -- first Android client
    // for this feature (item 117, found via a content-grep sweep: bank-mfe has it,
    // Android/iOS didn't). Mirrors bank-mfe's lib/shopping.ts exactly.
    @POST("api/v1/merchant/{merchantId}/follow")
    suspend fun followMerchant(@Path("merchantId") merchantId: String): MerchantFollowResponse

    @DELETE("api/v1/merchant/{merchantId}/follow")
    suspend fun unfollowMerchant(@Path("merchantId") merchantId: String): SuccessResponse

    @GET("api/v1/merchant/follows")
    suspend fun getMyFollowedMerchants(@Query("size") size: Int = 200): FollowedMerchantsResponse

    // Real local-business appointment booking (2026-07-25) -- see
    // rw.itunda.merchant.web.MerchantBookingController.
    @GET("api/v1/merchant/{merchantId}/booking-slots")
    suspend fun getBookingSlots(@Path("merchantId") merchantId: String, @Query("serviceId") serviceId: String, @Query("date") date: String): BookingSlotsResponse

    @POST("api/v1/merchant/bookings")
    suspend fun createBooking(@Body request: CreateBookingRequest): MerchantBookingDetailResponse

    @GET("api/v1/merchant/bookings/my-bookings")
    suspend fun getMyBookings(): MerchantBookingsResponse

    @POST("api/v1/merchant/bookings/{id}/cancel")
    suspend fun cancelBooking(@Path("id") bookingId: String): MerchantBookingDetailResponse

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

    // Real post-delivery product reviews (2026-07-20) -- see OrderController.submitProductReview.
    @POST("api/v1/orders/items/{id}/review")
    suspend fun submitProductReview(@Path("id") orderItemId: String, @Body request: SubmitProductReviewRequest): ProductReviewResponse

    @GET("api/v1/orders/products/{id}/rating")
    suspend fun getProductRating(@Path("id") productId: String): ProductRatingResponse

    @GET("api/v1/orders/products/{id}/reviews")
    suspend fun getProductReviews(@Path("id") productId: String): ProductReviewsResponse

    // Real Shop product wishlist (2026-07-24) -- backend shipped 2026-07-20
    // (ProductFavoriteService), bank-mfe wired the same day; this closes the
    // Android-side gap. Mirrors addListingFavorite/addJobPostFavorite exactly.
    @POST("api/v1/orders/products/{id}/favorite")
    suspend fun addProductFavorite(@Path("id") productId: String): SuccessResponse

    @DELETE("api/v1/orders/products/{id}/favorite")
    suspend fun removeProductFavorite(@Path("id") productId: String): SuccessResponse

    @GET("api/v1/orders/products/favorites")
    suspend fun getMyFavoriteProducts(): FavoriteProductsResponse

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

    // Real 배민오더-style table/QR in-store ordering (2026-07-25) -- see
    // rw.itunda.eats.web.DineInOrderController.
    @POST("api/v1/eats/dine-in/orders")
    suspend fun placeDineInOrder(@Header("Idempotency-Key") idempotencyKey: String, @Body request: PlaceDineInOrderRequest): DineInOrderDetailResponse

    @GET("api/v1/eats/dine-in/orders/my-orders")
    suspend fun getMyDineInOrders(): DineInOrdersResponse

    @GET("api/v1/eats/dine-in/orders/{id}")
    suspend fun getDineInOrder(@Path("id") orderId: String): DineInOrderDetailResponse

    @POST("api/v1/eats/dine-in/orders/{id}/cancel")
    suspend fun cancelDineInOrder(@Path("id") orderId: String): DineInOrderDetailResponse

    // Real Toss Securities-style stock investing (rw.itunda.stocks) -- this is the
    // first mobile UI this feature has ever had (bank-mfe just got its own real UI the
    // same session, closing what had been a zero-client-UI gap even for the original
    // pre-existing buy/sell/portfolio backend). See StockCatalog.kt's own doc comment
    // on the backend for why day-over-day movement/history is a real deterministic
    // simulation, not fabricated randomness or live RSE data.
    @GET("api/v1/stocks")
    suspend fun getStocks(): StocksResponse

    @GET("api/v1/stocks/{id}/history")
    suspend fun getStockHistory(@Path("id") stockId: String, @Query("days") days: Int = 14): StockHistoryResponse

    @GET("api/v1/stocks/portfolio")
    suspend fun getStockPortfolio(): StockPortfolioResponse

    @GET("api/v1/stocks/portfolio/history")
    suspend fun getPortfolioHistory(@Query("days") days: Int = 30): PortfolioHistoryResponse

    @POST("api/v1/stocks/buy")
    suspend fun buyStock(@Header("Idempotency-Key") idempotencyKey: String, @Body request: TradeStockRequest): TradeStockResponse

    @POST("api/v1/stocks/sell")
    suspend fun sellStock(@Header("Idempotency-Key") idempotencyKey: String, @Body request: TradeStockRequest): TradeStockResponse

    @GET("api/v1/stocks/watchlist")
    suspend fun getStockWatchlist(): StocksResponse

    @POST("api/v1/stocks/{id}/watch")
    suspend fun watchStock(@Path("id") stockId: String): WatchStockResponse

    @DELETE("api/v1/stocks/{id}/watch")
    suspend fun unwatchStock(@Path("id") stockId: String): UnwatchStockResponse

    @GET("api/v1/overview")
    suspend fun getOverview(): OverviewResponse

    @POST("api/v1/accounts/link")
    suspend fun linkAccount(@Body request: LinkAccountRequest): LinkAccountResponse

    @GET("api/v1/accounts/linked")
    suspend fun getLinkedAccounts(): LinkedAccountsResponse

    @POST("api/v1/accounts/link/{accountId}/unlink")
    suspend fun unlinkAccount(@Path("accountId") accountId: String): LinkAccountResponse

    @GET("api/v1/loans/offers")
    suspend fun getLoanOffers(@Query("lenderId") lenderId: String? = null): LoanOffersResponse

    @GET("api/v1/loans/lenders")
    suspend fun getLenders(): LendersResponse

    @GET("api/v1/loans/my-loans")
    suspend fun getMyLoans(): MyLoansResponse

    @POST("api/v1/loans/apply")
    suspend fun applyForLoan(@Header("Idempotency-Key") idempotencyKey: String, @Body request: ApplyLoanRequest): ApplyLoanResponse

    @POST("api/v1/loans/repay")
    suspend fun repayLoan(@Header("Idempotency-Key") idempotencyKey: String, @Body request: RepayLoanRequest): RepayLoanResponse

    @POST("api/v1/support/tickets")
    suspend fun createSupportTicket(@Body request: CreateSupportTicketRequest): CreateSupportTicketResponse

    @GET("api/v1/support/tickets")
    suspend fun getSupportTickets(): SupportTicketsResponse

    @GET("api/v1/credit-score")
    suspend fun getCreditScore(): CreditScoreResponse

    @POST("api/v1/certificate/issue")
    suspend fun issueCertificate(): IssueCertificateResponse

    @GET("api/v1/certificate/me")
    suspend fun getMyCertificate(): MyCertificateResponse

    @POST("api/v1/certificate/revoke")
    suspend fun revokeCertificate(): RevokeCertificateResponse

    @POST("api/v1/identity/submit")
    suspend fun submitIdentity(@Body request: SubmitIdentityRequest): SubmitIdentityResponse

    @GET("api/v1/identity/status")
    suspend fun getIdentityStatus(): IdentityStatusResponse

    @POST("api/v1/split-bills/conversations/{groupConversationId}")
    suspend fun createSplitBill(
        @Path("groupConversationId") groupConversationId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: CreateSplitBillRequest,
    ): CreateSplitBillResponse

    @GET("api/v1/split-bills/conversations/{groupConversationId}")
    suspend fun getSplitBillsForGroup(@Path("groupConversationId") groupConversationId: String): SplitBillsForGroupResponse

    @GET("api/v1/split-bills/{id}")
    suspend fun getSplitBill(@Path("id") splitBillId: String): SplitBillResponse

    @POST("api/v1/split-bills/{id}/pay")
    suspend fun paySplitBillShare(@Path("id") splitBillId: String, @Header("Idempotency-Key") idempotencyKey: String): PaySplitBillShareResponse

    @GET("api/v1/contacts")
    suspend fun getContacts(): ContactsResponse

    @POST("api/v1/contacts")
    suspend fun addContact(@Body request: AddContactRequest): AddContactResponse

    // Real 26-week savings plan (2026-07-21) -- see WeeklySavingsPlanDto's own doc
    // comment. No Idempotency-Key header on any of these -- WeeklySavingsController
    // genuinely doesn't declare that header for this feature, unlike deposit/
    // claimInterest/sendDirect above.
    @GET("api/v1/weekly-savings/plans")
    suspend fun getWeeklySavingsPlans(): WeeklySavingsPlansResponse

    @GET("api/v1/weekly-savings/plans/{id}")
    suspend fun getWeeklySavingsPlan(@Path("id") id: String): WeeklySavingsPlanDetailResponse

    @POST("api/v1/weekly-savings/plans")
    suspend fun createWeeklySavingsPlan(@Body request: CreateWeeklySavingsPlanRequest): CreateWeeklySavingsPlanResponse

    @POST("api/v1/weekly-savings/plans/{id}/cancel")
    suspend fun cancelWeeklySavingsPlan(@Path("id") id: String): WeeklySavingsActionResponse

    @POST("api/v1/weekly-savings/plans/{id}/withdraw")
    suspend fun withdrawWeeklySavingsPlan(@Path("id") id: String): WeeklySavingsActionResponse

    // Real Toss Bank 먼저 이자받는 정기예금 (interest-paid-upfront term deposit)
    // equivalent (2026-07-25) -- see backend UpfrontInterestDeposit's own doc comment.
    @GET("api/v1/upfront-deposits")
    suspend fun getUpfrontDeposits(): UpfrontDepositsResponse

    @POST("api/v1/upfront-deposits")
    suspend fun openUpfrontDeposit(@Body request: OpenUpfrontDepositRequest): UpfrontDepositResponse

    @POST("api/v1/upfront-deposits/{id}/withdraw")
    suspend fun withdrawUpfrontDeposit(@Path("id") id: String): UpfrontDepositResponse

    // Real KakaoBank mini-style capped starter wallet (rw.itunda.wallet.
    // MiniWalletService, 2026-07-28) -- first mobile client for this feature (item 100),
    // mirroring bank-mfe's lib/miniWallet.ts equivalent added one item earlier.
    @POST("api/v1/wallet/mini/open")
    suspend fun openMiniWallet(): OpenMiniWalletResponse

    @POST("api/v1/wallet/mini/deposit")
    suspend fun depositMiniWallet(@Body request: DepositMiniWalletRequest): DepositMiniWalletResponse

    // Real Kakao T-style ride-hailing (rw.itunda.rideshare, real since 2026-07-26) --
    // first Android client for this feature (item 109, found via a fresh matrix scan:
    // bank-mfe has had it since the same day, Android/iOS never did). Mirrors
    // bank-mfe's lib/rideshare.ts exactly.
    @POST("api/v1/rides/drivers/register")
    suspend fun registerAsRideDriver(): RideDriverResponse

    @GET("api/v1/rides/drivers/me")
    suspend fun getMyRideDriverProfile(): RideDriverResponse

    @POST("api/v1/rides/drivers/availability")
    suspend fun setRideDriverAvailability(@Body request: SetRideDriverAvailabilityRequest): RideDriverResponse

    @POST("api/v1/rides/drivers/location")
    suspend fun updateRideDriverLocation(@Body request: UpdateRideDriverLocationRequest): RideDriverResponse

    @POST("api/v1/rides/trips")
    suspend fun requestRideTrip(@Body request: RequestRideTripRequest, @Header("Idempotency-Key") idempotencyKey: String): RideTripResponse

    @GET("api/v1/rides/trips/available")
    suspend fun getAvailableRideTrips(): RideTripsResponse

    @GET("api/v1/rides/trips/my-trips")
    suspend fun getMyRideTrips(): RideTripsResponse

    @GET("api/v1/rides/trips/my-driver-trips")
    suspend fun getMyRideDriverTrips(): RideTripsResponse

    @POST("api/v1/rides/trips/{tripId}/accept")
    suspend fun acceptRideTrip(@Path("tripId") tripId: String): RideTripResponse

    @POST("api/v1/rides/trips/{tripId}/decline")
    suspend fun declineRideTrip(@Path("tripId") tripId: String): RideTripResponse

    @POST("api/v1/rides/trips/{tripId}/start")
    suspend fun startRideTrip(@Path("tripId") tripId: String): RideTripResponse

    @POST("api/v1/rides/trips/{tripId}/complete")
    suspend fun completeRideTrip(@Path("tripId") tripId: String): RideTripResponse

    @POST("api/v1/rides/trips/{tripId}/cancel")
    suspend fun cancelRideTrip(@Path("tripId") tripId: String): RideTripResponse

    // Real Kakao Bank 모임통장 (group/shared account) equivalent -- first Android client
    // for this feature (item 104, found via a fresh matrix scan: zero client on either
    // mobile platform despite being real and live since well before this session).
    // Mirrors bank-mfe's lib/groupAccounts.ts exactly.
    @POST("api/v1/group-accounts")
    suspend fun createGroupAccount(@Body request: CreateGroupAccountRequest): CreateGroupAccountResponse

    @GET("api/v1/group-accounts")
    suspend fun getMyGroupAccounts(): GroupAccountsResponse

    @GET("api/v1/group-accounts/{id}")
    suspend fun getGroupAccount(@Path("id") id: String): GroupAccountDetailResponse

    @POST("api/v1/group-accounts/{id}/members")
    suspend fun inviteGroupAccountMember(@Path("id") id: String, @Body request: InviteMemberRequest): InviteMemberResponse

    @POST("api/v1/group-accounts/{id}/deposit")
    suspend fun depositToGroupAccount(
        @Path("id") id: String,
        @Body request: GroupAccountAmountRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): GroupAccountDetailResponse

    @POST("api/v1/group-accounts/{id}/withdraw")
    suspend fun withdrawFromGroupAccount(
        @Path("id") id: String,
        @Body request: GroupAccountAmountRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): GroupAccountDetailResponse

    @PUT("api/v1/group-accounts/{id}/dues")
    suspend fun setGroupAccountDuesAmount(@Path("id") id: String, @Body request: SetDuesAmountRequest): CreateGroupAccountResponse

    @GET("api/v1/group-accounts/{id}/dues")
    suspend fun getGroupAccountDues(@Path("id") id: String): GroupAccountDuesResponse

    @POST("api/v1/group-accounts/{id}/dues/remind")
    suspend fun requestUnpaidGroupAccountDues(@Path("id") id: String): RemindUnpaidDuesResponse
}

data class UpfrontDepositDto(
    val id: String, val userId: String, val walletId: String, val principal: Double,
    val interestRate: Double, val interestPaid: Double, val status: String,
    val openedAt: String, val maturesAt: String, val maturedAt: String? = null, val withdrawnAt: String? = null,
)
data class OpenUpfrontDepositRequest(val principal: java.math.BigDecimal)
data class UpfrontDepositResponse(val success: Boolean, val deposit: UpfrontDepositDto, val message: String? = null)
data class UpfrontDepositsResponse(val success: Boolean, val deposits: List<UpfrontDepositDto>)

data class TransactionHistoryResponse(val success: Boolean, val transactions: List<TransactionDto>)

// Real Kakao T-style ride-hailing -- mirrors RideDriver.kt/RideTrip.kt exactly.
data class RideDriverDto(
    val id: String, val userId: String, val walletId: String, val status: String, val available: Boolean,
    val currentLatitude: Double?, val currentLongitude: Double?, val locationUpdatedAt: String?,
)
data class RideDriverResponse(val success: Boolean, val driver: RideDriverDto)
data class SetRideDriverAvailabilityRequest(val available: Boolean)
data class UpdateRideDriverLocationRequest(val latitude: Double, val longitude: Double)
data class RideTripDto(
    val id: String, val passengerId: String, val driverId: String?, val pickupAddress: String,
    val pickupLatitude: Double, val pickupLongitude: Double, val dropoffAddress: String,
    val dropoffLatitude: Double, val dropoffLongitude: Double, val distanceKm: Double,
    val fare: java.math.BigDecimal, val platformFee: java.math.BigDecimal, val status: String, val createdAt: String,
)
data class RideTripResponse(val success: Boolean, val trip: RideTripDto)
data class RideTripsResponse(val success: Boolean, val trips: List<RideTripDto>)
data class RequestRideTripRequest(
    val pickupAddress: String, val pickupLatitude: Double, val pickupLongitude: Double,
    val dropoffAddress: String, val dropoffLatitude: Double, val dropoffLongitude: Double,
)

data class SpendingCategoryDto(val name: String, val amount: java.math.BigDecimal)
data class SpendingInsightResponse(val success: Boolean, val categories: List<SpendingCategoryDto>, val totalSpent: java.math.BigDecimal)

// Real Kakao Bank 모임통장 (group/shared account) equivalent -- mirrors
// GroupAccount.kt/GroupAccountService.kt exactly.
data class GroupAccountDto(val id: String, val name: String, val ownerId: String, val walletId: String, val monthlyDuesAmount: java.math.BigDecimal?, val createdAt: String)
data class GroupAccountMemberDto(val userId: String, val firstName: String, val lastName: String, val isOwner: Boolean, val joinedAt: String)
data class CreateGroupAccountRequest(val name: String)
data class CreateGroupAccountResponse(val success: Boolean, val groupAccount: GroupAccountDto)
data class GroupAccountsResponse(val success: Boolean, val groupAccounts: List<GroupAccountDto>)
data class GroupAccountDetailResponse(val success: Boolean, val groupAccount: GroupAccountDto, val balance: java.math.BigDecimal, val members: List<GroupAccountMemberDto>, val message: String? = null)
data class InviteMemberRequest(val phoneNumber: String)
data class InviteMemberResponse(val success: Boolean, val member: GroupAccountMemberDto)
data class GroupAccountAmountRequest(val amount: java.math.BigDecimal)
data class SetDuesAmountRequest(val amount: java.math.BigDecimal?)
data class GroupAccountDuesMemberDto(val userId: String, val firstName: String, val lastName: String, val contributedAmount: java.math.BigDecimal, val paid: Boolean)
data class GroupAccountDuesDto(val duesAmount: java.math.BigDecimal?, val cycleMonth: String, val members: List<GroupAccountDuesMemberDto>)
data class GroupAccountDuesResponse(val success: Boolean, val dues: GroupAccountDuesDto)
data class RemindUnpaidDuesResponse(val success: Boolean, val remindedCount: Int)

// Real KakaoBank mini-style capped starter wallet -- see MiniWalletService.kt's own
// doc comment (real balance/daily/monthly caps plus a real 7-18 age-eligibility gate).
data class OpenMiniWalletResponse(val success: Boolean, val wallet: Wallet)
data class DepositMiniWalletRequest(val amount: java.math.BigDecimal)
data class DepositMiniWalletResponse(val success: Boolean, val id: String, val amount: java.math.BigDecimal, val completedAt: String)

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

// Real device binding (2026-07-21) -- shared, public so any money-moving call site
// can check it, not just MainViewModel's original three (sendTransfer/
// depositToSavingsGoal/claimInterest). A 403 alone isn't enough (other real 403s
// exist elsewhere in this backend); the real `ApiError.code` field in the response
// body is what DeviceVerificationFilter actually sets, so that's what's checked.
// Mirrors bank-mfe's own `err.code === 'DEVICE_NOT_VERIFIED'` check on its ApiError
// exactly.
fun isDeviceNotVerifiedError(e: retrofit2.HttpException): Boolean {
    if (e.code() != 403) return false
    return try {
        val body = e.response()?.errorBody()?.string() ?: return false
        com.google.gson.JsonParser.parseString(body).asJsonObject.get("code")?.asString == "DEVICE_NOT_VERIFIED"
    } catch (_: Exception) {
        false
    }
}

// Same pattern as isDeviceNotVerifiedError above -- CertificateController's
// /certificate/issue real-403s with code KYC_REQUIRED when the caller's identity
// isn't verified yet (CertificateUserNotVerifiedException), so CreditScoreScreen/
// CertificateScreen can show a real, specific message instead of a generic failure.
fun isKycRequiredError(e: retrofit2.HttpException): Boolean {
    if (e.code() != 403) return false
    return try {
        val body = e.response()?.errorBody()?.string() ?: return false
        com.google.gson.JsonParser.parseString(body).asJsonObject.get("code")?.asString == "KYC_REQUIRED"
    } catch (_: Exception) {
        false
    }
}

// Generic form of isDeviceNotVerifiedError/isKycRequiredError above, for call sites
// (like the Mini wallet's birth-date/age gate, 2026-07-28) that need to distinguish
// between multiple real ApiError codes on the same HTTP status rather than just a
// single yes/no check.
fun apiErrorCode(e: retrofit2.HttpException): String? = try {
    val body = e.response()?.errorBody()?.string() ?: return null
    com.google.gson.JsonParser.parseString(body).asJsonObject.get("code")?.asString
} catch (_: Exception) {
    null
}

// Network Client Singleton
object NetworkClient {
    // Was hardcoded to "http://10.0.2.2:8080/" -- the emulator-only loopback alias, at
    // the wrong port (services/backend listens on 4001). Real base URL is now passed
    // in from :app's own real BuildConfig.API_BASE_URL via init() below (2026-07-22
    // change, made while relocating this whole file from :app to :core:network so
    // Feature modules can depend on it directly -- a Gradle library module can't read
    // an application module's BuildConfig, and passing the value in at runtime avoids
    // needing to relocate the buildConfigField/`-PapiBaseUrl=` override plumbing too).
    // Overridable at build time for a physical device -- see app/build.gradle.kts's
    // apiBaseUrl comment (2026-07-11 fix, still the actual place that's set).
    private var BASE_URL: String = "http://10.0.2.2:4001/"

    // Must be initialized once, from ItundaApplication.onCreate(), before any request
    // fires -- see that file. Held nullable rather than lateinit so a request made
    // before init() (which should never happen, but interceptors must never crash the
    // whole app over it) just goes out unauthenticated instead of throwing.
    private var tokenStore: TokenStore? = null

    // Real device binding (2026-07-21 port) -- same nullable-not-lateinit reasoning
    // as tokenStore above.
    private var deviceStore: DeviceStore? = null

    fun init(context: Context, baseUrl: String) {
        BASE_URL = baseUrl
        tokenStore = TokenStore(context.applicationContext)
        deviceStore = DeviceStore(context.applicationContext)
    }

    fun currentTokenStore(): TokenStore =
        tokenStore ?: throw IllegalStateException("NetworkClient.init() was never called")

    fun currentDeviceStore(): DeviceStore =
        deviceStore ?: throw IllegalStateException("NetworkClient.init() was never called")

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

    private val diagnosticLoggingInterceptor = Interceptor { chain ->
        val request = chain.request()
        android.util.Log.d("ITUNDA_NET", "--> ${request.method} ${request.url}")
        try {
            val response = chain.proceed(request)
            android.util.Log.d("ITUNDA_NET", "<-- ${response.code} ${request.url}")
            response
        } catch (e: Exception) {
            android.util.Log.e("ITUNDA_NET", "<-- FAILED ${request.url}: ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(diagnosticLoggingInterceptor)
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
