package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.formatMoney
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons
import rw.itunda.core.network.NetWorthHistoryPointDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.OverviewResponse
import rw.itunda.app.R
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Real Toss "총자산" detail screen (2026-09-12, direct user-supplied total-assets
 * screenshots) -- tapping the net worth header on OverviewScreen opens this: a
 * proportional breakdown bar + percentage list (account balances vs. reward
 * points, matching the backend's NetWorthSnapshot scope exactly -- see that
 * entity's own doc comment for why savings/investments/loans are deliberately
 * excluded), plus the "자산 변화" monthly trend chart backed by the new
 * GET /api/v1/overview/net-worth-history endpoint. Own file, not inline in
 * OverviewScreen.kt, to avoid pushing that file past the file-size-lint
 * 500-line guideline. Uses Card/Material3 throughout, matching this screen
 * family's own established exception to the flat-design sweep.
 */
@Composable
fun NetWorthDetailScreen(overview: OverviewResponse, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var history by remember { mutableStateOf<List<NetWorthHistoryPointDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val historyError = stringResource(R.string.net_worth_detail_history_error)

    LaunchedEffect(Unit) {
        try {
            history = NetworkClient.apiService.getNetWorthHistory().history
        } catch (_: Exception) {
            error = historyError
        }
    }

    val accountsTotal = overview.accounts.fold(java.math.BigDecimal.ZERO) { acc, a -> acc + a.balance }
    val pointsTotal = overview.points.rewardsTotal
    val trackedTotal = accountsTotal + pointsTotal

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Ids.layout.screenHorizontal, vertical = 12.dp)) {
            Box(
                modifier = Modifier.size(Ids.layout.minTouchTarget).clip(CircleShape).pressScaleClickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(IdsIcons.Back, contentDescription = stringResource(R.string.back), modifier = Modifier.size(18.dp), tint = Ids.colors.textPrimary)
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = Ids.layout.screenHorizontal)) {
            Text(
                stringResource(R.string.net_worth_detail_current_total),
                color = Ids.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp),
            )
            Text(
                "${formatMoney(trackedTotal)} RWF", fontWeight = FontWeight.Bold, fontSize = 26.sp,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            if (trackedTotal.signum() > 0) {
                val accountsPercent = (accountsTotal.toDouble() / trackedTotal.toDouble() * 100).toInt()
                val pointsPercent = 100 - accountsPercent
                BreakdownBar(
                    segments = buildList {
                        if (accountsTotal.signum() > 0) add(BreakdownSegment(stringResource(R.string.net_worth_detail_accounts), accountsTotal, accountsPercent, Ids.colors.brand))
                        if (pointsTotal.signum() > 0) add(BreakdownSegment(stringResource(R.string.net_worth_detail_points), pointsTotal, pointsPercent, Ids.colors.success))
                    },
                )
            } else {
                Text(stringResource(R.string.net_worth_detail_empty), color = Ids.colors.textSecondary, fontSize = 13.sp)
            }

            Text(
                stringResource(R.string.net_worth_detail_trend_title), fontWeight = FontWeight.Bold, fontSize = 16.sp,
                modifier = Modifier.padding(top = 28.dp, bottom = 8.dp),
            )
            when {
                error != null -> Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                history == null -> Text(stringResource(R.string.loading), color = Ids.colors.textSecondary, fontSize = 13.sp)
                history!!.isEmpty() -> Text(
                    stringResource(R.string.net_worth_detail_trend_empty), color = Ids.colors.textSecondary, fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
                else -> TrendChart(history!!)
            }
            Text(
                stringResource(R.string.net_worth_detail_trend_disclosure_a),
                color = Ids.colors.textTertiary, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                stringResource(R.string.net_worth_detail_trend_disclosure_b),
                color = Ids.colors.textTertiary, fontSize = 11.sp, modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

private data class BreakdownSegment(val label: String, val amount: java.math.BigDecimal, val percent: Int, val color: androidx.compose.ui.graphics.Color)

@Composable
private fun BreakdownBar(segments: List<BreakdownSegment>) {
    Column {
        Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
            segments.forEach { s -> Box(Modifier.weight(s.percent.coerceAtLeast(1).toFloat()).fillMaxHeight().background(s.color)) }
        }
        segments.forEach { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(s.color))
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(s.label, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("${s.percent}%", color = Ids.colors.textSecondary, fontSize = 12.sp)
                }
                Text("${formatMoney(s.amount)} RWF", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun TrendChart(history: List<NetWorthHistoryPointDto>) {
    val max = (history.maxOfOrNull { it.liquidTotal.toDouble() } ?: 1.0).coerceAtLeast(1.0)
    Row(Modifier.fillMaxWidth().height(160.dp).padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        history.forEachIndexed { index, point ->
            val isLatest = index == history.lastIndex
            val color = if (isLatest) Ids.colors.brand else Ids.colors.divider
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    formatMoney(point.liquidTotal), fontSize = 10.sp,
                    color = if (isLatest) Ids.colors.brand else Ids.colors.textSecondary,
                    fontWeight = if (isLatest) FontWeight.Bold else FontWeight.Normal,
                )
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    val fraction = (point.liquidTotal.toDouble() / max).toFloat().coerceIn(0.04f, 1f)
                    Box(Modifier.fillMaxWidth().fillMaxHeight(fraction).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(color))
                }
                Text(monthLabel(point.month), color = Ids.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

private fun monthLabel(month: String): String {
    val parts = month.split("-")
    if (parts.size != 2) return month
    val monthNum = parts[1].toIntOrNull() ?: return month
    return YearMonth.of(parts[0].toInt(), monthNum).month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
}
