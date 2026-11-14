package rw.itunda.feature.pay.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

/**
 * Prototype "itunda pay" hub: map hero with an earning-stores pill, the Earning 3% card and benefits.
 * All numbers are dummy until a rewards summary endpoint exists.
 */
@Composable
fun PayHub(onBack: () -> Unit, onFindStores: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ids.colors.background).verticalScroll(rememberScrollState())) {
        Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) { BackTopBar(title = "itunda pay", onBack = onBack) }
        StoresMapHero()
        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(16.dp))
            EarningCard(onFindStores)
            Spacer(Modifier.height(28.dp))
            Text("Benefits received in November", color = Ids.colors.textTertiary, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text("6,400 RWF received", color = Ids.colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(16.dp))
            BenefitRow("Points · Pay Money", "Total 6,400 RWF")
            BenefitRow("Coupons received", "0")
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StoresMapHero() {
    Box(Modifier.fillMaxWidth().height(190.dp).background(Color(0xFFE9ECEF)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val road = Color.White
            drawRect(Color(0xFFCFE8D9), Offset(size.width * 0.72f, 0f), androidx.compose.ui.geometry.Size(size.width * 0.28f, size.height * 0.7f))
            drawLine(road, Offset(0f, size.height * 0.55f), Offset(size.width, size.height * 0.3f), 28f)
            drawLine(road, Offset(size.width * 0.3f, 0f), Offset(size.width * 0.45f, size.height), 22f)
            drawLine(Color(0xFFE8457C), Offset(0f, size.height * 0.2f), Offset(size.width * 0.3f, size.height * 0.28f), 8f, StrokeCap.Round)
            drawLine(Color(0xFFE8457C), Offset(size.width * 0.3f, size.height * 0.28f), Offset(size.width * 0.42f, size.height), 8f, StrokeCap.Round)
            listOf(0.55f to 0.2f, 0.18f to 0.55f, 0.82f to 0.78f, 0.45f to 0.7f).forEach { (x, y) ->
                drawCircle(Color(0xFFF28C38), 14f, Offset(size.width * x, size.height * y))
                drawCircle(Color.White, 5f, Offset(size.width * x, size.height * y))
            }
            drawCircle(Color.White, 15f, Offset(size.width * 0.3f, size.height * 0.28f), style = Stroke(8f))
        }
        Text(
            "128 earning stores nearby", color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 24.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun EarningCard(onFindStores: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ids.colors.surfaceSoft).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("itunda Pay", color = Ids.colors.textTertiary, fontSize = 15.sp)
                Text("Earning 3%", color = Ids.colors.textBrand, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text(
                "Find stores", color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Ids.colors.brand)
                    .pressScaleClickable(onClick = onFindStores).padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(Ids.colors.brand))
            Column(Modifier.padding(start = 16.dp)) {
                Text("itunda card benefits", color = Ids.colors.textBrand, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Get 2% extra back", color = Ids.colors.textTertiary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun BenefitRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = Ids.colors.textTertiary, fontSize = 14.sp)
    }
}
