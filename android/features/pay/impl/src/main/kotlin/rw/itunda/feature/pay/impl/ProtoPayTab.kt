package rw.itunda.feature.pay.impl

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.theme.Ids
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rw.itunda.core.network.GenerateCustomerPaymentCodeRequest
import rw.itunda.core.network.NetworkClient

internal enum class PaySub { SETTINGS, AMOUNT, TILL, ORDER, DONE, HISTORY, SAVED, POINTS, RECEIPT, COUPONS, TOP_UP }

/** Stateful Pay tab for the prototype layout: real payment code + real PAY account, nothing invented. */
@Composable
fun ProtoPayTab(
    onFindStores: () -> Unit,
    onSettings: () -> Unit,
    onChangeFunding: () -> Unit,
    onReceive: () -> Unit,
    onSupport: () -> Unit,
    scanContent: @Composable (Boolean, ((() -> Unit) -> Unit), (Boolean) -> Unit) -> Unit,
) {
    var showHub by remember { mutableStateOf(false) }
    var sub by remember { mutableStateOf<PaySub?>(null) }
    var merchant by remember { mutableStateOf("") }
    var receipt by remember { mutableStateOf(0) }
    // v1038 enters Pay on tab index 1 (My code); Scan is the first tab but is not the initial state.
    var mode by remember { mutableStateOf(ProtoPayMode.MY_CODE) }
    var code by remember { mutableStateOf<String?>(null) }
    var expiresAt by remember { mutableStateOf(0L) }
    var secondsLeft by remember { mutableStateOf(0) }
    var balance by remember { mutableStateOf<Double?>(null) }
    var accounts by remember { mutableStateOf<List<rw.itunda.core.network.Account>>(emptyList()) }
    var payAccount by remember { mutableStateOf<rw.itunda.core.network.Account?>(null) }
    var accountsLoading by remember { mutableStateOf(true) }
    var accountsError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refreshAccounts() {
        accountsLoading = true
        accountsError = null
        try {
            accounts = NetworkClient.apiService.getAccounts().accounts
            payAccount = accounts.firstOrNull { it.type == "PAY" }
            balance = payAccount?.balance
        } catch (_: Exception) {
            accountsError = "Couldn't load your Pay account. Check your connection and try again."
        } finally {
            accountsLoading = false
        }
    }
    LaunchedEffect(Unit) { refreshAccounts() }
    LaunchedEffect(mode) {
        if (mode != ProtoPayMode.MY_CODE) return@LaunchedEffect
        while (true) {
            try {
                val res = NetworkClient.apiService.generateCustomerPaymentCode(GenerateCustomerPaymentCodeRequest())
                code = res.code
                expiresAt = java.time.Instant.parse(res.expiresAt).toEpochMilli()
            } catch (_: Exception) { code = null }
            delay((expiresAt - System.currentTimeMillis() - 10_000L).coerceAtLeast(5_000L))
        }
    }
    LaunchedEffect(mode) {
        if (mode != ProtoPayMode.MY_CODE) return@LaunchedEffect
        while (true) {
            secondsLeft = ((expiresAt - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
            delay(1000)
        }
    }
    when (sub) {
        PaySub.SETTINGS -> { PaySettingsScreen(onBack = { sub = null }, onAllSettings = onSettings); return }
        PaySub.AMOUNT -> { PayAmountScreen(merchant, onBack = { sub = null }, onPay = { PayReceiptState.lastAmount = it; sub = PaySub.DONE }); return }
        PaySub.TILL -> { PayTillScreen(onBack = { sub = null }, onNext = { merchant = it.name; sub = PaySub.ORDER }); return }
        PaySub.ORDER -> { PayOrderScreen(merchant, onBack = { sub = null }, onPay = { PayReceiptState.lastAmount = 7_000L; sub = PaySub.DONE }); return }
        PaySub.DONE -> { PayDoneScreen(merchant, onDone = { sub = null }); return }
        PaySub.HISTORY -> { PayHistoryScreen(onBack = { sub = null }, onOpen = { receipt = it; sub = PaySub.RECEIPT }); return }
        PaySub.RECEIPT -> { PayReceiptScreen(receipt, onBack = { sub = PaySub.HISTORY }, onPayAgain = { merchant = it; sub = PaySub.AMOUNT }); return }
        PaySub.SAVED -> { PaySavedMerchantsScreen(onBack = { sub = null }); return }
        PaySub.POINTS -> { PayPointsScreen(onBack = { sub = null }); return }
        PaySub.COUPONS -> { CouponBoxScreen(onBack = { sub = null }, onBrowseMerchants = onFindStores); return }
        PaySub.TOP_UP -> {
            val destination = payAccount
            if (destination != null) {
                AddMoneyScreen(
                    destination,
                    accounts.filter { it.id != destination.id },
                    onBack = { sub = null },
                    onDone = {
                        scope.launch {
                            refreshAccounts()
                            sub = null
                        }
                    },
                )
            } else {
                PaySubScreen(onBack = { sub = null }, title = "Top up") { p ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp)) {
                        Text(
                            if (accountsLoading) "Loading your Pay account…" else "Your Pay account isn't available",
                            color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        )
                        Text(
                            accountsError ?: if (accountsLoading) "Please wait a moment." else "Create or activate a Pay account before adding money.",
                            color = p.tt, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
                        )
                        if (!accountsLoading && accountsError != null) {
                            PayBtn("Try again", p, onClick = { scope.launch { refreshAccounts() } })
                        }
                    }
                }
            }
            return
        }
        null -> Unit
    }
    if (showHub) {
        PayHub(
            onBack = { showHub = false }, onFindStores = onFindStores,
            onOpenPoints = { sub = PaySub.POINTS }, onOpenHistory = { sub = PaySub.HISTORY }, onOpenSettings = { sub = PaySub.SETTINGS },
            onOpenCoupons = { sub = PaySub.COUPONS }, onOpenSupport = onSupport,
        )
        return
    }
    ProtoPayContent(
        mode = mode, onMode = { mode = it }, qrContent = code, secondsLeft = secondsLeft, onTopUp = { sub = PaySub.TOP_UP },
        fundingName = "itunda Pay Money",
        fundingBalance = balance?.let { String.format(java.util.Locale.US, "%,.0f", it) } ?: "—",
        onOpenPay = { showHub = true }, onSettings = { sub = PaySub.SETTINGS }, onChangeFunding = onChangeFunding, onReceive = onReceive,
        scanContent = { flash, registerPhoto, flashAvailability -> Column(Modifier.verticalScroll(rememberScrollState())) { scanContent(flash, registerPhoto, flashAvailability) } },
        onTill = { sub = PaySub.TILL },
        onMerchant = { merchant = it; sub = if (it == "Amina K.") PaySub.AMOUNT else PaySub.ORDER },
    )
}
