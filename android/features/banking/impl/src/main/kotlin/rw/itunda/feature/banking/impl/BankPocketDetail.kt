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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

/** Pocket detail (prototype "pocket"): Manage chip, 76dp icon, balance, two rows, empty state, Fill. */
@Composable
internal fun BankPocketDetail(
    name: String, emoji: String, amount: Long, canDelete: Boolean, isJar: Boolean, taken: List<String>,
    onRename: (String) -> Unit, onDelete: () -> Unit, onBack: () -> Unit,
) {
    var done by remember { mutableStateOf(false) }
    var manage by remember { mutableStateOf(false) }
    var autoFill by remember { mutableStateOf(false) }
    if (done) { BankDone("Pocket filled", "Added to $name from itunda Bank.", onBack); return }
    if (manage) PocketManageDialog(name, isJar, canDelete, taken, { manage = false }, onRename, onDelete)
    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        BankSubTopBar("", onBack) {
            Box(
                Modifier.padding(top = 0.dp).height(36.dp).clip(RoundedCornerShape(12.dp)).background(Ids.colors.surfaceSoft)
                    .pressScaleClickable { manage = true }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Manage", color = Ids.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.padding(start = 24.dp).size(76.dp).clip(CircleShape).background(Ids.colors.brand.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) { PocketGlyph(name, emoji, 40.dp) }
            Text(name, color = Ids.colors.textTertiary, fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = (-0.5).sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 24.dp, top = 12.dp))
            Row(Modifier.padding(start = 24.dp, top = 4.dp).height(53.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(rwf(amount), color = Ids.colors.textPrimary, fontSize = 34.sp, lineHeight = 48.sp, letterSpacing = (-1.4).sp, fontWeight = FontWeight.Bold)
                Text(" RWF", color = Ids.colors.textTertiary, fontSize = 18.7.sp, letterSpacing = (-0.2).sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(40.dp))
            PocketInfoRow("Interest received", "0", "RWF", 79.2.dp)
            PocketInfoRow("Auto-fill", if (autoFill) "On" else "Off", null, 81.2.dp)
            Box(Modifier.padding(start = 24.dp, end = 0.dp, top = 7.dp).fillMaxWidth().height(1.dp).background(Ids.colors.surfaceSoft))
            Text("Nothing here yet", color = Ids.colors.textPrimary, fontSize = 18.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp))
            Text("Fill it from itunda Bank, which has 842,300 RWF. Or let it fill itself.", color = Ids.colors.textTertiary, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 6.dp))
            Box(
                Modifier.padding(start = 24.dp, top = 12.dp, bottom = 16.dp).height(44.dp).clip(RoundedCornerShape(22.dp)).background(Ids.colors.surfaceSoft)
                    .pressScaleClickable { autoFill = !autoFill }.padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (autoFill) "Turn off auto-fill" else "Set up auto-fill", color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        }
        BankSubCta("Fill", top = 14.dp) { done = true }
    }
}

@Composable
private fun PocketGlyph(name: String, emoji: String, size: androidx.compose.ui.unit.Dp) {
    when (name) {
        "Interest jar" -> ItundaArt("money", ItundaArtSet.Proto, size, fit = true)
        "Emergency fund" -> ItundaArt("lock", ItundaArtSet.Proto, size, fit = true)
        "School fees" -> ItundaArt("school", ItundaArtSet.Line, size, tint = Ids.colors.textPrimary)
        else -> Text(emoji, fontSize = (size.value * 0.55f).sp)
    }
}

@Composable
private fun PocketInfoRow(label: String, value: String, unit: String?, height: androidx.compose.ui.unit.Dp) {
    Row(Modifier.fillMaxWidth().height(height).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Ids.colors.brand.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            ItundaArt("bolt", ItundaArtSet.Line, 20.dp, tint = Ids.colors.brand)
        }
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(label, color = Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = (-0.2).sp, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, color = Ids.colors.textPrimary, fontSize = 18.sp, lineHeight = 25.2.sp, letterSpacing = (-0.4).sp, fontWeight = FontWeight.Bold)
                unit?.let { Text(" $it", color = Ids.colors.textTertiary, fontSize = 14.sp, letterSpacing = (-0.4).sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 2.dp)) }
            }
        }
        ItundaArt("chev", ItundaArtSet.Line, 16.dp, tint = Ids.colors.textTertiary)
    }
}

/** Prototype pkManage: the Interest jar can't be renamed or deleted; other pockets rename, and delete once empty. */
@Composable
private fun PocketManageDialog(name: String, isJar: Boolean, canDelete: Boolean, taken: List<String>, onClose: () -> Unit, onRename: (String) -> Unit, onDelete: () -> Unit) {
    var text by remember { mutableStateOf(name) }
    val clean = text.trim()
    val clash = taken.any { it.equals(clean, ignoreCase = true) }
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ids.colors.background).padding(24.dp)) {
            if (isJar) {
                Text("Interest jar", color = Ids.colors.textPrimary, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold)
                Text("Put money aside and take it out any time. This pocket is always here, so it can't be renamed or deleted.", color = Ids.colors.textTertiary, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
                PocketDialogButton("Close", false, onClose)
            } else {
                Text("Manage pocket", color = Ids.colors.textPrimary, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold)
                Text("Pocket name", color = Ids.colors.textTertiary, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).background(Ids.colors.surfaceSoft).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                    BasicTextField(text, { if (it.length <= 20) text = it }, singleLine = true, textStyle = TextStyle(color = Ids.colors.textPrimary, fontSize = 16.sp), cursorBrush = SolidColor(Ids.colors.brand), modifier = Modifier.fillMaxWidth())
                }
                if (clash) Text("You already have a pocket with that name", color = Ids.colors.textTertiary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                if (!canDelete) Text("Take out the money before deleting this pocket.", color = Ids.colors.textTertiary, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (canDelete) Box(Modifier.weight(1f)) { PocketDialogButton("Delete", false) { onDelete(); onClose() } }
                    Box(Modifier.weight(1f)) { PocketDialogButton("Save", true, enabled = clean.isNotEmpty() && !clash) { onRename(clean); onClose() } }
                }
            }
        }
    }
}

@Composable
private fun PocketDialogButton(label: String, primary: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp))
            .background(if (primary) Ids.colors.brand.copy(alpha = if (enabled) 1f else 0.3f) else Ids.colors.surfaceSoft)
            .then(if (enabled) Modifier.pressScaleClickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}
