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
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.outlined.ShoppingBag
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
    /** Backend transaction type; merchant-style types show as "Paid", other outgoing as "Sent". */
    val type: String = "",
    /** ISO-8601 timestamp used for the prototype's "Yesterday / Tuesday / 08:12 / 8 Oct" subtitle. */
    val createdAt: String = "",
)

private fun TransactionDisplayItem.isPaid() = isOutgoing && type.uppercase().let { it.contains("PAY") || it.contains("MERCHANT") || it.contains("PURCHASE") || it.contains("BILL") }

/** Prototype subtitle time: today -> HH:mm, yesterday -> "Yesterday", this week -> weekday, else "d MMM". */
private fun whenLabel(iso: String): String {
    if (iso.isBlank()) return ""
    return try {
        val zone = java.time.ZoneId.systemDefault()
        val at = java.time.OffsetDateTime.parse(iso).atZoneSameInstant(zone)
        val today = java.time.LocalDate.now(zone)
        val days = java.time.temporal.ChronoUnit.DAYS.between(at.toLocalDate(), today)
        when {
            days <= 0 -> String.format(java.util.Locale.US, "%02d:%02d", at.hour, at.minute)
            days == 1L -> "Yesterday"
            days < 7 -> at.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.US)
            else -> at.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.US))
        }
    } catch (_: Exception) { "" }
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
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(0) } // 0 All, 1 Sent, 2 Received, 3 Paid
    val shown = transactions.filter {
        (filter == 0 || (filter == 1 && it.isOutgoing && !it.isPaid()) || (filter == 2 && !it.isOutgoing) || (filter == 3 && it.isPaid())) && it.description.contains(query.trim(), ignoreCase = true)
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
        Column(modifier = Modifier.fillMaxSize().background(Ids.colors.surfaceSoft)) {
            Row(Modifier.fillMaxWidth().height(70.dp).background(Ids.colors.background).padding(start = 6.dp, top = 16.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(48.dp).pressScaleClickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    ItundaArt("back", ItundaArtSet.Line, 24.dp, tint = Ids.colors.textPrimary)
                }
                Text("Activity", color = Ids.colors.textPrimary, fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 10.dp))
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp)).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = query, onValueChange = { query = it.take(40) }, singleLine = true,
                    textStyle = TextStyle(color = Ids.colors.textPrimary, fontSize = 16.sp),
                    cursorBrush = SolidColor(Ids.colors.brand), modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner -> if (query.isEmpty()) Text("Search activity", color = Ids.colors.textTertiary, fontSize = 16.sp); inner() },
                )
            }
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                listOf("All", "Sent", "Received", "Paid").forEachIndexed { i, label ->
                    val on = filter == i
                    Box(
                        Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(if (on) Ids.colors.textPrimary else Ids.colors.background)
                            .pressScaleClickable { filter = i }.padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, fontSize = 14.sp, lineHeight = 19.6.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, color = if (on) Ids.colors.background else Ids.colors.textTertiary) }
                }
            }
            if (shown.isEmpty()) {
                EmptyState("No transactions yet — sends, receives, and payments will show up here.", icon = Icons.Outlined.ReceiptLong)
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp), contentPadding = PaddingValues(top = 34.dp)) {
                    items(shown, key = { it.id }) { tx -> TransactionRow(tx) }
                }
            }
        }
        PullToRefreshContainer(state = pullToRefreshState, modifier = Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun TransactionRow(tx: TransactionDisplayItem) {
    val kind = if (!tx.isOutgoing) "Received" else if (tx.isPaid()) "Paid" else "Sent"
    val whenText = whenLabel(tx.createdAt)
    Row(modifier = Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            val art = when {
                !tx.isOutgoing -> "receive"
                tx.isPaid() && tx.description.contains("bus", ignoreCase = true) -> "transit"
                tx.isPaid() -> "bag"
                else -> "send"
            }
            ItundaArt(art, ItundaArtSet.Proto, 31.dp, fit = true)
        }
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(tx.description, color = Ids.colors.textPrimary, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.3).sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(kind + if (whenText.isNotEmpty()) " · $whenText" else "", color = Ids.colors.textSecondary, fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = (-0.2).sp)
        }
        Text(
            (if (tx.isOutgoing) "−" else "+") + rwfFormatter.format(tx.amount.toLong()),
            color = if (tx.isOutgoing) Ids.colors.textPrimary else Ids.colors.textBrand,
            fontSize = 14.sp, lineHeight = 19.6.sp, letterSpacing = (-0.3).sp, fontWeight = FontWeight.Bold,
        )
        Text(" " + tx.currency, color = Ids.colors.textSecondary, fontSize = 13.sp, letterSpacing = (-0.3).sp, fontWeight = FontWeight.SemiBold)
    }
}
