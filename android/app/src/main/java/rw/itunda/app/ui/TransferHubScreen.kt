package rw.itunda.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.AutoTransferDto
import rw.itunda.core.network.AutoTransferFrequency
import rw.itunda.core.network.CreateAutoTransferRequest
import rw.itunda.core.network.CreateScheduledTransferRequest
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.ScheduledTransferDto
import rw.itunda.core.network.superAppErrorMessage
import java.util.UUID

/**
 * Real Toss Bank 송금 (Transfer) full page (2026-07-24) -- Home's own "Send" button
 * previously jumped straight into the 2-step Recipient/Amount flow with no page in
 * between, unlike real Toss where Home shows only a thin curated teaser and every
 * money-feature area (Transfer, Pay, Account) has its own full dedicated page grouping
 * every related action (confirmed via real Toss screenshots: the 송금 page groups
 * 송금하기/자동이체/해외송금/더치페이/etc, not just a single send action). This groups
 * itunda's own equivalents -- Send money now, real Auto-transfer (see AutoTransfer.kt's
 * own doc comment on the backend), and Transfer history -- the same real
 * curated-home-plus-full-page structure, scoped to what itunda actually has rather than
 * inventing Toss features itunda has no backend for (overseas remittance, political
 * donations).
 */
@Composable
fun TransferHubScreen(
    autoTransferCount: Int,
    onBack: () -> Unit,
    onSendMoney: () -> Unit,
    onOpenAutoTransfers: () -> Unit,
    onOpenScheduledTransfers: () -> Unit,
    onSplitBill: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(Ids.colors.background)) {
        BackTopBar("Transfer", onBack)
        Column(modifier = Modifier.padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical)) {
            TransferHubRow(
                icon = Icons.AutoMirrored.Outlined.Send,
                title = "Send money",
                subtitle = "Account number · contact",
                onClick = onSendMoney,
            )
            Spacer(Modifier.height(8.dp))
            TransferHubRow(
                icon = Icons.Outlined.AutoMode,
                title = "Auto-transfer",
                subtitle = if (autoTransferCount > 0) "$autoTransferCount active" else "Set up a recurring transfer",
                onClick = onOpenAutoTransfers,
            )
            Spacer(Modifier.height(8.dp))
            // Real Toss 예약송금 (scheduled/reserved one-time transfer) row (2026-08-04)
            // -- see ScheduledTransferDto's own doc comment on the backend. Genuinely
            // distinct from Auto-transfer above (recurring): explicitly one-time on a
            // single future date.
            TransferHubRow(
                icon = Icons.Outlined.Schedule,
                title = "Scheduled transfer",
                subtitle = "Send on a future date, one time",
                onClick = onOpenScheduledTransfers,
            )
            Spacer(Modifier.height(8.dp))
            // Real 더치페이 (Split bill) row (2026-07-24) -- completes real Toss's
            // own 송금 page grouping (송금하기/자동이체/더치페이), deliberately
            // deferred when this screen was first built. Split Bill itself already
            // lives inside a group's own Talk thread (see MenuScreen's identical
            // "Split a bill with friends" row) -- this just adds the same real
            // entry point here too, not a second implementation.
            TransferHubRow(
                icon = Icons.Outlined.Groups,
                title = "Split a bill",
                subtitle = "Settle up with friends in Talk",
                onClick = onSplitBill,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "Transfer history",
                color = Ids.colors.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier
                    .clickable(onClick = onOpenHistory)
                    .padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun TransferHubRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ids.layout.cardCornerRadius))
            .background(Ids.colors.surface)
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Ids.colors.brand, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(subtitle, color = Ids.colors.textSecondary, fontSize = 12.sp)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Ids.colors.textTertiary, modifier = Modifier.size(18.dp))
    }
}

/**
 * Real auto-transfer list + creation -- see AutoTransferService.kt's own doc comment on
 * the backend for the exact real ledger movement this schedules (P2pService.sendDirect,
 * unmodified, just triggered by a scheduler instead of a direct tap).
 */
