package rw.itunda.core.network

data class SuccessResponse(val success: Boolean)

data class RecordAnalyticsEventRequest(
    val eventName: String,
    val platform: String = "android",
    val metadata: String? = null,
)

data class ReactionGroupDto(
    val emoji: String,
    val userIds: List<String>,
)

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
    val emoticonId: String? = null,
    val imageUrl: String? = null,
    val forwardedFromMessageId: String? = null,
    val forwardedFromType: String? = null,
    val replyCount: Long = 0,
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
    val emoticonId: String? = null,
    val imageUrl: String? = null,
    val forwardedFromMessageId: String? = null,
    val forwardedFromType: String? = null,
    val unreadCount: Long = 0,
    val replyCount: Long = 0,
)
