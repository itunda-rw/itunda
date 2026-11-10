package rw.itunda.feature.pay.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.theme.Ids

/**
 * Prototype "Tap" screen: concentric rings around the countdown tile, balance below.
 * Tap-to-pay has no NFC backend yet, so this says so instead of faking a terminal tap.
 */
@Composable
internal fun ProtoPayTap(secondsLeft: Int, fundingName: String, fundingBalance: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(Ids.colors.surfaceSoft).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(254.dp), contentAlignment = Alignment.Center) {
            val brand = Ids.colors.brand
            Canvas(Modifier.size(254.dp)) {
                drawCircle(brand.copy(alpha = 0.10f), radius = size.minDimension / 2)
                drawCircle(brand.copy(alpha = 0.16f), radius = size.minDimension / 2 * 0.68f)
            }
            Box(Modifier.size(92.dp).clip(RoundedCornerShape(30.dp)).background(brand), contentAlignment = Alignment.Center) {
                Text("$secondsLeft", color = Ids.colors.textPrimary, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(fundingName, color = Ids.colors.textTertiary, fontSize = 14.sp)
            Text("$fundingBalance RWF", color = Ids.colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
    Spacer(Modifier.height(16.dp))
    Text(
        "Tap to pay isn't available yet. Use Scan or My code at the till.",
        color = Ids.colors.textTertiary, fontSize = 15.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    )
}
