package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Real 당근모임 (Karrot Meetups) recurring schedule -- sourced from Karrot's own real,
 * published feature (about.daangn.com's own PR archive, second release): a regular
 * meetup group (running, tennis, etc.) can create a real recurring series of up to
 * `CommunityService.MAX_MEETUP_SESSIONS` (6, Karrot's own real sourced cap) fixed
 * weekly/biweekly/monthly session dates in one action, rather than posting one
 * `CommunityPost` per real occurrence.
 *
 * Deliberately a lightweight child of the existing `CommunityPost` (the meetup's own
 * `groupConversationId` single shared chat is unchanged, reused across every real
 * session -- itunda's own honest v1 scope-down from Karrot's own real per-session
 * sub-chat, named explicitly, not silently dropped) rather than a parallel meetup
 * concept: `postId` ties every real session back to the one real meetup post/group a
 * user already joined via `CommunityService.joinMeetup`.
 */
@Entity
@Table(name = "meetup_sessions")
class MeetupSession(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "post_id", nullable = false, length = 64)
    val postId: String,

    @Column(nullable = false)
    val sequence: Int,

    @Column(name = "scheduled_for", nullable = false)
    val scheduledFor: Instant,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", postId = "", sequence = 0, scheduledFor = Instant.now())
}
