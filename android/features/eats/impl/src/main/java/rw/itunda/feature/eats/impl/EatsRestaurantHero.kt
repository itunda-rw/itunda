package rw.itunda.feature.eats.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

/** Prototype restaurant page top: peach hero with back button, rounded sheet, name and meta (demo meta line). */
@Composable
fun EatsRestaurantHero(name: String, onBack: () -> Unit, meta: String = "15-25 min · Fee Free · Min 2,000") {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(200.dp).background(Color(0xFFFFE6C7))) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(28.dp).clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Ids.colors.background))
            ItundaArt("bread", ItundaArtSet.Color, 96.dp, modifier = Modifier.align(Alignment.Center).padding(bottom = 16.dp))
            ItundaArt("juice", ItundaArtSet.Color, 56.dp, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 48.dp, top = 24.dp))
            Box(
                Modifier.padding(start = 10.dp, top = 12.dp).size(40.dp).clip(CircleShape).background(Color.White).pressScaleClickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { ItundaArt("back", ItundaArtSet.Line, 22.dp, tint = Ids.colors.textPrimary) }
        }
        Column(
            Modifier.fillMaxWidth().background(Ids.colors.background).padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            Text(name, color = Ids.colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            Text(meta, color = Ids.colors.textTertiary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
