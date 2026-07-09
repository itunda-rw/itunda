package com.itunda.app.ui.screens.insurance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itunda.app.ui.theme.*

private data class Policy(
    val icon: ImageVector,
    val name: String,
    val insurer: String,
    val premium: String,
    val status: String,
    val color: Color
)

private data class Product(
    val icon: ImageVector,
    val name: String,
    val blurb: String,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsuranceScreen() {
    val activePolicies = remember {
        listOf(
            Policy(Icons.Outlined.DirectionsCar, "Motor cover", "AAR Insurance", "24,500 RWF/mo", "Active", Primary),
        )
    }
    val products = remember {
        listOf(
            Product(Icons.Outlined.DirectionsCar, "Motor insurance", "Third-party or comprehensive vehicle cover", Primary),
            Product(Icons.Outlined.HealthAndSafety, "Health insurance", "Outpatient and inpatient medical cover", PositiveGreen),
            Product(Icons.Outlined.Home, "Home insurance", "Fire, theft, and property damage", Purple),
            Product(Icons.Outlined.Flight, "Travel insurance", "Trip cancellation and medical abroad", Teal),
            Product(Icons.Outlined.Favorite, "Life insurance", "Term life and family protection", NegativeRed),
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Background),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            TopAppBar(
                title = { Text("Insurance", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }

        if (activePolicies.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Your policies", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                        Spacer(Modifier.height(14.dp))
                        activePolicies.forEach { PolicyRow(it) }
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
                    Text("Marketplace", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                    Text("Compare cover from local insurers", fontSize = 12.sp, color = TextSecondary)
                    Spacer(Modifier.height(14.dp))
                    products.forEachIndexed { i, p ->
                        ProductRow(p)
                        if (i < products.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Divider)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PolicyRow(policy: Policy) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(policy.color.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(policy.icon, null, tint = policy.color, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(policy.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
            Text(policy.insurer, fontSize = 12.sp, color = TextSecondary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(policy.premium, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
            Surface(shape = RoundedCornerShape(6.dp), color = PositiveGreen.copy(alpha = 0.1f)) {
                Text(
                    policy.status, color = PositiveGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun ProductRow(product: Product) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(product.color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(product.icon, null, tint = product.color, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(product.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
            Text(product.blurb, fontSize = 12.sp, color = TextSecondary)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = TextTertiary, modifier = Modifier.size(18.dp))
    }
}
