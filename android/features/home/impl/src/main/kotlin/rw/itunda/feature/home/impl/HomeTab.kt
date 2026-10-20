package rw.itunda.feature.home.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.theme.Ids

// Moved here from :app's ItundaAppScreen.kt (2026-09-02, Home Feature-module
// decomposition -- following the user's explicit "Continue into HomeTab next"
// direction, extending the "Full ItundaAppScreen decomposition" scope beyond
// Banking/Credit into a genuinely cross-vertical tab with no pre-existing Feature
// scaffold on either platform). See [[project_itunda_feature_isolation]] for the
// full account of why Home needed a NEW module rather than reusing an existing one.
//
// Real Naver-style Home redesign (2026-08-14, direct user reference: 5 real Naver
// Home screenshots -- search bar, weather/stock widgets, a Clip video grid, an
// infinite content feed). itunda has no weather/entertainment content to show
// honestly, but it IS a real super app with real cross-vertical content -- the same
// Marketplace/Community/Jobs/Property "my neighborhood" endpoints Explore's own
// screens already call, just merged into one feed here. Each source fetches its own
// minimal real data (same "each screen fetches its own minimal real data" convention
// BankHubScreen's own depositProtection/creditScore fetches establish), not pushed
// into MainViewModel as a global concern.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTab(
    // Real decoupling (2026-09-02) -- was `viewModel: MainViewModel` (the whole
    // :app-only ViewModel); narrowed to just the 6 values this screen actually
    // reads, hoisted to plain params/callbacks, matching BankHubScreen's own
    // slice-2 precedent.
    discoverItems: List<rw.itunda.core.network.DiscoverItem>,
    unreadNotificationCount: Int,
    isOffline: Boolean,
    primaryAccount: rw.itunda.core.network.Account?,
    neighborhoodSet: Boolean,
    isRefreshing: Boolean,
    onRetry: () -> Unit,
    onOpenPay: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenOverview: () -> Unit = {},
    onOpenBank: () -> Unit = {},
    onOpenIdentity: () -> Unit = {},
    onOpenLoans: () -> Unit = {},
    onOpenAccountDetail: () -> Unit = {},
    onOpenMarketplace: () -> Unit = {},
    onOpenCommunity: () -> Unit = {},
    onOpenJobs: () -> Unit = {},
    onOpenProperty: () -> Unit = {},
    onOpenInvest: () -> Unit = {},
    onOpenShop: () -> Unit = {},
) {
    // Real, minimal usage signal (2026-08-10) -- see the "itunda: the wedge, not the
    // mirror" strategy memo, recommendation (ii), and rw.itunda.core.network.
    // recordAnalyticsEvent's own doc comment. Fired once per real composition of
    // Home, the baseline every retention question is measured against -- same event
    // name/shape bank-mfe's identical HomeView effect already fires.
    LaunchedEffect(Unit) { rw.itunda.core.network.recordAnalyticsEvent("home_view") }

    var stocks by remember { mutableStateOf<List<rw.itunda.core.network.StockDto>>(emptyList()) }
    var trendingListings by remember { mutableStateOf<List<rw.itunda.core.network.ListingDto>>(emptyList()) }
    var feedEntries by remember { mutableStateOf<List<HomeFeedEntry>>(emptyList()) }
    LaunchedEffect(Unit) {
        try { stocks = rw.itunda.core.network.NetworkClient.apiService.getStocks().stocks.take(2) } catch (_: Exception) {}
    }
    // Real fix (2026-08-26, live-caught: uiautomator logs showed 4 guaranteed
    // 400s -- NeighborhoodNotSetException -- firing on every single Home load
    // for any user who hasn't set a neighborhood yet). All 4 of these are
    // real "my neighborhood" endpoints that unconditionally throw when
    // MarketplaceService.myNeighborhood's own real caller.neighborhood check
    // fails server-side; the try/catch below already degraded gracefully
    // (empty feed, no crash) but still wasted 4 real round-trips + 4 noisy
    // error logs every load.
    LaunchedEffect(neighborhoodSet) {
        if (!neighborhoodSet) return@LaunchedEffect
        val listings = try { rw.itunda.core.network.NetworkClient.apiService.getListingsMyNeighborhood().listings } catch (_: Exception) { emptyList() }
        val posts = try { rw.itunda.core.network.NetworkClient.apiService.getCommunityPostsMyNeighborhood().posts } catch (_: Exception) { emptyList() }
        val jobs = try { rw.itunda.core.network.NetworkClient.apiService.getJobPostsMyNeighborhood().posts } catch (_: Exception) { emptyList() }
        val properties = try { rw.itunda.core.network.NetworkClient.apiService.getPropertyListingsMyNeighborhood().listings } catch (_: Exception) { emptyList() }
        // Listings with a real photo lead the Trending grid (Naver's own Clip section
        // is image-first); the rest -- including photo-less listings -- flow into the
        // merged feed below like every other vertical.
        val (withPhoto, withoutPhoto) = listings.partition { !it.photoUrl.isNullOrBlank() }
        trendingListings = withPhoto.take(4)
        val trendingIds = trendingListings.map { it.id }.toSet()
        feedEntries = (
            (listings.filter { it.id !in trendingIds }).map {
                HomeFeedEntry(it.id, "marketplace", it.title, String.format(Locale.US, "%,.0f RWF", it.price), it.createdAt, it.photoUrl)
            } +
            posts.map { HomeFeedEntry(it.id, "community", it.title, it.body.take(80), it.createdAt) } +
            jobs.map { HomeFeedEntry(it.id, "jobs", it.title, String.format(Locale.US, "${it.payType} · %,.0f RWF", it.payAmount), it.createdAt) } +
            properties.map { HomeFeedEntry(it.id, "property", it.title, String.format(Locale.US, "%,.0f RWF", it.price), it.createdAt) }
        ).sortedByDescending { it.createdAt }.take(30)
    }
    // Real blended "universal search" (2026-08-14, direct user reference: real Naver
    // search results blend multiple content types on one page -- products, places,
    // posts -- each with its own card style, not a single flat list). itunda's own
    // equivalents are its 5 real content verticals; all fired in parallel (matching
    // this file's own "each screen fetches its own minimal real data" convention),
    // each independently null (loading) / empty (no matches) / populated, rendered as
    // labeled sections rather than one merged, type-blind list.
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<rw.itunda.core.network.ProductSearchResultDto>?>(null) }
    var marketplaceResults by remember { mutableStateOf<List<HomeFeedEntry>?>(null) }
    var communityResults by remember { mutableStateOf<List<HomeFeedEntry>?>(null) }
    var jobResults by remember { mutableStateOf<List<HomeFeedEntry>?>(null) }
    var propertyResults by remember { mutableStateOf<List<HomeFeedEntry>?>(null) }
    val searchScope = rememberCoroutineScope()
    fun runSearch(query: String) {
        searchResults = null; marketplaceResults = null; communityResults = null; jobResults = null; propertyResults = null
        searchScope.launch {
            searchResults = try { rw.itunda.core.network.NetworkClient.apiService.searchProducts(query).products } catch (_: Exception) { emptyList() }
        }
        searchScope.launch {
            marketplaceResults = try {
                rw.itunda.core.network.NetworkClient.apiService.searchListings(query).listings.map {
                    HomeFeedEntry(it.id, "marketplace", it.title, String.format(Locale.US, "%,.0f RWF", it.price), it.createdAt, it.photoUrl)
                }
            } catch (_: Exception) { emptyList() }
        }
        searchScope.launch {
            communityResults = try {
                rw.itunda.core.network.NetworkClient.apiService.searchCommunityPosts(query).posts.map {
                    HomeFeedEntry(it.id, "community", it.title, it.body.take(80), it.createdAt)
                }
            } catch (_: Exception) { emptyList() }
        }
        searchScope.launch {
            jobResults = try {
                rw.itunda.core.network.NetworkClient.apiService.searchJobPosts(query).posts.map {
                    HomeFeedEntry(it.id, "jobs", it.title, String.format(Locale.US, "${it.payType} · %,.0f RWF", it.payAmount), it.createdAt)
                }
            } catch (_: Exception) { emptyList() }
        }
        searchScope.launch {
            propertyResults = try {
                rw.itunda.core.network.NetworkClient.apiService.searchPropertyListings(query).listings.map {
                    HomeFeedEntry(it.id, "property", it.title, String.format(Locale.US, "%,.0f RWF", it.price), it.createdAt)
                }
            } catch (_: Exception) { emptyList() }
        }
    }

    // Real Toss/Kakao pull-to-refresh (2026-08-11 research pass) -- Home had no way
    // to manually refresh at all beyond leaving and re-entering the tab, despite
    // being the one screen with the most live, changing data (balance, transactions,
    // Discover). isRefreshing already spans the exact duration of the real fetch, so
    // the indicator only hides once fresh data has actually landed rather than on a
    // fixed timer.
    val pullToRefreshState = rememberPullToRefreshState()
    LaunchedEffect(pullToRefreshState.isRefreshing) {
        if (pullToRefreshState.isRefreshing) onRetry()
    }
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) pullToRefreshState.endRefresh()
    }

    Box(Modifier.fillMaxSize().nestedScroll(pullToRefreshState.nestedScrollConnection)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Ids.layout.screenHorizontal,
                top = 12.dp,
                end = Ids.layout.screenHorizontal,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                HomeTopBar(
                    onOpenPay = onOpenPay,
                    onOpenNotifications = onOpenNotifications,
                    onOpenOverview = onOpenOverview,
                    onOpenAccountDetail = onOpenAccountDetail,
                    unreadCount = unreadNotificationCount,
                )
            }

            // v395 first-pass Android parity: the prototype leads with the Bank
            // balance, then the compact Pay row and two clear primary actions.
            // The balance is always sourced from the real account object; no
            // prototype/demo balance is introduced here.
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenBank)
                            .padding(vertical = 2.dp),
                    ) {
                        Text("itunda Bank", color = Ids.colors.textSecondary, fontSize = 14.sp)
                        if (primaryAccount != null) {
                            Text(
                                text = String.format(Locale.US, "%,.0f", primaryAccount.balance),
                                color = Ids.colors.textPrimary,
                                fontSize = 40.sp,
                                lineHeight = 46.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-1.2).sp,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                            Text(
                                primaryAccount.currency,
                                color = Ids.colors.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        } else {
                            Text(
                                "Balance unavailable",
                                color = Ids.colors.textSecondary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenPay)
                            .padding(top = 18.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("itunda Pay", color = Ids.colors.textSecondary, fontSize = 15.sp)
                        Text("Open Pay  ›", color = Ids.colors.brand, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HomePrototypePrimaryAction(
                        label = "Send",
                        modifier = Modifier.weight(1f),
                        onClick = onOpenPay,
                    )
                    HomePrototypeSecondaryAction(
                        label = "Add money",
                        modifier = Modifier.weight(1f),
                        onClick = onOpenPay,
                    )
                }
            }

            item {
                HomePrototypeSectionTitle("Quick actions", modifier = Modifier.padding(top = 26.dp, bottom = 12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        Triple("↗", "Send", onOpenPay),
                        Triple("▦", "Scan to pay", onOpenPay),
                        Triple("▤", "Pay bills", onOpenPay),
                        Triple("⌁", "Airtime", onOpenPay),
                    ).forEach { (symbol, label, action) ->
                        HomePrototypeActionTile(
                            symbol = symbol,
                            label = label,
                            modifier = Modifier.weight(1f),
                            onClick = action,
                        )
                    }
                }
            }

            item {
                HomePrototypeSectionTitle("Rwanda services", modifier = Modifier.padding(top = 26.dp, bottom = 12.dp))
            }
            listOf(
                Triple("⌂", "Irembo", onOpenPay),
                Triple("▤", "RRA taxes", onOpenPay),
                Triple("ϟ", "Electricity", onOpenPay),
                Triple("◌", "Water", onOpenPay),
                Triple("▱", "School fees", onOpenPay),
            ).chunked(3).forEach { serviceRow ->
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        serviceRow.forEach { (symbol, label, action) ->
                            HomePrototypeActionTile(
                                symbol = symbol,
                                label = label,
                                modifier = Modifier.weight(1f),
                                onClick = action,
                            )
                        }
                        repeat(3 - serviceRow.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }

            item {
                HomePrototypeSectionTitle("Spending", modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
                HomePrototypeNavigationRow(
                    symbol = "◔",
                    title = "Spending overview",
                    subtitle = "Review your recent activity",
                    onClick = onOpenOverview,
                )
                HomePrototypeNavigationRow(
                    symbol = "▤",
                    title = "Transactions",
                    subtitle = "View your account history",
                    onClick = onOpenAccountDetail,
                )
            }

            item {
                HomePrototypeSectionTitle("For you", modifier = Modifier.padding(top = 22.dp, bottom = 8.dp))
                HomePrototypeNavigationRow(
                    symbol = "♧",
                    title = "Savings and goals",
                    subtitle = "Explore ways to grow your money",
                    onClick = onOpenBank,
                )
                HomePrototypeNavigationRow(
                    symbol = "▥",
                    title = "Credit score",
                    subtitle = "Check your credit options",
                    onClick = onOpenLoans,
                )
            }

            // Keep real, already-integrated content below the prototype's core
            // hierarchy. These cards use live API data and existing destinations.
            if (primaryAccount != null || stocks.isNotEmpty()) {
                item {
                    HomePrototypeSectionTitle("Your account and markets", modifier = Modifier.padding(top = 22.dp, bottom = 10.dp))
                    HomeMarketWidgetRow(primaryAccount, stocks, onOpenBank = onOpenBank, onOpenInvest = onOpenInvest)
                }
            }
            if (trendingListings.isNotEmpty()) {
                item {
                    HomePrototypeSectionTitle("Trending nearby", modifier = Modifier.padding(top = 24.dp, bottom = 10.dp))
                    HomeTrendingGrid(trendingListings, onOpenMarketplace = onOpenMarketplace)
                }
            }
            if (feedEntries.isNotEmpty()) {
                item {
                    HomePrototypeSectionTitle("Around your neighborhood", modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
                }
                items(feedEntries, key = { it.id }) { entry ->
                    HomeFeedRow(
                        entry = entry,
                        onClick = when (entry.kind) {
                            "marketplace" -> onOpenMarketplace
                            "community" -> onOpenCommunity
                            "jobs" -> onOpenJobs
                            "property" -> onOpenProperty
                            else -> ({})
                        },
                    )
                }
            }
            if (isOffline) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = Ids.colors.textSecondary, modifier = Modifier.size(18.dp))
                        Text("Offline — some information may be out of date", color = Ids.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        PullToRefreshContainer(state = pullToRefreshState, modifier = Modifier.align(Alignment.TopCenter))
    }
}


/**
 * Public composition entry point for the Home feature.
 *
 * The app shell consumes this entry point instead of importing the concrete
 * HomeTab implementation. Feature internals stay private to :features:home:impl.
 */
@Composable
fun HomeEntryPoint(
    discoverItems: List<rw.itunda.core.network.DiscoverItem>,
    unreadNotificationCount: Int,
    isOffline: Boolean,
    primaryAccount: rw.itunda.core.network.Account?,
    neighborhoodSet: Boolean,
    isRefreshing: Boolean,
    onRetry: () -> Unit,
    onOpenPay: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenOverview: () -> Unit = {},
    onOpenBank: () -> Unit = {},
    onOpenIdentity: () -> Unit = {},
    onOpenLoans: () -> Unit = {},
    onOpenAccountDetail: () -> Unit = {},
    onOpenMarketplace: () -> Unit = {},
    onOpenCommunity: () -> Unit = {},
    onOpenJobs: () -> Unit = {},
    onOpenProperty: () -> Unit = {},
    onOpenInvest: () -> Unit = {},
    onOpenShop: () -> Unit = {},
) {
    HomeTab(
        discoverItems = discoverItems,
        unreadNotificationCount = unreadNotificationCount,
        isOffline = isOffline,
        primaryAccount = primaryAccount,
        neighborhoodSet = neighborhoodSet,
        isRefreshing = isRefreshing,
        onRetry = onRetry,
        onOpenPay = onOpenPay,
        onOpenNotifications = onOpenNotifications,
        onOpenOverview = onOpenOverview,
        onOpenBank = onOpenBank,
        onOpenIdentity = onOpenIdentity,
        onOpenLoans = onOpenLoans,
        onOpenAccountDetail = onOpenAccountDetail,
        onOpenMarketplace = onOpenMarketplace,
        onOpenCommunity = onOpenCommunity,
        onOpenJobs = onOpenJobs,
        onOpenProperty = onOpenProperty,
        onOpenInvest = onOpenInvest,
        onOpenShop = onOpenShop,
    )
}


@Composable
private fun HomePrototypeSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        color = Ids.colors.textPrimary,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.25).sp,
        modifier = modifier,
    )
}

@Composable
private fun HomePrototypePrimaryAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Ids.colors.brand)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HomePrototypeSecondaryAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Ids.colors.chip)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HomePrototypeActionTile(
    symbol: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(Ids.colors.chip),
            contentAlignment = Alignment.Center,
        ) {
            Text(symbol, color = Ids.colors.textPrimary, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            text = label,
            color = Ids.colors.textSecondary,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 7.dp),
            maxLines = 2,
        )
    }
}

@Composable
private fun HomePrototypeNavigationRow(
    symbol: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Ids.colors.chip),
            contentAlignment = Alignment.Center,
        ) {
            Text(symbol, color = Ids.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Ids.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Text("›", color = Ids.colors.textTertiary, fontSize = 24.sp)
    }
}
