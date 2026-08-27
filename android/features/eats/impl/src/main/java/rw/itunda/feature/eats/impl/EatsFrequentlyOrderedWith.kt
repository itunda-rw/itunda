package rw.itunda.feature.eats.impl

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.FrequentlyOrderedWithItemDto
import rw.itunda.core.network.NetworkClient

// Real "frequently ordered together" cross-sell (itunda Eats redesign, 2026-08-28,
// direct user reference: real Coupang Eats "다른 고객은 함께 주문했어요" rail). See
// backend OrderItemRepository.getFrequentlyOrderedWith's own doc comment: a real,
// derived co-occurrence signal, never a fabricated pairing -- renders nothing at all
// when the real list comes back empty.
@Composable
internal fun EatsFrequentlyOrderedWith(productId: String, onAdd: (FrequentlyOrderedWithItemDto) -> Unit) {
    var items by remember(productId) { mutableStateOf<List<FrequentlyOrderedWithItemDto>>(emptyList()) }
    LaunchedEffect(productId) {
        items = try {
            NetworkClient.apiService.getFrequentlyOrderedWith(productId).products
        } catch (e: Exception) {
            emptyList()
        }
    }
    if (items.isEmpty()) return

    Column(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
        Text("Frequently ordered together", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items.forEach { item ->
                Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface)) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        MenuItemThumb(item.imageUrl, size = 80.dp)
                        Text(item.name, color = Ids.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        Text("%,.0f RWF".format(item.price), color = Ids.colors.textSecondary, fontSize = 12.sp)
                        Row(
                            modifier = Modifier.padding(top = 6.dp).pressScaleClickable(enabled = item.stockQuantity != 0) { onAdd(item) },
                        ) {
                            Text(
                                if (item.stockQuantity == 0) "Out of stock" else "+ Add",
                                color = if (item.stockQuantity == 0) Ids.colors.textTertiary else Ids.colors.brand,
                                fontWeight = FontWeight.Bold, fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
