package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.SkeletonBlock
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
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.FundInvestmentRequest
import rw.itunda.core.network.isDeviceNotVerifiedError
import rw.itunda.core.network.PortfolioValuePointDto
import rw.itunda.core.network.SetPriceAlertRequest
import rw.itunda.core.network.StockDto
import rw.itunda.core.network.StockHoldingDto
import rw.itunda.core.network.StockPortfolioDto
import rw.itunda.core.network.StockPricePointDto
import rw.itunda.core.network.TradeStockRequest
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.util.UUID
import kotlin.math.max
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.ErrorCard

// Real Toss Securities-style stock investing UI (2026-07-20) -- the first Invest UI
// this feature has ever had on any client, ported from bank-mfe's own real BankDashboard
// StocksView the same session. Day-over-day movement/price history/portfolio history
// are all real deterministic simulations against itunda's own Rwanda RSE catalog, not
// live market data or fabricated randomness -- see the backend's StockCatalog.kt for
// the full account.

private enum class InvestMode { MARKET, PORTFOLIO, WATCHLIST }

@Composable
fun InvestScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var mode by remember { mutableStateOf(InvestMode.MARKET) }
    var selectedStock by remember { mutableStateOf<StockDto?>(null) }
    var watchlist by remember { mutableStateOf<List<StockDto>?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun loadWatchlist() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getStockWatchlist()
                if (res.success) watchlist = res.watchlist ?: emptyList()
            } catch (_: Exception) {
                // Non-critical -- only backs the star-fill state on the market list.
            }
        }
    }
    LaunchedEffect(Unit) { loadWatchlist() }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = "Invest", onBack = if (selectedStock != null) ({ selectedStock = null }) else onBack)

        if (selectedStock != null) {
            StockDetailContent(
                stock = selectedStock!!,
                isWatched = watchlist?.any { it.id == selectedStock!!.id } == true,
                onTraded = { },
                onWatchToggled = ::loadWatchlist,
            )
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp)
                .clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft).padding(4.dp),
        ) {
            listOf(InvestMode.MARKET to "Market", InvestMode.PORTFOLIO to "Portfolio", InvestMode.WATCHLIST to "Watchlist").forEach { (m, label) ->
                val selected = mode == m
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .background(if (selected) Ids.colors.brand else Color.Transparent)
                        .pressScaleClickable { mode = m }.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (selected) Color.White else Ids.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (mode) {
                InvestMode.MARKET -> item { MarketContent(watchlist, onOpen = { selectedStock = it }) }
                InvestMode.PORTFOLIO -> item { PortfolioContent() }
                InvestMode.WATCHLIST -> item { WatchlistContent(watchlist, onOpen = { selectedStock = it }) }
            }
        }
    }
}

@Composable
private fun MarketContent(watchlist: List<StockDto>?, onOpen: (StockDto) -> Unit) {
    var stocks by remember { mutableStateOf<List<StockDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // Real Toss/Naver 해외주식 (overseas stock trading, item 230) -- mirrors bank-mfe's
    // own All/Rwanda(RSE)/Overseas(NASDAQ) filter exactly.
    var marketFilter by remember { mutableStateOf("ALL") }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getStocks()
                if (res.success) stocks = res.stocks
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
        stocks == null -> SkeletonBlock(height = 220.dp)
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL" to "All", "RSE" to "Rwanda (RSE)", "NASDAQ" to "Overseas").forEach { (id, label) ->
                    val selected = marketFilter == id
                    Text(
                        label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) Ids.colors.brand else Ids.colors.textSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) Ids.colors.brand.copy(alpha = 0.12f) else Color.Transparent)
                            .pressScaleClickable { marketFilter = id }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            stocks!!.filter { marketFilter == "ALL" || it.market == marketFilter }
                .forEach { StockRow(it, isWatched = watchlist?.any { w -> w.id == it.id } == true, onClick = { onOpen(it) }) }
        }
    }
}

@Composable
private fun WatchlistContent(watchlist: List<StockDto>?, onOpen: (StockDto) -> Unit) {
    when {
        watchlist == null -> SkeletonBlock(height = 220.dp)
        watchlist.isEmpty() -> EmptyState("No stocks watched yet — open a stock in the Market tab and tap the star to follow it.")
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            watchlist.forEach { StockRow(it, isWatched = true, onClick = { onOpen(it) }) }
        }
    }
}

@Composable
private fun StockRow(stock: StockDto, isWatched: Boolean, onClick: () -> Unit) {
    val positive = stock.changePercent >= 0
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Ids.layout.cardCornerRadius))
            .background(Ids.colors.surface).pressScaleClickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stock.symbol, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (stock.market != "RSE") {
                    Text(
                        stock.market, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textSecondary,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Ids.colors.chip)
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }
            Text(stock.name, color = Ids.colors.textSecondary, fontSize = 12.sp)
        }
        if (isWatched) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${formatMoney(stock.price)} RWF", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (positive) Icons.Outlined.TrendingUp else Icons.Outlined.TrendingDown,
                    contentDescription = null,
                    tint = if (positive) Ids.colors.success else Ids.colors.danger,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    "${if (positive) "+" else ""}${"%.2f".format(stock.changePercent)}%",
                    color = if (positive) Ids.colors.success else Ids.colors.danger,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun PortfolioContent() {
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

@Composable
private fun StockDetailContent(stock: StockDto, isWatched: Boolean, onTraded: () -> Unit, onWatchToggled: () -> Unit) {
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

// Lightweight dependency-free bar sparkline -- no charting library exists anywhere in
// this app, matching the exact same "not disproportionate to a real MVP chart" call
// bank-mfe's own Sparkline component made the same session.
@Composable
private fun Sparkline(values: List<Double>, positive: Boolean) {
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

private fun formatMoney(value: Double): String {
    val rounded = Math.round(value * 100.0) / 100.0
    return if (rounded == Math.floor(rounded)) "%,d".format(rounded.toLong()) else "%,.2f".format(rounded)
}
