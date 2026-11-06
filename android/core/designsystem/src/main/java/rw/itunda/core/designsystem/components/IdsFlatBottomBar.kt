package rw.itunda.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.theme.Ids

data class IdsBottomTabItem(val label: String, val icon: ImageVector)

/**
 * Flat, edge-to-edge bottom tab bar with a hairline top divider and the same page
 * color as the content above it (UI_UX_GUIDELINES rules 4 and 10) -- shared by the
 * standalone Rider (Picker) and Merchant (Business) apps so the two don't each keep a
 * private copy of the same chrome (ARCHITECTURE_GUIDELINES rule 3). The consumer app's
 * `ItundaBottomBar` is richer (unread badge) and intentionally left as is.
 */
@Composable
fun IdsFlatBottomBar(
    items: List<IdsBottomTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.background(Ids.colors.background).navigationBarsPadding()) {
        Divider(color = Ids.colors.divider, thickness = 0.5.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .pressScaleClickable { onSelect(index) }
                        .semantics {
                            role = Role.Tab
                            selected = isSelected
                        }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (isSelected) Ids.colors.brand else Ids.colors.textTertiary,
                    )
                    Text(
                        text = item.label,
                        fontSize = 11.sp,
                        color = if (isSelected) Ids.colors.brand else Ids.colors.textTertiary,
                    )
                }
            }
        }
    }
}
