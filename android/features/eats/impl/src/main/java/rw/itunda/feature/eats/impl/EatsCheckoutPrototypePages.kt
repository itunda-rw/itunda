package rw.itunda.feature.eats.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable

@Composable
internal fun EatsCheckoutPaymentPage(
    selected: EatsPaymentMethod,
    payBalance: Double,
    bankBalance: Double,
    onBack: () -> Unit,
    onSelect: (EatsPaymentMethod) -> Unit,
    onDone: () -> Unit,
) {
    val palette = eatsPrototypePalette()
    Column(Modifier.fillMaxSize().background(palette.background)) {
        EatsCheckoutPageBack(onBack, palette)
        Text("Pay with", color = palette.primary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 10.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            EatsCheckoutPaymentOption(
                title = "itunda Pay",
                detail = String.format(java.util.Locale.US, "%,.0f RWF", payBalance),
                selected = selected == EatsPaymentMethod.ITUNDA_PAY,
                palette = palette,
                onClick = { onSelect(EatsPaymentMethod.ITUNDA_PAY) },
            )
            EatsCheckoutPaymentOption(
                title = "itunda Bank",
                detail = String.format(java.util.Locale.US, "%,.0f RWF", bankBalance),
                selected = selected == EatsPaymentMethod.ITUNDA_BANK,
                palette = palette,
                onClick = { onSelect(EatsPaymentMethod.ITUNDA_BANK) },
            )
            EatsCheckoutPaymentOption(
                title = "Cash on delivery",
                detail = "Pay your rider when the food arrives",
                selected = selected == EatsPaymentMethod.CASH,
                palette = palette,
                onClick = { onSelect(EatsPaymentMethod.CASH) },
            )
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp).height(52.dp)
                .clip(RoundedCornerShape(16.dp)).background(palette.accentTint)
                .pressScaleClickable(onClick = onDone),
            contentAlignment = Alignment.Center,
        ) {
            Text("Done", color = palette.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EatsCheckoutPaymentOption(
    title: String,
    detail: String,
    selected: Boolean,
    palette: EatsPrototypePalette,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).pressScaleClickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(20.dp).clip(CircleShape)
                .background(if (selected) palette.brandFill else palette.background)
                .border(if (selected) 0.dp else 1.dp, palette.divider, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Text("✓", color = palette.brandOn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column {
            Text(title, color = palette.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = palette.secondary, fontSize = 12.sp)
        }
    }
}

@Composable
internal fun EatsCheckoutAddressPage(
    currentAddress: String,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onUseAddress: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val palette = eatsPrototypePalette()
    var showForm by remember { mutableStateOf(false) }
    var newAddress by remember { mutableStateOf("") }
    var savedNewAddress by remember { mutableStateOf<String?>(null) }
    var selectedAddress by remember(currentAddress) { mutableStateOf(currentAddress) }

    Column(Modifier.fillMaxSize().background(palette.background)) {
        EatsCheckoutPageBack(onBack, palette)
        Text("Delivery address", color = palette.primary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 20.dp, top = 4.dp))
        Text("Your rider will go to the address you pick.", color = palette.secondary, fontSize = 12.sp, modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp))
        if (!showForm) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                EatsCheckoutAddressOption(
                    label = "Home",
                    address = "Kimironko, KG 11 Ave",
                    selected = selectedAddress == "Kimironko, KG 11 Ave",
                    art = "home",
                    palette = palette,
                    onClick = { selectedAddress = "Kimironko, KG 11 Ave"; onSelect(selectedAddress) },
                )
                EatsCheckoutAddressOption(
                    label = "Work",
                    address = "Kacyiru, KG 7 Ave",
                    selected = selectedAddress == "Kacyiru, KG 7 Ave",
                    art = "briefcase",
                    palette = palette,
                    onClick = { selectedAddress = "Kacyiru, KG 7 Ave"; onSelect(selectedAddress) },
                )
                savedNewAddress?.let { value ->
                    EatsCheckoutAddressOption(
                        label = "Other",
                        address = value,
                        selected = selectedAddress == value,
                        art = "pin",
                        palette = palette,
                        onClick = { selectedAddress = value; onSelect(value) },
                    )
                }
                Row(
                    Modifier.fillMaxWidth().height(56.dp).pressScaleClickable { showForm = true },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(palette.accentTint), contentAlignment = Alignment.Center) {
                        ItundaArt("plus", ItundaArtSet.Line, 18.dp, tint = palette.accent)
                    }
                    Text("Add a new address", color = palette.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text("Street or landmark", color = palette.secondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                EatsCheckoutAddressInput(newAddress, { newAddress = it }, "e.g. Remera, KG 5 Ave", palette)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.weight(0.34f).height(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(palette.accentTint).pressScaleClickable {
                        if (showForm) { showForm = false; newAddress = "" } else onClose()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (showForm) "Cancel" else "Close", color = palette.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                Modifier.weight(0.66f).height(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(palette.brandFill).pressScaleClickable {
                        if (showForm) {
                            if (newAddress.isNotBlank()) {
                                savedNewAddress = newAddress.trim()
                                selectedAddress = newAddress.trim()
                                onSelect(selectedAddress)
                                showForm = false
                                newAddress = ""
                            }
                        } else onUseAddress()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (showForm) "Save and use" else "Use this address", color = palette.brandOn, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EatsCheckoutAddressOption(
    label: String,
    address: String,
    selected: Boolean,
    art: String,
    palette: EatsPrototypePalette,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(54.dp).pressScaleClickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(palette.accentTint), contentAlignment = Alignment.Center) {
            ItundaArt(art, ItundaArtSet.Color, 18.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(label, color = palette.primary, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
            Text(address, color = palette.secondary, fontSize = 12.sp)
        }
        if (selected) ItundaArt("check", ItundaArtSet.Line, 18.dp, tint = palette.accent)
    }
}

@Composable
private fun EatsCheckoutAddressInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    palette: EatsPrototypePalette,
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(fontSize = 15.sp, color = palette.primary),
        cursorBrush = SolidColor(palette.accent),
        modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp))
            .background(palette.surfaceSoft)
            .border(if (focused) 1.dp else 0.dp, palette.accent, RoundedCornerShape(14.dp))
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = "New delivery address" }
            .padding(horizontal = 14.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, color = palette.secondary, fontSize = 14.sp)
                inner()
            }
        },
    )
}

@Composable
internal fun EatsCheckoutConfirmPage(
    amount: Double,
    restaurantName: String,
    submitting: Boolean,
    error: String?,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    val palette = eatsPrototypePalette()
    Box(Modifier.fillMaxSize().background(palette.background)) {
        Column(Modifier.fillMaxSize()) {
            EatsCheckoutPageBack(onBack, palette)
            ItundaArt("shield", ItundaArtSet.Line, 42.dp, tint = palette.brandFill, modifier = Modifier.padding(start = 20.dp, top = 4.dp))
            Text("Confirm it’s you", color = palette.primary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 20.dp, top = 14.dp))
            Text(
                "Pay ${String.format(java.util.Locale.US, "%,.0f RWF", amount)} · $restaurantName",
                color = palette.secondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 20.dp, top = 4.dp),
            )
            error?.let { Text(it, color = Color(0xFFE5484D), fontSize = 12.sp, modifier = Modifier.padding(start = 20.dp, top = 12.dp)) }
        }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp)
                .height(52.dp).clip(RoundedCornerShape(16.dp)).background(palette.brandFill)
                .pressScaleClickable(enabled = !submitting, onClick = onConfirm),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (submitting) "Confirming…" else "Confirm", color = palette.brandOn, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EatsCheckoutPageBack(onBack: () -> Unit, palette: EatsPrototypePalette) {
    Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).pressScaleClickable(onClick = onBack), contentAlignment = Alignment.Center) {
            ItundaArt("back", ItundaArtSet.Line, 24.dp, tint = palette.primary)
        }
    }
}
