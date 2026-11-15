package rw.itunda.feature.menu.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.components.FlatRow
import rw.itunda.core.designsystem.components.FlatSection
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons

// Real Explore primary bottom tab (renamed 2026-08-10 from All). Content that
// doesn't belong in an exhaustive product catalog is elsewhere; this is the
// exhaustive product catalog.
//
// Moved into :features:menu:impl (2026-09-02, Menu Feature-module decomposition,
// completing the Home/Pay/Menu/My scope) -- confirmed zero MainViewModel coupling
// before moving. Real blocker found and fixed: 5 distinct rw.itunda.app.miniapps.*
// Activity/loader references (RewardTasksMiniAppActivity, PayBillsMiniAppActivity,
// InsuranceMiniAppActivity, AccountBalanceMiniAppActivity, PartnerMiniAppLoader),
// all :app-manifest-only classes a Feature module can't reference -- each replaced
// with an injected callback, same pattern as PayTab's onOpenRewardsMiniApp. See
// [[project_itunda_feature_isolation]] for the full account.
@Composable
fun MenuEntryPoint(
    onOpenShop: () -> Unit = {},
    onOpenEats: () -> Unit = {},
    onOpenMarketplace: () -> Unit = {},
    onOpenCommunity: () -> Unit = {},
    onOpenJobs: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenInvest: () -> Unit = {},
    onOpenBank: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    onOpenOverview: () -> Unit = {},
    onOpenLoans: () -> Unit = {},
    onOpenSupport: () -> Unit = {},
    onOpenUssdSettings: () -> Unit = {},
    onOpenCreditScore: () -> Unit = {},
    onOpenCertificate: () -> Unit = {},
    onOpenIdentity: () -> Unit = {},
    onOpenWeeklySavings: () -> Unit = {},
    onOpenGrow31Savings: () -> Unit = {},
    onOpenUpfrontDeposit: () -> Unit = {},
    onOpenYouthAccount: () -> Unit = {},
    onOpenCard: () -> Unit = {},
    onOpenTransit: () -> Unit = {},
    onOpenGroupAccounts: () -> Unit = {},
    onOpenIkimina: () -> Unit = {},
    onOpenSacco: () -> Unit = {},
    onOpenHarvestAdvance: () -> Unit = {},
    onOpenSpending: () -> Unit = {},
    onOpenRides: () -> Unit = {},
    onOpenDesignatedDriver: () -> Unit = {},
    onOpenBikeRental: () -> Unit = {},
    onOpenParking: () -> Unit = {},
    onOpenMotoFareCollect: () -> Unit = {},
    onOpenBus: () -> Unit = {},
    onOpenKnowledge: () -> Unit = {},
    onOpenVehicleInspection: () -> Unit = {},
    onOpenVehicleValuation: () -> Unit = {},
    onOpenFamilyLink: () -> Unit = {},
    onOpenSubscriptions: () -> Unit = {},
    onOpenForeignCurrency: () -> Unit = {},
    onOpenRequestMoney: () -> Unit = {},
    onOpenAutoTopUp: () -> Unit = {},
    onOpenTrustScore: () -> Unit = {},
    onOpenAgentOperator: () -> Unit = {},
    onOpenFloatMarketplace: () -> Unit = {},
    onOpenVupLoan: () -> Unit = {},
    onOpenStudentLoan: () -> Unit = {},
    onOpenMotoOwnership: () -> Unit = {},
    onOpenTransferHub: () -> Unit = {},
    onClaimInterest: () -> Unit = {},
    onSwitchToTalk: () -> Unit = {},
    onOpenProperty: () -> Unit = {},
    partnerMiniApps: List<rw.itunda.core.network.PartnerMiniAppDto>,
    // Real injected callbacks (2026-09-02) -- see this file's own header comment.
    onOpenRewardTasksMiniApp: () -> Unit = {},
    onOpenPayBillsMiniApp: () -> Unit = {},
    onOpenInsuranceMiniApp: () -> Unit = {},
    onOpenAccountBalanceMiniApp: () -> Unit = {},
    onLaunchPartnerMiniApp: suspend (activity: android.app.Activity, app: rw.itunda.core.network.PartnerMiniAppDto, onError: (String) -> Unit) -> Unit = { _, _, _ -> },
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var partnerLoadError by remember { mutableStateOf<String?>(null) }
    var loadingPartnerAppId by remember { mutableStateOf<String?>(null) }
    var showMiniAppsHub by remember { mutableStateOf(false) }
    // Real Toss/Kakao mini-app-store reference (2026-09-11) -- promoted from an
    // inline "Load more" list into its own dedicated hub screen (real category
    // filter + search), matching CouponBoxScreen/MembershipScreen's own
    // `if (showX) { Screen(...); return }` pattern in PayTab.kt.
    if (showMiniAppsHub) {
        MiniAppsHubScreen(onBack = { showMiniAppsHub = false }, onLaunchPartnerMiniApp = onLaunchPartnerMiniApp)
        return
    }
    var menuSearchQuery by remember { mutableStateOf("") }
    var availableTaskCount by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        try {
            val result = rw.itunda.core.network.NetworkClient.apiService.getRewardTasks()
            availableTaskCount = result.tasks.count { it.eligible && !it.claimed }
        } catch (_: Exception) { /* keep the generic fallback subtitle */ }
    }

    val sections = menuSections(
        availableTaskCount = availableTaskCount,
        onOpenShop = onOpenShop, onOpenEats = onOpenEats, onOpenMap = onOpenMap,
        onOpenMarketplace = onOpenMarketplace, onOpenCommunity = onOpenCommunity, onOpenJobs = onOpenJobs, onOpenProperty = onOpenProperty,
        onOpenRewardTasksMiniApp = onOpenRewardTasksMiniApp, onOpenInvest = onOpenInvest,
        onOpenWeeklySavings = onOpenWeeklySavings, onOpenGrow31Savings = onOpenGrow31Savings,
        onOpenOverview = onOpenOverview, onOpenCard = onOpenCard, onOpenTransit = onOpenTransit, onOpenSpending = onOpenSpending,
        onOpenGroupAccounts = onOpenGroupAccounts, onOpenFamilyLink = onOpenFamilyLink, onOpenForeignCurrency = onOpenForeignCurrency,
        onOpenSubscriptions = onOpenSubscriptions, onOpenCertificate = onOpenCertificate,
        onOpenTransferHub = onOpenTransferHub, onOpenRequestMoney = onOpenRequestMoney, onOpenAutoTopUp = onOpenAutoTopUp,
        onOpenPayBillsMiniApp = onOpenPayBillsMiniApp,
        onOpenUpfrontDeposit = onOpenUpfrontDeposit, onOpenYouthAccount = onOpenYouthAccount, onOpenIkimina = onOpenIkimina, onOpenSacco = onOpenSacco,
        onOpenLoans = onOpenLoans, onOpenCreditScore = onOpenCreditScore, onOpenHarvestAdvance = onOpenHarvestAdvance,
        onOpenVupLoan = onOpenVupLoan, onOpenStudentLoan = onOpenStudentLoan, onOpenMotoOwnership = onOpenMotoOwnership,
        onOpenRides = onOpenRides, onOpenDesignatedDriver = onOpenDesignatedDriver, onOpenBikeRental = onOpenBikeRental,
        onOpenParking = onOpenParking, onOpenBus = onOpenBus, onOpenMotoFareCollect = onOpenMotoFareCollect,
        onOpenVehicleInspection = onOpenVehicleInspection, onOpenVehicleValuation = onOpenVehicleValuation,
        onOpenTrustScore = onOpenTrustScore, onOpenKnowledge = onOpenKnowledge,
        onOpenAgentOperator = onOpenAgentOperator, onOpenFloatMarketplace = onOpenFloatMarketplace,
        onClaimInterest = onClaimInterest, onSwitchToTalk = onSwitchToTalk, onOpenSupport = onOpenSupport,
        onOpenUssdSettings = onOpenUssdSettings,
    )

    ProtoExploreContent(
        query = menuSearchQuery, onQuery = { menuSearchQuery = it }, onHelp = onOpenSupport, onSettings = onOpenSettings,
        onOpen = { key ->
            when (key) {
                "shop" -> onOpenShop(); "eats", "front" -> onOpenEats(); "marketplace" -> onOpenMarketplace()
                "community" -> onOpenCommunity(); "jobs" -> onOpenJobs(); "property" -> onOpenProperty(); "places", "directions" -> onOpenMap()
                "benefits" -> onOpenRewardTasksMiniApp(); "invest" -> onOpenInvest(); "week26" -> onOpenWeeklySavings(); "day31" -> onOpenGrow31Savings()
                "open" -> onOpenBank(); "assets" -> onOpenOverview(); "card" -> onOpenCard(); "transit" -> onOpenTransit(); "spending" -> onOpenSpending()
                "group" -> onOpenGroupAccounts(); "family" -> onOpenFamilyLink(); "fx" -> onOpenForeignCurrency(); "subs" -> onOpenSubscriptions()
                "cert" -> onOpenCertificate(); "transfer" -> onOpenTransferHub(); "request" -> onOpenRequestMoney(); "topup" -> onOpenAutoTopUp()
                "mobile" -> onOpenPayBillsMiniApp(); "deposit" -> onOpenUpfrontDeposit(); "youth" -> onOpenYouthAccount(); "ikimina" -> onOpenIkimina()
                "sacco" -> onOpenSacco(); "loan" -> onOpenLoans(); "credit" -> onOpenCreditScore(); "harvest" -> onOpenHarvestAdvance()
                "vup" -> onOpenVupLoan(); "student" -> onOpenStudentLoan(); "moto" -> onOpenMotoOwnership(); "rides" -> onOpenRides()
                "bike" -> onOpenBikeRental(); "bus" -> onOpenBus(); "chat", "report", "news" -> onOpenSupport(); "ussd" -> onOpenUssdSettings()
                "apps" -> showMiniAppsHub = true; "verify" -> onOpenIdentity(); "bank" -> onOpenBank()
            }
        },
    )
}

// Real icon-render fix (2026-09-11, Mini-Apps hub pass) -- FlatRow.icon is a Material
// ImageVector, not a remote-image slot, so a partner mini-app's own real iconUrl was
// never rendered anywhere on Android (bank-mfe/iOS already show it). Shared between
// this screen's own capped teaser and MiniAppsHubScreen.kt's full list.
@Composable
internal fun PartnerMiniAppGlyph(iconUrl: String?) {
    if (iconUrl != null) {
        AsyncImage(
            model = iconUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Ids.colors.chip),
        )
    } else {
        Box(modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Ids.colors.chip))
    }
}
