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
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rw.itunda.core.network.AgentWithdrawalAuthorizationDto
import rw.itunda.core.network.CancelAgentWithdrawalAuthorizationRequest
import rw.itunda.core.network.CreateAgentWithdrawalAuthorizationRequest
import rw.itunda.core.network.NetworkClient
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
    var copiedCode by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
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
            IdsButton(text = "Find a nearby Itunda agent", onClick = onFindNearbyAgent)
        }
        item {
            IdsTextField(value = amountText, onValueChange = {
                    amountText = it
                    // Editing the amount is a new customer intent. Retrying unchanged
                    // input after a timeout deliberately keeps the original key.
                    pendingCreationKey = null
                }, label = "Amount (RWF)", isAmount = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            IdsButton(text = if (busy) "Creating…" else "Create withdrawal code", enabled = !busy, onClick = {
                val amount = amountText.toBigDecimalOrNull()
                if (amount == null || amount <= BigDecimal.ZERO) { error = "Enter a valid positive amount."; return@IdsButton }
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
            })
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        item {
            Text(
                "Your codes (${authorizations.count { it.status == "ACTIVE" }} active)",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        item {
            Text(
                "For your security, give a code only to an Itunda agent at the counter. It can be used once for the exact amount shown.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        items(authorizations, key = { it.id }) { authorization -> AuthorizationCard(authorization, busy, copiedCode == authorization.code, {
            clipboard.setText(AnnotatedString(authorization.code))
            copiedCode = authorization.code
        }) {
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
private fun AuthorizationCard(
    authorization: AgentWithdrawalAuthorizationDto,
    busy: Boolean,
    copied: Boolean,
    onCopy: () -> Unit,
    onCancel: () -> Unit,
) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp)) {
        Text("RWF ${authorization.amount}", style = MaterialTheme.typography.titleLarge)
        if (authorization.status == "ACTIVE") {
            Text("Withdrawal code", style = MaterialTheme.typography.labelMedium)
            Text(authorization.code, style = MaterialTheme.typography.headlineSmall)
            Text("Show it only when the agent is ready to give you cash.", style = MaterialTheme.typography.bodySmall)
            IdsButton(
                text = if (copied) "Code copied" else "Copy code",
                enabled = !busy,
                onClick = onCopy,
                size = IdsButtonSize.Medium,
            )
        } else {
            Text(authorization.status)
        }
        Text("Expires: ${authorization.expiresAt}", style = MaterialTheme.typography.bodySmall)
        if (authorization.status == "ACTIVE") {
            IdsButton(
                text = "Cancel code",
                enabled = !busy,
                onClick = onCancel,
                variant = IdsButtonVariant.Tinted,
                size = IdsButtonSize.Medium,
            )
        }
    }
}
