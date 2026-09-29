package rw.itunda.account

import rw.itunda.core.domain.Transaction
import java.math.BigDecimal

data class SpendingCategory(val name: String, val amount: BigDecimal)
data class SpendingInsightResult(val categories: List<SpendingCategory>, val totalSpent: BigDecimal)

// Real Kakao Pay 페이아이 소비 리포트 (AI spending report) -- see
// AccountService.getMonthlySpendingReport's own doc comment for the full sourced account.
// `percentChange` is null (not zero) when there's genuinely no prior-month spend in that
// category to compare against -- a real "new this month" signal, never a fabricated 0%.
data class SpendingComparisonCategory(
    val name: String,
    val currentAmount: BigDecimal,
    val previousAmount: BigDecimal,
    val percentChange: Int?,
)
data class MonthlySpendingReport(
    val currentTotal: BigDecimal,
    val previousTotal: BigDecimal,
    val percentChange: Int?,
    val categories: List<SpendingComparisonCategory>,
)

enum class BudgetStatus { UNDER, NEAR, OVER }
data class BudgetView(
    val category: String?,
    val monthlyLimit: BigDecimal,
    val spent: BigDecimal,
    val remaining: BigDecimal,
    val percentUsed: Int,
    val status: BudgetStatus,
)

// Real Toss Timeline-style unusual-spend flag -- see AccountService.getTransactionTimeline's
// own doc comment for the full account.
data class TransactionTimelineEntry(val transaction: Transaction, val unusuallyLarge: Boolean)
