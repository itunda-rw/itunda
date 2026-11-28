package rw.itunda.feature.pay.impl

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable

/**
 * Prototype `payrcd` ("Receipt") for a paid receipt: merchant tile, amount, Merchant / Date / Paid from /
 * Reference rows (tap the reference to copy it), Share receipt, Save merchant, a refund request with its
 * status timeline, and Pay again. Refunds and saved merchants are demo state until the backend has them.
 */
@Composable
fun PayReceiptScreen(index: Int, onBack: () -> Unit, onPayAgain: (merchant: String) -> Unit) {
    val r = payHistory[index]
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    var refundOpen by remember { mutableStateOf(false) }
    val case = PayReceiptState.cases[index]
    val saved = r.till in PayReceiptState.savedTills
    val fullAmount = r.amount.replace(",", "").toLong()
    if (refundOpen) RefundSheet(r.name, fullAmount, { refundOpen = false }) { reason, amt -> PayReceiptState.request(index, reason, amt); refundOpen = false; Toast.makeText(ctx, "Refund request sent", Toast.LENGTH_SHORT).show() }
    PaySubScreen(
        onBack = onBack, title = "Receipt",
        cta = {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 28.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val pal = rememberPayPalette()
                if (case == null) Box(Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(18.dp)).background(pal.tint).pressScaleClickable { refundOpen = true }, contentAlignment = Alignment.Center) {
                    Text("Request refund", color = pal.tintFg, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, maxLines = 1)
                }
                Box(Modifier.weight(1f)) { PayBtn("Pay again", pal) { onPayAgain(r.name) } }
            }
        },
    ) { p ->
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(32.dp)).background(p.soft), contentAlignment = Alignment.Center) {
                    Text(r.name.take(1), color = p.text, fontSize = 26.sp, lineHeight = 36.4.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text(r.name, color = p.tt, fontSize = 17.sp, lineHeight = 23.8.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp, modifier = Modifier.padding(top = 9.5.dp))
                Text("${r.amount} RWF", color = p.text, fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, modifier = Modifier.padding(top = 6.5.dp))
                PayReceiptState.status(index)?.let { st ->
                    val bad = st == "Refund declined"
                    val good = st == "Refunded"
                    Text(st, color = if (bad) Color(0xFFE5484D) else if (good) Color(0xFF1A9E63) else p.tintFg, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(if (bad) Color(0x1FE5484D) else if (good) Color(0x1F1A9E63) else p.tint).padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
            Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(1.dp).background(p.soft))
            Column(Modifier.padding(horizontal = 24.dp)) {
                Kv("Merchant", "Till ${r.till}", p, first = true)
                Kv("Date", r.date, p)
                Kv("Paid from", "itunda Pay", p)
                Kv("Reference", r.ref, p, copy = true, onClick = {
                    clipboard.setText(AnnotatedString(r.ref))
                    Toast.makeText(ctx, "Reference copied", Toast.LENGTH_SHORT).show()
                })
            }
            case?.let { c -> RefundTimeline(c, index, p) }
            Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(20.dp)).background(p.soft)
                    .semantics { contentDescription = "Share receipt"; role = Role.Button }
                    .pressScaleClickable {
                        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "itunda Pay receipt\nMerchant: ${r.name}\nTill: ${r.till}\nAmount: ${r.amount} RWF\nDate: ${r.date}\nPaid from: itunda Pay\nReference: ${r.ref}") }
                        ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                contentAlignment = Alignment.Center,
            ) { Text("Share receipt", color = p.text, fontSize = 14.sp, lineHeight = 19.6.sp, fontWeight = FontWeight.Bold) }
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(20.dp)).background(if (saved) p.tint else p.soft)
                        .semantics { contentDescription = if (saved) "Saved merchant" else "Save merchant"; role = Role.Button }
                        .pressScaleClickable { PayReceiptState.toggleSaved(r.till) },
                    contentAlignment = Alignment.Center,
                ) { Text(if (saved) "★ Saved" else "☆ Save merchant", color = if (saved) p.tintFg else p.text, fontSize = 14.sp, lineHeight = 19.6.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun Kv(label: String, value: String, p: PayPalette, first: Boolean = false, copy: Boolean = false, onClick: (() -> Unit)? = null) {
    Column {
        if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(p.soft))
        Row(
            Modifier.fillMaxWidth().height(if (first) 55.dp else 55.dp)
                .let {
                    if (onClick != null) it.semantics {
                        contentDescription = "Copy receipt reference"
                        role = Role.Button
                    }.pressScaleClickable(onClick = onClick) else it
                },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = p.tt, fontSize = 16.sp, lineHeight = 22.4.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, color = p.text, fontSize = 16.sp, lineHeight = 22.4.sp, fontWeight = FontWeight.SemiBold)
                if (copy) Canvas(Modifier.padding(start = 6.dp).size(13.dp)) {
                    val st = Stroke(1.6.dp.toPx())
                    drawRoundRect(p.text, Offset(0f, 3.dp.toPx()), Size(9.dp.toPx(), 10.dp.toPx()), CornerRadius(2.dp.toPx()), st)
                    drawRoundRect(p.text, Offset(3.5.dp.toPx(), 0f), Size(9.dp.toPx(), 10.dp.toPx()), CornerRadius(2.dp.toPx()), st)
                }
            }
        }
    }
}

