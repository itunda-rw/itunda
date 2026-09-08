package rw.itunda.feature.pay.impl

import androidx.compose.foundation.background
import java.time.Duration
import java.time.Instant
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
import rw.itunda.core.network.DelayedTransferDto
import rw.itunda.core.network.DelayedTransferStatus
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SendDelayedTransferRequest
import rw.itunda.core.network.superAppErrorMessage
import java.math.BigDecimal
import java.util.UUID

/**
 * Real Korean 지연이체서비스 (Delayed Transfer Service) -- see backend
 * P2pDelayedTransfer.kt's own doc comment for the full sourced account (a real,
 * government-documented anti-voice-phishing safeguard every major Korean bank offers).
 * Already shipped on iOS (DelayedTransferListScreen.swift) and web (DelayedTransfersCard
 * in PayTransferCards.tsx) -- this is the first Android client, mirroring
 * AutoTransferListScreen.kt's own list + "+ New" push-to-create-form structure.
 */
@Composable
fun DelayedTransferListScreen(onBack: () -> Unit) {
    var transfers by remember { mutableStateOf<List<DelayedTransferDto>?>(null) }
    var showNewForm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            transfers = NetworkClient.apiService.getMyDelayedTransfers().transfers
            error = null
        } catch (e: retrofit2.HttpException) {
            error = superAppErrorMessage(e)
        } catch (e: Exception) {
            error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
    LaunchedEffect(Unit) { load() }

    if (showNewForm) {
        NewDelayedTransferScreen(
            onBack = { showNewForm = false },
            onCreated = {
                showNewForm = false
                scope.launch { load() }
            },
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(Ids.colors.background)) {
        BackTopBar("Delayed transfers", onBack)
        Column(modifier = Modifier.padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical)) {
            Text(
                "Send safely: the money is held for a few hours so you can still cancel a transfer sent under pressure or to the wrong person.",
                color = Ids.colors.textSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(12.dp))
            IdsButton(
                "+ Send safely",
                onClick = { showNewForm = true },
                modifier = Modifier.fillMaxWidth(),
                variant = IdsButtonVariant.Filled,
                size = IdsButtonSize.Medium,
            )
        }
        error?.let {
            Text(it, color = Ids.colors.danger, fontSize = 13.sp, modifier = Modifier.padding(horizontal = Ids.layout.screenHorizontal))
        }
        val list = transfers
        if (list == null) {
            // Loading -- no fabricated content shown meanwhile.
        } else if (list.isEmpty()) {
            Text(
                "No delayed transfers yet.",
                color = Ids.colors.textSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = Ids.layout.screenHorizontal, vertical = 24.dp),
            )
        } else {
            val pending = list.filter { it.status == DelayedTransferStatus.PENDING }
            val past = list.filter { it.status != DelayedTransferStatus.PENDING }.take(3)
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(pending + past, key = { it.id }) { transfer ->
                    DelayedTransferCard(
                        transfer = transfer,
                        busy = busyId == transfer.id,
                        onCancel = {
                            busyId = transfer.id
                            scope.launch {
                                try {
                                    NetworkClient.apiService.cancelDelayedTransfer(transfer.id)
                                    load()
                                } catch (e: retrofit2.HttpException) {
                                    error = superAppErrorMessage(e)
                                } catch (e: Exception) {
                                    error = "Couldn't reach itunda. Check your connection and try again."
                                } finally {
                                    busyId = null
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
private fun DelayedTransferCard(transfer: DelayedTransferDto, busy: Boolean, onCancel: () -> Unit) {
    val releaseInstant = runCatching { Instant.parse(transfer.releaseAt) }.getOrNull()
    val statusLabel = when (transfer.status) {
        DelayedTransferStatus.PENDING -> releaseInstant?.let { "Releases in ${formatReleaseCountdown(it)}" } ?: "Pending"
        DelayedTransferStatus.COMPLETED -> "Completed"
        DelayedTransferStatus.CANCELLED -> "Cancelled"
    }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Ids.layout.cardCornerRadius)).background(Ids.colors.surface).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(String.format(java.util.Locale.US, "%,.0f RWF", transfer.amount), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(statusLabel, color = Ids.colors.textSecondary, fontSize = 12.sp)
        }
        if (transfer.status == DelayedTransferStatus.PENDING) {
            IdsButton(if (busy) "…" else "Cancel", onClick = onCancel, variant = IdsButtonVariant.Tinted, size = IdsButtonSize.Small)
        }
    }
}

// Same real hold-window countdown as iOS's own formatReleaseCountdown
// (DelayedTransferListScreen.swift) -- "Xh Ym" while over an hour out, "Ym" once under.
private fun formatReleaseCountdown(releaseAt: Instant): String {
    val seconds = Duration.between(Instant.now(), releaseAt).seconds
    if (seconds <= 0) return "0m"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

@Composable
private fun NewDelayedTransferScreen(onBack: () -> Unit, onCreated: () -> Unit) {
    var recipient by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        val amountValue = amount.toDoubleOrNull()
        if (recipient.isBlank()) { error = "Enter a phone number or account number."; return }
        if (amountValue == null || amountValue <= 0) { error = "Enter a valid amount."; return }
        submitting = true
        error = null
        scope.launch {
            try {
                NetworkClient.apiService.sendDelayed(
                    UUID.randomUUID().toString(),
                    SendDelayedTransferRequest(recipient.trim(), BigDecimal.valueOf(amountValue), description),
                )
                onCreated()
            } catch (e: retrofit2.HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: Exception) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                submitting = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Ids.colors.background)) {
        BackTopBar("Send safely", onBack)
        FixedBottomCta(
            content = {
                Text(
                    "This transfer will be held for a few hours before it reaches the recipient, so you can still cancel it.",
                    color = Ids.colors.textSecondary,
                    fontSize = 12.sp,
                )
                IdsTextField(value = recipient, onValueChange = { recipient = it }, label = "Phone number or account number", modifier = Modifier.fillMaxWidth())
                IdsTextField(value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = "Amount (RWF)", modifier = Modifier.fillMaxWidth())
                IdsTextField(value = description, onValueChange = { description = it }, label = "What's this for? (optional)", modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = Ids.colors.danger, fontSize = 13.sp) }
            },
            cta = {
                IdsButton(
                    if (submitting) "Sending…" else "Send safely",
                    onClick = ::submit,
                    enabled = !submitting,
                )
            },
        )
    }
}
