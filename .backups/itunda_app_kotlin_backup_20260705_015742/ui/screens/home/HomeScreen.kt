package com.itunda.app.ui.screens.home

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.itunda.app.data.models.Transaction
import com.itunda.app.data.models.Wallet
import com.itunda.app.ui.theme.*
import com.itunda.app.viewmodel.HomeViewModel
// ── Data ──────────────────────────────────────────────────────────────────
data class QuickAction(val icon: ImageVector, val label: String, val color: Color, val onClick: () -> Unit)

// ── Root ──────────────────────────────────────────────────────────────────
@Composable
fun HomeScreen(
    onNavigateToPay: () -> Unit,
    onNavigateToBills: () -> Unit,
    onNavigateToStocks: () -> Unit,
    onNavigateToLoans: () -> Unit,
    onNavigateToQR: () -> Unit = {},
    onNavigateToTransactions: () -> Unit = {},
    onNavigateToAnalytics: () -> Unit = {},
    onNavigateToSavings: () -> Unit = {},
    onNavigateToInsurance: () -> Unit = {},
    onLogout: () -> Unit
) {
    val viewModel: HomeViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var balanceVisible by remember { mutableStateOf(true) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log out", fontWeight = FontWeight.Bold) },
            text  = { Text("Are you sure you want to log out?", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { showLogoutDialog = false; onLogout() }) {
                    Text("Log out", color = NegativeRed, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // 1. Top bar
        item { TopBar(onLogout = { showLogoutDialog = true }) }

        // 2. Balance hero card
        item {
            BalanceCard(
                totalBalance   = state.totalBalance,
                balanceVisible = balanceVisible,
                onToggleVisibility = { balanceVisible = !balanceVisible },
                wallets        = state.wallets
            )
        }

        // 3. Quick actions
        item { QuickActionsSection(onNavigateToPay, onNavigateToQR, onNavigateToBills, onNavigateToSavings, onNavigateToStocks, onNavigateToLoans) }

        // 4. Rwanda rails card
        item { RwandaRailsCard(onNavigateToPay, onNavigateToBills) }

        // 5. Spending summary
        item { SpendingCard(onClick = onNavigateToAnalytics) }

        // 6. Credit score
        item { CreditScoreCard(state.creditScore) }

        // 7. Shortcut grid
        item { ShortcutGrid(onNavigateToStocks, onNavigateToLoans, onNavigateToInsurance, onNavigateToSavings) }

        // 8. Transactions
        item { RecentTransactionsSection(state.transactions, onSeeAll = onNavigateToTransactions) }
    }
}

// ── Top bar ───────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(onLogout: () -> Unit) {
    // Toss home header: logo left, bell + avatar right. White background.
    TopAppBar(
        title = {
            Text(
                "itunda",
                fontWeight    = FontWeight.ExtraBold,
                fontSize      = 22.sp,
                color         = Primary,
                letterSpacing = (-0.5).sp,
                fontFamily    = InterFamily
            )
        },
        actions = {
            // Notification bell with mock dot
            Box {
                IconButton(onClick = {}) {
                    Icon(Icons.Outlined.Notifications, null, tint = TextPrimary)
                }
                // Red notification dot — exactly like Toss
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(NegativeRed)
                        .align(Alignment.TopEnd)
                        .offset(x = (-10).dp, y = 10.dp)
                )
            }
            // Profile circle
            IconButton(onClick = onLogout) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(PrimaryLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Person, null,
                        tint     = Primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
    )
}

// ── Balance hero card ─────────────────────────────────────────────────────
@Composable
private fun BalanceCard(
    totalBalance: Double,
    balanceVisible: Boolean,
    onToggleVisibility: () -> Unit,
    wallets: List<Wallet>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape  = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Total assets", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                IconButton(
                    onClick  = onToggleVisibility,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (balanceVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        contentDescription = null,
                        tint     = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Big balance
            if (balanceVisible) {
                Text(
                    text       = "%,.0f RWF".format(totalBalance),
                    fontSize   = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color      = TextPrimary,
                    letterSpacing = (-1).sp
                )
            } else {
                Text(
                    text       = "•••••• RWF",
                    fontSize   = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color      = TextPrimary,
                    letterSpacing = (-1).sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Change pill (mock positive)
            Surface(
                shape = RoundedCornerShape(50),
                color = PositiveGreen.copy(alpha = 0.1f),
                modifier = Modifier.wrapContentWidth()
            ) {
                Text(
                    text     = "+ 12,400 RWF this month",
                    color    = PositiveGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Divider)

            // Wallet rows
            val displayWallets = wallets.ifEmpty {
                listOf(
                    Wallet("1", "wallet", "itunda Wallet", "RWF", totalBalance * 0.6, "RWF", "I", true),
                    Wallet("2", "bank",   "Bank of Kigali", "BK", totalBalance * 0.4, "RWF", "B", false)
                )
            }
            displayWallets.take(3).forEachIndexed { i, wallet ->
                WalletRow(wallet)
                if (i < displayWallets.take(3).lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Divider)
                }
            }
        }
    }
}

// ── Wallet row ────────────────────────────────────────────────────────────
@Composable
private fun WalletRow(wallet: Wallet) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (wallet.type == "bank") PrimaryLight else SecondaryLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (wallet.type == "bank") Icons.Outlined.AccountBalance else Icons.Outlined.AccountBalanceWallet,
                contentDescription = null,
                tint     = if (wallet.type == "bank") Primary else Secondary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(wallet.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
            Text(wallet.number, fontSize = 12.sp, color = TextSecondary)
        }
        Text(
            text       = "%,.0f RWF".format(wallet.balance),
            fontWeight = FontWeight.Bold,
            fontSize   = 15.sp,
            color      = TextPrimary
        )
    }
}

// ── Quick actions ─────────────────────────────────────────────────────────
@Composable
private fun QuickActionsSection(
    onPay: () -> Unit, onQR: () -> Unit, onBills: () -> Unit, onSavings: () -> Unit, onStocks: () -> Unit, onLoans: () -> Unit
) {
    val actions = listOf(
        QuickAction(Icons.AutoMirrored.Outlined.Send,       "Send",    Primary,       onPay),
        QuickAction(Icons.Outlined.QrCodeScanner,   "QR Pay",  PositiveGreen, onQR),
        QuickAction(Icons.Outlined.Receipt,         "Bills",   Purple,        onBills),
        QuickAction(Icons.Outlined.Savings,         "Save",    Teal,          onSavings),
        QuickAction(Icons.AutoMirrored.Outlined.TrendingUp, "Invest",  Orange,        onStocks),
        QuickAction(Icons.Outlined.AccountBalance,  "Loans",   Gold,          onLoans),
    )

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            actions.forEach { action ->
                QuickActionItem(action)
            }
        }
    }
}

