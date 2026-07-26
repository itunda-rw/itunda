package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

/**
 * Non-wallet ledger clearing accounts (fee income, money in flight to external rails/
 * custody/lending) — mirrors the `ledgerAccounts` map in backend/src/services/ledger.ts.
 * Kept separate from `wallets` so they never show up as a customer-facing balance.
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
            // wallet, it just hasn't reached its final recipient yet), not a fake holding
            // pattern invented for this feature.
            "eats_delivery_holding" to "Eats Delivery Fee Holding",
            // Holds a real KakaoTalk-style gift's money from the moment it's sent until
            // the recipient explicitly opens/claims it, or it's auto-refunded to the
            // sender after Gift.EXPIRY (rw.itunda.gift.GiftService, 2026-07-20) -- same
            // real escrow-clearing-account shape as eats_delivery_holding above.
            "gift_holding" to "Gift Holding",
            "cash_vault" to "Itunda Cash Vault",
            // Real foreign-currency conversion clearing, one per real supported
            // currency (2026-07-25) -- see ForeignCurrencyWalletService's own doc
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
        )
    }
}
