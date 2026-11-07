package rw.itunda.core.designsystem.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.theme.Ids

/**
 * Filter/category chip, per the v998b/v1000 prototype pass ("chips carry no brand
 * colour -- Toss avoids colour noise"): selected = neutral ink fill with a page-colour
 * label, unselected = soft grey fill with a secondary label. Brand colour stays on
 * primary buttons, switches and links only.
 *
 * Ink/page colours come from semantic tokens, so the pair inverts correctly in dark
 * mode (white fill, dark label) instead of the old hard-coded `Color.White` on brand.
 */
@Composable
fun IdsChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = rememberPressScale(interactionSource)
    Text(
        label,
        color = if (selected) Ids.colors.background else Ids.colors.textSecondary,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
        fontSize = 14.sp,
        modifier = modifier
            .scale(pressScale)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Ids.colors.textPrimary else Ids.colors.surfaceSoft)
            .semantics { role = Role.Tab; this.selected = selected }
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
