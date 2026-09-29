package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * Per-user controls for a private Talk room. They deliberately do not live on
 * [Conversation]: muting or moving a room aside must never change what the other
 * participant sees. This is the durable basis for KakaoTalk-style quiet rooms.
 */
@Entity
@Table(
    name = "conversation_preferences",
    uniqueConstraints = [UniqueConstraint(name = "uk_conversation_preference_user_room", columnNames = ["conversation_id", "user_id"])],
)
class ConversationPreference(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "conversation_id", nullable = false, length = 64)
    val conversationId: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "quiet", nullable = false)
    var quiet: Boolean = false,

    // Real recoverable archive (Kakao's official "archive-without-leaving" feature) --
    // same private-to-one-participant model as quiet above: removed from this user's
    // active list without leaving, deleting history, or affecting the other
    // participant. Recoverable: unarchiving is just flipping this back to false.
    @Column(name = "archived", nullable = false)
    var archived: Boolean = false,

    // Real KakaoTalk "채팅방 상단 고정" (pin chat room to top) (2026-08-17) -- the third
    // real long-press room action alongside quiet/archived above. Same
    // private-to-one-participant model: pinning is never visible to or forced on the
    // other participant. Sort order lives at the DB level in
    // ConversationRepository.findByParticipantNotArchived (not post-hoc in-app, same
    // "pagination stays correct" discipline archived's own doc comment already
    // establishes) -- a pinned room sorts above every unpinned room regardless of
    // lastMessageAt, matching real KakaoTalk behavior.
    @Column(name = "pinned", nullable = false)
    var pinned: Boolean = false,

    // Real KakaoTalk-style "favorite" chat (itunda Talk redesign, 2026-08-28) --
    // same private-to-one-participant model as every other preference here. Scoped
    // to 1:1 conversations only for now, matching this table's own scope -- group
    // chat has no equivalent per-user preference entity yet (no quiet/archived/
    // pinned-to-top on a GroupConversation either), so this isn't a new gap, just
    // consistent with what already exists.
    @Column(name = "favorite", nullable = false)
    var favorite: Boolean = false,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", conversationId = "", userId = "")
}