@Composable
fun AutoTransferListScreen(onBack: () -> Unit, onChanged: () -> Unit) {
    var autoTransfers by remember { mutableStateOf<List<AutoTransferDto>?>(null) }
    var showNewForm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            autoTransfers = NetworkClient.apiService.getMyAutoTransfers().autoTransfers
            error = null
        } catch (e: retrofit2.HttpException) {
            error = superAppErrorMessage(e)
        } catch (e: Exception) {
            error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
    LaunchedEffect(Unit) { load() }

    if (showNewForm) {
        NewAutoTransferScreen(
            onBack = { showNewForm = false },
            onCreated = {
                showNewForm = false
                scope.launch { load() }
                onChanged()
            },
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(Ids.colors.background)) {
        BackTopBar("Auto-transfer", onBack)
        Column(modifier = Modifier.padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical)) {
            IdsButton(
                "+ New auto-transfer",
                onClick = { showNewForm = true },
                modifier = Modifier.fillMaxWidth(),
                variant = IdsButtonVariant.Filled,
                size = IdsButtonSize.Medium,
            )
        }
        error?.let {
            Text(it, color = Ids.colors.danger, fontSize = 13.sp, modifier = Modifier.padding(horizontal = Ids.layout.screenHorizontal))
        }
        val list = autoTransfers
        if (list == null) {
            // Loading -- no fabricated content shown meanwhile.
        } else if (list.isEmpty()) {
            Text(
                "No auto-transfers yet.",
                color = Ids.colors.textSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = Ids.layout.screenHorizontal, vertical = 24.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.id }) { autoTransfer ->
                    AutoTransferCard(
                        autoTransfer = autoTransfer,
                        onTogglePause = {
                            scope.launch {
                                try {
                                    if (autoTransfer.status == "ACTIVE") NetworkClient.apiService.pauseAutoTransfer(autoTransfer.id)
                                    else NetworkClient.apiService.resumeAutoTransfer(autoTransfer.id)
                                    load()
                                } catch (_: Exception) { /* best-effort, row just won't update this tap */ }
                            }
                        },
                        onCancel = {
                            scope.launch {
                                try {
                                    NetworkClient.apiService.cancelAutoTransfer(autoTransfer.id)
                                    load()
                                    onChanged()
                                } catch (_: Exception) { }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoTransferCard(autoTransfer: AutoTransferDto, onTogglePause: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Ids.layout.cardCornerRadius)).background(Ids.colors.surface).padding(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(autoTransfer.recipientName, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("%,.0f RWF".format(autoTransfer.amount), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        val cadence = when (autoTransfer.frequency) {
            AutoTransferFrequency.WEEKLY -> "Weekly"
            AutoTransferFrequency.MONTHLY -> "Monthly on day ${autoTransfer.dayOfMonth}"
        }
        Text(cadence, color = Ids.colors.textSecondary, fontSize = 12.sp)
        if (autoTransfer.status == "PAUSED") {
            Text("Paused", color = Ids.colors.textTertiary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        autoTransfer.lastFailureReason?.let {
            Text("Last attempt skipped: $it", color = Ids.colors.danger, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IdsButton(
                if (autoTransfer.status == "PAUSED") "Resume" else "Pause",
                onClick = onTogglePause,
                variant = IdsButtonVariant.Tinted,
                size = IdsButtonSize.Small,
            )
            IdsButton("Cancel", onClick = onCancel, variant = IdsButtonVariant.Tinted, size = IdsButtonSize.Small)
        }
    }
}

@Composable
fun NewAutoTransferScreen(onBack: () -> Unit, onCreated: () -> Unit) {
    var recipient by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf(AutoTransferFrequency.MONTHLY) }
    var dayOfMonth by remember { mutableStateOf("1") }
    var dayOfWeek by remember { mutableStateOf(1) }
    var description by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        val amountValue = amount.toDoubleOrNull()
        if (recipient.isBlank()) { error = "Enter a phone number or account number."; return }
        if (amountValue == null || amountValue <= 0) { error = "Enter a valid amount."; return }
        val dayOfMonthValue = if (frequency == AutoTransferFrequency.MONTHLY) dayOfMonth.toIntOrNull() else null
        if (frequency == AutoTransferFrequency.MONTHLY && (dayOfMonthValue == null || dayOfMonthValue !in 1..28)) {
            error = "Day of month must be between 1 and 28."; return
        }
        submitting = true
        error = null
        scope.launch {
            try {
                NetworkClient.apiService.createAutoTransfer(
                    java.util.UUID.randomUUID().toString(),
                    CreateAutoTransferRequest(
                        recipient = recipient.trim(),
                        amount = java.math.BigDecimal.valueOf(amountValue),
                        frequency = frequency,
                        dayOfWeek = if (frequency == AutoTransferFrequency.WEEKLY) dayOfWeek else null,
                        dayOfMonth = dayOfMonthValue,
                        description = description,
                    ),
                )
                onCreated()
            } catch (e: retrofit2.HttpException) {
                // Real fix (2026-08-11) -- was a local, hand-rolled regex over the raw
                // JSON body, fragile against escaped characters/nested quotes and a
                // straight duplicate of what superAppErrorMessage already does with a
                // real JSON parser (see ErrorMessages.kt's own doc comment on why this
                // shared function exists in the first place).
                error = superAppErrorMessage(e)
            } catch (e: Exception) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                submitting = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Ids.colors.background)) {
        BackTopBar("New auto-transfer", onBack)
        Column(
            modifier = Modifier.padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IdsTextField(value = recipient, onValueChange = { recipient = it }, label = "Phone number or account number", modifier = Modifier.fillMaxWidth())
            IdsTextField(value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = "Amount (RWF)", modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(AutoTransferFrequency.WEEKLY to "Weekly", AutoTransferFrequency.MONTHLY to "Monthly").forEach { (f, label) ->
                    val selected = frequency == f
                    Text(
                        label,
                        color = if (selected) androidx.compose.ui.graphics.Color.White else Ids.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) Ids.colors.brand else Ids.colors.surfaceSoft)
                            .clickable { frequency = f }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            if (frequency == AutoTransferFrequency.MONTHLY) {
                IdsTextField(value = dayOfMonth, onValueChange = { dayOfMonth = it.filter(Char::isDigit) }, label = "Day of month (1-28)", modifier = Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun").forEach { (d, label) ->
                        val selected = dayOfWeek == d
                        Text(
                            label,
                            color = if (selected) androidx.compose.ui.graphics.Color.White else Ids.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (selected) Ids.colors.brand else Ids.colors.surfaceSoft)
                                .clickable { dayOfWeek = d }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            IdsTextField(value = description, onValueChange = { description = it }, label = "What's this for? (optional)", modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 13.sp) }
            IdsButton(
                if (submitting) "Setting up…" else "Set up auto-transfer",
                onClick = ::submit,
                enabled = !submitting,
                modifier = Modifier.fillMaxWidth(),
                variant = IdsButtonVariant.Filled,
                size = IdsButtonSize.Large,
            )
        }
    }
}

/**
 * Real Toss 예약송금 (scheduled/reserved one-time transfer) -- see
 * ScheduledTransferDto's own doc comment on the backend. Genuinely distinct from
 * AutoTransferListScreen above (recurring): a single future-dated transfer, real
 * scheduler-executed at 9am on that date via P2pService.sendDirect (unmodified),
 * cancellable any time while still PENDING. Mirrors AutoTransferListScreen's exact
 * shape/conventions.
 */
@Composable
fun ScheduledTransferListScreen(onBack: () -> Unit) {
    var scheduledTransfers by remember { mutableStateOf<List<ScheduledTransferDto>?>(null) }
    var showNewForm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            scheduledTransfers = NetworkClient.apiService.getMyScheduledTransfers().scheduledTransfers
            error = null
        } catch (e: Exception) {
            error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
    LaunchedEffect(Unit) { load() }

    if (showNewForm) {
        NewScheduledTransferScreen(
            onBack = { showNewForm = false },
            onCreated = {
                showNewForm = false
                scope.launch { load() }
            },
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(Ids.colors.background)) {
        BackTopBar("Scheduled transfer", onBack)
        Column(modifier = Modifier.padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical)) {
            IdsButton(
                "+ New scheduled transfer",
                onClick = { showNewForm = true },
                modifier = Modifier.fillMaxWidth(),
                variant = IdsButtonVariant.Filled,
                size = IdsButtonSize.Medium,
            )
        }
        error?.let {
            Text(it, color = Ids.colors.danger, fontSize = 13.sp, modifier = Modifier.padding(horizontal = Ids.layout.screenHorizontal))
        }
        val list = scheduledTransfers
        if (list == null) {
            // Loading -- no fabricated content shown meanwhile.
        } else if (list.isEmpty()) {
            Text(
                "No scheduled transfers yet.",
                color = Ids.colors.textSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = Ids.layout.screenHorizontal, vertical = 24.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.id }) { transfer ->
                    ScheduledTransferCard(
                        transfer = transfer,
                        onCancel = {
                            scope.launch {
                                try {
                                    NetworkClient.apiService.cancelScheduledTransfer(transfer.id)
                                    load()
                                } catch (_: Exception) { }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduledTransferCard(transfer: ScheduledTransferDto, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Ids.layout.cardCornerRadius)).background(Ids.colors.surface).padding(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(transfer.recipientName, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("%,.0f RWF".format(transfer.amount), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Text("Sends ${transfer.scheduledDate}", color = Ids.colors.textSecondary, fontSize = 12.sp)
        Text(transfer.status, color = if (transfer.status == "FAILED") Ids.colors.danger else Ids.colors.textTertiary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        transfer.failureReason?.let {
            Text("Failed: $it", color = Ids.colors.danger, fontSize = 12.sp)
        }
        if (transfer.status == "PENDING") {
            Spacer(Modifier.height(10.dp))
            IdsButton("Cancel", onClick = onCancel, variant = IdsButtonVariant.Tinted, size = IdsButtonSize.Small)
        }
    }
}

@Composable
fun NewScheduledTransferScreen(onBack: () -> Unit, onCreated: () -> Unit) {
    var recipient by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var daysFromNow by remember { mutableStateOf("1") }
    var description by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        val amountValue = amount.toDoubleOrNull()
        val days = daysFromNow.toLongOrNull()
        if (recipient.isBlank()) { error = "Enter a phone number or account number."; return }
        if (amountValue == null || amountValue <= 0) { error = "Enter a valid amount."; return }
        if (days == null || days <= 0) { error = "Enter how many days from now to send this."; return }
        submitting = true
        error = null
        scope.launch {
            try {
                val scheduledDate = java.time.LocalDate.now().plusDays(days).toString()
                NetworkClient.apiService.createScheduledTransfer(
                    UUID.randomUUID().toString(),
                    CreateScheduledTransferRequest(
                        recipient = recipient.trim(),
                        amount = java.math.BigDecimal.valueOf(amountValue),
                        scheduledDate = scheduledDate,
                        description = description,
                    ),
                )
                onCreated()
            } catch (e: retrofit2.HttpException) {
                // Real fix (2026-08-11) -- see NewAutoTransferScreen's own identical
                // fix above for the full account.
                error = superAppErrorMessage(e)
            } catch (e: Exception) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                submitting = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Ids.colors.background)) {
        BackTopBar("New scheduled transfer", onBack)
        Column(
            modifier = Modifier.padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IdsTextField(value = recipient, onValueChange = { recipient = it }, label = "Phone number or account number", modifier = Modifier.fillMaxWidth())
            IdsTextField(value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = "Amount (RWF)", modifier = Modifier.fillMaxWidth())
            IdsTextField(value = daysFromNow, onValueChange = { daysFromNow = it.filter(Char::isDigit) }, label = "Send in how many days", modifier = Modifier.fillMaxWidth())
            IdsTextField(value = description, onValueChange = { description = it }, label = "What's this for? (optional)", modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 13.sp) }
            IdsButton(
                if (submitting) "Setting up…" else "Schedule transfer",
                onClick = ::submit,
                enabled = !submitting,
                modifier = Modifier.fillMaxWidth(),
                variant = IdsButtonVariant.Filled,
                size = IdsButtonSize.Large,
            )
        }
    }
}
