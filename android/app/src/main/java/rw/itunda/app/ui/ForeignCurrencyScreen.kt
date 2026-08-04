package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import rw.itunda.core.designsystem.components.IdsTextField
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
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.OpenForeignWalletRequest
import rw.itunda.core.network.Wallet as WalletDto
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException

// Real 토스뱅크 외화통장 (foreign-currency account) equivalent (2026-07-25) -- scoped to
// USD/EUR/GBP, the currencies real Rwandan diaspora remittance corridors (US,
// Eurozone/Belgium, UK) actually run through, not Toss's real 17-currency breadth. Real
// live conversion rate (ForeignCurrencyRateClient, a free keyless public FX feed) plus
// itunda's own real margin -- honestly a conversion between the user's OWN wallets, not
// a cross-border receiving rail (see backend ForeignCurrencyWalletService's own doc
// comment for why that part stays out of scope). Same no-ViewModel, NetworkClient-direct
// shape as UpfrontDepositScreen.kt.
private val SUPPORTED_CURRENCIES = listOf("USD", "EUR", "GBP")

@Composable
fun ForeignCurrencyScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var wallets by remember { mutableStateOf<List<WalletDto>?>(null) }
    var conversions by remember { mutableStateOf<List<CurrencyConversionDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var openingCurrency by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val walletsRes = NetworkClient.apiService.getForeignWallets()
                if (walletsRes.success) wallets = walletsRes.wallets
                val conversionsRes = NetworkClient.apiService.getMyConversions()
                if (conversionsRes.success) conversions = conversionsRes.conversions
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(refreshKey) { load() }

    fun openWallet(currency: String) {
        openingCurrency = currency
        coroutineScope.launch {
            try {
                NetworkClient.apiService.openForeignWallet(OpenForeignWalletRequest(currency))
                refreshKey++
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                openingCurrency = null
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = "Foreign currency", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            error?.let { item { Text(it, color = Ids.colors.danger, fontSize = 13.sp) } }

            val list = wallets
            if (list == null) {
                item { Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth().height(80.dp)) {} }
            } else {
                items(list, key = { it.id }) { wallet ->
                    Card(
                        shape = RoundedCornerShape(Ids.layout.cardCornerRadius),
                        colors = CardDefaults.cardColors(containerColor = TossCard),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(wallet.currency, color = TossText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("${formatFx(wallet.balance)} ${wallet.currency}", color = TossText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
                val openCurrencies = list.map { it.currency }.toSet()
                val missing = SUPPORTED_CURRENCIES.filter { it !in openCurrencies }
                if (missing.isNotEmpty()) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            missing.forEach { code ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (openingCurrency == code) Ids.colors.textTertiary else TossBlue)
                                        .clickable(enabled = openingCurrency == null) { openWallet(code) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                ) { Text("+ Open $code", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                            }
                        }
                    }
                }
            }

            if (!wallets.isNullOrEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Convert", color = TossText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                item { ConvertPanel(wallets = wallets.orEmpty(), onConverted = { refreshKey++ }) }
            }

            val history = conversions
            if (!history.isNullOrEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Recent conversions", color = TossText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                items(history, key = { it.id }) { c ->
                    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = TossCard), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("${formatFx(c.fromAmount)} ${c.fromCurrency} → ${formatFx(c.toAmount)} ${c.toCurrency}", color = TossText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Rate ${c.rate} · itunda fee ${formatFx(c.marginAmount)} ${c.toCurrency}", color = TossSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConvertPanel(wallets: List<WalletDto>, onConverted: () -> Unit) {
    var direction by remember { mutableStateOf(true) } // true = RWF -> foreign, false = foreign -> RWF
    var foreignCurrency by remember { mutableStateOf(wallets.first().currency) }
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

    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = TossCard), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft),
            ) {
                listOf(true to "RWF → foreign", false to "Foreign → RWF").forEach { (v, label) ->
                    val selected = v == direction
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (selected) TossBlue else Color.Transparent)
                            .clickable { direction = v }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, color = if (selected) Color.White else TossSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                wallets.map { it.currency }.forEach { code ->
                    val selected = code == foreignCurrency
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (selected) TossBlue else Ids.colors.surfaceSoft)
                            .clickable { foreignCurrency = code }.padding(horizontal = 14.dp, vertical = 8.dp),
                    ) { Text(code, color = if (selected) Color.White else TossText, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                }
            }
            IdsTextField(value = amountText, onValueChange = { amountText = it }, label = "Amount ($fromCurrency)", modifier = Modifier.fillMaxWidth())
            rate?.let { r ->
                Text("Live rate: 1 $fromCurrency = ${"%.4f".format(r)} $toCurrency", color = TossSecondary, fontSize = 12.sp)
            }
            previewAmount?.let {
                Text("You'll receive ~${formatFx(it)} $toCurrency (after itunda's 1.5% fee)", color = Ids.colors.success, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
            success?.let { Text(it, color = Ids.colors.success, fontSize = 12.sp) }
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(if (submitting) Ids.colors.textTertiary else TossBlue)
                    .clickable(enabled = !submitting) {
                        val amount = amountText.trim().toDoubleOrNull()
                        if (amount == null || amount <= 0.0) {
                            error = "Enter a real amount."
                            return@clickable
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
}

private fun formatFx(value: Double): String {
    val rounded = Math.round(value * 100.0) / 100.0
    return if (rounded == Math.floor(rounded)) rounded.toLong().toString() else "%.2f".format(rounded)
}
