package rw.itunda.core.designsystem.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import rw.itunda.core.designsystem.theme.Ids

/**
 * Real fix, 2026-08-03, second correction same day: the actual bug behind "Home
 * still has theme problems" (after the border was already dropped) was neither the
 * border nor the shadow -- it was this component's own `elevation =
 * Ids.layout.cardElevation` default. Passing any nonzero elevation to a Material3
 * `Card` whose containerColor equals `MaterialTheme.colorScheme.surface` (which
 * `Ids.colors.surface` does, numerically) triggers M3's automatic dark-mode
 * tonal-elevation overlay -- it blends `colorScheme.surfaceTint` (itunda's brand
 * blue) into the card background, proportional to elevation. Pixel-sampled on a
 * real device screenshot: Home's cards read #212737 (visibly blue-navy) while
 * Talk's "New chat" card -- built with a plain `Card(colors = ...)` and no
 * elevation param at all -- reads #202126, a clean, untinted match for
 * Ids.colors.surface's real defined value. Shop's cards (reverted to the same
 * elevation-less plain `Card(...)` pattern earlier today) show the same clean
 * result. Dropped elevation entirely here to match.
 */
@Composable
fun IdsCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Ids.layout.cardCornerRadius),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Ids.colors.surface),
        content = content,
    )
}
