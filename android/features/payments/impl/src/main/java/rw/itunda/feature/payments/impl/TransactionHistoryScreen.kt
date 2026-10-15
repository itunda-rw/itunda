package rw.itunda.feature.payments.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import rw.itunda.core.designsystem.components.EmptyState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.theme.Ids

/**
 * Plain display model, not the App target's TransactionDto directly -- this Feature
 * module can't depend back on :app (:app depends on :features:payments:impl, never
 * the reverse), same constraint as iOS's BankView/SavingsRowData. ItundaAppScreen.kt
 * maps the real TransactionDto list into this shape before calling this screen.
 */
data class TransactionDisplayItem(
    val id: String,
    val description: String,
    val amount: Double,
    val currency: String,
    val status: String,
    val isOutgoing: Boolean,
    val type: String = "",
) {
    val isPayment: Boolean
        get() = listOf("PAYMENT", "BILL", "PURCHASE", "ORDER", "EATS", "SHOP")
            .any { type.contains(it, ignoreCase = true) }
}

/**
 * Real transaction history screen, matching the real Toss card-detail reference
 * screenshot (user-provided, 2026-07-12): spend-this-month total up top, then a
 * real list -- "아직 내역이 없어요" (no history yet) when empty, matching Toss's own
 * empty state exactly rather than inventing fake rows to fill the screen. Backed
 * by services/backend/account's real getTransactionHistory endpoint (previously a
 * dead repository method with no controller ever calling it) -- no card issuance
 * or card network exists, so this is framed as spend history, not a real card.
 *
 * Real Toss/Kakao pull-to-refresh (2026-08-12 research pass) -- itunda's own real
 * pull-to-refresh implementation (Home tab, `ItundaAppScreen.kt`) existed on exactly
 * one screen despite this being one of Toss's most iconic, pervasive interaction
 * patterns; transaction history is the single most-checked list in any real banking
 * app and had no way to manually refresh beyond leaving and re-entering the screen.
 * `onRefresh`/`isRefreshing` are plain props, not owned here -- this Feature module
 * has no `MainViewModel` of its own to call `retry()` on (same "dumb presentational
 * component, parent owns the real data" architecture this file's own doc comment
 * above already establishes for `transactions` itself); only the pull gesture's own
 * UI mechanics (state, nested scroll, the container) live in this composable.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(
    transactions: List<TransactionDisplayItem>,
    onBack: () -> Unit,
    onRefresh: () -> Unit = {},
    isRefreshing: Boolean = false,
) {
    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }
    val filteredTransactions = transactions.filter { tx ->
        val matchesFilter = when (selectedFilter) {
            "SENT" -> tx.isOutgoing && !tx.isPayment
            "RECEIVED" -> !tx.isOutgoing
            "PAID" -> tx.isPayment
            else -> true
        }
        val q = query.trim()
        val matchesQuery = q.isBlank() ||
            tx.description.contains(q, ignoreCase = true) ||
            tx.status.contains(q, ignoreCase = true) ||
            tx.type.contains(q, ignoreCase = true) ||
            tx.amount.toLong().toString().contains(q)
        matchesFilter && matchesQuery
    }
    val pullToRefreshState = rememberPullToRefreshState()
    LaunchedEffect(pullToRefreshState.isRefreshing) {
        if (pullToRefreshState.isRefreshing) onRefresh()
    }
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) pullToRefreshState.endRefresh()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(pullToRefreshState.nestedScrollConnection),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Ids.colors.background)
        ) {
            FlowTopBar(onBack)

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(14.dp)).background(Ids.colors.chip)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = Ids.colors.textTertiary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(stringResource(R.string.transaction_history_search), color = Ids.colors.textTertiary, fontSize = 14.sp)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Ids.colors.textPrimary, fontSize = 14.sp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "ALL" to R.string.transaction_history_filter_all,
                    "SENT" to R.string.transaction_history_filter_sent,
                    "RECEIVED" to R.string.transaction_history_filter_received,
                    "PAID" to R.string.transaction_history_filter_paid,
                ).forEach { (key, label) ->
                    val selected = selectedFilter == key
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(999.dp))
                            .background(if (selected) Ids.colors.textPrimary else Ids.colors.chip)
                            .clickable { selectedFilter = key }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    ) {
                        Text(stringResource(label),
                            color = if (selected) Ids.colors.background else Ids.colors.textSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredTransactions.isEmpty()) {
                EmptyState(
                    if (transactions.isEmpty()) stringResource(R.string.transaction_history_empty)
                    else stringResource(R.string.transaction_history_no_matches),
                    icon = Icons.Outlined.ReceiptLong,
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                    items(filteredTransactions, key = { it.id }) { tx -> TransactionRow(tx) }
                }
            }
        }
        PullToRefreshContainer(state = pullToRefreshState, modifier = Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun TransactionRow(tx: TransactionDisplayItem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (tx.isOutgoing) Ids.colors.dangerTint else Ids.colors.successTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (tx.isOutgoing) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (tx.isOutgoing) Ids.colors.danger else Ids.colors.success
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(tx.description, color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(tx.status.lowercase().replaceFirstChar { it.uppercase() }, color = Ids.colors.textTertiary, fontSize = 13.sp)
        }
        Text(
            "${if (tx.isOutgoing) "-" else "+"}${rwfFormatter.format(tx.amount.toLong())} ${tx.currency}",
            color = if (tx.isOutgoing) Ids.colors.textPrimary else Ids.colors.success,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
