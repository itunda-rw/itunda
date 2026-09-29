package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

/**
 * A real menu-item option group -- closes docs/DESIGN_REFERENCES.md's Eats section's
 * single biggest-flagged structural gap: itunda's `MerchantProduct` (see its own doc
 * comment) was flat `id, merchantId, name, price, active, createdAt` with no way to
 * represent spice level, size, or add-ons, unlike Coupang Eats' seller guide (a
 * required-option system is a first-class, *enforced* concept there) or Baemin's own
 * item-detail model.
 *
 * **`required`/`multiSelect` added 2026-07-26**, closing the exact "multi-select
 * optional add-ons (e.g. extra toppings) are a real, separate follow-up, not built
 * here" gap this doc comment itself originally named. Four real combinations, matching
 * Coupang Eats/Baemin's own real option-group model: required+single (the original v1
 * default, e.g. "Size": exactly one of Small/Medium/Large), required+multi (e.g. "pick
 * at least one spice level"), optional+single (e.g. an optional size upgrade), and
 * optional+multi (the new headline case -- toppings: choose zero or more). Existing
 * pre-2026-07-26 groups default to `required=true, multiSelect=false` (migration
 * `V113`), preserving their exact original "exactly one choice" enforcement unchanged
 * -- this is additive, not a behavior change for any already-created group. Deliberately
 * still no min/max choice-count bound (Baemin's own more advanced model) -- itunda's
 * own honest v2 scoping choice, the two-flag model is the simpler, honestly-buildable
 * one, same reasoning `MerchantCoupon`'s flat-fee tiers chose over a CPC/CPM auction.
 *
 * No JPA `@ManyToOne` to `MerchantProduct` -- same deliberate "raw id string, theta-style
 * join where needed" convention `MerchantProductRepository.search`'s own doc comment
 * already established for this codebase (see also `GroupAccountMember.groupAccountId`).
 */
@Entity
@Table(name = "menu_option_groups")
class MenuOptionGroup(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "product_id", nullable = false, length = 64)
    val productId: String,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,

    @Column(nullable = false)
    var required: Boolean = true,

    @Column(name = "multi_select", nullable = false)
    var multiSelect: Boolean = false,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", productId = "", name = "")
}

/** A real, real-priced choice within a [MenuOptionGroup] -- `priceDelta` can be zero
 * (Coupang Eats' own seller guide explicitly requires at least one real +0원 choice per
 * required group so a listed price never diverges from the checkout price once a
 * selection is made) or positive (e.g. "Large" costs 500 RWF more than the base price).
 * Never negative in v1 -- see `MenuOptionService.addOptionGroup`'s own validation. */
@Entity
@Table(name = "menu_option_choices")
class MenuOptionChoice(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "group_id", nullable = false, length = 64)
    val groupId: String,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(name = "price_delta", nullable = false, precision = 18, scale = 2)
    var priceDelta: BigDecimal = BigDecimal.ZERO,

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", groupId = "", name = "")
}
