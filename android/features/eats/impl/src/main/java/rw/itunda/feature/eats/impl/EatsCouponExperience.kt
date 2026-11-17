package rw.itunda.feature.eats.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
            Text(if (isPickup) "20% off your meal" else "Free delivery on your first order", color = Color(0xFF191F28), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text(if (isPickup) "Up to 2,000 RWF with EATS20" else "Code FREEDEL, auto-applied at checkout", color = Color(0xFF3B4350), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
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
    onRemove: () -> Unit = {},
) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun applyCode() {
        val entered = code.trim().uppercase()
        when {
            entered.isBlank() -> error = "Enter a code first."
            entered != "FREEDEL" && entered != "EATS20" -> error = "Code not valid. Try EATS20."
            entered == "FREEDEL" && isPickup -> error = "Free delivery only applies to delivery orders."
            else -> onSelect(entered)
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Ids.colors.background, contentColor = Ids.colors.textPrimary) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Your coupons", color = Ids.colors.textPrimary, style = TextStyle(fontSize = 22.sp, letterSpacing = (-0.5).sp, fontWeight = FontWeight.ExtraBold))
            EatsCouponCard("Free delivery", "First order · delivery only", "FREEDEL", "Free", Color(0xFFFFE08A), selectedCoupon == "FREEDEL", isPickup, { onSelect("FREEDEL") }, onRemove)
            EatsCouponCard("20% off your meal", "Up to 2,000 RWF off", "EATS20", "-20%", Color(0xFFCFE8FF), selectedCoupon == "EATS20", false, { onSelect("EATS20") }, onRemove)
            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).background(Ids.colors.surfaceSoft).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (code.isEmpty()) Text("Enter a code", color = Ids.colors.textSecondary, fontSize = 15.sp)
                    BasicTextField(
                        value = code,
                        onValueChange = { code = it.uppercase(); error = null },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { applyCode() }),
                        textStyle = TextStyle(color = Ids.colors.textPrimary, fontSize = 15.sp),
                        cursorBrush = SolidColor(Ids.colors.brand),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Promo code" },
                    )
                }
                Box(
                    modifier = Modifier.height(48.dp).clip(RoundedCornerShape(14.dp)).background(Ids.colors.textPrimary)
                        .pressScaleClickable(onClick = ::applyCode).padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Apply", color = Ids.colors.background, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp) }
            }
            error?.let { Text(it, color = Color(0xFFD92D20), fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun EatsCouponCard(
    title: String,
    description: String,
    code: String,
    value: String,
    background: Color,
    selected: Boolean,
    disabled: Boolean,
    onUse: () -> Unit,
    onRemove: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier.fillMaxWidth().clip(shape).background(Ids.colors.surfaceSoft)
            .then(if (selected) Modifier.border(2.dp, Ids.colors.brand, shape) else Modifier)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(background), contentAlignment = Alignment.Center) {
            Text(value, color = Color(0xFF191F28), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text("$description · $code", color = Ids.colors.textSecondary, fontSize = 13.sp)
            if (selected) Text("Remove", color = Ids.colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp).pressScaleClickable(onClick = onRemove))
        }
        Box(
            modifier = Modifier.height(40.dp).clip(RoundedCornerShape(20.dp))
                .background(if (selected || disabled) Ids.colors.background else Ids.colors.textPrimary)
                .semantics { contentDescription = if (disabled) "Delivery only" else if (selected) "Applied" else "Use" }
                .pressScaleClickable(enabled = !selected && !disabled, onClick = onUse)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (selected) "Applied ✓" else if (disabled) "Delivery only" else "Use", color = if (selected || disabled) Ids.colors.textSecondary else Ids.colors.background, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        }
    }
}
