package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

enum class LedgerDirection { DEBIT, CREDIT }

enum class LedgerAccountType {
    WALLET, FEE_REVENUE, RAIL_SUSPENSE, LOAN_PAYABLE, SECURITIES_SUSPENSE,
    SAVINGS_GOAL_PAYABLE, INTEREST_EXPENSE, INSURANCE_PREMIUM_REVENUE, REWARDS_EXPENSE,
    INSURANCE_CLAIMS_EXPENSE, EATS_DELIVERY_HOLDING, GIFT_HOLDING,
    AGENT_CASH,
    CASH_VAULT,
    // Real foreign-currency conversion clearing (2026-07-25) -- see
    // ForeignCurrencyWalletService's own doc comment for why a conversion is two
    // separate, each-individually-balanced single-currency ledger transactions rather
    // than one cross-currency one (postLedgerTransaction enforces raw debits==credits
    // per call, with no per-currency dimension). itunda's own real counterparty position
    // (the currency it's holding/owed on the other side of every user's foreign-currency
    // balance), same real-counterparty shape CASH_VAULT/AGENT_CASH already establish for
    // cash-in/out -- one FX_CLEARING account per currency (accountId e.g.
    // "fx_clearing_usd"), never one shared account mixing currencies.
    FX_CLEARING,
    // Real Marketplace escrow holding (2026-07-25) -- closes a real trust gap Naver
    // Cafe's own "안전거래" (Safe Trade) product exists specifically to solve: itunda's
    // Marketplace has always settled buyer/seller in person, off-platform, with zero
    // protection against a no-show or a not-as-described item. Same real
    // escrow-clearing-account shape EATS_DELIVERY_HOLDING/GIFT_HOLDING already
    // establish -- the buyer's money already left their wallet, it just hasn't reached
    // its final recipient yet. See MarketplaceEscrow.kt's own doc comment.
    MARKETPLACE_ESCROW_HOLDING,
    // Real Kakao Hair Shop-style 100%-prepay-to-book holding (2026-07-25) -- see
    // BookingDeposit.kt's own doc comment. Same escrow-clearing-account shape
    // MARKETPLACE_ESCROW_HOLDING already establishes: a customer's real money already
    // left their wallet at booking time, it just hasn't reached the merchant (or been
    // refunded/forfeited) yet.
    BOOKING_DEPOSIT_HOLDING,
    // Real Kakao T-style ride-hailing fare holding (2026-07-26) -- see RideTrip.kt's own
    // doc comment. Same real escrow-clearing-account shape EATS_DELIVERY_HOLDING already
    // establishes: the passenger's real fare leaves their wallet at request time, held
    // until the trip completes (or refunded if cancelled before a driver is assigned).
    RIDE_HOLDING,
    // Real KakaoTalk Emoticon Store revenue (2026-07-26) -- see EmoticonService's own
    // doc comment. A direct sale, not an escrow hold: unlike GIFT_HOLDING/RIDE_HOLDING
    // (money in flight to another real user, pending an event), a purchased emoticon
    // pack is itunda's own product -- the same real "itunda earns this outright"
    // revenue-account shape FEE_REVENUE already establishes, just its own dedicated
    // account so emoticon sales can be reconciled independently of transaction fees.
    EMOTICON_REVENUE,
}

/**
 * Append-only double-entry ledger row. Mirrors backend/src/types/index.ts LedgerEntry
 * and the invariant enforced by backend/src/services/ledger.ts: every transactionId's
 * entries must sum to zero (debits == credits) before any of them are persisted.
 */
@Entity
@Table(name = "ledger_entries")
class LedgerEntry(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "transaction_id", nullable = false, length = 64)
    val transactionId: String,

    @Column(name = "account_id", nullable = false, length = 64)
    val accountId: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 32)
    val accountType: LedgerAccountType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    val direction: LedgerDirection,

    @Column(nullable = false, precision = 18, scale = 2)
    val amount: BigDecimal,

    @Column(nullable = false, length = 8)
    val currency: String,

    @Column(name = "balance_after", nullable = false, precision = 18, scale = 2)
    val balanceAfter: BigDecimal,

    @Column(nullable = false, length = 255)
    val memo: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(
        id = "", transactionId = "", accountId = "", accountType = LedgerAccountType.WALLET,
        direction = LedgerDirection.DEBIT, amount = BigDecimal.ZERO, currency = "RWF",
        balanceAfter = BigDecimal.ZERO, memo = "",
    )
}
