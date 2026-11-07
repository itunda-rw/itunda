package rw.itunda.feature.credit.impl

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Divider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import rw.itunda.core.designsystem.theme.Ids

/** Flat list block: content, then a hairline divider. Replaces Material `Card` (UI_UX_GUIDELINES rule 10). */
@Composable
internal fun FlatBlock(content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        content()
        Divider(color = Ids.colors.divider, thickness = 0.5.dp)
    }
}
