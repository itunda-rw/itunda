package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import rw.itunda.app.network.AgentWithdrawalAuthorizationDto
import rw.itunda.app.network.CancelAgentWithdrawalAuthorizationRequest
import rw.itunda.app.network.CreateAgentWithdrawalAuthorizationRequest
import rw.itunda.app.network.NetworkClient
import java.math.BigDecimal
import java.util.UUID

/** Customer-owned, short-lived cash-out consent. No balance moves until the agent validates this code and pays cash. */
@Composable
fun AgentCashScreen(onBack: () -> Unit, onFindNearbyAgent: () -> Unit) {
    var amountText by remember { mutableStateOf("") }
    var pendingCreationKey by remember { mutableStateOf<String?>(null) }
    var authorizations by remember { mutableStateOf<List<AgentWithdrawalAuthorizationDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    suspend fun refresh() {
        try { authorizations = NetworkClient.apiService.getAgentWithdrawalAuthorizations().authorizations; error = null }
        catch (_: Exception) { error = "Could not load your withdrawal codes." }
    }
    LaunchedEffect(Unit) { refresh() }
    BackHandler(onBack = onBack)
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Cash out at an Itunda agent", style = MaterialTheme.typography.headlineSmall) }
        item { Text("Create a one-time code, then show it to the agent only when they are ready to hand over cash. Codes expire in 10 minutes.") }
        item {
            Button(onClick = onFindNearbyAgent, modifier = Modifier.fillMaxWidth()) {
                Text("Find a nearby Itunda agent")
            }
        }
        item {
            OutlinedTextField(
                amountText,
                {
                    amountText = it
                    // Editing the amount is a new customer intent. Retrying unchanged
                    // input after a timeout deliberately keeps the original key.
                    pendingCreationKey = null
                },
                label = { Text("Amount (RWF)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                val amount = amountText.toBigDecimalOrNull()
                if (amount == null || amount <= BigDecimal.ZERO) { error = "Enter a valid positive amount."; return@Button }
                busy = true; error = null
                scope.launch {
                    try {
                        val key = pendingCreationKey ?: UUID.randomUUID().toString().also { pendingCreationKey = it }
                        NetworkClient.apiService.createAgentWithdrawalAuthorization(
                            key, CreateAgentWithdrawalAuthorizationRequest(amount),
                        )
                        amountText = ""
                        pendingCreationKey = null
                        refresh()
                    }
                    catch (_: Exception) { error = "Could not create a code. You may already have three active codes." }
                    finally { busy = false }
                }
            }) { Text(if (busy) "Creating…" else "Create withdrawal code") }
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        item { Text("Your codes", style = MaterialTheme.typography.titleMedium) }
        items(authorizations) { authorization -> AuthorizationCard(authorization, busy) {
            busy = true
            scope.launch {
                try { NetworkClient.apiService.cancelAgentWithdrawalAuthorization(CancelAgentWithdrawalAuthorizationRequest(authorization.code)); refresh() }
                catch (_: Exception) { error = "This code could not be cancelled." }
                finally { busy = false }
            }
        } }
    }
}

@Composable
private fun AuthorizationCard(authorization: AgentWithdrawalAuthorizationDto, busy: Boolean, onCancel: () -> Unit) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp)) {
        Text("RWF ${authorization.amount}", style = MaterialTheme.typography.titleLarge)
        Text(if (authorization.status == "ACTIVE") "Show this code to the agent: ${authorization.code}" else authorization.status)
        Text("Expires: ${authorization.expiresAt}", style = MaterialTheme.typography.bodySmall)
        if (authorization.status == "ACTIVE") Button(enabled = !busy, onClick = onCancel, modifier = Modifier.padding(top = 8.dp)) { Text("Cancel code") }
    }
}
