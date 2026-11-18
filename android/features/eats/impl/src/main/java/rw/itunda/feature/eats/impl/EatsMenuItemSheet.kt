package rw.itunda.feature.eats.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ExperimentalMaterial3Api
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.PretendardFontFamily
import java.util.Locale

/** The v1038 item-detail sheet: preferences, quantity, and one explicit add action. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun EatsMenuItemSheet(
    name: String,
    description: String?,
    price: Double,
    imageUrl: String?,
    art: String,
    onDismiss: () -> Unit,
    onAdd: (quantity: Int, preferences: List<String>) -> Unit,
) {
    val palette = eatsPrototypePalette()
    val preferenceLabels = listOf("No onions", "Extra spicy", "Less salt", "No sugar")
    var selectedPreferences by remember(name) { mutableStateOf(emptySet<String>()) }
    var quantity by remember(name) { mutableStateOf(1) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = palette.background,
        contentColor = palette.primary,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        dragHandle = null,
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = PretendardFontFamily)) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 28.dp, bottom = 28.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(palette.surfaceSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!imageUrl.isNullOrBlank()) MenuItemThumb(imageUrl, 72.dp)
                        else ItundaArt(art, ItundaArtSet.Color, 56.dp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(name, color = palette.primary, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            String.format(Locale.US, "%,.0f RWF", price),
                            color = palette.primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                if (!description.isNullOrBlank()) {
                    Text(
                        description,
                        color = palette.secondary,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                Text(
                    "Preferences",
                    color = palette.primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 18.dp, bottom = 10.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    preferenceLabels.forEach { label ->
                        val selected = label in selectedPreferences
                        Box(
                            Modifier.height(36.dp).clip(RoundedCornerShape(18.dp))
                                .background(if (selected) palette.selected else palette.surfaceSoft)
                                .pressScaleClickable {
                                    selectedPreferences = if (selected) selectedPreferences - label else selectedPreferences + label
                                }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                color = if (selected) palette.selectedText else palette.primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape)
                            .background(palette.surfaceSoft)
                            .semantics { contentDescription = "Remove one" }
                            .pressScaleClickable(enabled = quantity > 1) { quantity = (quantity - 1).coerceAtLeast(1) },
                        contentAlignment = Alignment.Center,
                    ) { ItundaArt("minus", ItundaArtSet.Line, 18.dp, tint = if (quantity > 1) palette.primary else palette.secondary.copy(alpha = 0.4f)) }
                    Text(quantity.toString(), color = palette.primary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 18.dp))
                    Box(
                        Modifier.size(44.dp).clip(CircleShape)
                            .background(palette.surfaceSoft)
                            .semantics { contentDescription = "Add one" }
                            .pressScaleClickable(enabled = quantity < 9) { quantity = (quantity + 1).coerceAtMost(9) },
                        contentAlignment = Alignment.Center,
                    ) { ItundaArt("plus", ItundaArtSet.Line, 18.dp, tint = palette.primary) }
                }
                Box(
                    Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp))
                        .background(palette.brandFill)
                        .pressScaleClickable { onAdd(quantity, preferenceLabels.filter { it in selectedPreferences }) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Add to cart · " + String.format(Locale.US, "%,.0f", price * quantity) + " RWF",
                        color = palette.brandOn,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

internal fun eatsMenuItemArt(name: String): String = when {
    name.contains("mandazi", ignoreCase = true) -> "mandazi"
    name.contains("juice", ignoreCase = true) -> "juice"
    name.contains("coffee", ignoreCase = true) || name.contains("cappuccino", ignoreCase = true) -> "coffee"
    name.contains("rice", ignoreCase = true) || name.contains("beans", ignoreCase = true) -> "rice"
    name.contains("brochette", ignoreCase = true) || name.contains("goat", ignoreCase = true) -> "brochette"
    name.contains("chip", ignoreCase = true) || name.contains("fries", ignoreCase = true) -> "chips"
    else -> "bread"
}
