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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import rw.itunda.core.designsystem.components.pressScaleClickable

/** Prototype "Pay" settings: default funding source and two preferences (kept on-screen only until a settings API exists). */
@Composable
fun PaySettingsScreen(onBack: () -> Unit, onAllSettings: () -> Unit = {}) {
    var fromBalance by remember { mutableStateOf(true) }
    var tap by remember { mutableStateOf(true) }
    var receipts by remember { mutableStateOf(true) }
    PaySubScreen(onBack = onBack) { p ->
        Text("Pay", color = p.text, fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp, modifier = Modifier.padding(start = 24.dp, top = 14.dp))
        Box(Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp).fillMaxWidth().height(30.4.dp).clip(RoundedCornerShape(20.dp)).background(p.soft)) {
            Text("Pay from", color = p.text, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 5.dp))
        }
        Row(Modifier.padding(start = 25.dp, top = 13.dp).height(40.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
            listOf("Pay balance" to true, "Bank" to false).forEach { (label, isBalance) ->
                val on = fromBalance == isBalance
                Box(
                    Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(if (on) p.text.let { if (p.page == Color(0xFF17171C)) p.soft else Color(0xFF191F28) } else p.soft)
                        .pressScaleClickable { fromBalance = isBalance }.semantics { selected = on }.padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = if (on) Color.White else p.tt, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(0.dp))
        SettingRow(p, "Tap to pay", "Pay at shops by tapping your phone", tap) { tap = it }
        SettingRow(p, "Payment receipts", "Get a message after every payment", receipts) { receipts = it }
        Text("All settings", color = p.brandText, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 24.dp, top = 12.dp).pressScaleClickable(onClick = onAllSettings).padding(vertical = 8.dp))
    }
}

@Composable
private fun SettingRow(p: PayPalette, title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(74.dp).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
            Text(body, color = p.tt, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.2).sp)
        }
        Box(
            Modifier.size(width = 50.dp, height = 30.dp).clip(RoundedCornerShape(15.dp)).background(if (checked) p.brand else p.soft.copy(alpha = 1f))
                .pressScaleClickable { onChange(!checked) }.semantics { role = Role.Switch; stateDescription = if (checked) "On" else "Off" },
        ) {
            Box(Modifier.offset(x = if (checked) 23.dp else 3.dp, y = 3.dp).size(24.dp).clip(CircleShape).background(Color.White))
        }
    }
}
