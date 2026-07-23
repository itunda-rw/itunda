package rw.itunda.app.ui

import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.SkeletonBlock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rw.itunda.core.network.LinkAccountRequest
import rw.itunda.core.network.LinkedAccountEntityDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.OverviewResponse

// Real Toss-style unified account overview (2026-07-22) -- found fully built on the
// backend (rw.itunda.overview) with zero client UI anywhere. Aggregates wallets,
// savings, loans, investments, insurance, and linked external bank/MoMo accounts in
// one screen, matching Toss's own real "전체" home tab. See OverviewService.kt's own
// doc comment for why insurance is excluded from net worth (a sunk expense, not an
// asset) and LinkedAccount.kt's for why linked balances are honestly labeled demo --
// itunda has no live Open Banking access to fetch a real one.
private val COMMON_LINK_PROVIDERS = listOf("MTN Mobile Money", "Airtel Money", "Bank of Kigali", "Equity Bank Rwanda")

@Composable
fun OverviewScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var overview by remember { mutableStateOf<OverviewResponse?>(null) }
    var linkedAccounts by remember { mutableStateOf<List<LinkedAccountEntityDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showLinkForm by remember { mutableStateOf(false) }
    var providerText by remember { mutableStateOf("") }
    var accountNumberText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        try {
            overview = NetworkClient.apiService.getOverview()
            linkedAccounts = NetworkClient.apiService.getLinkedAccounts().linkedAccounts
            error = null
        } catch (_: Exception) {
            error = "Could not load your overview."
        }
    }
    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(20.dp)) {
            BackTopBar(title = "My assets", onBack = onBack)
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val current = overview
            if (current == null) {
                if (error != null) item { Text(error!!, color = MaterialTheme.colorScheme.error) }
                else item { SkeletonBlock() }
            } else {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Net worth", style = MaterialTheme.typography.labelMedium)
                            Text("RWF ${current.netWorth}", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
                item { Text("Accounts", style = MaterialTheme.typography.titleMedium) }
                items(current.accounts, key = { it.id }) { account ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(account.name, style = MaterialTheme.typography.bodyLarge)
                                Text(account.type, style = MaterialTheme.typography.bodySmall)
                            }
                            Text("${account.currency} ${account.balance}", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                item {
                    SummaryRow("Savings", "RWF ${current.savings.totalSaved} across ${current.savings.goalCount} goal(s)")
                }
                item {
                    SummaryRow("Loans", "RWF ${current.loans.totalOutstanding} outstanding, ${current.loans.activeCount} active")
                }
                item {
                    SummaryRow("Investments", "RWF ${current.investments.totalCostBasis} cost basis, ${current.investments.holdingCount} holding(s)")
                }
                item {
                    SummaryRow("Insurance", "${current.insurance.activePolicyCount} active plan(s), RWF ${current.insurance.totalMonthlyPremium}/month")
                }
                item { Spacer(Modifier.height(4.dp)) }
                item { Text("Linked accounts", style = MaterialTheme.typography.titleMedium) }
                items(linkedAccounts, key = { it.id }) { account ->
                    LinkedAccountCard(account, busy) {
                        busy = true
                        scope.launch {
                            try { NetworkClient.apiService.unlinkAccount(account.id); refresh() }
                            catch (_: Exception) { error = "Could not unlink this account." }
                            finally { busy = false }
                        }
                    }
                }
                item {
                    if (!showLinkForm) {
                        Button(onClick = { showLinkForm = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Link a bank or mobile money account")
                        }
                    } else {
                        Column {
                            Text("Provider", style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                COMMON_LINK_PROVIDERS.forEach { name ->
                                    Button(onClick = { providerText = name }) { Text(name) }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                providerText, { providerText = it },
                                label = { Text("Provider name") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                accountNumberText, { accountNumberText = it },
                                label = { Text("Account / phone number") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                enabled = !busy && providerText.isNotBlank() && accountNumberText.length >= 4,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    busy = true
                                    scope.launch {
                                        try {
                                            NetworkClient.apiService.linkAccount(LinkAccountRequest(providerText, accountNumberText))
                                            providerText = ""; accountNumberText = ""; showLinkForm = false
                                            refresh()
                                        } catch (_: Exception) {
                                            error = "Could not link that account."
                                        } finally { busy = false }
                                    }
                                },
                            ) { Text(if (busy) "Linking…" else "Link account") }
                        }
                    }
                }
                error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            }
        }
    }
}

@Composable
private fun SummaryRow(title: String, value: String) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LinkedAccountCard(account: LinkedAccountEntityDto, busy: Boolean, onUnlink: () -> Unit) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp)) {
        Text(account.provider, style = MaterialTheme.typography.bodyLarge)
        Text(account.externalAccountNumberMasked, style = MaterialTheme.typography.bodySmall)
        Text(account.status, style = MaterialTheme.typography.bodySmall)
        if (account.demoBalance != null) {
            Text("Demo balance: ${account.demoBalanceCurrency} ${account.demoBalance}", style = MaterialTheme.typography.bodySmall)
        }
        if (account.status == "LINKED") {
            Button(enabled = !busy, onClick = onUnlink, modifier = Modifier.padding(top = 8.dp)) { Text("Unlink") }
        }
    }
}
