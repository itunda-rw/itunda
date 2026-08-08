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
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import rw.itunda.core.designsystem.components.IdsTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rw.itunda.app.R
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
    val context = LocalContext.current
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
            error = context.getString(R.string.overview_load_error)
        }
    }
    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(20.dp)) {
            BackTopBar(title = stringResource(R.string.overview_title), onBack = onBack)
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
                            Text(stringResource(R.string.overview_net_worth), style = MaterialTheme.typography.labelMedium)
                            Text(stringResource(R.string.overview_amount_rwf, current.netWorth), style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
                item { Text(stringResource(R.string.overview_accounts), style = MaterialTheme.typography.titleMedium) }
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
                    SummaryRow(stringResource(R.string.overview_savings_title), stringResource(R.string.overview_savings_value, current.savings.totalSaved, current.savings.goalCount))
                }
                item {
                    SummaryRow(stringResource(R.string.overview_loans_title), stringResource(R.string.overview_loans_value, current.loans.totalOutstanding, current.loans.activeCount))
                }
                item {
                    SummaryRow(stringResource(R.string.overview_investments_title), stringResource(R.string.overview_investments_value, current.investments.totalCostBasis, current.investments.holdingCount))
                }
                item {
                    SummaryRow(stringResource(R.string.overview_insurance_title), stringResource(R.string.overview_insurance_value, current.insurance.activePolicyCount, current.insurance.totalMonthlyPremium))
                }
                item { Spacer(Modifier.height(4.dp)) }
                item { Text(stringResource(R.string.overview_linked_accounts), style = MaterialTheme.typography.titleMedium) }
                items(linkedAccounts, key = { it.id }) { account ->
                    LinkedAccountCard(account, busy) {
                        busy = true
                        scope.launch {
                            try { NetworkClient.apiService.unlinkAccount(account.id); refresh() }
                            catch (_: Exception) { error = context.getString(R.string.overview_unlink_error) }
                            finally { busy = false }
                        }
                    }
                }
                item {
                    if (!showLinkForm) {
                        IdsButton(text = stringResource(R.string.overview_link_prompt), onClick = { showLinkForm = true })
                    } else {
                        Column {
                            Text(stringResource(R.string.overview_provider_label), style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                COMMON_LINK_PROVIDERS.forEach { name ->
                                    IdsButton(
                                        text = name,
                                        variant = IdsButtonVariant.Tinted,
                                        size = IdsButtonSize.Small,
                                        onClick = { providerText = name },
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            IdsTextField(value = providerText, onValueChange = { providerText = it }, label = stringResource(R.string.overview_provider_name_placeholder), modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            IdsTextField(value = accountNumberText, onValueChange = { accountNumberText = it }, label = stringResource(R.string.overview_account_phone_placeholder), modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            IdsButton(
                                text = if (busy) stringResource(R.string.overview_linking) else stringResource(R.string.overview_link_account),
                                enabled = !busy && providerText.isNotBlank() && accountNumberText.length >= 4,
                                onClick = {
                                    busy = true
                                    scope.launch {
                                        try {
                                            val linked = NetworkClient.apiService.linkAccount(LinkAccountRequest(providerText, accountNumberText)).linkedAccount
                                            val submittedProvider = providerText
                                            providerText = ""; accountNumberText = ""; showLinkForm = false
                                            refresh()
                                            // Real gap found via Toss Simplicity21 research (2026-08-08): a declined
                                            // provider verification is still a 200 response (the account is saved as
                                            // VERIFICATION_FAILED so it shows up in history below) -- without this
                                            // check the form just closed as if the link had worked.
                                            if (linked.status == "VERIFICATION_FAILED") {
                                                error = linked.failureReason
                                                    ?: context.getString(R.string.overview_verification_failed, submittedProvider)
                                            }
                                        } catch (_: Exception) {
                                            error = context.getString(R.string.overview_link_error)
                                        } finally { busy = false }
                                    }
                                },
                            )
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
        val demoBalance = account.demoBalance
        if (demoBalance != null) {
            Text(stringResource(R.string.overview_demo_balance, account.demoBalanceCurrency ?: "", demoBalance), style = MaterialTheme.typography.bodySmall)
        }
        if (account.status == "LINKED") {
            IdsButton(text = stringResource(R.string.overview_unlink), enabled = !busy, variant = IdsButtonVariant.Tinted, size = IdsButtonSize.Small, onClick = onUnlink)
        }
    }
}
