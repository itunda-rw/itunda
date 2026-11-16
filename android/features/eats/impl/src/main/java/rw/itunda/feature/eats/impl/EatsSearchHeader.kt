package rw.itunda.feature.eats.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
) {
    Column {
        Row(
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
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf<String?>(null).plus(categories).forEach { c ->
                    val on = c == selectedCategory
                    Box(
                        Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) Ids.colors.textPrimary else Ids.colors.background)
                            .pressScaleClickable { onSelectCategory(c) }.padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(c ?: "All", color = if (on) Ids.colors.background else Ids.colors.textTertiary, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}
