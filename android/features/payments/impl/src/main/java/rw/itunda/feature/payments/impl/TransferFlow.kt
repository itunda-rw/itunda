package rw.itunda.feature.payments.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.theme.Tds
import java.text.NumberFormat
import java.util.Locale

/**
 * A real, from-scratch transfer flow built directly against the actual
 * Toss reference screenshots (user-provided, 2026-07-10) rather than
 * incrementally patching the earlier ported RecipientScreen/TransferScreen,
 * which were deleted -- this replaces them.
 *
 * Two screens, matching the reference exactly:
 *  - RecipientEntryScreen: "어떤 계좌로 보낼까요?" -- account number input,
 *    bank selector, numeric keypad, no chrome beyond a back arrow.
 *  - TransferAmountScreen: "얼마나 보낼까요?" -- from/to account summary with
 *    a connector line, a headline that goes from muted to bold once an
 *    amount is entered, quick-amount chips, a Next bar, and a keypad.
 *
 * Backed by local state only, not TransferService -- itunda has no login
 * flow yet (see MainViewModel.kt / NetworkClient.kt), so there is no real
 * session to quote a transfer against. Honestly a UI shell, not wired to
 * the backend, exactly like the mini-app host's "no active session" finding
 * earlier this session -- not silently claimed as more than it is.
 */

private val rwfFormatter = NumberFormat.getNumberInstance(Locale.US)

@Composable
fun RecipientEntryScreen(
    onBack: () -> Unit,
    onNext: (accountNumber: String) -> Unit
) {
    var accountNumber by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tds.colors.background)
    ) {
        FlowTopBar(onBack)

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                "Which account should\nwe send to?",
                color = Tds.colors.textPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 34.sp
            )
            Spacer(modifier = Modifier.height(28.dp))
            Text("Enter account number", color = Tds.colors.brand, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            // Fixed (2026-07-11): the only form input in this app had no accessible
            // label at all -- BasicTextField, unlike a View-based TextInputLayout,
            // doesn't auto-associate the visible "Enter account number" Text() above
            // it (Compose doesn't merge sibling composables into one accessible node
            // unless told to), so TalkBack announced this as a bare, unlabeled edit
            // field. docs/ACCESSIBILITY.md flagged form labels as an open, unaudited
            // item -- this was the field that audit needed to find.
            BasicTextField(
                value = accountNumber,
                onValueChange = { input -> accountNumber = input.filter { it.isDigit() }.take(16) },
                textStyle = TextStyle(color = Tds.colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Tds.colors.brand),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Account number, up to 16 digits" }
            )
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.material3.Divider(color = Tds.colors.brand, thickness = 2.dp)

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Select bank", color = Tds.colors.textTertiary, fontSize = 17.sp)
                    if (accountNumber.isEmpty()) {
                        Text(
                            "We'll find the bank once you enter the account number.",
                            color = Tds.colors.textTertiary,
                            fontSize = 13.sp
                        )
                    }
                }
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Tds.colors.textTertiary)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (accountNumber.length >= 4) {
            FlowNextBar(enabled = true, label = "Next") { onNext(accountNumber) }
        }
        NumericKeypad(
            onDigit = { d -> if (accountNumber.length < 16) accountNumber += d },
            onDelete = { if (accountNumber.isNotEmpty()) accountNumber = accountNumber.dropLast(1) }
        )
    }
}

@Composable
fun TransferAmountScreen(
    recipientAccountNumber: String,
    onBack: () -> Unit,
    onConfirm: (amountRwf: Long) -> Unit
) {
    var digits by remember { mutableStateOf("") }
    val amount = digits.toLongOrNull() ?: 0L

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tds.colors.background)
    ) {
        FlowTopBar(onBack)

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            TransferPartyRow(
                label = "From Itunda Wallet",
                sublabel = "Available RWF 112,242",
                icon = Icons.Outlined.AccountBalanceWallet
            )
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .padding(start = 21.dp)
                    .width(2.dp)
                    .height(20.dp)
                    .background(Tds.colors.divider)
            )
            Spacer(modifier = Modifier.height(2.dp))
            TransferPartyRow(
                label = "To account $recipientAccountNumber",
                sublabel = "New recipient",
                icon = Icons.Outlined.Savings
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("How much to send?", color = Tds.colors.textSecondary, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (digits.isEmpty()) "0 RWF" else "${rwfFormatter.format(amount)} RWF",
                fontSize = if (digits.isEmpty()) 32.sp else 42.sp,
                fontWeight = FontWeight.Bold,
                color = if (digits.isEmpty()) Tds.colors.textTertiary else Tds.colors.textPrimary,
                textAlign = TextAlign.Center
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickAmountChip("+10,000") { digits = ((digits.toLongOrNull() ?: 0L) + 10_000L).toString() }
            QuickAmountChip("+100,000") { digits = ((digits.toLongOrNull() ?: 0L) + 100_000L).toString() }
            QuickAmountChip("Max") { digits = "112242" }
        }

        FlowNextBar(enabled = digits.isNotEmpty() && amount > 0, label = "Send") { onConfirm(amount) }
        NumericKeypad(
            onDigit = { d -> if (digits.length < 9) digits += d },
            onDelete = { if (digits.isNotEmpty()) digits = digits.dropLast(1) }
        )
    }
}

@Composable
private fun FlowTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.ArrowBackIosNew, contentDescription = "Back", modifier = Modifier.size(18.dp), tint = Tds.colors.textPrimary)
        }
    }
}

@Composable
private fun TransferPartyRow(label: String, sublabel: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label, color = Tds.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(sublabel, color = Tds.colors.textTertiary, fontSize = 13.sp)
        }
        Box(
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Tds.colors.chip),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = Tds.colors.textPrimary)
        }
    }
}

@Composable
private fun QuickAmountChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Tds.colors.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(label, color = Tds.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FlowNextBar(enabled: Boolean, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) Tds.colors.brand else Tds.colors.chip)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (enabled) Color.White else Tds.colors.textTertiary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun NumericKeypad(onDigit: (String) -> Unit, onDelete: () -> Unit) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("00", "0", "DEL")
    )
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        keys.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth().height(60.dp)) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { if (key == "DEL") onDelete() else onDigit(key) }
                            // Digit keys' visible text is already their own accessible
                            // name; DEL's "⌫" glyph is not, so it needs an explicit one
                            // -- same reasoning as TopIconButton's fix elsewhere.
                            .then(if (key == "DEL") Modifier.semantics { contentDescription = "Delete" } else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        if (key == "DEL") {
                            Text("⌫", fontSize = 22.sp, color = Tds.colors.textPrimary)
                        } else {
                            Text(key, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Tds.colors.textPrimary)
                        }
                    }
                }
            }
        }
    }
}
