package rw.itunda.agent.ui

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import rw.itunda.core.designsystem.components.IdsTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rw.itunda.agent.network.ActivityDto
import rw.itunda.agent.network.CashInRequest
import rw.itunda.agent.network.CashOutRequest
import rw.itunda.agent.network.NetworkClient
import rw.itunda.agent.network.TillCountRequest
import rw.itunda.agent.network.TillDto
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonVariant
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsTypography
import java.math.BigDecimal

private enum class TransactionMode { CASH_IN, CASH_OUT, COUNT_TILL }

@Composable
fun AgentHomeScreen(onLogout: () -> Unit) {
    var action by remember { mutableStateOf<TransactionMode?>(null) }
    var till by remember { mutableStateOf<TillDto?>(null) }
    var activity by remember { mutableStateOf<List<ActivityDto>>(emptyList()) }
    var showingActivity by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    suspend fun refresh() {
        try { till = NetworkClient.agentApi.till().till; activity = NetworkClient.agentApi.activity().activity; error = null }
        catch (_: Exception) { error = "Could not refresh store data. Check the connection and try again." }
        finally { loading = false }
    }
    action?.let { selected ->
        CashOperationScreen(mode = selected, onBack = { action = null }, onCompleted = { action = null; scope.launch { refresh() } })
        return
    }
    LaunchedEffect(Unit) { refresh() }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text(till?.agentName ?: "Your agent store", style = IdsTypography.Title1, color = Ids.colors.textPrimary); Text("Today’s cash desk", style = IdsTypography.Body2, color = Ids.colors.textSecondary) }
                TextButton(onClick = onLogout) { Text("Sign out") }
            }
        }
        till?.let { snapshot -> item { TillSummary(snapshot) } }
        item { ActionPicker(onAction = { action = it }) }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        item {
            TextButton(onClick = { showingActivity = !showingActivity }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showingActivity) "Hide today’s activity" else "View today’s activity")
            }
        }
        if (showingActivity) {
            item { Text(if (loading) "Refreshing…" else "Today’s activity", style = MaterialTheme.typography.titleMedium) }
            if (!loading && activity.isEmpty()) item { Text("No store transactions recorded yet.", style = MaterialTheme.typography.bodyMedium) }
            items(activity.take(5)) { entry -> ActivityCard(entry) }
        }
    }
}

@Composable
private fun ActivityCard(entry: ActivityDto) = Card(Modifier.fillMaxWidth()) {
    val cashIn = entry.type == "CASH_IN"
    Column(Modifier.padding(12.dp)) {
        Text(if (cashIn) "Cash in completed" else "Cash out completed", style = MaterialTheme.typography.titleSmall)
        Text((if (cashIn) "+" else "−") + " RWF ${entry.amount} · Receipt ${entry.receiptNumber}")
        Text(entry.createdAt, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TillSummary(till: TillDto) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp)) {
        Text("Cash expected in till", style = IdsTypography.Body2, color = Ids.colors.textSecondary)
        Text("RWF ${till.expectedCash}", style = IdsTypography.LargeAmount, color = Ids.colors.textPrimary)
        Text("Today: RWF ${till.todayCashIn} in · RWF ${till.todayCashOut} out", style = IdsTypography.Body2, color = Ids.colors.textSecondary)
    }
}

@Composable
private fun ActionPicker(onAction: (TransactionMode) -> Unit) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp)) {
        Text("What do you need to do?", style = IdsTypography.Title2, color = Ids.colors.textPrimary)
        Spacer(Modifier.height(8.dp))
        IdsButton(text = "Receive cash from customer", onClick = { onAction(TransactionMode.CASH_IN) })
        Spacer(Modifier.height(8.dp))
        IdsButton(text = "Pay cash to customer", onClick = { onAction(TransactionMode.CASH_OUT) }, variant = IdsButtonVariant.Tinted)
        TextButton(onClick = { onAction(TransactionMode.COUNT_TILL) }, modifier = Modifier.fillMaxWidth()) { Text("End-of-day cash count") }
    }
}

