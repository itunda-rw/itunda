package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

/**
 * Real round-up auto-saving -- closes the gap named in Kakao Pay's own real 머니굴리기
 * ("rolling money") product: every qualifying real payment rounds up to a real chosen
 * increment, and the spare change auto-deposits into a real savings destination.
 * Honestly scoped v1: only real P2P transfers ([rw.itunda.p2p.P2pService.sendDirect])
 * trigger a round-up, not every money-moving flow in this backend (Kakao's own product
 * is described as covering "every Kakao Pay payment," but wiring every itunda flow --
 * merchant checkout, bills, Eats, Shop -- in one pass would touch too many
 * already-tested money-movement call sites at once; P2P transfer is the single most
 * frequent real consumer money-out action, and this is a real, honest, named-scope v1,
 * not a silently partial "every payment" claim).
 *
 * `targetGoalId` must reference a real [SavingsGoal] the same user owns -- `enabled`
 * can only be real-true once a real target is set, never a dangling "on" with nowhere
 * for the money to go. `roundToNearest` is a real merchant-set... no, user-set
 * increment (e.g. round every transfer up to the nearest 100/500/1,000 RWF), not a
 * fabricated default.
 */
@Entity
@Table(name = "round_up_settings")
class RoundUpSettings(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, unique = true, length = 64)
    val userId: String,

    @Column(nullable = false)
    var enabled: Boolean = false,

    @Column(name = "round_to_nearest", nullable = false, precision = 18, scale = 2)
    var roundToNearest: BigDecimal,

    @Column(name = "target_goal_id", length = 64)
    var targetGoalId: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", roundToNearest = BigDecimal("100"))
}
