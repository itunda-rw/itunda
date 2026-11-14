package rw.itunda.rider.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import rw.itunda.core.designsystem.components.IdsBottomTabItem
import rw.itunda.core.designsystem.components.IdsFlatBottomBar

/** The three places a rider goes, same split as the Picker prototype's bottom tabs. */
enum class RiderTab(val label: String, val icon: ImageVector) {
    Work("Work", Icons.Outlined.LocalShipping),
    Earnings("Earnings", Icons.Outlined.AccountBalanceWallet),
    Me("Me", Icons.Outlined.Person),
}

@Composable
fun RiderBottomBar(selected: RiderTab, onSelect: (RiderTab) -> Unit) {
    IdsFlatBottomBar(
        items = RiderTab.entries.map { IdsBottomTabItem(it.label, it.icon) },
        selectedIndex = selected.ordinal,
        onSelect = { onSelect(RiderTab.entries[it]) },
    )
}
