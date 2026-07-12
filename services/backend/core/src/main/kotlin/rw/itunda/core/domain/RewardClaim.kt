package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

// One row per user per claimed task -- the claim-once guard is a unique constraint on
// (user_id, task_id), not just an application-level check, so a race between two concurrent
// claim requests for the same task can't both succeed (see V6 migration).
@Entity
@Table(name = "reward_claims")
class RewardClaim(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "task_id", nullable = false, length = 64)
    val taskId: String,

    @Column(nullable = false, precision = 18, scale = 2)
    val amount: BigDecimal,

    @Column(name = "claimed_at", nullable = false)
    val claimedAt: Instant,
) {
    protected constructor() : this(id = "", userId = "", taskId = "", amount = BigDecimal.ZERO, claimedAt = Instant.now())
}
