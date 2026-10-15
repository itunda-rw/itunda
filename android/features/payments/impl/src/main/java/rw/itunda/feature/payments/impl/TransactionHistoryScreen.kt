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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
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
    val fee: Double = 0.0,
    val createdAt: String = "",
) {
    // Match the backend TransactionType enum exactly. Substring matching made
    // filters depend on display text and could misclassify future types.
    val isPayment: Boolean
        get() = type.uppercase() in setOf("PAYMENT", "BILL", "AIRTIME")
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
    var selectedTransaction by remember { mutableStateOf<TransactionDisplayItem?>(null) }
    val clipboard = LocalClipboardManager.current
    val filteredTransactions = transactions.filter { tx ->
        val matchesFilter = when (selectedFilter) {
            "SENT" -> tx.isOutgoing && !tx.isPayment
            "RECEIVED" -> !tx.isOutgoing && !tx.isPayment
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
                    items(filteredTransactions, key = { it.id }) { tx -> TransactionRow(tx, onClick = { selectedTransaction = tx }) }
                }
            }
        }
        PullToRefreshContainer(state = pullToRefreshState, modifier = Modifier.align(Alignment.TopCenter))
        selectedTransaction?.let { tx ->
            ModalBottomSheet(
                onDismissRequest = { selectedTransaction = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = Ids.colors.background,
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
                    Box(
                        Modifier.size(56.dp).clip(CircleShape)
                            .background(if (tx.isOutgoing) Ids.colors.dangerTint else Ids.colors.successTint)
                            .align(Alignment.CenterHorizontally),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (tx.isOutgoing) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                            contentDescription = null,
                            tint = if (tx.isOutgoing) Ids.colors.danger else Ids.colors.success,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Text(tx.description, Modifier.fillMaxWidth().padding(top = 12.dp),
                        color = Ids.colors.textSecondary, fontSize = 15.sp, textAlign = TextAlign.Center)
                    Text(
                        "${if (tx.isOutgoing) "-" else "+"}${rwfFormatter.format(tx.amount.toLong())} ${tx.currency}",
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        color = if (tx.isOutgoing) Ids.colors.textPrimary else Ids.colors.success,
                        fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Ids.colors.chip).padding(horizontal = 16.dp)) {
                        TransactionDetailRow(stringResource(R.string.transaction_detail_status), tx.status.lowercase().replaceFirstChar { it.uppercase() })
                        TransactionDetailRow(stringResource(R.string.transaction_detail_type), tx.type.lowercase().replaceFirstChar { it.uppercase() }, divider = true)
                        TransactionDetailRow(stringResource(R.string.transaction_detail_date), tx.createdAt.substringBefore("T").ifBlank { "—" }, divider = true)
                        TransactionDetailRow(stringResource(if (tx.isOutgoing) R.string.transaction_detail_to else R.string.transaction_detail_from), stringResource(R.string.transaction_detail_itunda_account), divider = true)
                        TransactionDetailRow(stringResource(R.string.transaction_detail_fee), "${rwfFormatter.format(tx.fee.toLong())} ${tx.currency}", divider = true)
                        TransactionDetailRow(stringResource(R.string.transaction_detail_reference), tx.id, divider = true)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.transaction_detail_real_data_note), color = Ids.colors.textTertiary, fontSize = 12.sp)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Ids.colors.chip)
                            .clickable { selectedTransaction = null }.padding(vertical = 15.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.transaction_detail_close), color = Ids.colors.textPrimary, fontWeight = FontWeight.SemiBold)
                        }
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Ids.colors.accent)
                            .clickable {
                                clipboard.setText(AnnotatedString("${tx.description}\n${tx.type} · ${tx.status}\n${tx.amount} ${tx.currency}\n${tx.id}"))
                                selectedTransaction = null
                            }.padding(vertical = 15.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.transaction_detail_copy), color = Ids.colors.textPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionDetailRow(label: String, value: String, divider: Boolean = false) {
    if (divider) Spacer(Modifier.height(1.dp).fillMaxWidth().background(Ids.colors.background))
    Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ids.colors.textSecondary, fontSize = 14.sp)
        Text(value, color = Ids.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TransactionRow(tx: TransactionDisplayItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
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
