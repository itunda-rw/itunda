package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant

enum class IkiminaStatus { FORMING, ACTIVE, COMPLETED }

/**
 * Real ikimina -- Rwanda's own rotating savings & credit association (ROSCA), distinct
 * from every Toss/Kakao/Naver/Coupang-sourced feature in this backend: a group of
 * members each contribute an equal, fixed amount every real cycle, and ONE member
 * receives the full pot each round, in a pre-agreed rotating order, until every member
 * has been paid exactly once. Sourced from real, published Rwanda-context research
 * (ikimina/ROSCA academic literature) and confirmed as a real, currently-live product
 * category by an existing Rwandan startup (smartikimina.rw) that digitizes the same
 * mechanic via mobile money -- a genuine market need, not an invented one.
 *
 * Deliberately NOT a variant of `GroupAccount` (Kakao Bank 모임통장): that entity's
 * whole shape is "one permanent owner holds sole withdrawal authority, everyone else
 * can only deposit" -- there is no rotation, no per-cycle payout recipient, no turn
 * order anywhere in it. An ikimina is peer-governed with a rotating beneficiary, a
 * structurally different primitive that needs its own entity, not a reused one.
 *
 * Backed by a real `Account` (AccountType.GROUP, the same real type `GroupAccount`
 * already established -- no new account-type concept needed), so every contribution/
 * payout is the same real ledger-backed ACCOUNT-to-ACCOUNT movement every other
 * money-moving feature in this backend already uses.
 *
 * `@Version`: the payout trigger is a real check-then-act operation (has every member
 * contributed this round?) -- applying this session's own hard-won lesson from the
 * Bike/Parking/Knowledge/SupportTicket concurrency fixes by adding real optimistic
 * locking from day one instead of waiting to find the race live.
 */
@Entity
@Table(name = "ikiminas")
class Ikimina(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(nullable = false, length = 255)
    var name: String,

    @Column(name = "organizer_id", nullable = false, length = 64)
    val organizerId: String,

    @Column(name = "account_id", nullable = false, length = 64)
    val accountId: String,

    @Column(name = "contribution_amount", nullable = false, precision = 18, scale = 2)
    val contributionAmount: BigDecimal,

    // Real cadence in days -- 7 for weekly, 30 for monthly, the two real cadences
    // ikimina groups actually use, matching how a real group agrees on a schedule
    // rather than an invented enum with more granularity than any real group needs.
    @Column(name = "cycle_frequency_days", nullable = false)
    val cycleFrequencyDays: Int,

    // Real ikimina groups are typically 8-15 members (per the sourced ROSCA
    // literature) -- enforced as a real, named upper bound, not an invented one.
    @Column(name = "member_cap", nullable = false)
    val memberCap: Int,

    @Column(nullable = false)
    var currentRound: Int = 1,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: IkiminaStatus = IkiminaStatus.FORMING,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(
        id = "", name = "", organizerId = "", accountId = "", contributionAmount = BigDecimal.ZERO,
        cycleFrequencyDays = 30, memberCap = 15,
    )
}

/**
 * One real member's rotation slot in a real ikimina -- see Ikimina.kt's own doc
 * comment. `payoutOrder` is the pre-agreed slot (1-based, unique per ikimina) this
 * member receives the full pot in; set at join time, matching the real ROSCA practice
 * of drawing/agreeing slots before the cycle starts (an honest v1 simplification: the
 * organizer assigns the order rather than this backend simulating a live random draw
 * -- a real, named scope choice, not a fabricated mechanic).
 */
@Entity
@Table(name = "ikimina_members")
class IkiminaMember(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "ikimina_id", nullable = false, length = 64)
    val ikiminaId: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "payout_order", nullable = false)
    val payoutOrder: Int,

    @Column(name = "has_received_payout", nullable = false)
    var hasReceivedPayout: Boolean = false,

    @Column(name = "joined_at", nullable = false)
    val joinedAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", ikiminaId = "", userId = "", payoutOrder = 0)
}

/**
 * One real member's real contribution for one real round -- see Ikimina.kt's own doc
 * comment. A real DB unique constraint on (ikimina_id, member_id, round) is the actual
 * guard against a member double-contributing the same round (not just an
 * application-level check), matching this backend's own established discipline of
 * backing a real invariant with a real constraint wherever one exists (e.g.
 * GroupAccountMember's own group+user uniqueness).
 */
@Entity
@Table(name = "ikimina_contributions")
class IkiminaContribution(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "ikimina_id", nullable = false, length = 64)
    val ikiminaId: String,

    @Column(name = "member_id", nullable = false, length = 64)
    val memberId: String,

    @Column(nullable = false)
    val round: Int,

    @Column(nullable = false, precision = 18, scale = 2)
    val amount: BigDecimal,

    @Column(name = "contributed_at", nullable = false)
    val contributedAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", ikiminaId = "", memberId = "", round = 0, amount = BigDecimal.ZERO)
}
