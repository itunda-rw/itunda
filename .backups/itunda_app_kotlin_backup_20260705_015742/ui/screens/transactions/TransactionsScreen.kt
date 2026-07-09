package com.itunda.app.ui.screens.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itunda.app.data.models.Transaction
import com.itunda.app.ui.theme.*

private val mockTransactions = listOf(
    Transaction("1", "debit",  "Jean Baptiste", "P2P transfer",       -15_000.0, "Today, 14:22",  "completed", null, "Jean Baptiste"),
    Transaction("2", "credit", "Salary deposit", "Bank of Kigali",      450_000.0, "Today, 09:00",  "completed", "Bank of Kigali", null),
    Transaction("3", "debit",  "REG electricity","Bill payment",        -22_400.0, "Yesterday, 18:10", "completed", null, "REG"),
    Transaction("4", "debit",  "MTN airtime",   "Top-up",                -2_000.0, "Yesterday, 12:05", "completed", null, "MTN"),
    Transaction("5", "credit", "Marie Claire",  "Received transfer",     30_000.0, "Jul 3, 09:41",  "completed", "Marie Claire", null),
    Transaction("6", "debit",  "Simba Supermarket", "QR payment",       -18_750.0, "Jul 2, 17:30",  "completed", null, "Simba Supermarket"),
    Transaction("7", "debit",  "WASAC water",   "Bill payment",          -8_200.0, "Jul 1, 08:15",  "completed", null, "WASAC"),
    Transaction("8", "credit", "Cashback reward", "Itunda rewards",       1_250.0, "Jun 30, 20:00", "completed", "Itunda", null),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen() {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }

    val filtered = mockTransactions
        .filter { filter == "All" || (filter == "In" && it.type == "credit") || (filter == "Out" && it.type == "debit") }
        .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }

    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        TopAppBar(
            title = { Text("Transactions", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search transactions") },
            leadingIcon = { Icon(Icons.Outlined.Search, null, tint = TextSecondary) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Primary,
                unfocusedBorderColor = CardBorder,
            ),
            keyboardOptions = KeyboardOptions.Default
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "In", "Out").forEach { f ->
                val selected = filter == f
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) Primary else Color.White)
                        .clickable { filter = f }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(f, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else TextSecondary)
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                Text("No transactions found", color = TextTertiary, fontSize = 14.sp)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                            filtered.forEachIndexed { i, txn ->
                                TxnRow(txn)
                                if (i < filtered.lastIndex) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Divider)
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
private fun TxnRow(txn: Transaction) {
    val isCredit = txn.type == "credit"
    val iconColor = if (isCredit) PositiveGreen else NegativeRed
    val icon = if (isCredit) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward

    Row(modifier = Modifier.fillMaxWidth().clickable {}, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(42.dp).clip(CircleShape).background(iconColor.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(txn.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
            Text("${txn.description} · ${txn.date}", fontSize = 12.sp, color = TextSecondary)
        }
        Text(
            text = "${if (isCredit) "+" else ""}%,.0f RWF".format(txn.amount),
            fontWeight = FontWeight.Bold, fontSize = 14.sp,
            color = if (isCredit) PositiveGreen else TextPrimary
        )
    }
}
