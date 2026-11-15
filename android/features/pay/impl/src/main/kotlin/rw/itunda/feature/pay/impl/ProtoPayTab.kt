package rw.itunda.feature.pay.impl

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import rw.itunda.core.network.GenerateCustomerPaymentCodeRequest
import rw.itunda.core.network.NetworkClient

internal enum class PaySub { SETTINGS, AMOUNT, TILL, ORDER, DONE, HISTORY, SAVED, POINTS }

/** Stateful Pay tab for the prototype layout: real payment code + real PAY account, nothing invented. */
@Composable
fun ProtoPayTab(
    onFindStores: () -> Unit,
    onSettings: () -> Unit,
    onChangeFunding: () -> Unit,
    onReceive: () -> Unit,
    scanContent: @Composable () -> Unit,
) {
    var showHub by remember { mutableStateOf(false) }
    var sub by remember { mutableStateOf<PaySub?>(null) }
    var merchant by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(ProtoPayMode.SCAN) }
    var code by remember { mutableStateOf<String?>(null) }
    var expiresAt by remember { mutableStateOf(0L) }
    var secondsLeft by remember { mutableStateOf(0) }
    var balance by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(Unit) {
        try {
            balance = NetworkClient.apiService.getAccounts().accounts.firstOrNull { it.type == "PAY" }?.balance
        } catch (_: Exception) { }
    }
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
        while (true) {
            secondsLeft = ((expiresAt - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
            delay(1000)
        }
    }
    when (sub) {
        PaySub.SETTINGS -> { PaySettingsScreen(onBack = { sub = null }, onAllSettings = onSettings); return }
        PaySub.AMOUNT -> { PayAmountScreen(merchant, onBack = { sub = null }, onPay = { sub = PaySub.DONE }); return }
        PaySub.TILL -> { PayTillScreen(onBack = { sub = null }, onNext = { merchant = it.name; sub = PaySub.ORDER }); return }
        PaySub.ORDER -> { PayOrderScreen(merchant, onBack = { sub = null }, onPay = { sub = PaySub.DONE }); return }
        PaySub.DONE -> { PayDoneScreen(merchant, onDone = { sub = null }); return }
        PaySub.HISTORY -> { PayHistoryScreen(onBack = { sub = null }); return }
        PaySub.SAVED -> { PaySavedMerchantsScreen(onBack = { sub = null }); return }
        PaySub.POINTS -> { PayPointsScreen(onBack = { sub = null }); return }
        null -> Unit
    }
    if (showHub) {
        PayHub(
            onBack = { showHub = false }, onFindStores = onFindStores,
            onOpenPoints = { sub = PaySub.POINTS }, onOpenHistory = { sub = PaySub.HISTORY }, onOpenSaved = { sub = PaySub.SAVED },
        )
        return
    }
    ProtoPayContent(
        mode = mode, onMode = { mode = it }, qrContent = code, secondsLeft = secondsLeft,
        fundingName = "itunda Pay Money",
        fundingBalance = balance?.let { String.format(java.util.Locale.US, "%,.0f", it) } ?: "—",
        onOpenPay = { showHub = true }, onSettings = { sub = PaySub.SETTINGS }, onChangeFunding = onChangeFunding, onReceive = onReceive,
        scanContent = { Column(Modifier.verticalScroll(rememberScrollState())) { scanContent() } },
        onTill = { sub = PaySub.TILL },
        onMerchant = { merchant = it; sub = if (it == "Amina K.") PaySub.AMOUNT else PaySub.ORDER },
    )
}
