package rw.itunda.feature.banking.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

/** Prototype `mv` (Fill / Take out): from/to header with a connector, amount sentence, +chips, keypad, Next, then a confirm sheet. */
@Composable
internal fun PocketMoveScreen(name: String, emoji: String, dir: Int, pocketBalance: Long, onBack: () -> Unit, onDone: (Long) -> Unit) {
    val inn = dir > 0
    val max = if (inn) POCKET_BANK_BALANCE else pocketBalance
    var digits by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    val v = digits.toLongOrNull() ?: 0L
    val bad = v > max
    val verb = if (inn) "Fill" else "Take out"
    val after = if (inn) pocketBalance + v else pocketBalance - v
    if (confirm) PocketConfirmDialog(verb, v, if (inn) "itunda Bank" else name, if (inn) name else "itunda Bank", after, { confirm = false }) { confirm = false; onDone(v) }
    val from = if (inn) "From itunda Bank" to "Available ${rwf(POCKET_BANK_BALANCE)} RWF" else "From $name" to "Can take out ${rwf(pocketBalance)} RWF"
    val to = if (inn) "To $name" to "Balance ${rwf(pocketBalance)} RWF" else "To itunda Bank" to "Balance ${rwf(POCKET_BANK_BALANCE)} RWF"
    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        BankSubTopBar("", onBack)
        Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                listOf(from, to).forEach { (a, b) ->
                    Column(Modifier.height(68.dp), verticalArrangement = Arrangement.Center) {
                        Text(a, color = Ids.colors.textPrimary, fontSize = 19.sp, letterSpacing = (-0.4).sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        Text(b, color = Ids.colors.textTertiary, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            Column(Modifier.width(38.dp).padding(top = 15.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                MoveIcon(if (inn) null else name, emoji)
                val line = if (inn) Brush.verticalGradient(listOf(Ids.colors.surfaceSoft, Ids.colors.brand)) else Brush.verticalGradient(listOf(Ids.colors.surfaceSoft, Ids.colors.surfaceSoft))
                Box(Modifier.size(width = 3.dp, height = 30.dp).clip(RoundedCornerShape(2.dp)).background(line))
                MoveIcon(if (inn) name else null, emoji)
            }
        }
        val amountText = when {
            v == 0L -> buildAnnotatedString { withStyle(SpanStyle(fontSize = 26.sp)) { append(if (inn) "How much to fill?" else "How much to take out?") } }
            else -> buildAnnotatedString {
                append(rwf(v)); withStyle(SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold)) { append(" RWF") }
                if (!bad) withStyle(SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ids.colors.textTertiary)) { append(if (inn) " to fill?" else " to take out?") }
            }
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 96.dp).padding(start = 24.dp, end = 24.dp, top = 36.dp)) {
            Text(amountText, color = if (bad) Ids.colors.danger else if (v > 0) Ids.colors.textPrimary else Ids.colors.textTertiary, fontSize = if (v > 0) 40.sp else 26.sp, lineHeight = 48.sp, letterSpacing = (if (v > 0) -1.0 else -0.6).sp, fontWeight = FontWeight.ExtraBold)
        }
        val msg = when {
            bad -> if (inn) "That’s more than your balance of ${rwf(POCKET_BANK_BALANCE)} RWF. Try a smaller amount." else "Only ${rwf(pocketBalance)} RWF in this pocket"
            v > 0 -> "Pocket balance after: ${rwf(after)} RWF"
            else -> ""
        }
        Text(msg, color = if (bad) Ids.colors.danger else Ids.colors.textTertiary, fontSize = 14.sp, modifier = Modifier.heightIn(min = 24.dp).padding(start = 24.dp, end = 24.dp, top = 6.dp))
        Spacer(Modifier.weight(1f))
        val steps = if (max >= 1_000_000) listOf(100_000L, 1_000_000L) else if (max >= 100_000) listOf(10_000L, 100_000L) else if (max >= 10_000) listOf(1_000L, 10_000L) else listOf(1_000L, 5_000L)
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            steps.forEach { n -> MoveChip("+${rwf(n)}", max != 0L && v + n <= max, Modifier.weight(1f)) { digits = (v + n).toString().take(9) } }
            MoveChip("All", max != 0L && v < max, Modifier.weight(1f)) { digits = max.toString() }
        }
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("00", "0", "⌫")).forEach { row ->
            Row(Modifier.fillMaxWidth().height(56.dp)) {
                row.forEach { k ->
                    Box(
                        Modifier.weight(1f).fillMaxHeight().pressScaleClickable {
                            digits = when (k) { "⌫" -> digits.dropLast(1); else -> (digits + k).trimStart('0').let { if (it.length > 9) digits else it } }
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (k == "⌫") ItundaArt("back", ItundaArtSet.Line, 28.dp, tint = Ids.colors.textPrimary) else Text(k, color = Ids.colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        BankSubCta("Next", enabled = v > 0 && !bad, top = 8.dp) { confirm = true }
    }
}

@Composable
private fun MoveIcon(pocket: String?, emoji: String) {
    Box(Modifier.size(38.dp).clip(CircleShape).background(Ids.colors.brand.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        if (pocket == null) ItundaArt("bank", ItundaArtSet.Proto, 20.dp, fit = true) else Text(emoji, fontSize = 18.sp)
    }
}

@Composable
private fun MoveChip(label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(38.dp).alpha(if (enabled) 1f else 0.35f).clip(RoundedCornerShape(19.dp)).background(Ids.colors.surfaceSoft)
            .then(if (enabled) Modifier.pressScaleClickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

/** Prototype mvAsk sheet: summary rows, Cancel and the action. */
@Composable
private fun PocketConfirmDialog(verb: String, v: Long, from: String, to: String, after: Long, onCancel: () -> Unit, onGo: () -> Unit) {
    Dialog(onDismissRequest = onCancel) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ids.colors.background).padding(24.dp)) {
            Text("$verb ${rwf(v)} RWF?", color = Ids.colors.textPrimary, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold)
            Column(Modifier.padding(top = 16.dp, bottom = 20.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ids.colors.surfaceSoft).padding(horizontal = 18.dp, vertical = 2.dp)) {
                listOf("From" to from, "To" to to, "Fee" to "0 RWF", "Pocket balance after" to "${rwf(after)} RWF").forEach { (a, b) ->
                    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(a, color = Ids.colors.textTertiary, fontSize = 15.sp)
                        Text(b, color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PocketDialogButton("Cancel", false, Modifier.weight(1f)) { onCancel() }
                PocketDialogButton(verb, true, Modifier.weight(1f)) { onGo() }
            }
        }
    }
}
