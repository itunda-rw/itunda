package rw.itunda.feature.front.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

@Composable
internal fun AddPill(qty: Int, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    if (qty == 0) {
        Box(modifier.height(44.dp).clip(RoundedCornerShape(14.dp)).background(Ids.colors.brand.copy(alpha = 0.15f)).pressScaleClickable { onChange(1) }, contentAlignment = Alignment.Center) {
            Text("Add", color = Ids.colors.textBrand, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }
    } else {
        Row(modifier.height(44.dp).clip(RoundedCornerShape(14.dp)).background(Ids.colors.brand.copy(alpha = 0.15f)), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(44.dp).pressScaleClickable { onChange(qty - 1) }, contentAlignment = Alignment.Center) { Text("−", color = Ids.colors.textBrand, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
            Text("$qty", color = Ids.colors.textBrand, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Box(Modifier.weight(1f).height(44.dp).pressScaleClickable { onChange(qty + 1) }, contentAlignment = Alignment.Center) { Text("+", color = Ids.colors.textBrand, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
        }
    }
}

@Composable
internal fun FrontMenuStep(qty: Map<String, Int>, onQty: (String, Int) -> Unit) {
    var group by rememberSaveable { mutableIntStateOf(0) }
    val groups = listOf("All", "Mains", "Sides and sweets", "Drinks")
    Text("Start with these", color = Ids.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp))
    Text("Easy picks if you are not sure", color = Ids.colors.textTertiary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 28.dp))
    Spacer(Modifier.height(12.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        frontDishes.filter { it.badge != null }.forEach { d ->
            Column(Modifier.width(178.dp).clip(RoundedCornerShape(24.dp)).background(Ids.colors.surfaceSoft).padding(12.dp)) {
                Box(Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(16.dp)).background(Ids.colors.background), contentAlignment = Alignment.Center) { ItundaArt(d.art, ItundaArtSet.Color, 48.dp) }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Ids.colors.brand.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text(d.badge!!, color = Ids.colors.textBrand, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(8.dp))
                Text(d.name, color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(rwf(d.price), color = Ids.colors.textTertiary, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                AddPill(qty[d.id] ?: 0, Modifier.fillMaxWidth()) { onQty(d.id, it) }
            }
        }
    }
    Spacer(Modifier.height(28.dp))
    Text("All menu", color = Ids.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 28.dp))
    Spacer(Modifier.height(12.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        groups.forEachIndexed { i, label ->
            val on = group == i
            Box(Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(if (on) Ids.colors.textPrimary else androidx.compose.ui.graphics.Color.Transparent).pressScaleClickable { group = i }.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (on) Ids.colors.background else Ids.colors.textPrimary)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    frontDishes.filter { group == 0 || it.group == groups[group] }.forEach { d ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(d.name, color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text(d.note, color = Ids.colors.textTertiary, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text(rwf(d.price), color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) { ItundaArt(d.art, ItundaArtSet.Color, 38.dp) }
                Spacer(Modifier.height(8.dp))
                AddPill(qty[d.id] ?: 0, Modifier.width(80.dp)) { onQty(d.id, it) }
            }
        }
    }
}
