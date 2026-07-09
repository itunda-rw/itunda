package com.itunda.app.ui.screens.stocks

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
import com.itunda.app.data.models.Stock
import com.itunda.app.data.models.StockHolding
import com.itunda.app.ui.theme.*
import com.itunda.app.viewmodel.StocksViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StocksScreen() {
    val viewModel: StocksViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // ── Order success ──────────────────────────────────────────────────────
    state.orderResult?.let { result ->
        TradeSuccessScreen(result) { viewModel.clearResult() }
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
                title   = { Text("Invest", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                actions = {
                    IconButton(onClick = { viewModel.loadData() }) {
                        Icon(Icons.Outlined.Refresh, null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }

        // ── Portfolio card ─────────────────────────────────────────────────
        item {
            Card(
                modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape     = RoundedCornerShape(20.dp),
                colors    = CardDefaults.cardColors(containerColor = Primary),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Portfolio value", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    if (state.holdings.isNotEmpty()) {
                        Text(
                            "%,.0f RWF".format(state.totalValue),
                            fontSize   = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color      = Color.White,
                            letterSpacing = (-1).sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val isPositive = state.totalReturn >= 0
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = (if (isPositive) PositiveGreen else NegativeRed).copy(alpha = 0.25f)
                        ) {
                            Text(
                                "${if (isPositive) "+" else ""}%,.0f RWF  (${String.format("%.1f", state.totalReturnPercent)}%)".format(state.totalReturn),
                                color      = if (isPositive) Color(0xFFAAFFCC) else Color(0xFFFFAABB),
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier   = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    } else {
                        Text(
                            "0 RWF",
                            fontSize   = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color      = Color.White,
                            letterSpacing = (-1).sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Start investing in Rwanda stocks", color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
                    }
                }
            }
        }

        // ── My holdings ────────────────────────────────────────────────────
        if (state.holdings.isNotEmpty()) {
            item {
                Card(
                    modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape     = RoundedCornerShape(20.dp),
                    colors    = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("My holdings", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                        Spacer(modifier = Modifier.height(14.dp))
                        state.holdings.forEachIndexed { i, holding ->
                            HoldingRow(holding) { viewModel.sellStock(holding.stockId) }
                            if (i < state.holdings.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Divider)
                            }
                        }
                    }
                }
            }
        }

        // ── Buy sheet inline ───────────────────────────────────────────────
        if (state.selectedStock != null) {
            item {
                BuyCard(
                    stock         = state.selectedStock!!,
                    shares        = state.orderShares,
                    onSharesChange = { viewModel.updateOrderShares(it) },
                    onBuy         = { viewModel.buyStock() },
                    onDismiss     = { viewModel.clearResult() }
                )
            }
        }

        // ── RSE market ─────────────────────────────────────────────────────
        item {
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
                        Text("RSE market", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                        Text("Rwanda Stock Exchange", fontSize = 12.sp, color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    state.stocks.forEachIndexed { i, stock ->
                        StockRow(stock) { viewModel.selectStock(stock) }
                        if (i < state.stocks.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Divider)
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
private fun TradeSuccessScreen(result: String, onDone: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Box(
                modifier = Modifier.size(80.dp).clip(CircleShape).background(PositiveGreen.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Check, null, tint = PositiveGreen, modifier = Modifier.size(44.dp))
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text("Order placed!", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = TextPrimary)
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

// ── Stock row ─────────────────────────────────────────────────────────────
@Composable
private fun StockRow(stock: Stock, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(PrimaryLight),
            contentAlignment = Alignment.Center
        ) {
            Text(stock.symbol.take(3), fontWeight = FontWeight.Bold, color = Primary, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(stock.symbol, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
            Text(stock.name, fontSize = 12.sp, color = TextSecondary, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("%,.0f".format(stock.price), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
            val isPos = stock.change >= 0
            Text(
                "${if (isPos) "+" else ""}${String.format("%.1f", stock.changePercent)}%",
                fontSize   = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color      = if (isPos) PositiveGreen else NegativeRed
            )
        }
    }
}

// ── Holding row ───────────────────────────────────────────────────────────
@Composable
private fun HoldingRow(holding: StockHolding, onSell: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(SecondaryLight),
            contentAlignment = Alignment.Center
        ) {
            Text(holding.symbol.take(3), fontWeight = FontWeight.Bold, color = Secondary, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(holding.symbol, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
            Text("${holding.shares} shares @ ${"%,.0f".format(holding.avgPrice)}", fontSize = 12.sp, color = TextSecondary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("%,.0f RWF".format(holding.totalValue), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            val isPos = holding.totalReturn >= 0
            Text(
                "${if (isPos) "+" else ""}${String.format("%.1f", holding.returnPercent)}%",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color    = if (isPos) PositiveGreen else NegativeRed
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        OutlinedButton(
            onClick       = onSell,
            shape         = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            modifier      = Modifier.height(34.dp),
            border        = BorderStroke(1.dp, NegativeRed.copy(alpha = 0.5f)),
            colors        = ButtonDefaults.outlinedButtonColors(contentColor = NegativeRed)
        ) {
            Text("Sell", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ── Buy card ──────────────────────────────────────────────────────────────
@Composable
private fun BuyCard(
    stock: Stock,
    shares: String,
    onSharesChange: (String) -> Unit,
    onBuy: () -> Unit,
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
                Text("Buy ${stock.symbol}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
            Text("%,.0f RWF / share".format(stock.price), color = TextSecondary, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value         = shares,
                onValueChange = onSharesChange,
                label         = { Text("Number of shares") },
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(14.dp),
                singleLine    = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary, unfocusedBorderColor = CardBorder,
                    focusedLabelColor  = Primary, cursorColor = Primary
                )
            )

            shares.toIntOrNull()?.let { count ->
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(PrimaryLight)
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total cost", color = Primary, fontSize = 14.sp)
                    Text(
                        "%,.0f RWF".format(count * stock.price),
                        fontWeight = FontWeight.Bold, color = Primary, fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick  = onBuy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = Primary,
                    disabledContainerColor = Primary.copy(alpha = 0.4f)
                ),
                enabled  = shares.toIntOrNull()?.let { it > 0 } == true
            ) {
                Text("Buy", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
