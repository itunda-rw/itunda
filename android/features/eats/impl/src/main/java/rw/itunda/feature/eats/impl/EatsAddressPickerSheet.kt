package rw.itunda.feature.eats.impl

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import rw.itunda.core.designsystem.theme.Ids

/** A saved Eats delivery address. Notes are optional and shown under the address. */
data class EatsSavedAddress(
    val label: String,
    val address: String,
    val note: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EatsAddressPickerSheet(state: EatsBrowseState, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var pendingIndex by remember(state) {
        mutableIntStateOf(state.savedAddresses.indexOfFirst { it.address == state.address }.coerceAtLeast(0))
    }
    var showForm by remember { mutableStateOf(false) }
    var newLabel by remember { mutableStateOf("Other") }
    var newAddress by remember { mutableStateOf("") }
    var newNote by remember { mutableStateOf("") }

    fun removeAddress(index: Int) {
        val removed = state.savedAddresses[index]
        state.savedAddresses = state.savedAddresses.filterIndexed { i, _ -> i != index }
        if (state.address == removed.address) state.address = state.savedAddresses.firstOrNull()?.address.orEmpty()
        pendingIndex = when {
            pendingIndex == index -> 0
            pendingIndex > index -> pendingIndex - 1
            else -> pendingIndex
        }.coerceIn(0, (state.savedAddresses.size - 1).coerceAtLeast(0))
    }

    fun saveAddress() {
        val value = newAddress.trim()
        if (value.isEmpty()) {
            Toast.makeText(context, "Enter an address first", Toast.LENGTH_SHORT).show()
            return
        }
        state.savedAddresses = state.savedAddresses + EatsSavedAddress(newLabel, value, newNote.trim().ifEmpty { null })
        state.address = value
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Ids.colors.surface,
        contentColor = Ids.colors.textPrimary,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
        ) {
            Text(
                "Delivery address",
                color = Ids.colors.textPrimary,
                style = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp, fontWeight = FontWeight.ExtraBold),
            )
            Text(
                "Your rider will go to the address you pick.",
                color = Ids.colors.textSecondary,
                style = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
                modifier = Modifier.padding(top = 6.dp),
            )

            if (!showForm) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    state.savedAddresses.forEachIndexed { index, saved ->
                        val selected = pendingIndex == index
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .pressScaleClickable { pendingIndex = index }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(14.dp))
                                    .background(if (selected) Ids.colors.pressed else Ids.colors.surfaceSoft),
                                contentAlignment = Alignment.Center,
                            ) {
                                when (saved.label) {
                                    "Home" -> ItundaArt(
                                        "home", ItundaArtSet.Line, 20.dp,
                                        tint = if (selected) Ids.colors.textBrand else Ids.colors.iconPrimary,
                                    )
                                    "Work" -> ItundaArt("briefcase", ItundaArtSet.Color, 20.dp)
                                    else -> ItundaArt(
                                        "pin", ItundaArtSet.Line, 20.dp,
                                        tint = if (selected) Ids.colors.textBrand else Ids.colors.iconPrimary,
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (saved.label == "Other") saved.address.substringBefore(',').trim() else saved.label,
                                    color = Ids.colors.textPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                )
                                Text(saved.address, color = Ids.colors.textSecondary, fontSize = 14.sp, lineHeight = 20.sp)
                                saved.note?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, color = Ids.colors.textBrand, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                            if (selected) ItundaArt("check", ItundaArtSet.Line, 22.dp, tint = Ids.colors.textBrand)
                        }
                        if (index > 1) {
                            Text(
                                "Remove",
                                color = Ids.colors.danger,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(start = 54.dp, bottom = 8.dp)
                                    .pressScaleClickable { removeAddress(index) },
                            )
                        }
                    }
                    if (state.savedAddresses.size < 5) {
                        Row(
                            modifier = Modifier.fillMaxWidth().pressScaleClickable { showForm = true }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(Ids.colors.pressed),
                                contentAlignment = Alignment.Center,
                            ) {
                                ItundaArt("plus", ItundaArtSet.Line, 22.dp, tint = Ids.colors.textBrand)
                            }
                            Text("Add a new address", color = Ids.colors.textBrand, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)) {
                    Text("Label", color = Ids.colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Home", "Work", "Other").forEach { label ->
                            Box(
                                modifier = Modifier.height(36.dp).clip(RoundedCornerShape(18.dp))
                                    .background(if (newLabel == label) Ids.colors.pressed else Ids.colors.surfaceSoft)
                                    .pressScaleClickable { newLabel = label }
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(label, color = if (newLabel == label) Ids.colors.textBrand else Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = if (newLabel == label) FontWeight.ExtraBold else FontWeight.Normal)
                            }
                        }
                    }
                    Text("Street or landmark", color = Ids.colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                    EatsAddressInput(newAddress, { newAddress = it.take(40) }, "e.g. Remera, KG 5 Ave", "Street or landmark")
                    Text("Note for the rider (optional)", color = Ids.colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                    EatsAddressInput(newNote, { newNote = it.take(60) }, "Gate colour, floor, who to ask for", "Note for the rider (optional)")
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                EatsAddressActionButton(
                    label = if (showForm) "Cancel" else "Close",
                    primary = false,
                    modifier = Modifier.weight(0.34f),
                ) {
                    if (showForm) {
                        showForm = false
                        newLabel = "Other"
                        newAddress = ""
                        newNote = ""
                    } else onDismiss()
                }
                EatsAddressActionButton(
                    label = if (showForm) "Save and use" else "Use this address",
                    primary = true,
                    modifier = Modifier.weight(0.66f),
                ) {
                    if (showForm) saveAddress()
                    else {
                        state.savedAddresses.getOrNull(pendingIndex)?.let { state.address = it.address }
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun EatsAddressInput(value: String, onValueChange: (String) -> Unit, placeholder: String, label: String) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(fontSize = 16.sp, color = Ids.colors.textPrimary),
        cursorBrush = SolidColor(Ids.colors.textBrand),
        modifier = Modifier.fillMaxWidth().height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Ids.colors.surfaceSoft)
            .border(if (focused) 2.dp else 0.dp, Ids.colors.textBrand, RoundedCornerShape(14.dp))
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = label }
            .padding(horizontal = 16.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, color = Ids.colors.textSecondary, fontSize = 16.sp)
                inner()
            }
        },
    )
}

@Composable
private fun EatsAddressActionButton(
    label: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier.height(52.dp).clip(RoundedCornerShape(16.dp))
            .background(if (primary) Ids.colors.brand else Ids.colors.surfaceSoft)
            .pressScaleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (primary) Ids.colors.onBrand else Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
    }
}
