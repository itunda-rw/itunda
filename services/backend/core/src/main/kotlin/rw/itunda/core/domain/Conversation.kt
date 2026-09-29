package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A real 1:1 conversation between two itunda users -- the foundational messaging
 * primitive named in the 2026-07-17 "super app" goal expansion (Kakao-style
 * messaging), picked as the first of the three newly-named phases (Coupang/e-commerce,
 * 당근마켓/neighborhood marketplace, Kakao/messaging) since both of the other two would
 * eventually need real buyer/seller chat anyway -- building it once now avoids
 * building it twice later.
 *
 * `participantAId`/`participantBId` are always stored with the lexicographically
 * smaller user id first (see MessagingService.canonicalPair) so a real DB unique
 * constraint on the pair can enforce "at most one conversation between any two users"
 * without needing an application-level race-prone check-then-insert.
 */
@Entity
@Table(name = "conversations")
class Conversation(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "participant_a_id", nullable = false, length = 64)
    val participantAId: String,

    @Column(name = "participant_b_id", nullable = false, length = 64)
    val participantBId: String,

    @Column(name = "last_message_at", nullable = false)
    var lastMessageAt: Instant = Instant.now(),

    @Column(name = "pinned_message_id", length = 64)
    var pinnedMessageId: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", participantAId = "", participantBId = "")
}
