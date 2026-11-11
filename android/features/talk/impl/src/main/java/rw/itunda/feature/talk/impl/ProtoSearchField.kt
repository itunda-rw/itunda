package rw.itunda.feature.talk.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.theme.Ids

/** Prototype Messages search box: soft pill, search glyph, local filter (no backend call). */
@Composable
internal fun ProtoSearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(16.dp)).background(Ids.colors.surfaceSoft).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ItundaArt("search", ItundaArtSet.Line, 20.dp, tint = Ids.colors.textTertiary)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search messages", color = Ids.colors.textTertiary, fontSize = 16.sp)
            BasicTextField(
                value = query, onValueChange = onQuery, singleLine = true,
                textStyle = TextStyle(color = Ids.colors.textPrimary, fontSize = 16.sp),
                cursorBrush = SolidColor(Ids.colors.brand),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
