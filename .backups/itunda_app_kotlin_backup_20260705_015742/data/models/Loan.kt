package com.itunda.app.data.models

data class LoanOffer(
    val id: String,
    val name: String,
    val maxAmount: Double,
    val minAmount: Double,
    val interestRate: Double,
    val termMonths: Int,
    val description: String,
    val requirements: List<String>
)

data class ActiveLoan(
    val id: String,
    val offerId: String,
    val type: String,
    val amount: Double,
    val remaining: Double,
    val interestRate: Double,
    val termMonths: Int,
    val monthlyPayment: Double,
    val dueDate: String,
    val status: String
)

data class LoanOffersResponse(
    val success: Boolean,
    val offers: List<LoanOffer>
)

data class MyLoansResponse(
    val success: Boolean,
    val loans: List<ActiveLoan>
)

data class LoanApplyRequest(
    val offerId: String,
    val amount: Double,
    val termMonths: Int
)

data class LoanApplyResponse(
    val success: Boolean,
    val loanId: String?,
    val message: String?
)

data class RepayRequest(
    val loanId: String,
    val amount: Double
)

data class RepayResponse(
    val success: Boolean,
    val message: String?
)
