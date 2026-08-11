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
    // Real KakaoTalk 선물하기 기프티콘 (mobile gift voucher) holding (2026-07-26) -- see
    // GiftVoucher.kt's own doc comment. Same real escrow-clearing-account shape
    // GIFT_HOLDING already establishes for money gifts: the purchaser's real money
    // already left their wallet, it just hasn't reached the merchant (redemption) or
    // been refunded (expiry) yet.
    GIFT_VOUCHER_HOLDING,
    // Real Toss Bank/KakaoBank 마이너스통장 (overdraft/revolving line-of-credit) interest
    // income (2026-07-27) -- see OverdraftAccount.kt's own doc comment. A real, direct
    // "itunda earns this outright" revenue account, the same shape FEE_REVENUE/
    // EMOTICON_REVENUE already establish -- interest income is conceptually distinct
    // from a transaction fee, so this gets its own dedicated account rather than being
    // folded into FEE_REVENUE and muddying that account's own real reconciliation.
    INTEREST_INCOME,
    // Real MTN MoMo-style agent cash-in/cash-out commission expense (2026-07-27) -- see
    // AgentCommissionSchedule.kt's own doc comment. A real "itunda pays this out"
    // expense account, the same shape REWARDS_EXPENSE/INTEREST_EXPENSE already
    // establish, distinct from AGENT_CASH (the agent's own physical float/till).
    AGENT_COMMISSION_EXPENSE,
    // Real Toss Bank 체크카드 (check/debit card) purchase expense (2026-07-31) -- see
    // DebitCard.kt's own doc comment. A real "itunda pays this out" expense account,
    // the same shape REWARDS_EXPENSE/AGENT_COMMISSION_EXPENSE already establish: a card
    // purchase's real counterparty is an external, unmodeled merchant/POS, not another
    // itunda account.
    CARD_SPEND_EXPENSE,
    // Real Naver Pay/Kakao Pay/Toss 후불결제 (postpaid/BNPL credit line, 2026-07-31) --
    // see PostpaidCreditLine.kt's own doc comment. A real "owed by the user" receivable,
    // the same shape LOAN_PAYABLE already establishes -- its own dedicated account so
    // postpaid-credit exposure can be reconciled independently of term-loan/overdraft
    // exposure, matching how every other new money-movement product in this ledger
    // (EATS_DELIVERY_HOLDING, RIDE_HOLDING, CARD_SPEND_EXPENSE) gets its own account
    // rather than sharing LOAN_PAYABLE the way OverdraftAccount deliberately does.
    POSTPAID_CREDIT_PAYABLE,
    // Real 당근마켓 중고차 정비소 동행 (used-car mechanic-inspection accompaniment,
    // 2026-07-31) -- see VehicleInspectionBooking.kt's own doc comment. Same real
    // escrow-clearing-account shape MARKETPLACE_ESCROW_HOLDING/BOOKING_DEPOSIT_HOLDING
    // already establish: a buyer's real inspection fee already left their wallet, it
    // just hasn't reached the mechanic (or been refunded) yet.
    VEHICLE_INSPECTION_HOLDING,
    // Real Kakao T 대리운전 (designated driver) fare holding -- see
    // DesignatedDriverTrip.kt's own doc comment. Same real escrow-clearing-account shape
    // RIDE_HOLDING already establishes for ride-hailing fares: the customer's real fare
    // already left their wallet at request time, held until the trip completes (or
    // refunded if cancelled before a driver is assigned) -- its own dedicated account
    // so designated-driver volume can be reconciled independently of ride-hailing
    // volume, matching how every distinct trip/booking product in this ledger already
    // gets its own account rather than sharing RIDE_HOLDING.
    DESIGNATED_DRIVER_HOLDING,
    // Real Ejo Heza ya Moto-style premium savings fund (2026-08-02) -- see
    // InsurancePremiumFund.kt's own doc comment. A real "owed back to the user until it
    // either pays the premium or gets refunded" liability, the same shape
    // SAVINGS_GOAL_PAYABLE already establishes for savings-goal deposits -- its own
    // dedicated account so premium-fund float can be reconciled independently of
    // ordinary savings-goal float.
    INSURANCE_PREMIUM_FUND_PAYABLE,
    // Real itunda Deposit Protection Fund (2026-08-11) -- see DepositProtectionFund.kt's
    // own doc comment. itunda has no real BNR banking license, so unlike a real bank's
    // government-backed deposit insurance, this is itunda's own internal reserve,
    // honestly disclosed as a simulation rather than a real regulatory scheme. Real
    // double-entry pair with DEPOSIT_PROTECTION_EXPENSE below: itunda periodically sets
    // aside a real percentage of covered deposits into this reserve, the same
    // expense-funds-a-reserve shape AGENT_COMMISSION_EXPENSE/INTEREST_INCOME already
    // establish for "itunda's own money moving between its own accounts."
    DEPOSIT_PROTECTION_RESERVE,
    DEPOSIT_PROTECTION_EXPENSE,
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
