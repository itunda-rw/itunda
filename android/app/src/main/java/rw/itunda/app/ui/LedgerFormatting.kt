package rw.itunda.app.ui

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.AccentIndigo
import rw.itunda.core.designsystem.theme.AccentTeal
import rw.itunda.core.designsystem.theme.AccentPurple
import rw.itunda.core.designsystem.theme.AccentOrange

// Real fix (2026-08-26): split out of ItundaAppScreen.kt once that file grew past
// its file-size-lint baseline. Pure transaction-ledger row rendering + formatting
// helpers, already internal (used by PayMoneyDetailScreen.kt), self-contained --
// no dependency on anything else in ItundaAppScreen.kt beyond a doc-comment
// reference. Same package, so zero import changes at any call site.

// Real Toss Bank reference: every transaction row shows the account's real balance
// AFTER that transaction directly under the signed amount, not just the amount
// alone -- reuses AccountMiniRow's own signed-amount/icon convention (see its doc
// comment) and adds that second line.
@Composable
internal fun AccountLedgerRow(transaction: rw.itunda.core.network.TransactionDto, isOutgoing: Boolean, afterBalance: Double, currency: String, isScrollTouched: Boolean = false, onClick: () -> Unit = {}) {
    val amountText = "${if (isOutgoing) "-" else "+"}%,.0f $currency".format(transaction.amount)
    // Real fix (2026-08-14, direct user screenshots of their own real Toss Bank
    // ledger): incoming amounts are tinted the real brand blue there, not a generic
    // green success color -- this file's own earlier claim otherwise (see
    // AccountMiniRow's doc comment below) was never actually checked against a real
    // reference until now.
    val amountColor = if (isOutgoing) Ids.colors.textPrimary else Ids.colors.brand
    val (rowIcon, rowIconColor) = ledgerRowIcon(transaction)
    // Real fix (2026-08-24, direct user side-by-side of itunda's own ledger against a
    // real Toss Bank screenshot: "toss ux is more big clear to see"). Bumped the
    // title/amount from 15sp SemiBold to IdsTypography's real Subtitle1 step (17sp
    // Bold, sourced TDS scale -- not an invented size) and the icon circle from 38dp
    // to 42dp proportionally, matching Toss's real bigger, bolder row weight. The
    // secondary time/balance lines stay small and subdued, same as the real reference.
    // Real fix (2026-08-24, direct user follow-up: "no I mean presable effect" --
    // clarifying the earlier scroll-bounce fix missed the actual ask). This row had
    // no onClick and no press feedback at all -- Toss's own real spring press-scale
    // is exactly what tells a user a row is tappable in the first place, and this
    // row had nothing behind it to tap INTO either: itunda's ledger never had a
    // transaction-detail screen, despite this whole thread's own first message
    // already assuming one existed ("clear anyway when click on each transactions
    // they get to see it's detail screen"). pressScaleClickable is the same shared
    // spring used by every other real tappable row in this app (IdsListRow etc.),
    // not a new one-off animation.
    //
    // Follow-up fix (same day, "not as smooth as toss spring effect ... user
    // finger touch presable components while scroll user can feel that spring
    // effect", then "implant that into our designs and apply it across our
    // ecosystems"): isScrollTouched is now a real parameter of the SHARED
    // pressScaleClickable (IdsInteractions.kt), not one-off inline code -- see its
    // own doc comment for why plain Modifier.clickable alone can't do this (its
    // press state is cancelled the instant a touch is recognized as a scroll drag).
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
            // Real bug, caught live (2026-08-24, direct on-device screenshot): a long
            // free-text transfer title ("post-deploy regression check") rendered
            // touching the amount column with zero gap ("check-RWF 500"), reading as
            // one word -- NOT a wrap/overflow issue (the title Column's weight(1f)
            // width was never exceeded), but a missing minimum gap: this title
            // Column and the amount Column below had no Spacer between them, only
            // relying on leftover weighted width once the title text's own natural
            // width was subtracted -- which shrinks to zero once the title is long
            // enough, and for an outgoing (debit) row the amount is the exact same
            // white textPrimary color as the title, so the two ran together
            // invisibly instead of just looking cramped. maxLines/ellipsis here is a
            // second, independent guard for titles too long to fit at all.
            Text(ledgerRowTitle(transaction), color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(ledgerTimeOfDay(transaction.createdAt), color = Ids.colors.textTertiary, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(amountText, color = amountColor, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("%,.0f $currency".format(afterBalance), color = Ids.colors.textTertiary, fontSize = 12.sp)
        }
    }
}

// Real Toss Bank reference (20 screenshots, 2026-08-21, direct user follow-up:
// "icons the size" -- comparing itunda's real ledger, which only ever showed a
// generic up/down arrow, against the real one, where every row carries a
// distinctive per-category icon). Classifies by description keyword first (real
// itunda-specific products the description already names -- Ride/Eats/Gift/
// Escrow/USSD/Savings interest/Cashback), falling back to the real backend
// TransactionType enum (TRANSFER/PAYMENT/DEPOSIT/WITHDRAWAL/BILL/AIRTIME/LOAN/
// INTEREST, core/domain/Transaction.kt) for anything the description doesn't
// name specifically. Real category colors, not per-merchant logos itunda has no
// real artwork for.
@Composable
internal fun ledgerRowIcon(transaction: rw.itunda.core.network.TransactionDto): Pair<androidx.compose.ui.graphics.vector.ImageVector, Color> {
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

// Real fix (2026-08-24, direct user side-by-side of itunda's own ledger against a
// real Toss Bank ledger + detail-screen screenshot: "since we are using real logos
// and icons no need to mention Eat order, kigali grill house will be enough and
// clear anyway when click on each transactions they get to see it's detail
// screen"). Toss's own real ledger rows show the bare counterparty/merchant name
// only -- the category is already carried by the row's icon, and the full
// "Eats order - Kigali Grill House" phrasing is preserved untouched on the tap-in
// detail screen.
//
// Follow-up fix (same day, "they are still some transactions that don't follow
// the same pattern"): the first pass only allowlisted eats/gift/escrow-marketplace,
// but a full sweep of every Transaction.description call site across the backend
// (Order/Card payment/Payment/QR payment/Salary payment/Booking deposit/
// Subscription charge/Dine-in order/Split bill share/Transfer/Delayed transfer --
// MerchantService.kt, OrderService.kt, DineInOrderService.kt, SplitBillService.kt,
// P2pService.kt, PayrollService.kt, MerchantBookingService.kt) shows the exact same
// "{category} - {a real name}" shape almost everywhere; the allowlist was just
// incomplete, not the right model. Flipped to strip-by-default with an EXCLUDE list
// for the only two real exceptions found -- "Bill payment - $billId" and
// "$provider Airtime - $phoneNumber" -- where the text after the dash is a raw
// id/phone number, not a name, and stripping it would make the row less clear.
private val LEDGER_TITLE_KEEP_PREFIX = listOf("bill payment", "airtime")

internal fun ledgerRowTitle(transaction: rw.itunda.core.network.TransactionDto): String {
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
// picks up the device's own locale -- on the test device that's Korean, so this
// rendered "8월 13" even though the rest of this app's UI is fixed English
// (strings.xml has no localization at all). Locale.ENGLISH keeps it consistent
// with itunda's own actual language, not whatever the phone happens to be set to.
internal fun ledgerDateHeader(iso: String): String =
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

internal fun ledgerFullDateTime(iso: String): String =
    try {
        java.time.Instant.parse(iso).atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm", java.util.Locale.ENGLISH))
    } catch (_: Exception) {
        iso
    }

// Real, sourced backend enum (rw.itunda.core.domain.Transaction.TransactionType) --
// same set ledgerRowIcon's own fallback switch already reads, just given a real
// display label here instead of the raw enum constant.
internal fun transactionTypeLabel(type: String): String = when (type) {
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
