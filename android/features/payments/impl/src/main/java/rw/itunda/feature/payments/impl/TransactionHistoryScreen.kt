package rw.itunda.feature.payments.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import rw.itunda.core.designsystem.components.EmptyState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.IdsIcons
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
)

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
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(0) } // 0 All, 1 Sent, 2 Received
    val shown = transactions.filter {
        (filter == 0 || (filter == 1) == it.isOutgoing) && it.description.contains(query.trim(), ignoreCase = true)
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
            Text("Activity", color = Ids.colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).clip(RoundedCornerShape(16.dp)).background(Ids.colors.surfaceSoft).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(IdsIcons.Search, contentDescription = null, tint = Ids.colors.textTertiary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                BasicTextField(
                    value = query, onValueChange = { query = it.take(40) }, singleLine = true,
                    textStyle = TextStyle(color = Ids.colors.textPrimary, fontSize = 16.sp),
                    cursorBrush = SolidColor(Ids.colors.brand), modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner -> if (query.isEmpty()) Text("Search activity", color = Ids.colors.textTertiary, fontSize = 16.sp); inner() },
                )
            }
            Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Sent", "Received").forEachIndexed { i, label ->
                    val on = filter == i
                    Box(
                        Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(if (on) Ids.colors.textPrimary else Ids.colors.surfaceSoft)
                            .pressScaleClickable { filter = i }.padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (on) Ids.colors.background else Ids.colors.textTertiary) }
                }
            }
            if (shown.isEmpty()) {
                EmptyState("No transactions yet — sends, receives, and payments will show up here.", icon = Icons.Outlined.ReceiptLong)
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                    items(shown, key = { it.id }) { tx -> TransactionRow(tx) }
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
            modifier = Modifier.size(40.dp).clip(CircleShape).background(if (tx.isOutgoing) Ids.colors.surfaceSoft else Ids.colors.successTint),
            contentAlignment = Alignment.Center
        ) {
            if (tx.isOutgoing) ItundaArt("send", ItundaArtSet.Color, 26.dp)
            else Icon(Icons.Outlined.ArrowDownward, contentDescription = null, modifier = Modifier.size(20.dp), tint = Ids.colors.success)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(tx.description, color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                (if (tx.isOutgoing) "Sent" else "Received") + " · " + tx.status.lowercase().replaceFirstChar { it.uppercase() },
                color = Ids.colors.textTertiary, fontSize = 13.sp,
            )
        }
        Text(
            (if (tx.isOutgoing) "−" else "+") + rwfFormatter.format(tx.amount.toLong()),
            color = if (tx.isOutgoing) Ids.colors.textPrimary else Ids.colors.textBrand,
            fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
        )
        Text(" " + tx.currency, color = Ids.colors.textTertiary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
