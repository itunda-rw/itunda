package rw.itunda.feature.pay.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
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
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

/** Prototype "Pay" settings: default funding source and two preferences (kept on-screen only until a settings API exists). */
@Composable
fun PaySettingsScreen(onBack: () -> Unit, onAllSettings: () -> Unit = {}) {
    var fromBalance by remember { mutableStateOf(true) }
    var tap by remember { mutableStateOf(true) }
    var receipts by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().background(Ids.colors.background).padding(horizontal = 20.dp)) {
        BackTopBar("Pay", onBack)
        Text("Pay from", color = Ids.colors.textTertiary, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp, bottom = 10.dp))
        Row {
            listOf("Pay balance" to true, "Bank" to false).forEach { (label, isBalance) ->
                val on = fromBalance == isBalance
                Text(label, color = if (on) Color.White else Ids.colors.textTertiary, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(20.dp)).background(if (on) Ids.colors.textPrimary else Ids.colors.surfaceSoft)
                        .pressScaleClickable { fromBalance = isBalance }.padding(horizontal = 18.dp, vertical = 10.dp))
            }
        }
        SettingRow("Tap to pay", "Pay at shops by tapping your phone", tap) { tap = it }
        SettingRow("Payment receipts", "Get a message after every payment", receipts) { receipts = it }
        Text("All settings", color = Ids.colors.textBrand, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 20.dp).pressScaleClickable(onClick = onAllSettings).padding(vertical = 8.dp))
    }
}

@Composable
private fun SettingRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(body, color = Ids.colors.textTertiary, fontSize = 13.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
