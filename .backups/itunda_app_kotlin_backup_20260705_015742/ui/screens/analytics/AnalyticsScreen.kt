package com.itunda.app.ui.screens.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itunda.app.ui.theme.*

private data class Category(val label: String, val amount: Double, val color: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen() {
    val categories = remember {
        listOf(
            Category("Bills & utilities", 148_000.0, Primary),
            Category("Groceries", 96_500.0, PositiveGreen),
            Category("Transport", 54_200.0, Purple),
            Category("Airtime & data", 22_000.0, Teal),
            Category("Entertainment", 19_800.0, Gold),
        )
    }
    val total = categories.sumOf { it.amount }
    val monthly = remember { listOf(210_000.0, 265_000.0, 190_000.0, 340_500.0) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Background),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            TopAppBar(
                title = { Text("Spending analytics", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Spent this month", color = TextSecondary, fontSize = 13.sp)
                    Text(
                        "%,.0f RWF".format(total),
                        fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary
                    )
                    Spacer(Modifier.height(16.dp))
                    BarChart(monthly)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("Apr", "May", "Jun", "Jul").forEach {
                            Text(it, fontSize = 11.sp, color = TextTertiary)
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("By category", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                    Spacer(Modifier.height(14.dp))
                    categories.forEachIndexed { i, cat ->
                        CategoryRow(cat, cat.amount / total)
                        if (i < categories.lastIndex) Spacer(Modifier.height(14.dp))
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(PrimaryLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("AI", color = Primary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("You spent 18% more on bills than last month", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                        Text("Recommendation · Set a bills budget", fontSize = 12.sp, color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun BarChart(values: List<Double>) {
    val max = values.max()
    Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
        val barWidth = size.width / (values.size * 2)
        values.forEachIndexed { i, v ->
            val barHeight = (v / max * size.height).toFloat()
            val x = (i * 2 + 0.5f) * barWidth
            drawRoundRect(
                color = if (i == values.lastIndex) Primary else PrimaryLight,
                topLeft = Offset(x, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
            )
        }
    }
}

@Composable
private fun CategoryRow(category: Category, fraction: Double) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(category.color))
                Spacer(Modifier.width(8.dp))
                Text(category.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            }
            Text("%,.0f RWF".format(category.amount), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { fraction.toFloat() },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = category.color,
            trackColor = Background
        )
    }
}
