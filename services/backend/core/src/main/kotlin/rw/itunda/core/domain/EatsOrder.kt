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

// Forward-only, same discipline as commerce's OrderStatus (see Order.kt). The first four
// states are restaurant-driven (PLACED -> ACCEPTED -> PREPARING -> READY_FOR_PICKUP), the
// next three are rider-driven (a rider claims a READY_FOR_PICKUP order, moving it to
// RIDER_ASSIGNED, then PICKED_UP, then DELIVERED -- the transition that triggers the real
// delivery-fee payout out of `eats_delivery_holding` into the rider's own wallet). A real
// CANCELLED terminal state (2026-07-18) is reachable only from PLACED -- before the
// restaurant has started real fulfillment work and before any rider is involved, the
// safest and simplest real scope. See EatsOrderService.cancelOrder's own doc comment for
// the reversing-ledger-entry technique this reuses from SupportService.reverseTransaction.
enum class EatsOrderStatus { PLACED, ACCEPTED, PREPARING, READY_FOR_PICKUP, RIDER_ASSIGNED, PICKED_UP, DELIVERED, CANCELLED }

// Real Baemin-style 포장주문 (Pickup) order type (2026-07-26) -- see
// EatsOrderService.placeOrder's own doc comment for the full account. DELIVERY is the
// default, preserving every existing/legacy order's exact current behavior unchanged.
enum class EatsFulfillmentType { DELIVERY, PICKUP }

/**
 * A real Coupang Eats-style food order -- built on top of the same real `Merchant`/
 * `MerchantProduct` catalog Commerce/Toss Shopping/Toss Place already reuse (a restaurant
 * IS a `Merchant`, a menu item IS a `MerchantProduct`; no new catalog system invented),
 * but kept as its own entity/table rather than extending commerce's `Order` -- the two
 * have genuinely different lifecycles (Eats needs a rider assignment and a longer,
 * delivery-specific status chain) and this session's own established discipline is to
 * favor purely additive new entities over modifying already-tested money-movement code.
 *
 * `totalAmount` = `itemsSubtotal` + `deliveryFee`, charged to the buyer in one atomic
 * ledger post at placement time. `platformFee` (itunda's cut, same 1.5% rate reasoning
 * `OrderService.feeRate`/`MerchantService.feeRate` already use) comes out of the
 * restaurant's share of `itemsSubtotal`. `deliveryFee` is real but flat (no real
 * distance/geo data exists anywhere in this backend, the same honest simplification
 * already named for the neighborhood marketplace) -- it's held in the real
 * `eats_delivery_holding` clearing account from placement until a real rider completes
 * the delivery, at which point `EatsOrderService` posts a second real ledger transaction
 * paying it straight into that rider's own wallet.
 */
