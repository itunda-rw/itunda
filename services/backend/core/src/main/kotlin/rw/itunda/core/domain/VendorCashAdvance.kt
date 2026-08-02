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

enum class VendorCashAdvanceStatus { REQUESTED, DISBURSED, REPAID }

/**
 * Real Isoko ("market" in Kinyarwanda) Vendor Cash Advance -- see
 * `VendorCashAdvanceService`'s own doc comment for the full sourced account and the
 * three honest v1 limitations. This is the "사장님 대출 (Boss Loans) as a
 * business-specific lending product" follow-up gap `MerchantBusinessAccountService`'s
 * own doc comment names as unshipped.
 *
 * Genuinely distinct from every other lending row in this codebase: `remainingOwed`
 * is auto-collected as a variable SHARE of a merchant's real observed daily
 * settlement inflow (via `VendorCashAdvanceCollectionScheduler.runDailyCollection`),
 * never a fixed installment the borrower initiates -- see `VupLoan`/`StudentLoan`/
 * `CooperativeService`'s harvest advance for the fixed/lump-sum/self-declared
 * alternatives this is deliberately NOT.
 *
 * `@Version` from day one -- the daily collection sweep (`runDailyCollection`) and a
 * manual `repayEarly` call both mutate `remainingOwed` on the SAME row, a real
 * concurrent check-then-act shape (same reasoning `VupLoan`/`StudentLoan`/
 * `MotoOwnershipPlan` already needed it for): without it, a collection sweep running
 * at the exact moment a merchant manually repays could silently lose one of the two
 * writes to the other.
 */
@Entity
@Table(name = "vendor_cash_advances")
class VendorCashAdvance(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "merchant_id", nullable = false, length = 64)
    val merchantId: String,

    @Column(name = "principal_amount", nullable = false, precision = 18, scale = 2)
    val principalAmount: BigDecimal,

    @Column(name = "fee_amount", nullable = false, precision = 18, scale = 2)
    val feeAmount: BigDecimal,

    // Computed once at creation (principalAmount + feeAmount), never recomputed --
    // the fixed real total the merchant owes back, regardless of how remainingOwed
    // moves afterward.
    @Column(name = "total_owed", nullable = false, precision = 18, scale = 2)
    val totalOwed: BigDecimal,

    @Column(name = "remaining_owed", nullable = false, precision = 18, scale = 2)
    var remainingOwed: BigDecimal,

    @Column(name = "collection_rate_percent", nullable = false)
    val collectionRatePercent: Double,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: VendorCashAdvanceStatus = VendorCashAdvanceStatus.REQUESTED,

    @Column(name = "requested_at", nullable = false)
    val requestedAt: Instant = Instant.now(),

    @Column(name = "disbursed_at")
    var disbursedAt: Instant? = null,

    @Column(name = "repaid_at")
    var repaidAt: Instant? = null,

    @Column(name = "last_collection_at")
    var lastCollectionAt: Instant? = null,

    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(
        id = "", merchantId = "", principalAmount = BigDecimal.ZERO, feeAmount = BigDecimal.ZERO,
        totalOwed = BigDecimal.ZERO, remainingOwed = BigDecimal.ZERO, collectionRatePercent = 0.0,
    )
}
