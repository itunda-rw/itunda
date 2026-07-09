package com.itunda.app.ui.screens.loans

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.itunda.app.data.models.ActiveLoan
import com.itunda.app.data.models.LoanOffer
import com.itunda.app.ui.theme.*
import com.itunda.app.viewmodel.LoansViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansScreen() {
    val viewModel: LoansViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // ── Success ─────────────────────────────────────────────────────────
    state.result?.let { result ->
        LoanSuccessScreen(result) { viewModel.clearResult() }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            TopAppBar(
                title   = { Text("Credit", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                actions = {
                    IconButton(onClick = { viewModel.loadData() }) {
                        Icon(Icons.Outlined.Refresh, null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }

        // ── Credit limit header ──────────────────────────────────────────
        item {
            Card(
                modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape     = RoundedCornerShape(20.dp),
                colors    = CardDefaults.cardColors(containerColor = Primary),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Available credit limit", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "500,000 RWF",
                        fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        letterSpacing = (-1).sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress    = { 0.4f },
                        modifier    = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
                        color       = Color.White.copy(alpha = 0.9f),
                        trackColor  = Color.White.copy(alpha = 0.25f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("200,000 RWF in use", color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
                }
            }
        }

        // ── Active loans ─────────────────────────────────────────────────
        if (state.activeLoans.isNotEmpty()) {
            item {
                Card(
                    modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape     = RoundedCornerShape(20.dp),
                    colors    = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Active loans", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                        Spacer(modifier = Modifier.height(14.dp))
                        state.activeLoans.forEachIndexed { i, loan ->
                            ActiveLoanRow(loan) { viewModel.repayLoan(loan.id, loan.monthlyPayment) }
                            if (i < state.activeLoans.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Divider)
                            }
                        }
                    }
                }
            }
        }

        // ── Apply form ────────────────────────────────────────────────────
        if (state.selectedOffer != null) {
            item {
                ApplyCard(
                    offer         = state.selectedOffer!!,
                    amount        = state.applyAmount,
                    onAmountChange = { viewModel.updateApplyAmount(it) },
                    onApply       = { viewModel.applyLoan() },
                    onDismiss     = { viewModel.clearResult() }
                )
            }
        }

        // ── Loan offers ───────────────────────────────────────────────────
        item {
            Card(
                modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape     = RoundedCornerShape(20.dp),
                colors    = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Loan offers", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(14.dp))
                    state.offers.forEachIndexed { i, offer ->
                        LoanOfferRow(offer) { viewModel.selectOffer(offer) }
                        if (i < state.offers.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Divider)
                        }
                    }
                }
            }
        }

        state.error?.let { error ->
            item {
                Snackbar(modifier = Modifier.padding(16.dp), containerColor = NegativeRed, contentColor = Color.White) {
                    Text(error)
                }
            }
        }
    }
}

// ── Success ───────────────────────────────────────────────────────────────
@Composable
private fun LoanSuccessScreen(result: String, onDone: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Box(
                modifier = Modifier.size(80.dp).clip(CircleShape).background(PositiveGreen.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Check, null, tint = PositiveGreen, modifier = Modifier.size(44.dp))
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text("Application submitted", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = TextPrimary, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(8.dp))
            Text(result, color = TextSecondary, fontSize = 15.sp, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(40.dp))
            Button(
                onClick  = onDone,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text("Done", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Active loan row ───────────────────────────────────────────────────────
@Composable
private fun ActiveLoanRow(loan: ActiveLoan, onRepay: () -> Unit) {
    val isOverdue = loan.status == "overdue"
    val statusColor = if (isOverdue) NegativeRed else PositiveGreen

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(statusColor.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.AccountBalance, null, tint = statusColor, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(loan.type, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                Surface(shape = RoundedCornerShape(6.dp), color = statusColor.copy(alpha = 0.1f)) {
                    Text(
                        if (isOverdue) "Overdue" else "Active",
                        color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                "Remaining: %,.0f RWF  ·  ${loan.interestRate}%".format(loan.remaining),
                fontSize = 12.sp, color = TextSecondary
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("%,.0f".format(loan.monthlyPayment), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("/ month", fontSize = 11.sp, color = TextSecondary)
        }
    }

    Spacer(modifier = Modifier.height(10.dp))
    Button(
        onClick  = onRepay,
        modifier = Modifier.fillMaxWidth().height(44.dp),
        shape    = RoundedCornerShape(12.dp),
        colors   = ButtonDefaults.buttonColors(containerColor = if (isOverdue) NegativeRed else Primary)
    ) {
        Text("Repay ${"%,.0f".format(loan.monthlyPayment)} RWF", fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

// ── Loan offer row ────────────────────────────────────────────────────────
@Composable
private fun LoanOfferRow(offer: LoanOffer, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Gold.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.MonetizationOn, null, tint = Gold, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(offer.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
            Text(offer.description, fontSize = 12.sp, color = TextSecondary, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("Up to", fontSize = 11.sp, color = TextSecondary)
            Text("%,.0f".format(offer.maxAmount), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Primary)
            Text("${offer.interestRate}% APR", fontSize = 11.sp, color = TextSecondary)
        }
        Spacer(modifier = Modifier.width(4.dp))
        Icon(Icons.Outlined.ChevronRight, null, tint = TextTertiary)
    }
}

// ── Apply card ────────────────────────────────────────────────────────────
@Composable
private fun ApplyCard(
    offer: LoanOffer,
    amount: String,
    onAmountChange: (String) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Apply — ${offer.name}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
            Text("${offer.interestRate}% APR  ·  up to ${offer.termMonths} months", color = TextSecondary, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value         = amount,
                onValueChange = onAmountChange,
                label         = { Text("Loan amount (RWF)") },
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(14.dp),
                singleLine    = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary, unfocusedBorderColor = CardBorder,
                    focusedLabelColor  = Primary, cursorColor = Primary
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(offer.minAmount, offer.maxAmount / 2.0, offer.maxAmount).forEach { amt ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Background)
                            .clickable { onAmountChange(amt.toLong().toString()) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("%,.0f".format(amt), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick  = onApply,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = Primary,
                    disabledContainerColor = Primary.copy(alpha = 0.4f)
                ),
                enabled  = amount.toDoubleOrNull()?.let { it >= offer.minAmount && it <= offer.maxAmount } == true
            ) {
                Text("Apply now", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
