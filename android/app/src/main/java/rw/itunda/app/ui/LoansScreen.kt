package rw.itunda.app.ui

import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonVariant
import rw.itunda.core.designsystem.components.IdsSegmentedControl
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.theme.Ids
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import rw.itunda.core.network.ApplyLoanRequest
import rw.itunda.core.network.LenderDto
import rw.itunda.core.network.LoanAccountDto
import rw.itunda.core.network.LoanOfferDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.OpenOverdraftRequest
import rw.itunda.core.network.OverdraftAccountDto
import rw.itunda.core.network.OverdraftAmountRequest
import rw.itunda.core.network.PostpaidCreditAmountRequest
import rw.itunda.core.network.PostpaidCreditLineDto
import rw.itunda.core.network.RefinanceLoanRequest
import rw.itunda.core.network.RefinanceResult
import rw.itunda.core.network.RepayLoanRequest
import java.math.BigDecimal
import java.util.UUID

// Real multi-lender loan marketplace (2026-07-22) -- found fully built on the backend
// (rw.itunda.loans, BNR-licensed partner banks alongside itunda's own underwriting
// book, see LoanOffer.kt's own doc comment) while every "Loan"/"Get a loan" row in
// this app was 100% hardcoded static text ("11% ~ 24%") with zero API call behind it.
// This screen replaces that decoration with the real offers/apply/repay flow.
private enum class LoansMode { OFFERS, MY_LOANS, OVERDRAFT, POSTPAID_CREDIT }

// Real fix (2026-08-26): this whole file interpolated raw BigDecimal amounts with zero
// thousands-separator grouping and currency-prefix ordering ("RWF 500000"), missed by
// the earlier app-wide formatMoneyX() comma-grouping sweep because it never called a
// formatter at all -- see [[project_itunda_money_formatting_sweep]].
private fun formatMoneyLoans(value: BigDecimal): String {
    val rounded = value.stripTrailingZeros()
    return if (rounded.scale() <= 0) "%,d".format(rounded.toBigInteger()) else "%,.2f".format(rounded)
}

