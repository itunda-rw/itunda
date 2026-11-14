package rw.itunda.feature.banking.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

private data class ManageRow(val emoji: String, val title: String, val subtitle: String, val trailing: String = "")

private val accountRows = listOf(
    ManageRow("💳", "Account details", "Holder, number, bank, branch"),
    ManageRow("📈", "Interest", "4.0% a year (sample)", "1,462 RWF"),
)
private val moneyRows = listOf(
    ManageRow("🔒", "Limits & alerts", "Daily limit 1,000,000 RWF"),
    ManageRow("🕒", "Scheduled transfers", "Rent, fees, monthly gifts"),
    ManageRow("🔁", "Auto-transfer", "Move money on its own"),
)
private val documentRows = listOf(ManageRow("📄", "Documents & letters", "Account letter, proof of balance"))

/** Prototype "Manage account": holder block with Copy number, then Account / Money / Documents rows (dummy). */
@Composable
internal fun BankManageScreen(onBack: () -> Unit, onOpen: (BankSub) -> Unit) {
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        BankSubTopBar("Manage account", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Text("itunda Bank", color = Ids.colors.textTertiary, fontSize = 13.sp)
            Text("Amina Uwase", color = Ids.colors.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("0214-3058-1980", color = Ids.colors.textTertiary, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
            BankChip("Copy number", false, { clipboard.setText(AnnotatedString("0214-3058-1980")) })
            Section("Account", accountRows)
            Section("Money", moneyRows, onOpen)
            Section("Documents", documentRows)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, rows: List<ManageRow>, onOpen: (BankSub) -> Unit = {}) {
    Text(title, color = Ids.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 28.dp, bottom = 6.dp))
    rows.forEach { r ->
        Row(
            Modifier.fillMaxWidth().then(if (r.title == "Limits & alerts") Modifier.pressScaleClickable { onOpen(BankSub.LIMITS) } else Modifier).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) { Text(r.emoji, fontSize = 18.sp) }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(r.title, color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(r.subtitle, color = Ids.colors.textTertiary, fontSize = 13.sp)
            }
            if (r.trailing.isNotEmpty()) Text(r.trailing, color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (r.title == "Limits & alerts") Text("  \u203A", color = Ids.colors.textTertiary, fontSize = 20.sp)
        }
    }
}

/** Pocket detail (prototype "pocket"): icon, amount, Interest received / Auto-fill rows, empty state, Fill. */
@Composable
internal fun BankPocketDetail(name: String, emoji: String, amount: Long, onBack: () -> Unit) {
    var done by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (done) { BankDone("Pocket filled", "Added to $name from itunda Bank.", onBack); return }
    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        BankSubTopBar("", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) { Text(emoji, fontSize = 30.sp) }
            Text(name, color = Ids.colors.textTertiary, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
            Text("${rwf(amount)} RWF", color = Ids.colors.textPrimary, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(20.dp))
            PocketLine("Interest received", "0 RWF")
            PocketLine("Auto-fill", "Off")
            Text("Nothing here yet", color = Ids.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 28.dp))
            Text(
                "Fill it from itunda Bank, which has 842,300 RWF. Or let it fill itself.",
                color = Ids.colors.textTertiary, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp, bottom = 14.dp),
            )
        }
        BankSubCta("Fill") { done = true }
    }
}

@Composable
private fun PocketLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = Ids.colors.textTertiary, fontSize = 16.sp)
    }
}
