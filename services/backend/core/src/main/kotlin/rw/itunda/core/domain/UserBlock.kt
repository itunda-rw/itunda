package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A private, directional Talk safety boundary. A block is never exposed to the
 * blocked person and is enforced before a direct conversation can be opened or a
 * message can be delivered. It is intentionally separate from reports: blocking
 * is immediate user control, while reports require moderation review.
 */
@Entity
@Table(name = "user_blocks")
class UserBlock(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "blocker_user_id", nullable = false, length = 64)
    val blockerUserId: String,

    @Column(name = "blocked_user_id", nullable = false, length = 64)
    val blockedUserId: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", blockerUserId = "", blockedUserId = "")
}
