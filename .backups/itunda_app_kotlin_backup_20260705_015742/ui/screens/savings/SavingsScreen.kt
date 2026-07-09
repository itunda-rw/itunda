package com.itunda.app.ui.screens.savings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itunda.app.ui.theme.*

private data class SavingsGoal(
    val icon: ImageVector,
    val name: String,
    val saved: Double,
    val target: Double,
    val color: Color
)

private data class FixedDeposit(
    val term: String,
    val rate: String,
    val minAmount: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsScreen(onBack: () -> Unit = {}) {
    val goals = remember {
        listOf(
            SavingsGoal(Icons.Outlined.Flight, "Kigali → Dubai trip", 185_000.0, 600_000.0, Primary),
            SavingsGoal(Icons.Outlined.PhoneIphone, "New phone", 92_000.0, 450_000.0, Purple),
            SavingsGoal(Icons.Outlined.School, "School fees", 310_000.0, 400_000.0, Teal),
        )
    }
    val deposits = remember {
        listOf(
            FixedDeposit("3 months", "8.5% p.a.", "50,000 RWF"),
            FixedDeposit("6 months", "10.2% p.a.", "50,000 RWF"),
            FixedDeposit("12 months", "12.8% p.a.", "100,000 RWF"),
        )
    }
    val totalSaved = goals.sumOf { it.saved }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Background),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            TopAppBar(
                title = { Text("Savings", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }

        // Summary hero
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Total saved across goals", color = TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "%,.0f RWF".format(totalSaved),
                        fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New savings goal", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Goals
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Your goals", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                    Spacer(Modifier.height(14.dp))
                    goals.forEachIndexed { i, goal ->
                        GoalRow(goal)
                        if (i < goals.lastIndex) Spacer(Modifier.height(18.dp))
                    }
                }
            }
        }

        // Fixed deposits
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Fixed deposits", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                    Text("Lock savings for a guaranteed return", fontSize = 12.sp, color = TextSecondary)
                    Spacer(Modifier.height(14.dp))
                    deposits.forEachIndexed { i, d ->
                        DepositRow(d)
                        if (i < deposits.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Divider)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalRow(goal: SavingsGoal) {
    val progress = (goal.saved / goal.target).toFloat().coerceIn(0f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(42.dp).clip(CircleShape).background(goal.color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(goal.icon, null, tint = goal.color, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(goal.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                Text("${(progress * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = goal.color)
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = goal.color,
                trackColor = Background
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "%,.0f / %,.0f RWF".format(goal.saved, goal.target),
                fontSize = 11.sp, color = TextSecondary
            )
        }
    }
}

@Composable
private fun DepositRow(deposit: FixedDeposit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(deposit.term, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
            Text("Min ${deposit.minAmount}", fontSize = 12.sp, color = TextSecondary)
        }
        Surface(shape = RoundedCornerShape(8.dp), color = PositiveGreen.copy(alpha = 0.1f)) {
            Text(
                deposit.rate, color = PositiveGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Outlined.ChevronRight, null, tint = TextTertiary)
    }
}
