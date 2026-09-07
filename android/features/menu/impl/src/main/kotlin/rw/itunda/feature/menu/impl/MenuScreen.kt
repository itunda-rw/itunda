package rw.itunda.feature.menu.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun MenuScreen(
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

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(start = Ids.layout.screenHorizontal, end = Ids.layout.screenHorizontal, top = Ids.layout.screenVertical),
        verticalArrangement = Arrangement.spacedBy(Ids.layout.cardGap)
    ) {
        item {
            AllTopBar(
                onOpenAuthentication = onOpenIdentity,
                onOpenHelp = onOpenSupport,
                onOpenSettings = onOpenSettings,
            )
        }
        item {
            MenuSearchBar(
                query = menuSearchQuery,
                onQueryChange = { menuSearchQuery = it },
                placeholder = "Search everything else",
            )
        }
        if (menuSearchQuery.isBlank()) {
            item {
                IconGridSection(
                    "Open",
                    listOf(
                        "Youth" to Icons.Outlined.Savings,
                        "Games" to Icons.Outlined.SportsEsports,
                        "Bank" to Icons.Outlined.AccountBalance,
                        "Pick" to IdsIcons.Star,
                    ),
                    onItemClick = { label ->
                        when (label) {
                            "Youth" -> onOpenYouthAccount()
                            "Bank" -> onOpenBank()
                        }
                    },
                )
            }
            item {
                IconGridSection(
                    "Shortcuts",
                    listOf(
                        "Open account" to Icons.Outlined.AddCircleOutline,
                        "Verify" to Icons.Outlined.VerifiedUser,
                        "Send" to IdsIcons.Send,
                        "Group" to Icons.Outlined.Group,
                        "Property" to Icons.Outlined.HomeWork,
                        "Insurance" to IdsIcons.ShieldCheck,
                    ),
                    onItemClick = { label ->
                        when (label) {
                            "Open account" -> onOpenOverview()
                            "Verify" -> onOpenIdentity()
                            "Send" -> onOpenTransferHub()
                            "Group" -> onOpenGroupAccounts()
                            "Property" -> onOpenProperty()
                            "Insurance" -> onOpenInsuranceMiniApp()
                        }
                    },
                )
            }
            sections.forEach { (title, rows) ->
                // Real fix (2026-08-12): Mini apps' own 4 rows (Account balance/Pay
                // bills/Reward tasks/Insurance) fold into "Money tools" at render
                // time only -- NOT part of the shared `sections` list `allMenuSectionsForSearch`
                // used to power search too, matching this screen's original behavior
                // exactly (these 4 shortcuts were never searchable before this move
                // either).
                if (title == "Money tools") {
                    item {
                        FlatSection(
                            title,
                            rows + listOf(
                                FlatRow("Account balance", onClick = onOpenAccountBalanceMiniApp),
                                FlatRow("Pay bills", onClick = onOpenPayBillsMiniApp),
                                FlatRow("Reward tasks", onClick = onOpenRewardTasksMiniApp),
                                FlatRow("Insurance", onClick = onOpenInsuranceMiniApp),
                            ),
                        )
                    }
                } else {
                    item { FlatSection(title, rows) }
                }
            }
            if (partnerMiniApps.isNotEmpty()) {
                item {
                    FlatSection(
                        title = "Partner mini-apps",
                        rows = partnerMiniApps.map { app ->
                            FlatRow(
                                title = app.name,
                                subtitle = if (loadingPartnerAppId == app.id) "Loading..." else app.description,
                                onClick = {
                                    if (loadingPartnerAppId == null) {
                                        loadingPartnerAppId = app.id
                                        coroutineScope.launch {
                                            onLaunchPartnerMiniApp(context as android.app.Activity, app) { message -> partnerLoadError = message }
                                            loadingPartnerAppId = null
                                        }
                                    }
                                }
                            )
                        }
                    )
                }
            }
        } else {
            val query = menuSearchQuery.trim()
            val matchingSections = sections.map { (title, rows) ->
                title to rows.filter { it.title.contains(query, ignoreCase = true) }
            }.filter { it.second.isNotEmpty() }
            if (matchingSections.isEmpty()) {
                item {
                    Text(
                        "No match for \"$query\".",
                        color = Ids.colors.textTertiary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                matchingSections.forEach { (title, rows) ->
                    item { FlatSection(title, rows) }
                }
            }
        }
    }

    val currentPartnerLoadError = partnerLoadError
    if (currentPartnerLoadError != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { partnerLoadError = null },
            title = { Text("Couldn't load mini-app") },
            text = { Text(currentPartnerLoadError) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { partnerLoadError = null }) { Text("OK") }
            }
        )
    }
}
