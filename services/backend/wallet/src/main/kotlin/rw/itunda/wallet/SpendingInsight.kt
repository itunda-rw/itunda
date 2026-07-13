package rw.itunda.wallet

import java.math.BigDecimal

data class SpendingCategory(val name: String, val amount: BigDecimal)
data class SpendingInsightResult(val categories: List<SpendingCategory>, val totalSpent: BigDecimal)

enum class BudgetStatus { UNDER, NEAR, OVER }
data class BudgetView(
    val category: String?,
    val monthlyLimit: BigDecimal,
    val spent: BigDecimal,
    val remaining: BigDecimal,
    val percentUsed: Int,
    val status: BudgetStatus,
)
