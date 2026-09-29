package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

/**
 * Non-account ledger clearing accounts (fee income, money in flight to external rails/
 * custody/lending) — mirrors the `ledgerAccounts` map in backend/src/services/ledger.ts.
 * Kept separate from `accounts` so they never show up as a customer-facing balance.
 */
@Entity
@Table(name = "ledger_accounts")
class LedgerAccount(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false, precision = 18, scale = 2)
    var balance: BigDecimal = BigDecimal.ZERO,
) {
    protected constructor() : this(id = "", name = "")

    companion object {
        val SEED_IDS = listOf(
            "fee_revenue" to "Fee Revenue",
            "rail_suspense" to "Rail Settlement Suspense",
            "loan_payable" to "Loan Principal Payable",
            "securities_suspense" to "Securities Custody Suspense",
            "savings_goal_payable" to "Savings Goal Payable",
            "interest_expense" to "Interest Expense",
            "insurance_premium_revenue" to "Insurance Premium Revenue",
            "rewards_expense" to "Rewards Expense",
            "insurance_claims_expense" to "Insurance Claims Expense",
            // Used by VUP and cooperative loan write-off flows (bad-debt expense). Seeded so
            // the first real write-off cannot fail with an unknown ledger account.
            "bad_debt_expense" to "Bad Debt Expense",
            // A dedicated clearing account for MerchantService.chargeCard's demo card
            // authorization flow (2026-07-17) -- deliberately separate from the shared
            // "rail_suspense" account (bills/airtime/external transfers) so card
            // settlement can be reconciled independently, matching how real accounting
            // systems use a granular suspense account per settlement channel rather
            // than one shared bucket.
            "card_network_clearing" to "Card Network Settlement Clearing",
            // Holds a buyer's real delivery fee from the moment a Coupang Eats-style
            // order is placed until it's real-paid out to whichever rider completes the
            // delivery (rw.itunda.eats.EatsOrderService, 2026-07-18) -- a real, standard
            // escrow-style clearing account (the buyer's money already left their
            // account, it just hasn't reached its final recipient yet), not a fake holding
            // pattern invented for this feature.
            "eats_delivery_holding" to "Eats Delivery Fee Holding",
            // Holds a real KakaoTalk-style gift's money from the moment it's sent until
            // the recipient explicitly opens/claims it, or it's auto-refunded to the
            // sender after Gift.EXPIRY (rw.itunda.gift.GiftService, 2026-07-20) -- same
            // real escrow-clearing-account shape as eats_delivery_holding above.
            "gift_holding" to "Gift Holding",
            "cash_vault" to "Itunda Cash Vault",
            // Real foreign-currency conversion clearing, one per real supported
            // currency (2026-07-25) -- see ForeignCurrencyAccountService's own doc
            // comment for why each conversion is two separate single-currency ledger
            // transactions rather than one cross-currency one, and why these are
            // per-currency accounts rather than one shared "fx_clearing" bucket.
            "fx_clearing_rwf" to "FX Clearing (RWF)",
            "fx_clearing_usd" to "FX Clearing (USD)",
            "fx_clearing_eur" to "FX Clearing (EUR)",
            "fx_clearing_gbp" to "FX Clearing (GBP)",
            // Real Marketplace escrow holding (2026-07-25) -- see
            // MarketplaceEscrow.kt's own doc comment.
            "marketplace_escrow_holding" to "Marketplace Escrow Holding",
            // Real Kakao Hair Shop-style prepay-to-book holding (2026-07-25) -- see
            // BookingDeposit.kt's own doc comment.
            "booking_deposit_holding" to "Booking Deposit Escrow Holding",
            // Real Kakao T-style ride-hailing fare holding (2026-07-26) -- see
            // RideTrip.kt's own doc comment.
            "ride_holding" to "Ride Fare Holding",
            // Real KakaoTalk Emoticon Store revenue (2026-07-26) -- see
            // EmoticonService.kt's own doc comment.
            "emoticon_revenue" to "Emoticon Store Revenue",
            // Real KakaoTalk 선물하기 기프티콘 (mobile gift voucher) holding (2026-07-26) --
            // see GiftVoucher.kt's own doc comment.
            "gift_voucher_holding" to "Gift Voucher Escrow Holding",
            // Real Toss Bank/KakaoBank 마이너스통장 (overdraft) interest income
            // (2026-07-27) -- see OverdraftAccount.kt's own doc comment. Real bug found
            // live: this row was missed when INTEREST_INCOME was added, so the very
            // first real accrual would have real-500'd with "Unknown ledger account" --
            // caught only because a SEPARATE feature (agent commission, added the same
            // day) exercised this exact same class of bug live first.
            "interest_income" to "Overdraft Interest Income",
            // Real MTN MoMo-style agent cash-in/cash-out commission expense
            // (2026-07-27) -- see AgentCommissionSchedule.kt's own doc comment. The real
            // bug that surfaced this row was missing: a real live cash-in 500'd with
            // "Unknown ledger account agent_commission_expense" the first time this
            // feature was ever actually exercised end-to-end.
            "agent_commission_expense" to "Agent Commission Expense",
            // Real Toss Bank 체크카드 (check/debit card) purchase expense (2026-07-31) --
            // see DebitCard.kt's own doc comment. Learned from the interest_income/
            // agent_commission_expense precedent above: seed this before the first real
            // card purchase, not after one 500s discovering it's missing.
            "card_spend_expense" to "Debit Card Spend Expense",
            // Real Naver Pay/Kakao Pay/Toss 후불결제 (postpaid/BNPL credit line,
            // 2026-07-31) -- see PostpaidCreditLine.kt's own doc comment. Learned from
            // the interest_income/agent_commission_expense/card_spend_expense precedent
            // above: seed this before the first real spend, not after one 500s
            // discovering it's missing.
            "postpaid_credit_payable" to "Postpaid Credit Payable",
            // Real 당근마켓 중고차 정비소 동행 (used-car mechanic-inspection
            // accompaniment, 2026-07-31) -- see VehicleInspectionBooking.kt's own doc
            // comment. Seeded before the first real booking, learning from the
            // interest_income/agent_commission_expense/postpaid_credit_payable
            // precedent above.
            "vehicle_inspection_holding" to "Vehicle Inspection Fee Holding",
            // Real Kakao T 대리운전 (designated driver, 2026-08-01) -- see
            // DesignatedDriverTrip.kt's own doc comment. Real bug found live (2026-08-01)
            // while live-verifying this exact feature end-to-end: the first real trip
            // request 500'd with "Unknown ledger account designated_driver_holding" --
            // this row was missed the same way interest_income/agent_commission_expense/
            // card_spend_expense/postpaid_credit_payable/vehicle_inspection_holding all
            // were before it. Every one of those was only ever caught live, never by a
            // compile or a mocked unit test (LedgerService is always mocked in this
            // codebase's own unit tests, so a missing seed row is invisible to them) --
            // this is now six real instances of the identical bug class in one file.
            "designated_driver_holding" to "Designated Driver Fare Holding",
            // Real Ejo Heza ya Moto-style insurance premium savings fund (2026-08-02) --
            // see InsurancePremiumFund.kt's own doc comment. Learned from the
            // interest_income/agent_commission_expense/card_spend_expense/
            // postpaid_credit_payable/vehicle_inspection_holding/designated_driver_holding
            // precedent above: seed this before the first real contribution, not after
            // one 500s discovering it's missing.
            "insurance_premium_fund_payable" to "Insurance Premium Fund Payable",
            // Real itunda Deposit Protection Fund (2026-08-11) -- see
            // DepositProtectionFund.kt's own doc comment. Learned from this exact
            // file's own documented history (interest_income/agent_commission_expense/
            // card_spend_expense/postpaid_credit_payable/vehicle_inspection_holding/
            // designated_driver_holding/insurance_premium_fund_payable -- SEVEN prior
            // instances of the identical bug class): seeded here BEFORE the first real
            // contribution runs, not after a live 500 discovers it's missing. (Missed
            // once anyway on first deploy -- DepositProtectionScheduler's first tick
            // real-500'd with exactly this "Unknown ledger account" error, confirming
            // this file's own warning that the bug is only ever caught live.)
            "deposit_protection_expense" to "Deposit Protection Fund Contribution Expense",
            "deposit_protection_reserve" to "Deposit Protection Fund Reserve",
            // Real Baemin-style tiered order-amount promotion (2026-08-16) -- see
            // EatsPromotionCalculator's own doc comment. Learned from this exact file's
            // own documented history (EIGHT prior instances of the identical bug class
            // above): seeded here BEFORE the first real live-verification order, not
            // after a live 500 discovers it's missing. (Missed once anyway on first
            // live-verification -- confirming this file's own standing warning that a
            // missing seed row is invisible to compile and to this codebase's own
            // mocked-LedgerService unit tests, only ever caught live.)
            "promotion_expense" to "Eats Order Promotion Expense",
            // Real Korean 지연이체서비스 (Delayed Transfer Service, 2026-08-18) -- see
            // P2pDelayedTransfer.kt's own doc comment. Learned from this exact file's
            // own documented history (NINE prior instances of the identical
            // seed-row-missing bug class above): seeded here BEFORE the first real
            // delayed transfer runs, not after a live 500 discovers it's missing.
            "p2p_delay_holding" to "P2P Delayed Transfer Holding",
            // Real Kigali Tap&Go-style transit stored-value balance (2026-08-27) -- see
            // TransitBalance.kt's own doc comment. Learned from this exact file's own
            // documented history (TEN prior instances of the identical
            // seed-row-missing bug class above): seeded here BEFORE the first real
            // top-up runs, not after a live 500 discovers it's missing.
            "transit_balance_payable" to "Transit Balance Payable",
            "transit_fare_expense" to "Transit Fare Expense",
        )
    }
}
