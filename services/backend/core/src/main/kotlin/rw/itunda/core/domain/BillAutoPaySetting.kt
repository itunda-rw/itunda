package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

/**
 * Real Kakao Pay 자동납부 (automatic bill payment) -- Kakao Pay's own real feature lets
 * a user register a recurring bill once and have it paid automatically each cycle,
 * "so customers never miss a bill." One row per (user, provider) real pair --
 * `uq_bill_auto_pay_user_provider` -- since re-registering the same provider replaces
 * the prior setting rather than creating a second, conflicting auto-pay for it.
 *
 * `maxAmount` is a real, sourced safety cap (Kakao Pay's own auto-pay lets a user
 * bound the maximum they'll ever be auto-charged, so a real unusually large bill still
 * requires a manual review rather than silently draining the account) -- a due
 * [rw.itunda.bills.BillsCatalog] `PendingBill` whose real amount exceeds this cap is
 * honestly skipped, never auto-paid anyway.
 *
 * `lastPaidBillId` is this codebase's own real once-per-bill guard: `BillsCatalog
 * .pendingBills` is a small, static, in-memory demo catalog (same "demo-speed, real
 * money movement" convention `StockCatalog`'s own doc comment already establishes for
 * an unrelated feature) with no real per-user "already paid" state of its own -- this
 * field is what stops the scheduler from re-paying the identical bill id on every poll.
 *
 * `lastLowBalanceWarnedBillId` (Section 178) is the identical once-per-bill dedup guard
 * for `BillAutoPayProcessor`'s real proactive low-balance warning -- see that class's
 * own doc comment for the sourced Kakao Bank feature this ports. Same reasoning as
 * `lastPaidBillId`: without a stored "already warned about this exact bill" marker, a
 * 60-second poll would re-send the identical push every cycle for as long as the real
 * balance stayed insufficient.
 */
@Entity
@Table(name = "bill_auto_pay_settings")
class BillAutoPaySetting(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "provider_id", nullable = false, length = 64)
    val providerId: String,

    @Column(name = "account_number", nullable = false, length = 64)
    var accountNumber: String,

    @Column(name = "max_amount", nullable = false, precision = 18, scale = 2)
    var maxAmount: BigDecimal,

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(name = "last_paid_bill_id", length = 64)
    var lastPaidBillId: String? = null,

    @Column(name = "last_low_balance_warned_bill_id", length = 64)
    var lastLowBalanceWarnedBillId: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", providerId = "", accountNumber = "", maxAmount = BigDecimal.ZERO)
}
