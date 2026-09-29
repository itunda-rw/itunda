package rw.itunda.feature.menu.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

// Moved here from :app's ItundaAppScreen.kt (2026-09-02, Menu Feature-module
// decomposition) -- confirmed used only by MenuScreen, which moved to this same
// module in the same slice.
@Composable
internal fun IconGridSection(
    title: String,
    items: List<Pair<String, ImageVector>>,
    onItemClick: (String) -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, color = Ids.colors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        ) {
            items.forEach { (label, icon) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(64.dp).pressScaleClickable { onItemClick(label) },
                ) {
                    Box(modifier = Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = Ids.colors.textPrimary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(label, color = Ids.colors.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }
    }
}
