package rw.itunda.app.ui

import rw.itunda.core.designsystem.components.formatMoney
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.app.R
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
    // Real Toss "결제 계좌" (payment account) reference (2026-09-12) -- see
    // CardActivitySection.kt's CardPaySection doc comment for why only MAIN/PAY are
    // ever real choices here.
    var fundingAccountType by remember { mutableStateOf("MAIN") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var chargeMessage by remember { mutableStateOf<String?>(null) }
    // Real gap closed 2026-09-07 (Card product-completeness pass): rendering used
    // to sniff chargeMessage.startsWith("Paid") to pick success/danger color -- a
    // real correctness bug once that text is localized (rw/fr never start with
    // "Paid"). A real boolean, not a string-content guess.
    var chargeSucceeded by remember { mutableStateOf(false) }
    // Real "카드 비밀번호 변경" (change card PIN) inline form (2026-09-01, direct
    // user-supplied Toss Bank card-management screenshots) -- matches bank-mfe's
    // identical setCardPin flow.
    var showPinForm by remember { mutableStateOf(false) }
    var newPinInput by remember { mutableStateOf("") }
    var pinPasswordInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var pinSuccess by remember { mutableStateOf(false) }
    // Real "Card benefits" port of bank-mfe's BankDashboard.tsx (2026-09-07) -- UI
    // wiring only, getCreditScore()/getCreditScoreSuggestions() already existed.
    var cardUsageFactor by remember { mutableStateOf<rw.itunda.core.network.CreditScoreFactorDto?>(null) }
    var cardSuggestion by remember { mutableStateOf<rw.itunda.core.network.CreditScoreSuggestionDto?>(null) }
    val coroutineScope = rememberCoroutineScope()
    // Prefetched here (LoginScreen.kt's checkingPhoneError convention): stringResource()
    // only works during composition, not inside the plain functions/coroutines below.
    val networkError = stringResource(R.string.card_network_error)
    val limitValidationError = stringResource(R.string.card_limit_validation_error)
    val pinLengthError = stringResource(R.string.card_pin_length_error)
    val pinPasswordRequiredError = stringResource(R.string.card_pin_password_required)
    val chargeValidationError = stringResource(R.string.card_charge_validation_error)
    val pinSavedMessage = stringResource(R.string.card_pin_saved)
    // Fetched as a raw, unsubstituted template (no format args passed) since the
    // real values aren't known until charge() runs later inside a coroutine, well
    // outside composition -- String.format applies it at that point instead.
    val cardPaidMessageTemplate = stringResource(R.string.card_paid_message)

    fun load() {
        coroutineScope.launch {
            try {
                val c = NetworkClient.apiService.getMyCard().card
                card = c
                dailyLimitInput = c.dailyLimit.toPlainString()
                monthlyLimitInput = c.monthlyLimit.toPlainString()
                mode = CardMode.ACTIVE
                transactions = runCatching { NetworkClient.apiService.getCardTransactions().transactions }.getOrDefault(emptyList())
                cardUsageFactor = runCatching { NetworkClient.apiService.getCreditScore().factors.find { it.name == "Card usage" } }.getOrNull()
                cardSuggestion = runCatching {
                    NetworkClient.apiService.getCreditScoreSuggestions().suggestions.find { it.action == "Use your itunda Card more" || it.action == "Get an itunda Card" }
                }.getOrNull()
            } catch (e: HttpException) {
                if (apiErrorCode(e) == "CARD_NOT_FOUND") {
                    mode = CardMode.NO_CARD
                } else {
                    error = superAppErrorMessage(e)
                }
            } catch (e: IOException) {
                error = networkError
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    fun issue(design: String) {
        busy = true
        error = null
        coroutineScope.launch {
            try {
                NetworkClient.apiService.issueCard(UUID.randomUUID().toString(), IssueCardRequest(design))
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
                error = networkError
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
                error = networkError
            } finally {
                busy = false
            }
        }
    }

    fun saveLimits() {
        val daily = dailyLimitInput.trim().toBigDecimalOrNull()
        val monthly = monthlyLimitInput.trim().toBigDecimalOrNull()
        if (daily == null || monthly == null || daily <= BigDecimal.ZERO || monthly <= BigDecimal.ZERO) {
            error = limitValidationError
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
                error = networkError
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
                error = networkError
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
                error = networkError
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
                card = NetworkClient.apiService.reissueCard(UUID.randomUUID().toString()).card
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = networkError
            } finally {
                busy = false
            }
        }
    }

    fun setPin() {
        if (!newPinInput.matches(Regex("^\\d{4}$"))) {
            pinError = pinLengthError
            return
        }
        if (pinPasswordInput.isBlank()) {
            pinError = pinPasswordRequiredError
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
                pinError = networkError
            } finally {
                busy = false
            }
        }
    }

    fun charge() {
        val parsedAmount = chargeAmount.trim().toBigDecimalOrNull()
        if (parsedAmount == null || parsedAmount <= BigDecimal.ZERO || merchantName.isBlank()) {
            chargeMessage = chargeValidationError
            chargeSucceeded = false
            return
        }
        busy = true
        chargeMessage = null
        coroutineScope.launch {
            try {
                val result = NetworkClient.apiService.chargeCard(
                    UUID.randomUUID().toString(),
                    ChargeCardRequest(parsedAmount, merchantName.trim(), fundingAccountType),
                )
                card = result.card
                chargeMessage = String.format(cardPaidMessageTemplate, formatMoney(result.transaction.amount), result.transaction.merchantName)
                chargeSucceeded = true
                merchantName = ""
                chargeAmount = ""
                load()
            } catch (e: HttpException) {
                chargeMessage = superAppErrorMessage(e)
                chargeSucceeded = false
            } catch (e: IOException) {
                chargeMessage = networkError
                chargeSucceeded = false
            } finally {
                busy = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = stringResource(R.string.card_title), onBack = onBack)
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
                                            Text(stringResource(R.string.card_masked_label), color = onFront.copy(alpha = 0.85f), fontSize = 13.sp)
                                            Text("•••• •••• •••• ${c.last4}", color = onFront, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 2.sp)
                                            val statusLabel = when {
                                                c.closedAt != null -> stringResource(R.string.card_status_closed)
                                                c.lost -> stringResource(R.string.card_status_lost)
                                                c.frozen -> stringResource(R.string.card_status_frozen)
                                                else -> stringResource(R.string.card_status_active)
                                            }
                                            Text(statusLabel, color = onFront.copy(alpha = 0.85f), fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            if (c.lost || c.closedAt != null) {
                                CardActionButton(stringResource(R.string.card_get_new_card), enabled = !busy) { reissue() }
                            } else {
                                CardActionButton(if (c.frozen) stringResource(R.string.card_unfreeze) else stringResource(R.string.card_freeze), enabled = !busy) { toggleFreeze() }
                            }
                        }
                        item {
                            Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth().pressScaleClickable(enabled = !busy && c.closedAt == null) { showPinForm = !showPinForm }, horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(if (c.pinSet) stringResource(R.string.card_change_pin) else stringResource(R.string.card_set_pin), color = Ids.colors.textPrimary, fontSize = 14.sp)
                                    }
                                    if (showPinForm) {
                                        Text(
                                            stringResource(R.string.card_pin_form_subtitle),
                                            color = Ids.colors.textTertiary, fontSize = 11.sp,
                                        )
                                        pinError?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
                                        IdsTextField(value = newPinInput, onValueChange = { newPinInput = it.filter { c -> c.isDigit() }.take(4) }, label = stringResource(R.string.card_new_pin_label), isPassword = true, keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword, modifier = Modifier.fillMaxWidth())
                                        IdsTextField(value = pinPasswordInput, onValueChange = { pinPasswordInput = it }, label = stringResource(R.string.card_current_password_label), isPassword = true, modifier = Modifier.fillMaxWidth())
                                        CardActionButton(if (busy) "…" else stringResource(R.string.card_save_pin), enabled = !busy) { setPin() }
                                    }
                                }
                            }
                        }
                        pinSuccess.let { if (it) item { Text(pinSavedMessage, color = Ids.colors.success, fontSize = 12.sp) } }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().pressScaleClickable(enabled = !busy && !c.lost && c.closedAt == null) { reportLost() },
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(if (c.lost) stringResource(R.string.card_status_lost) else stringResource(R.string.card_report_lost), color = Ids.colors.textPrimary, fontSize = 14.sp)
                            }
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().pressScaleClickable(enabled = !busy && c.closedAt == null) { closeCard() },
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(if (c.closedAt != null) stringResource(R.string.card_card_closed) else stringResource(R.string.card_close_card), color = if (c.closedAt != null) Ids.colors.textSecondary else Ids.colors.danger, fontSize = 14.sp)
                            }
                        }
                        item {
                            Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(stringResource(R.string.card_spend_limits), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(stringResource(R.string.card_today_spend, formatMoney(c.spentToday), formatMoney(c.dailyLimit)), color = Ids.colors.textSecondary, fontSize = 12.sp)
                                    Text(stringResource(R.string.card_month_spend, formatMoney(c.spentThisMonth), formatMoney(c.monthlyLimit)), color = Ids.colors.textSecondary, fontSize = 12.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        IdsTextField(value = dailyLimitInput, onValueChange = { dailyLimitInput = it }, label = stringResource(R.string.card_daily_limit_label), modifier = Modifier.weight(1f))
                                        IdsTextField(value = monthlyLimitInput, onValueChange = { monthlyLimitInput = it }, label = stringResource(R.string.card_monthly_limit_label), modifier = Modifier.weight(1f))
                                    }
                                    CardActionButton(stringResource(R.string.card_save_limits), enabled = !busy) { saveLimits() }
                                }
                            }
                        }
                        item {
                            CardPaySection(
                                card = c, merchantName = merchantName, onMerchantNameChange = { merchantName = it },
                                chargeAmount = chargeAmount, onChargeAmountChange = { chargeAmount = it },
                                fundingAccountType = fundingAccountType, onFundingAccountTypeChange = { fundingAccountType = it },
                                chargeMessage = chargeMessage, chargeSucceeded = chargeSucceeded, busy = busy,
                                onCharge = { charge() },
                            )
                        }
                        cardRecentActivitySection(transactions)
                        cardBenefitsSection(cardUsageFactor, cardSuggestion)
                    }
                }
            }
        }
    }
}