@Composable
private fun CashOperationScreen(mode: TransactionMode, onBack: () -> Unit, onCompleted: () -> Unit) {
    var account by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var receipt by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var payoutChecked by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(when (mode) { TransactionMode.CASH_IN -> "Receive cash"; TransactionMode.CASH_OUT -> "Pay cash"; TransactionMode.COUNT_TILL -> "Cash count" }, style = IdsTypography.Title1, color = Ids.colors.textPrimary)
            TextButton(onClick = onBack, enabled = !busy) { Text("Back") }
        }
        if (mode == TransactionMode.COUNT_TILL) {
            IdsTextField(value = amount, onValueChange = { amount = it }, label = "Counted cash (RWF)", modifier = Modifier.fillMaxWidth())
        } else {
            IdsTextField(value = account, onValueChange = { account = it }, label = "Customer account number", modifier = Modifier.fillMaxWidth())
            IdsTextField(value = amount, onValueChange = { amount = it }, label = "Amount (RWF)", modifier = Modifier.fillMaxWidth())
            IdsTextField(value = receipt, onValueChange = { receipt = it }, label = "Store receipt number", modifier = Modifier.fillMaxWidth())
            if (mode == TransactionMode.CASH_OUT) {
                IdsTextField(value = code, onValueChange = { code = it.uppercase() }, label = "Customer withdrawal code", modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Checkbox(checked = payoutChecked, onCheckedChange = { payoutChecked = it }, enabled = !busy)
                    Text("I checked the code and counted the cash. Do not hand over cash until Itunda confirms.", modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        successMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(8.dp))
        IdsButton(enabled = !busy, onClick = {
            val numericAmount = amount.toBigDecimalOrNull()
            val wholeRwf = numericAmount?.stripTrailingZeros()?.scale()?.let { it <= 0 } == true
            val validWithdrawalCode = code.matches(Regex("[0-9A-F]{12}"))
            if (numericAmount == null || !wholeRwf || numericAmount <= BigDecimal.ZERO || (mode != TransactionMode.COUNT_TILL && (account.isBlank() || receipt.isBlank())) || (mode == TransactionMode.CASH_OUT && (!validWithdrawalCode || !payoutChecked))) {
                message = if (mode == TransactionMode.CASH_OUT && !validWithdrawalCode) "Enter the 12-character withdrawal code exactly as shown to the customer."
                else if (!wholeRwf) "RWF amounts must be whole numbers."
                else "Complete all required fields and confirm the cash check."
                return@IdsButton
            }
            busy = true; message = null; successMessage = null
            scope.launch {
                try {
                    when (mode) {
                        TransactionMode.CASH_IN -> NetworkClient.agentApi.cashIn(request = CashInRequest(account.trim(), numericAmount, receipt.trim()))
                        TransactionMode.CASH_OUT -> NetworkClient.agentApi.cashOut(request = CashOutRequest(account.trim(), numericAmount, receipt.trim(), code.trim()))
                        TransactionMode.COUNT_TILL -> NetworkClient.agentApi.submitTillCount(TillCountRequest(numericAmount))
                    }
                    successMessage = when (mode) {
                        TransactionMode.CASH_IN -> "Cash in confirmed. The customer balance has been updated."
                        TransactionMode.CASH_OUT -> "Cash out confirmed. You can now hand the cash to the customer."
                        TransactionMode.COUNT_TILL -> "Till count submitted for supervisor review."
                    }
                    account = ""; amount = ""; receipt = ""; code = ""; payoutChecked = false; onCompleted()
                } catch (_: Exception) { message = "Transaction was not completed. Check the details; do not give cash until confirmation succeeds." }
                finally { busy = false }
            }
        }, text = if (busy) "Submitting…" else when (mode) { TransactionMode.CASH_IN -> "Confirm cash received"; TransactionMode.CASH_OUT -> "Confirm cash paid"; TransactionMode.COUNT_TILL -> "Submit count" })
    }
}
