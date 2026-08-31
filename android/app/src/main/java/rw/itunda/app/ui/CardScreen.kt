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
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.CardDto
import rw.itunda.core.network.CardTransactionDto
import rw.itunda.core.network.ChargeCardRequest
import rw.itunda.core.network.IssueCardRequest
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SetCardLimitsRequest
import rw.itunda.core.network.SetCardPinRequest
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
    // Real "카드 비밀번호 변경" (change card PIN) inline form (2026-09-01, direct
    // user-supplied Toss Bank card-management screenshots) -- matches bank-mfe's
    // identical setCardPin flow.
    var showPinForm by remember { mutableStateOf(false) }
    var newPinInput by remember { mutableStateOf("") }
    var pinPasswordInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var pinSuccess by remember { mutableStateOf(false) }
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

    fun issue(design: String) {
        busy = true
        error = null
        coroutineScope.launch {
            try {
                NetworkClient.apiService.issueCard(IssueCardRequest(design))
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

    // Real "분실신고" (report lost or stolen) -- a distinct, one-way backend state
    // now exists (POST /api/v1/card/report-lost), closing the gap bank-mfe's own
    // previous version had already found and disclosed live.
    fun reportLost() {
        val current = card ?: return
        if (current.lost || current.closedAt != null) return
        busy = true
        error = null
        coroutineScope.launch {
            try {
                card = NetworkClient.apiService.reportCardLost().card
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busy = false
            }
        }
    }

    // Real "카드 해지하기" (close card) -- a deliberate, one-way retirement distinct
    // from a lost/stolen report; only reissue() below can recover from either.
    fun closeCard() {
        val current = card ?: return
        if (current.closedAt != null) return
        busy = true
        error = null
        coroutineScope.launch {
            try {
                card = NetworkClient.apiService.closeCard().card
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busy = false
            }
        }
    }

    // Real "카드 재발급" (reissue) -- the real recovery path from a lost/stolen or
    // closed card; regenerates last4 and clears the old PIN in place.
    fun reissue() {
        busy = true
        error = null
        coroutineScope.launch {
            try {
                card = NetworkClient.apiService.reissueCard().card
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busy = false
            }
        }
    }

    fun setPin() {
        if (!newPinInput.matches(Regex("^\\d{4}$"))) {
            pinError = "Your card PIN must be exactly 4 digits."
            return
        }
        if (pinPasswordInput.isBlank()) {
            pinError = "Enter your current login password."
            return
        }
        busy = true
        pinError = null
        pinSuccess = false
        coroutineScope.launch {
            try {
                card = NetworkClient.apiService.setCardPin(SetCardPinRequest(newPinInput, pinPasswordInput)).card
                newPinInput = ""
                pinPasswordInput = ""
                showPinForm = false
                pinSuccess = true
            } catch (e: HttpException) {
                pinError = superAppErrorMessage(e)
            } catch (e: IOException) {
                pinError = "Couldn't reach itunda. Check your connection and try again."
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
                chargeMessage = "Paid ${formatMoneyCard(result.transaction.amount)} RWF at ${result.transaction.merchantName}"
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
                // Real Toss Bank "which color do you like?" issuance step -- direct
                // user instruction 2026-08-27: "update itunda bank with all those
                // cards designs allowing users to choose from those designs... that's
                // how toss does it too". See CardDesignPicker.kt's own doc comment for
                // the full sourced account (this replaced the earlier single-design
                // ProductExplainerScreen mockup, which is still real and still used
                // as-is by YouthAccountScreen's own pre-open state).
                CardMode.NO_CARD -> item {
                    CardDesignPicker(busy = busy, onIssue = { design -> issue(design) })
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
                            val cardDesign = CardDesigns.byId(c.design)
                            // Real per-design colors (2026-08-27, direct user
                            // instruction: "update itunda bank with all those cards
                            // designs allowing users to choose from those designs") --
                            // the issued card renders the finish this account actually
                            // chose, not one hardcoded brand gradient. Text stays dark
                            // on Frost Onyx's light front, matching bank-mfe's
                            // identical contrast fix.
                            val onFront = if (!c.frozen && cardDesign.frontLight) Color(0xFF191F28) else Color.White
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
                                                    if (c.frozen) Ids.colors.textTertiary else cardDesign.front,
                                                    if (c.frozen) Ids.colors.textTertiary else cardDesign.front,
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
                                            if (c.frozen) LockGlyph(size = 18.dp) else CardContactlessGlyph(size = 18.dp, tint = onFront.copy(alpha = 0.85f))
                                        }
                                        Column {
                                            Text("itunda card", color = onFront.copy(alpha = 0.85f), fontSize = 13.sp)
                                            Text("•••• •••• •••• ${c.last4}", color = onFront, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 2.sp)
                                            val statusLabel = when {
                                                c.closedAt != null -> "Closed"
                                                c.lost -> "Reported lost or stolen"
                                                c.frozen -> "Frozen"
                                                else -> "✓ Active"
                                            }
                                            Text(statusLabel, color = onFront.copy(alpha = 0.85f), fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            if (c.lost || c.closedAt != null) {
                                CardActionButton("Get a new card", enabled = !busy) { reissue() }
                            } else {
                                CardActionButton(if (c.frozen) "Unfreeze card" else "Freeze card", enabled = !busy) { toggleFreeze() }
                            }
                        }
                        item {
                            Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth().pressScaleClickable(enabled = !busy && c.closedAt == null) { showPinForm = !showPinForm }, horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(if (c.pinSet) "Change card PIN" else "Set card PIN", color = Ids.colors.textPrimary, fontSize = 14.sp)
                                    }
                                    if (showPinForm) {
                                        Text(
                                            "A real 4-digit card PIN, separate from your login password. Confirm your current login password to change it.",
                                            color = Ids.colors.textTertiary, fontSize = 11.sp,
                                        )
                                        pinError?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
                                        IdsTextField(value = newPinInput, onValueChange = { newPinInput = it.filter { c -> c.isDigit() }.take(4) }, label = "New 4-digit PIN", isPassword = true, keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword, modifier = Modifier.fillMaxWidth())
                                        IdsTextField(value = pinPasswordInput, onValueChange = { pinPasswordInput = it }, label = "Current login password", isPassword = true, modifier = Modifier.fillMaxWidth())
                                        CardActionButton(if (busy) "…" else "Save PIN", enabled = !busy) { setPin() }
                                    }
                                }
                            }
                        }
                        pinSuccess.let { if (it) item { Text("Card PIN saved.", color = Ids.colors.success, fontSize = 12.sp) } }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().pressScaleClickable(enabled = !busy && !c.lost && c.closedAt == null) { reportLost() },
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(if (c.lost) "Reported lost or stolen" else "Report lost or stolen", color = Ids.colors.textPrimary, fontSize = 14.sp)
                            }
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().pressScaleClickable(enabled = !busy && c.closedAt == null) { closeCard() },
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(if (c.closedAt != null) "Card closed" else "Close card", color = if (c.closedAt != null) Ids.colors.textSecondary else Ids.colors.danger, fontSize = 14.sp)
                            }
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
                                    val payLabel = when {
                                        c.closedAt != null -> "Card is closed"
                                        c.lost -> "Card reported lost"
                                        c.frozen -> "Card is frozen"
                                        busy -> "Paying…"
                                        else -> "Pay"
                                    }
                                    CardActionButton(payLabel, enabled = !busy && !c.frozen) { charge() }
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
