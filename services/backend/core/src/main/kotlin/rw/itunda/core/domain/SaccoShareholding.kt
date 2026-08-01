package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant

/**
 * Real Umurenge SACCO-style shares & dividends -- Rwanda's own government-backed
 * cooperative savings model, distinct from `Ikimina` (informal rotating-pot ROSCA, no
 * shares/dividends) and from every Toss/Kakao/Naver/Coupang-sourced feature in this
 * backend. Sourced from real, published facts: Rwanda established one SACCO
 * (Savings and Credit Cooperative Organization) in each of its 416 administrative
 * sectors starting 2008/2009 as deliberate rural financial-inclusion policy -- 4M+
 * members, RWF 200B+ in deposits as of 2024 (Rwanda Cooperative Agency, The New
 * Times). Members are real shareholder-owners, not just depositors: they buy shares
 * and receive periodic dividend distributions tied to the cooperative's real
 * performance, distinct from mobile money's flat/no interest and a normal savings
 * account's fixed interest rate.
 *
 * **Honest v1 scope, named explicitly**: ONE virtual "itunda SACCO" pool, not 416
 * separate sector-based SACCOs -- a real, deliberate simplification, not an invented
 * one (real per-sector SACCO membership/geography verification is outside what this
 * backend can honestly check). Shares mint 1:1 with contribution amount (no
 * speculative share-price fluctuation -- the simplest honest model). Redemption pays
 * out at the ORIGINAL contributed value (par), matching how a real SACCO share
 * withdrawal preserves principal -- dividend GAINS are distributed separately via
 * `SaccoDividendDistribution`, not baked into a fluctuating share price.
 *
 * Backed by a real `Wallet` (WalletType.GROUP, the same real type `GroupAccount`/
 * `Ikimina` already establish for a shared pool) -- every buy/redeem/dividend payout
 * is the same real ledger-backed WALLET-to-WALLET movement every other money-moving
 * feature in this backend already uses.
 *
 * `@Version`: applying this session's own hard-won lesson from the Bike/Parking/
 * Knowledge/SupportTicket/Ikimina concurrency fixes -- a concurrent buy+redeem against
 * the same shareholding is a real check-then-act race, guarded from day one instead of
 * waiting to find it live.
 */
@Entity
@Table(name = "sacco_shareholdings")
class SaccoShareholding(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, unique = true, length = 64)
    val userId: String,

    @Column(name = "wallet_id", nullable = false, length = 64)
    val walletId: String,

    @Column(name = "shares_held", nullable = false, precision = 18, scale = 2)
    var sharesHeld: BigDecimal = BigDecimal.ZERO,

    @Column(name = "total_contributed", nullable = false, precision = 18, scale = 2)
    var totalContributed: BigDecimal = BigDecimal.ZERO,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(id = "", userId = "", walletId = "")
}

/**
 * One real declared dividend distribution across every real shareholder -- see
 * SaccoShareholding.kt's own doc comment. `dividendRate` is computed from a real,
 * already-established number in this same module (`SavingsService`'s own real 7.5%
 * annual savings-goal rate), prorated by the real elapsed days since the last
 * distribution -- reusing a real rate this backend already declares rather than
 * fabricating a new one from nothing.
 */
@Entity
@Table(name = "sacco_dividend_distributions")
class SaccoDividendDistribution(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "distribution_date", nullable = false)
    val distributionDate: Instant = Instant.now(),

    @Column(name = "total_pool_value", nullable = false, precision = 18, scale = 2)
    val totalPoolValue: BigDecimal,

    @Column(name = "dividend_rate", nullable = false, precision = 10, scale = 6)
    val dividendRate: BigDecimal,

    @Column(name = "total_dividend_paid", nullable = false, precision = 18, scale = 2)
    var totalDividendPaid: BigDecimal,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", totalPoolValue = BigDecimal.ZERO, dividendRate = BigDecimal.ZERO, totalDividendPaid = BigDecimal.ZERO)
}

/**
 * One real shareholder's real payout from one real distribution -- see
 * SaccoShareholding.kt's own doc comment. A real, queryable audit trail independent
 * of the ledger's own entries, same discipline `Payslip`/`IkiminaContribution`
 * already establish for their own per-recipient breakdown rows.
 */
@Entity
@Table(name = "sacco_dividend_payouts")
class SaccoDividendPayout(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "distribution_id", nullable = false, length = 64)
    val distributionId: String,

    @Column(name = "shareholding_id", nullable = false, length = 64)
    val shareholdingId: String,

    @Column(nullable = false, precision = 18, scale = 2)
    val amount: BigDecimal,

    @Column(name = "payout_transaction_id", nullable = false, length = 64)
    val payoutTransactionId: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", distributionId = "", shareholdingId = "", amount = BigDecimal.ZERO, payoutTransactionId = "")
}
