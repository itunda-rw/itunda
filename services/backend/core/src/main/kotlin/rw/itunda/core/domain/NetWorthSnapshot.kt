package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

/**
 * Real Toss "자산 변화" (asset change over time) reference (2026-09-12, direct
 * user-supplied Toss total-assets screenshots) -- see OverviewService's own
 * `captureSnapshot`/`getNetWorthHistory` doc comments for why `liquidTotal` is
 * deliberately narrower than `OverviewResult.netWorth`: Toss's own real
 * disclosure on this exact screen ("대출·투자·현금은 포함되지 않아요" -- loans,
 * investments, and cash aren't included) excludes anything whose value can
 * swing for reasons unrelated to genuine saving/spending behavior. itunda's
 * equivalent excludes SAVINGS/INVESTMENT/LOAN the same way `netWorth` itself
 * already separates them out, tracking only real account balances (checking-
 * type wallets: MAIN/PAY/FOREIGN_CURRENCY/etc, matching the reference's own
 * "입출금" bucket) plus rewards points ("포인트").
 *
 * One row per user per day (`NetWorthSnapshotScheduler`, daily) -- `getNetWorthHistory`
 * buckets these by calendar month and reads the latest row in each bucket, the
 * same "snapshot daily, chart the month-end value" shape a real personal-finance
 * product uses. No backfill/reconstruction of pre-2026-09-12 history is attempted
 * -- an honestly sparse chart (as few as one bar) is the real state of a
 * brand-new metric, not a gap to fake data over.
 */
@Entity
@Table(name = "net_worth_snapshots")
class NetWorthSnapshot(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "liquid_total", nullable = false, precision = 18, scale = 2)
    val liquidTotal: BigDecimal,

    @Column(name = "captured_at", nullable = false)
    val capturedAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", liquidTotal = BigDecimal.ZERO)
}
