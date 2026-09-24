package rw.itunda.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsColors
import rw.itunda.core.designsystem.theme.IdsTypography

/**
 * Real fix, found live 2026-08-05 auditing the app on a real emulator: Talk's
 * Direct/Groups toggle hand-rolled this exact shape with
 * `.background(Ids.colors.textTertiary)` on the track -- a TEXT color token used as a
 * SURFACE fill, which is why the unselected pill rendered as flat mid-gray with black
 * text: the same visual signature Android uses for a *disabled* button, not an
 * unselected-but-tappable one. No shared segmented-control component existed, so
 * every screen that needed this shape would have hit the same trap.
 */
@Composable
fun <T> IdsSegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Ids.colors.surface)
            .padding(4.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) Ids.colors.brand else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = IdsTypography.Body2,
                    color = if (isSelected) IdsColors.White else Ids.colors.textSecondary,
                )
            }
        }
    }
}
