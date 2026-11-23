package rw.itunda.feature.wealth.impl

import java.util.Locale
import rw.itunda.core.designsystem.components.formatMoney
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.stringResource
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

internal enum class InvestMode { PORTFOLIO, MARKET, ACTIVITY }

@Composable
fun InvestScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var mode by remember { mutableStateOf(InvestMode.PORTFOLIO) }
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
        rw.itunda.core.designsystem.components.ProtoTopBar("Invest", if (selectedStock != null) ({ selectedStock = null }) else onBack)

        if (selectedStock != null) {
            StockDetailContent(
                stock = selectedStock!!,
                isWatched = watchlist?.any { it.id == selectedStock!!.id } == true,
                onTraded = { },
                onWatchToggled = ::loadWatchlist,
            )
            return@Column
        }

        InvestTabs(mode) { mode = it }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Ids.layout.screenHorizontal)) {
            when (mode) {
                InvestMode.MARKET -> MarketContent(watchlist, onOpen = { selectedStock = it })
                InvestMode.PORTFOLIO -> PortfolioContent(onBrowse = { mode = InvestMode.MARKET })
                InvestMode.ACTIVITY -> InvestActivityEmpty(onBrowse = { mode = InvestMode.MARKET })
            }
        }
        if (mode == InvestMode.PORTFOLIO) InvestBottomCta(onTbills = { mode = InvestMode.MARKET }, onBrowse = { mode = InvestMode.MARKET })
    }
}

/** Static empty-portfolio composition used by the screenshot harness (same pieces as the live screen, no network). */
@Composable
fun InvestPortfolioPreview() {
    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        rw.itunda.core.designsystem.components.ProtoTopBar("Invest") {}
        InvestTabs(InvestMode.PORTFOLIO) {}
        Column(Modifier.weight(1f).padding(horizontal = Ids.layout.screenHorizontal)) {
            Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp)) {
                Text("Invested value", color = Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 20.sp)
                Text("0 RWF", color = Ids.colors.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = (-1.2).sp)
                Spacer(Modifier.height(20.dp))
                InvestIndexHeader()
            }
            InvestStartCard {}
        }
        InvestBottomCta({}, {})
    }
}

/** Prototype tabs: underline style on Portfolio (43.8dp strip, 17sp/700), pill segmented control on Market/Activity. */
@Composable
private fun InvestTabs(mode: InvestMode, onMode: (InvestMode) -> Unit) {
    val items = listOf(InvestMode.PORTFOLIO to "Portfolio", InvestMode.MARKET to "Market", InvestMode.ACTIVITY to "Activity")
    if (mode == InvestMode.PORTFOLIO) {
        Row(Modifier.fillMaxWidth().padding(start = 25.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            items.forEach { (m, label) ->
                val selected = mode == m
                Column(Modifier.width(IntrinsicSize.Max).pressScaleClickable { onMode(m) }.padding(top = 7.dp)) {
                    Text(label, color = if (selected) Ids.colors.textPrimary else Ids.colors.textTertiary, fontSize = 17.sp, lineHeight = 23.8.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.176).sp)
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.height(2.dp).fillMaxWidth().background(if (selected) Ids.colors.textPrimary else Color.Transparent))
                }
            }
        }
        androidx.compose.material3.HorizontalDivider(color = Ids.colors.divider, thickness = 0.5.dp)
    } else {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(Ids.colors.surfaceSoft).padding(7.dp)) {
            items.forEach { (m, label) ->
                val selected = mode == m
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(21.dp)).background(if (selected) Ids.colors.brand else Color.Transparent).pressScaleClickable { onMode(m) }, contentAlignment = Alignment.Center) {
                    Text(label, color = if (selected) Color(0xFF17171C) else Ids.colors.textTertiary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun InvestActivityEmpty(onBrowse: () -> Unit) {
    Text("History", color = Ids.colors.textPrimary, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, modifier = Modifier.padding(top = 12.dp))
    Column(Modifier.fillMaxWidth().padding(top = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        rw.itunda.core.designsystem.components.ItundaArt("receipt", rw.itunda.core.designsystem.components.ItundaArtSet.Proto, 96.dp, fit = true)
        Text("No trades yet. Your buys, sells, dividends and Treasury bills show up here.", color = Ids.colors.textTertiary, fontSize = 17.sp, lineHeight = 24.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 20.dp, start = 16.dp, end = 16.dp))
    }
    Box(Modifier.padding(top = 40.dp).fillMaxWidth().height(58.dp).clip(RoundedCornerShape(18.dp)).background(Ids.colors.brand).pressScaleClickable(onClick = onBrowse), contentAlignment = Alignment.Center) {
        Text("Browse stocks", color = Color(0xFF17171C), fontSize = 18.sp, letterSpacing = (-0.36).sp, fontWeight = FontWeight.Bold)
    }
    InvestFinePrint()
}

@Composable
internal fun InvestFinePrint() {
    Text("Money for buying comes from your itunda Bank balance. itunda is not a licensed broker and this is not investment advice.", color = Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 24.dp, bottom = 24.dp))
}

@Composable
private fun InvestBottomCta(onTbills: () -> Unit, onBrowse: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 28.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFEEEDFF)).pressScaleClickable(onClick = onTbills), contentAlignment = Alignment.Center) {
            Text("Treasury bills", color = Color(0xFF5B54D6), fontSize = 18.sp, letterSpacing = (-0.36).sp, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(18.dp)).background(Ids.colors.brand).pressScaleClickable(onClick = onBrowse), contentAlignment = Alignment.Center) {
            Text("Browse stocks", color = Color(0xFF17171C), fontSize = 18.sp, letterSpacing = (-0.36).sp, fontWeight = FontWeight.Bold)
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
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("ALL" to "Stocks", "BONDS" to "Bonds", "IPO" to "IPO", "WATCH" to "Watchlist").forEach { (id, label) ->
                    val selected = marketFilter == id
                    Text(
                        label, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        color = if (selected) Ids.colors.background else Ids.colors.textTertiary,
                        modifier = Modifier.height(40.dp).clip(RoundedCornerShape(20.dp))
                            .background(if (selected) Ids.colors.textPrimary else Ids.colors.surfaceSoft)
                            .pressScaleClickable { marketFilter = id }
                            .padding(horizontal = 18.dp).wrapContentHeight(Alignment.CenterVertically),
                    )
                }
            }
            if (marketFilter == "BONDS" || marketFilter == "IPO") Text(if (marketFilter == "BONDS") "Treasury bills and bonds will list here." else "No IPOs open right now.", color = Ids.colors.textTertiary, fontSize = 15.sp, lineHeight = 22.sp)
            if (marketFilter == "WATCH") WatchlistContent(watchlist, onOpen = onOpen)
            if (marketFilter == "ALL") stocks!!
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
                    "${if (positive) "+" else ""}${String.format(Locale.US, "%.2f", stock.changePercent)}%",
                    color = if (positive) Ids.colors.success else Ids.colors.danger,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}



/** Feature composition entry point. Keeps the app shell independent of concrete screen names. */
@Composable
fun InvestEntryPoint(
    onBack: () -> Unit,
) {
    InvestScreen(onBack)
}
