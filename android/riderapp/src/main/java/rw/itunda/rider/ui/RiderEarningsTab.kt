package rw.itunda.rider.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import rw.itunda.core.designsystem.components.IdsLoading
import rw.itunda.core.designsystem.components.IdsSegmentedControl
import rw.itunda.core.designsystem.components.formatMoney
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsTypography
import rw.itunda.rider.network.CommerceOrderDto
import rw.itunda.rider.network.EatsOrderDto

private enum class EarningsPeriod(val label: String, val days: Long) {
    Today("Today", 1),
    Week("Week", 7),
    Month("Month", 30),
}

/** Yyyy-mm-dd prefix of an ISO timestamp, or null if the backend sent something unexpected. */
private fun orderDate(createdAt: String): LocalDate? = runCatching { LocalDate.parse(createdAt.take(10)) }.getOrNull()

/**
 * Earnings, computed only from real data this app already receives: the delivery fee of
 * the rider's own DELIVERED Eats orders. Commerce (package) orders model no rider
 * payout field, so they are counted as deliveries but never summed into money. There is
 * no cash-out here on purpose: the backend exposes no rider payout endpoint, and a
 * button that pretends to pay out is the "fake success" failure in AI_AGENT_SELF_CHECK.md.
 */
@Composable
fun RiderEarningsTab(
    eatsMine: List<EatsOrderDto>?,
    commerceMine: List<CommerceOrderDto>?,
    today: LocalDate = LocalDate.now(),
) {
    var period by remember { mutableStateOf(EarningsPeriod.Week) }
    if (eatsMine == null && commerceMine == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { IdsLoading() }
        return
    }
    val since = today.minusDays(period.days - 1)
    val deliveredEats = eatsMine.orEmpty().filter { it.status == "DELIVERED" }
    val inPeriod = deliveredEats.filter { o -> orderDate(o.createdAt)?.let { !it.isBefore(since) && !it.isAfter(today) } == true }
    val packages = commerceMine.orEmpty().count { c ->
        c.status == "DELIVERED" && orderDate(c.createdAt)?.let { !it.isBefore(since) && !it.isAfter(today) } == true
    }
    val total = inPeriod.sumOf { it.deliveryFee }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
                Text("Earnings", style = IdsTypography.Title1, color = Ids.colors.textPrimary)
                Spacer(modifier = Modifier.height(16.dp))
                IdsSegmentedControl(
                    options = EarningsPeriod.entries.map { it to it.label },
                    selected = period,
                    onSelect = { period = it },
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text("${formatMoney(total)} RWF", style = IdsTypography.LargeAmount, color = Ids.colors.textPrimary)
                Text(
                    "${inPeriod.size} food deliveries" + if (packages > 0) " · $packages package deliveries" else "",
                    style = IdsTypography.Body2,
                    color = Ids.colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(24.dp))
                WeekBars(deliveredEats, today)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Delivery fees from your delivered food orders, grouped by the day the order was placed. " +
                        "Package deliveries don't report a rider payout, so they're counted but not added up.",
                    style = IdsTypography.Typography7,
                    color = Ids.colors.textTertiary,
                )
            }
        }
    }
}

/** Last seven days, one bar per day, the best day highlighted in brand color. */
@Composable
private fun WeekBars(delivered: List<EatsOrderDto>, today: LocalDate) {
    val days = (6L downTo 0L).map { today.minusDays(it) }
    val totals = days.map { d -> delivered.filter { orderDate(it.createdAt) == d }.sumOf { it.deliveryFee } }
    val max = totals.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
    val best = totals.indices.maxByOrNull { totals[it] }?.takeIf { totals[it] > 0.0 }
    Row(
        modifier = Modifier.fillMaxWidth().height(120.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEachIndexed { i, d ->
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((88 * (totals[i] / max)).coerceAtLeast(4.0).dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (i == best) Ids.colors.brand else Ids.colors.surfaceSoft),
                    )
                }
                Text(
                    d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() },
                    style = IdsTypography.Typography7,
                    color = Ids.colors.textTertiary,
                )
            }
        }
    }
}
