package rw.itunda.app.ui

import rw.itunda.core.designsystem.components.BackTopBar
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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SpendingInsightResponse
import java.math.BigDecimal

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
                    item { Text("No spending recorded yet.", style = MaterialTheme.typography.bodyMedium) }
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
            }
        }
    }
}

private fun formatMoneySpending(value: BigDecimal): String {
    val rounded = value.stripTrailingZeros()
    return if (rounded.scale() <= 0) rounded.toBigInteger().toString() else "%,.2f".format(rounded)
}
