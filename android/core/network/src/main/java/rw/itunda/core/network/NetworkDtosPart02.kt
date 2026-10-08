data class MiniAppCatalogResponse(val success: Boolean, val miniApps: List<PartnerMiniAppDto>, val page: Int, val totalPages: Int)

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
    // Real recoverable archive (2026-08-05) -- see backend ConversationPreference
    // .archived's own doc comment. Same private-to-me model as quiet.
    val archived: Boolean = false,
    // Real KakaoTalk 채팅방 상단 고정 (pin chat room to top) (2026-08-18) -- see backend
    // MessagingController's own doc comment distinguishing this from the existing
    // per-message pin (pinnedMessageId above). Already returned by GET /conversations
    // for every summary since MessagingController shipped it; bank-mfe wired a client
    // for it (Section 165) but Android never read this field back. Same
    // defined-but-uncalled shape as archived above.
    val pinnedToTop: Boolean = false,
    // Real KakaoTalk favorite chat toggle (itunda Talk redesign, 2026-08-28) -- see
    // TalkApi.kt's own doc comment.
    val favorite: Boolean = false,
)

data class StartConversationRequest(val phoneNumber: String? = null, val otherUserId: String? = null)
// Real photo message (2026-08-04) -- closes docs/DESIGN_REFERENCES.md's Talk
// recommendation #6 (no photo/file send at all). The backend (MessagingService
// .sendMessage/GroupMessagingService.sendMessage) already validated a real
// /api/v1/uploads/-prefixed imageUrl before this field existed on either Android
// request DTO -- a real "defined but uncalled" gap, same class this file's own history
// already names for group emoticons/group pin. Reuses the exact real upload flow
// MarketplaceScreen/PropertyScreen already established (GetContent() picker ->
// uploadPhoto() -> real server URL), not a new upload pipeline.
data class SendMessageRequest(val body: String, val replyToMessageId: String? = null, val imageUrl: String? = null)
data class TalkContactDto(val userId: String, val name: String)
data class TalkContactsResponse(val success: Boolean, val contacts: List<TalkContactDto>)
data class ConversationQuietResponse(val success: Boolean, val quiet: Boolean)
// Real recoverable archive (2026-08-05) -- see backend ConversationPreference
// .archived's own doc comment.
data class ConversationArchivedResponse(val success: Boolean, val archived: Boolean)
data class SetConversationArchivedRequest(val archived: Boolean)
// Real KakaoTalk 채팅방 상단 고정 (pin chat room to top) (2026-08-18) -- see
// ConversationSummaryDto.pinnedToTop's own doc comment. Named ConversationPinnedToTop
// (not ConversationPinned) to stay distinct from the existing per-message
// pinConversationMessage/unpinConversationMessage pair below, same naming discipline
// bank-mfe's lib/messaging.ts already established for this endpoint.
data class ConversationPinnedToTopResponse(val success: Boolean, val pinned: Boolean)
data class SetConversationPinnedToTopRequest(val pinned: Boolean)
data class CreateChatReportRequest(val messageId: String, val reason: String)
data class SetConversationQuietRequest(val quiet: Boolean)
data class ToggleReactionRequest(val emoji: String)

data class ConversationResponse(val success: Boolean, val conversation: ConversationDto)
data class ConversationsResponse(val success: Boolean, val conversations: List<ConversationSummaryDto>, val page: Int = 0, val totalPages: Int = 1)
data class MessagesResponse(val success: Boolean, val messages: List<MessageDto>)
data class MessageResponse(val success: Boolean, val message: MessageDto)
// Real message forwarding (2026-08-04) -- see MessagingController.forwardMessage's own
// doc comment on the backend. destinationType is "DIRECT" (a conversationId) or "GROUP"
// (a groupId).
data class ForwardMessageRequest(val destinationType: String, val destinationId: String)
data class PinnedMessageResponse(val success: Boolean, val message: MessageDto?)
data class ReactionsResponse(val success: Boolean, val reactions: List<ReactionGroupDto>)

// Real group chat (2026-07-18) -- see rw.itunda.messaging.web.GroupMessagingController.
// memberPhoneNumbers is the real human-friendly entry point (same reasoning as
// StartConversationRequest.phoneNumber).
data class CreateGroupRequest(val name: String, val memberUserIds: List<String> = emptyList(), val memberPhoneNumbers: List<String> = emptyList())
data class SendGroupMessageRequest(val body: String, val replyToMessageId: String? = null, val imageUrl: String? = null)
data class AddGroupMemberRequest(val userId: String)

