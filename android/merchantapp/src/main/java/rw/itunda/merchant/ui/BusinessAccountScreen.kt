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
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rw.itunda.merchant.network.BusinessLedgerEntryDto
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ReceiptLong
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.merchant.network.BusinessAccountDto
import rw.itunda.merchant.network.MerchantDto
import rw.itunda.merchant.network.MoveBusinessMoneyRequest
import rw.itunda.merchant.network.NetworkClient
import java.util.UUID

/**
 * Real 토스뱅크 개인사업자 (business banking for sole proprietors) equivalent
 * (2026-07-25) -- closes a real gap named in the backend's own Merchant.kt doc comment:
 * every merchant's real card/QR collection has always settled straight into their
 * PERSONAL main account, with business income and personal spending genuinely
 * inseparable. This tab lets a merchant open a real, dedicated business account and
 * deliberately move money into/out of it, with its own real transaction history.
 */
@Composable
fun BusinessAccountTab() {
    var account by remember { mutableStateOf<BusinessAccountDto?>(null) }
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
    // Real fix (2026-08-10): found live-testing bank-mfe's identical Group account
    // deposit/withdraw bug -- "To business" and "To personal" move money in opposite
    // directions but shared this one flag+dialog, and onVerified just cleared the
    // flag with no retry at all. Worse than the pure-friction version of this bug:
    // moveToBusiness/moveToPersonal are genuinely different real transfers, so this
    // tracks which one was actually pending rather than guessing (or, as before,
    // doing nothing and leaving the merchant to redo it by hand).
    var pendingMoveAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val scope = rememberCoroutineScope()

    if (needsDeviceVerification) {
        DeviceStepUpDialog(
            onVerified = { val action = pendingMoveAction; pendingMoveAction = null; action?.invoke() },
            onCancel = { pendingMoveAction = null; needsDeviceVerification = false },
        )
    }

    fun load() {
        scope.launch {
            try {
                account = NetworkClient.apiService.getBusinessAccount().account
                transactions = try { NetworkClient.apiService.getBusinessTransactions().transactions } catch (e: Exception) { emptyList() }
                error = null
            } catch (e: Exception) {
                account = null
            } finally {
                loaded = true
            }
        }
    }

    fun moveToBusinessAction() {
        val amount = moveAmount.toDoubleOrNull()
        if (amount == null || amount <= 0.0) { error = "Enter a real amount."; return }
        moving = true
        error = null
        needsDeviceVerification = false
        scope.launch {
            try {
                NetworkClient.apiService.moveToBusiness(UUID.randomUUID().toString(), MoveBusinessMoneyRequest(amount))
                moveAmount = ""
                load()
            } catch (e: retrofit2.HttpException) {
                if (rw.itunda.merchant.network.isDeviceNotVerifiedError(e)) {
                    pendingMoveAction = { moveToBusinessAction() }
                    needsDeviceVerification = true
                } else {
                    error = rw.itunda.merchant.network.apiErrorMessage(e) ?: "Couldn't move this money. Check your personal balance."
                }
            } catch (e: Exception) {
                error = "Couldn't move this money. Check your personal balance."
            } finally {
                moving = false
            }
        }
    }

    fun moveToPersonalAction() {
        val amount = moveAmount.toDoubleOrNull()
        if (amount == null || amount <= 0.0) { error = "Enter a real amount."; return }
        moving = true
        error = null
        needsDeviceVerification = false
        scope.launch {
            try {
                NetworkClient.apiService.moveToPersonal(UUID.randomUUID().toString(), MoveBusinessMoneyRequest(amount))
                moveAmount = ""
                load()
            } catch (e: retrofit2.HttpException) {
                if (rw.itunda.merchant.network.isDeviceNotVerifiedError(e)) {
                    pendingMoveAction = { moveToPersonalAction() }
                    needsDeviceVerification = true
                } else {
                    error = rw.itunda.merchant.network.apiErrorMessage(e) ?: "Couldn't move this money. Check your business balance."
                }
            } catch (e: Exception) {
                error = "Couldn't move this money. Check your business balance."
            } finally {
                moving = false
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

    val current = account
    if (current == null) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            merchant?.let { m -> FeeWaiverCard(merchant = m, onUpdated = { merchant = it }) }
            merchant?.let { m -> WebhookUrlCard(merchant = m, onUpdated = { merchant = it }) }
            ApiIntegrationCard()
            merchant?.let { m -> StoreSettingsCard(merchant = m, onUpdated = { merchant = it }) }
            merchant?.let { m -> MoreStoreSettingsCard(merchant = m, onUpdated = { merchant = it }) }
            merchant?.let { m -> KybCard(merchant = m) }
            Text("Business account", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                "Keep your business money separate from your personal account. Your real card/QR " +
                    "collections still settle to your personal account as before -- move money into your " +
                    "business account whenever you're ready to set it aside.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            IdsButton(
                text = if (opening) "Opening…" else "Open business account",
                enabled = !opening,
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
            )
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        merchant?.let { m -> item { FeeWaiverCard(merchant = m, onUpdated = { merchant = it }) } }
        merchant?.let { m -> item { WebhookUrlCard(merchant = m, onUpdated = { merchant = it }) } }
        item { ApiIntegrationCard() }
        merchant?.let { m -> item { StoreSettingsCard(merchant = m, onUpdated = { merchant = it }) } }
        merchant?.let { m -> item { MoreStoreSettingsCard(merchant = m, onUpdated = { merchant = it }) } }
        merchant?.let { m -> item { KybCard(merchant = m) } }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Business balance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${String.format(java.util.Locale.US, "%,.0f", current.balance)} RWF", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(current.accountNumber, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Move money", fontWeight = FontWeight.Bold)
                    IdsTextField(value = moveAmount, onValueChange = { moveAmount = it }, label = "Amount (RWF)", modifier = Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IdsButton(
                            text = "To business",
                            enabled = !moving,
                            size = IdsButtonSize.Medium,
                            modifier = Modifier.weight(1f),
                            onClick = { moveToBusinessAction() },
                        )
                        IdsButton(
                            text = "To personal",
                            enabled = !moving,
                            size = IdsButtonSize.Medium,
                            modifier = Modifier.weight(1f),
                            onClick = { moveToPersonalAction() },
                        )
                    }
                }
            }
        }
        item { BusinessExpenseSummaryCard() }
        item { Text("Business transactions", fontWeight = FontWeight.Bold) }
        val txns = transactions
        if (txns.isNullOrEmpty()) {
            // Real copy-voice fix (item 244, round 7 of the empty-state pass -- native
            // MerchantApp had the same bare strings web's merchant-mfe just had, never
            // reached by the mobile-app copy-voice rounds either): auto-recorded, not
            // user-initiated setup, matching AccountTransactionsView's own precedent.
            item { EmptyState("No business transactions yet — once you send or receive money, it'll show up here.", icon = Icons.Outlined.ReceiptLong) }
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
                            "${if (entry.direction == "CREDIT") "+" else "-"}${String.format(java.util.Locale.US, "%,.0f", entry.amount)} RWF",
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
 * Real business expense summary (2026-08-11) -- see backend's AccountService.
 * getBusinessExpenseSummary doc comment for the real Toss Bank 세금 신고용 이용내역
 * 자동발송 (tax-filing usage summary) pattern this closes the honest slice of: a
 * categorized breakdown of the business account's own real spend over the last few
 * months, so a sole proprietor doesn't have to reconstruct it from raw transactions
 * by hand. Not a real Rwanda Revenue Authority filing integration -- itunda has no
 * access to file into (same genuinely-blocked-external-access category as NIDA/PSP
 * elsewhere), just itunda's own real, categorized numbers.
 */
@Composable
private fun BusinessExpenseSummaryCard() {
    var summary by remember { mutableStateOf<rw.itunda.merchant.network.BusinessExpenseSummaryResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            summary = NetworkClient.apiService.getBusinessExpenseSummary()
        } catch (e: Exception) {
            error = "Could not load your expense summary."
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Expense summary", fontWeight = FontWeight.Bold)
            Text(
                "For tax filing -- your business account's own spending, grouped by category, from the last few months.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val current = summary
            when {
                current != null && current.categories.isEmpty() -> {
                    Text("No business spending yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                current != null -> {
                    Text("${String.format(java.util.Locale.US, "%,.0f", current.totalSpent)} RWF total", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    current.categories.forEach { cat ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(cat.name, style = MaterialTheme.typography.bodyMedium)
                            Text("${String.format(java.util.Locale.US, "%,.0f", cat.amount)} RWF", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                error != null -> Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                else -> CircularProgressIndicator()
            }
        }
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
                    IdsButton(
                        text = if (busy) "…" else "Apply",
                        enabled = !busy,
                        size = IdsButtonSize.Small,
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
                    )
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
            IdsTextField(value = webhookUrl, onValueChange = { webhookUrl = it; saved = false }, label = "https://your-server.example.com/webhooks/itunda", modifier = Modifier.fillMaxWidth())
            Text(
                "We'll notify this address every time a payment completes. If it doesn't respond, we'll keep retrying for about 3 days.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (saved) Text("Saved.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            IdsButton(
                text = if (busy) "Saving…" else "Save",
                enabled = !busy,
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
            )
        }
    }
}

/**
 * Real API key + webhook delivery log/replay -- see ApiService.kt's own doc comment.
 * The natural companion to WebhookUrlCard above: generate a key to authenticate
 * programmatic requests, and see whether the configured URL is actually receiving
 * delivery attempts (with a Replay action once all 7 real retries are exhausted).
 */
@Composable
private fun ApiIntegrationCard() {
    var apiKey by remember { mutableStateOf<String?>(null) }
    var generating by remember { mutableStateOf(false) }
    var generateError by remember { mutableStateOf<String?>(null) }
    var webhookSecret by remember { mutableStateOf<String?>(null) }
    var generatingSecret by remember { mutableStateOf(false) }
    var generateSecretError by remember { mutableStateOf<String?>(null) }
    var deliveries by remember { mutableStateOf<List<rw.itunda.merchant.network.WebhookDeliveryDto>?>(null) }
    var replayingId by remember { mutableStateOf<String?>(null) }
    var deliveriesError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun loadDeliveries() {
        scope.launch {
            try {
                deliveries = NetworkClient.apiService.getWebhookDeliveries().deliveries
                deliveriesError = null
            } catch (e: Exception) {
                deliveriesError = "Could not load webhook deliveries."
            }
        }
    }
    LaunchedEffect(Unit) { loadDeliveries() }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("API integration", fontWeight = FontWeight.Bold)
            Text(
                "For merchants integrating their own systems with itunda.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IdsButton(
                text = if (generating) "Generating…" else "Generate a new API key",
                enabled = !generating,
                onClick = {
                    generating = true
                    generateError = null
                    scope.launch {
                        try {
                            apiKey = NetworkClient.apiService.generateApiKey().apiKey
                        } catch (e: Exception) {
                            generateError = "Could not generate an API key."
                        } finally {
                            generating = false
                        }
                    }
                },
            )
            apiKey?.let { key ->
                Text(key, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
            generateError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            // Real webhook signature verification (2026-08-30) -- see backend
            // WebhookDeliveryService's own doc comment. Lets a merchant's own server
            // verify a PAYMENT_STATUS_CHANGED POST genuinely came from itunda
            // (HMAC-SHA256 over the raw body, X-Itunda-Signature header).
            Text(
                "Used to verify a webhook delivery genuinely came from itunda — check the X-Itunda-Signature header against an HMAC-SHA256 of the raw request body using this secret.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IdsButton(
                text = if (generatingSecret) "Generating…" else "Generate a new webhook secret",
                enabled = !generatingSecret,
                onClick = {
                    generatingSecret = true
                    generateSecretError = null
                    scope.launch {
                        try {
                            webhookSecret = NetworkClient.apiService.generateWebhookSecret().webhookSecret
                        } catch (e: Exception) {
                            generateSecretError = "Could not generate a webhook secret."
                        } finally {
                            generatingSecret = false
                        }
                    }
                },
            )
            webhookSecret?.let { secret ->
                Text(secret, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
            generateSecretError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            Text("Recent webhook deliveries", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            deliveriesError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            val list = deliveries
            if (list == null) {
                Text("Loading…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (list.isEmpty()) {
                // Real copy-voice fix (item 244, round 7 of the empty-state pass):
                // event-driven, not something to set up further here.
                EmptyState("No webhook deliveries yet — deliveries will show up here once an event triggers your webhook.", icon = Icons.Outlined.ReceiptLong)
            } else {
                list.take(20).forEach { d ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(d.eventType, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${d.status} · ${d.attemptCount} attempt${if (d.attemptCount == 1) "" else "s"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (d.status == "EXHAUSTED") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (d.status == "EXHAUSTED") {
                            IdsButton(
                                text = if (replayingId == d.id) "Replaying…" else "Replay",
                                enabled = replayingId != d.id,
                                size = IdsButtonSize.Small,
                                onClick = {
                                    replayingId = d.id
                                    scope.launch {
                                        try {
                                            NetworkClient.apiService.replayWebhookDelivery(d.id)
                                            loadDeliveries()
                                        } catch (e: Exception) {
                                            deliveriesError = "Could not replay this delivery."
                                        } finally {
                                            replayingId = null
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
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
    var minOrderAmount by remember(merchant.id) { mutableStateOf(merchant.minOrderAmount?.let { String.format(java.util.Locale.US, "%.0f", it) } ?: "") }
    var cashbackPercent by remember(merchant.id) { mutableStateOf(merchant.cashbackRate?.let { String.format(java.util.Locale.US, "%.1f", it * 100) } ?: "") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    var scheduledBusy by remember { mutableStateOf(false) }
    var scheduledError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Store settings", fontWeight = FontWeight.Bold)
            IdsTextField(value = category, onValueChange = { category = it; saved = false }, label = "Category (e.g. Rwandan, Bakery, Cafe)", modifier = Modifier.fillMaxWidth())
            IdsTextField(value = photoUrl, onValueChange = { photoUrl = it; saved = false }, label = "Store photo URL", modifier = Modifier.fillMaxWidth())
            IdsTextField(value = minOrderAmount, onValueChange = { minOrderAmount = it; saved = false }, label = "Minimum order (RWF, blank = none)", modifier = Modifier.fillMaxWidth())
            IdsTextField(value = cashbackPercent, onValueChange = { cashbackPercent = it; saved = false }, label = "Boosted cashback (0-5%, blank = standard)", modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (saved) Text("Saved.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            IdsButton(
                text = if (busy) "Saving…" else "Save",
                enabled = !busy,
                onClick = {
                    val trimmedCategory = category.trim()
                    if (trimmedCategory.isEmpty()) { error = "Enter a real category."; return@IdsButton }
                    val minOrder = minOrderAmount.trim().let { if (it.isEmpty()) null else it.toDoubleOrNull() }
                    if (minOrderAmount.trim().isNotEmpty() && minOrder == null) { error = "Enter a real minimum order amount."; return@IdsButton }
                    val cashbackRate = cashbackPercent.trim().let { if (it.isEmpty()) null else it.toDoubleOrNull()?.div(100.0) }
                    if (cashbackPercent.trim().isNotEmpty() && cashbackRate == null) { error = "Enter a real cashback percent."; return@IdsButton }
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
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Accept scheduled orders", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text("Let buyers pick a future delivery/pickup time.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IdsButton(
                    text = if (scheduledBusy) "…" else if (merchant.acceptsScheduledOrders) "On" else "Off",
                    enabled = !scheduledBusy,
                    variant = if (merchant.acceptsScheduledOrders) IdsButtonVariant.Filled else IdsButtonVariant.Tinted,
                    size = IdsButtonSize.Small,
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
                )
            }
            scheduledError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