@Composable
private fun QuickActionItem(action: QuickAction) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = action.onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)                          // ← perfect circle like Toss
                .background(action.color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(action.icon, action.label, tint = action.color, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.height(7.dp))
        Text(action.label, fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
    }
}

// ── Rwanda rails ──────────────────────────────────────────────────────────
@Composable
private fun RwandaRailsCard(onPay: () -> Unit, onBills: () -> Unit) {
    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Rwanda rails", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
            Spacer(modifier = Modifier.height(14.dp))

            val rails = listOf(
                Triple(Icons.Outlined.PhoneAndroid,    "Mobile money",    "MTN + Airtel"),
                Triple(Icons.Outlined.QrCode,          "Merchant QR",     "Scan & pay"),
                Triple(Icons.Outlined.AccountBalance,  "Bank routing",    "eKash rails"),
                Triple(Icons.AutoMirrored.Outlined.ReceiptLong, "Public services", "Irembo · RRA"),
            )

            rails.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { (icon, title, subtitle) ->
                        val isPayAction = title != "Public services"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Background)
                                .clickable(onClick = if (isPayAction) onPay else onBills)
                                .padding(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(icon, title, tint = Primary, modifier = Modifier.size(22.dp))
                                Text(subtitle, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

// ── Spending ──────────────────────────────────────────────────────────────
@Composable
private fun SpendingCard(onClick: () -> Unit) {
    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(NegativeRed.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.CreditCard, null, tint = NegativeRed, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Spent this month", color = TextSecondary, fontSize = 13.sp)
                Text("340,500 RWF", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPrimary)
                Text("Upcoming bill · 120,000 RWF", color = TextTertiary, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Surface(shape = RoundedCornerShape(8.dp), color = NegativeRed.copy(alpha = 0.08f)) {
                    Text("D-3", color = NegativeRed, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }
    }
}

// ── Credit score ──────────────────────────────────────────────────────────
@Composable
private fun CreditScoreCard(score: Int) {
    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {}
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val scoreColor = when {
                score >= 700 -> PositiveGreen
                score >= 500 -> WarningAmber
                score > 0    -> NegativeRed
                else         -> TextSecondary
            }
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(scoreColor.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Shield, null, tint = scoreColor, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Credit score", color = TextSecondary, fontSize = 13.sp)
                Text(
                    if (score > 0) "$score" else "Check now",
                    fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPrimary
                )
                Text(
                    when {
                        score >= 700 -> "Excellent"
                        score >= 500 -> "Fair"
                        score > 0    -> "Needs work"
                        else         -> "Tap to check your score"
                    },
                    color = scoreColor, fontSize = 12.sp, fontWeight = FontWeight.Medium
                )
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = TextTertiary)
        }
    }
}

// ── Shortcut grid ─────────────────────────────────────────────────────────
private data class Shortcut(val icon: ImageVector, val label: String, val onClick: () -> Unit)

@Composable
private fun ShortcutGrid(onStocks: () -> Unit, onLoans: () -> Unit, onInsurance: () -> Unit, onSavings: () -> Unit) {
    val shortcuts = listOf(
        Shortcut(Icons.Outlined.DirectionsCar, "Motor cover", onInsurance),
        Shortcut(Icons.Outlined.Fingerprint,   "National ID KYC") {},
        Shortcut(Icons.AutoMirrored.Outlined.TrendingUp,    "RSE stocks", onStocks),
        Shortcut(Icons.Outlined.Store,         "Merchant tools") {},
        Shortcut(Icons.Outlined.Payments,      "SME loans", onLoans),
        Shortcut(Icons.Outlined.Savings,       "Goal savings", onSavings),
    )

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        shortcuts.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { s ->
                    Card(
                        modifier  = Modifier
                            .weight(1f)
                            .height(96.dp)
                            .clickable(onClick = s.onClick),
                        shape     = RoundedCornerShape(16.dp),
                        colors    = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(s.icon, s.label, tint = Primary, modifier = Modifier.size(22.dp))
                            Text(s.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                color = TextPrimary, lineHeight = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

// ── Transactions ──────────────────────────────────────────────────────────
@Composable
private fun RecentTransactionsSection(transactions: List<Transaction>, onSeeAll: () -> Unit = {}) {
    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
                Text("Recent", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                Text(
                    "See all", color = Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onSeeAll)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No recent transactions", color = TextTertiary, fontSize = 14.sp)
                }
            } else {
                transactions.take(5).forEachIndexed { i, txn ->
                    TransactionRow(txn)
                    if (i < transactions.take(5).lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Divider)
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(txn: Transaction) {
    val isCredit = txn.type == "credit"
    val iconColor = if (isCredit) PositiveGreen else NegativeRed
    val iconBg    = iconColor.copy(alpha = 0.08f)
    val amtColor  = if (isCredit) PositiveGreen else TextPrimary
    val icon      = if (isCredit) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(txn.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
            Text(txn.date,  fontSize = 12.sp, color = TextSecondary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text       = "${if (isCredit) "+" else "-"}%,.0f".format(txn.amount),
                fontWeight = FontWeight.Bold,
                fontSize   = 15.sp,
                color      = amtColor
            )
            Text(txn.description, fontSize = 11.sp, color = TextTertiary, maxLines = 1)
        }
    }
}
