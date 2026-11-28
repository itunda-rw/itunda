package rw.itunda.feature.eats.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

/** Prototype menu row: name, description and price on the left; photo tile with a "+" badge on the right. */
@Composable
fun EatsMenuItemRow(
    name: String,
    description: String?,
    price: String,
    originalPrice: String?,
    imageUrl: String?,
    qty: Int,
    optionsHint: String?,
    onAdd: () -> Unit,
    onMinus: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenDetails: () -> Unit = {},
    art: String = "bread",
) {
    Row(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).pressScaleClickable(onClick = onOpenDetails)) {
            Text(name, color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            if (!description.isNullOrBlank()) {
                Text(description, color = Ids.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(price, color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (originalPrice != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(originalPrice, color = Ids.colors.textTertiary, fontSize = 12.sp, textDecoration = TextDecoration.LineThrough)
                }
            }
            if (optionsHint != null) Text(optionsHint, color = Ids.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(16.dp))
        Box(Modifier.size(104.dp).pressScaleClickable(onClick = onOpenDetails)) {
            Box(Modifier.size(104.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFFFFE8C7)), contentAlignment = Alignment.Center) {
                if (imageUrl != null) MenuItemThumb(imageUrl, 104.dp)
                else ItundaArt(art, ItundaArtSet.Color, 58.dp)
            }
            if (qty == 0) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(6.dp).size(38.dp).clip(CircleShape).background(Ids.colors.brand).pressScaleClickable(onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) { Text("+", color = Color(0xFF17171C), fontSize = 24.sp, fontWeight = FontWeight.Bold) }
            } else {
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).clip(RoundedCornerShape(16.dp)).background(Ids.colors.surfaceSoft),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text("−", Modifier.pressScaleClickable(onClick = onMinus).padding(horizontal = 10.dp, vertical = 4.dp), color = Ids.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(qty.toString(), color = Ids.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("+", Modifier.pressScaleClickable(onClick = onAdd).padding(horizontal = 10.dp, vertical = 4.dp), color = Ids.colors.brand, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
