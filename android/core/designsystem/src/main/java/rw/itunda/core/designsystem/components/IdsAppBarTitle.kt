package rw.itunda.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons

/**
 * The shared title of the Home, Pay and Explore app bars (prototype v1001/v1004): the
 * itunda mark, "itunda" in ink, then the area name ("pay", "mini apps") in grey, then a
 * small chevron. Same logo, same greys, same position on every tab, so moving between
 * tabs never makes the header jump.
 *
 * [area] null = Home ("itunda >"); pass the area name for the others. [onClick] null =
 * not tappable (no chevron).
 */
@Composable
fun IdsAppBarTitle(area: String? = null, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Row(
        modifier = if (onClick != null) modifier.pressScaleClickable(onClick = onClick) else modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(Ids.colors.brand),
            contentAlignment = Alignment.Center,
        ) {
            PetalMark(size = 16.dp)
        }
        Spacer(Modifier.width(8.dp))
        Text("itunda", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        if (area != null) {
            Text(" $area", color = Ids.colors.textSecondary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        }
        if (onClick != null) {
            Icon(
                IdsIcons.ChevronRight, contentDescription = null,
                modifier = Modifier.size(20.dp), tint = Ids.colors.textTertiary,
            )
        }
    }
}
