package rw.itunda.rider.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Person
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
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

/** The three places a rider goes, same split as the Picker prototype's bottom tabs. */
enum class RiderTab(val label: String, val icon: ImageVector) {
    Work("Work", Icons.Outlined.LocalShipping),
    Earnings("Earnings", Icons.Outlined.AccountBalanceWallet),
    Me("Me", Icons.Outlined.Person),
}

/**
 * Flat, edge-to-edge bottom bar with a hairline top divider -- the same shape as the
 * consumer app's `ItundaBottomBar` (`:app` internal, so not importable from here; a
 * small stateless chrome composable is cheaper to keep per app than to couple the
 * mini-apps to `:app`, see ARCHITECTURE_GUIDELINES.md rule 1's duplication carve-out).
 */
@Composable
fun RiderBottomBar(selected: RiderTab, onSelect: (RiderTab) -> Unit) {
    Column(modifier = Modifier.background(Ids.colors.background).navigationBarsPadding()) {
        Divider(color = Ids.colors.divider, thickness = 0.5.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RiderTab.entries.forEach { tab ->
                val isSelected = tab == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .pressScaleClickable { onSelect(tab) }
                        .semantics {
                            role = Role.Tab
                            this.selected = isSelected
                        }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (isSelected) Ids.colors.brand else Ids.colors.textTertiary,
                    )
                    Text(
                        text = tab.label,
                        fontSize = 11.sp,
                        color = if (isSelected) Ids.colors.brand else Ids.colors.textTertiary,
                    )
                }
            }
        }
    }
}
