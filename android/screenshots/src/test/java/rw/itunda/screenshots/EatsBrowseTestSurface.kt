package rw.itunda.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsTheme
import rw.itunda.feature.eats.impl.EatsBrowseHeader
import rw.itunda.feature.eats.impl.EatsBrowseOffers
import rw.itunda.feature.eats.impl.EatsBrowseState
import rw.itunda.feature.eats.impl.EatsDeliveryPickupRow
import rw.itunda.feature.eats.impl.EatsSearchHeader

@Composable
internal fun EatsBrowseTestSurface(
    state: EatsBrowseState,
    darkTheme: Boolean = false,
    searchInput: String = "",
    cartCount: Int = 0,
    onSearchChange: (String) -> Unit = {},
    onOrdersClick: () -> Unit = {},
    onCartClick: () -> Unit = {},
) {
    IdsTheme(darkTheme = darkTheme) {
        Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
            EatsBrowseHeader(state, searchInput, cartCount, onSearchChange, onOrdersClick, onCartClick)
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                EatsDeliveryPickupRow(state)
                EatsSearchHeader(
                    searchInput = searchInput,
                    onSearchChange = onSearchChange,
                    categories = listOf("Brochette", "Chicken", "Pizza", "Burgers"),
                    selectedCategory = null,
                    onSelectCategory = {},
                    showSearch = false,
                )
                EatsBrowseOffers(state)
            }
        }
    }
}
