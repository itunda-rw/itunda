package rw.itunda.feature.wealth.impl

import java.util.Locale
import rw.itunda.core.designsystem.components.formatMoney
import androidx.compose.foundation.background
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
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.DeviceStepUpHost
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.PortfolioValuePointDto
import rw.itunda.core.network.StockHoldingDto
import rw.itunda.core.network.StockPortfolioDto
import rw.itunda.core.network.isDeviceNotVerifiedError
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.util.UUID
import kotlin.math.max

@Composable
internal fun PortfolioContent() {
    var portfolio by remember { mutableStateOf<StockPortfolioDto?>(null) }
    var history by remember { mutableStateOf<List<PortfolioValuePointDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val portfolioRes = NetworkClient.apiService.getStockPortfolio()
                val historyRes = NetworkClient.apiService.getPortfolioHistory()
                if (portfolioRes.success) portfolio = portfolioRes.portfolio
                if (historyRes.success) history = historyRes.history
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    when {
        error != null -> ErrorCard(error!!, onRetry = ::load)
        portfolio == null -> SkeletonBlock(height = 220.dp)
        else -> {
            val p = portfolio!!
            val positive = p.totalReturn >= 0
            // Real fix (2026-08-24, flat-design sweep): dropped the Card wrappers around
            // the summary/add-funds sections and each holding row (docs/UI_UX_GUIDELINES.md
            // §10) -- a divider now marks the boundary between the summary+add-funds
            // section and the holdings list below it, matching ShellSection's shape.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text("Total value", color = Ids.colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("${formatMoney(p.totalValue)} RWF", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                    Text(
                        "${if (positive) "+" else ""}${formatMoney(p.totalReturn)} RWF (${if (positive) "+" else ""}${String.format(Locale.US, "%.2f", p.totalReturnPercent)}%)",
                        color = if (positive) Ids.colors.success else Ids.colors.danger, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    )
                    if (!history.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Sparkline(history!!.map { it.value }, positive = positive)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Last 30 days of simulated practice-portfolio value",
                            color = Ids.colors.textSecondary, fontSize = 11.sp,
                        )
                    }
                }
                Text(p.virtualCashBalance?.let { "Virtual practice cash: ${formatMoney(it)} RWF. No real money moves." } ?: "Practice cash is unavailable from this server version.", color = Ids.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                Divider(color = Ids.colors.divider, thickness = 0.5.dp)
                if (p.holdings.isEmpty()) {
                    Text("No practice shares yet. Browse the Market tab to place a practice order.", color = Ids.colors.textSecondary, fontSize = 14.sp)
                } else {
                    p.holdings.forEach { HoldingRow(it) }
                }
            }
        }
    }
}

@Composable
private fun HoldingRow(holding: StockHoldingDto) {
    val positive = holding.`return` >= 0
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(holding.symbol, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("${formatMoney(holding.value)} RWF", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${holding.shares} shares @ ${formatMoney(holding.avgPrice)} avg", color = Ids.colors.textSecondary, fontSize = 12.sp)
            Text(
                "${if (positive) "+" else ""}${String.format(Locale.US, "%.2f", holding.`return`)}%",
                color = if (positive) Ids.colors.success else Ids.colors.danger, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            )
        }
    }
}

// Lightweight dependency-free bar sparkline -- no charting library exists anywhere in
// this app, matching the exact same "not disproportionate to a real MVP chart" call
// bank-mfe's own Sparkline component made the same session.
@Composable
internal fun Sparkline(values: List<Double>, positive: Boolean) {
    if (values.isEmpty()) return
    val min = values.min()
    val max = values.max()
    val range = max(max - min, 0.0001)
    Row(modifier = Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.Bottom) {
        values.forEach { v ->
            val heightFraction = max(0.08, (v - min) / range).toFloat()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 1.dp)
                    .fillMaxHeight(heightFraction)
                    .background(if (positive) Ids.colors.success else Ids.colors.danger, RoundedCornerShape(2.dp)),
            )
        }
    }
}
