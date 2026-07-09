package com.itunda.app.ui.screens.pay

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.itunda.app.data.models.Contact
import com.itunda.app.ui.theme.*
import com.itunda.app.viewmodel.PayViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayScreen() {
    val viewModel: PayViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val favorites = state.contacts.filter { it.isFavorite }

    // ── Success overlay ────────────────────────────────────────────────────
    state.sendResult?.let { result ->
        SendSuccessSheet(result = result, onDone = { viewModel.clearResult() })
        return
    }

    // ── Contact selected → numpad screen ──────────────────────────────────
    if (state.selectedContact != null) {
        NumpadSendScreen(
            contact  = state.selectedContact!!,
            amount   = state.amount,
            isSending = state.isSending,
            onKey    = { key ->
                val current = state.amount
                viewModel.updateAmount(
                    when (key) {
                        "⌫" -> if (current.isNotEmpty()) current.dropLast(1) else ""
                        "." -> if ("." in current) current else "$current."
                        else -> if (current == "0") key else current + key
                    }
                )
            },
            onSend   = { viewModel.sendMoney() },
            onBack   = { viewModel.clearResult(); viewModel.updateAmount("") }
        )
        return
    }

    // ── Contact picker ─────────────────────────────────────────────────────
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        item {
            TopAppBar(
                title  = { Text("Send money", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }

        // Transfer method chips
        item {
            Card(
                modifier  = Modifier.fillMaxWidth().padding(16.dp),
                shape     = RoundedCornerShape(20.dp),
                colors    = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SendMethodItem(Icons.Outlined.Person,         "To contact")
                    SendMethodItem(Icons.Outlined.AccountBalance, "To bank")
                    SendMethodItem(Icons.Outlined.PhoneAndroid,   "To mobile")
                    SendMethodItem(Icons.Outlined.QrCodeScanner,  "QR scan")
                }
            }
        }

        // Favorites
        if (favorites.isNotEmpty()) {
            item {
                Card(
                    modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape     = RoundedCornerShape(20.dp),
                    colors    = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Favorites", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                        Spacer(modifier = Modifier.height(14.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            items(favorites) { contact ->
                                FavoriteContactItem(contact) { viewModel.selectContact(contact) }
                            }
                        }
                    }
                }
            }
        }

        // All contacts
        item {
            Card(
                modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape     = RoundedCornerShape(20.dp),
                colors    = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Recent contacts", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(12.dp))
                    state.contacts.forEachIndexed { i, contact ->
                        ContactRow(contact) { viewModel.selectContact(contact) }
                        if (i < state.contacts.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Divider)
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

// ── Numpad send screen ────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NumpadSendScreen(
    contact: Contact,
    amount: String,
    isSending: Boolean,
    onKey: (String) -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopAppBar(
            title       = {},
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Avatar + name
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(PrimaryLight),
            contentAlignment = Alignment.Center
        ) {
            Text(
                contact.name.take(2).uppercase(),
                fontWeight = FontWeight.Bold,
                fontSize   = 22.sp,
                color      = Primary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(contact.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
        Text(contact.phone, fontSize = 13.sp, color = TextSecondary)

        Spacer(modifier = Modifier.height(32.dp))

        // Amount display
        val displayAmount = if (amount.isEmpty()) "0" else amount
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text       = displayAmount,
                fontSize   = if (displayAmount.length > 8) 36.sp else 48.sp,
                fontWeight = FontWeight.Bold,
                color      = if (amount.isEmpty()) TextTertiary else TextPrimary,
                letterSpacing = (-1.5).sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("RWF", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = TextSecondary,
                modifier = Modifier.padding(bottom = 8.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        // Numpad
        val keys = listOf("1","2","3","4","5","6","7","8","9",".","0","⌫")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            keys.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    row.forEach { key ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (key == "⌫") Color.Transparent else Background)
                                .clickable { onKey(key) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (key == "⌫") {
                                Icon(Icons.AutoMirrored.Filled.Backspace, null, tint = TextPrimary, modifier = Modifier.size(22.dp))
                            } else {
                                Text(key, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick  = onSend,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 24.dp),
            shape  = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Primary,
                disabledContainerColor = Primary.copy(alpha = 0.4f)
            ),
            enabled = amount.toDoubleOrNull()?.let { it > 0 } == true && !isSending
        ) {
            if (isSending) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("Send", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

// ── Success screen ────────────────────────────────────────────────────────
@Composable
private fun SendSuccessSheet(result: String, onDone: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
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
            Text("Sent!", fontWeight = FontWeight.Bold, fontSize = 28.sp, color = TextPrimary)
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

// ── Supporting composables ─────────────────────────────────────────────────
@Composable
private fun SendMethodItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable {}
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PrimaryLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = Primary, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FavoriteContactItem(contact: Contact, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(PrimaryLight),
            contentAlignment = Alignment.Center
        ) {
            Text(contact.name.take(2).uppercase(), fontWeight = FontWeight.Bold, color = Primary, fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(contact.name, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
    }
}

@Composable
private fun ContactRow(contact: Contact, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(PrimaryLight),
            contentAlignment = Alignment.Center
        ) {
            Text(contact.name.take(2).uppercase(), fontWeight = FontWeight.Bold, color = Primary, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(contact.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextPrimary)
            Text(contact.phone, fontSize = 13.sp, color = TextSecondary)
        }
        if (contact.recentAmount != null) {
            Text("%,.0f RWF".format(contact.recentAmount), fontSize = 13.sp, color = TextTertiary)
        }
    }
}
