package rw.itunda.feature.pay.impl

import androidx.compose.foundation.background
import java.util.Locale
import rw.itunda.core.designsystem.components.pressScaleClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.FixedBottomCta
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.AutoTransferDto
import rw.itunda.core.network.AutoTransferFrequency
import rw.itunda.core.network.CreateAutoTransferRequest
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.superAppErrorMessage
import java.math.BigDecimal
import java.util.UUID

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
                                } catch (e: retrofit2.HttpException) {
                                    error = superAppErrorMessage(e)
                                } catch (e: Exception) {
                                    error = "Couldn't reach itunda. Check your connection and try again."
                                }
                            }
                        },
                        onCancel = {
                            scope.launch {
                                try {
                                    NetworkClient.apiService.cancelAutoTransfer(autoTransfer.id)
                                    load()
                                    onChanged()
                                } catch (e: retrofit2.HttpException) {
                                    error = superAppErrorMessage(e)
                                } catch (e: Exception) {
                                    error = "Couldn't reach itunda. Check your connection and try again."
                                }
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
            Text(String.format(Locale.US, "%,.0f RWF", autoTransfer.amount), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
                    UUID.randomUUID().toString(),
                    CreateAutoTransferRequest(
                        recipient = recipient.trim(),
                        amount = BigDecimal.valueOf(amountValue),
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
        // Real fix (full-app audit, docs/UI_UX_GUIDELINES.md rule 1) -- matches the
        // identical iOS fix (TransferHubScreen.swift): the submit button used to sit
        // inline at the end of a plain, non-scrolling Column -- on a small device
        // this could clip content instead of the button ever moving, the same real
        // risk Grow31/WeeklySavings had before FixedBottomCta. No intro screen (this
        // form has no real non-obvious mechanics rule 13 would require explaining).
        FixedBottomCta(
            content = {
                IdsTextField(value = recipient, onValueChange = { recipient = it }, label = "Phone number or account number", modifier = Modifier.fillMaxWidth())
                IdsTextField(value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = "Amount (RWF)", modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(AutoTransferFrequency.WEEKLY to "Weekly", AutoTransferFrequency.MONTHLY to "Monthly").forEach { (f, label) ->
                        val selected = frequency == f
                        Text(
                            label,
                            color = if (selected) Color.White else Ids.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (selected) Ids.colors.brand else Ids.colors.surfaceSoft)
                                .pressScaleClickable { frequency = f }
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
                                color = if (selected) Color.White else Ids.colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (selected) Ids.colors.brand else Ids.colors.surfaceSoft)
                                    .pressScaleClickable { dayOfWeek = d }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                IdsTextField(value = description, onValueChange = { description = it }, label = "What's this for? (optional)", modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = Ids.colors.danger, fontSize = 13.sp) }
            },
            cta = {
                IdsButton(
                    if (submitting) "Setting up…" else "Set up auto-transfer",
                    onClick = ::submit,
                    enabled = !submitting,
                )
            },
        )
    }
}
