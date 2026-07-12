package rw.itunda.wallet

import java.math.BigDecimal

data class SpendingCategory(val name: String, val amount: BigDecimal)
data class SpendingInsightResult(val categories: List<SpendingCategory>, val totalSpent: BigDecimal)
