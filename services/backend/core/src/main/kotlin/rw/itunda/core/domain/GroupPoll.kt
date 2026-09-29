package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * Real group 투표 (poll) (itunda Talk redesign, 2026-08-28) -- flat FK entities
 * (`GroupPoll`/`GroupPollOption`/`GroupPollVote`), no `@OneToMany`/`@ElementCollection`,
 * matching this codebase's own repo-wide flat-entity convention. Creation open to "any
 * member," same reasoning as [GroupAnnouncement]'s own doc comment.
 */
@Entity
@Table(name = "group_polls")
class GroupPoll(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "group_conversation_id", nullable = false, length = 64)
    val groupConversationId: String,

    @Column(name = "created_by", nullable = false, length = 64)
    val createdBy: String,

    @Column(nullable = false, length = 500)
    var question: String,

    @Column(name = "allow_multiple", nullable = false)
    var allowMultiple: Boolean = false,

    @Column(name = "closes_at")
    var closesAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", groupConversationId = "", createdBy = "", question = "")
}

@Entity
@Table(name = "group_poll_options")
class GroupPollOption(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "poll_id", nullable = false, length = 64)
    val pollId: String,

    @Column(nullable = false, length = 200)
    var text: String,
) {
    protected constructor() : this(id = "", pollId = "", text = "")
}

@Entity
@Table(
    name = "group_poll_votes",
    // Real DB-level backstop against double-clicking the same option twice (always
    // wrong, regardless of allowMultiple) -- one row per (poll, option, user).
    // Single-choice-poll semantics (only one option selected at a time) are enforced
    // by GroupPollAnnouncementService.vote deleting any existing vote row(s) for this
    // (poll, user) transactionally before inserting the new one, a real
    // delete-then-insert "replace" that's race-safe without needing a second,
    // conflicting unique-constraint shape.
    uniqueConstraints = [UniqueConstraint(name = "uk_group_poll_vote_poll_option_user", columnNames = ["poll_id", "option_id", "user_id"])],
)
class GroupPollVote(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "poll_id", nullable = false, length = 64)
    val pollId: String,

    @Column(name = "option_id", nullable = false, length = 64)
    val optionId: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "voted_at", nullable = false)
    val votedAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", pollId = "", optionId = "", userId = "")
}
