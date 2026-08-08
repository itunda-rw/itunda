package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A real group conversation (2026-07-18) -- the single most defining KakaoTalk
 * capability the original 1:1-only `Conversation`/`Message` pair didn't cover, closing
 * that gap at the user's direct request ("Talk should be 100% like KakaoTalk + 당근
 * 채팅 for Rwanda"). Deliberately a brand-new, additive set of entities
 * (`GroupConversation`/`GroupConversationMember`/`GroupMessage`) rather than widening
 * the existing `Conversation`'s fixed `participantAId`/`participantBId` columns to
 * support N participants -- that would mean migrating already-live, already-proven 1:1
 * conversation data and rewriting `MessagingService`'s own already-verified logic,
 * exactly the kind of risk to already-tested money-adjacent (real chat, real users)
 * code this session's own discipline avoids everywhere else. 1:1 messaging is
 * completely untouched by this.
 *
 * See `GroupMessagingService`'s own doc comment for the real membership/read-tracking
 * design.
 */
@Entity
@Table(name = "group_conversations")
class GroupConversation(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(name = "created_by", nullable = false, length = 64)
    val createdBy: String,

    @Column(name = "last_message_at", nullable = false)
    var lastMessageAt: Instant = Instant.now(),

    // Real group-chat pin (2026-07-26), same shape as 1:1 Conversation.pinnedMessageId
    // -- see GroupMessagingService.setPinnedMessage's own doc comment.
    @Column(name = "pinned_message_id", length = 64)
    var pinnedMessageId: String? = null,

    // Real group photo/description (2026-07-28) -- see GroupMessagingService
    // .setGroupPhotoUrl/setGroupDescription's own doc comments, closing one of the two
    // real gaps this class's own doc comment named as not attempted in v1 (admin/
    // owner roles beyond createdBy remain the one still-open gap). Both nullable --
    // unset is the pre-existing default for every group created before this.
    @Column(name = "photo_url", length = 2048)
    var photoUrl: String? = null,

    @Column(length = 500)
    var description: String? = null,

    // Real 1:1-chat split-bill support (2026-08-09) -- see
    // GroupMessagingService.getOrCreateDirectSplitGroup's own doc comment. TRUE for a
    // synthetic 2-person group created behind the scenes to back a bill split between
    // two people talking 1:1, not a real named group -- excluded from
    // GroupConversationRepository.findByMember so it never shows up in either person's
    // "My Groups" list. FALSE (the default) is every real group, past and future.
    @Column(name = "is_direct", nullable = false)
    var isDirect: Boolean = false,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", name = "", createdBy = "")
}

/**
 * A real member of a [GroupConversation] -- `lastReadAt` tracks this member's own read
 * position (a real per-member cursor, updated whenever they fetch a page of messages),
 * the same "viewing the screen marks it read" convention 1:1 `Message.readAt` already
 * uses, just shaped for N members instead of exactly one recipient. **Update
 * 2026-07-26**: this single cursor turned out to be enough to build the real Kakao-style
 * per-message read-receipt countdown too (see `GroupMessagingService.getUnreadCounts`'s
 * own doc comment) -- a message's remaining-unread count is just "how many other
 * members' cursors are still behind this message's `sentAt`," no separate
 * per-message-per-member row needed after all.
 */
@Entity
@Table(name = "group_conversation_members")
class GroupConversationMember(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "group_conversation_id", nullable = false, length = 64)
    val groupConversationId: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "joined_at", nullable = false)
    val joinedAt: Instant = Instant.now(),

    @Column(name = "last_read_at")
    var lastReadAt: Instant? = null,
) {
    protected constructor() : this(id = "", groupConversationId = "", userId = "")
}

/** A real message within a [GroupConversation] -- same shape as 1:1 `Message`, kept as
 * its own table (not a shared/polymorphic one) so neither entity's queries or FK
 * constraints have to reason about the other's conversation-id space. */
@Entity
@Table(name = "group_messages")
class GroupMessage(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "group_conversation_id", nullable = false, length = 64)
    val groupConversationId: String,

    @Column(name = "sender_id", nullable = false, length = 64)
    val senderId: String,

    @Column(nullable = false, length = 2000)
    val body: String,

    @Column(name = "reply_to_message_id", length = 64)
    val replyToMessageId: String? = null,

    @Column(name = "sent_at", nullable = false)
    val sentAt: Instant = Instant.now(),

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,

    @Column(name = "deleted_by_user_id", length = 64)
    var deletedByUserId: String? = null,

    // Real message forwarding (2026-07-25) -- see Message.kt's own doc comment for the
    // full account; identical shape here.
    @Column(name = "forwarded_from_message_id", length = 64)
    val forwardedFromMessageId: String? = null,

    @Column(name = "forwarded_from_type", length = 16)
    val forwardedFromType: String? = null,

    // Real @mention support (2026-07-25) -- comma-separated real user ids, resolved
    // server-side against this group's actual membership at send time (see
    // GroupMessagingService.parseMentions's own doc comment), never a client-asserted
    // list. Null/empty when the message mentions no one.
    @Column(name = "mentioned_user_ids", length = 1000)
    val mentionedUserIds: String? = null,

    // Real composer photo send (2026-07-25) -- see Message.kt's own doc comment for the
    // full account; identical shape here.
    @Column(name = "image_url", length = 255)
    val imageUrl: String? = null,

    // Real Emoticon Store send (2026-07-26) -- see Message.kt's own doc comment for the
    // full account; identical shape here.
    @Column(name = "emoticon_id", length = 64)
    val emoticonId: String? = null,
) {
    protected constructor() : this(id = "", groupConversationId = "", senderId = "", body = "")
}