/** Prototype refund case: three steps (sent, merchant reviewing, decision), the reason line, and Check status. */
@Composable
private fun RefundTimeline(c: PayRefundCase, index: Int, p: PayPalette) {
    val declined = c.step >= 2 && c.reason == "mind"
    val steps = listOf("Request sent", "Merchant is reviewing", if (c.step < 2) "Decision" else if (declined) "Declined" else "Refunded to itunda Pay")
    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 26.dp)) {
        Text("Refund · ${c.id}", color = p.text, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp, modifier = Modifier.padding(bottom = 8.dp))
        steps.forEachIndexed { i, label ->
            val done = i < c.step || (i == 2 && c.step >= 2 && !declined)
            val bad = i == 2 && declined
            val on = i == c.step && c.step < 2
            Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(24.dp).clip(CircleShape).background(if (bad) Color(0xFFE5484D) else if (done) p.brand else if (on) p.tint else p.soft),
                    contentAlignment = Alignment.Center,
                ) { Text(if (done) "✓" else if (bad) "×" else "${i + 1}", color = if (bad) Color.White else if (done) Color(0xFF17171C) else if (on) p.tintFg else p.tt, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Text(label, color = if (done || on || bad) p.text else p.tt, fontSize = 15.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
            }
        }
        val reason = PAY_REFUND_REASONS.firstOrNull { it.first == c.reason }?.second ?: ""
        Text("Reason: $reason · ${"%,d".format(c.amount)} RWF" + if (declined) "\nThis reason is not covered by the merchant’s refund policy." else "", color = p.tt, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp))
        if (c.step < 2) Box(
            Modifier.padding(top = 14.dp).height(44.dp).clip(RoundedCornerShape(20.dp)).background(p.tint).pressScaleClickable { PayReceiptState.advance(index) }.padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Check status", color = p.tintFg, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
    }
}

/** Prototype pxRefSheet: reason, full or half, a note on where the money goes, Send request, Cancel. */
@Composable
private fun RefundSheet(merchant: String, full: Long, onClose: () -> Unit, onSend: (reason: String, amount: Long) -> Unit) {
    val p = rememberPayPalette()
    var reason by remember { mutableStateOf("") }
    var half by remember { mutableStateOf(false) }
    val amt = if (half) Math.round(full / 2.0) else full
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(p.page).padding(24.dp)) {
            Text("Request a refund", color = p.text, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold)
            Text("$merchant · ${"%,d".format(full)} RWF", color = p.tt, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
            Text("What went wrong?", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            PAY_REFUND_REASONS.forEach { (k, label) -> RefundChoice(label, reason == k, p) { reason = k } }
            Text("How much?", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { RefundChoice("Full ${"%,d".format(full)}", !half, p) { half = false } }
                Box(Modifier.weight(1f)) { RefundChoice("Half ${"%,d".format(Math.round(full / 2.0))}", half, p) { half = true } }
            }
            Text("Refunds go back to itunda Pay. The merchant has to approve the request.", color = p.tt, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 12.dp))
            Spacer(Modifier.height(16.dp))
            PayBtn("Send request" + if (reason.isNotEmpty()) " · ${"%,d".format(amt)} RWF" else "", p, enabled = reason.isNotEmpty()) { onSend(reason, amt) }
            Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(48.dp).pressScaleClickable(onClick = onClose), contentAlignment = Alignment.Center) { Text("Cancel", color = p.tt, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun RefundChoice(label: String, on: Boolean, p: PayPalette, onClick: () -> Unit) {
    Box(
        Modifier.padding(bottom = 6.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(20.dp)).background(if (on) p.segOn else p.soft).pressScaleClickable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) { Text(label, color = if (on) p.page else p.text, fontSize = 15.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1) }
}
