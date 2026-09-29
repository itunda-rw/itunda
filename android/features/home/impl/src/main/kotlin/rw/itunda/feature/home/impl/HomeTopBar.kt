package rw.itunda.feature.home.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import rw.itunda.core.designsystem.components.IdsIconButton
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons

// Moved here from :app's ItundaAppScreen.kt (2026-09-02, Home Feature-module
// decomposition) -- confirmed used only by HomeTab, which moved to this same module
// in the same slice.
@Composable
internal fun HomeTopBar(
    onOpenPay: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenOverview: () -> Unit = {},
    onOpenAccountDetail: () -> Unit = {},
    unreadCount: Int = 0,
) {
    var showAccountSwitcher by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.pressScaleClickable { showAccountSwitcher = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("itunda", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Icon(
                Icons.Outlined.ExpandMore, contentDescription = stringResource(R.string.home_account_switcher),
                modifier = Modifier.size(20.dp), tint = Ids.colors.textPrimary,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .border(1.dp, Ids.colors.divider, RoundedCornerShape(999.dp))
                .pressScaleClickable(onClick = onOpenPay)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.home_pay_shortcut), color = Ids.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.width(4.dp))
        Box {
            IdsIconButton(IdsIcons.Bell, contentDescription = stringResource(R.string.home_notifications), onClick = onOpenNotifications)
            if (unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 6.dp)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Ids.colors.danger),
                )
            }
        }
    }
    if (showAccountSwitcher) {
        AccountSwitcherSheet(
            onDismiss = { showAccountSwitcher = false },
            onOpenOverview = { showAccountSwitcher = false; onOpenOverview() },
            onOpenAccount = { showAccountSwitcher = false; onOpenAccountDetail() },
        )
    }
}

@Composable
private fun AccountSwitcherSheet(onDismiss: () -> Unit, onOpenOverview: () -> Unit, onOpenAccount: () -> Unit = {}) {
    var linkedAccounts by remember { mutableStateOf<List<rw.itunda.core.network.LinkedAccountEntityDto>?>(null) }
    LaunchedEffect(Unit) {
        try {
            linkedAccounts = rw.itunda.core.network.NetworkClient.apiService.getLinkedAccounts().linkedAccounts
                .filter { it.status == "LINKED" }
        } catch (_: Exception) {
            linkedAccounts = emptyList()
        }
    }
    BackHandler(onBack = onDismiss)
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)).clickable(onClick = onDismiss)) {
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 64.dp, start = Ids.layout.screenHorizontal, end = Ids.layout.screenHorizontal)
                .fillMaxWidth()
                .clip(RoundedCornerShape(Ids.layout.cardCornerRadius))
                .background(Ids.colors.surface)
                .clickable(enabled = false) {}
                .padding(vertical = 8.dp),
        ) {
            Text(
                stringResource(R.string.home_your_accounts),
                color = Ids.colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().pressScaleClickable(onClick = onOpenAccount).padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(22.dp), tint = Ids.colors.brand)
                Spacer(modifier = Modifier.width(12.dp))
                Text("itunda Bank account", color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.CheckCircle, contentDescription = stringResource(R.string.home_current_account), modifier = Modifier.size(18.dp), tint = Ids.colors.brand)
            }
            when {
                linkedAccounts == null -> {}
                linkedAccounts!!.isEmpty() -> {}
                else -> linkedAccounts!!.forEach { account ->
                    Divider(color = Ids.colors.divider, modifier = Modifier.padding(horizontal = 18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.AccountBalance, contentDescription = null, modifier = Modifier.size(22.dp), tint = Ids.colors.textSecondary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${account.provider} · ${account.externalAccountNumberMasked}", color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                account.demoBalance?.let { "${account.demoBalanceCurrency ?: "RWF"} " + String.format(Locale.US, "%,.0f (Demo)", it) } ?: stringResource(R.string.home_demo_balance_unavailable),
                                color = Ids.colors.textSecondary, fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
            Divider(color = Ids.colors.divider, modifier = Modifier.padding(horizontal = 18.dp))
            Text(
                stringResource(R.string.home_link_another_account),
                color = Ids.colors.brand, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .pressScaleClickable(onClick = onOpenOverview)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }
    }
}
