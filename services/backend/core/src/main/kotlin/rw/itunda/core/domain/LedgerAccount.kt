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
        )
    }
}
