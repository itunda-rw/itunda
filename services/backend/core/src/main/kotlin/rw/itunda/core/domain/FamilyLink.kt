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

enum class FamilyLinkStatus { PENDING, ACTIVE, DECLINED, REVOKED }

/**
 * A real Toss 유스 (Toss Youth)-style guardian-child account link -- sourced from
 * Toss's own real published "용돈관리서비스": a parent creates a real link to their
 * child's account, sends allowance, and gets real read-only visibility into the
 * child's spending, while the child keeps sending/spending on their own itunda
 * account.
 *
 * Honest, explicit scope boundary (read before assuming this is more than it is): real
 * Toss Youth is a genuinely separate minor-specific account product with its own KYC
 * flow, a physical prepaid card (₩500,000 cap), and real spend-limit enforcement at
 * the card-network level. itunda has none of that infrastructure -- this is a real
 * link between two ORDINARY existing itunda accounts (no separate "minor account"
 * type), giving the guardian real read-only oversight (child's wallet balance + real
 * transaction history) via `FamilyLinkService.getChildOverview`. Allowance itself
 * needs no new mechanism at all -- the guardian just points the already-real
 * `AutoTransfer`/`ScheduledTransfer` features at the child's phone number, the same
 * "reuse the real money-movement path, don't invent a second one" discipline this
 * codebase has followed all session.
 *
 * `dailySpendLimit` (added 2026-07-27) closes this row's own previously-named deferred
 * follow-up: real spend-limit enforcement, modeled on KakaoBank mini's real published
 * 일일이체한도 (daily transfer limit) for youth accounts -- the closest real, sourced,
 * implementable analogue to Toss Youth's own card-network-level cap, since itunda has
 * no card network to enforce against. NULL means unrestricted (this link's original,
 * pre-existing behavior) -- honestly opt-in, never a silent new restriction on an
 * existing link. Enforced only against the child's own real P2P sends (see
 * `P2pService.sendDirect`'s own call-site comment), the same "P2P is the single most
 * frequent real money-out action, widen flow-by-flow rather than claim blanket
 * coverage" honest-scoping precedent `RoundUpSettings` already established twice.
 */
@Entity
@Table(name = "family_links")
class FamilyLink(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "guardian_user_id", nullable = false, length = 64)
    val guardianUserId: String,

    @Column(name = "child_user_id", nullable = false, length = 64)
    val childUserId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: FamilyLinkStatus = FamilyLinkStatus.PENDING,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "responded_at")
    var respondedAt: Instant? = null,

    @Column(name = "daily_spend_limit", precision = 18, scale = 2)
    var dailySpendLimit: BigDecimal? = null,

    // Real bug found live (2026-08-02): respondToInvite/revokeLink both read-then-mutate
    // this row's status with no concurrency guard -- two concurrent respondToInvite
    // calls (accept and decline racing from a flaky client retry) could both read
    // PENDING and both commit, whichever writes last silently winning. Only one real
    // state transition may resolve a given invitation/link.
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(id = "", guardianUserId = "", childUserId = "")
}
