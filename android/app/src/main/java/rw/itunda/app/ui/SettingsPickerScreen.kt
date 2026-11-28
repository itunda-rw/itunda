package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.app.R
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons

/** One selectable row of a [SettingsPickerScreen]. */
internal data class PickerOption(val label: String, val selected: Boolean, val fontSizeSp: Float = 17f, val onPick: () -> Unit)

/**
 * Prototype full-screen picker (theme, text size, language): back arrow, big title,
 * then one tall row per option -- the chosen one in ink with a brand check, the rest
 * in grey. Replaces the earlier AlertDialog pickers; picks apply immediately.
 */
@Composable
internal fun SettingsPickerScreen(title: String, options: List<PickerOption>, onBack: () -> Unit, intro: String? = null) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).size(44.dp).clip(CircleShape).pressScaleClickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(IdsIcons.Back, contentDescription = stringResource(R.string.settings_back), modifier = Modifier.size(18.dp), tint = Ids.colors.textPrimary)
        }
        Text(title, color = Ids.colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp))
        if (intro != null) Text(intro, color = Ids.colors.textSecondary, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        options.forEach { o ->
            Row(
                modifier = Modifier.fillMaxWidth().pressScaleClickable(onClick = o.onPick).padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    o.label, modifier = Modifier.weight(1f), fontSize = o.fontSizeSp.sp, fontWeight = FontWeight.SemiBold,
                    color = if (o.selected) Ids.colors.textPrimary else Ids.colors.textTertiary,
                )
                if (o.selected) ItundaArt("check", ItundaArtSet.Line, 22.dp, tint = Ids.colors.brand)
            }
        }
    }
}
