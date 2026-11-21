package rw.itunda.merchant.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Divider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import rw.itunda.core.designsystem.theme.Ids

/**
 * Flat list block: content followed by a hairline divider, no card chrome.
 * Replaces Material `Card` in the Business inner screens (UI_UX_GUIDELINES rule 10,
 * matching the prototype's flat rows).
 */
@Composable
internal fun FlatBlock(content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        content()
        Divider(color = Ids.colors.divider, thickness = 0.5.dp)
    }
}
