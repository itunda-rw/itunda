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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

private data class Tx(val day: String, val name: String, val time: String, val amount: String, val after: String, val incoming: Boolean)

private val demoTx = listOf(
    Tx("Yesterday", "Amina K.", "18:20", "+25,000 RWF", "842,300 RWF", true),
    Tx("9 Oct", "Rent", "04:25", "−150,000 RWF", "817,300 RWF", false),
    Tx("6 Oct", "Jean Paul", "05:25", "−30,000 RWF", "967,300 RWF", false),
)

/** Prototype account detail: balance, daily interest note, grouped activity, Fill / Send. */
@Composable
internal fun BankAccountScreen(onBack: () -> Unit, onOpen: (BankSub) -> Unit) {
    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        BankSubTopBar("", onBack) {
            Text("Statement", color = Ids.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.pressScaleClickable { onOpen(BankSub.STATEMENT) }.padding(8.dp))
            Text("Manage", color = Ids.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.pressScaleClickable { onOpen(BankSub.MANAGE) }.padding(8.dp))
            Text("Move", color = Ids.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.pressScaleClickable { onOpen(BankSub.MOVE) }.padding(8.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text("itunda Bank 0214-3058-1980", color = Ids.colors.textTertiary, fontSize = 14.sp)
                Text("842,300 RWF", color = Ids.colors.textPrimary, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(vertical = 6.dp))
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Ids.colors.brand.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { Text("⚡", fontSize = 18.sp) }
                    Column(Modifier.padding(start = 14.dp)) {
                        Text("Interest so far 1,462 RWF", color = Ids.colors.textTertiary, fontSize = 13.sp)
                        Text("Interest is added every day", color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth().height(8.dp).background(Ids.colors.surfaceSoft))
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                BankChip("All", true, {})
                demoTx.forEach { t ->
                    Text(t.day, color = Ids.colors.textTertiary, fontSize = 14.sp, modifier = Modifier.padding(top = 22.dp, bottom = 10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
                            Text(if (t.incoming) "↓" else "➤", color = Ids.colors.textBrand, fontSize = 18.sp)
                        }
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(t.name, color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(t.time, color = Ids.colors.textTertiary, fontSize = 13.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(t.amount, color = if (t.incoming) Ids.colors.textBrand else Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(t.after, color = Ids.colors.textTertiary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CtaHalf("Fill", false, Modifier.weight(1f)) { onOpen(BankSub.DEPOSIT) }
            CtaHalf("Send", true, Modifier.weight(1f)) { onOpen(BankSub.SEND) }
        }
    }
}

@Composable
private fun CtaHalf(label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(56.dp).clip(RoundedCornerShape(16.dp)).background(if (primary) Ids.colors.brand else Ids.colors.brand.copy(alpha = 0.15f)).pressScaleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (primary) Ids.colors.textPrimary else Ids.colors.textBrand, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold) }
}

@Composable
internal fun BankStatementScreen(onBack: () -> Unit) {
    var period by remember { mutableStateOf(0) }
    var format by remember { mutableStateOf(0) }
    var done by remember { mutableStateOf(false) }
    val periods = listOf("This month", "Last 3 months", "This year")
    val formats = listOf("CSV", "PDF")
    if (done) { BankDone("Statement ready", "${periods[period]} as ${formats[format]}. We also emailed you a copy.", onBack); return }
    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        BankSubTopBar("", onBack)
        Column(Modifier.weight(1f).padding(horizontal = 24.dp)) {
            Text("Choose a period\nand format.", color = Ids.colors.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 38.sp)
            BankSectionLabel("Period")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { periods.forEachIndexed { i, p -> BankChip(p, period == i, { period = i }, Modifier.weight(if (i == 1) 1.2f else 1f)) } }
            BankSectionLabel("Format")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { formats.forEachIndexed { i, f -> BankChip(f, format == i, { format = i }) } }
        }
        BankSubCta("Download") { done = true }
    }
}

private data class Pocket(val name: String, val emoji: String, val amount: Long, val goal: Long? = null)

@Composable
internal fun BankPocketsScreen(onBack: () -> Unit) {
    val pockets = remember {
        mutableStateListOf(Pocket("Interest jar", "💰", 0), Pocket("Emergency fund", "🔒", 50_000), Pocket("School fees", "🎓", 120_000, 300_000))
    }
    var open by remember { mutableStateOf<Pocket?>(null) }
    open?.let { BankPocketDetail(it.name, it.emoji, it.amount) { open = null }; return }
    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        BankSubTopBar("", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Text("Total in pockets", color = Ids.colors.textTertiary, fontSize = 14.sp)
            Text("${rwf(pockets.sumOf { it.amount })} RWF", color = Ids.colors.textPrimary, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            pockets.forEach { p ->
                Row(Modifier.fillMaxWidth().pressScaleClickable { open = p }.padding(top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) { Text(p.emoji, fontSize = 22.sp) }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(p.name, color = Ids.colors.textTertiary, fontSize = 13.sp)
                        Text("${rwf(p.amount)} RWF", color = Ids.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        p.goal?.let { g ->
                            Spacer(Modifier.height(6.dp))
                            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Ids.colors.divider)) {
                                Box(Modifier.fillMaxWidth((p.amount.toFloat() / g).coerceIn(0f, 1f)).height(6.dp).background(Ids.colors.brand))
                            }
                            Text("${(p.amount * 100 / g)}% of ${rwf(g)} RWF", color = Ids.colors.textTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            Row(Modifier.padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Trip" to "✈️", "Phone" to "📱", "Custom" to "✨").forEach { (n, e) ->
                    BankChip("+ $n", false, { if (pockets.none { it.name == n }) pockets.add(Pocket(n, e, 0)) })
                }
            }
        }
    }
}

/** Hub row opening Pockets. */
@Composable
internal fun BankPocketsRow(count: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().pressScaleClickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Pockets", color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text("$count", color = Ids.colors.textTertiary, fontSize = 15.sp)
        Text("  ›", color = Ids.colors.textTertiary, fontSize = 20.sp)
    }
}

@Composable
fun BankSubOverlay(sub: BankSub?, onClose: () -> Unit, onOpen: (BankSub) -> Unit) {
    when (sub) {
        null -> Unit
        BankSub.ACCOUNT -> BankAccountScreen(onClose, onOpen)
        BankSub.DEPOSIT -> BankDepositScreen { onClose() }
        BankSub.SEND -> BankSendScreen { onClose() }
        BankSub.MOVE -> BankMoveScreen { onClose() }
        BankSub.STATEMENT -> BankStatementScreen { onClose() }
        BankSub.POCKETS -> BankPocketsScreen(onClose)
        BankSub.MANAGE -> BankManageScreen(onClose, onOpen)
        BankSub.LIMITS -> BankLimitsScreen { onOpen(BankSub.MANAGE) }
    }
}
