package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.SkeletonBlock
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.CardDto
import rw.itunda.core.network.CardTransactionDto
import rw.itunda.core.network.ChargeCardRequest
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SetCardLimitsRequest
import rw.itunda.core.network.apiErrorCode
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.math.BigDecimal
import java.util.UUID
import rw.itunda.core.designsystem.components.EmptyState

// Real Toss Bank 체크카드 (check/debit card) screen (2026-07-31, item 207) -- first
// Android client, direct port of bank-mfe's CardView. See backend DebitCard.kt's own
// doc comment: itunda has no real card-network partnership, so "paying with your card"
// below is itunda's own honest, ledger-backed simulation of a card-present purchase.
// Same no-ViewModel, NetworkClient-direct-from-Composable shape as MiniWalletScreen.kt.
private enum class CardMode { LOADING, NO_CARD, ACTIVE }

@Composable
fun CardScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var mode by remember { mutableStateOf(CardMode.LOADING) }
    var card by remember { mutableStateOf<CardDto?>(null) }
    var transactions by remember { mutableStateOf<List<CardTransactionDto>>(emptyList()) }
    var dailyLimitInput by remember { mutableStateOf("") }
    var monthlyLimitInput by remember { mutableStateOf("") }
    var merchantName by remember { mutableStateOf("") }
    var chargeAmount by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var chargeMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val c = NetworkClient.apiService.getMyCard().card
                card = c
                dailyLimitInput = c.dailyLimit.toPlainString()
                monthlyLimitInput = c.monthlyLimit.toPlainString()
                mode = CardMode.ACTIVE
                transactions = runCatching { NetworkClient.apiService.getCardTransactions().transactions }.getOrDefault(emptyList())
            } catch (e: HttpException) {
                if (apiErrorCode(e) == "CARD_NOT_FOUND") {
                    mode = CardMode.NO_CARD
                } else {
                    error = superAppErrorMessage(e)
                }
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    fun issue() {
        busy = true
        error = null
        coroutineScope.launch {
            try {
                NetworkClient.apiService.issueCard()
                load()
            } catch (e: HttpException) {
                if (apiErrorCode(e) == "CARD_ALREADY_ISSUED") {
                    // Real Toss-style resolution, not a dead-end error: the account
                    // genuinely already has a card -- load it and move forward
                    // instead of erroring on every retry.
                    load()
                } else {
                    error = superAppErrorMessage(e)
                }
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busy = false
            }
        }
    }

    fun toggleFreeze() {
        val current = card ?: return
        busy = true
        error = null
        coroutineScope.launch {
            try {
                card = if (current.frozen) NetworkClient.apiService.unfreezeCard().card else NetworkClient.apiService.freezeCard().card
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busy = false
            }
        }
    }

    fun saveLimits() {
        val daily = dailyLimitInput.trim().toBigDecimalOrNull()
        val monthly = monthlyLimitInput.trim().toBigDecimalOrNull()
        if (daily == null || monthly == null || daily <= BigDecimal.ZERO || monthly <= BigDecimal.ZERO) {
            error = "Enter real, positive limits."
            return
        }
        busy = true
        error = null
        coroutineScope.launch {
            try {
                card = NetworkClient.apiService.setCardLimits(SetCardLimitsRequest(daily, monthly)).card
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busy = false
            }
        }
    }

    fun charge() {
        val parsedAmount = chargeAmount.trim().toBigDecimalOrNull()
        if (parsedAmount == null || parsedAmount <= BigDecimal.ZERO || merchantName.isBlank()) {
            chargeMessage = "Enter a real merchant name and amount."
            return
        }
        busy = true
        chargeMessage = null
        coroutineScope.launch {
            try {
                val result = NetworkClient.apiService.chargeCard(
                    UUID.randomUUID().toString(),
                    ChargeCardRequest(parsedAmount, merchantName.trim()),
                )
                card = result.card
                chargeMessage = "Paid ${result.transaction.amount.toPlainString()} RWF at ${result.transaction.merchantName}"
                merchantName = ""
                chargeAmount = ""
                load()
            } catch (e: HttpException) {
                chargeMessage = superAppErrorMessage(e)
            } catch (e: IOException) {
                chargeMessage = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busy = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = "Card", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            error?.let { msg -> item { Text(msg, color = Ids.colors.danger, fontSize = 13.sp) } }
            when (mode) {
                CardMode.LOADING -> item {
                    SkeletonBlock(height = 120.dp)
                }
                CardMode.NO_CARD -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "App-controlled spend limits and one-tap freeze -- no branch visit, no waiting.",
                            color = Ids.colors.textSecondary, fontSize = 13.sp,
                        )
                        CardActionButton(if (busy) "Issuing…" else "Get your itunda card", enabled = !busy) { issue() }
                    }
                }
                CardMode.ACTIVE -> {
                    val c = card
                    if (c != null) {
                        item {
                            Card(
                                shape = RoundedCornerShape(Ids.layout.cardCornerRadius),
                                colors = CardDefaults.cardColors(containerColor = if (c.frozen) Ids.colors.textTertiary else Ids.colors.brand),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text("itunda card", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                                    Text("•••• •••• •••• ${c.last4}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 2.sp)
                                    Text(if (c.frozen) "🔒 Frozen" else "✓ Active", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                                }
                            }
                        }
                        item {
                            CardActionButton(if (c.frozen) "Unfreeze card" else "Freeze card", enabled = !busy) { toggleFreeze() }
                        }
                        item {
                            Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Spend limits", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("Today: ${c.spentToday.toPlainString()} / ${c.dailyLimit.toPlainString()} RWF", color = Ids.colors.textSecondary, fontSize = 12.sp)
                                    Text("This month: ${c.spentThisMonth.toPlainString()} / ${c.monthlyLimit.toPlainString()} RWF", color = Ids.colors.textSecondary, fontSize = 12.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        IdsTextField(value = dailyLimitInput, onValueChange = { dailyLimitInput = it }, label = "Daily limit", modifier = Modifier.weight(1f))
                                        IdsTextField(value = monthlyLimitInput, onValueChange = { monthlyLimitInput = it }, label = "Monthly limit", modifier = Modifier.weight(1f))
                                    }
                                    CardActionButton("Save limits", enabled = !busy) { saveLimits() }
                                }
                            }
                        }
                        item {
                            Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Pay with your card", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        "itunda has no real card-network partnership yet, so this simulates a real card-present purchase -- real money moves, real limits apply.",
                                        color = Ids.colors.textTertiary, fontSize = 11.sp,
                                    )
                                    chargeMessage?.let { Text(it, color = if (it.startsWith("Paid")) Ids.colors.success else Ids.colors.danger, fontSize = 12.sp) }
                                    IdsTextField(value = merchantName, onValueChange = { merchantName = it }, label = "Merchant name", modifier = Modifier.fillMaxWidth())
                                    IdsTextField(value = chargeAmount, onValueChange = { chargeAmount = it }, label = "Amount (RWF)", isAmount = true, modifier = Modifier.fillMaxWidth())
                                    CardActionButton(if (c.frozen) "Card is frozen" else if (busy) "Paying…" else "Pay", enabled = !busy && !c.frozen) { charge() }
                                }
                            }
                        }
                        item {
                            Text("Recent card activity", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        if (transactions.isEmpty()) {
                            item { EmptyState("No card purchases yet — once you use your card, they'll show up here.") }
                        } else {
                            items(transactions, key = { it.id }) { t ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(t.merchantName, color = Ids.colors.textPrimary, fontSize = 13.sp)
                                    Text("${t.amount.toPlainString()} RWF", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardActionButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (enabled) Ids.colors.brand else Ids.colors.textTertiary).clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}
