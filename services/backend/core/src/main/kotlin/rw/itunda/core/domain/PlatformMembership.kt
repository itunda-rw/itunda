package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant

/**
 * Real Coupang 와우 (Wow) membership-style unconditional delivery-fee waiver --
 * Coupang's own real, currently-live product (news.coupang.com's own press material;
 * digitaltoday.co.kr's April 2026 coverage of the policy tightening non-members now
 * face: free shipping requires 19,800+ KRW post-discount, while a Wow member gets free
 * delivery on every order, no minimum, even a single item). 7,890 KRW/month is
 * Coupang's real current price; itunda's own `MEMBERSHIP_TIERS` below is itunda's own
 * honest RWF scoping choice, not a currency conversion of that real number.
 *
 * Deliberately distinct from `EatsMembership` (item 102, Baemin Club-style), not a
 * replacement: that one only waives the fee at a restaurant that has itself opted in
 * (`Merchant.participatesInEatsMembership`), mirroring Baemin's own real "참여 가게"
 * scoping. This one is the real Wow-style upgrade -- an unconditional, account-wide
 * waiver at every restaurant, no merchant opt-in required, the same real distinction
 * Coupang Wow has over a participating-seller-only free-shipping program. Both can be
 * held at once; `EatsOrderService.placeOrder` checks either resolves to a fee waiver
 * for a given order, checking `PlatformMembership` first since that's a strictly
 * broader real guarantee.
 *
 * Same real flat-fee-per-time-slot pattern `EatsMembership`/`Listing.boostedUntil`
 * already establish (pay once, extend `activeUntil`, stacking on repeat purchase)
 * rather than a recurring-auto-billing scheduler -- itunda's own honestly-simpler v1.
 *
 * Lives in `:core`/is owned by `rw.itunda.eats.PlatformMembershipService` since Eats'
 * delivery fee is the only real fee this backend charges today that a membership could
 * honestly waive -- Commerce/Shop orders have no delivery-fee concept at all to waive
 * (itunda's own internal rider fleet, added 2026-07-26, carries no separate charge). A
 * genuinely platform-wide home (its own module) is a natural follow-up if/when a second
 * real fee this could apply to exists.
 */
@Entity
@Table(name = "platform_memberships")
class PlatformMembership(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, unique = true, length = 64)
    val userId: String,

    @Column(name = "active_until", nullable = false)
    var activeUntil: Instant,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    // Real optimistic lock (found live 2026-08-02) -- same race
    // EatsMembership.kt's own doc comment names: PlatformMembershipService.subscribe
    // is a real check-then-act shape once a membership row already exists (read the
    // current `activeUntil`, extend it, save), and the unique constraint on
    // `userId` only protects the very first subscribe's INSERT, not two concurrent
    // EXTENSIONS of an already-existing membership.
    @Version
    var version: Long = 0,
) {
    protected constructor() : this(id = "", userId = "", activeUntil = Instant.EPOCH)
}
