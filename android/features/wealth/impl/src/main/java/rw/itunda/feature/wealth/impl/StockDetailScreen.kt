package rw.itunda.feature.wealth.impl

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
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
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SetPriceAlertRequest
import rw.itunda.core.network.StockDto
import rw.itunda.core.network.StockPricePointDto
import rw.itunda.core.network.TradeStockRequest
import rw.itunda.core.network.isDeviceNotVerifiedError
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.util.UUID

@Composable
internal fun StockDetailContent(stock: StockDto, isWatched: Boolean, onTraded: () -> Unit, onWatchToggled: () -> Unit) {
    var history by remember { mutableStateOf<List<StockPricePointDto>?>(null) }
    var shares by remember { mutableStateOf("") }
    var buyMode by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var watching by remember { mutableStateOf(isWatched) }
    // Real device binding step-up (2026-07-21) -- Stocks buy/sell was a real gap:
    // already correctly enforced server-side (a real 403 DEVICE_NOT_VERIFIED) but
    // showed only a generic error, same fix already applied to Transfer/Savings.
    var needsDeviceVerification by remember { mutableStateOf(false) }
    // Real Toss Securities 목표가 알림 (target price alert, section 113/167) -- found via
    // a fresh "defined but uncalled" endpoint sweep: the backend shipped fully
    // live-verified 2026-08-17 but had zero client anywhere, on any platform. This is
    // Android's first wiring for it.
    var alertTargetPrice by remember { mutableStateOf<Double?>(null) }
    var alertDirection by remember { mutableStateOf<String?>(null) }
    var alertTriggeredAt by remember { mutableStateOf<String?>(null) }
    var alertExpanded by remember { mutableStateOf(false) }
    var alertInput by remember { mutableStateOf("") }
    var alertAbove by remember { mutableStateOf(true) }
    var alertBusy by remember { mutableStateOf(false) }
    var alertError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(stock.id) {
        try {
            val res = NetworkClient.apiService.getStockHistory(stock.id, 14)
            if (res.success) history = res.history
        } catch (_: Exception) {
            history = emptyList()
        }
        try {
            val res = NetworkClient.apiService.getPriceAlert(stock.id)
            alertTargetPrice = res.targetPrice
            alertDirection = res.targetDirection
            alertTriggeredAt = res.alertTriggeredAt
        } catch (_: Exception) {
            // Non-critical -- the alert section just shows "no alert set".
        }
    }

    fun setAlert() {
        val target = alertInput.toDoubleOrNull()
        if (target == null || target <= 0) {
            alertError = "Enter a real target price."
            return
        }
        alertBusy = true
        alertError = null
        coroutineScope.launch {
            try {
                val direction = if (alertAbove) "ABOVE" else "BELOW"
                NetworkClient.apiService.setPriceAlert(stock.id, SetPriceAlertRequest(target, direction))
                alertTargetPrice = target
                alertDirection = direction
                alertTriggeredAt = null
                alertInput = ""
                alertExpanded = false
                if (!watching) { watching = true; onWatchToggled() }
            } catch (e: Exception) {
                alertError = "Could not set that alert."
            } finally {
                alertBusy = false
            }
        }
    }

    fun clearAlert() {
        alertBusy = true
        coroutineScope.launch {
            try {
                NetworkClient.apiService.clearPriceAlert(stock.id)
                alertTargetPrice = null
                alertDirection = null
                alertTriggeredAt = null
            } catch (_: Exception) {
                // Non-critical -- same "no error surfaced" convention the watch toggle above uses.
            } finally {
                alertBusy = false
            }
        }
    }

    fun toggleWatch() {
        coroutineScope.launch {
            try {
                if (watching) NetworkClient.apiService.unwatchStock(stock.id) else NetworkClient.apiService.watchStock(stock.id)
                watching = !watching
                onWatchToggled()
            } catch (_: Exception) {
                // Non-critical -- the star just doesn't flip.
            }
        }
    }

    fun trade() {
        val shareCount = shares.toDoubleOrNull()
        if (shareCount == null || shareCount <= 0) {
            error = "Enter a real number of shares."
            return
        }
        submitting = true
        needsDeviceVerification = false
        coroutineScope.launch {
            try {
                val request = TradeStockRequest(stock.id, shareCount)
                val key = UUID.randomUUID().toString()
                if (buyMode) NetworkClient.apiService.buyStock(key, request) else NetworkClient.apiService.sellStock(key, request)
                shares = ""
                error = null
                onTraded()
            } catch (e: HttpException) {
                if (isDeviceNotVerifiedError(e)) {
                    needsDeviceVerification = true
                } else {
                    error = superAppErrorMessage(e)
                }
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                submitting = false
            }
        }
    }

    val positive = stock.changePercent >= 0

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${stock.symbol} · ${stock.marketCap}", color = Ids.colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Icon(
                if (watching) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = "Toggle watch",
                tint = if (watching) Color(0xFFFFC107) else Ids.colors.textSecondary,
                modifier = Modifier.size(24.dp).pressScaleClickable { toggleWatch() },
            )
        }
        Text(stock.name, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("${formatMoney(stock.price)} RWF", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 26.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (positive) Icons.Outlined.TrendingUp else Icons.Outlined.TrendingDown,
                contentDescription = null, tint = if (positive) Ids.colors.success else Ids.colors.danger, modifier = Modifier.size(16.dp),
            )
            Text(
                "${if (positive) "+" else ""}${formatMoney(stock.change)} (${if (positive) "+" else ""}${"%.2f".format(stock.changePercent)}%) today",
                color = if (positive) Ids.colors.success else Ids.colors.danger, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (history == null) {
            Card(shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) {}
        } else if (history!!.isNotEmpty()) {
            Sparkline(history!!.map { it.price }, positive = positive)
            Text("Last 14 days -- real deterministic simulation, not live RSE data", color = Ids.colors.textSecondary, fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft).padding(4.dp),
        ) {
            listOf(true to "Buy", false to "Sell").forEach { (isBuy, label) ->
                val selected = buyMode == isBuy
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .background(if (selected) (if (isBuy) Ids.colors.brand else Ids.colors.danger) else Color.Transparent)
                        .pressScaleClickable { buyMode = isBuy }.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (selected) Color.White else Ids.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IdsTextField(value = shares, onValueChange = { shares = it }, label = "Shares", modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    .background(if (buyMode) Ids.colors.brand else Ids.colors.danger)
                    .pressScaleClickable(enabled = !submitting) { trade() }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Text(if (submitting) "Working…" else if (buyMode) "Buy" else "Sell", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        DeviceStepUpHost(
            visible = needsDeviceVerification,
            onDismiss = { needsDeviceVerification = false },
            onVerified = {
                needsDeviceVerification = false
                submitting = true
                try {
                    val request = TradeStockRequest(stock.id, shares.toDoubleOrNull() ?: 0.0)
                    val key = UUID.randomUUID().toString()
                    if (buyMode) NetworkClient.apiService.buyStock(key, request) else NetworkClient.apiService.sellStock(key, request)
                    shares = ""
                    error = null
                    onTraded()
                } catch (e: HttpException) {
                    error = superAppErrorMessage(e)
                } catch (e: IOException) {
                    error = "Couldn't reach itunda. Check your connection and try again."
                } finally {
                    submitting = false
                }
            },
        )

        Spacer(modifier = Modifier.height(16.dp))
        // Real Toss Securities 목표가 알림 (target price alert, section 113/167).
        if (alertTargetPrice != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(IdsIcons.Bell, contentDescription = null, tint = Ids.colors.textPrimary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Alert set: notify when ${if (alertDirection == "ABOVE") "≥" else "≤"} ${formatMoney(alertTargetPrice!!)} RWF",
                            color = Ids.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                    if (alertTriggeredAt != null) {
                        Text("Already triggered -- set a new target to re-arm it.", color = Ids.colors.textSecondary, fontSize = 12.sp)
                    }
                }
                Text(
                    "Remove", color = Ids.colors.danger, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.pressScaleClickable(enabled = !alertBusy) { clearAlert() },
                )
            }
        } else if (alertExpanded) {
            Text("Notify me when the price goes", color = Ids.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft).padding(4.dp)) {
                listOf(true to "Above", false to "Below").forEach { (isAbove, label) ->
                    val selected = alertAbove == isAbove
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                            .background(if (selected) Ids.colors.brand else Color.Transparent)
                            .pressScaleClickable { alertAbove = isAbove }.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, color = if (selected) Color.White else Ids.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IdsTextField(value = alertInput, onValueChange = { alertInput = it }, label = "Target price (RWF)", modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Ids.colors.brand)
                        .pressScaleClickable(enabled = !alertBusy) { setAlert() }.padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    Text(if (alertBusy) "Working…" else "Set", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            alertError?.let { Text(it, color = Ids.colors.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.pressScaleClickable { alertExpanded = true },
            ) {
                Icon(IdsIcons.Bell, contentDescription = null, tint = Ids.colors.brand, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Set a price alert", color = Ids.colors.brand, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
