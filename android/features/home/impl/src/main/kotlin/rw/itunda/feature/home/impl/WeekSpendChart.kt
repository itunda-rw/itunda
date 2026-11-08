package rw.itunda.feature.home.impl

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsTypography
import rw.itunda.core.network.TransactionDto

/** One day of outgoing spending for the chart. */
internal data class DaySpend(val date: LocalDate, val amount: Double)

/**
 * Last 7 days of money going OUT of [userId] (completed only), oldest first, from the
 * same transaction history the history screen uses -- no new endpoint.
 */
internal fun weekSpend(transactions: List<TransactionDto>, userId: String, today: LocalDate = LocalDate.now()): List<DaySpend> {
    val zone = ZoneId.systemDefault()
    val byDay = transactions
        .filter { it.senderId == userId && it.status == "COMPLETED" }
        .mapNotNull { t -> runCatching { Instant.parse(t.createdAt).atZone(zone).toLocalDate() to t.amount }.getOrNull() }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, v) -> v.sum() }
    return (6 downTo 0).map { back -> today.minusDays(back.toLong()).let { d -> DaySpend(d, byDay[d] ?: 0.0) } }
}

/**
 * Prototype v1021: a 7-day spending bar chart on Home. Bars grow in on first show; touch
 * or drag across them and the readout above rolls to that day. Solid fills only.
 */
@Composable
internal fun WeekSpendChart(days: List<DaySpend>, hidden: Boolean, modifier: Modifier = Modifier) {
    if (days.isEmpty() || days.all { it.amount == 0.0 }) return
    var selected by remember { mutableIntStateOf(days.lastIndex) }
    var widthPx by remember { mutableIntStateOf(1) }
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(600)) }
    val max = days.maxOf { it.amount }.coerceAtLeast(1.0)
    val day = days[selected.coerceIn(0, days.lastIndex)]

    fun indexAt(x: Float) = ((x / widthPx) * days.size).toInt().coerceIn(0, days.lastIndex)

    Column(modifier = modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(
            "Spent ${if (day.date == LocalDate.now()) "today" else day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}",
            style = IdsTypography.Body2, color = Ids.colors.textSecondary,
        )
        Text(
            if (hidden) "••••••" else "${String.format(Locale.US, "%,.0f", day.amount)} RWF",
            style = IdsTypography.Subtitle1, color = Ids.colors.textPrimary,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .padding(top = 12.dp)
                .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
                .pointerInput(days) { detectTapGestures { selected = indexAt(it.x) } }
                .pointerInput(days) { detectHorizontalDragGestures { change, _ -> selected = indexAt(change.position.x) } },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            days.forEachIndexed { i, d ->
                val fraction = (d.amount / max).toFloat().coerceIn(0.04f, 1f) * grow.value
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (i == selected) Ids.colors.brand else Ids.colors.surfaceSoft),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            days.forEach { d ->
                Text(
                    d.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                    style = IdsTypography.Body2, color = Ids.colors.textTertiary,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}
