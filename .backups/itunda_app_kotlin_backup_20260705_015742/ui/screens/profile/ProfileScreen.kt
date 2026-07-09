package com.itunda.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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

private data class SettingItem(val icon: ImageVector, val label: String, val subtitle: String)
private data class SettingSection(val title: String, val items: List<SettingItem>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onLogout: () -> Unit) {
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log out", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to log out?", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { showLogoutDialog = false; onLogout() }) {
                    Text("Log out", color = NegativeRed, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel", color = TextSecondary) }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    val sections = listOf(
        SettingSection("Identity", listOf(
            SettingItem(Icons.Outlined.Fingerprint, "National ID KYC", "Verified · Tier 2"),
            SettingItem(Icons.Outlined.VerifiedUser, "Linked accounts", "MTN Money, Bank of Kigali"),
        )),
        SettingSection("Security", listOf(
            SettingItem(Icons.Outlined.Lock, "Login PIN", "Change 6-digit PIN"),
            SettingItem(Icons.Outlined.Fingerprint, "Biometric login", "Face/fingerprint unlock enabled"),
            SettingItem(Icons.Outlined.DevicesOther, "Device management", "1 active device"),
        )),
        SettingSection("Preferences", listOf(
            SettingItem(Icons.Outlined.Notifications, "Notifications", "Push, SMS, email"),
            SettingItem(Icons.Outlined.Language, "Language", "English"),
            SettingItem(Icons.Outlined.DarkMode, "Appearance", "Light"),
        )),
        SettingSection("Support", listOf(
            SettingItem(Icons.Outlined.SupportAgent, "Help center", "Failed transfer, disputes, refunds"),
            SettingItem(Icons.Outlined.Description, "Terms & privacy", "Legal documents"),
        )),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Background),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            TopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
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
                Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(56.dp).clip(CircleShape).background(PrimaryLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Person, null, tint = Primary, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Uwase Diane", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextPrimary)
                        Text("+250 788 123 456", fontSize = 13.sp, color = TextSecondary)
                        Spacer(Modifier.height(4.dp))
                        Surface(shape = RoundedCornerShape(6.dp), color = PositiveGreen.copy(alpha = 0.1f)) {
                            Text(
                                "KYC verified", color = PositiveGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = TextTertiary)
                }
            }
        }

        sections.forEach { section ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Text(
                            section.title, color = TextSecondary, fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        section.items.forEachIndexed { i, item ->
                            SettingRow(item)
                            if (i < section.items.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Divider)
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable { showLogoutDialog = true },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("Log out", color = NegativeRed, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }

        item {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text("itunda v1.0 · Rwanda", fontSize = 12.sp, color = TextTertiary)
            }
        }
    }
}

@Composable
private fun SettingRow(item: SettingItem) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(PrimaryLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(item.icon, null, tint = Primary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
            Text(item.subtitle, fontSize = 12.sp, color = TextSecondary)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = TextTertiary, modifier = Modifier.size(18.dp))
    }
}
