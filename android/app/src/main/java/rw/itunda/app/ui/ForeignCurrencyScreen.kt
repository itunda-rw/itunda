package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.components.rememberCountUp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.SkeletonBlock
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
import rw.itunda.core.network.ConvertCurrencyRequest
import rw.itunda.core.network.CurrencyConversionDto
import rw.itunda.core.network.ExchangeRateAlertDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.OpenForeignAccountRequest
import rw.itunda.core.network.SetRateAlertRequest
import rw.itunda.core.network.Account as AccountDto
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException

// Real 토스뱅크 외화통장 (foreign-currency account) equivalent (2026-07-25) -- scoped to
// USD/EUR/GBP, the currencies real Rwandan diaspora remittance corridors (US,
// Eurozone/Belgium, UK) actually run through, not Toss's real 17-currency breadth. Real
// live conversion rate (ForeignCurrencyRateClient, a free keyless public FX feed) plus
// itunda's own real margin -- honestly a conversion between the user's OWN accounts, not
// a cross-border receiving rail (see backend ForeignCurrencyAccountService's own doc
// comment for why that part stays out of scope). Same no-ViewModel, NetworkClient-direct
// shape as UpfrontDepositScreen.kt.
private val SUPPORTED_CURRENCIES = listOf("USD", "EUR", "GBP")

// Real Toss product-intro pattern (docs/UI_UX_GUIDELINES.md rule 13) -- see
// ForeignCurrencyIntroContent.kt's own doc comment. Mirrors Grow31SavingsScreen.kt's/
// WeeklySavingsScreen.kt's identical LIST/INTRO mode split.
private enum class ForeignCurrencyMode { LIST, INTRO }

