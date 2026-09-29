package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

// OPEN: participants can still join/edit their own items. FINALIZED: the host has
// combined every participant's items into one real EatsOrder (resultingOrderId set) --
// terminal, same forward-only discipline EatsOrderStatus already established.
// CANCELLED: the host called off the group order before finalizing -- also terminal.
enum class GroupEatsOrderStatus { OPEN, FINALIZED, CANCELLED }

/**
 * Real 배달의민족 함께주문 (Baemin "Together Order") -- multiple people share one
 * restaurant's cart via a short join code, each adding their own items, before ONE of
 * them (the host) places ONE real [EatsOrder] for the combined cart. Deliberately kept
 * as its own pre-checkout staging entity rather than a new field on [EatsOrder] itself --
 * an [EatsOrder] only ever has one real payer/buyer (its ledger-charged `buyerId`), and
 * this group cart's whole job is to resolve down to exactly that shape once finalized,
 * same "purely additive, reuse the already-tested money-movement path unchanged"
 * discipline this session already established for [EatsOrder] itself.
 *
 * Real Baemin's own 2026-06 update (더치페이, "Dutch pay") settles payment the same way
 * this reuses: the host pays for the whole real order up front, then requests
 * reimbursement from each other participant afterward for their own share -- not a
 * multi-payer atomic split at checkout time. `GroupEatsOrderService.finalize` mirrors
 * this exactly: place one real order as the host, then call the already-real, already-
 * tested `SplitBillService.createDirectSplitBill` once per other participant with their
 * own real subtotal.
 */
@Entity
@Table(name = "group_eats_orders")
class GroupEatsOrder(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "host_user_id", nullable = false, length = 64)
    val hostUserId: String,

    @Column(name = "restaurant_id", nullable = false, length = 64)
    val restaurantId: String,

    // Real short, human-shareable code (e.g. "K3F9XQ") -- not the row's own `id`, which
    // stays a full UUID-shaped internal identifier never meant to be spoken/typed. See
    // GroupEatsOrderService.generateJoinCode's own doc comment.
    @Column(name = "join_code", nullable = false, length = 12, unique = true)
    val joinCode: String,

    @Column(name = "delivery_address", nullable = false, length = 500)
    var deliveryAddress: String,

    @Column(name = "delivery_latitude")
    var deliveryLatitude: Double? = null,

    @Column(name = "delivery_longitude")
    var deliveryLongitude: Double? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_type", nullable = false, length = 16)
    var fulfillmentType: EatsFulfillmentType = EatsFulfillmentType.DELIVERY,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: GroupEatsOrderStatus = GroupEatsOrderStatus.OPEN,

    // Set only once FINALIZED -- the one real EatsOrder this group cart resolved into.
    @Column(name = "resulting_order_id", length = 64)
    var resultingOrderId: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "finalized_at")
    var finalizedAt: Instant? = null,
) {
    protected constructor() : this(
        id = "", hostUserId = "", restaurantId = "", joinCode = "", deliveryAddress = "",
    )
}

@Entity
@Table(name = "group_eats_order_participants")
class GroupEatsOrderParticipant(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "group_order_id", nullable = false, length = 64)
    val groupOrderId: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "joined_at", nullable = false)
    val joinedAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", groupOrderId = "", userId = "")
}

/**
 * One participant's own line item within a [GroupEatsOrder]'s shared cart, same real
 * shape as [EatsOrderItem]'s snapshot-at-add-time convention. `unitPriceSnapshot` is
 * resolved at add-time from the live menu (base price + any selected option-choice
 * deltas) purely for showing a running per-participant subtotal in the group-cart UI and
 * sizing the post-finalize Dutch-pay request -- it is NEVER the source of truth for the
 * real charge, which [EatsOrderService.placeOrder] re-resolves authoritatively and
 * atomically from the live menu at finalize time, same price-tampering prevention every
 * other real order in this codebase already gets.
 */
@Entity
@Table(name = "group_eats_order_items")
class GroupEatsOrderItem(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "group_order_id", nullable = false, length = 64)
    val groupOrderId: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "product_id", nullable = false, length = 64)
    val productId: String,

    @Column(nullable = false)
    val quantity: Int,

    // Comma-separated MenuOptionChoice ids, same "small bounded list, no join table"
    // convention EatsOrder.excludedRiderUserIds already established -- never queried by
    // itself, only re-resolved by id at both preview-price and finalize time.
    @Column(name = "selected_choice_ids", length = 500)
    val selectedChoiceIds: String? = null,

    @Column(name = "unit_price_snapshot", nullable = false, precision = 18, scale = 2)
    val unitPriceSnapshot: BigDecimal,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(
        id = "", groupOrderId = "", userId = "", productId = "", quantity = 0, unitPriceSnapshot = BigDecimal.ZERO,
    )
}
