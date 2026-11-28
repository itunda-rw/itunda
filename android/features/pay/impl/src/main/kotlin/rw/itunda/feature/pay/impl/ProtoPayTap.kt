package rw.itunda.feature.pay.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable

/**
 * Prototype "Tap" screen (card 350x409.2, r28): two brand rings (258.8 / 177.6dp, 16% alpha)
 * around the 92dp countdown tile, then the balance row. Tap-to-pay has no NFC backend yet,
 * so the hint says so instead of faking a terminal tap.
 */
@Composable
internal fun ProtoPayTap(p: PayPalette, secondsLeft: Int, fundingName: String, fundingBalance: String, onTopUp: () -> Unit) {
    Column(Modifier.fillMaxWidth().height(409.2.dp).clip(RoundedCornerShape(28.dp)).background(p.soft)) {
        Box(Modifier.fillMaxWidth().height(331.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(258.8.dp).clip(CircleShape).background(p.brand.copy(alpha = 0.16f)))
            Box(Modifier.size(177.6.dp).clip(CircleShape).background(p.brand.copy(alpha = 0.16f)))
            Box(Modifier.size(92.dp).clip(RoundedCornerShape(30.dp)).background(p.brand), contentAlignment = Alignment.Center) {
                Text("$secondsLeft", color = p.segOn, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp).height(77.8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).offset(y = (-9.5).dp)) {
                Text(fundingName, color = p.tt, fontSize = 13.sp, lineHeight = 18.2.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$fundingBalance RWF", color = p.text, fontSize = 24.sp, lineHeight = 31.2.sp, fontWeight = FontWeight.ExtraBold)
                    Box(Modifier.padding(start = 10.dp).size(width = 10.dp, height = 5.dp).drawBehind {
                        drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2, size.height); close() }, p.text)
                    })
                }
            }
            Box(Modifier.width(73.9.dp).height(36.dp).clip(RoundedCornerShape(12.dp)).background(p.tint).pressScaleClickable(onClick = onTopUp), contentAlignment = Alignment.Center) {
                Text("Top up", color = p.tintFg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    Spacer(Modifier.height(21.8.dp))
    Text(
        "Tap to pay isn't available yet. Use Scan or My code at the till.", color = p.tt, fontSize = 14.sp, lineHeight = 19.6.sp,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
    )
}
