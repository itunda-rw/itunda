package com.itunda.app.ui.screens.bills

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.itunda.app.data.models.BillProvider
import com.itunda.app.data.models.PendingBill
import com.itunda.app.ui.theme.*
import com.itunda.app.viewmodel.BillsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsScreen() {
    val viewModel: BillsViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // ── Success screen ─────────────────────────────────────────────────────
    state.paymentResult?.let { result ->
        BillSuccessScreen(result) { viewModel.clearPaymentResult() }
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
                title  = { Text("Pay bills", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }

        // ── Pending bills ──────────────────────────────────────────────────
        if (state.pendingBills.isNotEmpty()) {
            item {
                Card(
                    modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape     = RoundedCornerShape(20.dp),
                    colors    = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Due bills", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                            Surface(shape = RoundedCornerShape(8.dp), color = NegativeRed.copy(alpha = 0.08f)) {
                                Text(
                                    "${state.pendingBills.size}",
                                    color     = NegativeRed,
                                    fontSize  = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier  = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        state.pendingBills.forEachIndexed { i, bill ->
                            PendingBillRow(bill) { viewModel.payBill(bill.id, bill.amount) }
                            if (i < state.pendingBills.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Divider)
                            }
                        }
                    }
                }
            }
        }

        // ── Bill providers ─────────────────────────────────────────────────
        item {
            Card(
                modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape     = RoundedCornerShape(20.dp),
                colors    = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Bill providers", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(16.dp))
                    val providers = state.providers.filter { !it.isAirtime }
                    val rows = (providers.size + 3) / 4
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.height((rows * 88).dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement   = Arrangement.spacedBy(12.dp),
                        userScrollEnabled     = false
                    ) {
                        items(providers) { provider ->
                            ProviderItem(provider) { viewModel.selectProvider(provider) }
                        }
                    }
                }
            }
        }

        // ── Airtime ────────────────────────────────────────────────────────
        item {
            Card(
                modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape     = RoundedCornerShape(20.dp),
                colors    = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Airtime top-up", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Network selector
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("MTN", "Airtel").forEach { network ->
                            val selected = state.airtimeNetwork == network
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) PrimaryLight else Background)
                                    .border(
                                        1.dp,
                                        if (selected) Primary else CardBorder,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { viewModel.updateAirtimeNetwork(network) }
                                    .padding(horizontal = 20.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    network,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = if (selected) Primary else TextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value         = state.airtimePhone,
                        onValueChange = { viewModel.updateAirtimePhone(it) },
                        label         = { Text("Phone number") },
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(14.dp),
                        singleLine    = true,
                        prefix        = { Text("+250", color = TextSecondary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors        = tossTextFieldColors()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value         = state.airtimeAmount,
                        onValueChange = { viewModel.updateAirtimeAmount(it) },
                        label         = { Text("Amount (RWF)") },
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(14.dp),
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors        = tossTextFieldColors()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick amount chips
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("500", "1,000", "2,000", "5,000").forEach { amt ->
                            val raw = amt.replace(",", "")
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Background)
                                    .clickable { viewModel.updateAirtimeAmount(raw) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(amt, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick  = { viewModel.buyAirtime() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor         = Primary,
                            disabledContainerColor = Primary.copy(alpha = 0.4f)
                        ),
                        enabled  = state.airtimePhone.length >= 9
                                && state.airtimeAmount.toDoubleOrNull()?.let { it > 0 } == true
                    ) {
                        Text("Buy airtime", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }

        state.error?.let { error ->
            item {
                Snackbar(
                    modifier          = Modifier.padding(16.dp),
                    containerColor    = NegativeRed,
                    contentColor      = Color.White
                ) { Text(error) }
            }
        }
    }
}

// ── Bill success ──────────────────────────────────────────────────────────
@Composable
private fun BillSuccessScreen(result: String, onDone: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(PositiveGreen.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Check, null, tint = PositiveGreen, modifier = Modifier.size(44.dp))
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text("Payment complete", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = TextPrimary)
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

// ── Provider item ─────────────────────────────────────────────────────────
@Composable
private fun ProviderItem(provider: BillProvider, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PrimaryLight),
            contentAlignment = Alignment.Center
        ) {
            Text(
                provider.name.take(2).uppercase(),
                fontWeight = FontWeight.Bold, color = Primary, fontSize = 15.sp
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            provider.name, fontSize = 11.sp, color = TextSecondary,
            maxLines = 1, textAlign = TextAlign.Center
        )
    }
}

// ── Pending bill row ──────────────────────────────────────────────────────
@Composable
private fun PendingBillRow(bill: PendingBill, onPay: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(NegativeRed.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Receipt, null, tint = NegativeRed, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(bill.providerName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
            Text("Due ${bill.dueDate}", fontSize = 12.sp, color = NegativeRed)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("%,.0f RWF".format(bill.amount), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Button(
            onClick       = onPay,
            shape         = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            modifier      = Modifier.height(36.dp),
            colors        = ButtonDefaults.buttonColors(containerColor = Primary)
        ) {
            Text("Pay", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun tossTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = Primary,
    unfocusedBorderColor = CardBorder,
    focusedLabelColor    = Primary,
    unfocusedLabelColor  = TextSecondary,
    cursorColor          = Primary
)
