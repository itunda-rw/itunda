package rw.itunda.core.designsystem.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import rw.itunda.core.designsystem.theme.Ids

/**
 * Real fix, 2026-08-03, corrected same day: an earlier pass here added a 1dp
 * Ids.colors.divider hairline border, theorizing Material3's default shadow-only
 * elevation was invisible in dark mode. The user directly corrected this after
 * comparing on-device: the plain, border-less `Card(containerColor =
 * Ids.colors.surface)` pattern already used on other tabs (Shop, etc.) looks right
 * as-is -- the border made Home look inconsistent with the rest of the app, not
 * more correct. Dropped. This component now exists purely to avoid re-hand-rolling
 * `Card(shape = ..., colors = CardDefaults.cardColors(containerColor =
 * Ids.colors.surface), elevation = ...)` at every call site, not to change how a
 * card actually looks.
 */
@Composable
fun IdsCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Ids.layout.cardCornerRadius),
    elevation: Dp = Ids.layout.cardElevation,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Ids.colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        content = content,
    )
}
