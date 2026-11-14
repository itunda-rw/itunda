package rw.itunda.feature.banking.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import java.util.Locale

/** The itunda Bank sub-screens that open over the hub. Dummy data throughout until the backend lands. */
enum class BankSub { ACCOUNT, DEPOSIT, SEND, MOVE, STATEMENT, POCKETS, MANAGE }

internal fun rwf(n: Long): String = String.format(Locale.US, "%,d", n)

@Composable
internal fun BankSubTopBar(title: String, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).pressScaleClickable(onClick = onBack), contentAlignment = Alignment.CenterStart) {
            ItundaArt("back", ItundaArtSet.Line, 24.dp, tint = Ids.colors.textPrimary)
        }
        if (title.isNotEmpty()) Text(title, color = Ids.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        else Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
internal fun BankSubCta(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp).height(56.dp).clip(RoundedCornerShape(16.dp))
            .background(if (enabled) Ids.colors.brand else Ids.colors.brand.copy(alpha = 0.3f))
            .then(if (enabled) Modifier.pressScaleClickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold) }
}

@Composable
internal fun BankChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(50)).background(if (selected) Ids.colors.textPrimary else Ids.colors.surfaceSoft)
            .pressScaleClickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (selected) Ids.colors.background else Ids.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) }
}

@Composable
internal fun BankSectionLabel(text: String) {
    Text(text, color = Ids.colors.textTertiary, fontSize = 14.sp, modifier = Modifier.padding(top = 24.dp, bottom = 10.dp))
}

/** Local confirmation shown after a dummy action, so every flow ends somewhere. */
@Composable
internal fun BankDone(title: String, body: String, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        Column(Modifier.weight(1f).padding(horizontal = 24.dp), verticalArrangement = Arrangement.Center) {
            Text(title, color = Ids.colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            Text(body, color = Ids.colors.textTertiary, fontSize = 16.sp)
        }
        BankSubCta("Done", onClick = onClose)
    }
}