data class GroupSummaryDto(
    val groupId: String,
    val name: String,
    val memberCount: Int,
    val lastMessageAt: String,
    val lastMessagePreview: String?,
    val unreadCount: Long,
    val quiet: Boolean = false,
    // Real group photo/description (2026-07-28) -- see GroupMessagingService
    // .setGroupPhotoUrl/setGroupDescription's own doc comments. Found 2026-08-01 via a
    // defined-but-uncalled-endpoint sweep: real on backend since it shipped, zero
    // client anywhere on any of the 3 platforms until now.
    val photoUrl: String? = null,
    val description: String? = null,
)
data class SetGroupPhotoUrlRequest(val photoUrl: String)
data class SetGroupDescriptionRequest(val description: String)
data class GroupSummaryResponse(val success: Boolean, val group: GroupSummaryDto)
data class GroupResponse(val success: Boolean, val group: GroupSummaryDto)
data class GroupsResponse(val success: Boolean, val groups: List<GroupSummaryDto>, val page: Int = 0, val totalPages: Int = 1)

// Real open-group DTOs (item 244) -- distinct shape from GroupSummaryDto (a real
// joinCode, but no memberCount/lastMessage/etc. yet since the group was just
// created).
data class CreateOpenGroupRequest(val name: String)
data class OpenGroupDto(val id: String, val name: String, val joinCode: String)
data class OpenGroupResponse(val success: Boolean, val group: OpenGroupDto)
data class JoinGroupByCodeRequest(val joinCode: String)
data class JoinedGroupDto(val id: String, val name: String)
data class JoinGroupResponse(val success: Boolean, val group: JoinedGroupDto)
// Real group-chat pin (2026-08-04) -- see ApiService's own getPinnedGroupMessage doc
// comment. Mirrors PinnedMessageResponse's own 1:1 shape.
data class GroupPinnedMessageResponse(val success: Boolean, val message: GroupMessageDto?)
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
data class MarkSoldRequest(val buyerPhoneNumber: String? = null)
data class BoostListingRequest(val days: Int)
data class UpdateListingPriceRequest(val price: Double)
data class BoostTiersResponse(val success: Boolean, val tiers: Map<String, Double>)

// Real "pay via itunda" Marketplace escrow (2026-07-25) -- see backend
// MarketplaceEscrow.kt's own doc comment. An opt-in safer alternative to the existing
// in-person cash handoff, never replacing it.
data class MarketplaceEscrowDto(
    val id: String, val listingId: String, val buyerId: String, val sellerId: String,
    val amount: Double, val fee: Double, val status: String,
    val holdTransactionId: String, val resolutionTransactionId: String? = null,
    val disputeReason: String? = null,
    // Real gap closed 2026-08-15 -- see the backend MarketplaceEscrow.deliveryAddress's
    // own doc comment (당근마켓 바로구매-style shipped-item support, escrow previously
    // only ever assumed an in-person handoff). Null for the original in-person case.
    val deliveryAddress: String? = null,
    val createdAt: String, val updatedAt: String,
)
data class MarketplaceEscrowResponse(val success: Boolean, val escrow: MarketplaceEscrowDto)
data class DisputeEscrowRequest(val reason: String)
data class PayEscrowRequest(val deliveryAddress: String? = null)
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

data class UploadResponse(val success: Boolean, val url: String)
data class ListingResponse(val success: Boolean, val listing: ListingDto)
// trustScores added 2026-07-24 -- backend has spread this alongside every
// listing/job-post/property-listing browse response since 2026-07-21
// (rw.itunda.core.web.TrustScoreSupport), but no client ever parsed or rendered it.
// A sellerId/posterId/listerId -> User.trustScore map (Karrot-Score-style, 0-1000,
// starting at 30 -- see backend User.kt's own doc comment for why not a literal
// manner-temperature metaphor).
// likedByMe added 2026-08-03 -- see MarketplaceController.kt's own doc comment: real
// on myNeighborhood/getMyListings/getMyPurchases (all authenticated), absent on
// browse/nearby (deliberately unauthenticated, guest-browsable) -- an honest gap, not
// a client bug; the heart's count is always real either way, just starts unfilled on
// those two endpoints until a real tap.
data class ListingsResponse(val success: Boolean, val listings: List<ListingDto>, val trustScores: Map<String, Int> = emptyMap(), val likedByMe: Set<String> = emptySet(), val page: Int = 0, val totalPages: Int = 1, val totalElements: Int = 0)
data class ToggleLikeResponse(val success: Boolean, val liked: Boolean)

