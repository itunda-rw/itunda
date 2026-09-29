package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * Real Karrot(당근마켓) 동네생활 "새 댓글 알림 끄기" (turn off new-comment notifications) --
 * Karrot's own official support FAQ (cs.kr.karrotmarket.com/wv/faqs/3106, "동네생활 새 댓글
 * 알림을 끌 수 있나요?") confirms this is a real, shipped per-category notification
 * toggle in the app's own notification settings screen. `CommunityService.addComment`
 * currently notifies a post's author on every single comment unconditionally, with no
 * way to turn it off -- exactly the gap this FAQ answers for the real product.
 *
 * One row per user (missing row = notifications ON, same "no row = default" convention
 * [ConversationPreference] already establishes for `quiet`/`archived`/`pinned`) --
 * global across all of a user's posts, matching the real product's own category-level
 * toggle (not a per-post mute, which Karrot's cited FAQ does not describe).
 */
@Entity
@Table(
    name = "community_notification_preferences",
    uniqueConstraints = [UniqueConstraint(name = "uk_community_notification_preference_user", columnNames = ["user_id"])],
)
class CommunityNotificationPreference(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "comment_notifications_enabled", nullable = false)
    var commentNotificationsEnabled: Boolean = true,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
