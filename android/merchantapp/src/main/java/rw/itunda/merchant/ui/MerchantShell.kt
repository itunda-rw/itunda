package rw.itunda.merchant.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Divider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.IdsBottomTabItem
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonVariant
import rw.itunda.core.designsystem.components.IdsFlatBottomBar
import rw.itunda.core.designsystem.components.IdsTabs
import rw.itunda.core.designsystem.components.StatusBadge
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsTypography
import rw.itunda.merchant.network.MerchantDto
import rw.itunda.merchant.network.NetworkClient

/** Every screen the merchant app already had; the shell only decides where each one lives. */
internal enum class MerchantTab(val label: String) {
    ORDERS("Orders"), DINE_IN("Dine-in"), BOOKINGS("Bookings"), CATALOG("Catalog"), REGISTER("Register"),
    BUSINESS_ACCOUNT("Business account"), REPORTS("Reports"), REVIEWS("Reviews"), COUPONS("Coupons"),
    VISITORS("Visitors"), FOLLOWERS("Followers"), UPDATES("Updates"), PHOTOS("Photos"), PAYROLL("Payroll"),
    ADS("Ads"), BILLING("Billing"), CASH_ADVANCE("Cash advance"), VOUCHERS("Vouchers"),
}

/**
 * Four bottom tabs like the Business prototype (Today / Store / Payments / All) instead of
 * 18 scrolling top tabs (UI_UX_GUIDELINES rule 1: one screen, one job). Today keeps a short
 * top switcher because the four things a shop does all day (orders, dine-in, bookings,
 * register) are used back to back; Store and Payments are lists that open one screen at a time.
 */
private enum class MerchantHub(val label: String, val icon: ImageVector, val tabs: List<MerchantTab>) {
    Today("Today", Icons.Outlined.Receipt, listOf(MerchantTab.ORDERS, MerchantTab.DINE_IN, MerchantTab.BOOKINGS, MerchantTab.REGISTER)),
    Store(
        "Store", Icons.Outlined.Storefront,
        listOf(
            MerchantTab.CATALOG, MerchantTab.PHOTOS, MerchantTab.UPDATES, MerchantTab.COUPONS,
            MerchantTab.ADS, MerchantTab.REVIEWS, MerchantTab.FOLLOWERS, MerchantTab.VISITORS,
        ),
    ),
    Payments(
        "Payments", Icons.Outlined.AccountBalanceWallet,
        listOf(
            MerchantTab.BUSINESS_ACCOUNT, MerchantTab.BILLING, MerchantTab.PAYROLL,
            MerchantTab.CASH_ADVANCE, MerchantTab.VOUCHERS, MerchantTab.REPORTS,
        ),
    ),
    All("All", Icons.Outlined.Menu, emptyList()),
}

@Composable
internal fun MerchantShell(merchant: MerchantDto, onLogout: () -> Unit) {
    var hubIndex by rememberSaveable { mutableStateOf(MerchantHub.Today.ordinal) }
    var todayIndex by rememberSaveable { mutableStateOf(0) }
    var detailName by rememberSaveable { mutableStateOf<String?>(null) }
    val hub = MerchantHub.entries[hubIndex]
    val detail = detailName?.let { MerchantTab.valueOf(it) }
    BackHandler(enabled = detail != null) { detailName = null }

    Scaffold(
        containerColor = Ids.colors.background,
        bottomBar = {
            IdsFlatBottomBar(
                items = MerchantHub.entries.map { IdsBottomTabItem(it.label, it.icon) },
                selectedIndex = hubIndex,
                onSelect = { hubIndex = it; detailName = null },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                detail != null -> Column(modifier = Modifier.fillMaxSize()) {
                    BackTopBar(title = detail.label, onBack = { detailName = null })
                    MerchantTabContent(detail, merchant)
                }
                hub == MerchantHub.Today -> Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        merchant.businessName,
                        style = IdsTypography.Title1,
                        color = Ids.colors.textPrimary,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
                    )
                    IdsTabs(
                        labels = hub.tabs.map { it.label },
                        selectedIndex = todayIndex,
                        onSelectedIndexChange = { todayIndex = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MerchantTabContent(hub.tabs[todayIndex], merchant)
                }
                hub == MerchantHub.All -> MerchantAllTab(merchant, onLogout)
                else -> MerchantHubList(hub.label, hub.tabs, onOpen = { detailName = it.name })
            }
        }
    }
}

@Composable
private fun MerchantHubList(title: String, tabs: List<MerchantTab>, onOpen: (MerchantTab) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                title,
                style = IdsTypography.Title1,
                color = Ids.colors.textPrimary,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
            )
        }
        items(tabs, key = { it.name }) { tab ->
            Column(modifier = Modifier.fillMaxWidth().pressScaleClickable { onOpen(tab) }) {
                Text(
                    tab.label,
                    style = IdsTypography.Subtitle1,
                    color = Ids.colors.textPrimary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                )
                Divider(color = Ids.colors.divider, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 24.dp))
            }
        }
    }
}

@Composable
private fun MerchantAllTab(merchant: MerchantDto, onLogout: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp)) {
        Text("All", style = IdsTypography.Title1, color = Ids.colors.textPrimary)
        Spacer(modifier = Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(merchant.businessName, style = IdsTypography.Subtitle1, color = Ids.colors.textPrimary)
            // Real Coupang badge system (2026-08-05) -- a named verification tier next to the
            // business name, same as the old header showed.
            if (merchant.kybVerified) StatusBadge("✓ Verified", tint = Ids.colors.brand)
        }
        Spacer(modifier = Modifier.weight(1f))
        IdsButton(
            text = "Log out",
            variant = IdsButtonVariant.Tinted,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                NetworkClient.currentTokenStore().clearSession()
                onLogout()
            },
        )
    }
}

@Composable
private fun MerchantTabContent(tab: MerchantTab, merchant: MerchantDto) {
    when (tab) {
        MerchantTab.ORDERS -> OrdersTab()
        MerchantTab.DINE_IN -> DineInTab(restaurantId = merchant.id)
        MerchantTab.BOOKINGS -> BookingTab()
        MerchantTab.CATALOG -> CatalogTab()
        MerchantTab.REGISTER -> PosTab()
        MerchantTab.BUSINESS_ACCOUNT -> BusinessAccountTab()
        MerchantTab.REPORTS -> ReportsTab()
        MerchantTab.REVIEWS -> ReviewsTab(restaurantId = merchant.id)
        MerchantTab.COUPONS -> CouponsTab()
        MerchantTab.VISITORS -> VisitorAnalyticsTab()
        MerchantTab.FOLLOWERS -> FollowersTab()
        MerchantTab.UPDATES -> UpdatesTab(merchantId = merchant.id)
        MerchantTab.PHOTOS -> PhotosTab()
        MerchantTab.PAYROLL -> PayrollTab()
        MerchantTab.ADS -> AdsTab()
        MerchantTab.BILLING -> BillingTab()
        MerchantTab.CASH_ADVANCE -> VendorCashAdvanceTab(merchantId = merchant.id)
        MerchantTab.VOUCHERS -> VoucherRedeemTab()
    }
}
