package rw.itunda.feature.front.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

private data class FrontPlace(val name: String, val rating: String, val reviews: Int, val km: String, val art: String)

private val frontPlaces = listOf(
    FrontPlace("Kigali Roast", "4.4", 212, "0.2 km", "coffee"),
    FrontPlace("Mama Rice House", "4.6", 208, "2.6 km", "rice"),
    FrontPlace("Sweet Tooth Cakes", "4.9", 221, "4.0 km", "cake"),
    FrontPlace("Fresh Press Juice Bar", "4.2", 176, "4.1 km", "juice"),
)

/** Prototype "Front" (order, pay and go) landing, demo data until the backend exists. */
@Composable
fun FrontEntryPoint(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var eatHere by rememberSaveable { mutableStateOf(true) }
    var inTable by rememberSaveable { mutableStateOf(false) }
    if (inTable) {
        FrontTableFlow(onExit = { inTable = false })
        return
    }
    Column(Modifier.fillMaxSize().background(Ids.colors.background).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).pressScaleClickable(onClick = onBack), contentAlignment = Alignment.CenterStart) {
                ItundaArt("back", ItundaArtSet.Line, 24.dp, tint = Ids.colors.textPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text("Front", color = Ids.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text("Order, pay and go", color = Ids.colors.textTertiary, fontSize = 13.sp)
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier.width(294.dp).clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFF8F89FF), Color(0xFFC4C1FF)))).padding(20.dp),
            ) {
                Text("Lunch deal", color = Ids.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(6.dp))
                Text("Pay with itunda and earn double points", color = Ids.colors.textPrimary, fontSize = 13.sp)
            }
            Column(Modifier.width(294.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFFFFE3BF)).padding(20.dp)) {
                Text("New on the menu", color = Ids.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(6.dp))
                Text("Fresh juice, passion or mango", color = Ids.colors.textPrimary, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("Choose how to order", color = Ids.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 28.dp))
        Spacer(Modifier.height(14.dp))
        Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OrderModeCard(Modifier.weight(1f), "restaurant", "Eat here", "We bring it to your table", eatHere) { eatHere = true }
            OrderModeCard(Modifier.weight(1f), "bag", "Take away", "Pick up at the counter", !eatHere) { eatHere = false }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.padding(horizontal = 24.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ids.colors.surfaceSoft).pressScaleClickable { inTable = true }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ItundaArt("qr", ItundaArtSet.Line, 28.dp, tint = Ids.colors.textPrimary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Scan the table code", color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Menu, bill and payment at your table", color = Ids.colors.textTertiary, fontSize = 13.sp)
            }
            ItundaArt("chev", ItundaArtSet.Line, 18.dp, tint = Ids.colors.textTertiary)
        }
        Spacer(Modifier.height(28.dp))
        Text(if (eatHere) "Eat here near you" else "Take away near you", color = Ids.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 28.dp))
        Spacer(Modifier.height(8.dp))
        frontPlaces.forEach { p ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
                    ItundaArt(p.art, ItundaArtSet.Color, 30.dp)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("★", color = Color(0xFFFFB800), fontSize = 13.sp)
                        Text(" ${p.rating} (${p.reviews}) · ${p.km}", color = Ids.colors.textTertiary, fontSize = 13.sp)
                    }
                }
                ItundaArt("chev", ItundaArtSet.Line, 18.dp, tint = Ids.colors.textTertiary)
            }
        }
    }
}

@Composable
private fun OrderModeCard(modifier: Modifier, art: String, title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(if (selected) Ids.colors.brand.copy(alpha = 0.12f) else Ids.colors.background)
            .border(if (selected) 2.dp else 1.dp, if (selected) Ids.colors.brand else Ids.colors.surfaceSoft, RoundedCornerShape(20.dp))
            .pressScaleClickable(onClick = onClick).padding(16.dp),
    ) {
        ItundaArt(art, ItundaArtSet.Color, 34.dp)
        Spacer(Modifier.height(14.dp))
        Text(title, color = Ids.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, color = Ids.colors.textTertiary, fontSize = 13.sp)
    }
}
