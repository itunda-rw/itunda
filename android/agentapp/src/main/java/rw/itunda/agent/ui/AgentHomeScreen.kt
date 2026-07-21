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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import java.math.BigDecimal

private enum class TransactionMode { CASH_IN, CASH_OUT, COUNT_TILL }

@Composable
fun AgentHomeScreen(onLogout: () -> Unit) {
    var till by remember { mutableStateOf<TillDto?>(null) }
    var activity by remember { mutableStateOf<List<ActivityDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    suspend fun refresh() {
        try { till = NetworkClient.agentApi.till().till; activity = NetworkClient.agentApi.activity().activity; error = null }
        catch (_: Exception) { error = "Could not refresh store data. Check the connection and try again." }
        finally { loading = false }
    }
    LaunchedEffect(Unit) { refresh() }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("Agent till", style = MaterialTheme.typography.headlineMedium); Text(till?.agentName ?: "Loading store…") }
                Button(onClick = onLogout) { Text("Sign out") }
            }
        }
        till?.let { snapshot -> item { TillSummary(snapshot) } }
        item { CashOperationCard(onCompleted = { loading = true; scope.launch { refresh() } }) }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        item { Text(if (loading) "Refreshing…" else "Recent activity", style = MaterialTheme.typography.titleMedium) }
        items(activity) { entry ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text(if (entry.type == "CASH_IN") "Cash in" else "Cash out", style = MaterialTheme.typography.titleSmall)
                Text("RWF ${entry.amount} · Receipt ${entry.receiptNumber}")
                Text(entry.createdAt, style = MaterialTheme.typography.bodySmall)
            } }
        }
    }
}

@Composable
private fun TillSummary(till: TillDto) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp)) {
        Text("Cash expected in till", style = MaterialTheme.typography.titleMedium)
        Text("RWF ${till.expectedCash}", style = MaterialTheme.typography.headlineMedium)
        Text("Today: RWF ${till.todayCashIn} in · RWF ${till.todayCashOut} out")
    }
}

@Composable
private fun CashOperationCard(onCompleted: () -> Unit) {
    var mode by remember { mutableStateOf(TransactionMode.CASH_IN) }
    var account by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var receipt by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var payoutChecked by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
        Text("Record store transaction", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { mode = TransactionMode.CASH_IN; payoutChecked = false }, enabled = !busy) { Text("Cash in") }
            Button(onClick = { mode = TransactionMode.CASH_OUT; payoutChecked = false }, enabled = !busy) { Text("Cash out") }
            Button(onClick = { mode = TransactionMode.COUNT_TILL; payoutChecked = false }, enabled = !busy) { Text("Count till") }
        }
        if (mode == TransactionMode.COUNT_TILL) {
            OutlinedTextField(amount, { amount = it }, label = { Text("Counted cash (RWF)") }, modifier = Modifier.fillMaxWidth())
        } else {
            OutlinedTextField(account, { account = it }, label = { Text("Customer account number") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(amount, { amount = it }, label = { Text("Amount (RWF)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(receipt, { receipt = it }, label = { Text("Store receipt number") }, modifier = Modifier.fillMaxWidth())
            if (mode == TransactionMode.CASH_OUT) {
                OutlinedTextField(code, { code = it.uppercase() }, label = { Text("Customer withdrawal code") }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Checkbox(checked = payoutChecked, onCheckedChange = { payoutChecked = it }, enabled = !busy)
                    Text("I checked the code and counted the cash. Do not hand over cash until Itunda confirms.", modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(8.dp))
        Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
            val numericAmount = amount.toBigDecimalOrNull()
            val wholeRwf = numericAmount?.stripTrailingZeros()?.scale()?.let { it <= 0 } == true
            val validWithdrawalCode = code.matches(Regex("[0-9A-F]{12}"))
            if (numericAmount == null || !wholeRwf || numericAmount <= BigDecimal.ZERO || (mode != TransactionMode.COUNT_TILL && (account.isBlank() || receipt.isBlank())) || (mode == TransactionMode.CASH_OUT && (!validWithdrawalCode || !payoutChecked))) {
                message = if (mode == TransactionMode.CASH_OUT && !validWithdrawalCode) "Enter the 12-character withdrawal code exactly as shown to the customer."
                else if (!wholeRwf) "RWF amounts must be whole numbers."
                else "Complete all required fields and confirm the cash check."
                return@Button
            }
            busy = true; message = null
            scope.launch {
                try {
                    when (mode) {
                        TransactionMode.CASH_IN -> NetworkClient.agentApi.cashIn(request = CashInRequest(account.trim(), numericAmount, receipt.trim()))
                        TransactionMode.CASH_OUT -> NetworkClient.agentApi.cashOut(request = CashOutRequest(account.trim(), numericAmount, receipt.trim(), code.trim()))
                        TransactionMode.COUNT_TILL -> NetworkClient.agentApi.submitTillCount(TillCountRequest(numericAmount))
                    }
                    account = ""; amount = ""; receipt = ""; code = ""; payoutChecked = false; onCompleted()
                } catch (_: Exception) { message = "Transaction was not completed. Check the details; do not give cash until confirmation succeeds." }
                finally { busy = false }
            }
        }) { Text(if (busy) "Submitting…" else when (mode) { TransactionMode.CASH_IN -> "Confirm cash in"; TransactionMode.CASH_OUT -> "Confirm cash out"; TransactionMode.COUNT_TILL -> "Submit count" }) }
    } }
}
