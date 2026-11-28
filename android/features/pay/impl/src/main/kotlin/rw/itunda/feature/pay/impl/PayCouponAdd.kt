package rw.itunda.feature.pay.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Prototype "Add a coupon". Code redemption has no backend yet, so Register says so instead of pretending. */
@Composable
fun PayCouponAddScreen(onBack: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf(false) }
    PaySubScreen(
        onBack = onBack, title = "Add a coupon",
        cta = { Box(Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 28.dp)) { PayBtn("Register", rememberPayPalette(), enabled = code.isNotBlank()) { notice = true } } },
    ) { p ->
        Column(Modifier.weight(1f).padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(44.dp))
            Text("Coupon code", color = p.text, fontSize = 13.sp, lineHeight = 18.2.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            BasicTextField(
                value = code, onValueChange = { code = it; notice = false }, singleLine = true,
                textStyle = TextStyle(color = p.text, fontSize = 17.sp, lineHeight = 23.8.sp, fontWeight = FontWeight.SemiBold), cursorBrush = SolidColor(p.brandText),
                modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(p.soft),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                        if (code.isEmpty()) Text("Enter your coupon code", color = p.tt, fontSize = 17.sp, lineHeight = 23.8.sp, fontWeight = FontWeight.SemiBold)
                        inner()
                    }
                },
            )
            Box(Modifier.fillMaxWidth().height(20.dp).padding(top = 0.dp)) {
                if (notice) Text("Coupon codes aren’t live yet.", color = Color(0xFFC4281B), fontSize = 14.sp, lineHeight = 19.6.sp)
            }
            Spacer(Modifier.height(16.dp))
            listOf(
                "Coupons you add show up in your Coupon box.",
                "Money coupons are paid in right away.",
                "A coupon can only be added once, and cannot be cancelled.",
                "Coupons cannot be refunded for cash and expire.",
            ).forEach {
                Row { Box(Modifier.width(18.dp)) { Text("•", color = p.tt, fontSize = 14.sp, lineHeight = 22.sp) }; Text(it, color = p.tt, fontSize = 14.sp, lineHeight = 22.sp) }
            }
        }
    }
}
