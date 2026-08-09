package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import rw.itunda.core.designsystem.components.IdsTextField
import androidx.compose.material3.Switch
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
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.AutoTopUpSettingDto
import rw.itunda.core.network.ConfigureAutoTopUpRequest
import rw.itunda.core.network.LinkedAccountEntityDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.math.BigDecimal

// Real Naver Pay Money 자동충전 (auto-charge) equivalent (item 168/176) -- see
// AutoTopUpService's own doc comment: a real background scheduler already pulls from a
// linked external account whenever the wallet drops below a threshold, plus a real
// topUpShortfall hook wired into P2pService.sendDirect. Fully real but had zero mobile
// UI anywhere until now -- bank-mfe got its own client the same session (item 168). Same
// no-ViewModel, NetworkClient-direct shape as RequestMoneyScreen.kt/ForeignCurrencyScreen.kt.
@Composable
fun AutoTopUpScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var walletId by remember { mutableStateOf<String?>(null) }
    var setting by remember { mutableStateOf<AutoTopUpSettingDto?>(null) }
    var settingLoaded by remember { mutableStateOf(false) }
    var linkedAccounts by remember { mutableStateOf<List<LinkedAccountEntityDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            val wallets = NetworkClient.apiService.getWallets()
            walletId = (wallets.wallets.firstOrNull { it.type == "MAIN" } ?: wallets.wallets.firstOrNull())?.id
        } catch (e: Exception) {
            error = "Could not load your wallet."
        }
        try {
            linkedAccounts = NetworkClient.apiService.getLinkedAccounts().linkedAccounts
        } catch (e: Exception) {
            linkedAccounts = emptyList()
        }
    }

    LaunchedEffect(walletId, refreshKey) {
        val id = walletId ?: return@LaunchedEffect
        settingLoaded = false
        setting = try {
            NetworkClient.apiService.getAutoTopUpSetting(id).setting
        } catch (e: HttpException) {
            null
        } catch (e: IOException) {
            error = "Couldn't reach itunda. Check your connection and try again."
            null
        }
        settingLoaded = true
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = "Auto top-up", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Automatically top up your wallet from a linked account whenever it drops below a threshold you set.",
                    color = Ids.colors.textSecondary,
                    fontSize = 13.sp,
                )
            }
            error?.let { item { Text(it, color = Ids.colors.danger, fontSize = 13.sp) } }
            val accounts = linkedAccounts
            val id = walletId
            if (id == null || accounts == null || !settingLoaded) {
                item { Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth().height(120.dp)) {} }
            } else if (accounts.none { it.status == "LINKED" }) {
                item {
                    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Link an external account first -- see My > Linked accounts.",
                            color = Ids.colors.textSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            } else {
                item {
                    AutoTopUpConfigCard(
                        walletId = id,
                        linkedAccounts = accounts.filter { it.status == "LINKED" },
                        setting = setting,
                        onChanged = { refreshKey++ },
                    )
                }
                if (setting != null) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(Ids.colors.surface)
                                .clickable {
                                    coroutineScope.launch {
                                        try {
                                            NetworkClient.apiService.triggerAutoTopUp(id)
                                            refreshKey++
                                        } catch (e: HttpException) {
                                            error = superAppErrorMessage(e)
                                        } catch (e: IOException) {
                                            error = "Couldn't reach itunda. Check your connection and try again."
                                        }
                                    }
                                }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("Check now", color = Ids.colors.brand, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoTopUpConfigCard(
    walletId: String,
    linkedAccounts: List<LinkedAccountEntityDto>,
    setting: AutoTopUpSettingDto?,
    onChanged: () -> Unit,
) {
    var selectedAccountId by remember(setting) { mutableStateOf(setting?.linkedAccountId ?: linkedAccounts.first().id) }
    var threshold by remember(setting) { mutableStateOf(setting?.thresholdAmount?.toPlainString() ?: "") }
    var topUpAmount by remember(setting) { mutableStateOf(setting?.topUpAmount?.toPlainString() ?: "") }
    var enabled by remember(setting) { mutableStateOf(setting?.enabled ?: true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (setting == null) "Set up auto top-up" else "Auto top-up settings", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold)
                if (setting != null) {
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
            if (linkedAccounts.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    linkedAccounts.forEach { account ->
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                .background(if (selectedAccountId == account.id) Ids.colors.brand else Ids.colors.textTertiary)
                                .clickable { selectedAccountId = account.id }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                "${account.provider} ${account.externalAccountNumberMasked}",
                                color = if (selectedAccountId == account.id) Color.White else Ids.colors.textPrimary,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            } else {
                Text("${linkedAccounts.first().provider} ${linkedAccounts.first().externalAccountNumberMasked}", color = Ids.colors.textSecondary, fontSize = 13.sp)
            }
            IdsTextField(value = threshold, onValueChange = { threshold = it }, label = "Top up when wallet drops below (RWF)", modifier = Modifier.fillMaxWidth())
            IdsTextField(value = topUpAmount, onValueChange = { topUpAmount = it }, label = "Amount to top up (RWF)", modifier = Modifier.fillMaxWidth())
            if (setting != null) {
                Text("Triggered ${setting.triggersToday}/${setting.dailyTriggerCap} times today.", color = Ids.colors.textSecondary, fontSize = 12.sp)
            }
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(if (saving) Ids.colors.textTertiary else Ids.colors.brand)
                    .clickable(enabled = !saving) {
                        val thresholdBd = threshold.trim().toBigDecimalOrNull()
                        val topUpBd = topUpAmount.trim().toBigDecimalOrNull()
                        if (thresholdBd == null || thresholdBd < BigDecimal.ZERO || topUpBd == null || topUpBd <= BigDecimal.ZERO) {
                            error = "Enter real amounts."
                            return@clickable
                        }
                        saving = true
                        error = null
                        coroutineScope.launch {
                            try {
                                NetworkClient.apiService.configureAutoTopUp(
                                    walletId,
                                    ConfigureAutoTopUpRequest(selectedAccountId, thresholdBd, topUpBd, enabled = enabled),
                                )
                                onChanged()
                            } catch (e: HttpException) {
                                error = superAppErrorMessage(e)
                            } catch (e: IOException) {
                                error = "Couldn't reach itunda. Check your connection and try again."
                            } finally {
                                saving = false
                            }
                        }
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (saving) "Saving…" else "Save", color = Color.White, fontWeight = FontWeight.Bold) }
        }
    }
}
