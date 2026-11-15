package rw.itunda.feature.eats.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

/** Prototype Eats promo strip: yellow card with a gift. Fixed light colours so text stays readable in dark mode. */
@Composable
fun EatsPromoBanner(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFFFFE08A)).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Free delivery on your first order", color = Color(0xFF191F28), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text("Code FREEDEL, auto-applied", color = Color(0xFF4E5968), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
        ItundaArt("gift", ItundaArtSet.Color, 48.dp)
    }
}

/** Prototype quick-action row: round tinted tile with art, label below. */
@Composable
fun EatsQuickActions(onFastest: () -> Unit, onClub: () -> Unit, onSaved: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        EatsQuickAction("moto", "Fastest", onFastest)
        EatsQuickAction("gift", "Eats Club", onClub)
        EatsQuickAction("heart", "Saved", onSaved)
    }
}

@Composable
private fun EatsQuickAction(art: String, label: String, onClick: () -> Unit) {
    Column(Modifier.pressScaleClickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(20.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
            ItundaArt(art, ItundaArtSet.Color, 30.dp)
        }
        Text(label, color = Ids.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
    }
}
