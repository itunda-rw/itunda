package rw.itunda.app.ui

import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.dashedBorder
import rw.itunda.core.designsystem.theme.Ids
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rw.itunda.app.R
import rw.itunda.app.miniapps.InsuranceMiniAppActivity
import rw.itunda.app.miniapps.PayBillsMiniAppActivity
import rw.itunda.app.miniapps.RewardTasksMiniAppActivity
import rw.itunda.core.network.LinkAccountRequest
import rw.itunda.core.network.LinkedAccountEntityDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.superAppErrorMessage
import rw.itunda.core.network.OverviewResponse
import java.util.UUID

// Real "My assets" tab-by-tab redesign (2026-08-27, direct user reference: 3 real
// Toss "총자산" screenshots, "this is how my asset screen should look like"). Toss's
// real pattern: a horizontal scrollable category tab bar, each tab showing either a
// real summary card or a masked teaser card with a dashed-border CTA -- a real
// up-sell-to-link pattern, not fabricated data. See OverviewService.kt's own doc
// comments for exactly how each of the 4 new categories (Cards/Vehicles/Tax/Points)
// is sourced. Android already used M3 Card throughout this screen (never flattened
// in the 2026-08-24 flat-design sweep web/iOS both went through), so this build only
// adds the tab bar + teaser pattern, no card-vs-flat reversal needed.
private val COMMON_LINK_PROVIDERS = listOf("MTN Mobile Money", "Airtel Money", "Bank of Kigali", "Equity Bank Rwanda")
// Real friction point found live via Toss Simplicity21 research (2026-08-08, session 2-1
// "신은 디테일에 있다" -- eliminating friction from a real bank-linking flow): for a MoMo
// provider, the "account number" IS the caller's own real phone number -- the same number
// they're already logged in with. Making them retype it is unnecessary friction with a
// real, already-known answer, the exact shape that session's own title names.
private val MOMO_LINK_PROVIDERS = setOf("MTN Mobile Money", "Airtel Money")

private enum class AssetTab { ACCOUNTS, CARDS, LOANS, INVESTMENT, INSURANCE, REAL_ESTATE, CAR, TAX, POINTS }

// Real itunda-owned, freely-spendable wallet types -- distinct from locked-purpose
// product ledgers (SAVINGS/INVESTMENT/LOAN/GROUP/WEEKLY_SAVINGS/UPFRONT_DEPOSIT/
// GROW31_SAVINGS, each with its own dedicated withdraw/close flow) and from PAY (kept
// asymmetric from Bank on purpose, see project_itunda_bank_pay_separation) -- only
// these can realistically fund an arbitrary P2P send the way a real Toss checking/
// foreign-currency/business account can. Mirrors bank-mfe's identical
// SENDABLE_ACCOUNT_TYPES the same day.
private val SENDABLE_ACCOUNT_TYPES = setOf("MAIN", "FOREIGN_CURRENCY", "BUSINESS", "MINI")

