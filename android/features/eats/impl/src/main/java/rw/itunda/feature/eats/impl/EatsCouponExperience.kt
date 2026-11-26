package rw.itunda.feature.eats.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
            Text(if (isPickup) "20% off your meal" else "Free delivery on your first\norder", color = Color(0xFF191F28), fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)
            Text(if (isPickup) "Up to 2,000 RWF with EATS20" else "Code FREEDEL, auto-applied", color = Color(0xFF3B4350), fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
        }
        ItundaArt(if (isPickup) "voucher" else "gift", ItundaArtSet.Color, 52.dp)
    }
}

/** v1038 coupon wallet: two offers, applied/removal states, and a working code field. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EatsCouponsSheet(
    isPickup: Boolean,
    selectedCoupon: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var codeInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current

    val applyCode = {
        val code = codeInput.trim().uppercase()
        when {
            code.isEmpty() -> errorMessage = "Enter a code first."
            code != "FREEDEL" && code != "EATS20" -> errorMessage = "Code not valid. Try EATS20."
            code == "FREEDEL" && isPickup -> errorMessage = "Free delivery only applies to delivery orders."
            else -> {
                keyboard?.hide()
                onSelect(code)
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = Ids.colors.background,
        contentColor = Ids.colors.textPrimary,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 32.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
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
                value = "Free",
                description = "First order · delivery only",
                code = "FREEDEL",
                valueBackground = Color(0xFFFFE08A),
                selected = selectedCoupon == "FREEDEL",
                disabled = isPickup,
                onUse = { onSelect("FREEDEL") },
                onRemove = onRemove,
            )
            EatsCouponCard(
                title = "20% off your meal",
                value = "-20%",
                description = "Up to 2,000 RWF off",
                code = "EATS20",
                valueBackground = Color(0xFFCFE8FF),
                selected = selectedCoupon == "EATS20",
                disabled = false,
                onUse = { onSelect("EATS20") },
                onRemove = onRemove,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp))
                        .background(Ids.colors.chip).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (codeInput.isEmpty()) Text("Enter a code", color = Ids.colors.textSecondary, fontSize = 15.sp, lineHeight = 21.sp)
                    BasicTextField(
                        value = codeInput,
                        onValueChange = {
                            codeInput = it.uppercase()
                            errorMessage = ""
                        },
                        singleLine = true,
                        textStyle = TextStyle(color = Ids.colors.textPrimary, fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = (-0.176).sp),
                        cursorBrush = SolidColor(Ids.colors.brand),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { applyCode() }),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Promo code" },
                    )
                }
                Box(
                    modifier = Modifier.height(48.dp).clip(RoundedCornerShape(14.dp))
                        .background(Ids.colors.textPrimary)
                        .pressScaleClickable(onClick = applyCode)
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Apply", color = Ids.colors.background, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Text(
                errorMessage,
                color = Ids.colors.danger,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 18.dp)
                    .semantics { contentDescription = if (errorMessage.isNotEmpty()) errorMessage else "Coupon code status" },
            )
        }
    }
}

@Composable
private fun EatsCouponCard(
    title: String,
    value: String,
    description: String,
    code: String,
    valueBackground: Color,
    selected: Boolean,
    disabled: Boolean,
    onUse: () -> Unit,
    onRemove: (String) -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val canUse = !selected && !disabled
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            .clip(shape).background(Ids.colors.chip)
            .border(if (selected) 2.dp else 0.dp, if (selected) Ids.colors.brand else Color.Transparent, shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(valueBackground),
            contentAlignment = Alignment.Center,
        ) {
            Text(value, color = Color(0xFF191F28), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text("$description · $code", color = Ids.colors.textSecondary, fontSize = 13.sp, lineHeight = 17.sp)
            if (selected) {
                Text(
                    "Remove",
                    color = Ids.colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp).semantics { contentDescription = "Remove $code" }
                        .pressScaleClickable { onRemove(code) },
                )
            }
        }
        Box(
            modifier = Modifier.height(40.dp).clip(RoundedCornerShape(20.dp))
                .background(if (canUse) Ids.colors.textPrimary else Ids.colors.background)
                .semantics { contentDescription = if (canUse) "Use $code" else if (selected) "Applied $code" else "Delivery only $code" }
                .pressScaleClickable(enabled = canUse, onClick = onUse)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                when {
                    selected -> "Applied ✓"
                    disabled -> "Delivery only"
                    else -> "Use"
                },
                color = if (canUse) Ids.colors.background else Ids.colors.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}
