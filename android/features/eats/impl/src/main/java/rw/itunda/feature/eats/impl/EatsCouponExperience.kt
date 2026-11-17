package rw.itunda.feature.eats.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

/** Prototype v1038 promo: delivery gets FREEDEL; pickup gets EATS20. */
@Composable
fun EatsPromoBanner(isPickup: Boolean, selected: Boolean, onSelectCoupon: () -> Unit, modifier: Modifier = Modifier) {
    if (selected) return
    val background = if (isPickup) Color(0xFFCFE8FF) else Color(0xFFFFE08A)
    Row(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(background)
            .pressScaleClickable(onClick = onSelectCoupon).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(if (isPickup) "20% off your meal" else "Free delivery on your first order", color = Color(0xFF191F28), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text(if (isPickup) "Up to 2,000 RWF with EATS20" else "Code FREEDEL, auto-applied", color = Color(0xFF3B4350), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
        }
        ItundaArt(if (isPickup) "voucher" else "gift", ItundaArtSet.Color, 52.dp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EatsCouponsSheet(
    isPickup: Boolean,
    selectedCoupon: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onUnavailable: () -> Unit = { onDismiss() },
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = Ids.colors.background,
        contentColor = Ids.colors.textPrimary,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 32.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Your coupons",
                color = Ids.colors.textPrimary,
                fontSize = 22.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 2.dp),
            )
            EatsCouponCard(
                title = "Free delivery",
                description = "First order, delivery only",
                code = "FREEDEL",
                background = Color(0xFFFFE08A),
                selected = selectedCoupon == "FREEDEL",
                onUse = { if (isPickup) onUnavailable() else onSelect("FREEDEL") },
            )
            EatsCouponCard(
                title = "20% off",
                description = "Up to 2,000 RWF off your meal",
                code = "EATS20",
                background = Color(0xFFCFE8FF),
                selected = selectedCoupon == "EATS20",
                onUse = { onSelect("EATS20") },
            )
        }
    }
}

@Composable
private fun EatsCouponCard(
    title: String,
    description: String,
    code: String,
    background: Color,
    selected: Boolean,
    onUse: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(background).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color(0xFF191F28), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("$description · $code", color = Color(0xFF3B4350), fontSize = 13.sp)
        }
        Box(
            modifier = Modifier.height(40.dp).clip(RoundedCornerShape(20.dp))
                .background(Ids.colors.textPrimary)
                .semantics { contentDescription = if (selected) "Saved" else "Use" }
                .pressScaleClickable(enabled = !selected, onClick = onUse)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (selected) "Saved" else "Use", color = Ids.colors.background, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
