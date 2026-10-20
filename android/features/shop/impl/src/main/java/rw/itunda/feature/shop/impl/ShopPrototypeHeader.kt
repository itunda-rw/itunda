package rw.itunda.feature.shop.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

/** Shared prototype-v66 Shop header. Search and cart remain connected to real Shop state. */
@Composable
internal fun ShopPrototypeHeader(
    searchInput: String,
    onSearchInputChange: (String) -> Unit,
    onSearch: () -> Unit,
    searchEnabled: Boolean,
    totalItems: Int,
    onCart: () -> Unit,
    onBack: () -> Unit,
    onOpenEats: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(Ids.colors.surface).pressScaleClickable { onBack() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = Ids.colors.textPrimary) }
            Box(modifier = Modifier.weight(1f)) {
                IdsTextField(
                    value = searchInput,
                    onValueChange = onSearchInputChange,
                    label = "Search products",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(Ids.colors.surface).pressScaleClickable(enabled = searchEnabled, onClick = onSearch),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Search, contentDescription = "Search products", tint = Ids.colors.textPrimary) }
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(Ids.colors.surface).pressScaleClickable(onClick = onCart),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.ShoppingCart, contentDescription = "Cart", tint = Ids.colors.textPrimary)
                if (totalItems > 0) {
                    Box(
                        modifier = Modifier.align(Alignment.TopEnd).padding(2.dp)
                            .clip(RoundedCornerShape(9.dp)).background(Ids.colors.brand)
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(totalItems.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(Ids.colors.surface).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                    .background(Ids.colors.brand).pressScaleClickable { /* Shop is the current destination. */ }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Shop", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                    .pressScaleClickable(onClick = onOpenEats).padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Eats", color = Ids.colors.textSecondary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
        }
    }
}
