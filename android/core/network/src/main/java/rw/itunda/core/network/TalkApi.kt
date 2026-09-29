package rw.itunda.core.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// Real itunda Talk (KakaoTalk-parity) redesign, 2026-08-28 -- favorite toggle, service
// channel, AI chatbot, group announcement/poll. Kept as its own Retrofit
// interface/file rather than growing the already-at-baseline ApiService.kt, same real
// precedent HoodApi.kt already established for the same reason.

// Real KakaoTalk favorite chat toggle -- see backend ConversationPreference
// .favorite's own doc comment. Mirrors ConversationPinnedToTopResponse/
// SetConversationPinnedToTopRequest's exact shape.
data class ConversationFavoriteResponse(val success: Boolean, val favorite: Boolean)
data class SetConversationFavoriteRequest(val favorite: Boolean)

// Real itunda service channel -- itunda's own Notification rows projected into a
// read-only alimtalk-style chat thread. See backend ServiceChannelService's own doc
// comment. ctaRoute is a real in-app route string (e.g. "/bank/transactions") or null.
data class ServiceChannelBubbleDto(
    val id: String, val title: String, val body: String, val type: String,
    val createdAt: String, val isRead: Boolean, val ctaRoute: String?,
)
data class ServiceChannelResponse(
    val success: Boolean, val bubbles: List<ServiceChannelBubbleDto>,
    val page: Int, val size: Int, val totalElements: Long, val totalPages: Int,
)

// Real AI chatbot channel, reusing the self-hosted llama-server -- see backend
// AiChatService's own doc comment on the real single-flight guard + per-user cooldown.
// A 429 response (AiChatBusyException) means the shared model is busy right now --
// callers should render this as a real, always-visible state, never fabricate a reply.
data class AiChatMessageDto(val id: String, val userId: String, val role: String, val content: String, val createdAt: String)
data class SendAiChatMessageRequest(val text: String)
data class SendAiChatMessageResponse(val success: Boolean, val message: AiChatMessageDto, val reply: AiChatMessageDto)
data class AiChatHistoryResponse(val success: Boolean, val messages: List<AiChatMessageDto>, val page: Int, val size: Int, val totalElements: Long, val totalPages: Int)

// Real 1:1 calling -- call-log only in this pass (see CallService.kt's own doc
// comment); no dial/answer/end UI here, that's a separate later pass.
data class CallSessionDto(
    val id: String, val conversationId: String, val callerId: String, val calleeId: String,
    val callType: String, val startedAt: String, val answeredAt: String?, val endedAt: String?, val endReason: String?,
)
data class CallHistoryResponse(val success: Boolean, val calls: List<CallSessionDto>, val page: Int, val size: Int, val totalElements: Long, val totalPages: Int)

// Real group 공지/투표 (announcement + poll) -- open to any group member, see backend
// GroupPollAnnouncementService's own doc comment on why no admin/role gate exists yet.
data class GroupAnnouncementDto(val id: String, val groupConversationId: String, val createdBy: String, val body: String, val createdAt: String)
data class GroupAnnouncementResponse(val success: Boolean, val announcement: GroupAnnouncementDto?)
data class PostGroupAnnouncementRequest(val body: String)
data class GroupPollOptionDto(val id: String, val pollId: String, val text: String)
data class GroupPollDto(val id: String, val groupConversationId: String, val createdBy: String, val question: String, val allowMultiple: Boolean, val closesAt: String?, val createdAt: String)
data class GroupPollWithVotesDto(val poll: GroupPollDto, val options: List<GroupPollOptionDto>, val voteCountByOptionId: Map<String, Int>, val myVoteOptionIds: List<String>)
data class GroupPollResponse(val success: Boolean, val poll: GroupPollWithVotesDto)
data class GroupPollsResponse(val success: Boolean, val polls: List<GroupPollWithVotesDto>)
data class CreateGroupPollRequest(val question: String, val options: List<String>, val allowMultiple: Boolean = false, val closesAt: String? = null)
data class VoteGroupPollRequest(val optionId: String)

interface TalkApi {
    @GET("api/v1/messages/conversations/{id}/favorite")
    suspend fun getConversationFavorite(@Path("id") conversationId: String): ConversationFavoriteResponse

    @POST("api/v1/messages/conversations/{id}/favorite")
    suspend fun setConversationFavorite(@Path("id") conversationId: String, @Body request: SetConversationFavoriteRequest): ConversationFavoriteResponse

    @GET("api/v1/talk/service-channel")
    suspend fun getServiceChannel(@Query("page") page: Int = 0, @Query("size") size: Int = 30): ServiceChannelResponse

    @POST("api/v1/talk/ai-chat/messages")
    suspend fun sendAiChatMessage(@Body request: SendAiChatMessageRequest): SendAiChatMessageResponse

    @GET("api/v1/talk/ai-chat/messages")
    suspend fun getAiChatHistory(@Query("page") page: Int = 0, @Query("size") size: Int = 50): AiChatHistoryResponse

    @GET("api/v1/calls/history")
    suspend fun getCallHistory(@Query("page") page: Int = 0, @Query("size") size: Int = 30): CallHistoryResponse

    @POST("api/v1/messages/groups/{groupId}/announcement")
    suspend fun postGroupAnnouncement(@Path("groupId") groupId: String, @Body request: PostGroupAnnouncementRequest): GroupAnnouncementResponse

    @GET("api/v1/messages/groups/{groupId}/announcement")
    suspend fun getGroupAnnouncement(@Path("groupId") groupId: String): GroupAnnouncementResponse

    @POST("api/v1/messages/groups/{groupId}/polls")
    suspend fun createGroupPoll(@Path("groupId") groupId: String, @Body request: CreateGroupPollRequest): GroupPollResponse

    @GET("api/v1/messages/groups/{groupId}/polls")
    suspend fun getGroupPolls(@Path("groupId") groupId: String): GroupPollsResponse

    @POST("api/v1/messages/groups/{groupId}/polls/{pollId}/vote")
    suspend fun voteGroupPoll(@Path("groupId") groupId: String, @Path("pollId") pollId: String, @Body request: VoteGroupPollRequest): GroupPollResponse

    // Real cross-platform-parity gap found live (2026-09-13) -- web already shows a
    // real numeric badge (capped "99+") on the Messages tab using this exact
    // unbounded aggregate endpoint (BankDashboard.tsx); Android had neither the
    // endpoint nor any bottom-nav badge mechanism at all.
    @GET("api/v1/messages/unread-count")
    suspend fun getUnreadCount(): UnreadCountResponse
}

data class UnreadCountResponse(val success: Boolean, val conversationsUnread: Long, val groupsUnread: Long, val total: Long)
