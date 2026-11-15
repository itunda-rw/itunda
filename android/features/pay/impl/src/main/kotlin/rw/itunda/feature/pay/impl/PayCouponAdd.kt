package rw.itunda.feature.pay.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.theme.Ids

/** Prototype "Add a coupon". Code redemption has no backend yet, so Register says so instead of pretending. */
@Composable
fun PayCouponAddScreen(onBack: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Ids.colors.background).padding(horizontal = 24.dp)) {
        BackTopBar("Add a coupon", onBack)
        Text("Coupon code", color = Ids.colors.textTertiary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        BasicTextField(
            value = code, onValueChange = { code = it; notice = false }, singleLine = true,
            textStyle = TextStyle(color = Ids.colors.textPrimary, fontSize = 16.sp),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ids.colors.surfaceSoft).padding(16.dp),
            decorationBox = { inner -> if (code.isEmpty()) Text("Enter your coupon code", color = Ids.colors.textTertiary, fontSize = 16.sp); inner() },
        )
        Spacer(Modifier.height(24.dp))
        listOf(
            "Coupons you add show up in your Coupon box.",
            "Money coupons are paid in right away.",
            "A coupon can only be added once, and cannot be cancelled.",
            "Coupons cannot be refunded for cash and expire.",
        ).forEach { Text("•  $it", color = Ids.colors.textTertiary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp)) }
        Spacer(Modifier.weight(1f))
        if (notice) Text("Coupon codes aren’t live yet.", color = Ids.colors.textTertiary, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
        PayPrimaryButton("Register", enabled = code.isNotBlank()) { notice = true }
        Spacer(Modifier.height(16.dp))
    }
}
