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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons

private data class Tx(val day: String, val name: String, val time: String, val amount: String, val after: String, val incoming: Boolean)

private val demoTx = listOf(
    Tx("Yesterday", "Amina K.", "18:20", "+25,000 RWF", "842,300 RWF", true),
    Tx("9 Oct", "Rent", "04:25", "−150,000 RWF", "817,300 RWF", false),
    Tx("6 Oct", "Jean Paul", "05:25", "−30,000 RWF", "967,300 RWF", false),
)

/** Prototype account detail (`.bdt` / `.bdb` / `.pkrow` / `.bdr`): 64dp bar with Card / Statement / Manage pills, 36sp balance, interest row, grouped activity, Fill / Send. */
@Composable
internal fun BankAccountScreen(onBack: () -> Unit, onOpen: (BankSub) -> Unit) {
    Box(Modifier.fillMaxSize().background(Ids.colors.background)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 120.dp)) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 6.dp, top = 16.dp, end = 12.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(48.dp).pressScaleClickable(onClick = onBack), contentAlignment = Alignment.Center) { ItundaArt("back", ItundaArtSet.Line, 24.dp, tint = Ids.colors.textPrimary) }
                Spacer(Modifier.weight(1f))
                Row(Modifier.padding(top = 2.dp)) {
                    AccountAction("card", "Card") { onOpen(BankSub.CARDS) }
                    AccountAction("receipt-rw", "Statement") { onOpen(BankSub.STATEMENT) }
                    AccountAction("gear", "Manage") { onOpen(BankSub.MANAGE) }
                }
            }
            Text("itunda Bank 0214-3058-1980", color = Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 19.6.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp, modifier = Modifier.padding(start = 24.dp, top = 22.dp))
            Text("842,300 RWF", color = Ids.colors.textPrimary, fontSize = 36.sp, lineHeight = 50.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp, modifier = Modifier.padding(start = 24.dp))
            Row(Modifier.fillMaxWidth().height(77.2.dp).pressScaleClickable { onOpen(BankSub.MANAGE) }.padding(start = 24.dp, end = 24.dp, top = 18.dp)) {
                Box(Modifier.padding(top = 1.6.dp).size(44.dp).clip(CircleShape).background(Ids.colors.brand.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { ItundaArt("bolt-solid", ItundaArtSet.Solid, 24.dp, tint = Color(0xFFFFC433)) }
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text("Interest so far 1,462 RWF", color = Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.2).sp)
                    Text("Interest is added every day", color = Ids.colors.textPrimary, fontSize = 18.sp, lineHeight = 25.2.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, modifier = Modifier.padding(top = 2.dp))
                }
                Box(Modifier.padding(top = 15.dp)) { ItundaArt("chev", ItundaArtSet.Line, 16.dp, tint = Ids.colors.textPrimary) }
            }
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth().height(10.dp).background(Ids.colors.surfaceSoft.copy(alpha = 0.5f)))
            var filter by remember { mutableStateOf(0) }
            var menu by remember { mutableStateOf(false) }
            var searching by remember { mutableStateOf(false) }
            var query by remember { mutableStateOf("") }
            Box(Modifier.fillMaxWidth().height(76.dp)) {
                Box(Modifier.offset(48.dp, 34.dp)) {
                    Row(
                        Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(Ids.colors.surfaceSoft).pressScaleClickable { menu = true }.padding(start = 14.dp, end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(listOf("All", "Money in", "Money out")[filter], color = Ids.colors.textPrimary, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)
                        androidx.compose.material3.Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Ids.colors.textPrimary, modifier = Modifier.size(20.dp))
                    }
                    androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        listOf("All", "Money in", "Money out").forEachIndexed { i, l -> androidx.compose.material3.DropdownMenuItem(text = { Text(l) }, onClick = { filter = i; menu = false }) }
                    }
                }
                Box(
                    Modifier.offset(330.dp, 25.dp).size(44.dp).clip(CircleShape).background(Ids.colors.surfaceSoft).pressScaleClickable { searching = !searching; if (!searching) query = "" },
                    contentAlignment = Alignment.Center,
                ) { ItundaArt("search", ItundaArtSet.Line, 20.dp, tint = Ids.colors.textPrimary) }
            }
            if (searching) {
                androidx.compose.foundation.text.BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Ids.colors.textPrimary, fontSize = 16.sp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).clip(RoundedCornerShape(12.dp)).background(Ids.colors.surfaceSoft).padding(12.dp),
                    decorationBox = { inner -> if (query.isEmpty()) Text("Search activity", color = Ids.colors.textTertiary, fontSize = 16.sp) else inner() },
                )
            }
            demoTx.filter { (filter == 0 || (filter == 1) == it.incoming) && it.name.contains(query, ignoreCase = true) }.forEach { t ->
                Text(t.day, color = Ids.colors.textTertiary, fontSize = 13.sp, lineHeight = 18.2.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.1).sp, modifier = Modifier.padding(start = 24.dp, top = 21.dp, bottom = 5.dp))
                Row(Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
                        if (t.incoming) IncomingGlyph() else ItundaArt("send", ItundaArtSet.Proto, 26.dp, fit = true)
                    }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(t.name, color = Ids.colors.textPrimary, fontSize = 17.sp, lineHeight = 23.8.sp, fontWeight = FontWeight.Bold)
                        Text(t.time, color = Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(t.amount, color = if (t.incoming) Ids.colors.textBrand else Ids.colors.textPrimary, fontSize = 17.sp, lineHeight = 23.8.sp, fontWeight = FontWeight.Bold)
                        Text(t.after, color = Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Ids.colors.background).padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 28.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CtaHalf("Fill", false, Modifier.weight(1f)) { onOpen(BankSub.DEPOSIT) }
            CtaHalf("Send", true, Modifier.weight(1f)) { onOpen(BankSub.SEND) }
        }
    }
}

