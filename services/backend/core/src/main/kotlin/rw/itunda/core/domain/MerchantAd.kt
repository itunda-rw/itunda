package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant

/**
 * Real radius-targeted local business ads -- closes 당근 (Karrot)'s own real, sourced
 * "반경 타기팅" (radius targeting) feature (about.daangn.com's own press release):
 * Korea's first hyperlocal ad capability, a real business-configurable radius from
 * 300m to 1.5km around the business's registered address, in 100m increments,
 * launched for 9 real local-business industries. Karrot's own reported real 2-week
 * test (July 13-26) showed a real 20% higher click-through rate and 30% lower
 * cost-per-conversion versus their prior district-level targeting.
 *
 * One real ad slot per merchant (matches Baemin's own real 울트라콜 mechanic --
 * one flat-fee placement per time slot -- the same model `Listing.boostedUntil`'s
 * own doc comment already chose over Coupang's per-click auction as "the simpler,
 * more honestly-buildable of the two real sourced models"), unique on `merchantId`,
 * upserted like `RoundUpSettings` rather than allowing unbounded simultaneous ad rows.
 *
 * **`radiusMeters` is the genuinely new dimension** neither `Listing.boostedUntil`
 * (Marketplace boost) nor `MerchantProduct.discountPercent` has -- see
 * `MerchantAdService.LOCAL_AD_TIERS`'s own doc comment for why day-based flat pricing
 * is reused rather than inventing a CPC/CPM auction Karrot's own press release never
 * disclosed pricing for.
 *
 * The target point is the merchant's own registered [Merchant.latitude]/[Merchant.longitude]
 * -- itunda has no stored per-user coordinate anywhere in this backend (`User` only
 * ever persists a reverse-geocoded neighborhood *name*, never the raw coordinate --
 * see `AuthService.setNeighborhood`'s own doc comment), so matching is necessarily a
 * real "pull" model: `MerchantAdService.nearby` takes the CALLER's live coordinate as a
 * request parameter, the exact same honest shape `AgentService.nearby`/
 * `MarketplaceService.nearby`/`JobPostService.nearby`/`PropertyListingService.nearby`
 * already established for every other "find X near me" feature in this codebase --
 * not a background push to nearby devices, which this backend has no infrastructure
 * for at all (confirmed by a full repo-wide sweep during the review-reply feature
 * shipped the same day: zero FCM/APNs/device-token infrastructure anywhere).
 */
@Entity
@Table(name = "merchant_ads")
class MerchantAd(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "merchant_id", nullable = false, unique = true, length = 64)
    val merchantId: String,

    @Column(nullable = false, length = 100)
    var title: String,

    @Column(length = 300)
    var description: String? = null,

    @Column(name = "radius_meters", nullable = false)
    var radiusMeters: Int,

    @Column(name = "active_until", nullable = false)
    var activeUntil: Instant,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    // Real optimistic lock (found live 2026-08-02) -- same real check-then-act
    // create-or-extend race EatsMembership.kt's own doc comment names:
    // MerchantAdService.createOrExtendAd reads the current `activeUntil` and
    // extends it, and the unique constraint on `merchantId` only protects the very
    // first ad's INSERT, not two concurrent EXTENSIONS of an already-existing ad,
    // which would both debit the merchant's account for a real charge but only
    // actually extend `activeUntil` once.
    @Version
    var version: Long = 0,
) {
    protected constructor() : this(id = "", merchantId = "", title = "", radiusMeters = 300, activeUntil = Instant.EPOCH)
}
