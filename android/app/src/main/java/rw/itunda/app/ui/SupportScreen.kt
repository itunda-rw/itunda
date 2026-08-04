package rw.itunda.app.ui

import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.SkeletonBlock
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rw.itunda.core.network.CreateSupportTicketRequest
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SupportTicketDto
import rw.itunda.core.network.TransactionDto

// Real customer support tickets (2026-07-22) -- found fully built on the backend
// (rw.itunda.support) with zero client UI anywhere; this app's "Support" section was
// five static rows (FAQ/Live chat/Call support/...) with no backend behind any of
// them. A ticket is always tied to a specific transaction (see SupportTicket.kt's own
// doc comment for why), so this screen has the user pick one from their real
// transaction history rather than filing a free-floating complaint.
private val CATEGORIES = listOf("GENERAL", "PAYMENT_DISPUTE", "ACCOUNT_TAKEOVER")

@Composable
fun SupportScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var tickets by remember { mutableStateOf<List<SupportTicketDto>?>(null) }
    var transactions by remember { mutableStateOf<List<TransactionDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showNewTicketForm by remember { mutableStateOf(false) }
    var selectedTransactionId by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf(CATEGORIES.first()) }
    var descriptionText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        try {
            tickets = NetworkClient.apiService.getSupportTickets().tickets
            transactions = NetworkClient.apiService.getTransactionHistory().transactions
            error = null
        } catch (_: Exception) {
            error = "Could not load support tickets."
        }
    }
    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(20.dp)) {
            BackTopBar(title = "Support", onBack = onBack)
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            item {
                if (!showNewTicketForm) {
                    Button(onClick = { showNewTicketForm = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Report an issue with a transaction")
                    }
                } else {
                    Column {
                        Text("Which transaction?", style = MaterialTheme.typography.labelMedium)
                        transactions.take(10).forEach { tx ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${tx.description} · ${tx.currency} ${tx.amount}", style = MaterialTheme.typography.bodySmall)
                                Button(onClick = { selectedTransactionId = tx.id }) {
                                    Text(if (selectedTransactionId == tx.id) "Selected" else "Select")
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Category", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CATEGORIES.forEach { category ->
                                Button(onClick = { selectedCategory = category }) {
                                    Text(if (selectedCategory == category) "✓ $category" else category)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        IdsTextField(value = descriptionText, onValueChange = { descriptionText = it }, label = "Describe the issue", modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Button(
                            enabled = !busy && selectedTransactionId != null && descriptionText.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val transactionId = selectedTransactionId ?: return@Button
                                busy = true
                                scope.launch {
                                    try {
                                        NetworkClient.apiService.createSupportTicket(
                                            CreateSupportTicketRequest(transactionId, selectedCategory, descriptionText),
                                        )
                                        showNewTicketForm = false
                                        selectedTransactionId = null
                                        descriptionText = ""
                                        refresh()
                                    } catch (_: Exception) {
                                        error = "That ticket could not be submitted."
                                    } finally { busy = false }
                                }
                            },
                        ) { Text(if (busy) "Submitting…" else "Submit ticket") }
                    }
                }
            }
            item { Text("Your tickets", style = MaterialTheme.typography.titleMedium) }
            val currentTickets = tickets
            if (currentTickets == null) item { SkeletonBlock() }
            else if (currentTickets.isEmpty()) item { Text("You have no support tickets.") }
            else items(currentTickets, key = { it.id }) { ticket -> TicketCard(ticket) }
        }
    }
}

@Composable
private fun TicketCard(ticket: SupportTicketDto) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp)) {
        Text(ticket.category, style = MaterialTheme.typography.titleMedium)
        Text(ticket.description, style = MaterialTheme.typography.bodyMedium)
        Text("Status: ${ticket.status}", style = MaterialTheme.typography.bodySmall)
        ticket.resolution?.let { Text("Resolution: $it", style = MaterialTheme.typography.bodySmall) }
        Text("Filed: ${ticket.createdAt}", style = MaterialTheme.typography.bodySmall)
    }
}
