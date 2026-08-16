package rw.itunda.app.ui

import rw.itunda.core.designsystem.components.BackTopBar
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import rw.itunda.core.designsystem.components.IdsButton
import androidx.compose.material3.Card
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rw.itunda.core.network.AgentActivityItemDto
import rw.itunda.core.network.AgentCashInRequest
import rw.itunda.core.network.AgentCashOutRequest
import rw.itunda.core.network.AgentTillSnapshotDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SetAgentLocationRequest
import rw.itunda.core.network.SubmitTillCountRequest
import rw.itunda.core.network.apiErrorCode
import rw.itunda.core.network.superAppErrorMessage
import java.util.UUID
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.rememberRealLocationRequester

// Real Itunda cash-agent operator console -- see AgentOperatorController.kt's own doc
// comment: "Store-facing API: the operator's JWT determines the agent; callers never
// supply an agent id." Distinct from AgentCashScreen.kt's own customer-facing
// withdrawal-code creation -- this is the STAFF side, real till balance + real
// cash-in/cash-out + real till reconciliation. bank-mfe already has this
// (AgentOperatorView); this is the first native client (Android/iOS).
@Composable
fun AgentOperatorScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var till by remember { mutableStateOf<AgentTillSnapshotDto?>(null) }
    var activity by remember { mutableStateOf<List<AgentActivityItemDto>>(emptyList()) }
    var notOperator by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Real gap found live (uncalled-endpoint sweep, 2026-08-16) -- see backend
    // AgentService.setLocationForOperator's own doc comment. The real customer-facing
    // "nearby agents" feature depends entirely on this; this is the first client
    // anywhere that can actually report it.
    var reportingLocation by remember { mutableStateOf(false) }
    val requestLocationAndReport = rememberRealLocationRequester(
        onLocating = { reportingLocation = it },
        onSuccess = { lat, lng ->
            scope.launch {
                try {
                    NetworkClient.apiService.setAgentLocation(SetAgentLocationRequest(lat, lng))
                    message = "Your location has been updated."
                } catch (e: retrofit2.HttpException) {
                    error = superAppErrorMessage(e)
                } catch (e: Exception) {
                    error = "Could not update your location."
                }
            }
        },
        onError = { error = it },
    )

    fun load() {
        error = null
        scope.launch {
            try {
                till = NetworkClient.apiService.getAgentTill().till
                notOperator = false
            } catch (e: retrofit2.HttpException) {
                if (apiErrorCode(e) == "AGENT_OPERATOR_NOT_AUTHORIZED") {
                    notOperator = true
                } else {
                    error = superAppErrorMessage(e)
                }
            } catch (e: Exception) {
                error = "Could not load your till."
            }
            try {
                activity = NetworkClient.apiService.getAgentActivity().activity
            } catch (e: Exception) {
                activity = emptyList()
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(20.dp)) {
            BackTopBar(title = "Agent till", onBack = onBack)
        }
        if (notOperator) {
            Text(
                "You are not assigned as an Itunda agent till operator. Ask an Itunda staff admin to assign your account to a store.",
                modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
            val current = till
            if (current == null) {
                item { Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                item { TillSummaryCard(till = current) }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Store location", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Real customers use \"nearby agents\" to find your store -- keep your location current.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            IdsButton(
                                text = if (reportingLocation) "Getting your location…" else "Report my location",
                                onClick = { message = null; error = null; requestLocationAndReport() },
                                enabled = !reportingLocation,
                            )
                        }
                    }
                }
                item {
                    CashInCard(onSubmitted = { result -> message = "Cash in accepted — new customer balance ${"%,.0f".format(result)} RWF"; load() }, onError = { error = it })
                }
                item {
                    CashOutCard(onSubmitted = { result -> message = "Cash out paid — new customer balance ${"%,.0f".format(result)} RWF"; load() }, onError = { error = it })
                }
                item {
                    TillCountCard(onSubmitted = { variance, status -> message = "Till count submitted — variance ${"%,.0f".format(variance)} RWF ($status)"; load() }, onError = { error = it })
                }
                item { Text("Recent activity", style = MaterialTheme.typography.titleMedium) }
                if (activity.isEmpty()) {
                    item { EmptyState("No cash movements yet today — your cash-in/cash-out activity will show up here.") }
                }
                items(activity, key = { it.id }) { a ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (a.type == "CASH_IN") "↓ Cash in · ${a.receiptNumber}" else "↑ Cash out · ${a.receiptNumber}")
                            Text("${"%,.0f".format(a.amount)} RWF")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TillSummaryCard(till: AgentTillSnapshotDto) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(till.agentName, style = MaterialTheme.typography.labelMedium)
            Text("${"%,.0f".format(till.expectedCash)} RWF expected in till", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Today: ${"%,.0f".format(till.todayCashIn)} RWF in · ${"%,.0f".format(till.todayCashOut)} RWF out",
                style = MaterialTheme.typography.bodySmall,
            )
            till.reconciliation?.let {
                Text(
                    "Last count: ${"%,.0f".format(it.countedCash)} RWF (${it.status}, variance ${"%,.0f".format(it.variance)})",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun CashInCard(onSubmitted: (java.math.BigDecimal) -> Unit, onError: (String) -> Unit) {
    var account by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var receipt by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Accept cash-in", style = MaterialTheme.typography.titleMedium)
            IdsTextField(value = account, onValueChange = { account = it }, label = "Customer account number", modifier = Modifier.fillMaxWidth())
            IdsTextField(value = amount, onValueChange = { amount = it }, label = "Amount (RWF)", isAmount = true, modifier = Modifier.fillMaxWidth())
            IdsTextField(value = receipt, onValueChange = { receipt = it }, label = "Receipt number", modifier = Modifier.fillMaxWidth())
            IdsButton(
                text = if (busy) "Working…" else "Accept cash-in",
                enabled = !busy,
                onClick = {
                    val value = amount.toBigDecimalOrNull()
                    if (account.isBlank() || receipt.isBlank() || value == null || value <= java.math.BigDecimal.ZERO) {
                        onError("Enter a real account number, receipt number, and a positive amount.")
                        return@IdsButton
                    }
                    busy = true
                    scope.launch {
                        try {
                            val result = NetworkClient.apiService.agentCashIn(UUID.randomUUID().toString(), AgentCashInRequest(account.trim(), value, receipt.trim()))
                            account = ""; amount = ""; receipt = ""
                            onSubmitted(result.newBalance)
                        } catch (e: retrofit2.HttpException) {
                            // Real fix (2026-08-11) -- a real backend decline here
                            // (duplicate receipt number, float exhausted, etc.) is
                            // exactly the kind of specific reason an agent operator
                            // needs to see to act correctly, not a generic dead end.
                            onError(superAppErrorMessage(e))
                        } catch (e: Exception) {
                            onError("Could not accept this cash-in.")
                        } finally {
                            busy = false
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun CashOutCard(onSubmitted: (java.math.BigDecimal) -> Unit, onError: (String) -> Unit) {
    var account by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var receipt by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pay cash-out", style = MaterialTheme.typography.titleMedium)
            IdsTextField(value = account, onValueChange = { account = it }, label = "Customer account number", modifier = Modifier.fillMaxWidth())
            IdsTextField(value = amount, onValueChange = { amount = it }, label = "Amount (RWF)", isAmount = true, modifier = Modifier.fillMaxWidth())
            IdsTextField(value = receipt, onValueChange = { receipt = it }, label = "Receipt number", modifier = Modifier.fillMaxWidth())
            IdsTextField(value = code, onValueChange = { code = it }, label = "Customer's withdrawal code", modifier = Modifier.fillMaxWidth())
            IdsButton(
                text = if (busy) "Working…" else "Pay cash-out",
                enabled = !busy,
                onClick = {
                    val value = amount.toBigDecimalOrNull()
                    if (account.isBlank() || receipt.isBlank() || code.isBlank() || value == null || value <= java.math.BigDecimal.ZERO) {
                        onError("Enter a real account number, receipt number, withdrawal code, and a positive amount.")
                        return@IdsButton
                    }
                    busy = true
                    scope.launch {
                        try {
                            val result = NetworkClient.apiService.agentCashOut(UUID.randomUUID().toString(), AgentCashOutRequest(account.trim(), value, receipt.trim(), code.trim()))
                            account = ""; amount = ""; receipt = ""; code = ""
                            onSubmitted(result.newBalance)
                        } catch (e: retrofit2.HttpException) {
                            onError(superAppErrorMessage(e))
                        } catch (e: Exception) {
                            onError("Could not pay this cash-out. Check the withdrawal code.")
                        } finally {
                            busy = false
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun TillCountCard(onSubmitted: (java.math.BigDecimal, String) -> Unit, onError: (String) -> Unit) {
    var counted by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Submit today's till count", style = MaterialTheme.typography.titleMedium)
            IdsTextField(value = counted, onValueChange = { counted = it }, label = "Counted cash (RWF)", modifier = Modifier.fillMaxWidth())
            IdsButton(
                text = if (busy) "Working…" else "Submit count",
                enabled = !busy,
                onClick = {
                    val value = counted.toBigDecimalOrNull()
                    if (value == null || value < java.math.BigDecimal.ZERO) {
                        onError("Enter a real counted-cash amount.")
                        return@IdsButton
                    }
                    busy = true
                    scope.launch {
                        try {
                            val result = NetworkClient.apiService.submitAgentTillCount(SubmitTillCountRequest(value)).reconciliation
                            counted = ""
                            onSubmitted(result.variance, result.status)
                        } catch (e: retrofit2.HttpException) {
                            onError(superAppErrorMessage(e))
                        } catch (e: Exception) {
                            onError("Could not submit this till count.")
                        } finally {
                            busy = false
                        }
                    }
                },
            )
        }
    }
}
