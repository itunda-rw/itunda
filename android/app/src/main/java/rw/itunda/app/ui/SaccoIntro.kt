package rw.itunda.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.theme.Ids

/** Prototype SACCO intro shown before the first share: headline, building art, share price, three benefits. */
@Composable
fun SaccoIntro(sharePrice: String) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Own a part of\nyour cooperative", color = Ids.colors.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
            lineHeight = 38.sp, modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Buy shares in your SACCO. When it makes a surplus, you get a dividend.",
            color = Ids.colors.textTertiary, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        Spacer(Modifier.height(24.dp))
        BuildingArt()
        Spacer(Modifier.height(16.dp))
        Text("$sharePrice RWF", color = Ids.colors.textBrand, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            "for one share, with about 300 RWF a year in dividend", color = Ids.colors.textBrand, fontSize = 14.sp,
            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(20.dp))
        Benefit("🏦", "Own part of your SACCO", "Each share is a small stake in your cooperative.")
        Benefit("🔒", "Earn a dividend", "A share of the surplus is paid to you each year, when there is one.")
        Benefit("📅", "Take it out at year end", "Withdraw your shares when the year ends.")
    }
}

@Composable
private fun BuildingArt() {
    val soft = Ids.colors.brand.copy(alpha = 0.18f)
    val ink = Ids.colors.brand
    Canvas(Modifier.size(width = 200.dp, height = 130.dp)) {
        drawRoundRect(soft, Offset(0f, size.height - 14.dp.toPx()), Size(size.width, 14.dp.toPx()), CornerRadius(7.dp.toPx()))
        val roof = Path().apply {
            moveTo(size.width * 0.12f, 38.dp.toPx()); lineTo(size.width / 2, 6.dp.toPx()); lineTo(size.width * 0.88f, 38.dp.toPx()); close()
        }
        drawPath(roof, ink)
        listOf(0.2f, 0.4f, 0.6f, 0.8f).forEach { x ->
            drawRoundRect(soft, Offset(size.width * x - 7.dp.toPx(), 46.dp.toPx()), Size(14.dp.toPx(), 56.dp.toPx()), CornerRadius(4.dp.toPx()))
        }
    }
}

@Composable
private fun Benefit(emoji: String, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) { Text(emoji, fontSize = 18.sp) }
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(body, color = Ids.colors.textTertiary, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
