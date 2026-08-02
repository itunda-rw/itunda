package rw.itunda.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import rw.itunda.core.designsystem.theme.Ids

/**
 * Real fix, 2026-08-03: a real device screenshot (system dark mode on) showed every
 * card on Home reading as one flat, undifferentiated dark-navy blob -- Material3's
 * default `Card` elevation is a drop shadow, and a drop shadow disappears when the
 * page background is already near-black (Ids.colors.shadow's alpha-black doesn't
 * show up against Ids.colors.background). ItundaAppScreen.kt had 9 separate
 * `Card(colors = CardDefaults.cardColors(containerColor = TossCard), elevation = ...)`
 * call sites hand-rolling the same shadow-reliant pattern, none of them with any
 * other form of edge definition.
 *
 * The actual fix isn't a bigger shadow (still invisible on near-black) -- it's a
 * real, always-visible 1dp hairline border in Ids.colors.divider, which is real
 * Toss's own recovery for the exact same problem (dark surfaces get a subtle
 * hairline, not a stronger shadow). Works identically well in light mode (divider
 * there is a soft light gray) so this isn't a dark-mode-only special case.
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
        border = BorderStroke(1.dp, Ids.colors.divider),
        content = content,
    )
}
