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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    recentTransactions: List<rw.itunda.core.network.TransactionDto>,
    neighborhoodSet: Boolean,
    isRefreshing: Boolean,
    onRetry: () -> Unit,
    onOpenPay: () -> Unit = {},
    onOpenSend: () -> Unit = onOpenPay,
    onOpenNotifications: () -> Unit = {},
    onOpenOverview: () -> Unit = {},
    onOpenBank: () -> Unit = {},
    onOpenIdentity: () -> Unit = {},
    onOpenLoans: () -> Unit = {},
    onOpenAccountDetail: () -> Unit = {},
    onOpenActivity: () -> Unit = {},
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(8.dp).clip(CircleShape)
                            .background(if (isOffline) Ids.colors.danger else Ids.colors.brand),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(if (isOffline) R.string.home_connection_offline else R.string.home_connection_online),
                        color = Ids.colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            // Prototype parity: the account is the first content card after the top bar,
            // not a secondary tile buried below recommendations and search.
            if (primaryAccount != null) {
                item {
                    HomeAccountHeroCard(
                        account = primaryAccount,
                        payAccount = payAccount,
                        onOpenBank = onOpenBank,
                        onOpenPay = onOpenPay,
                    )
                }
            }
            item {
                HomePrimaryActions(onSend = onOpenSend, onAddMoney = onOpenPay)
            }
            item {
                HomeQuickActions(
                    onSend = onOpenSend,
                    onScanToPay = onOpenPay,
                    onPayBills = onOpenPay,
                    onAirtime = onOpenPay,
                )
            }
            item { HomeRwandaServices(onOpenService = onOpenPay) }
            item {
                Text(
                    stringResource(R.string.home_spending),
                    color = Ids.colors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 2.dp),
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().pressScaleClickable(onClick = onOpenActivity)
                        .padding(vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(18.dp))
                            .background(Ids.colors.chip),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            androidx.compose.material.icons.Icons.Outlined.ReceiptLong,
                            contentDescription = null,
                            tint = Ids.colors.textPrimary,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (recentTransactions.isEmpty()) stringResource(R.string.home_spending_empty)
                            else stringResource(R.string.home_spending_activity),
                            color = Ids.colors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(R.string.home_spending_subtitle),
                            color = Ids.colors.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    Text(
                        stringResource(R.string.home_see_all),
                        color = Ids.colors.brand,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .pressScaleClickable(onClick = onOpenActivity).padding(6.dp),
                    )
                }
            }
            item {
                HomeRecentActivity(
                    transactions = recentTransactions,
                    onSeeAll = onOpenActivity,
                    onOpenTransaction = onOpenActivity,
                )
            }
            item {
                Text(
                    stringResource(R.string.home_for_you),
                    color = Ids.colors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            }
            val heroDiscoverItem = discoverItems.sortedByDescending { it.priority }.firstOrNull()
            if (heroDiscoverItem != null) {
                item {
                    val onOpenAction: () -> Unit = when (heroDiscoverItem.category) {
                        "account" -> onOpenIdentity
                        "savings" -> onOpenBank
                        "credit" -> onOpenLoans
                        else -> onOpenOverview
                    }
                    PersonalRecommendationCard(heroDiscoverItem, onOpenAction = onOpenAction)
                }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .background(Ids.colors.surface)
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.home_for_you_empty),
                            color = Ids.colors.textSecondary,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
            val remainingDiscoverItems = discoverItems.filter { it.id != heroDiscoverItem?.id }
            if (remainingDiscoverItems.isNotEmpty()) {
                item { DiscoverSection(remainingDiscoverItems) }
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
    recentTransactions: List<rw.itunda.core.network.TransactionDto>,
    neighborhoodSet: Boolean,
    isRefreshing: Boolean,
    onRetry: () -> Unit,
    onOpenPay: () -> Unit = {},
    onOpenSend: () -> Unit = onOpenPay,
    onOpenNotifications: () -> Unit = {},
    onOpenOverview: () -> Unit = {},
    onOpenBank: () -> Unit = {},
    onOpenIdentity: () -> Unit = {},
    onOpenLoans: () -> Unit = {},
    onOpenAccountDetail: () -> Unit = {},
    onOpenActivity: () -> Unit = {},
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
        recentTransactions = recentTransactions,
        neighborhoodSet = neighborhoodSet,
        isRefreshing = isRefreshing,
        onRetry = onRetry,
        onOpenPay = onOpenPay,
        onOpenSend = onOpenSend,
        onOpenNotifications = onOpenNotifications,
        onOpenOverview = onOpenOverview,
        onOpenBank = onOpenBank,
        onOpenIdentity = onOpenIdentity,
        onOpenLoans = onOpenLoans,
        onOpenAccountDetail = onOpenAccountDetail,
        onOpenActivity = onOpenActivity,
        onOpenMarketplace = onOpenMarketplace,
        onOpenCommunity = onOpenCommunity,
        onOpenJobs = onOpenJobs,
        onOpenProperty = onOpenProperty,
        onOpenInvest = onOpenInvest,
        onOpenShop = onOpenShop,
    )
}
