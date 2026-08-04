package rw.itunda.merchant.ui

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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rw.itunda.merchant.network.BusinessLedgerEntryDto
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ReceiptLong
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.merchant.network.BusinessWalletDto
import rw.itunda.merchant.network.MerchantDto
import rw.itunda.merchant.network.MoveBusinessMoneyRequest
import rw.itunda.merchant.network.NetworkClient
import java.util.UUID

/**
 * Real 토스뱅크 개인사업자 (business banking for sole proprietors) equivalent
 * (2026-07-25) -- closes a real gap named in the backend's own Merchant.kt doc comment:
 * every merchant's real card/QR collection has always settled straight into their
 * PERSONAL main wallet, with business income and personal spending genuinely
 * inseparable. This tab lets a merchant open a real, dedicated business wallet and
 * deliberately move money into/out of it, with its own real transaction history.
 */
@Composable
fun BusinessAccountTab() {
    var wallet by remember { mutableStateOf<BusinessWalletDto?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var merchant by remember { mutableStateOf<MerchantDto?>(null) }
    var transactions by remember { mutableStateOf<List<BusinessLedgerEntryDto>?>(null) }
    var opening by remember { mutableStateOf(false) }
    var moveAmount by remember { mutableStateOf("") }
    var moving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Real device step-up (2026-07-28 port) -- a real 403 DEVICE_NOT_VERIFIED (this
    // device hasn't been step-up-verified yet) gets its own case, not a generic error.
    var needsDeviceVerification by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (needsDeviceVerification) {
        DeviceStepUpDialog(
            onVerified = { needsDeviceVerification = false },
            onCancel = { needsDeviceVerification = false },
        )
    }

    fun load() {
        scope.launch {
            try {
                wallet = NetworkClient.apiService.getBusinessAccount().wallet
                transactions = try { NetworkClient.apiService.getBusinessTransactions().transactions } catch (e: Exception) { emptyList() }
                error = null
            } catch (e: Exception) {
                wallet = null
            } finally {
                loaded = true
            }
        }
    }
    LaunchedEffect(Unit) {
        load()
        try { merchant = NetworkClient.apiService.getMyMerchant().merchant } catch (e: Exception) { /* fee-waiver card just stays hidden */ }
    }

    if (!loaded) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator() }
        return
    }

    val current = wallet
    if (current == null) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            merchant?.let { m -> FeeWaiverCard(merchant = m, onUpdated = { merchant = it }) }
            merchant?.let { m -> WebhookUrlCard(merchant = m, onUpdated = { merchant = it }) }
            merchant?.let { m -> StoreSettingsCard(merchant = m, onUpdated = { merchant = it }) }
            Text("Business account", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                "Keep your business money separate from your personal wallet. Your real card/QR " +
                    "collections still settle to your personal wallet as before -- move money into your " +
                    "business account whenever you're ready to set it aside.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    opening = true
                    scope.launch {
                        try {
                            NetworkClient.apiService.openBusinessAccount()
                            load()
                        } catch (e: Exception) {
                            error = "Couldn't open a business account. Try again."
                        } finally {
                            opening = false
                        }
                    }
                },
                enabled = !opening,
            ) { Text(if (opening) "Opening…" else "Open business account") }
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        merchant?.let { m -> item { FeeWaiverCard(merchant = m, onUpdated = { merchant = it }) } }
        merchant?.let { m -> item { WebhookUrlCard(merchant = m, onUpdated = { merchant = it }) } }
        merchant?.let { m -> item { StoreSettingsCard(merchant = m, onUpdated = { merchant = it }) } }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Business balance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${"%,.0f".format(current.balance)} RWF", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(current.accountNumber, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Move money", fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = moveAmount, onValueChange = { moveAmount = it }, label = { Text("Amount (RWF)") }, modifier = Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val amount = moveAmount.toDoubleOrNull()
                                if (amount == null || amount <= 0.0) { error = "Enter a real amount."; return@Button }
                                moving = true
                                error = null
                                scope.launch {
                                    try {
                                        NetworkClient.apiService.moveToBusiness(UUID.randomUUID().toString(), MoveBusinessMoneyRequest(amount))
                                        moveAmount = ""
                                        load()
                                    } catch (e: retrofit2.HttpException) {
                                        if (rw.itunda.merchant.network.isDeviceNotVerifiedError(e)) {
                                            needsDeviceVerification = true
                                        } else {
                                            error = "Couldn't move this money. Check your personal balance."
                                        }
                                    } catch (e: Exception) {
                                        error = "Couldn't move this money. Check your personal balance."
                                    } finally {
                                        moving = false
                                    }
                                }
                            },
                            enabled = !moving,
                            modifier = Modifier.weight(1f),
                        ) { Text("To business") }
                        Button(
                            onClick = {
                                val amount = moveAmount.toDoubleOrNull()
                                if (amount == null || amount <= 0.0) { error = "Enter a real amount."; return@Button }
                                moving = true
                                error = null
                                scope.launch {
                                    try {
                                        NetworkClient.apiService.moveToPersonal(UUID.randomUUID().toString(), MoveBusinessMoneyRequest(amount))
                                        moveAmount = ""
                                        load()
                                    } catch (e: retrofit2.HttpException) {
                                        if (rw.itunda.merchant.network.isDeviceNotVerifiedError(e)) {
                                            needsDeviceVerification = true
                                        } else {
                                            error = "Couldn't move this money. Check your business balance."
                                        }
                                    } catch (e: Exception) {
                                        error = "Couldn't move this money. Check your business balance."
                                    } finally {
                                        moving = false
                                    }
                                }
                            },
                            enabled = !moving,
                            modifier = Modifier.weight(1f),
                        ) { Text("To personal") }
                    }
                }
            }
        }
        item { Text("Business transactions", fontWeight = FontWeight.Bold) }
        val txns = transactions
        if (txns.isNullOrEmpty()) {
            item { EmptyState("No business transactions yet.", icon = Icons.Outlined.ReceiptLong) }
        } else {
            items(txns, key = { it.id }) { entry ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(entry.memo, style = MaterialTheme.typography.bodyMedium)
                            Text(entry.createdAt.take(10), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            "${if (entry.direction == "CREDIT") "+" else "-"}${"%,.0f".format(entry.amount)} RWF",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

/**
 * Real Naver Pay 영세 가맹점 수수료 지원 (small-merchant fee-waiver support program) --
 * see rw.itunda.merchant.MerchantFeeWaiverService's own doc comment. Eligibility (a real
 * 30-day payment-volume threshold) is checked server-side; this card just surfaces the
 * current state and lets an eligible merchant apply. merchant-mfe already has this; this
 * is the first Android client.
 */
@Composable
private fun FeeWaiverCard(merchant: MerchantDto, onUpdated: (MerchantDto) -> Unit) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val waived = merchant.feeRateOverride == 0.0
    val scope = rememberCoroutineScope()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Small-merchant fee waiver", fontWeight = FontWeight.Bold)
                    Text(
                        if (waived) {
                            "Active -- you pay no platform fee on payments you collect."
                        } else {
                            "If your payment volume over the last 30 days is small, you may qualify for a full fee waiver."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!waived) {
                    Button(
                        onClick = {
                            busy = true
                            error = null
                            scope.launch {
                                try {
                                    onUpdated(NetworkClient.apiService.applyForFeeWaiver().merchant)
                                } catch (e: Exception) {
                                    error = "Couldn't apply for a fee waiver."
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy,
                    ) { Text(if (busy) "…" else "Apply") }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/**
 * Real payment-event webhook URL settings -- see
 * rw.itunda.merchant.MerchantService.setWebhookUrl's own doc comment. merchant-mfe
 * already has this; this is the first Android client.
 */
@Composable
private fun WebhookUrlCard(merchant: MerchantDto, onUpdated: (MerchantDto) -> Unit) {
    var webhookUrl by remember(merchant.id) { mutableStateOf(merchant.webhookUrl ?: "") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Webhook URL", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = webhookUrl,
                onValueChange = { webhookUrl = it; saved = false },
                placeholder = { Text("https://your-server.example.com/webhooks/itunda") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "We'll notify this address every time a payment completes. If it doesn't respond, we'll keep retrying for about 3 days.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (saved) Text("Saved.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = {
                    busy = true
                    error = null
                    saved = false
                    scope.launch {
                        try {
                            onUpdated(NetworkClient.apiService.setWebhookUrl(rw.itunda.merchant.network.SetWebhookUrlRequest(webhookUrl)).merchant)
                            saved = true
                        } catch (e: Exception) {
                            error = "Could not save."
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy,
            ) { Text(if (busy) "Saving…" else "Save") }
        }
    }
}

/**
 * Real store-settings bundle -- category, photo URL, minimum order amount, boosted
 * cashback rate, and scheduled-orders opt-in. See MerchantController.kt's own doc
 * comments for each endpoint. Found 2026-08-01 via a dead-field sweep: category was a
 * real MerchantDto field with zero UI anywhere on this app; photo/min-order/cashback-
 * rate/scheduled-orders were real backend endpoints with zero client anywhere at all
 * (not even merchant-mfe) until this same pass.
 */
@Composable
private fun StoreSettingsCard(merchant: MerchantDto, onUpdated: (MerchantDto) -> Unit) {
    var category by remember(merchant.id) { mutableStateOf(merchant.category ?: "") }
    var photoUrl by remember(merchant.id) { mutableStateOf(merchant.photoUrl ?: "") }
    var minOrderAmount by remember(merchant.id) { mutableStateOf(merchant.minOrderAmount?.let { "%.0f".format(it) } ?: "") }
    var cashbackPercent by remember(merchant.id) { mutableStateOf(merchant.cashbackRate?.let { "%.1f".format(it * 100) } ?: "") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    var scheduledBusy by remember { mutableStateOf(false) }
    var scheduledError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Store settings", fontWeight = FontWeight.Bold)
            OutlinedTextField(value = category, onValueChange = { category = it; saved = false }, label = { Text("Category (e.g. Rwandan, Bakery, Cafe)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = photoUrl, onValueChange = { photoUrl = it; saved = false }, label = { Text("Store photo URL") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = minOrderAmount, onValueChange = { minOrderAmount = it; saved = false }, label = { Text("Minimum order (RWF, blank = none)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = cashbackPercent, onValueChange = { cashbackPercent = it; saved = false }, label = { Text("Boosted cashback (0-5%, blank = standard)") }, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (saved) Text("Saved.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = {
                    val trimmedCategory = category.trim()
                    if (trimmedCategory.isEmpty()) { error = "Enter a real category."; return@Button }
                    val minOrder = minOrderAmount.trim().let { if (it.isEmpty()) null else it.toDoubleOrNull() }
                    if (minOrderAmount.trim().isNotEmpty() && minOrder == null) { error = "Enter a real minimum order amount."; return@Button }
                    val cashbackRate = cashbackPercent.trim().let { if (it.isEmpty()) null else it.toDoubleOrNull()?.div(100.0) }
                    if (cashbackPercent.trim().isNotEmpty() && cashbackRate == null) { error = "Enter a real cashback percent."; return@Button }
                    busy = true
                    error = null
                    saved = false
                    scope.launch {
                        try {
                            var updated = NetworkClient.apiService.setCategory(rw.itunda.merchant.network.SetCategoryRequest(trimmedCategory)).merchant
                            updated = NetworkClient.apiService.setMerchantPhotoUrl(rw.itunda.merchant.network.SetMerchantPhotoUrlRequest(photoUrl.trim())).merchant
                            updated = NetworkClient.apiService.setMinOrderAmount(rw.itunda.merchant.network.SetMinOrderAmountRequest(minOrder)).merchant
                            updated = NetworkClient.apiService.setCashbackRate(rw.itunda.merchant.network.SetCashbackRateRequest(cashbackRate)).merchant
                            onUpdated(updated)
                            saved = true
                        } catch (e: Exception) {
                            error = "Could not save."
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy,
            ) { Text(if (busy) "Saving…" else "Save") }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Accept scheduled orders", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text("Let buyers pick a future delivery/pickup time.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = {
                        scheduledBusy = true
                        scheduledError = null
                        scope.launch {
                            try {
                                onUpdated(NetworkClient.apiService.setAcceptsScheduledOrders(rw.itunda.merchant.network.SetAcceptsScheduledOrdersRequest(!merchant.acceptsScheduledOrders)).merchant)
                            } catch (e: Exception) {
                                scheduledError = "Could not save."
                            } finally {
                                scheduledBusy = false
                            }
                        }
                    },
                    enabled = !scheduledBusy,
                ) { Text(if (scheduledBusy) "…" else if (merchant.acceptsScheduledOrders) "On" else "Off") }
            }
            scheduledError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
