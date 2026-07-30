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

// HELD: paid, held in booking_deposit_holding. RELEASED: the merchant rendered the
// service (MerchantBookingService.markCompleted), paid out minus itunda's fee. REFUNDED:
// the booking was cancelled or declined before it happened -- full refund, no fee, same
// "an explicit cancel/decline always refunds" rule MerchantBooking.kt's own doc comment
// names. FORFEITED: the real no-show case this whole feature exists for -- a CONFIRMED
// booking's scheduled time passed with no explicit action from either side
// (BookingNoShowScheduler) -- paid to the merchant minus itunda's fee, same as RELEASED.
enum class BookingDepositStatus { HELD, RELEASED, REFUNDED, FORFEITED }

/**
 * Real Kakao Hair Shop-style 100%-prepay-to-book -- see `docs/DESIGN_REFERENCES.md`'s own
 * sourced line: "Kakao Hair Shop's real, sourced innovation is a 100%-prepay-to-book
 * mechanic that cut no-shows from ~20% to under 0.5%." `MerchantBooking` was built with
 * no payment integration at all by explicit design (its own doc comment: "no research
 * source described a mandatory booking deposit, so this doesn't invent one") -- this is
 * the honest, sourced extension: opt-in per service (`MerchantProduct.requiresPrepay`),
 * not a universal requirement itunda invented on its own.
 *
 * Modeled directly on `MarketplaceEscrow`'s already-proven pay/hold/release/refund shape
 * (same escrow-clearing-account pattern, same fee-on-release-not-on-refund rule) rather
 * than inventing a new one. The one deliberate difference: `MarketplaceEscrow` has a
 * human-reviewed DISPUTED state because "did the item arrive as described" genuinely
 * needs a person to judge; a booking no-show has no such ambiguity -- either the
 * merchant marked it COMPLETED (service rendered) or the slot's time passed while still
 * CONFIRMED (nobody showed), so FORFEITED is fully automated
 * (`BookingNoShowScheduler`), not admin-reviewed.
 */
@Entity
@Table(name = "merchant_booking_deposits")
class BookingDeposit(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "booking_id", nullable = false, unique = true, length = 64)
    val bookingId: String,

    @Column(name = "merchant_id", nullable = false, length = 64)
    val merchantId: String,

    @Column(name = "customer_id", nullable = false, length = 64)
    val customerId: String,

    @Column(nullable = false, precision = 18, scale = 2)
    val amount: BigDecimal,

    @Column(nullable = false, precision = 18, scale = 2)
    val fee: BigDecimal,

    @Column(name = "hold_transaction_id", nullable = false, length = 64)
    val holdTransactionId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: BookingDepositStatus = BookingDepositStatus.HELD,

    @Column(name = "resolution_transaction_id", length = 64)
    var resolutionTransactionId: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    // Completion, cancellation, and scheduled no-show handling may all settle the
    // held deposit. Only one terminal settlement may commit.
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(
        id = "", bookingId = "", merchantId = "", customerId = "", amount = BigDecimal.ZERO,
        fee = BigDecimal.ZERO, holdTransactionId = "",
    )
}
