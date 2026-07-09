package rw.itunda.loans

import java.math.BigDecimal

data class LoanOffer(val id: String, val name: String, val maxAmount: BigDecimal, val interestRate: Double, val term: String, val requirements: String)

/** Same static catalog as backend/src/services/database.ts's loanOffers. */
object LoanCatalog {
    val offers = listOf(
        LoanOffer("loan_1", "Quick Loan", BigDecimal("500000"), 5.0, "30 days", "MTN MoMo account"),
        LoanOffer("loan_2", "Personal Loan", BigDecimal("2000000"), 3.5, "6 months", "KYC verified"),
        LoanOffer("loan_3", "Business Loan", BigDecimal("5000000"), 2.8, "12 months", "KYC verified + 3 months history"),
    )

    fun find(id: String) = offers.find { it.id == id }
}