@Composable
private fun AccountAction(art: String, label: String, onClick: () -> Unit) {
    Row(Modifier.height(44.dp).pressScaleClickable(onClick = onClick).padding(horizontal = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        ItundaArt(art, ItundaArtSet.Line, 20.dp, tint = Ids.colors.textTertiary)
        Text(label, color = Ids.colors.textTertiary, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun CtaHalf(label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(58.dp).clip(RoundedCornerShape(18.dp)).background(if (primary) Ids.colors.brand else Ids.colors.brand.copy(alpha = 0.15f)).pressScaleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (primary) Color(0xFF17171C) else Ids.colors.textBrand, fontSize = 18.sp, fontWeight = if (primary) FontWeight.Bold else FontWeight.SemiBold, letterSpacing = (-0.36).sp) }
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
            Spacer(Modifier.height(12.dp))
            Text("Choose a period\nand format.", color = Ids.colors.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp, letterSpacing = (-0.6).sp)
            Spacer(Modifier.height(28.dp))
            StmtLabel("Period")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { periods.forEachIndexed { i, p -> BankChip(p, period == i, { period = i }) } }
            Spacer(Modifier.height(28.dp))
            StmtLabel("Format")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { formats.forEachIndexed { i, f -> BankChip(f, format == i, { format = i }) } }
        }
        BankSubCta("Download") { done = true }
    }
}

/** Prototype incoming tile: mint circle with a white download arrow. */
@Composable
private fun IncomingGlyph() {
    androidx.compose.foundation.Canvas(Modifier.size(28.dp)) {
        drawCircle(Color(0xFF3DCCA0))
        val w = size.width
        val st = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.075f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.5f, w * 0.26f); lineTo(w * 0.5f, w * 0.58f)
            moveTo(w * 0.37f, w * 0.46f); lineTo(w * 0.5f, w * 0.59f); lineTo(w * 0.63f, w * 0.46f)
            moveTo(w * 0.34f, w * 0.72f); lineTo(w * 0.66f, w * 0.72f)
        }
        drawPath(path, Color.White, style = st)
    }
}

@Composable
private fun StmtLabel(text: String) {
    Text(text, color = Ids.colors.textPrimary, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
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
            Text("Total in pockets", color = Ids.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(rwf(pockets.sumOf { it.amount }), color = Ids.colors.textPrimary, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                Text(" RWF", color = Ids.colors.textTertiary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, bottom = 5.dp))
            }
            pockets.forEach { p ->
                Row(Modifier.fillMaxWidth().pressScaleClickable { open = p }.padding(top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(Ids.colors.brand.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        val art = when (p.name) { "Interest jar" -> "interest-jar"; "School fees" -> "school-fees"; else -> null }
                        if (art != null) ItundaArt(art, ItundaArtSet.Color, 28.dp) else Text(p.emoji, fontSize = 22.sp)
                    }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(p.name, color = Ids.colors.textTertiary, fontSize = 13.sp)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(rwf(p.amount), color = Ids.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(" RWF", color = Ids.colors.textTertiary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 1.dp))
                        }
                        p.goal?.let { g ->
                            Spacer(Modifier.height(6.dp))
                            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Ids.colors.divider)) {
                                Box(Modifier.fillMaxWidth((p.amount.toFloat() / g).coerceIn(0f, 1f)).height(6.dp).background(Ids.colors.brand))
                            }
                            Text("${(p.amount * 100 / g)}% of ${rwf(g)} RWF", color = Ids.colors.textTertiary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Trip" to "✈️", "Phone" to "📱", "Custom" to "✨").forEach { (n, e) ->
                    BankChip("+ $n", false, { if (pockets.none { it.name == n }) pockets.add(Pocket(n, e, 0)) }, Modifier.weight(1f))
                }
            }
        }
    }
}

/** Hub row opening Pockets. */
@Composable
internal fun BankPocketsRow(count: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().pressScaleClickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) { ItundaArt("pockets", ItundaArtSet.Color, 26.dp) }
        Text("Pockets", color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 14.dp))
        Text("$count", color = Ids.colors.textSecondary, fontSize = 13.sp)
        Text("  ›", color = Ids.colors.textTertiary, fontSize = 20.sp)
    }
}

@Composable
fun BankSubOverlay(sub: BankSub?, onClose: () -> Unit, onOpen: (BankSub) -> Unit, onAutoTransfers: () -> Unit = {}) {
    when (sub) {
        null -> Unit
        BankSub.ACCOUNT -> BankAccountScreen(onClose, onOpen)
        BankSub.DEPOSIT -> BankDepositScreen { onClose() }
        BankSub.SEND -> BankSendScreen { onClose() }
        BankSub.MOVE -> BankMoveScreen { onClose() }
        BankSub.STATEMENT -> BankStatementScreen { onClose() }
        BankSub.POCKETS -> BankPocketsScreen(onClose)
        BankSub.MANAGE -> BankManageScreen(onClose, onOpen, onAutoTransfers)
        BankSub.LIMITS -> BankLimitsScreen { onOpen(BankSub.MANAGE) }
        BankSub.CARDS -> BankCardsScreen(onClose)
        BankSub.PAYEES -> BankPayeesScreen(onClose)
        BankSub.SCHEDULED -> BankScheduledScreen(onClose)
        BankSub.BUDGET -> BankBudgetScreen(onClose)
        BankSub.CASES -> BankCasesScreen(onClose)
        BankSub.DOCS -> BankDocsScreen(onClose, onOpen)
        BankSub.DETAILS -> BankDetailsScreen(onClose)
    }
}
