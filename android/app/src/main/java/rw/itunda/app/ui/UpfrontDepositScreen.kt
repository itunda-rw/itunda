package rw.itunda.app.ui

import rw.itunda.core.designsystem.components.formatMoney
import rw.itunda.core.designsystem.components.UPFRONT_DEPOSIT_ANNUAL_RATE
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.components.BucketTransactionRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.OpenUpfrontDepositRequest
import rw.itunda.core.network.UpfrontDepositDto
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.math.BigDecimal

// Real Toss Bank 먼저 이자받는 정기예금 (interest-paid-upfront term deposit) UI
// (2026-07-25) -- the first mobile UI this feature has ever had; the backend
// (UpfrontInterestDepositController/Service) was real and ledger-backed from the start.
// Direct sibling of WeeklySavingsScreen.kt: same no-ViewModel, NetworkClient-direct-
// from-Composable shape, same Toss* color aliases/Ids.layout tokens, same
// BackTopBar/error-card pattern. Simpler than WeeklySavingsScreen on purpose -- no
// installments, no early-withdrawal/cancel path at all (see UpfrontInterestDeposit's
// own backend doc comment for why that's the one real design choice this feature
// depends on), so there's no separate detail screen -- everything a deposit needs to
// show fits on its list row.
private const val TERM_MONTHS = 12
// Consolidated 2026-09-06 into core/designsystem's UpfrontDepositPolicy.kt -- see its
// own doc comment.
private const val ANNUAL_RATE = UPFRONT_DEPOSIT_ANNUAL_RATE

private enum class UpfrontDepositMode { LIST, NEW }

@Composable
fun UpfrontDepositScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var mode by remember { mutableStateOf(UpfrontDepositMode.LIST) }
    var deposits by remember { mutableStateOf<List<UpfrontDepositDto>?>(null) }
    var listError by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    fun loadDeposits() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getUpfrontDeposits()
                if (res.success) deposits = res.deposits
                listError = null
            } catch (e: HttpException) {
                listError = superAppErrorMessage(e)
            } catch (e: IOException) {
                listError = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(refreshKey) { loadDeposits() }

    Column(modifier = Modifier.fillMaxSize()) {
        val title = if (mode == UpfrontDepositMode.NEW) "New 12-month deposit" else "12-Month Deposit"
        val backAction: () -> Unit = if (mode == UpfrontDepositMode.NEW) { { mode = UpfrontDepositMode.LIST } } else onBack
        BackTopBar(title = title, onBack = backAction)

        when (mode) {
            UpfrontDepositMode.NEW -> UpfrontDepositCreateContent(onCreated = { mode = UpfrontDepositMode.LIST; refreshKey++ })
            UpfrontDepositMode.LIST -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(Ids.colors.brand).pressScaleClickable { mode = UpfrontDepositMode.NEW }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+ Open 12-month deposit", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                when {
                    listError != null -> item { ErrorCard(listError!!, onRetry = ::loadDeposits) }
                    deposits == null -> item {
                        SkeletonBlock(height = 120.dp)
                    }
                    deposits!!.isEmpty() -> item {
                        Text(
                            "No deposits yet. Open one and get the full $TERM_MONTHS months' interest " +
                                "($ANNUAL_RATE% per year) paid to your main account immediately -- the principal " +
                                "stays locked for the full term.",
                            color = Ids.colors.textSecondary, fontSize = 14.sp,
                        )
                    }
                    else -> items(deposits!!, key = { it.id }) { deposit -> UpfrontDepositRow(deposit, onChanged = { refreshKey++ }) }
                }
            }
        }
    }
}

