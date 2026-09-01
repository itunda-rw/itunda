package rw.itunda.feature.credit.impl

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

// Real fix (2026-08-26): split out of LoansScreen.kt once that file grew past its
// file-size-lint baseline. Overdraft and postpaid-credit are both real, self-
// contained credit-line panels only rendered inside LoansScreen's own mode switch.
// Same package, so zero import changes anywhere.

// Real Toss Bank/KakaoBank 마이너스통장 (overdraft/revolving line-of-credit) -- see
// backend OverdraftAccount.kt's own doc comment. Found 2026-07-29 via a full-backend-
// endpoint sweep: zero client anywhere on any of the 3 platforms before this.
@Composable
internal fun OverdraftPanel() {
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
internal fun PostpaidCreditPanel() {
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
