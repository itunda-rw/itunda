package rw.itunda.feature.eats.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.PretendardFontFamily
import rw.itunda.core.network.EatsMenuOptionChoiceDto
import rw.itunda.core.network.EatsMenuOptionGroupDto
import java.util.Locale

/** Full-height v1038 product sheet with optional add-ons, preferences and a fixed cart action. */
@Composable
internal fun EatsMenuItemSheet(
    name: String,
    description: String?,
    price: Double,
    optionGroups: List<EatsMenuOptionGroupDto>,
    art: String,
    onDismiss: () -> Unit,
    onAdd: (quantity: Int, choiceIds: List<String>, preferences: List<String>) -> Unit,
) {
    val palette = eatsPrototypePalette()
    val secondaryText = if (palette.background == Color.White) Color(0xFF566170) else Color(0xFFA0A7B4)
    val addOnAccent = if (palette.background == Color.White) Color(0xFF6960F9) else Color(0xFF8F89FF)
    val preferenceLabels = listOf("No onions", "Extra spicy", "Less salt", "No sugar")
    val addOnChoices = optionGroups.flatMap { it.choices }
    var selectedChoiceIds by remember(name) { mutableStateOf(emptySet<String>()) }
    var selectedPreferences by remember(name) { mutableStateOf(emptySet<String>()) }
    var quantity by remember(name) { mutableStateOf(1) }
    val selectedAddOnTotal = addOnChoices.filter { it.id in selectedChoiceIds }.sumOf { it.priceDelta }
    val total = (price + selectedAddOnTotal) * quantity
    val requiredOptionsChosen = optionGroups.all { group ->
        !group.required || group.choices.any { it.id in selectedChoiceIds }
    }

    fun toggleChoice(choice: EatsMenuOptionChoiceDto) {
        val group = optionGroups.firstOrNull { candidate -> candidate.choices.any { it.id == choice.id } }
        selectedChoiceIds = if (choice.id in selectedChoiceIds) {
            if (group?.required == true) selectedChoiceIds else selectedChoiceIds - choice.id
        } else if (group?.multiSelect == true) {
            selectedChoiceIds + choice.id
        } else {
            (selectedChoiceIds - group?.choices.orEmpty().map { it.id }.toSet()) + choice.id
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            Column(
                Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 22.dp)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(palette.background)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                Spacer(Modifier.height(3.dp))
                Row(
                    Modifier.fillMaxWidth().height(70.dp).padding(start = 12.dp, top = 16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        Modifier.size(48.dp).semantics { contentDescription = "Back" }
                            .pressScaleClickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        ItundaArt("back", ItundaArtSet.Line, 24.dp, tint = palette.primary)
                    }
                }
                Spacer(Modifier.height(1.dp))
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = PretendardFontFamily)) {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp).padding(top = 8.dp),
                        ) {
                            Box(
                                Modifier.fillMaxWidth().height(168.dp).clip(RoundedCornerShape(22.dp))
                                    .background(Color(0xFFFFE8C7)),
                                contentAlignment = Alignment.Center,
                            ) {
                                ItundaArt(art, ItundaArtSet.Color, 120.dp)
                            }
                            Column(Modifier.padding(top = 14.dp)) {
                                Text(name, color = palette.primary, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    String.format(Locale.US, "%,.0f RWF", price),
                                    color = palette.primary,
                                    fontSize = 16.sp,
                                    lineHeight = 19.sp,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            if (!description.isNullOrBlank()) {
                                Text(
                                    description,
                                    color = secondaryText,
                                    fontSize = 15.sp,
                                    lineHeight = 21.sp,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                            }
                            if (addOnChoices.isNotEmpty()) {
                                Text(
                                    "Add-ons",
                                    color = palette.primary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
                                )
                                Column {
                                    addOnChoices.forEach { choice ->
                                        val selected = choice.id in selectedChoiceIds
                                        Row(
                                            Modifier.fillMaxWidth().height(42.dp).pressScaleClickable { toggleChoice(choice) },
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(choice.name, color = palette.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.weight(1f))
                                            Text(
                                                String.format(Locale.US, "+%,.0f RWF", choice.priceDelta),
                                                color = secondaryText,
                                                fontSize = 16.sp,
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Box(
                                                Modifier.size(22.dp).clip(RoundedCornerShape(7.dp))
                                                    .background(if (selected) addOnAccent else Color.Transparent)
                                                    .border(1.dp, if (selected) addOnAccent else palette.divider, RoundedCornerShape(7.dp)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                if (selected) ItundaArt("check", ItundaArtSet.Line, 14.dp, tint = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                            Text(
                                "Preferences",
                                color = palette.primary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(top = 18.dp, bottom = 10.dp),
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                preferenceLabels.forEach { label ->
                                    val selected = label in selectedPreferences
                                    Box(
                                        Modifier.width(270.dp).height(52.dp).clip(RoundedCornerShape(16.dp))
                                            .background(if (selected) palette.selected else palette.surfaceSoft)
                                            .pressScaleClickable {
                                                selectedPreferences = if (selected) selectedPreferences - label else selectedPreferences + label
                                            },
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        Text(
                                            label,
                                            color = if (selected) palette.selectedText else palette.primary,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Box(
                        Modifier.align(Alignment.BottomCenter).offset(y = 4.dp).fillMaxWidth().height(100.dp)
                            .background(palette.background),
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(top = 14.dp, bottom = 28.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                Modifier.width(122.dp).height(52.dp).clip(RoundedCornerShape(26.dp))
                                    .background(palette.surfaceSoft).padding(4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.size(44.dp).clip(CircleShape).semantics { contentDescription = "Remove one" }
                                        .pressScaleClickable(enabled = quantity > 1) { quantity = (quantity - 1).coerceAtLeast(1) },
                                    contentAlignment = Alignment.Center,
                                ) { ItundaArt("minus", ItundaArtSet.Line, 18.dp, tint = if (quantity > 1) palette.primary else secondaryText.copy(alpha = 0.4f)) }
                                Text(quantity.toString(), color = palette.primary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                Box(
                                    Modifier.size(44.dp).clip(CircleShape).semantics { contentDescription = "Add one" }
                                        .pressScaleClickable(enabled = quantity < 9) { quantity = (quantity + 1).coerceAtMost(9) },
                                    contentAlignment = Alignment.Center,
                                ) { ItundaArt("plus", ItundaArtSet.Line, 18.dp, tint = palette.primary) }
                            }
                            Box(
                                Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(18.dp))
                                    .background(palette.brandFill)
                                    .pressScaleClickable(enabled = requiredOptionsChosen) {
                                        onAdd(quantity, selectedChoiceIds.toList().sorted(), preferenceLabels.filter { it in selectedPreferences })
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "Add · " + String.format(Locale.US, "%,.0f", total) + " RWF",
                                    color = palette.brandOn,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
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
