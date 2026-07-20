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
 * Deliberately scoped down for this v1 pass, named honestly rather than silently
 * under-delivering: every group here is a REQUIRED, SINGLE-SELECT choice (e.g. "Size":
 * Small/Medium/Large, exactly one must be chosen) -- there is no `required`/`multiSelect`
 * flag because v1 doesn't need one, not because it was forgotten. Multi-select optional
 * add-ons (e.g. extra toppings) are a real, separate follow-up, not built here.
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
