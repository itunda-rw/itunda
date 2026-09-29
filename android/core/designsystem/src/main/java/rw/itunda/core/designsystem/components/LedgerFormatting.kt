package rw.itunda.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.AccentIndigo
import rw.itunda.core.designsystem.theme.AccentTeal
import rw.itunda.core.designsystem.theme.AccentPurple
import rw.itunda.core.designsystem.theme.AccentOrange

// Promoted here from :app's LedgerFormatting.kt (2026-09-02, third prerequisite for
// extracting PayTab into its own Feature module) -- PayMoneyDetailScreen/
// PayHomeExtras (both moving to :features:pay:impl) need AccountLedgerRow/
// ledgerRowIcon/ledgerDateHeader, and :app's own AccountDetailScreen/
// TransactionDetailScreen/BucketDetailScreen still need the same real functions --
// pure formatting + a self-contained Composable, zero MainViewModel/:app-only
// dependency, so the whole file (not just the 3 strictly-shared functions) moves
// together rather than fragmenting one cohesive ledger-rendering unit.

// Real Toss Bank reference: every transaction row shows the account's real balance
// AFTER that transaction directly under the signed amount, not just the amount
// alone -- reuses AccountMiniRow's own signed-amount/icon convention (see its doc
// comment) and adds that second line.
@Composable
fun AccountLedgerRow(transaction: rw.itunda.core.network.TransactionDto, isOutgoing: Boolean, afterBalance: Double, currency: String, isScrollTouched: Boolean = false, onClick: () -> Unit = {}) {
    val amountText = "${if (isOutgoing) "-" else "+"}" + String.format(Locale.US, "%,.0f $currency", transaction.amount)
    val amountColor = if (isOutgoing) Ids.colors.textPrimary else Ids.colors.brand
    val (rowIcon, rowIconColor) = ledgerRowIcon(transaction)
    Row(
        modifier = Modifier.fillMaxWidth()
            .pressScaleClickable(isScrollTouched = isScrollTouched, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(CircleShape).background(rowIconColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(rowIcon, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(ledgerRowTitle(transaction), color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(ledgerTimeOfDay(transaction.createdAt), color = Ids.colors.textTertiary, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(amountText, color = amountColor, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(String.format(Locale.US, "%,.0f $currency", afterBalance), color = Ids.colors.textTertiary, fontSize = 12.sp)
        }
    }
}

// Real Toss Bank reference (20 screenshots, 2026-08-21): classifies by description
// keyword first (real itunda-specific products the description already names --
// Ride/Eats/Gift/Escrow/USSD/Savings interest/Cashback), falling back to the real
// backend TransactionType enum for anything the description doesn't name
// specifically. Real category colors, not per-merchant logos itunda has no real
// artwork for.
@Composable
fun ledgerRowIcon(transaction: rw.itunda.core.network.TransactionDto): Pair<androidx.compose.ui.graphics.vector.ImageVector, Color> {
    val d = transaction.description.lowercase()
    return when {
        d.contains("ride") -> Icons.Outlined.DirectionsCar to AccentIndigo
        d.contains("eats") || d.contains("booking") || d.contains("dine-in") -> Icons.Outlined.Fastfood to AccentOrange
        d.contains("gift") -> Icons.Outlined.CardGiftcard to AccentPurple
        d.contains("escrow") || d.contains("marketplace") -> Icons.Outlined.ShoppingBag to AccentTeal
        d.contains("cashback") -> Icons.Outlined.LocalOffer to AccentOrange
        d.contains("interest") -> Icons.Outlined.Savings to AccentIndigo
        d.contains("ussd") -> Icons.Outlined.Call to AccentPurple
        else -> when (transaction.type) {
            "TRANSFER" -> Icons.Outlined.SwapHoriz to AccentIndigo
            "PAYMENT" -> Icons.Outlined.Payments to AccentTeal
            "DEPOSIT" -> Icons.Outlined.ArrowDownward to AccentIndigo
            "WITHDRAWAL" -> Icons.Outlined.ArrowUpward to Ids.colors.textSecondary
            "BILL" -> Icons.Outlined.Description to AccentOrange
            "AIRTIME" -> Icons.Outlined.Call to AccentPurple
            "LOAN" -> Icons.Outlined.AccountBalanceWallet to AccentIndigo
            "INTEREST" -> Icons.Outlined.Savings to AccentIndigo
            else -> Icons.Outlined.SwapHoriz to Ids.colors.textSecondary
        }
    }
}

// Real fix (2026-08-24): Toss's own real ledger rows show the bare counterparty/
// merchant name only -- the category is already carried by the row's icon.
// Strip-by-default with an EXCLUDE list for the two real exceptions found -- "Bill
// payment - $billId" and "$provider Airtime - $phoneNumber" -- where the text after
// the dash is a raw id/phone number, not a name.
private val LEDGER_TITLE_KEEP_PREFIX = listOf("bill payment", "airtime")

fun ledgerRowTitle(transaction: rw.itunda.core.network.TransactionDto): String {
    val d = transaction.description
    val lower = d.lowercase()
    if (LEDGER_TITLE_KEEP_PREFIX.any { lower.contains(it) }) return d
    val doubleDash = d.indexOf(" -- ")
    if (doubleDash >= 0) {
        val tail = d.substring(doubleDash + 4).trim()
        if (tail.isNotBlank()) return tail
    }
    val singleDash = d.indexOf(" - ")
    if (singleDash >= 0) {
        val tail = d.substring(singleDash + 3).trim()
        if (tail.isNotBlank()) return tail
    }
    return d
}

// Real bug, caught live (2026-08-13): with no explicit Locale, DateTimeFormatter
// picks up the device's own locale. Locale.ENGLISH keeps it consistent with
// itunda's own actual UI language.
fun ledgerDateHeader(iso: String): String =
    try {
        java.time.Instant.parse(iso).atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.ENGLISH))
    } catch (_: Exception) {
        iso.take(10)
    }

private fun ledgerTimeOfDay(iso: String): String =
    try {
        java.time.Instant.parse(iso).atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm", java.util.Locale.ENGLISH))
    } catch (_: Exception) {
        ""
    }

fun ledgerFullDateTime(iso: String): String =
    try {
        java.time.Instant.parse(iso).atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm", java.util.Locale.ENGLISH))
    } catch (_: Exception) {
        iso
    }

// Real, sourced backend enum (rw.itunda.core.domain.Transaction.TransactionType) --
// same set ledgerRowIcon's own fallback switch already reads, just given a real
// display label here instead of the raw enum constant.
fun transactionTypeLabel(type: String): String = when (type) {
    "TRANSFER" -> "Transfer"
    "PAYMENT" -> "Payment"
    "DEPOSIT" -> "Deposit"
    "WITHDRAWAL" -> "Withdrawal"
    "BILL" -> "Bill payment"
    "AIRTIME" -> "Airtime"
    "LOAN" -> "Loan"
    "INTEREST" -> "Interest"
    else -> type.lowercase().replaceFirstChar { it.uppercase() }
}
