package rw.itunda.core.designsystem.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import rw.itunda.core.designsystem.theme.Ids

@Composable
fun IdsTabs(
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    enabled: (Int) -> Boolean = { true },
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            TextButton(
                onClick = { onSelected(index) },
                enabled = enabled(index),
                modifier = Modifier.weight(1f),
            ) { Text(item, color = if (index == selectedIndex) Ids.colors.brand else Ids.colors.textSecondary) }
        }
    }
}
