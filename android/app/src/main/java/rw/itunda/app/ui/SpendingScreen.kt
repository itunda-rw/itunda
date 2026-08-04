package rw.itunda.app.ui

import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.SkeletonBlock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import rw.itunda.core.designsystem.components.IdsButton
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import rw.itunda.core.network.BudgetViewDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SetBudgetRequest
import rw.itunda.core.network.SpendingCategoryDto
import rw.itunda.core.network.SpendingInsightResponse
import java.math.BigDecimal
import rw.itunda.core.designsystem.components.EmptyState

// Real Kakao Pay 소비 리포트-style spending categorization (rw.itunda.wallet.
// WalletService.getSpendingInsight, real since 2026-07-13) -- first Android client for
// this feature (item 107, found backend-only via a fresh matrix scan; bank-mfe ported
// the same day as item 106). Same plain-Material3, no-Toss-color-alias convention as
// CreditScoreScreen.kt (a direct sibling: a real financial-insight read screen, no
// money-moving action of its own).
@Composable
fun SpendingScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var insight by remember { mutableStateOf<SpendingInsightResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            insight = NetworkClient.apiService.getSpendingInsight()
        } catch (_: Exception) {
            error = "Could not load your spending."
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(20.dp)) {
            BackTopBar(title = "Spending", onBack = onBack)
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            val current = insight
            if (current == null) {
                if (error == null) item { SkeletonBlock() }
            } else {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Total spent, all time", style = MaterialTheme.typography.labelMedium)
                            Text("${formatMoneySpending(current.totalSpent)} RWF", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "Real, ledger-based -- what every wallet debit actually paid for.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                if (current.categories.isEmpty()) {
                    item { EmptyState("No spending recorded yet.") }
                } else {
                    item { Text("By category", style = MaterialTheme.typography.titleMedium) }
                    val maxAmount = current.categories.maxOf { it.amount }.let { if (it > BigDecimal.ZERO) it else BigDecimal.ONE }
                    items(current.categories, key = { it.name }) { category ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(category.name, style = MaterialTheme.typography.bodyLarge)
                                    Text("${formatMoneySpending(category.amount)} RWF", style = MaterialTheme.typography.bodyLarge)
                                }
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(6.dp))
                                val fraction = (category.amount.toDouble() / maxAmount.toDouble()).coerceIn(0.0, 1.0)
                                androidx.compose.foundation.layout.Box(
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                ) {
                                    androidx.compose.foundation.layout.Box(
                                        modifier = Modifier.fillMaxWidth(fraction.toFloat()).fillMaxHeight()
                                            .clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.primary),
                                    )
                                }
                            }
                        }
                    }
                }
                item { BudgetsSection(categories = current.categories) }
            }
        }
    }
}

// Real Toss budgets/limits equivalent (item 172) -- WalletService.setBudget/
// getBudgets via ApiService.getBudgets/setBudget. Mirrors bank-mfe's own
// BudgetsSection/SetBudgetForm (item 165): per-category or overall (category == null)
// monthly limit, color-coded by UNDER/NEAR(>=80%)/OVER(>=100%) status.
@Composable
private fun BudgetsSection(categories: List<SpendingCategoryDto>) {
    var budgets by remember { mutableStateOf<List<BudgetViewDto>?>(null) }
    var showForm by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            budgets = NetworkClient.apiService.getBudgets().budgets
        } catch (_: Exception) {
            budgets = emptyList()
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Budgets", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { showForm = !showForm }) { Text(if (showForm) "Cancel" else "+ Set budget") }
        }
        if (showForm) {
            SetBudgetForm(
                categories = categories,
                onSet = { category, limit ->
                    showForm = false
                    coroutineScope.launch {
                        budgets = try {
                            NetworkClient.apiService.setBudget(SetBudgetRequest(category, limit))
                            NetworkClient.apiService.getBudgets().budgets
                        } catch (_: Exception) {
                            budgets
                        }
                    }
                },
            )
        }
        val current = budgets
        if (current != null && current.isNotEmpty()) {
            current.forEach { budget -> BudgetCard(budget) }
        }
    }
}

@Composable
private fun BudgetCard(budget: BudgetViewDto) {
    val barColor = when (budget.status) {
        "OVER" -> androidx.compose.ui.graphics.Color(0xFFE53935)
        "NEAR" -> androidx.compose.ui.graphics.Color(0xFFF5A623)
        else -> MaterialTheme.colorScheme.primary
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(budget.category ?: "Overall spending", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${formatMoneySpending(budget.spent)} / ${formatMoneySpending(budget.monthlyLimit)} RWF",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(6.dp))
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxWidth((budget.percentUsed / 100f).coerceIn(0f, 1f)).fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp)).background(barColor),
                )
            }
            if (budget.status == "OVER") {
                Text("Over budget", style = MaterialTheme.typography.bodySmall, color = barColor)
            } else if (budget.status == "NEAR") {
                Text("Nearing your limit", style = MaterialTheme.typography.bodySmall, color = barColor)
            }
        }
    }
}

@Composable
private fun SetBudgetForm(categories: List<SpendingCategoryDto>, onSet: (String?, BigDecimal) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var limitText by remember { mutableStateOf("") }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth()) {
                TextButton(onClick = { expanded = true }) { Text(selectedCategory ?: "Overall spending") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("Overall spending") }, onClick = { selectedCategory = null; expanded = false })
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = { selectedCategory = category.name; expanded = false },
                        )
                    }
                }
            }
            IdsTextField(
                value = limitText,
                onValueChange = { limitText = it },
                label = "Monthly limit (RWF)",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth(),
            )
            IdsButton(
                text = "Save budget",
                onClick = {
                    val limit = limitText.toBigDecimalOrNull()
                    if (limit != null && limit > BigDecimal.ZERO) onSet(selectedCategory, limit)
                },
            )
        }
    }
}

private fun formatMoneySpending(value: BigDecimal): String {
    val rounded = value.stripTrailingZeros()
    return if (rounded.scale() <= 0) rounded.toBigInteger().toString() else "%,.2f".format(rounded)
}