@Composable
fun ForeignCurrencyScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var mode by remember { mutableStateOf(ForeignCurrencyMode.LIST) }
    var selectedCurrency by remember { mutableStateOf<String?>(null) }
    var accounts by remember { mutableStateOf<List<AccountDto>?>(null) }
    var conversions by remember { mutableStateOf<List<CurrencyConversionDto>?>(null) }
    var rateAlerts by remember { mutableStateOf<List<ExchangeRateAlertDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var openingCurrency by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val accountsRes = NetworkClient.apiService.getForeignAccounts()
                if (accountsRes.success) accounts = accountsRes.accounts
                val conversionsRes = NetworkClient.apiService.getMyConversions()
                if (conversionsRes.success) conversions = conversionsRes.conversions
                val alertsRes = NetworkClient.apiService.getMyRateAlerts()
                if (alertsRes.success) rateAlerts = alertsRes.alerts
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(refreshKey) { load() }

    fun openAccount(currency: String) {
        openingCurrency = currency
        coroutineScope.launch {
            try {
                NetworkClient.apiService.openForeignAccount(OpenForeignAccountRequest(currency))
                mode = ForeignCurrencyMode.LIST
                selectedCurrency = null
                refreshKey++
            } catch (e: HttpException) {
                if (rw.itunda.core.network.apiErrorCode(e) == "FOREIGN_ACCOUNT_ALREADY_EXISTS") {
                    // Real Toss-style resolution, not a dead-end error: the account
                    // genuinely already exists -- reload and show it instead of
                    // erroring on every retry.
                    mode = ForeignCurrencyMode.LIST
                    selectedCurrency = null
                    refreshKey++
                } else {
                    error = superAppErrorMessage(e)
                }
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                openingCurrency = null
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val backAction: () -> Unit = if (mode == ForeignCurrencyMode.INTRO) {
            { mode = ForeignCurrencyMode.LIST; selectedCurrency = null }
        } else {
            onBack
        }
        BackTopBar(title = if (mode == ForeignCurrencyMode.INTRO) "Open account" else "Foreign currency", onBack = backAction)
        if (mode == ForeignCurrencyMode.INTRO) {
            val openCurrencies = accounts.orEmpty().map { it.currency }.toSet()
            ForeignCurrencyIntroContent(
                availableCurrencies = SUPPORTED_CURRENCIES.filter { it !in openCurrencies },
                selected = selectedCurrency,
                onSelect = { selectedCurrency = it },
                submitting = openingCurrency != null,
                onOpen = { selectedCurrency?.let { openAccount(it) } },
            )
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            error?.let { item { Text(it, color = Ids.colors.danger, fontSize = 13.sp) } }

            val list = accounts
            if (list == null) {
                item { SkeletonBlock(height = 80.dp) }
            } else {
                items(list, key = { it.id }) { account ->
                    // Real fix (flat-design sweep): dropped the per-row Card.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                            Text(account.currency, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            val animatedBalance = rememberCountUp(account.balance)
                            Text("${formatFx(animatedBalance)} ${account.currency}", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
                val openCurrencies = list.map { it.currency }.toSet()
                val missing = SUPPORTED_CURRENCIES.filter { it !in openCurrencies }
                // Real Toss product-intro pattern (docs/UI_UX_GUIDELINES.md rule 13) --
                // was a bare row of "+ Open USD"-style pills with zero explanation of
                // what the account does; see ForeignCurrencyIntroContent.kt's own doc
                // comment.
                if (missing.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Ids.colors.brand)
                                .pressScaleClickable { mode = ForeignCurrencyMode.INTRO }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("+ Open a foreign currency account", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                    }
                }
            }

            if (!accounts.isNullOrEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Convert", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                item { ConvertPanel(accounts = accounts.orEmpty(), onConverted = { refreshKey++ }) }
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Rate alerts", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                item { RateAlertsPanel(accounts = accounts.orEmpty(), alerts = rateAlerts, onChanged = { refreshKey++ }) }
            }

            val history = conversions
            if (!history.isNullOrEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Recent conversions", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                items(history, key = { it.id }) { c ->
                    // Real fix (flat-design sweep): dropped the per-row Card.
                    Column {
                            Text("${formatFx(c.fromAmount)} ${c.fromCurrency} → ${formatFx(c.toAmount)} ${c.toCurrency}", color = Ids.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Rate ${c.rate} · itunda fee ${formatFx(c.marginAmount)} ${c.toCurrency}", color = Ids.colors.textSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConvertPanel(accounts: List<AccountDto>, onConverted: () -> Unit) {
    var direction by remember { mutableStateOf(true) } // true = RWF -> foreign, false = foreign -> RWF
    var foreignCurrency by remember { mutableStateOf(accounts.first().currency) }
    var amountText by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf<Double?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val fromCurrency = if (direction) "RWF" else foreignCurrency
    val toCurrency = if (direction) foreignCurrency else "RWF"

    LaunchedEffect(fromCurrency, toCurrency) {
        rate = try {
            NetworkClient.apiService.getExchangeRate(fromCurrency, toCurrency).rate
        } catch (e: Exception) {
            null
        }
    }

    val previewAmount = amountText.trim().toDoubleOrNull()?.let { amt -> rate?.let { r -> amt * r * 0.985 } }

    // Real fix (flat-design sweep): dropped the Card wrapper -- an inline form
    // section on an otherwise-flat screen.
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft),
            ) {
                listOf(true to "RWF → foreign", false to "Foreign → RWF").forEach { (v, label) ->
                    val selected = v == direction
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (selected) Ids.colors.brand else Color.Transparent)
                            .pressScaleClickable { direction = v }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, color = if (selected) Color.White else Ids.colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                accounts.map { it.currency }.forEach { code ->
                    val selected = code == foreignCurrency
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (selected) Ids.colors.brand else Ids.colors.surfaceSoft)
                            .pressScaleClickable { foreignCurrency = code }.padding(horizontal = 14.dp, vertical = 8.dp),
                    ) { Text(code, color = if (selected) Color.White else Ids.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                }
            }
            IdsTextField(value = amountText, onValueChange = { amountText = it }, label = "Amount ($fromCurrency)", modifier = Modifier.fillMaxWidth())
            rate?.let { r ->
                Text("Live rate: 1 $fromCurrency = ${"%.4f".format(r)} $toCurrency", color = Ids.colors.textSecondary, fontSize = 12.sp)
            }
            previewAmount?.let {
                Text("You'll receive ~${formatFx(it)} $toCurrency (after itunda's 1.5% fee)", color = Ids.colors.success, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
            success?.let { Text(it, color = Ids.colors.success, fontSize = 12.sp) }
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(if (submitting) Ids.colors.textTertiary else Ids.colors.brand)
                    .pressScaleClickable(enabled = !submitting) {
                        val amount = amountText.trim().toDoubleOrNull()
                        if (amount == null || amount <= 0.0) {
                            error = "Enter a real amount."
                            return@pressScaleClickable
                        }
                        submitting = true
                        error = null
                        success = null
                        coroutineScope.launch {
                            try {
                                val res = NetworkClient.apiService.convertCurrency(ConvertCurrencyRequest(fromCurrency, toCurrency, amount))
                                if (res.success) {
                                    success = "Converted -- ${formatFx(res.conversion.toAmount)} $toCurrency credited."
                                    amountText = ""
                                    onConverted()
                                }
                            } catch (e: HttpException) {
                                error = superAppErrorMessage(e)
                            } catch (e: IOException) {
                                error = "Couldn't reach itunda. Check your connection and try again."
                            } finally {
                                submitting = false
                            }
                        }
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (submitting) "Converting…" else "Convert", color = Color.White, fontWeight = FontWeight.Bold) }
    }
}

// Real Toss 외환 환율 알림 (exchange rate alert, section 121/168) -- see
// rw.itunda.core.network.SetRateAlertRequest's own doc comment. Backend shipped
// backend-only with a live-verified-safe scheduler and zero client caller anywhere;
// found via a fresh uncalled-endpoint sweep, same pattern as section 113/167's stock
// target-price alert (InvestScreen.kt).
@Composable
private fun RateAlertsPanel(accounts: List<AccountDto>, alerts: List<ExchangeRateAlertDto>, onChanged: () -> Unit) {
    var currency by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.currency ?: "") }
    var direction by remember { mutableStateOf(true) } // true = ABOVE, false = BELOW
    var targetText by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun clear(code: String) {
        coroutineScope.launch {
            try {
                NetworkClient.apiService.clearRateAlert("RWF", code)
                onChanged()
            } catch (e: Exception) {
                // Non-critical -- same "no error surfaced" convention as elsewhere in this screen.
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Real fix (flat-design sweep): dropped the per-row Card.
        alerts.forEach { a ->
            val code = if (a.fromCurrency == "RWF") a.toCurrency else a.fromCurrency
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            "RWF/$code: notify when ${if (a.direction == "ABOVE") "≥" else "≤"} ${a.targetRate}",
                            color = Ids.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        )
                        if (a.alertTriggeredAt != null) {
                            Text("Already triggered -- set a new target to re-arm it.", color = Ids.colors.textSecondary, fontSize = 11.sp)
                        }
                    }
                    Text(
                        "Remove", color = Ids.colors.danger, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        modifier = Modifier.pressScaleClickable { clear(code) },
                    )
                }
        }

        if (accounts.isNotEmpty()) {
            // Real fix (flat-design sweep): dropped the Card wrapper.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        accounts.map { it.currency }.forEach { code ->
                            val selected = code == currency
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (selected) Ids.colors.brand else Ids.colors.surfaceSoft)
                                    .pressScaleClickable { currency = code }.padding(horizontal = 14.dp, vertical = 8.dp),
                            ) { Text("RWF/$code", color = if (selected) Color.White else Ids.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft),
                    ) {
                        listOf(true to "Above", false to "Below").forEach { (v, label) ->
                            val selected = v == direction
                            Box(
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (selected) Ids.colors.brand else Color.Transparent)
                                    .pressScaleClickable { direction = v }.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(label, color = if (selected) Color.White else Ids.colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                    IdsTextField(value = targetText, onValueChange = { targetText = it }, label = "Target rate (1 RWF = ? $currency)", modifier = Modifier.fillMaxWidth())
                    error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (submitting) Ids.colors.textTertiary else Ids.colors.brand)
                            .pressScaleClickable(enabled = !submitting) {
                                val target = targetText.trim().toDoubleOrNull()
                                if (target == null || target <= 0.0) {
                                    error = "Enter a real target rate."
                                    return@pressScaleClickable
                                }
                                submitting = true
                                error = null
                                coroutineScope.launch {
                                    try {
                                        NetworkClient.apiService.setRateAlert(
                                            SetRateAlertRequest("RWF", currency, target, if (direction) "ABOVE" else "BELOW"),
                                        )
                                        targetText = ""
                                        onChanged()
                                    } catch (e: HttpException) {
                                        error = superAppErrorMessage(e)
                                    } catch (e: IOException) {
                                        error = "Couldn't reach itunda. Check your connection and try again."
                                    } finally {
                                        submitting = false
                                    }
                                }
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (submitting) "Setting…" else "Set alert", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

private fun formatFx(value: Double): String {
    val rounded = Math.round(value * 100.0) / 100.0
    return if (rounded == Math.floor(rounded)) rounded.toLong().toString() else "%.2f".format(rounded)
}