@Composable
private fun UpfrontDepositRow(deposit: UpfrontDepositDto, onChanged: () -> Unit) {
    var withdrawing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Real per-bucket ledger (2026-08-31) -- this row previously had no drill-in of
    // any kind (its own doc history noted "everything a deposit needs fits on its
    // list row," true for the summary fields but not for a real transaction history,
    // which its dedicated account has always had, just never exposed).
    var showHistory by remember { mutableStateOf(false) }
    var transactions by remember { mutableStateOf<List<rw.itunda.core.network.BucketTransactionDto>?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Real fix (2026-08-24, flat-design sweep): dropped the per-row Card wrapper --
    // each row in this list gets no card, just vertical padding, matching
    // ShellSection's already-fixed shape (docs/UI_UX_GUIDELINES.md §10).
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Prototype v1038 deposit detail: caption, big principal, interest line, soft facts block.
        Text("Locked principal", color = Ids.colors.textTertiary, fontSize = 14.sp)
        Text("${formatMoney(deposit.principal)} RWF", color = Ids.colors.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp)
        Text("Interest already paid: ${formatMoney(deposit.interestPaid)} RWF", color = Ids.colors.success, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp).clip(RoundedCornerShape(20.dp)).background(Ids.colors.surfaceSoft).padding(horizontal = 20.dp, vertical = 4.dp)) {
            DepositSummaryLine(if (deposit.status == "ACTIVE") "Unlocks on" else "Matured on", formatUpfrontDate(deposit.maturesAt))
            DepositSummaryLine("Status", depositStatusLabel(deposit), depositStatusColor(deposit))
        }
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
        Text(
            if (showHistory) "Hide history" else "View history",
            color = Ids.colors.brand, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
            modifier = Modifier.pressScaleClickable {
                showHistory = !showHistory
                if (showHistory && transactions == null) {
                    coroutineScope.launch {
                        transactions = try {
                            NetworkClient.apiService.getUpfrontDepositTransactions(deposit.id).transactions
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                }
            },
        )
        if (showHistory) {
            when {
                transactions == null -> Text("Loading…", color = Ids.colors.textSecondary, fontSize = 12.sp)
                transactions!!.isEmpty() -> Text("No transactions to show yet.", color = Ids.colors.textSecondary, fontSize = 12.sp)
                else -> Column {
                    transactions!!.sortedByDescending { it.createdAt }.forEach { tx -> BucketTransactionRow(tx) }
                }
            }
        }
        if (deposit.status == "MATURED" && deposit.withdrawnAt == null) {
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.brand)
                    .pressScaleClickable(enabled = !withdrawing) {
                        withdrawing = true
                        coroutineScope.launch {
                            try {
                                NetworkClient.apiService.withdrawUpfrontDeposit(deposit.id)
                                onChanged()
                            } catch (e: HttpException) {
                                error = superAppErrorMessage(e)
                            } catch (e: IOException) {
                                error = "Couldn't reach itunda. Check your connection and try again."
                            } finally {
                                withdrawing = false
                            }
                        }
                    }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (withdrawing) "Working…" else "Withdraw to main account", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

private fun depositStatusLabel(deposit: UpfrontDepositDto): String = when {
    deposit.status == "MATURED" && deposit.withdrawnAt != null -> "Withdrawn"
    deposit.status == "MATURED" -> "Matured"
    else -> "Locked"
}

@Composable
private fun depositStatusColor(deposit: UpfrontDepositDto): Color = when {
    deposit.status == "MATURED" && deposit.withdrawnAt != null -> Ids.colors.textSecondary
    deposit.status == "MATURED" -> Ids.colors.success
    else -> Ids.colors.brand
}

@Composable
private fun UpfrontDepositCreateContent(onCreated: () -> Unit) {
    var principal by remember { mutableStateOf("100000") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val previewInterest = principal.trim().toBigDecimalOrNull()?.let {
        it.multiply(BigDecimal.valueOf(ANNUAL_RATE)).divide(BigDecimal(100))
    }

    fun submit() {
        val amountBd = principal.trim().toBigDecimalOrNull()
        if (amountBd == null || amountBd <= BigDecimal.ZERO) {
            error = "Enter a real deposit amount."
            return
        }
        submitting = true
        coroutineScope.launch {
            try {
                NetworkClient.apiService.openUpfrontDeposit(OpenUpfrontDepositRequest(amountBd))
                error = null
                onCreated()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                submitting = false
            }
        }
    }

    // Prototype v1038 "Get your interest today": amount chips, tinted summary, one CTA.
    val unlocks = remember { java.time.LocalDate.now().plusMonths(TERM_MONTHS.toLong()).toString() }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
    ) {
        Text("Get your interest today", color = Ids.colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            "Lock money for $TERM_MONTHS months. We pay the interest upfront and hold the principal.",
            color = Ids.colors.textTertiary, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(20.dp))
        listOf(50_000L, 100_000L, 250_000L, 500_000L).chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { v ->
                    val on = principal == v.toString()
                    Box(
                        Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (on) Ids.colors.brand else Ids.colors.surfaceSoft)
                            .pressScaleClickable { principal = v.toString() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${formatMoney(v.toDouble())} RWF", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (on) Ids.colors.textPrimary else Ids.colors.textTertiary)
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(20.dp)).background(Ids.colors.brand.copy(alpha = 0.08f)).padding(horizontal = 20.dp, vertical = 4.dp)) {
            DepositSummaryLine("Rate", "$ANNUAL_RATE%")
            DepositSummaryLine("Paid to you now", "+${formatMoney((previewInterest ?: BigDecimal.ZERO).toDouble())} RWF", Ids.colors.success)
            DepositSummaryLine("Unlocks", unlocks)
        }
        Spacer(modifier = Modifier.weight(1f))
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)) }
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(Ids.colors.brand)
                .pressScaleClickable(enabled = !submitting) { submit() }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (submitting) "Working…" else "Lock ${formatMoney((principal.toBigDecimalOrNull() ?: BigDecimal.ZERO).toDouble())} RWF",
                color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun DepositSummaryLine(label: String, value: String, valueColor: Color = Ids.colors.textPrimary) {
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ids.colors.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

private fun formatUpfrontDate(iso: String): String = try {
    java.time.Instant.parse(iso).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
} catch (_: Exception) {
    iso.take(10)
}

