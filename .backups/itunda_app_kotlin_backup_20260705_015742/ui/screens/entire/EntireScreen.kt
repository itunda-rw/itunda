package com.itunda.app.ui.screens.entire

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itunda.app.ui.theme.*

private data class ServiceItem(
    val icon: ImageVector,
    val label: String,
    val subtitle: String,
    val isNew: Boolean = false,
    val onClick: () -> Unit = {}
)
private data class ServiceSection(val title: String, val items: List<ServiceItem>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntireScreen(
    onNavigateToPay: () -> Unit = {},
    onNavigateToBills: () -> Unit = {},
    onNavigateToStocks: () -> Unit = {},
    onNavigateToLoans: () -> Unit = {},
    onNavigateToSavings: () -> Unit = {},
    onNavigateToInsurance: () -> Unit = {},
    onNavigateToQR: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {}
) {
    val sections = listOf(
        ServiceSection("Money movement", listOf(
            ServiceItem(Icons.AutoMirrored.Outlined.Send,        "Send money",             "Phone, account, or contact", onClick = onNavigateToPay),
            ServiceItem(Icons.Outlined.QrCode,                   "QR code · Scan to pay",  "Merchant and P2P", onClick = onNavigateToQR),
            ServiceItem(Icons.Outlined.Hub,                      "Smart route via eKash",  "Bank and wallet fallback"),
            ServiceItem(Icons.Outlined.PhoneAndroid,             "Airtime top-up",         "MTN and Airtel", onClick = onNavigateToBills),
            ServiceItem(Icons.Outlined.Public,                   "International transfer", "Regional corridor concept", true),
        )),
        ServiceSection("Bills and services", listOf(
            ServiceItem(Icons.Outlined.Bolt,                     "REG electricity",   "Token and bill payments", onClick = onNavigateToBills),
            ServiceItem(Icons.Outlined.WaterDrop,                "WASAC water",       "Validate and pay", onClick = onNavigateToBills),
            ServiceItem(Icons.Outlined.AccountBalance,           "Irembo services",   "Government payments", onClick = onNavigateToBills),
            ServiceItem(Icons.AutoMirrored.Outlined.ReceiptLong, "RRA tax payments",  "References and receipts", onClick = onNavigateToBills),
        )),
        ServiceSection("Savings, credit & invest", listOf(
            ServiceItem(Icons.Outlined.Savings,                  "Savings goals",       "Auto-save and fixed deposit", onClick = onNavigateToSavings),
            ServiceItem(Icons.AutoMirrored.Outlined.TrendingUp,  "RSE stocks",          "Local market investing", onClick = onNavigateToStocks),
            ServiceItem(Icons.Outlined.Payments,                 "SME working capital", "Merchant and salary-backed", onClick = onNavigateToLoans),
            ServiceItem(Icons.Outlined.HealthAndSafety,          "Insurance",           "Motor, health, life, travel", onClick = onNavigateToInsurance),
        )),
        ServiceSection("Merchant", listOf(
            ServiceItem(Icons.Outlined.Store,          "Merchant dashboard",  "Sales, fees, and cash flow"),
            ServiceItem(Icons.Outlined.Face,           "Face Pay terminal",   "Biometric payment", true),
            ServiceItem(Icons.Outlined.PointOfSale,    "POS and payment links","In-store and online"),
            ServiceItem(Icons.Outlined.SyncAlt,        "Batch settlements",   "Payouts and reconciliation"),
        )),
        ServiceSection("Trust and identity", listOf(
            ServiceItem(Icons.Outlined.Fingerprint,    "National ID & KYC",  "Identity assurance", onClick = onNavigateToProfile),
            ServiceItem(Icons.Outlined.Security,       "Fraud review",       "Limits, alerts, disputes"),
            ServiceItem(Icons.Outlined.VerifiedUser,   "Account consent",    "Linked banks and wallets", onClick = onNavigateToProfile),
            ServiceItem(Icons.Outlined.SupportAgent,   "Help center",        "Failed transfer support", onClick = onNavigateToProfile),
        )),
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            TopAppBar(
                title  = { Text("All", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }

        // ── Profile strip ──────────────────────────────────────────────
        item {
            Card(
                modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape     = RoundedCornerShape(20.dp),
                colors    = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToProfile)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(PrimaryLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Person, null, tint = Primary, modifier = Modifier.size(26.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("My Account", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                        Text("Profile · Security · Notifications", fontSize = 12.sp, color = TextSecondary)
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = TextTertiary)
                }
            }
        }

        // ── Service sections ───────────────────────────────────────────
        sections.forEach { section ->
            item {
                Card(
                    modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape     = RoundedCornerShape(20.dp),
                    colors    = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Text(
                            section.title,
                            color      = TextSecondary,
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.3.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        section.items.forEachIndexed { i, item ->
                            ServiceRow(item)
                            if (i < section.items.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Divider)
                            }
                        }
                    }
                }
            }
        }

        // ── App version footer ─────────────────────────────────────────
        item {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("itunda v1.0 · Rwanda", fontSize = 12.sp, color = TextTertiary)
            }
        }
    }
}

@Composable
private fun ServiceRow(item: ServiceItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = item.onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PrimaryLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(item.icon, null, tint = Primary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                if (item.isNew) {
                    Surface(shape = RoundedCornerShape(5.dp), color = Primary.copy(alpha = 0.1f)) {
                        Text(
                            "NEW", color = Primary, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(item.subtitle, fontSize = 12.sp, color = TextSecondary)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = TextTertiary, modifier = Modifier.size(18.dp))
    }
}
