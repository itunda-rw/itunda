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
 * Reference rows (tap the reference to copy it), Share receipt, and Pay again. Refund request and
 * Save merchant are left out until the backend has refunds and saved merchants.
 */
@Composable
fun PayReceiptScreen(index: Int, onBack: () -> Unit, onPayAgain: (merchant: String) -> Unit) {
    val r = payHistory[index]
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    PaySubScreen(
        onBack = onBack, title = "Receipt",
        cta = {
            Box(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 28.dp)) {
                PayBtn("Pay again", rememberPayPalette()) { onPayAgain(r.name) }
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
            Box(
                Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp).fillMaxWidth(0.488f).height(44.dp).clip(RoundedCornerShape(20.dp)).background(p.soft)
                    .semantics { contentDescription = "Share receipt"; role = Role.Button }
                    .pressScaleClickable {
                        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "itunda Pay receipt\nMerchant: ${r.name}\nTill: ${r.till}\nAmount: ${r.amount} RWF\nDate: ${r.date}\nPaid from: itunda Pay\nReference: ${r.ref}") }
                        ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                contentAlignment = Alignment.Center,
            ) { Text("Share receipt", color = p.text, fontSize = 14.sp, lineHeight = 19.6.sp, fontWeight = FontWeight.Bold) }
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
