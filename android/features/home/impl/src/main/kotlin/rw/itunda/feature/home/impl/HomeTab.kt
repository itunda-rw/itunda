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
    onOpenSend: () -> Unit = {},
    onOpenBills: () -> Unit = {},
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

    // 2026-11-09: Home is rebuilt from the HTML prototype (v1038), see ProtoHome.kt.
    ProtoHomeTab(
        refreshKey = isRefreshing,
        unreadNotificationCount = unreadNotificationCount,
        onOpenBank = onOpenBank,
        onOpenPay = onOpenPay,
        onOpenSend = onOpenSend,
        onOpenBills = onOpenBills,
        onOpenNotifications = onOpenNotifications,
        onOpenSavings = onOpenBank,
        onOpenCard = onOpenPay,
        onOpenSeeAll = onOpenOverview,
        onOpenRewards = onOpenOverview,
    )
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
    onOpenSend: () -> Unit = {},
    onOpenBills: () -> Unit = {},
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
        onOpenSend = onOpenSend,
        onOpenBills = onOpenBills,
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
