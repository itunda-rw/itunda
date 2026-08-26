package rw.itunda.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.FixedBottomCta
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.CreateScheduledTransferRequest
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.superAppErrorMessage
import java.util.UUID

// Real fix (2026-08-26): split out of TransferHubScreen.kt once that file grew past
// its file-size-lint baseline. Also picks up the same real full-app-audit finding
// its sibling NewAutoTransferScreen already got fixed the same session -- the submit
// button used to sit inline at the end of a plain, non-scrolling Column instead of a
// real FixedBottomCta.
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
                // fix for the full account.
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
        FixedBottomCta(
            content = {
                IdsTextField(value = recipient, onValueChange = { recipient = it }, label = "Phone number or account number", modifier = Modifier.fillMaxWidth())
                IdsTextField(value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = "Amount (RWF)", modifier = Modifier.fillMaxWidth())
                IdsTextField(value = daysFromNow, onValueChange = { daysFromNow = it.filter(Char::isDigit) }, label = "Send in how many days", modifier = Modifier.fillMaxWidth())
                IdsTextField(value = description, onValueChange = { description = it }, label = "What's this for? (optional)", modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = Ids.colors.danger, fontSize = 13.sp) }
            },
            cta = {
                IdsButton(
                    if (submitting) "Setting up…" else "Schedule transfer",
                    onClick = ::submit,
                    enabled = !submitting,
                )
            },
        )
    }
}
