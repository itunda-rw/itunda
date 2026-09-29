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
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
import rw.itunda.core.designsystem.components.IdsSegmentedControl
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.app.R
import rw.itunda.core.network.CreateSupportTicketRequest
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SupportTicketDto
import rw.itunda.core.network.TransactionDto
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException

// Real customer support tickets (2026-07-22) -- found fully built on the backend
// (rw.itunda.support) with zero client UI anywhere; this app's "Support" section was
// five static rows (FAQ/Live chat/Call support/...) with no backend behind any of
// them. A ticket is always tied to a specific transaction (see SupportTicket.kt's own
// doc comment for why), so this screen has the user pick one from their real
// transaction history rather than filing a free-floating complaint.
// Real cross-platform gap closed (Support product-completeness pass, 2026-09-08):
// "RIDE_ISSUE" is a real backend category with its own real web client pre-filled
// hand-off (bank-mfe's RidePassengerView "Report an issue" button); this screen now
// has the equivalent -- see initialTransactionId/initialCategory below, wired from
// RideScreen's own new onReportIssue callback via ItundaAppScreen.kt's
// pendingRideIssueTransactionId state (same shape as this file's own sibling
// hand-offs, e.g. MarketplaceContent's requestedView).
private val CATEGORIES = listOf("GENERAL", "PAYMENT_DISPUTE", "ACCOUNT_TAKEOVER", "RIDE_ISSUE", "EATS_ORDER_ISSUE")

@Composable
fun SupportScreen(
    onBack: () -> Unit,
    initialTransactionId: String? = null,
    initialCategory: String? = null,
    onConsumedInitial: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    var tickets by remember { mutableStateOf<List<SupportTicketDto>?>(null) }
    var transactions by remember { mutableStateOf<List<TransactionDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showNewTicketForm by remember { mutableStateOf(initialTransactionId != null) }
    var selectedTransactionId by remember { mutableStateOf(initialTransactionId) }
    var selectedCategory by remember { mutableStateOf(initialCategory ?: CATEGORIES.first()) }
    var descriptionText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(initialTransactionId) {
        if (initialTransactionId != null) onConsumedInitial()
    }

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
                    IdsButton(text = stringResource(R.string.support_report_issue_cta), onClick = { showNewTicketForm = true })
                } else {
                    Column {
                        Text(stringResource(R.string.support_which_transaction), style = MaterialTheme.typography.labelMedium)
                        transactions.take(10).forEach { tx ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${tx.description} · ${tx.currency} ${tx.amount}", style = MaterialTheme.typography.bodySmall)
                                IdsButton(
                                    text = if (selectedTransactionId == tx.id) "Selected" else "Select",
                                    variant = if (selectedTransactionId == tx.id) IdsButtonVariant.Filled else IdsButtonVariant.Tinted,
                                    size = IdsButtonSize.Small,
                                    onClick = { selectedTransactionId = tx.id },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.support_category), style = MaterialTheme.typography.labelMedium)
                        IdsSegmentedControl(
                            options = CATEGORIES.map { it to it },
                            selected = selectedCategory,
                            onSelect = { selectedCategory = it },
                        )
                        Spacer(Modifier.height(8.dp))
                        IdsTextField(value = descriptionText, onValueChange = { descriptionText = it }, label = stringResource(R.string.support_describe_issue), modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        IdsButton(
                            text = if (busy) stringResource(R.string.support_submitting) else stringResource(R.string.support_submit_ticket),
                            enabled = !busy && selectedTransactionId != null && descriptionText.isNotBlank(),
                            onClick = {
                                val transactionId = selectedTransactionId ?: return@IdsButton
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
                                    } catch (e: HttpException) {
                                        error = superAppErrorMessage(e)
                                    } catch (e: IOException) {
                                        error = "Couldn't reach itunda. Check your connection and try again."
                                    } finally { busy = false }
                                }
                            },
                        )
                    }
                }
            }
            item { Text(stringResource(R.string.support_your_tickets), style = MaterialTheme.typography.titleMedium) }
            val currentTickets = tickets
            if (currentTickets == null) item { SkeletonBlock() }
            else if (currentTickets.isEmpty()) item { Text(stringResource(R.string.support_no_tickets)) }
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
