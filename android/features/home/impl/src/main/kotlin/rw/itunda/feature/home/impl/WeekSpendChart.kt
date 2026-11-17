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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.platform.LocalConfiguration
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
 * The prototype's 7-day bars (v1038), measured from the rendered HTML: a 318dp-wide block
 * (readout row 29dp: selected day's amount left, "Today · Sat 7 Nov" right), seven 40.3dp
 * columns 6dp apart holding 30dp bars (7/7/3/3 corners, 17-56dp tall, selected = #8F89FF),
 * then 13sp day labels. Tap or drag across the bars to change the selected day.
 */
@Composable
internal fun ProtoWeekChart(days: List<DaySpend>, today: LocalDate = LocalDate.now(), modifier: Modifier = Modifier) {
    if (days.isEmpty()) return
    val p = homePalette()
    var selected by remember { mutableIntStateOf(days.lastIndex) }
    var widthPx by remember { mutableIntStateOf(1) }
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(600)) }
    val max = days.maxOf { it.amount }.coerceAtLeast(1.0)
    val day = days[selected.coerceIn(0, days.lastIndex)]
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val todayLabel = when (locale.language) { "rw" -> "Uyu munsi"; "fr" -> "Aujourd’hui"; else -> "Today" }

    fun indexAt(x: Float) = ((x / widthPx) * days.size).toInt().coerceIn(0, days.lastIndex)

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 36.dp).padding(top = 10.dp, bottom = 2.dp)) {
        Row(Modifier.fillMaxWidth().height(29.dp).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${String.format(Locale.US, "%,.0f", day.amount)} RWF",
                color = p.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.2).sp, modifier = Modifier.weight(1f),
            )
            Text(
                "${if (day.date == today) "$todayLabel · " else ""}${day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)} ${day.date.dayOfMonth} ${day.date.month.getDisplayName(TextStyle.SHORT, locale)}",
                color = p.mu, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(86.dp)
                .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
                .pointerInput(days) { detectTapGestures { selected = indexAt(it.x) } }
                .pointerInput(days) { detectHorizontalDragGestures { change, _ -> selected = indexAt(change.position.x) } },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            days.forEachIndexed { i, d ->
                val on = i == selected
                val h = (17f + (56f - 17f) * (d.amount / max).toFloat()).coerceAtMost(56f) * grow.value
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(66.dp), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier.width(30.dp).height(h.dp)
                                .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                                .background(if (on) p.barOn else p.tint),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                        color = if (on) p.text else p.mu, fontSize = 13.sp, lineHeight = 14.sp,
                        fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
