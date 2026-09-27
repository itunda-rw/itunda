package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.app.network.NetworkClient
import rw.itunda.app.network.PortfolioValuePointDto
import rw.itunda.app.network.StockDto
import rw.itunda.app.network.StockHoldingDto
import rw.itunda.app.network.StockPortfolioDto
import rw.itunda.app.network.StockPricePointDto
import rw.itunda.app.network.TradeStockRequest
import java.io.IOException
import java.util.UUID
import kotlin.math.max

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
                .clip(RoundedCornerShape(Ids.layout.controlRadius)).background(Ids.colors.surfaceSoft).padding(4.dp),
        ) {
            listOf(InvestMode.MARKET to "Market", InvestMode.PORTFOLIO to "Portfolio", InvestMode.WATCHLIST to "Watchlist").forEach { (m, label) ->
                val selected = mode == m
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(Ids.layout.tightCornerRadius))
                        .background(if (selected) Ids.colors.brand else Color.Transparent)
                        .clickable { mode = m }.padding(vertical = 8.dp),
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
        error != null -> ErrorCardInvest(error!!, onRetry = ::load)
        stocks == null -> Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth().height(220.dp)) {}
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            stocks!!.forEach { StockRow(it, isWatched = watchlist?.any { w -> w.id == it.id } == true, onClick = { onOpen(it) }) }
        }
    }
}

@Composable
private fun WatchlistContent(watchlist: List<StockDto>?, onOpen: (StockDto) -> Unit) {
    when {
        watchlist == null -> Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth().height(220.dp)) {}
        watchlist.isEmpty() -> Text("No stocks watched yet. Open a stock in the Market tab and tap the star to follow it.", color = Ids.colors.textSecondary, fontSize = 14.sp)
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
            .background(Ids.colors.surface).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stock.symbol, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
        error != null -> ErrorCardInvest(error!!, onRetry = ::load)
        portfolio == null -> Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth().height(220.dp)) {}
        else -> {
            val p = portfolio!!
            val positive = p.totalReturn >= 0
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
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
                }
                if (p.holdings.isEmpty()) {
                    Text("You don't hold any real shares yet. Browse the Market tab to buy some.", color = Ids.colors.textSecondary, fontSize = 14.sp)
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
    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
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
}

@Composable
private fun StockDetailContent(stock: StockDto, isWatched: Boolean, onTraded: () -> Unit, onWatchToggled: () -> Unit) {
    var history by remember { mutableStateOf<List<StockPricePointDto>?>(null) }
    var shares by remember { mutableStateOf("") }
    var buyMode by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var watching by remember { mutableStateOf(isWatched) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(stock.id) {
        try {
            val res = NetworkClient.apiService.getStockHistory(stock.id, 14)
            if (res.success) history = res.history
        } catch (_: Exception) {
            history = emptyList()
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
        coroutineScope.launch {
            try {
                val request = TradeStockRequest(stock.id, shareCount)
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
                modifier = Modifier.size(24.dp).clickable { toggleWatch() },
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
            Card(shape = RoundedCornerShape(Ids.layout.tightCornerRadius), modifier = Modifier.fillMaxWidth().height(48.dp)) {}
        } else if (history!!.isNotEmpty()) {
            Sparkline(history!!.map { it.price }, positive = positive)
            Text("Last 14 days -- real deterministic simulation, not live RSE data", color = Ids.colors.textSecondary, fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Ids.layout.controlRadius)).background(Ids.colors.surfaceSoft).padding(4.dp),
        ) {
            listOf(true to "Buy", false to "Sell").forEach { (isBuy, label) ->
                val selected = buyMode == isBuy
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(Ids.layout.tightCornerRadius))
                        .background(if (selected) (if (isBuy) Ids.colors.brand else Ids.colors.danger) else Color.Transparent)
                        .clickable { buyMode = isBuy }.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (selected) Color.White else Ids.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = shares, onValueChange = { shares = it }, placeholder = { Text("Shares") },
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier.clip(RoundedCornerShape(Ids.layout.controlRadius))
                    .background(if (buyMode) Ids.colors.brand else Ids.colors.danger)
                    .clickable(enabled = !submitting) { trade() }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Text(if (submitting) "Working…" else if (buyMode) "Buy" else "Sell", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
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

@Composable
private fun ErrorCardInvest(message: String, onRetry: () -> Unit) {
    Card(
        shape = RoundedCornerShape(Ids.layout.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = Ids.colors.surface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(message, color = Ids.colors.danger, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text("Retry", color = Ids.colors.brand, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onRetry))
        }
    }
}

private fun formatMoney(value: Double): String {
    val rounded = Math.round(value * 100.0) / 100.0
    return if (rounded == Math.floor(rounded)) rounded.toLong().toString() else "%.2f".format(rounded)
}
