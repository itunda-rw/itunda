package rw.itunda.feature.home.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.components.EmptyState
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
            contentPadding = PaddingValues(start = Ids.layout.screenHorizontal, top = 14.dp, end = Ids.layout.screenHorizontal, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(Ids.layout.cardGap)
        ) {
            item { HomeTopBar(onOpenPay = onOpenPay, onOpenNotifications = onOpenNotifications, onOpenOverview = onOpenOverview, onOpenAccountDetail = onOpenAccountDetail, unreadCount = unreadNotificationCount) }
        item {
            HomeSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it; if (it.isBlank()) searchResults = null else runSearch(it) },
                onClear = { searchQuery = ""; searchResults = null },
            )
        }
        if (searchQuery.isNotBlank()) {
            val stillLoading = searchResults == null || marketplaceResults == null ||
                communityResults == null || jobResults == null || propertyResults == null
            val totalResults = (searchResults?.size ?: 0) + (marketplaceResults?.size ?: 0) +
                (communityResults?.size ?: 0) + (jobResults?.size ?: 0) + (propertyResults?.size ?: 0)
            if (stillLoading && totalResults == 0) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Ids.colors.brand)
                    }
                }
            } else if (!stillLoading && totalResults == 0) {
                item { EmptyState(stringResource(R.string.home_search_empty)) }
            } else {
                searchResults?.takeIf { it.isNotEmpty() }?.let { results ->
                    item { HomeSearchSectionHeader(stringResource(R.string.home_search_section_products)) }
                    items(results, key = { "product_${it.id}" }) { result -> HomeSearchResultRow(result, onClick = onOpenShop) }
                }
                marketplaceResults?.takeIf { it.isNotEmpty() }?.let { results ->
                    item { HomeSearchSectionHeader(stringResource(R.string.home_search_section_marketplace)) }
                    items(results, key = { "marketplace_${it.id}" }) { entry -> HomeFeedRow(entry, onClick = onOpenMarketplace) }
                }
                communityResults?.takeIf { it.isNotEmpty() }?.let { results ->
                    item { HomeSearchSectionHeader(stringResource(R.string.home_search_section_community)) }
                    items(results, key = { "community_${it.id}" }) { entry -> HomeFeedRow(entry, onClick = onOpenCommunity) }
                }
                jobResults?.takeIf { it.isNotEmpty() }?.let { results ->
                    item { HomeSearchSectionHeader(stringResource(R.string.home_search_section_jobs)) }
                    items(results, key = { "jobs_${it.id}" }) { entry -> HomeFeedRow(entry, onClick = onOpenJobs) }
                }
                propertyResults?.takeIf { it.isNotEmpty() }?.let { results ->
                    item { HomeSearchSectionHeader(stringResource(R.string.home_search_section_property)) }
                    items(results, key = { "property_${it.id}" }) { entry -> HomeFeedRow(entry, onClick = onOpenProperty) }
                }
            }
        } else {
        // Real fix (2026-08-13, direct live-device catch): a genuinely offline user
        // saw normal-looking placeholder data (a "RWF 0" account, a fixed 3-item
        // Discover feed) with zero indication any of it wasn't real.
        if (isOffline) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Ids.colors.dangerTint)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(18.dp), tint = Ids.colors.danger)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(stringResource(R.string.home_offline_banner), color = Ids.colors.danger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        // Real personalized recommendation card (2026-08-11) -- direct comparison
        // against real Toss Bank reference screenshots (user-provided): Toss leads
        // Home with a large, illustrated, name-addressed card, not a small generic
        // list entry. Promotes the single highest-priority real item to hero
        // treatment; DiscoverSection below excludes it from its own list so nothing
        // renders twice. Sorted by `priority`, not `isNew` -- see backend
        // DiscoverService's own doc comment (Toss Intelligence-banner research): the
        // backend does real per-user eligibility + ranking server-side.
        val heroDiscoverItem = discoverItems.sortedByDescending { it.priority }.firstOrNull()
        if (heroDiscoverItem != null) {
            item {
                // Real per-category destination (2026-08-13) -- DiscoverService's
                // real backend only ever emits 3 real category values
                // (account/savings/credit, confirmed by grep) -- each maps to a real,
                // already-built itunda screen, not a guess.
                val onOpenAction: () -> Unit = when (heroDiscoverItem.category) {
                    "account" -> onOpenIdentity
                    "savings" -> onOpenBank
                    "credit" -> onOpenLoans
                    else -> {
                        {}
                    }
                }
                PersonalRecommendationCard(heroDiscoverItem, onOpenAction = onOpenAction)
            }
        }
        // Real architectural fix (2026-08-13, direct user directive): "all itunda
        // product features are independent and isolated -- itunda bank is a complete
        // product... tabs are not products, are just access points." Home no longer
        // carries any Bank- or Pay-specific data or destinations at all -- see
        // BankHubScreen/PayTab's own doc comments for where that content moved.
        val remainingDiscoverItems = discoverItems.filter { it.id != heroDiscoverItem?.id }
        if (remainingDiscoverItems.isNotEmpty()) {
            item { DiscoverSection(remainingDiscoverItems) }
        }
        // Real market widget row (2026-08-14) -- account balance (real, already
        // fetched) plus real RSE stock ticker chips, matching Naver's own weather/
        // stock-index widget row structurally without inventing weather data itunda
        // has no source for.
        if (primaryAccount != null || stocks.isNotEmpty()) {
            item { HomeMarketWidgetRow(primaryAccount, stocks, onOpenBank = onOpenBank, onOpenInvest = onOpenInvest) }
        }
        // Real Toss/Kakao/Naver hub-organization fix (2026-08-29) -- an account with
        // no neighborhood set silently left the entire bottom two-thirds of Home
        // blank. Real, established EmptyState pattern closes it here too, with a
        // real action into Hood/Community where neighborhood is actually set.
        if (trendingListings.isEmpty() && feedEntries.isEmpty() && !neighborhoodSet) {
            item {
                EmptyState(
                    "Set your neighborhood to see local marketplace, community, jobs, and property listings here.",
                    icon = Icons.Outlined.LocationOn,
                    actionLabel = "Set neighborhood",
                    onAction = onOpenCommunity,
                )
            }
        }
        if (trendingListings.isNotEmpty()) {
            item { HomeTrendingGrid(trendingListings, onOpenMarketplace = onOpenMarketplace) }
        }
        if (feedEntries.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.home_feed_title),
                    color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(feedEntries, key = { it.id }) { entry ->
                HomeFeedRow(
                    entry,
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
