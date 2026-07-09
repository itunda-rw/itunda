package rw.itunda.feature.banking.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.ids.IDS
import rw.itunda.core.designsystem.theme.Tds
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

data class Transaction(
    val id: String,
    val date: LocalDate,
    val title: String,
    val amount: Int,
    val isExpense: Boolean
)

// Mocked backend data (Rwandan context)
val mockTransactions = listOf(
    Transaction("1", LocalDate.now(), "MTN MoMo Transfer", 5000, true),
    Transaction("2", LocalDate.now(), "WASAC Water", 3000, true),
    Transaction("3", LocalDate.now().minusDays(1), "REG Electricity", 15000, true),
    Transaction("4", LocalDate.now().minusDays(2), "Salary Deposit", 150000, false),
    Transaction("5", LocalDate.now().minusDays(2), "Airtel Money", 2000, true)
)

@Composable
fun MySpendingScreen(onBack: () -> Unit = {}) {
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    val currentMonth = YearMonth.now()
    
    // Group transactions by date
    val transactionsByDate = remember {
        mockTransactions.groupBy { it.date }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tds.colors.background)
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Text("<", style = IDS.Typography.Title, color = Tds.colors.textPrimary)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "My Spending",
                style = IDS.Typography.Header,
                color = Tds.colors.textPrimary
            )
        }

        // Calendar Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(IDS.Shapes.Card)
                .background(Tds.colors.surface)
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${currentMonth.year}",
                    style = IDS.Typography.Title,
                    color = Tds.colors.textPrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Days of week header
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { day ->
                        Text(
                            text = day,
                            fontSize = 12.sp,
                            color = Tds.colors.textSecondary,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Calendar Grid
                val firstDayOfWeek = currentMonth.atDay(1).dayOfWeek.value % 7 // 0 = Sun
                val daysInMonth = currentMonth.lengthOfMonth()
                val totalCells = firstDayOfWeek + daysInMonth
                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier.height(260.dp),
                    userScrollEnabled = false
                ) {
                    items(totalCells) { index ->
                        if (index < firstDayOfWeek) {
                            Box(modifier = Modifier.aspectRatio(0.7f)) // Empty cell
                        } else {
                            val dayNumber = index - firstDayOfWeek + 1
                            val date = currentMonth.atDay(dayNumber)
                            val isSelected = selectedDate == date
                            val dailyTransactions = transactionsByDate[date] ?: emptyList()
                            val totalSpend = dailyTransactions.filter { it.isExpense }.sumOf { it.amount }
                            
                            Box(
                                modifier = Modifier
                                    .aspectRatio(0.7f)
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Tds.colors.brand.copy(alpha = 0.1f) else Color.Transparent)
                                    .clickable { selectedDate = date },
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text(
                                        text = dayNumber.toString(),
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Tds.colors.brand else Tds.colors.textPrimary
                                    )
                                    if (totalSpend > 0) {
                                        Text(
                                            text = "-${totalSpend / 1000}k",
                                            fontSize = 9.sp,
                                            color = Tds.colors.textSecondary,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Transaction List (Filtered by selected date, or showing all if none selected)
        val displayTransactions = if (selectedDate != null) {
            transactionsByDate[selectedDate] ?: emptyList()
        } else {
            mockTransactions
        }

        Text(
            text = if (selectedDate != null) "Transactions for ${selectedDate!!.dayOfMonth}" else "Recent Activity",
            style = IDS.Typography.Title,
            color = Tds.colors.textPrimary,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
        )
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            if (displayTransactions.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No spending on this day.", color = Tds.colors.textSecondary)
                    }
                }
            } else {
                items(displayTransactions) { tx ->
                    TransactionItem(
                        title = tx.title,
                        amount = (if (tx.isExpense) "-" else "+") + " RWF ${tx.amount}",
                        isPositive = !tx.isExpense
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

// The original ported file called this composable without ever defining it
// (a real gap, not something introduced here) -- added 2026-07-10 to make
// the screen actually compile and render, not just reference a name.
@Composable
private fun TransactionItem(title: String, amount: String, isPositive: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Tds.colors.surface)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = IDS.Typography.BodyBold, color = Tds.colors.textPrimary)
        Text(
            text = amount,
            style = IDS.Typography.BodyBold,
            color = if (isPositive) Tds.colors.success else Tds.colors.textPrimary
        )
    }
}
