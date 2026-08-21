package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant

/**
 * Real Toss Place-style 자동 적립 (automatic point accrual) -- one balance per real
 * (merchant, customer) pair, closing a genuine gap researched live (2026-08-18):
 * itunda already had [ShoppingCashbackService] (real cashback into a customer's own
 * itunda account, itunda-funded via `rewards_expense`, platform-wide) and
 * [MerchantCouponService] (real merchant-issued one-time discount codes) -- but Toss
 * Place's own real, documented 사장님 (merchant) console feature is neither of those:
 * a real ongoing points BALANCE, earned automatically on every real purchase AT one
 * specific store, funded by THAT store (not itunda), and redeemable only there --
 * closer to a real stamp card than a coupon or a platform-wide cashback credit.
 * Sourced from tossplace.com/product/pos's own real feature list ("고객 자동 적립" —
 * "automatic customer point accrual" — listed alongside coupon distribution and review
 * collection as a real, separate feature).
 *
 * Deliberately data-only, not a real ledger-backed liability account: unlike
 * [ShoppingCashbackService] (real itunda money credited into a real account the instant
 * it's earned) or [rw.itunda.p2p.P2pDelayedTransferService] (real money held in a real
 * clearing account), a loyalty point here never becomes real, spendable itunda-account
 * money on its own -- it only ever reduces a FUTURE real payment's own `chargeAmount`
 * at that same merchant, the exact moment real money moves, the same way
 * [MerchantCoupon]'s own discount already works. This is the honest, minimal v1: no
 * new `LedgerAccountType` needed, no risk of a customer ever being able to "cash out"
 * points as real transferable money.
 *
 * `@Version`-guarded: two real concurrent payments by the same customer at the same
 * merchant (a genuinely reachable race, not theoretical -- see
 * `MerchantLoyaltyPointsService.accrue`'s own doc comment) must never lose one side's
 * update to the other, the same real "concurrent balance mutation" discipline this
 * codebase's own concurrency-audit precedent already requires for every new
 * accrue-or-redeem balance.
 */
@Entity
@Table(
    name = "merchant_loyalty_accounts",
    uniqueConstraints = [UniqueConstraint(columnNames = ["merchant_id", "customer_id"])],
)
class MerchantLoyaltyAccount(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "merchant_id", nullable = false, length = 64)
    val merchantId: String,

    @Column(name = "customer_id", nullable = false, length = 64)
    val customerId: String,

    @Column(name = "point_balance", nullable = false, precision = 18, scale = 2)
    var pointBalance: BigDecimal = BigDecimal.ZERO,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(id = "", merchantId = "", customerId = "")
}
