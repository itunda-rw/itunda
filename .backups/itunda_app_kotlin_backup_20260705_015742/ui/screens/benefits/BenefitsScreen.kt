package com.itunda.app.ui.screens.benefits

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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

private data class BenefitItem(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val color: Color,
    val badge: String? = null
)

private val benefits = listOf(
    BenefitItem(Icons.Filled.DirectionsWalk, "itunda Walk",     "Earn rewards for every step",         PositiveGreen, "NEW"),
    BenefitItem(Icons.Filled.Star,           "itunda Prime",    "6% cashback on all payments",         Gold,          "NEW"),
    BenefitItem(Icons.Filled.CardGiftcard,   "Daily tasks",     "Complete tasks for bonuses",          Purple,        "NEW"),
    BenefitItem(Icons.Filled.LocalOffer,     "Coupons & deals", "Exclusive partner discounts",         NegativeRed),
    BenefitItem(Icons.Filled.EmojiEvents,    "Referral rewards","Earn 5,000 RWF per referral",         Orange),
    BenefitItem(Icons.Filled.Security,       "Insurance",       "Smart coverage for Rwanda",           Primary),
    BenefitItem(Icons.Filled.Savings,        "Savings goals",   "Auto-save with round-ups",            Secondary),
    BenefitItem(Icons.Filled.AccountBalance, "Ejo Heza",        "Long-term savings & pension",         Color(0xFF795548)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenefitsScreen() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            TopAppBar(
                title  = { Text("Benefits", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }

        // ── Prime banner ────────────────────────────────────────────────
        item {
            Card(
                modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape     = RoundedCornerShape(20.dp),
                colors    = CardDefaults.cardColors(containerColor = Gold),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.White.copy(alpha = 0.25f)
                        ) {
                            Text(
                                "PRIME",
                                color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("6% cashback", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = Color.White)
                        Text("on every payment, unlimited", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {},
                            shape   = RoundedCornerShape(10.dp),
                            colors  = ButtonDefaults.buttonColors(containerColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                        ) {
                            Text("Subscribe · 5,000 RWF/mo", color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(Icons.Filled.Star, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(64.dp))
                }
            }
        }

        // ── itunda Walk ─────────────────────────────────────────────────
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(PositiveGreen.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.DirectionsWalk, null, tint = PositiveGreen, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("itunda Walk", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Text("Today", color = TextSecondary, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        WalkStat("0", "Steps", PositiveGreen)
                        Box(modifier = Modifier.width(1.dp).height(48.dp).background(Divider))
                        WalkStat("0 RWF", "Earned", PositiveGreen)
                        Box(modifier = Modifier.width(1.dp).height(48.dp).background(Divider))
                        WalkStat("0 / 10K", "Goal", TextSecondary)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress   = { 0f },
                        modifier   = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                        color      = PositiveGreen,
                        trackColor = Background
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick  = {},
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = PositiveGreen)
                    ) {
                        Text("Claim 0 RWF", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ── All benefits list ────────────────────────────────────────────
        item {
            Card(
                modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape     = RoundedCornerShape(20.dp),
                colors    = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("All benefits", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(14.dp))
                    benefits.forEachIndexed { i, benefit ->
                        BenefitRow(benefit)
                        if (i < benefits.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Divider)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WalkStat(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = color)
        Text(label, fontSize = 11.sp, color = TextSecondary)
    }
}

@Composable
private fun BenefitRow(benefit: BenefitItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(benefit.color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(benefit.icon, null, tint = benefit.color, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(benefit.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                benefit.badge?.let { badge ->
                    Surface(shape = RoundedCornerShape(5.dp), color = Primary.copy(alpha = 0.1f)) {
                        Text(
                            badge, color = Primary, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(benefit.description, fontSize = 12.sp, color = TextSecondary)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = TextTertiary, modifier = Modifier.size(18.dp))
    }
}
