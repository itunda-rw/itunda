package rw.itunda.feature.eats.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
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
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import androidx.compose.ui.layout.ContentScale

/** Restaurant hero uses the merchant photo when available, otherwise category-matched Itunda artwork. */
@Composable
fun EatsRestaurantHero(
    name: String,
    onBack: () -> Unit,
    meta: String = "15-25 min · Fee Free · Min 2,000",
    category: String? = null,
    photoUrl: String? = null,
) {
    val heroTint = when (category?.lowercase()) {
        "bakery" -> Color(0xFFFFE6C7)
        "cafe", "coffee" -> Color(0xFFDDF3E4)
        "local" -> Color(0xFFFFDDD4)
        "dessert" -> Color(0xFFFFF0B8)
        "fries", "fast food" -> Color(0xFFE6DDF7)
        else -> Color(0xFFE6DDF7)
    }
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(200.dp).background(heroTint)) {
            if (!photoUrl.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = photoUrl,
                    contentDescription = "$name restaurant",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when (painter.state) {
                        is coil.compose.AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                        else -> Box(Modifier.fillMaxSize().background(heroTint), contentAlignment = Alignment.Center) {
                            ItundaArt(eatsCategoryArt(category), ItundaArtSet.Color, 104.dp)
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ItundaArt(eatsCategoryArt(category), ItundaArtSet.Color, 104.dp)
                }
            }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(28.dp).clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Ids.colors.background))
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