// Real Marketplace listing wishlist (2026-07-21 backend + bank-mfe, ported here) --
// mirrors FavoriteRestaurantDto's exact shape; see ListingFavoriteService.kt's own doc
// comment on the backend for why add/remove are both idempotent.
data class FavoriteListingDto(val listingId: String, val title: String, val price: Double, val category: String, val favoritedAt: String)
// Real pagination-discard fix (2026-09-13, same systemic gap fixed on web -- see
// project_itunda_pagination_discard_sweep memory) -- the real backend
// Pageable/pageMeta endpoint was always there; page just wasn't ever sent,
// silently capping this list (and any count badge reading it) at the most
// recent 20 favorited listings.
data class FavoriteListingsResponse(val success: Boolean, val favorites: List<FavoriteListingDto>, val page: Int = 0, val totalPages: Int = 1, val totalElements: Int = 0)

// Real "Hidden listings" list (Hood product-completeness pass, 2026-09-07) --
// hideListing/unhideListing below existed with no way to ever see or undo what was
// hidden. Mirrors FavoriteListingDto's exact shape.
data class HiddenListingDto(val listingId: String, val title: String, val price: Double, val category: String, val hiddenAt: String)
data class HiddenListingsResponse(val success: Boolean, val hidden: List<HiddenListingDto>)

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
// comment. Money leaves the sender's account into a real escrow account the moment a
// gift is sent, and only reaches the recipient's account once they explicitly claim it
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
    val theme: String?,
    val status: String,
    val holdTransactionId: String,
    val claimTransactionId: String?,
    val expiresAt: String,
    val claimedAt: String?,
    val createdAt: String,
)
data class SendGiftInConversationRequest(val amount: Double, val note: String? = null, val theme: String? = null)
data class SendGiftRequest(val recipientPhoneNumber: String, val amount: Double, val note: String? = null, val theme: String? = null)

// Real KakaoPay 송금봉투 (money envelope) themed presets (backend since 2026-07-26,
// GiftTheme's own doc comment) -- exactly these 4 real, sourced presets, optional and
// additive alongside the free-text note. Had zero client anywhere until now.
val GIFT_THEME_LABELS: Map<String, String> = mapOf(
    "CONGRATULATIONS" to "🎉 Congratulations",
    "HEARTFELT" to "💌 From the heart",
    "GOOD_LUCK" to "🍀 Good luck",
    "SETTLE_UP" to "🧾 Settling up",
)
data class GiftResponse(val success: Boolean, val gift: GiftDto)
data class GiftsResponse(val success: Boolean, val gifts: List<GiftDto>)

