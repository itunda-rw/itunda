package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant

/**
 * Real Baemin Club (배민클럽)-style free-delivery membership -- Baemin's own real
 * product (apps.apple.com's own Baemin app-store listing: "배달팁 무료" (free delivery
 * fees) on "배민클럽 입점 가게" (participating Baemin Club restaurants) via
 * "알뜰배달"/economical delivery, bundled with a real promotional YouTube Premium
 * tie-in). Exact real pricing wasn't consistently disclosed in the sources checked
 * (the app-store page itself explicitly disclaims "프로모션은... 변경 또는 종료될 수 있어요" --
 * subject to change), so `EatsMembershipService.MEMBERSHIP_TIERS`' actual fee is
 * itunda's own honest scoping choice, not a currency-converted reuse of an unconfirmed
 * Baemin number.
 *
 * Modeled on the same real flat-fee-per-time-slot pattern `Listing.boostedUntil`/
 * `MerchantAd`'s own tiers already established (pay once, extend `activeUntil`,
 * stacking on repeat purchase) rather than building a brand-new recurring-auto-billing
 * scheduler -- itunda's own honestly-simpler v1, the same reasoning that chose a flat
 * fee over a CPC/CPM auction for local ads.
 *
 * Free delivery only applies at a restaurant that has itself opted in
 * (`Merchant.participatesInEatsMembership`), mirroring Baemin's own real "참여 가게"
 * (participating store) scoping -- never a blanket waiver across every restaurant.
 */
@Entity
@Table(name = "eats_memberships")
class EatsMembership(
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

    // Real "date field with no reminder" gap, same shape already closed for
    // MerchantCoupon.expiryReminderSentAt/InsurancePolicy.endDate/GiftVoucher.expiresAt:
    // `activeUntil` sat here with zero notification hook before this. Real Baemin app
    // behavior sends a push before a paid membership benefit lapses so a member can
    // renew before losing free delivery -- this entity's own doc comment already
    // deliberately chose a flat-fee "pay once, extend" model over auto-billing, which
    // makes a pre-expiry reminder the honest equivalent of what an auto-renewing
    // membership's own "your card will be charged soon"/"your plan is ending" notice
    // does elsewhere. Null until a real reminder has been sent for the CURRENT
    // `activeUntil`; re-subscribing (which pushes `activeUntil` further out) should
    // eventually get its own new reminder, so `subscribe` resets this back to null
    // whenever it extends the membership.
    @Column(name = "reminder_sent_at")
    var reminderSentAt: Instant? = null,

    // Real optimistic lock (found live 2026-08-02): EatsMembershipService.subscribe
    // is a real check-then-act shape once a membership row already exists (read the
    // current `activeUntil`, extend it, save) -- the unique constraint on `userId`
    // only protects the very first subscribe's INSERT race, not two concurrent
    // EXTENSIONS of an already-existing membership, which would both debit the
    // account for a real charge but only actually extend `activeUntil` once (both
    // reads see the same starting point). This makes the loser's save fail with a
    // real optimistic lock conflict, rolling back its own duplicate charge with it
    // (same transaction).
    @Version
    var version: Long = 0,
) {
    protected constructor() : this(id = "", userId = "", activeUntil = Instant.EPOCH)
}
