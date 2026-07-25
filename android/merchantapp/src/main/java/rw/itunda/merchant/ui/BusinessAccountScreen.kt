package rw.itunda.merchant.ui

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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rw.itunda.merchant.network.BusinessLedgerEntryDto
import rw.itunda.merchant.network.BusinessWalletDto
import rw.itunda.merchant.network.MoveBusinessMoneyRequest
import rw.itunda.merchant.network.NetworkClient
import java.util.UUID

/**
 * Real 토스뱅크 개인사업자 (business banking for sole proprietors) equivalent
 * (2026-07-25) -- closes a real gap named in the backend's own Merchant.kt doc comment:
 * every merchant's real card/QR collection has always settled straight into their
 * PERSONAL main wallet, with business income and personal spending genuinely
 * inseparable. This tab lets a merchant open a real, dedicated business wallet and
 * deliberately move money into/out of it, with its own real transaction history.
 */
@Composable
fun BusinessAccountTab() {
    var wallet by remember { mutableStateOf<BusinessWalletDto?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var transactions by remember { mutableStateOf<List<BusinessLedgerEntryDto>?>(null) }
    var opening by remember { mutableStateOf(false) }
    var moveAmount by remember { mutableStateOf("") }
    var moving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            try {
                wallet = NetworkClient.apiService.getBusinessAccount().wallet
                transactions = try { NetworkClient.apiService.getBusinessTransactions().transactions } catch (e: Exception) { emptyList() }
                error = null
            } catch (e: Exception) {
                wallet = null
            } finally {
                loaded = true
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    if (!loaded) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator() }
        return
    }

    val current = wallet
    if (current == null) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Business account", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                "Keep your business money separate from your personal wallet. Your real card/QR " +
                    "collections still settle to your personal wallet as before -- move money into your " +
                    "business account whenever you're ready to set it aside.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    opening = true
                    scope.launch {
                        try {
                            NetworkClient.apiService.openBusinessAccount()
                            load()
                        } catch (e: Exception) {
                            error = "Couldn't open a business account. Try again."
                        } finally {
                            opening = false
                        }
                    }
                },
                enabled = !opening,
            ) { Text(if (opening) "Opening…" else "Open business account") }
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Business balance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${"%,.0f".format(current.balance)} RWF", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(current.accountNumber, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Move money", fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = moveAmount, onValueChange = { moveAmount = it }, label = { Text("Amount (RWF)") }, modifier = Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val amount = moveAmount.toDoubleOrNull()
                                if (amount == null || amount <= 0.0) { error = "Enter a real amount."; return@Button }
                                moving = true
                                error = null
                                scope.launch {
                                    try {
                                        NetworkClient.apiService.moveToBusiness(UUID.randomUUID().toString(), MoveBusinessMoneyRequest(amount))
                                        moveAmount = ""
                                        load()
                                    } catch (e: Exception) {
                                        error = "Couldn't move this money. Check your personal balance."
                                    } finally {
                                        moving = false
                                    }
                                }
                            },
                            enabled = !moving,
                            modifier = Modifier.weight(1f),
                        ) { Text("To business") }
                        Button(
                            onClick = {
                                val amount = moveAmount.toDoubleOrNull()
                                if (amount == null || amount <= 0.0) { error = "Enter a real amount."; return@Button }
                                moving = true
                                error = null
                                scope.launch {
                                    try {
                                        NetworkClient.apiService.moveToPersonal(UUID.randomUUID().toString(), MoveBusinessMoneyRequest(amount))
                                        moveAmount = ""
                                        load()
                                    } catch (e: Exception) {
                                        error = "Couldn't move this money. Check your business balance."
                                    } finally {
                                        moving = false
                                    }
                                }
                            },
                            enabled = !moving,
                            modifier = Modifier.weight(1f),
                        ) { Text("To personal") }
                    }
                }
            }
        }
        item { Text("Business transactions", fontWeight = FontWeight.Bold) }
        val txns = transactions
        if (txns.isNullOrEmpty()) {
            item { Text("No business transactions yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(txns, key = { it.id }) { entry ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(entry.memo, style = MaterialTheme.typography.bodyMedium)
                            Text(entry.createdAt.take(10), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            "${if (entry.direction == "CREDIT") "+" else "-"}${"%,.0f".format(entry.amount)} RWF",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}
