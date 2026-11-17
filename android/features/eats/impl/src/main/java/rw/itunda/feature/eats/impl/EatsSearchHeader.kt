package rw.itunda.feature.eats.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

/** Prototype Eats search: soft rounded pill with a search icon, then big bold category tabs (selected = dark filled). */
@Composable
fun EatsSearchHeader(
    searchInput: String,
    onSearchChange: (String) -> Unit,
    categories: List<String>,
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit,
    showSearch: Boolean = true,
) {
    Column {
        if (showSearch) Row(
            Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).background(Ids.colors.surfaceSoft).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ItundaArt("search", ItundaArtSet.Line, 20.dp, tint = Ids.colors.textTertiary)
            Box(Modifier.padding(start = 10.dp)) {
                if (searchInput.isEmpty()) Text("Search restaurants", color = Ids.colors.textTertiary, fontSize = 16.sp)
                BasicTextField(
                    value = searchInput, onValueChange = onSearchChange, singleLine = true,
                    textStyle = TextStyle(color = Ids.colors.textPrimary, fontSize = 16.sp),
                    cursorBrush = SolidColor(Ids.colors.brand),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (categories.isNotEmpty()) {
            Spacer(Modifier.height(if (showSearch) 14.dp else 4.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.offset(x = (-24).dp).width(maxWidth + 48.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                listOf<String?>(null).plus(categories).forEach { c ->
                    val on = c == selectedCategory
                    Box(
                        Modifier.height(44.dp).clip(RoundedCornerShape(14.dp)).background(if (on) Ids.colors.surfaceSoft else Color.Transparent)
                            .pressScaleClickable { onSelectCategory(c) }.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(c ?: "All", color = if (on) Ids.colors.textPrimary else Ids.colors.textSecondary, fontSize = 16.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                }
                }
            }
        }
    }
}