@Entity
@Table(name = "eats_orders")
class EatsOrder(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "buyer_id", nullable = false, length = 64)
    val buyerId: String,

    @Column(name = "restaurant_id", nullable = false, length = 64)
    val restaurantId: String,

    @Column(name = "rider_id", length = 64)
    var riderId: String? = null,

    @Column(name = "delivery_address", nullable = false, length = 500)
    val deliveryAddress: String,

    @Column(name = "items_subtotal", nullable = false, precision = 18, scale = 2)
    val itemsSubtotal: BigDecimal,

    @Column(name = "delivery_fee", nullable = false, precision = 18, scale = 2)
    val deliveryFee: BigDecimal,

    @Column(name = "platform_fee", nullable = false, precision = 18, scale = 2)
    val platformFee: BigDecimal,

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    val totalAmount: BigDecimal,

    @Column(name = "transaction_id", nullable = false, length = 64)
    val transactionId: String,

    @Column(name = "delivery_payout_transaction_id", length = 64)
    var deliveryPayoutTransactionId: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: EatsOrderStatus = EatsOrderStatus.PLACED,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "refund_transaction_id", length = 64)
    var refundTransactionId: String? = null,

    // Real delivery coordinates + real Haversine distance (2026-07-18), replacing this
    // class's own doc comment's prior "no real distance/geo data exists" limitation. Both
    // nullable: a buyer who doesn't submit coordinates (or a restaurant with no location
    // set) falls back to the pre-existing flat delivery fee, never a fabricated distance.
    // See EatsOrderService.computeDeliveryFee.
    @Column(name = "delivery_latitude")
    val deliveryLatitude: Double? = null,

    @Column(name = "delivery_longitude")
    val deliveryLongitude: Double? = null,

    @Column(name = "distance_km", precision = 8, scale = 3)
    val distanceKm: BigDecimal? = null,

    // Real free-text delivery instructions (2026-07-19) -- e.g. "Leave at the gate",
    // "Call on arrival". Set once at placement time only (matches deliveryAddress's own
    // immutability -- Coupang Eats itself doesn't let a buyer edit notes after
    // checkout), surfaced to the restaurant and the assigned rider.
    @Column(name = "delivery_notes", length = 500)
    val deliveryNotes: String? = null,

    // Real automatic dispatch (2026-07-20) -- exclusive right-of-refusal for one
    // specific rider at a time, closing the "full automatic assignment" gap on top of
    // the existing open-browse/first-claim-wins model (which stays as the real,
    // unchanged fallback once every real candidate has been tried or declined). See
    // EatsOrderService.dispatchToNextCandidate's own doc comment for the full account.
    @Column(name = "offered_rider_id", length = 64)
    var offeredRiderId: String? = null,

    @Column(name = "offer_expires_at")
    var offerExpiresAt: Instant? = null,

    // Real, small, bounded comma-separated list of rider user ids already offered this
    // delivery and declined/timed out -- never re-offered the same delivery twice. A
    // real join table would be over-engineering for a list this size (bounded by
    // NEAREST_RIDERS_TO_NOTIFY, currently 5), same "honest choice at this system's real
    // data scale" discipline SavingsService.getGoalsDueForAutoContribution's own doc
    // comment already established for a different small-scale simplification.
    @Column(name = "excluded_rider_user_ids", length = 500)
    var excludedRiderUserIds: String? = null,

    // Real Baemin-style 포장주문 (Pickup) order type (2026-07-26) -- a buyer-facing
    // fulfillment choice, distinct from READY_FOR_PICKUP (a restaurant-fulfillment
    // status meaning "food is ready for a RIDER to collect"). A PICKUP order always
    // has a zero deliveryFee and never gets a rider assigned -- see
    // EatsOrderService.completePickup's own doc comment for its real terminal edge.
    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_type", nullable = false, length = 16)
    var fulfillmentType: EatsFulfillmentType = EatsFulfillmentType.DELIVERY,

    // Real 배달의민족 예약주문 (scheduled ordering) (2026-07-26) -- see
    // EatsOrderService.placeOrder's own doc comment for the real window/opt-in rules.
    // Null (the default) means ASAP -- every existing/legacy order's exact current
    // behavior, completely unchanged.
    @Column(name = "scheduled_for")
    val scheduledFor: Instant? = null,

    // Real Baemin-style tiered order-amount promotion (2026-08-16, "가게배달" fee/
    // promotion restructuring, April 2026) -- see EatsPromotionCalculator's own doc
    // comment. itunda-funded, not restaurant-funded: netToRestaurant is computed from
    // itemsSubtotal/platformFee exactly as before and never reduced by this discount,
    // matching Baemin's own real "platform pays, not the restaurant" mechanic. Zero for
    // every order below the lowest real tier -- the common case, not a fabricated
    // always-present discount.
    @Column(name = "promotion_discount", nullable = false, precision = 18, scale = 2)
    val promotionDiscount: BigDecimal = BigDecimal.ZERO,

    // Real Baemin/Coupang Eats/Uber Eats-style 안심배달 (safe/contactless delivery)
    // proof photo (2026-08-17, migration V265) -- the assigned rider optionally attaches
    // a photo of the delivered order at the customer's door when they mark the order
    // DELIVERED (see EatsOrderService.updateRiderStatus's own doc comment). Nullable:
    // most deliveries are still real hand-to-hand and never submit one, matching the
    // real product's own optional, not-mandatory convention -- this is evidentiary
    // trust, never a gate on completing the delivery.
    @Column(name = "delivery_proof_photo_url", length = 500)
    var deliveryProofPhotoUrl: String? = null,

    // Real Baemin 포장할인 (pickup discount) (2026-08-17, migration V267) -- see
    // Merchant.pickupDiscountPercent's own doc comment. RESTAURANT-funded, unlike
    // promotionDiscount above (itunda-funded): netToRestaurant IS reduced by exactly
    // this amount when it's non-zero. Zero for every DELIVERY order and every PICKUP
    // order at a restaurant that hasn't opted in -- the common case, not a fabricated
    // always-present discount, same convention promotionDiscount already establishes.
    @Column(name = "pickup_discount", nullable = false, precision = 18, scale = 2)
    val pickupDiscount: BigDecimal = BigDecimal.ZERO,

    // Real Uber Eats-style post-delivery tip (2026-08-17, migration V269,
    // help.uber.com/en/ubereats "Add or change tip amount for a past order") -- see
    // EatsOrderService.tipRider's own doc comment. Null until a real tip is given
    // (DELIVERY orders with an assigned rider only -- a PICKUP order has no rider to
    // tip), same nullable-until-real-event convention deliveryProofPhotoUrl above
    // already establishes.
    @Column(name = "tip_amount", precision = 18, scale = 2)
    var tipAmount: BigDecimal? = null,

    @Column(name = "tip_transaction_id", length = 64)
    var tipTransactionId: String? = null,

    // Restaurant, rider, scheduler, and cancellation actions advance the same order
    // through different request paths.  Protect the row so a concurrent terminal
    // transition cannot post a duplicate delivery payout or refund.
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(
        id = "", buyerId = "", restaurantId = "", deliveryAddress = "", itemsSubtotal = BigDecimal.ZERO,
        deliveryFee = BigDecimal.ZERO, platformFee = BigDecimal.ZERO, totalAmount = BigDecimal.ZERO, transactionId = "",
    )
}
