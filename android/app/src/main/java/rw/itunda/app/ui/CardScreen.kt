package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
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
import rw.itunda.core.designsystem.components.BankCardChip
import rw.itunda.core.designsystem.components.CardContactlessGlyph
import rw.itunda.core.designsystem.itundaface.LockGlyph
import rw.itunda.core.designsystem.itundaface.MoneyBagGlyph
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
// Same no-ViewModel, NetworkClient-direct-from-Composable shape as YouthAccountScreen.kt.
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
                // Real, sourced Toss Bank "explain the product before you commit" rebuild
                // (2026-08-24, direct user-supplied reference + follow-up "improve a kind
                // of products like this that needs it" -- Card is the SAME real gap
                // YouthAccountScreen just closed). See ProductExplainerScreen's own doc
                // comment for the full sourced account and the honest boundary this
                // mirrors byte-for-byte from bank-mfe's own CardExplainer (commit
                // f9ff4971): no fabricated Toss-specific features (K-Pass, NFC tap-to-pay),
                // every claim grounded in CardService.kt's real capabilities.
                CardMode.NO_CARD -> item {
                    val feeIcon: @Composable () -> Unit = { MoneyBagGlyph(size = 20.dp) }
                    val instantIcon: @Composable () -> Unit = { Icon(Icons.Outlined.Bolt, contentDescription = null, tint = Ids.colors.brand, modifier = Modifier.size(20.dp)) }
                    val limitsIcon: @Composable () -> Unit = { Icon(Icons.Outlined.Shield, contentDescription = null, tint = Ids.colors.brand, modifier = Modifier.size(20.dp)) }
                    val freezeIcon: @Composable () -> Unit = { LockGlyph(size = 20.dp) }
                    val features: List<Pair<@Composable () -> Unit, String>> = listOf(
                        feeIcon to "No annual fee, ever",
                        instantIcon to "Issued instantly in the app -- no branch visit",
                        limitsIcon to "Set your own daily and monthly spend limits",
                        freezeIcon to "One-tap freeze if it's ever lost",
                    )
                    rw.itunda.core.designsystem.components.ProductExplainerScreen(
                        icon = {
                            // Real card-shaped mockup (2026-08-26, direct user
                            // instruction: "all cards designs should resemble real
                            // card") -- fully masked since no card is issued yet, a
                            // real bank app shows this same all-dots placeholder
                            // rather than a fabricated number.
                            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("itunda", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    CardContactlessGlyph(size = 16.dp)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                                    BankCardChip(size = 26.dp)
                                    Text("•••• •••• •••• ••••", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, letterSpacing = 1.sp)
                                }
                            }
                        },
                        title = "Your own itunda card, in seconds",
                        subtitle = "A real debit card for your itunda balance -- no paperwork, no waiting.",
                        features = features,
                        ctaLabel = if (busy) "Issuing…" else "Get your itunda card",
                        ctaEnabled = !busy,
                        onCta = { issue() },
                    )
                }
                CardMode.ACTIVE -> {
                    val c = card
                    if (c != null) {
                        item {
                            // Real card-shaped visual (2026-08-26, direct user
                            // instruction: "all cards designs should resemble real
                            // card") -- a real ISO/IEC 7810 ID-1 card aspect ratio,
                            // BankCardChip + CardContactlessGlyph (shared with
                            // AccountCardCarousel and ProductExplainerScreen, see
                            // their own doc comment in ItundaAppScreen.kt), instead
                            // of a flat color block with just masked-number text.
                            Card(
                                shape = RoundedCornerShape(Ids.layout.cardCornerRadius),
                                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                                modifier = Modifier.fillMaxWidth().aspectRatio(1.586f),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            androidx.compose.ui.graphics.Brush.linearGradient(
                                                listOf(
                                                    if (c.frozen) Ids.colors.textTertiary else Ids.colors.brand,
                                                    if (c.frozen) Ids.colors.textTertiary else Ids.colors.brand,
                                                    Color.Black.copy(alpha = 0.18f),
                                                ),
                                            ),
                                        ),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .background(
                                                androidx.compose.ui.graphics.Brush.linearGradient(
                                                    listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                                                ),
                                            ),
                                    )
                                    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            BankCardChip(size = 32.dp)
                                            if (c.frozen) LockGlyph(size = 18.dp) else CardContactlessGlyph(size = 18.dp)
                                        }
                                        Column {
                                            Text("itunda card", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                                            Text("•••• •••• •••• ${c.last4}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 2.sp)
                                            if (c.frozen) {
                                                Text("Frozen", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                                            } else {
                                                Text("✓ Active", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                                            }
                                        }
                                    }
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
                                    Text("Today: ${formatMoneyCard(c.spentToday)} / ${formatMoneyCard(c.dailyLimit)} RWF", color = Ids.colors.textSecondary, fontSize = 12.sp)
                                    Text("This month: ${formatMoneyCard(c.spentThisMonth)} / ${formatMoneyCard(c.monthlyLimit)} RWF", color = Ids.colors.textSecondary, fontSize = 12.sp)
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
                                    Text("${formatMoneyCard(t.amount)} RWF", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
            .background(if (enabled) Ids.colors.brand else Ids.colors.textTertiary).pressScaleClickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

// Real fix (2026-08-26) -- was raw BigDecimal.toPlainString() with zero thousands
// grouping (matches the exact same bug this pass fixed in the 19 other per-screen
// formatMoneyX helpers across this same app -- see e.g. RideScreen.kt's
// formatMoneyRide own doc comment).
private fun formatMoneyCard(value: BigDecimal): String {
    val rounded = value.stripTrailingZeros()
    return if (rounded.scale() <= 0) "%,d".format(rounded.toBigInteger()) else "%,.2f".format(rounded)
}
