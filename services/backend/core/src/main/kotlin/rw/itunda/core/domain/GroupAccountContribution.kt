package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

/**
 * A real per-cycle record of a member's deposits into a Group Account, recorded
 * alongside (not instead of) the real ledger-backed deposit -- see
 * GroupAccountService.deposit and .getDuesStatus's own doc comments. `cycleMonth` is
 * always "yyyy-MM" so a member's real dues status for the current month is a plain sum
 * over this table, with no separate "pay dues" action distinct from an ordinary deposit
 * -- matching how a real 모임통장 works (dues are just transfers that happen to meet
 * the configured monthly target, not a distinct payment type).
 */
@Entity
@Table(name = "group_account_contributions")
class GroupAccountContribution(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "group_account_id", nullable = false, length = 64)
    val groupAccountId: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "cycle_month", nullable = false, length = 7)
    val cycleMonth: String,

    @Column(nullable = false, precision = 18, scale = 2)
    val amount: BigDecimal,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", groupAccountId = "", userId = "", cycleMonth = "", amount = BigDecimal.ZERO)
}
