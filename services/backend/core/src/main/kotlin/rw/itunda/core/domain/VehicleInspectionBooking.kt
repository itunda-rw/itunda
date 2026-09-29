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

// REQUESTED: buyer paid, awaiting the mechanic's real accept. ACCEPTED: the mechanic
// committed to the real scheduled time. COMPLETED: the mechanic real-delivered the
// inspection (findings recorded), paid out minus itunda's fee. CANCELLED: withdrawn by
// the buyer before COMPLETED, or the mechanic declined -- full refund, no fee, same "an
// explicit cancel always refunds" rule `MerchantBooking`/`BookingDeposit` already
// establish.
//
// NO_SHOW added 2026-08-18 -- real bug fix, see VehicleInspectionNoShowScheduler's own
// doc comment. An ACCEPTED booking (the mechanic committed to the slot) whose real
// `scheduledFor` time has passed with neither `completeInspection` nor `cancelInspection`
// ever called was, until this fix, stuck exactly HELD/ACCEPTED forever with one specific
// exploit: `cancelInspection` itself has NO time-based check at all, so a buyer could
// let the mechanic travel to/perform the real inspection, then cancel days or weeks
// later and claw back the full fee -- the mechanic gets nothing for real committed time.
// `MerchantBooking`/`BookingDeposit` already close this exact gap for its own sibling
// 100%-prepay-to-book feature via `BookingNoShowScheduler`'s automatic forfeit-to-
// provider on a past-due CONFIRMED booking; this were never ported to this structurally
// identical escrow. Same real no-show semantics: fee forfeited to the mechanic net of
// itunda's fee, same as a real COMPLETED inspection -- once the scheduled slot's time
// has passed, `cancelInspection`'s explicit-status guard above no longer allows a
// refund.
enum class VehicleInspectionStatus { REQUESTED, ACCEPTED, COMPLETED, CANCELLED, NO_SHOW }

/**
 * Real 당근마켓 중고차 정비소 동행 (used-car mechanic-inspection accompaniment) -- see
 * `VehicleInspectionMechanic.kt`'s own doc comment for the full sourced account. A
 * buyer pays a real inspection fee upfront, 100% held in escrow until the mechanic
 * actually delivers the inspection -- the same real Kakao Hair Shop-sourced
 * "100%-prepay-to-book cuts no-shows" mechanic `BookingDeposit`'s own doc comment
 * already establishes, and the same real escrow-clearing-account shape
 * `MarketplaceEscrow`/`BookingDeposit` already prove out (`vehicle_inspection_holding`),
 * not a fabricated hold invented for this feature.
 *
 * `listingId` ties the inspection to the real specific `Listing` (used car) a buyer is
 * considering -- Karrot's own real feature is scoped to a specific car being evaluated
 * before purchase, not a general "check my car" service unrelated to any listing.
 */
@Entity
@Table(name = "vehicle_inspection_bookings")
class VehicleInspectionBooking(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "listing_id", nullable = false, length = 64)
    val listingId: String,

    @Column(name = "buyer_id", nullable = false, length = 64)
    val buyerId: String,

    @Column(name = "mechanic_id", nullable = false, length = 64)
    val mechanicId: String,

    @Column(nullable = false, precision = 18, scale = 2)
    val fee: BigDecimal,

    @Column(name = "platform_fee", nullable = false, precision = 18, scale = 2)
    val platformFee: BigDecimal,

    @Column(name = "scheduled_for", nullable = false)
    val scheduledFor: Instant,

    @Column(name = "hold_transaction_id", nullable = false, length = 64)
    val holdTransactionId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: VehicleInspectionStatus = VehicleInspectionStatus.REQUESTED,

    @Column(name = "resolution_transaction_id", length = 64)
    var resolutionTransactionId: String? = null,

    // Real buyer-visible inspection report, set once the mechanic marks this real
    // COMPLETED -- the actual real value Karrot's own feature exists to deliver, not
    // just a payment rail.
    @Column(length = 2000)
    var findings: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    // Accept, complete, and a buyer cancel could arrive at nearly the same time.
    // Versioning makes only one state transition win.
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(
        id = "", listingId = "", buyerId = "", mechanicId = "", fee = BigDecimal.ZERO,
        platformFee = BigDecimal.ZERO, scheduledFor = Instant.now(), holdTransactionId = "",
    )
}