@Composable
fun OverviewScreen(
    onBack: () -> Unit,
    onOpenCard: () -> Unit,
    onOpenLoans: () -> Unit,
    onOpenInvest: () -> Unit,
    onOpenProperty: () -> Unit,
    onOpenVehicleValuation: () -> Unit,
    // Real gap found live (2026-08-31, direct user reference of their own Toss app's
    // "My accounts" screen: every account row -- checking, savings pockets, even a
    // linked external bank account -- carries a "Send" action). This screen's own
    // account rows previously had no action at all. Linked external accounts
    // deliberately get no equivalent below: itunda only ever shows a real, honest
    // simulated demoBalance for those, it has no real access to move money out of an
    // account it doesn't control.
    onSend: (accountId: String, accountName: String, balance: Double) -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var overview by remember { mutableStateOf<OverviewResponse?>(null) }
    var linkedAccounts by remember { mutableStateOf<List<LinkedAccountEntityDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showLinkForm by remember { mutableStateOf(false) }
    var providerText by remember { mutableStateOf("") }
    var accountNumberText by remember { mutableStateOf("") }
    var myPhoneNumber by remember { mutableStateOf("") }
    var activeTab by remember { mutableStateOf(AssetTab.ACCOUNTS) }
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
    LaunchedEffect(Unit) {
        try { myPhoneNumber = NetworkClient.authApi.getProfile().user.phoneNumber } catch (_: Exception) {
            // Non-critical -- the pre-fill just won't happen; typing it manually still works.
        }
    }

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
                item {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        AssetTabLabel(stringResource(R.string.overview_accounts), activeTab == AssetTab.ACCOUNTS) { activeTab = AssetTab.ACCOUNTS }
                        AssetTabLabel(stringResource(R.string.overview_tab_cards), activeTab == AssetTab.CARDS) { activeTab = AssetTab.CARDS }
                        AssetTabLabel(stringResource(R.string.overview_tab_loans), activeTab == AssetTab.LOANS) { activeTab = AssetTab.LOANS }
                        AssetTabLabel(stringResource(R.string.overview_tab_investment), activeTab == AssetTab.INVESTMENT) { activeTab = AssetTab.INVESTMENT }
                        AssetTabLabel(stringResource(R.string.overview_tab_insurance), activeTab == AssetTab.INSURANCE) { activeTab = AssetTab.INSURANCE }
                        AssetTabLabel(stringResource(R.string.overview_tab_real_estate), activeTab == AssetTab.REAL_ESTATE) { activeTab = AssetTab.REAL_ESTATE }
                        AssetTabLabel(stringResource(R.string.overview_tab_car), activeTab == AssetTab.CAR) { activeTab = AssetTab.CAR }
                        AssetTabLabel(stringResource(R.string.overview_tab_tax), activeTab == AssetTab.TAX) { activeTab = AssetTab.TAX }
                        AssetTabLabel(stringResource(R.string.overview_tab_points), activeTab == AssetTab.POINTS) { activeTab = AssetTab.POINTS }
                    }
                }
                when (activeTab) {
                    AssetTab.ACCOUNTS -> items(current.accounts, key = { it.id }) { account ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Column {
                                    Text(account.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(account.type, style = MaterialTheme.typography.bodySmall)
                                }
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("${account.currency} ${account.balance}", style = MaterialTheme.typography.bodyLarge)
                                    if (account.type in SENDABLE_ACCOUNT_TYPES) {
                                        IdsButton(
                                            text = stringResource(R.string.overview_send),
                                            variant = IdsButtonVariant.Tinted,
                                            size = IdsButtonSize.Small,
                                            onClick = { onSend(account.id, account.name, account.balance.toDouble()) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    AssetTab.CARDS -> item {
                        if (current.cards.hasCard) {
                            SummaryRow(
                                stringResource(R.string.overview_card_number, current.cards.last4 ?: ""),
                                if (current.cards.frozen == true) stringResource(R.string.overview_card_frozen) else stringResource(R.string.overview_card_active),
                                stringResource(R.string.overview_manage), onOpenCard,
                            )
                        } else {
                            TeaserCard("••••", stringResource(R.string.overview_teaser_cards), stringResource(R.string.overview_teaser_cards_cta), onOpenCard)
                        }
                    }
                    AssetTab.LOANS -> item {
                        if (current.loans.activeCount > 0) {
                            SummaryRow(
                                stringResource(R.string.overview_loans_title),
                                stringResource(R.string.overview_loans_value, current.loans.totalOutstanding, current.loans.activeCount),
                                stringResource(R.string.overview_manage), onOpenLoans,
                            )
                        } else {
                            TeaserCard("₩???", stringResource(R.string.overview_teaser_loans), stringResource(R.string.overview_teaser_loans_cta), onOpenLoans)
                        }
                    }
                    AssetTab.INVESTMENT -> item {
                        if (current.investments.holdingCount > 0) {
                            SummaryRow(
                                stringResource(R.string.overview_investments_title),
                                stringResource(R.string.overview_investments_value, current.investments.totalCostBasis, current.investments.holdingCount),
                                stringResource(R.string.overview_manage), onOpenInvest,
                            )
                        } else {
                            TeaserCard("??%", stringResource(R.string.overview_teaser_investment), stringResource(R.string.overview_teaser_investment_cta), onOpenInvest)
                        }
                    }
                    AssetTab.INSURANCE -> item {
                        val openInsurance = { context.startActivity(Intent(context, InsuranceMiniAppActivity::class.java)) }
                        if (current.insurance.activePolicyCount > 0) {
                            SummaryRow(
                                stringResource(R.string.overview_insurance_title),
                                stringResource(R.string.overview_insurance_value, current.insurance.activePolicyCount, current.insurance.totalMonthlyPremium),
                                stringResource(R.string.overview_manage), openInsurance,
                            )
                        } else {
                            TeaserCard("???", stringResource(R.string.overview_teaser_insurance), stringResource(R.string.overview_teaser_insurance_cta), openInsurance)
                        }
                    }
                    AssetTab.REAL_ESTATE -> item {
                        // Real estate has no home-valuation/ownership-tracking backend feature
                        // at all (the realestate module is a marketplace listing flow, not a
                        // "track your own home" asset feature) -- always a teaser, matches
                        // Toss's own screenshot showing this tab in teaser state too.
                        TeaserCard("₩???", stringResource(R.string.overview_teaser_real_estate), stringResource(R.string.overview_teaser_real_estate_cta), onOpenProperty)
                    }
                    AssetTab.CAR -> item {
                        if (current.vehicles.vehicleCount > 0) {
                            SummaryRow(
                                stringResource(R.string.overview_tab_car),
                                stringResource(R.string.overview_car_summary, current.vehicles.vehicleCount, current.vehicles.totalPurchasePrice),
                                stringResource(R.string.overview_manage), onOpenVehicleValuation,
                            )
                        } else {
                            TeaserCard("₩???", stringResource(R.string.overview_teaser_car), stringResource(R.string.overview_teaser_car_cta), onOpenVehicleValuation)
                        }
                    }
                    AssetTab.TAX -> item {
                        // Always a real card, even at zero payments -- matches Toss's own
                        // always-populated Tax tab (no "link a tax account" step exists;
                        // paying a real RRA bill through Bills IS the real activity this
                        // reflects).
                        SummaryRow(
                            stringResource(R.string.overview_tab_tax),
                            stringResource(R.string.overview_tax_summary, current.tax.paymentCount, current.tax.totalPaid),
                            stringResource(R.string.overview_manage),
                        ) { context.startActivity(Intent(context, PayBillsMiniAppActivity::class.java)) }
                    }
                    AssetTab.POINTS -> item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(stringResource(R.string.overview_points_summary, current.points.rewardsTotal), style = MaterialTheme.typography.bodyLarge)
                                Text(stringResource(R.string.overview_pay_money_balance, current.points.payMoneyBalance), style = MaterialTheme.typography.bodySmall)
                                IdsButton(
                                    text = stringResource(R.string.overview_manage), variant = IdsButtonVariant.Tinted, size = IdsButtonSize.Small,
                                    onClick = { context.startActivity(Intent(context, RewardTasksMiniAppActivity::class.java)) },
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(4.dp)) }
                item { Text(stringResource(R.string.overview_linked_accounts), style = MaterialTheme.typography.titleMedium) }
                items(linkedAccounts, key = { it.id }) { account ->
                    LinkedAccountCard(account, busy) {
                        busy = true
                        scope.launch {
                            try { NetworkClient.apiService.unlinkAccount(account.id); refresh() }
                            catch (e: retrofit2.HttpException) { error = superAppErrorMessage(e) }
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
                                        onClick = {
                                            providerText = name
                                            // Real friction fix (2026-08-10) -- pre-fill with the
                                            // caller's own already-known phone number for a MoMo
                                            // provider, still editable in case they want to link a
                                            // different number. Left blank for a real bank, where
                                            // the account number is genuinely a different value.
                                            if (name in MOMO_LINK_PROVIDERS && accountNumberText.isBlank()) {
                                                accountNumberText = myPhoneNumber
                                            }
                                        },
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
                                            val linked = NetworkClient.apiService.linkAccount(UUID.randomUUID().toString(), LinkAccountRequest(providerText, accountNumberText)).linkedAccount
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
private fun AssetTabLabel(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Ids.colors.textPrimary else Ids.colors.textSecondary,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .height(2.dp)
                .width(if (selected) 24.dp else 0.dp)
                .background(Ids.colors.brand),
        )
    }
}

@Composable
private fun SummaryRow(title: String, value: String, ctaLabel: String, onCta: () -> Unit) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        IdsButton(text = ctaLabel, variant = IdsButtonVariant.Tinted, size = IdsButtonSize.Small, onClick = onCta)
    }
}

@Composable
private fun TeaserCard(maskedValue: String, message: String, ctaLabel: String, onCta: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .dashedBorder(Ids.colors.divider)
            .padding(20.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        Text(maskedValue, style = MaterialTheme.typography.headlineSmall, color = Ids.colors.textTertiary)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = Ids.colors.textSecondary)
        Spacer(Modifier.height(10.dp))
        IdsButton(text = ctaLabel, variant = IdsButtonVariant.Tinted, size = IdsButtonSize.Small, onClick = onCta)
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