// Real KakaoTalk Emoticon Store (item 135) -- mirrors bank-mfe's lib/emoticons.ts
// exactly (item 133).
data class EmoticonPackDto(val id: String, val title: String, val artistName: String, val thumbnailUrl: String, val price: Double, val active: Boolean, val createdAt: String)
data class EmoticonDto(val id: String, val packId: String, val imageUrl: String, val sortOrder: Int)
data class OwnedEmoticonPackDto(val id: String, val userId: String, val packId: String, val source: String, val acquiredAt: String)
data class EmoticonPacksResponse(val success: Boolean, val packs: List<EmoticonPackDto>)
data class EmoticonsResponse(val success: Boolean, val emoticons: List<EmoticonDto>)
data class OwnedEmoticonPacksResponse(val success: Boolean, val packs: List<OwnedEmoticonPackDto>)
data class OwnedEmoticonPackResponse(val success: Boolean, val ownedPack: OwnedEmoticonPackDto)
// Real bug caught before compiling: EmoticonController.giftPack returns the key
// "giftedPack", not "ownedPack" -- a distinct response shape, not reusable.
data class GiftedEmoticonPackResponse(val success: Boolean, val giftedPack: OwnedEmoticonPackDto)
data class GiftEmoticonPackRequest(val recipientPhoneNumber: String)
data class SendEmoticonRequest(val emoticonId: String)

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
    // Real hyperlocal neighborhood -- same gap as JobPostDto/PropertyListingDto's
    // identical fix, see [[project_itunda_full_ecosystem_polish]]. The backend has
    // stamped this on every post since 2026-07-20 and serializes the entity directly.
    val neighborhood: String? = null,
    // Real 동네생활 topic chip (2026-08-28) -- see backend CommunityPost.topic's own doc
    // comment, a lifestyle axis independent of the functional `category` above.
    val topic: String? = null,
    // Real AI-generated 모임 summary (2026-08-28) -- see backend HoodAiSummaryService's
    // own doc comment. Null means either not a meetup post or the honesty gate declined
    // (thin body) -- never fabricate a summary client-side when this is null.
    val aiSummary: String? = null,
    val aiSummaryGeneratedAt: String? = null,
)
data class CreateCommunityPostRequest(
    val category: String, val title: String, val body: String,
    val latitude: Double? = null, val longitude: Double? = null,
    val eventDate: String? = null, val capacity: Int? = null, val topic: String? = null,
)
data class CommunityPostResponse(val success: Boolean, val post: CommunityPostDto)
// joinedCounts added 2026-07-24 -- postId -> real member count of that meetup's group
// chat, closing docs/DESIGN_REFERENCES.md Section 4 recommendation #4's "같이해요
// (join-together) posts get a dedicated pinned mid-feed slot."
data class CommunityPostsResponse(val success: Boolean, val posts: List<CommunityPostDto>, val joinedCounts: Map<String, Int> = emptyMap(), val page: Int = 0, val totalPages: Int = 1)
data class CommunityCategoriesResponse(val success: Boolean, val categories: List<CommunityCategoryDto>)
data class CommunityTopicsResponse(val success: Boolean, val topics: List<CommunityCategoryDto>)
data class CommentNotificationsEnabledResponse(val success: Boolean, val commentNotificationsEnabled: Boolean)
data class SetCommentNotificationsEnabledRequest(val enabled: Boolean)
data class CommunityPostDetailResponse(val success: Boolean, val post: CommunityPostDto, val authorName: String, val likedByMe: Boolean)
data class CommunityCommentDto(val id: String, val postId: String, val authorId: String, val body: String, val createdAt: String)
data class CommunityCommentWithAuthorDto(val comment: CommunityCommentDto, val authorName: String)
data class CommunityCommentsResponse(val success: Boolean, val comments: List<CommunityCommentWithAuthorDto>, val page: Int = 0, val totalPages: Int = 1)
data class AddCommunityCommentRequest(val body: String)
data class CommunityCommentResponse(val success: Boolean, val comment: CommunityCommentDto)
data class ToggleCommunityLikeResponse(val success: Boolean, val liked: Boolean)
// Real 같이해요 (join-together) explicit join (2026-07-24) -- see backend
// CommunityService.joinMeetup's own doc comment.
data class JoinMeetupResponse(val success: Boolean, val groupId: String)

// Real 당근모임 (Karrot Meetups) recurring schedule + attendance check-in -- mirrors
// bank-mfe's lib/community.ts exactly.
data class ScheduleMeetupSessionsRequest(val dates: List<String>)
data class MeetupSessionDto(val id: String, val postId: String, val sequence: Int, val scheduledFor: String, val createdAt: String)
data class MeetupSessionsResponse(val success: Boolean, val sessions: List<MeetupSessionDto>)
data class MeetupAttendanceDto(val id: String, val sessionId: String, val userId: String, val checkedInAt: String)
data class MeetupAttendanceResponse(val success: Boolean, val attendance: MeetupAttendanceDto)

// Real "who attended" view (Hood product-completeness pass, 2026-09-08) -- see backend
// CommunityService.getSessionAttendance's own doc comment: real endpoint + zero client
// anywhere. Mirrors MeetupAttendanceDto but nested, matching the backend's own real
// MeetupAttendanceWithName response shape (each attendance plus its resolved userName).
data class SessionAttendeeDto(val attendance: MeetupAttendanceDto, val userName: String)
data class SessionAttendanceResponse(val success: Boolean, val attendance: List<SessionAttendeeDto>)
data class FinalizeGroupBuyRequest(val totalAmount: java.math.BigDecimal, val description: String)

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
    // Real hyperlocal neighborhood -- the backend has stamped this on every job post
    // since 2026-07-20 (same reverse-geocode-or-poster-fallback JobPost.kt's own doc
    // comment describes for Listing/PropertyListing) and serializes the entity
    // directly, but this DTO omitted the field until 2026-08-15, same class of gap as
    // PropertyListingDto's identical fix -- see
    // [[project_itunda_full_ecosystem_polish]] for the full pattern.
    val neighborhood: String? = null,
)
data class MarkFilledRequest(val workerPhoneNumber: String? = null)
data class CreateJobPostRequest(
    val category: String, val title: String, val description: String, val payType: String, val payAmount: Double,
    val latitude: Double? = null, val longitude: Double? = null,
)