@Composable
fun LoansScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var mode by remember { mutableStateOf(LoansMode.OFFERS) }
    var offers by remember { mutableStateOf<List<LoanOfferDto>?>(null) }
    var myLoans by remember { mutableStateOf<List<LoanAccountDto>?>(null) }
    var lenders by remember { mutableStateOf<List<LenderDto>?>(null) }
    var lenderId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var repayAmounts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var refinanceResult by remember { mutableStateOf<RefinanceResult?>(null) }
    // Real Toss writing-principle adoption ("숨은 감정 찾기" -- find the hidden emotion):
    // toss.tech/article/8-writing-principles-of-toss names a fully-repaid loan as their
    // own example of a moment that deserves more than transactional silence. Paying off
    // a loan was a silent list refresh before this -- no acknowledgment of what the user
    // just finished, even though `RepayLoanResponse.remaining` already tells us.
    var payoffMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        try {
            offers = NetworkClient.apiService.getLoanOffers().offers
            myLoans = NetworkClient.apiService.getMyLoans().loans
            lenders = NetworkClient.apiService.getLenders().lenders
            error = null
        } catch (_: Exception) {
            error = "Could not load loans."
        }
    }
    LaunchedEffect(Unit) { refresh() }

    // Real "browse by lender" filter (item 200) -- see bank-mfe/iOS ports' own comment.
    fun selectLender(id: String?) {
        lenderId = id
        error = null
        scope.launch {
            try {
                offers = NetworkClient.apiService.getLoanOffers(id).offers
            } catch (_: Exception) {
                error = "Could not load offers."
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(20.dp)) {
            BackTopBar(title = "Loans", onBack = onBack)
        }
        IdsSegmentedControl(
            options = listOf(
                LoansMode.OFFERS to "Offers",
                LoansMode.MY_LOANS to "My loans (${myLoans?.size ?: 0})",
                LoansMode.OVERDRAFT to "Overdraft",
                LoansMode.POSTPAID_CREDIT to "Postpaid credit",
            ),
            selected = mode,
            onSelect = { mode = it },
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            payoffMessage?.let { item { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) } }
            refinanceResult?.let { result ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Refinanced into ${result.newLoanName}", style = MaterialTheme.typography.titleSmall)
                            Text("${result.oldInterestRate}% → ${result.newInterestRate}%", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (mode == LoansMode.OFFERS) {
                lenders?.let { currentLenders ->
                    item {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            LenderChip("All lenders", selected = lenderId == null) { selectLender(null) }
                            currentLenders.forEach { lender ->
                                LenderChip(lender.name, selected = lenderId == lender.id) { selectLender(lender.id) }
                            }
                        }
                    }
                }
                val currentOffers = offers
                if (currentOffers == null) item { SkeletonBlock() }
                else if (currentOffers.isEmpty()) item { Text("No offers from this lender right now.") }
                else items(currentOffers, key = { it.id }) { offer ->
                    OfferCard(offer, busyId == offer.id) { amount ->
                        busyId = offer.id
                        scope.launch {
                            try {
                                NetworkClient.apiService.applyForLoan(
                                    UUID.randomUUID().toString(),
                                    ApplyLoanRequest(offer.id, amount),
                                )
                                mode = LoansMode.MY_LOANS
                                refresh()
                            } catch (_: Exception) {
                                error = "That loan application could not be completed."
                            } finally { busyId = null }
                        }
                    }
                }
            } else if (mode == LoansMode.OVERDRAFT) {
                item { OverdraftPanel() }
            } else if (mode == LoansMode.POSTPAID_CREDIT) {
                item { PostpaidCreditPanel() }
            } else {
                val currentLoans = myLoans
                if (currentLoans == null) item { SkeletonBlock() }
                else if (currentLoans.isEmpty()) item { Text("You have no loans yet.") }
                else items(currentLoans, key = { it.id }) { loan ->
                    MyLoanCard(
                        loan = loan,
                        busy = busyId == loan.id,
                        repayText = repayAmounts[loan.id] ?: "",
                        onRepayTextChanged = { repayAmounts = repayAmounts + (loan.id to it) },
                        onRepay = {
                            val amount = (repayAmounts[loan.id] ?: "").toBigDecimalOrNull()
                            if (amount == null || amount <= BigDecimal.ZERO) { error = "Enter a valid repayment amount."; return@MyLoanCard }
                            busyId = loan.id
                            error = null
                            scope.launch {
                                try {
                                    val result = NetworkClient.apiService.repayLoan(UUID.randomUUID().toString(), RepayLoanRequest(loan.id, amount))
                                    repayAmounts = repayAmounts - loan.id
                                    payoffMessage = if (result.remaining <= BigDecimal.ZERO) {
                                        "You paid off this loan in full — one less thing to carry."
                                    } else null
                                    refresh()
                                } catch (_: Exception) {
                                    error = "That repayment could not be completed."
                                } finally { busyId = null }
                            }
                        },
                        onRefinance = {
                            busyId = loan.id
                            error = null
                            scope.launch {
                                try {
                                    refinanceResult = NetworkClient.apiService.refinanceLoan(
                                        UUID.randomUUID().toString(),
                                        RefinanceLoanRequest(loan.id),
                                    )
                                    refresh()
                                } catch (_: Exception) {
                                    error = "No better rate is available for this loan right now."
                                } finally { busyId = null }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OfferCard(offer: LoanOfferDto, busy: Boolean, onApply: (BigDecimal) -> Unit) {
    var amountText by remember { mutableStateOf(offer.maxAmount.toPlainString()) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(offer.name, style = MaterialTheme.typography.titleMedium)
            Text(offer.lenderName, style = MaterialTheme.typography.bodySmall)
            Text("Up to ${formatMoneyLoans(offer.maxAmount)} RWF · ${offer.interestRate}% · ${offer.term}", style = MaterialTheme.typography.bodyMedium)
            Text(offer.requirements, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            IdsTextField(value = amountText, onValueChange = { amountText = it }, label = "Amount (RWF)", isAmount = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            IdsButton(
                text = if (busy) "Applying…" else "Apply",
                enabled = !busy,
                onClick = {
                    val amount = amountText.toBigDecimalOrNull()
                    if (amount != null && amount > BigDecimal.ZERO) onApply(amount)
                },
            )
        }
    }
}

@Composable
private fun MyLoanCard(
    loan: LoanAccountDto,
    busy: Boolean,
    repayText: String,
    onRepayTextChanged: (String) -> Unit,
    onRepay: () -> Unit,
    onRefinance: () -> Unit,
) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp)) {
        Text("${formatMoneyLoans(loan.principal)} RWF loan", style = MaterialTheme.typography.titleMedium)
        Text("Outstanding: ${formatMoneyLoans(loan.outstanding)} RWF", style = MaterialTheme.typography.bodyMedium)
        Text("Status: ${loan.status} · ${loan.interestRate}%", style = MaterialTheme.typography.bodySmall)
        if (loan.status == "ACTIVE") {
            Spacer(Modifier.height(8.dp))
            IdsTextField(value = repayText, onValueChange = onRepayTextChanged, label = "Repay amount (RWF)", isAmount = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            IdsButton(text = if (busy) "Repaying…" else "Repay", enabled = !busy, onClick = onRepay)
            Spacer(Modifier.height(8.dp))
            IdsButton(
                text = if (busy) "Checking…" else "Refinance to a lower rate",
                enabled = !busy,
                onClick = onRefinance,
                variant = IdsButtonVariant.Tinted,
            )
        }
    }
}

// Selectable filter chip, not a CTA -- kept as a custom shape (same call as the Maps
// search pill earlier this sweep) rather than forced into IdsButton, which has no
// "selected" visual state. Real bug was the *colors*: MaterialTheme.colorScheme.primary/
// onSurface/outline are stock Material3 defaults, not itunda's Ids tokens, so this
// rendered off-brand purple/black regardless of what the rest of the screen looked like.
@Composable
private fun LenderChip(label: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) Ids.colors.pressed else androidx.compose.ui.graphics.Color.Transparent,
            contentColor = if (selected) Ids.colors.textBrand else Ids.colors.textPrimary,
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) Ids.colors.brand else Ids.colors.divider,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

// Real Toss Bank/KakaoBank 마이너스통장 (overdraft/revolving line-of-credit) -- see
// backend OverdraftAccount.kt's own doc comment. Found 2026-07-29 via a full-backend-
// endpoint sweep: zero client anywhere on any of the 3 platforms before this.
@Composable
private fun OverdraftPanel() {
    var account by remember { mutableStateOf<OverdraftAccountDto?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var requestedLimit by remember { mutableStateOf("100000") }
    var drawAmount by remember { mutableStateOf("") }
    var repayAmount by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            account = NetworkClient.apiService.getMyOverdraft().account
        } catch (_: Exception) {
            error = "Could not load your overdraft account."
        } finally {
            loaded = true
        }
    }

    if (!loaded) { SkeletonBlock(); return }

    val current = account
    if (current == null) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Open an overdraft line", style = MaterialTheme.typography.titleMedium)
                Text(
                    "A pre-approved credit limit you can draw from anytime -- pay interest only on what you actually use, up to 500,000 RWF.",
                    style = MaterialTheme.typography.bodySmall,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(8.dp))
                IdsTextField(value = requestedLimit, onValueChange = { requestedLimit = it }, label = "Requested limit (RWF)", modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                IdsButton(
                    text = if (busy) "Opening…" else "Open overdraft",
                    enabled = !busy,
                    onClick = {
                        val limit = requestedLimit.toBigDecimalOrNull()
                        if (limit == null || limit <= BigDecimal.ZERO) { error = "Enter a valid credit limit."; return@IdsButton }
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                account = NetworkClient.apiService.openOverdraft(UUID.randomUUID().toString(), OpenOverdraftRequest(limit)).account
                            } catch (_: Exception) {
                                error = "Could not open an overdraft account."
                            } finally { busy = false }
                        }
                    },
                )
            }
        }
        return
    }

    val availableCredit = current.creditLimit.subtract(current.drawnBalance)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Overdraft line", style = MaterialTheme.typography.titleMedium)
            Text("Drawn: ${formatMoneyLoans(current.drawnBalance)} of ${formatMoneyLoans(current.creditLimit)} RWF", style = MaterialTheme.typography.bodyMedium)
            Text("Available to draw: ${formatMoneyLoans(availableCredit)} RWF · ${current.interestRate}% annual, interest only on what's drawn", style = MaterialTheme.typography.bodySmall)
            notice?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(8.dp))
            IdsTextField(value = drawAmount, onValueChange = { drawAmount = it }, label = "Draw amount (RWF)", isAmount = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            IdsButton(
                text = if (busy) "Drawing…" else "Draw",
                enabled = !busy,
                onClick = {
                    val amount = drawAmount.toBigDecimalOrNull()
                    if (amount == null || amount <= BigDecimal.ZERO) { error = "Enter a valid amount to draw."; return@IdsButton }
                    busy = true
                    error = null
                    notice = null
                    scope.launch {
                        try {
                            val res = NetworkClient.apiService.drawOverdraft(UUID.randomUUID().toString(), OverdraftAmountRequest(amount))
                            account = current.copy(drawnBalance = res.drawnBalance)
                            drawAmount = ""
                            notice = "Drew ${formatMoneyLoans(res.amount)} RWF -- ${formatMoneyLoans(res.availableCredit)} RWF still available."
                        } catch (_: Exception) {
                            error = "Could not draw from your overdraft."
                        } finally { busy = false }
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            IdsTextField(value = repayAmount, onValueChange = { repayAmount = it }, label = "Repay amount (RWF)", isAmount = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            IdsButton(
                text = if (busy) "Repaying…" else "Repay",
                enabled = !busy && current.drawnBalance > BigDecimal.ZERO,
                variant = IdsButtonVariant.Tinted,
                onClick = {
                    val amount = repayAmount.toBigDecimalOrNull()
                    if (amount == null || amount <= BigDecimal.ZERO) { error = "Enter a valid repayment amount."; return@IdsButton }
                    busy = true
                    error = null
                    notice = null
                    scope.launch {
                        try {
                            val res = NetworkClient.apiService.repayOverdraft(UUID.randomUUID().toString(), OverdraftAmountRequest(amount))
                            account = current.copy(drawnBalance = res.drawnBalance)
                            repayAmount = ""
                            notice = "Repaid ${formatMoneyLoans(res.amount)} RWF -- ${formatMoneyLoans(res.availableCredit)} RWF now available."
                        } catch (_: Exception) {
                            error = "Could not repay your overdraft."
                        } finally { busy = false }
                    }
                },
            )
        }
    }
}

// Real Naver Pay/Kakao Pay/Toss 후불결제 (postpaid/BNPL credit line, real since
// 2026-07-31) -- first Android client for this feature, mirroring bank-mfe's
// PostpaidCreditView.tsx and this screen's own OverdraftPanel shape exactly. Genuinely
// distinct from overdraft above: no requested-limit input (auto-computed from the
// caller's own real credit score), no interest shown for spending (only a real late fee
// if a cycle goes unpaid).
@Composable
private fun PostpaidCreditPanel() {
    var line by remember { mutableStateOf<PostpaidCreditLineDto?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var spendAmount by remember { mutableStateOf("") }
    var repayAmount by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            line = NetworkClient.apiService.getMyPostpaidCredit().line
        } catch (_: Exception) {
            error = "Could not load your postpaid credit line."
        } finally {
            loaded = true
        }
    }

    if (!loaded) { SkeletonBlock(); return }

    val current = line
    if (current == null) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Get postpaid credit", style = MaterialTheme.typography.titleMedium)
                Text(
                    "A small credit line for real purchases, interest-free if you pay within 30 days -- your limit is set automatically from your credit score, up to 300,000 RWF.",
                    style = MaterialTheme.typography.bodySmall,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(8.dp))
                IdsButton(
                    text = if (busy) "Applying…" else "Get postpaid credit",
                    enabled = !busy,
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                line = NetworkClient.apiService.applyForPostpaidCredit(UUID.randomUUID().toString()).line
                            } catch (_: Exception) {
                                error = "Could not open a postpaid credit line."
                            } finally { busy = false }
                        }
                    },
                )
            }
        }
        return
    }

    val availableCredit = current.creditLimit.subtract(current.currentBalance)
    val suspended = current.status == "SUSPENDED"
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Postpaid credit", style = MaterialTheme.typography.titleMedium)
            Text("Owed: ${formatMoneyLoans(current.currentBalance)} of ${formatMoneyLoans(current.creditLimit)} RWF", style = MaterialTheme.typography.bodyMedium)
            Text("Available: ${formatMoneyLoans(availableCredit)} RWF · interest-free if repaid within 30 days", style = MaterialTheme.typography.bodySmall)
            if (suspended) {
                Text("Suspended -- repay your overdue balance to keep spending.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            notice?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(8.dp))
            IdsTextField(value = spendAmount, onValueChange = { spendAmount = it }, label = "Spend amount (RWF)", isAmount = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            IdsButton(
                text = if (busy) "Adding…" else "Add to account",
                enabled = !busy && !suspended,
                onClick = {
                    val amount = spendAmount.toBigDecimalOrNull()
                    if (amount == null || amount <= BigDecimal.ZERO) { error = "Enter a valid amount to spend."; return@IdsButton }
                    busy = true
                    error = null
                    notice = null
                    scope.launch {
                        try {
                            val res = NetworkClient.apiService.spendPostpaidCredit(UUID.randomUUID().toString(), PostpaidCreditAmountRequest(amount))
                            line = current.copy(currentBalance = res.currentBalance)
                            spendAmount = ""
                            notice = "Added ${formatMoneyLoans(res.amount)} RWF to your account -- ${formatMoneyLoans(res.availableCredit)} RWF still available."
                        } catch (_: Exception) {
                            error = "Could not spend from your postpaid credit line."
                        } finally { busy = false }
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            IdsTextField(value = repayAmount, onValueChange = { repayAmount = it }, label = "Repay amount (RWF)", isAmount = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            IdsButton(
                text = if (busy) "Repaying…" else "Repay",
                enabled = !busy && current.currentBalance > BigDecimal.ZERO,
                variant = IdsButtonVariant.Tinted,
                onClick = {
                    val amount = repayAmount.toBigDecimalOrNull()
                    if (amount == null || amount <= BigDecimal.ZERO) { error = "Enter a valid repayment amount."; return@IdsButton }
                    busy = true
                    error = null
                    notice = null
                    scope.launch {
                        try {
                            val res = NetworkClient.apiService.repayPostpaidCredit(UUID.randomUUID().toString(), PostpaidCreditAmountRequest(amount))
                            line = current.copy(currentBalance = res.currentBalance, status = if (res.currentBalance <= BigDecimal.ZERO) "ACTIVE" else current.status)
                            repayAmount = ""
                            notice = "Repaid ${formatMoneyLoans(res.amount)} RWF -- ${formatMoneyLoans(res.availableCredit)} RWF now available."
                        } catch (_: Exception) {
                            error = "Could not repay your postpaid credit line."
                        } finally { busy = false }
                    }
                },
            )
        }
    }
}
