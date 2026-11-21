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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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

private data class ManageRow(val art: String, val title: String, val subtitle: String, val trailing: String = "", val target: BankSub? = null, val tapsAuto: Boolean = false)

private val accountRows = listOf(
    ManageRow("money", "Account details", "Holder, number, bank, branch", target = BankSub.DETAILS),
    ManageRow("bill", "Interest", "4.0% a year (sample)", "1,462 RWF"),
)
private val moneyRows = listOf(
    ManageRow("lock", "Limits & alerts", "Daily limit 1,000,000 RWF", target = BankSub.LIMITS),
    ManageRow("history", "Scheduled transfers", "Rent, fees, monthly gifts", target = BankSub.SCHEDULED),
    ManageRow("money", "Auto-transfer", "Move money on its own", tapsAuto = true),
)
private val documentRows = listOf(ManageRow("receipt", "Documents & letters", "Account letter, proof of balance", target = BankSub.DOCS))
private val cardRows = listOf(ManageRow("card", "Card", "Get your itunda card", target = BankSub.CARDS))

/** Prototype "Manage account": holder block with Copy number and Share, then Account / Money / Documents / Card rows. */
@Composable
internal fun BankManageScreen(onBack: () -> Unit, onOpen: (BankSub) -> Unit, onAutoTransfers: () -> Unit = {}) {
    val clipboard = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        BankSubTopBar("Manage account", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Ids.colors.brand.copy(alpha = 0.10f), Ids.colors.background))).padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 4.dp)) {
                Text("itunda Bank", color = Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = (-0.176).sp)
                Text("Amina Uwase", color = Ids.colors.textPrimary, fontSize = 28.sp, lineHeight = 39.2.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp, modifier = Modifier.padding(top = 4.dp))
                Text("0214-3058-1980", color = Ids.colors.textTertiary, fontSize = 16.sp, lineHeight = 22.4.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 2.dp, bottom = 14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BankChip("Copy number", false, { clipboard.setText(AnnotatedString("0214-3058-1980")) })
                    BankChip("Share", false, {
                        context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, "itunda Bank 0214-3058-1980"), null))
                    })
                }
            }
            Column(Modifier.padding(horizontal = 24.dp)) {
                Section("Account", accountRows, onOpen, onAutoTransfers)
                Section("Money", moneyRows, onOpen, onAutoTransfers)
                Section("Documents", documentRows, onOpen, onAutoTransfers)
                Section("Card", cardRows, onOpen, onAutoTransfers)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Section(title: String, rows: List<ManageRow>, onOpen: (BankSub) -> Unit, onAutoTransfers: () -> Unit) {
    Text(title, color = Ids.colors.textPrimary, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, modifier = Modifier.padding(top = 28.dp, bottom = 12.dp))
    rows.forEach { r ->
        val tappable = r.target != null || r.tapsAuto
        Row(
            Modifier.fillMaxWidth().height(64.dp).then(if (tappable) Modifier.pressScaleClickable { if (r.tapsAuto) onAutoTransfers() else onOpen(r.target!!) } else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
                rw.itunda.core.designsystem.components.ItundaArt(r.art, rw.itunda.core.designsystem.components.ItundaArtSet.Proto, 31.dp, fit = true)
            }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(r.title, color = Ids.colors.textPrimary, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
                Text(r.subtitle, color = Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = (-0.2).sp, modifier = Modifier.padding(top = 3.dp))
            }
            if (r.trailing.isNotEmpty()) Text(r.trailing, color = Ids.colors.textPrimary, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Black)
            else if (tappable) rw.itunda.core.designsystem.components.ItundaArt("chev", rw.itunda.core.designsystem.components.ItundaArtSet.Line, 16.dp, tint = Ids.colors.textPrimary)
        }
    }
    Spacer(Modifier.height(4.dp))
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
