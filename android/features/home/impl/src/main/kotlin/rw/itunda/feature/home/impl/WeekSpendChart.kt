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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsTypography
import rw.itunda.core.network.TransactionDto

/** One day of outgoing spending for the chart. */
data class DaySpend(val date: LocalDate, val amount: Double)

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
 * The prototype's 7-day bars (v1038): a readout row (selected day's amount left, "Today ·
 * Sat 7 Nov" right), seven 30dp bars with 7/7/3/3 corners (selected = brand, others soft
 * brand), and day labels (selected = bold ink). Touch or drag across the bars to change
 * the selected day. Measured values: bar width 30dp, tallest 56dp, shortest 17dp.
 */
@Composable
internal fun ProtoWeekChart(days: List<DaySpend>, today: LocalDate = LocalDate.now(), modifier: Modifier = Modifier) {
    if (days.isEmpty()) return
    var selected by remember { mutableIntStateOf(days.lastIndex) }
    var widthPx by remember { mutableIntStateOf(1) }
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(600)) }
    val max = days.maxOf { it.amount }.coerceAtLeast(1.0)
    val day = days[selected.coerceIn(0, days.lastIndex)]

    fun indexAt(x: Float) = ((x / widthPx) * days.size).toInt().coerceIn(0, days.lastIndex)

    Column(modifier = modifier.fillMaxWidth().padding(top = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${String.format(Locale.US, "%,.0f", day.amount)} RWF",
                color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f),
            )
            Text(
                "${if (day.date == today) "Today · " else ""}${day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${day.date.dayOfMonth} ${day.date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}",
                color = Ids.colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(top = 8.dp)
                .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
                .pointerInput(days) { detectTapGestures { selected = indexAt(it.x) } }
                .pointerInput(days) { detectHorizontalDragGestures { change, _ -> selected = indexAt(change.position.x) } },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            days.forEachIndexed { i, d ->
                val h = (17f + (56f - 17f) * (d.amount / max).toFloat()).coerceAtMost(56f) * grow.value
                Box(
                    Modifier
                        .width(30.dp)
                        .height(h.dp)
                        .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                        .background(if (i == selected) Ids.colors.brand else Ids.colors.brand.copy(alpha = 0.14f)),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEachIndexed { i, d ->
                Text(
                    d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    color = if (i == selected) Ids.colors.textPrimary else Ids.colors.textSecondary,
                    fontSize = 13.sp, fontWeight = if (i == selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.width(30.dp),
                )
            }
        }
    }
}
