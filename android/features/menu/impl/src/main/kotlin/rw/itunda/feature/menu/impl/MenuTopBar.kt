package rw.itunda.feature.menu.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons

// Moved here from :app's ItundaAppSharedUi.kt (2026-09-02, Menu Feature-module
// decomposition) -- confirmed used only by MenuScreen, which moved to this same
// module in the same slice.

// CORRECTED 2026-08-12: the real Toss All-tab header is exactly a 3-link text row:
// "Authentication | Help | Settings".
@Composable
internal fun AllTopBar(onOpenAuthentication: () -> Unit = {}, onOpenHelp: () -> Unit = {}, onOpenSettings: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Authentication",
            color = Ids.colors.textSecondary,
            fontSize = 15.sp,
            modifier = Modifier.pressScaleClickable(onClick = onOpenAuthentication),
        )
        VerticalDivider(
            modifier = Modifier.padding(horizontal = 10.dp).height(14.dp),
            color = Ids.colors.textTertiary,
        )
        Text(
            "Help",
            color = Ids.colors.textSecondary,
            fontSize = 15.sp,
            modifier = Modifier.pressScaleClickable(onClick = onOpenHelp),
        )
        VerticalDivider(
            modifier = Modifier.padding(horizontal = 10.dp).height(14.dp),
            color = Ids.colors.textTertiary,
        )
        Text(
            "Settings",
            color = Ids.colors.textSecondary,
            fontSize = 15.sp,
            modifier = Modifier.pressScaleClickable(onClick = onOpenSettings),
        )
    }
}

// Real fix (2026-08-10): this was pure decoration -- a Box with static text, no
// TextField, nothing typed into it ever did anything.
@Composable
internal fun MenuSearchBar(query: String, onQueryChange: (String) -> Unit, placeholder: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Ids.colors.surfaceSoft)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Icon(IdsIcons.Search, contentDescription = null, tint = Ids.colors.textTertiary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(placeholder, color = Ids.colors.textSecondary, fontSize = 16.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = Ids.colors.textPrimary, fontSize = 16.sp),
                cursorBrush = SolidColor(Ids.colors.brand),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(22.dp)) {
                Icon(IdsIcons.Close, contentDescription = "Clear search", tint = Ids.colors.textTertiary, modifier = Modifier.size(16.dp))
            }
        }
    }
}
