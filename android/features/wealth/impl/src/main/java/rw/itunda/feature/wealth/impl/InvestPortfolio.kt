package rw.itunda.feature.wealth.impl

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
import rw.itunda.core.network.FundInvestmentRequest
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
                        "${if (positive) "+" else ""}${formatMoney(p.totalReturn)} RWF (${if (positive) "+" else ""}${"%.2f".format(p.totalReturnPercent)}%)",
                        color = if (positive) Ids.colors.success else Ids.colors.danger, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    )
                    if (!history.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Sparkline(history!!.map { it.value }, positive = positive)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Last 30 days -- based on your current holdings applied to real historical prices, not a full historical reconstruction",
                            color = Ids.colors.textSecondary, fontSize = 11.sp,
                        )
                    }
                }
                AddFundsCard(onFunded = ::load)
                Divider(color = Ids.colors.divider, thickness = 0.5.dp)
                if (p.holdings.isEmpty()) {
                    Text("You don't hold any real shares yet. Browse the Market tab to buy some.", color = Ids.colors.textSecondary, fontSize = 14.sp)
                } else {
                    p.holdings.forEach { HoldingRow(it) }
                }
            }
        }
    }
}

// Real Investment-account top-up (2026-08-04) -- see ApiService.kt's own
// FundInvestmentRequest doc comment: StocksService.fundInvestmentAccount (a real MAIN
// -> INVESTMENT internal transfer) had zero client anywhere, so a user with no
// pre-seeded investment balance had no in-app way to ever actually buy a stock.
@Composable
private fun AddFundsCard(onFunded: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var needsDeviceVerification by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    suspend fun doFund() {
        val value = amount.toDoubleOrNull()
        if (value == null || value <= 0) {
            error = "Enter a real amount."
            return
        }
        busy = true
        needsDeviceVerification = false
        try {
            NetworkClient.apiService.fundInvestmentAccount(UUID.randomUUID().toString(), FundInvestmentRequest(value))
            amount = ""
            expanded = false
            error = null
            onFunded()
        } catch (e: HttpException) {
            if (isDeviceNotVerifiedError(e)) needsDeviceVerification = true else error = superAppErrorMessage(e)
        } catch (e: IOException) {
            error = "Couldn't reach itunda. Check your connection and try again."
        } finally {
            busy = false
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Investment cash", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(
                if (expanded) "Cancel" else "Add funds",
                color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.pressScaleClickable { expanded = !expanded; error = null },
            )
        }
        Text("Move money from your main account into your investment account.", color = Ids.colors.textSecondary, fontSize = 12.sp)
        if (expanded) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IdsTextField(value = amount, onValueChange = { amount = it }, label = "Amount (RWF)", isAmount = true, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                        .background(Ids.colors.brand)
                        .pressScaleClickable(enabled = !busy) { coroutineScope.launch { doFund() } }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    Text(if (busy) "Working…" else "Add", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
    DeviceStepUpHost(
        visible = needsDeviceVerification,
        onDismiss = { needsDeviceVerification = false },
        onVerified = { doFund() },
    )
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
                "${if (positive) "+" else ""}${"%.2f".format(holding.`return`)}%",
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
