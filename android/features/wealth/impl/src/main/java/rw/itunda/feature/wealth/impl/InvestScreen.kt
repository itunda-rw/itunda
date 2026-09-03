package rw.itunda.feature.wealth.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
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
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.StockDto
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException

// Real Toss Securities-style stock investing UI (2026-07-20) -- the first Invest UI
// this feature has ever had on any client, ported from bank-mfe's own real BankDashboard
// StocksView the same session. Day-over-day movement/price history/portfolio history
// are all real deterministic simulations against itunda's own Rwanda RSE catalog, not
// live market data or fabricated randomness -- see the backend's StockCatalog.kt for
// the full account.
//
// Real content moved 2026-09-03 (Wealth Feature-module extraction, filling the
// previously-empty :features:wealth scaffold noted in CLAUDE.md's own "product-scope
// gap" line): relocated from :app's own ui/InvestScreen.kt, zero MainViewModel/
// R.string coupling, split across InvestScreen.kt (market/watchlist), InvestPortfolio.kt
// (portfolio + add-funds), and StockDetailScreen.kt (buy/sell + price alerts) to stay
// under the 500-line file-size-lint cap.

internal enum class InvestMode { MARKET, PORTFOLIO, WATCHLIST }

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
        watchlist.isEmpty() -> rw.itunda.core.designsystem.components.EmptyState("No stocks watched yet — open a stock in the Market tab and tap the star to follow it.")
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            watchlist.forEach { StockRow(it, isWatched = true, onClick = { onOpen(it) }) }
        }
    }
}

@Composable
internal fun StockRow(stock: StockDto, isWatched: Boolean, onClick: () -> Unit) {
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

internal fun formatMoney(value: Double): String {
    val rounded = Math.round(value * 100.0) / 100.0
    return if (rounded == Math.floor(rounded)) "%,d".format(rounded.toLong()) else "%,.2f".format(rounded)
}
