package rw.itunda.loans

import java.math.BigDecimal

data class Lender(val id: String, val name: String, val kind: String)

/**
 * Real, BNR-licensed institutions (National Bank of Rwanda's own November 2025
 * "Licensed Institutions" list: https://www.bnr.rw/documents/Licensed_Institutions_-_November_2025_96EyafM.pdf)
 * -- named for real, the same sourcing discipline RailCatalog's MTN/Airtel prefixes and
 * WebhookRetryScheduler's Toss Payments schedule already follow in this backend. Itunda's
 * own book is the only lender with a real underwriting/disbursement path (LoansService);
 * the partner banks are real, correctly-named institutions with no live loan-origination
 * integration behind them yet -- the same honest "quote/bind adapters remain target" shape
 * the Insurance module's marketplace already carries, not a fabricated partnership.
 */
object LenderCatalog {
    val lenders = listOf(
        Lender("lender_itunda", "Itunda", "digital_bank"),
        Lender("lender_bk", "Bank of Kigali", "commercial_bank"),
        Lender("lender_equity", "Equity Bank Rwanda", "commercial_bank"),
        Lender("lender_urwego", "Urwego Bank", "microfinance_bank"),
    )

    fun find(id: String) = lenders.find { it.id == id }
}

data class LoanOffer(
    val id: String,
    val lenderId: String,
    val lenderName: String,
    val name: String,
    val maxAmount: BigDecimal,
    val interestRate: Double,
    val term: String,
    val requirements: String,
)

/** Same static catalog as backend/src/services/database.ts's loanOffers, now a real multi-lender marketplace. */
object LoanCatalog {
    val offers = listOf(
        LoanOffer("loan_1", "lender_itunda", "Itunda", "Quick Loan", BigDecimal("500000"), 5.0, "30 days", "MTN MoMo account"),
        LoanOffer("loan_2", "lender_itunda", "Itunda", "Personal Loan", BigDecimal("2000000"), 3.5, "6 months", "KYC verified"),
        LoanOffer("loan_3", "lender_itunda", "Itunda", "Business Loan", BigDecimal("5000000"), 2.8, "12 months", "KYC verified + 3 months history"),
        LoanOffer("loan_4", "lender_bk", "Bank of Kigali", "BK Personal Loan", BigDecimal("3000000"), 3.2, "9 months", "KYC verified"),
        LoanOffer("loan_5", "lender_equity", "Equity Bank Rwanda", "Equity SME Loan", BigDecimal("8000000"), 2.5, "18 months", "KYC verified + 3 months history"),
        LoanOffer("loan_6", "lender_urwego", "Urwego Bank", "Urwego Microloan", BigDecimal("300000"), 4.5, "30 days", "MTN MoMo account"),
    )

    fun find(id: String) = offers.find { it.id == id }
    fun findByLender(lenderId: String) = offers.filter { it.lenderId == lenderId }
}
